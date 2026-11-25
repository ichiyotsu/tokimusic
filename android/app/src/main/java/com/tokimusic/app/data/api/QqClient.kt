package com.tokimusic.app.data.api

import com.tokimusic.app.data.model.Album
import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Playlist
import com.tokimusic.app.data.model.QqPlaylistIds
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.data.model.qqArtistId
import com.tokimusic.app.data.model.qqSongId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.Proxy
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Direct-connection hosts for the QQ Music login flow; tests point them at a MockWebServer. */
data class QqHosts(
    val musicu: String = "https://u.y.qq.com",
    /** Plaintext-LRC lyric endpoint (c.y.qq.com, no login needed). */
    val lyric: String = "https://c.y.qq.com",
)

/** What the QQ client needs from settings (kept narrow so tests can fake it). */
interface QqSettings {
    var qqMusicId: String
    var qqMusicKey: String
    /** Stable client GUID (CGI comm OpenUDID/udid), generated once. */
    var qqDeviceId: String
    /** Second stable GUID (CGI comm OpenUDID2), generated once. */
    var qqDeviceId2: String
    /** Stable 16-hex Android id (CGI comm aid), generated once. */
    var qqAndroidId: String
    /** Encrypted UIN (euin) captured from the QQLogin / GetLoginUserInfo
     * responses when the server returns one — the euin-gated library
     * calls need it; empty = unknown (see [QqClient.resolveEuin]). */
    var qqEncryptUin: String
    var neteaseEnabled: Boolean
    var qqEnabled: Boolean
}

/** QQ login credential (musicid / musickey), persisted in [QqSettings]. */
data class QqCredential(val musicId: String, val musicKey: String, val strMusicId: String = "")

/** QQ profile (from GetLoginUserInfo; response shape is only loosely modelled — see [QqClient.getLoginUserInfo]). */
data class QqProfile(val nickname: String, val avatarUrl: String?, val musicId: String)

/** Anonymous Android session (GetSession) — its uid/sid ride in the QQLogin comm. */
private data class QqSession(val uid: String, val sid: String)

/** 手机号短信验证码发送结果（L-1124 PhoneLoginEvents：SEND=0 / CAPTCHA=20276 / FREQUENCY=100001）。 */
sealed interface QqPhoneAuthResult {
    /** 验证码已发送。 */
    data object Sent : QqPhoneAuthResult
    /** 需要人机验证：[securityUrl] 用 WebView 打开做验证，验证完重新发送。 */
    data class Captcha(val securityUrl: String) : QqPhoneAuthResult
    /** 请求过于频繁。 */
    data object Frequency : QqPhoneAuthResult
}

/** Login CGI 业务码 → 中文说明（L-1124 _validate_result；0 与未列出的码返回 null，
 * 调用方用 null 表示“成功”或“用通用文案 + 码号兜底”）。 */
internal fun qqLoginCodeMessage(code: Int): String? = when (code) {
    0 -> null
    1000, 104401, 104400 -> "登录鉴权已过期"
    20261 -> "登录参数错误"
    20271 -> "验证码错误"
    20272 -> "账号绑定异常"
    20274 -> "账号绑定缺失"
    20277, 20278 -> "账号受限"
    20279 -> "登录设备数量超限"
    20450 -> "账号已被封禁"
    104604 -> "操作过于频繁，请稍后再试"
    else -> null
}

/** 中国大陆手机号格式（11 位数字，以 1 开头）——纯函数，单测覆盖。 */
internal fun isValidCnPhone(phone: String): Boolean =
    phone.length == 11 && phone[0] == '1' && phone.all { it.isDigit() }

/** QQ playback URL result — never a fabricated URL: every non-Url case means "do not play". */
sealed interface QqSongUrlResult {
    data class Url(val url: String) : QqSongUrlResult
    /** No QQ credential stored — the user has not logged in. */
    data object NotLoggedIn : QqSongUrlResult
    /** Server result 104003 on every tier: not logged in server-side, or level too low (VIP). */
    data object NoPermission : QqSongUrlResult
    /** Any other failure to produce a URL (empty purl, other result codes). */
    data class Unavailable(val result: Int) : QqSongUrlResult
}

/**
 * File-type ladder per audio-quality preference (L-1124 song.py
 * SongFileType: F000 FLAC, M800 320k, M500 128k). First entry is tried
 * first; lower tiers are fallbacks when the tier has no playable copy
 * (e.g. FLAC is VIP-only → 104003 → try M800).
 */
internal fun qqFileTiersForQuality(quality: String): List<Pair<String, String>> = when (quality) {
    "lossless" -> listOf("F000" to ".flac", "M800" to ".mp3", "M500" to ".mp3")
    "higher", "exhigh" -> listOf("M800" to ".mp3", "M500" to ".mp3")
    else -> listOf("M500" to ".mp3", "M800" to ".mp3")
}

/**
 * 0.1.18 第 8 项：推荐分页合并——多页按顺序拼，按 [Song.sourceId]（QQ
 * songmid）去重，截到 [target] 首。无 sourceId 的行无法去重，直接丢弃
 * （parseQqSong 在 mid 为空时本来就返回 null，正常流程不会遇到）。
 * 纯函数，JVM 单测覆盖（QqClientTest）。
 */
internal fun mergeRecommendPages(pages: List<List<Song>>, target: Int): List<Song> {
    val seen = HashSet<String>()
    val out = ArrayList<Song>()
    for (page in pages) {
        for (song in page) {
            if (out.size >= target) return out
            val key = song.sourceId
            if (key.isBlank() || !seen.add(key)) continue
            out.add(song)
        }
    }
    return out
}

/**
 * OkHttp implementation of the QQ Music login flow — direct calls, no gateway.
 *
 * Faithful port of L-1124-QQMusicApi (`modules/login.py` + `core/executor.py` /
 * `core/versioning.py`), verified end-to-end by the Q0 spike (see
 * `~/workspace/work/qqmusic-direct-spike/REPORT.md`): ptqrshow → ptqrlogin
 * polling → check_sig → graph authorize → QQLogin CGI → GetLoginUserInfo.
 *
 * Sixth round, item 1 — login shell only: QR login + session refresh +
 * profile; no QQ playback, search or playlists yet (phase C). Whether the
 * QQLogin CGI call succeeds on a real device is NOT verified by the spike
 * (it stops at the polling step); the prime suspect if it fails is the
 * missing QIMEI36 — this client sends it as an empty string, which is
 * exactly what L-1124 does when no QIMEI is cached
 * (`versioning.py: QIMEI36=qimei["q36"] if qimei is not None else ""`),
 * but the server may still demand a real one. Porting QIMEI (RSA PKCS1v15
 * + AES-128-CBC, virtual vivo device) is the follow-up if login fails;
 * the constants live in the spike's `probe-android.js` and L-1124
 * `utils/qimei.py`.
 *
 * Device identity: a virtual vivo V2408A profile (the one the spike
 * verified) — phonetype V2408A, os_ver 15, ct 11, cv/v 20090008 — with
 * three stable random ids persisted in [QqSettings] (guid, OpenUDID2,
 * android_id). The credential travels the way L-1124's executor sends
 * it: comm `qq` + `authst`, plus cookies `uin` / `qqmusic_uin` /
 * `qm_keyst` / `qqmusic_key`.
 */
class QqClient(
    private val store: QqSettings,
    private val hosts: QqHosts = QqHosts(),
) {
    companion object {
        /** CGI user-agent (probe-android.js): "QQMusic 20090008(android 15)". */
        private const val CGI_UA = "QQMusic 20090008(android 15)"
        private const val PHONE_TYPE = "V2408A"
        private const val OS_VER = "15"
        private const val CT = 11
        private const val CV = 20090008
        private const val APP_VER = 20090008
        /** Stream fallback domain when neither the GetVkey sip nor GetCdnDispatch returns a CDN (L-1124 song.py _SONG_URL_FALLBACK_DOMAIN). */
        private const val FALLBACK_CDN = "https://isure.stream.qqmusic.qq.com/"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val http = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()


    /**
     * Login CGI 响应 → 凭证（L-1124 _validate_result 的 code 映射 +
     * Credential 字段）：code 0 才取 data.musicid/musickey 并落库到
     * [QqSettings]；其余 code 按映射抛中文错，未列出的码用码号兜底。
     * QQ 扫码换凭证、手机号登录、QQ音乐 App 扫码换凭证三路共用。
     */
    private fun credentialFromLoginResp(resp: JSONObject, failurePrefix: String): QqCredential {
        val code = resp.optInt("code", -1)
        if (code != 0) {
            val detail = qqLoginCodeMessage(code)
            throw ApiException(
                ApiErrorKind.Unknown,
                message = if (detail != null) "$failurePrefix：$detail" else "$failurePrefix（$code）",
            )
        }
        val data = resp.optJSONObject("data") ?: resp
        val musicId = data.opt("musicid")?.toString().orEmpty()
        val musicKey = data.optString("musickey", "")
        if (musicId.isBlank() || musicKey.isBlank()) {
            throw ApiException(ApiErrorKind.Unknown, message = "$failurePrefix：服务端未返回凭证")
        }
        val credential = QqCredential(musicId, musicKey, data.optString("str_musicid", musicId))
        store.qqMusicId = credential.musicId
        store.qqMusicKey = credential.musicKey
        // 0.1.15 资料库同步：QQLogin 响应里若带 encryptUin（L-1124 的
        // Credential 模型同样声明了该字段），落库供资料库的 euin
        // 接口用；没有也不报错（resolveEuin 再想办法）。
        euinCacheTried = false
        euinCache = null
        captureEncryptUin(data)
        return credential
    }

    // ---- phone SMS login (0.1.12) ----

    /**
     * 发手机号验证码（L-1124 send_authcode）：CGI SendPhoneAuthCode，
     * param tmeAppid=qqmusic + areaCode=86 + phoneNo，comm tmeLoginMethod=3，
     * 固定 ANDROID 平台。响应 code 0 → 已发送；20276 → 人机验证
     * （data.securityURL 用 WebView 打开）；100001 → 过于频繁。
     * 手机号须是 QQ音乐账号绑定的手机号。
     */
    suspend fun sendPhoneAuthCode(phone: String): QqPhoneAuthResult = withContext(Dispatchers.IO) {
        val resp = cgi(
            module = "music.login.LoginServer",
            method = "SendPhoneAuthCode",
            param = JSONObject().put("tmeAppid", "qqmusic").put("areaCode", "86").put("phoneNo", phone),
            commExtra = mapOf("tmeLoginMethod" to 3),
            attachStoredCredential = false,
        )
        when (val code = resp.optInt("code", -1)) {
            0 -> QqPhoneAuthResult.Sent
            20276 -> {
                val url = resp.optJSONObject("data")?.optString("securityURL", "").orEmpty()
                if (url.isBlank()) throw ApiException(ApiErrorKind.Unknown, message = "验证码发送失败：需要人机验证但未返回验证地址")
                QqPhoneAuthResult.Captcha(url)
            }
            100001 -> QqPhoneAuthResult.Frequency
            else -> throw ApiException(ApiErrorKind.Unknown, message = "验证码发送失败（$code）")
        }
    }

    /**
     * 手机号验证码换凭证（L-1124 phone_authorize）：CGI Login，
     * param code + loginMode=1 + phoneNo，comm tmeLoginMethod=3 /
     * tmeLoginType=0。成功后凭证落库（credentialFromLoginResp）。
     */
    suspend fun phoneAuthorize(phone: String, authCode: String): QqCredential = withContext(Dispatchers.IO) {
        val resp = cgi(
            module = "music.login.LoginServer",
            method = "Login",
            param = JSONObject().put("code", authCode).put("loginMode", 1).put("phoneNo", phone),
            commExtra = mapOf("tmeLoginMethod" to 3, "tmeLoginType" to 0),
            attachStoredCredential = false,
        )
        credentialFromLoginResp(resp, "手机号登录失败")
    }

    /**
     * QQ音乐 App 扫码换凭证（L-1124 _handle_mobile_message 的 cookies 事件）：
     * MQTT 收到 cookies 后取 qqmusic_uin + qqmusic_key，调 CGI Login，
     * param musicid/qrCodeID/token，comm tmeLoginType=6。
     */
    suspend fun exchangeMobileQrLogin(qrcodeId: String, uin: String, key: String): QqCredential = withContext(Dispatchers.IO) {
        val musicId = uin.toLongOrNull()
            ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ音乐扫码登录失败：凭证格式错误")
        val resp = cgi(
            module = "music.login.LoginServer",
            method = "Login",
            param = JSONObject().put("musicid", musicId).put("qrCodeID", qrcodeId).put("token", key),
            commExtra = mapOf("tmeLoginType" to 6),
            attachStoredCredential = false,
        )
        credentialFromLoginResp(resp, "QQ音乐扫码登录失败")
    }

    // ---- session / profile ----

    /** Anonymous GetSession (spike-verified): returns the uid/sid the QQLogin comm carries. */
    private suspend fun getSession(): QqSession {
        val resp = cgiRaw(
            module = "music.getSession.session",
            method = "GetSession",
            param = JSONObject().put("uid", "").put("vkey", 0).put("caller", 2),
        )
        val data = resp.optJSONObject("data")
            ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ GetSession 未返回 data")
        val session = data.optJSONObject("session")
            ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ GetSession 未返回 session")
        return QqSession(session.optString("uid", ""), session.optString("sid", ""))
    }

    /**
     * GetLoginUserInfo: code 0 → logged in. The profile shape is NOT
     * modelled in L-1124 (it only checks the code), so nickname/avatar are
     * read defensively from several candidate keys, top level and one
     * level deep; anything unrecognised falls back to "QQ用户" + no avatar.
     * Returns null when there is no credential or the server says the
     * credential is dead (caller maps that to Expired).
     */
    suspend fun getLoginUserInfo(): QqProfile? {
        val musicId = store.qqMusicId
        val musicKey = store.qqMusicKey
        if (musicId.isBlank() || musicKey.isBlank()) return null
        val credential = QqCredential(musicId, musicKey)
        val resp = cgi(
            module = "music.UserInfo.userInfoServer",
            method = "GetLoginUserInfo",
            param = null,
            credential = credential,
        )
        if (resp.optInt("code", -1) != 0) return null
        val data = resp.optJSONObject("data") ?: resp
        val nickname = firstString(data, "NickName", "nick", "nickname", "Nick", "name")
            ?: firstString(data.optJSONObject("userinfo"), "NickName", "nick", "nickname", "Nick", "name")
            ?: firstString(data.optJSONObject("userInfo"), "NickName", "nick", "nickname", "Nick", "name")
            ?: "QQ用户"
        val avatar = firstString(data, "Avatar", "avatar", "headurl", "headUrl", "face", "pic")
            ?: firstString(data.optJSONObject("userinfo"), "Avatar", "avatar", "headurl", "headUrl", "face", "pic")
            ?: firstString(data.optJSONObject("userInfo"), "Avatar", "avatar", "headurl", "headUrl", "face", "pic")
        // 0.1.15 资料库同步：响应里若带加密 UIN 也落库（L-1124 不建模
        // 该响应、只验 code，所以字段名只能防御性地读；没有也不报错）。
        captureEncryptUin(data)
        captureEncryptUin(data.optJSONObject("userinfo"))
        captureEncryptUin(data.optJSONObject("userInfo"))
        return QqProfile(nickname, avatar?.takeIf { it.isNotBlank() }, musicId)
    }

    /** 读出对象里的加密 UIN（encryptUin / EncryptUin / encrypted_uin /
     * EncryptedUin）并落库；没有或为空时不动已存的值（登录响应不带
     * 该字段是常态）。 */
    private fun captureEncryptUin(obj: JSONObject?) {
        if (obj == null) return
        val euin = firstString(obj, "encryptUin", "EncryptUin", "encrypted_uin", "EncryptedUin")
            ?.takeIf { it.isNotBlank() } ?: return
        store.qqEncryptUin = euin
    }

    // ---- playback (sixth round, QQ playback) ----

    /** In-memory CDN list from GetCdnDispatch (one call per client lifetime). */
    private var cachedSip: List<String>? = null

    /** In-memory Android session (GetSession uid/sid, one call per client lifetime). */
    private var cachedSession: QqSession? = null

    /** euin 解析的内存缓存（resolveEuin 只探一次；换凭证 / 退出登录时清掉）。 */
    private var euinCacheTried = false
    private var euinCache: String? = null

    /** Stored login credential, if the user is logged in to QQ. */
    private fun storedCredential(): QqCredential? {
        val id = store.qqMusicId
        val key = store.qqMusicKey
        return if (id.isNotBlank() && key.isNotBlank()) QqCredential(id, key) else null
    }

    /** Cached session, fetching one (best effort) the first time. */
    private suspend fun ensureSession(): QqSession? {
        cachedSession?.let { return it }
        return runCatching { getSession() }.getOrNull()?.also { cachedSession = it }
    }

    /**
     * Content/login CGI call (0.1.14 第 9 条)：L-1124 的 executor 给每
     * 次 ANDROID CGI 调用都带 Android session（uid/sid）与登录凭证
     * （comm qq/authst + Cookie），Kotlin 之前只在登录换凭证 / 播放时
     * 带，内容请求全是匿名的——排行榜 GetAll 回 10006。默认值从 store /
     * 缓存的 GetSession 补；显式参数优先。登录换凭证的调用传
     * attachStoredCredential=false（L-1124 登录走全新 scope，不带旧
     * 凭证）。GetSession 本身走 [cgiRaw]（它就是创建 session 的调用）。
     * session 只在会带凭证时取：匿名调用与登录换凭证调用不取（否则
     * 每次调用都多一次 GetSession；无凭证时 session 单独发也无用，
     * 探针已证明 session 对匿名调用的结果无影响）。
     */
    private suspend fun cgi(
        module: String,
        method: String,
        param: JSONObject?,
        commExtra: Map<String, Any?> = emptyMap(),
        credential: QqCredential? = null,
        session: QqSession? = null,
        attachStoredCredential: Boolean = true,
    ): JSONObject {
        val cred = credential ?: if (attachStoredCredential) storedCredential() else null
        val sess = session ?: if (cred != null) runCatching { ensureSession() }.getOrNull() else null
        return cgiRaw(module, method, param, commExtra, cred, sess)
    }

    /**
     * QQ playback URL (music.vkey.GetVkey / UrlGetVkey, ANDROID route —
     * the Q0 spike's only route that can return real audio; the WEB
     * GetVkeyServer route returns a 100-byte stub, never use it).
     * Requires login: anonymous calls come back 104003 with an empty
     * purl (spike-verified), so without a credential this returns
     * [QqSongUrlResult.NotLoggedIn] without touching the network.
     * Tiers fall back per [qqFileTiersForQuality]; the final URL is
     * `cdn + purl` (L-1124 docs/tutorial/download.md), cdn from the
     * GetVkey response's sip, else GetCdnDispatch, else the stream
     * fallback domain. HTTP failures throw [ApiException] (the caller
     * shows "无法获取播放地址，请重试"); a missing URL never throws.
     * Whether a logged-in call actually returns a real purl is NOT
     * verified — it needs a real person to scan the QR code on a device.
     */
    suspend fun songUrl(songmid: String, quality: String = "standard"): QqSongUrlResult {
        val credential = QqCredential(store.qqMusicId, store.qqMusicKey)
        if (credential.musicId.isBlank() || credential.musicKey.isBlank()) {
            return QqSongUrlResult.NotLoggedIn
        }
        var sawNoPermission = false
        var lastResult = -1
        for ((prefix, ext) in qqFileTiersForQuality(quality)) {
            val filename = "$prefix$songmid$songmid$ext"
            val resp = cgi(
                module = "music.vkey.GetVkey",
                method = "UrlGetVkey",
                param = JSONObject()
                    .put("uin", credential.strMusicId.ifBlank { credential.musicId })
                    .put("filename", JSONArray().put(filename))
                    .put("guid", store.qqDeviceId)
                    .put("songmid", JSONArray().put(songmid))
                    .put("songtype", JSONArray().put(0))
                    .put("ctx", 0),
                credential = credential,
            )
            if (resp.optInt("code", -1) != 0) {
                lastResult = resp.optInt("code", -1)
                continue
            }
            val data = resp.optJSONObject("data") ?: continue
            val info = data.optJSONArray("midurlinfo")?.optJSONObject(0)
            val result = info?.optInt("result", -1) ?: -1
            val purl = info?.optString("purl", "").orEmpty()
            if (result == 0 && purl.isNotBlank()) {
                val sip = data.optJSONArray("sip")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                }.orEmpty()
                val cdn = sip.firstOrNull() ?: cdnDispatch().firstOrNull() ?: FALLBACK_CDN
                return QqSongUrlResult.Url(joinUrl(cdn, purl))
            }
            if (result == 104003) sawNoPermission = true
            lastResult = result
        }
        return if (sawNoPermission) QqSongUrlResult.NoPermission else QqSongUrlResult.Unavailable(lastResult)
    }

    /**
     * QQ lyrics via the PlayLyricInfo CGI (0.1.15, item 5):
     * `music.musichallSong.PlayLyricInfo` / `GetPlayLyricInfo`
     * (crypt=1, qrc=0, roma=1, trans=1). The CGI returns the lyric /
     * trans / roma fields as encrypted hex; each is decrypted with
     * [QrcDecrypt] and lands in [LyricPayload] as lrc / tlyric /
     * romalrc (trans/roma are empty for Chinese songs, and only a
     * Japanese song on a real device can show whether the server ever
     * fills them). The CGI works anonymously (probe-verified code 0);
     * when logged in, [cgi] attaches the credential + session like any
     * other content call. When the CGI call fails or returns no lyric,
     * this falls back to the web GET endpoint ([lyricViaWeb]).
     * Returns an empty payload when the server has no lyrics.
     */
    suspend fun lyric(songmid: String): LyricPayload = withContext(Dispatchers.IO) {
        runCatching { lyricViaCgi(songmid) }.getOrNull()?.takeIf { it.lrc != null }
            ?: lyricViaWeb(songmid)
    }

    /** CGI lyric: encrypted hex fields -> decrypted [LyricPayload], or null to fall back. */
    private suspend fun lyricViaCgi(songmid: String): LyricPayload? {
        val resp = cgi(
            module = "music.musichallSong.PlayLyricInfo",
            method = "GetPlayLyricInfo",
            param = JSONObject()
                .put("crypt", 1)
                .put("lrc_t", 0)
                .put("qrc", 0)
                .put("qrc_t", 0)
                .put("roma", 1)
                .put("roma_t", 0)
                .put("trans", 1)
                .put("trans_t", 0)
                .put("needSingingAnnotations", false)
                .put("type", 1)
                .put("songMid", songmid),
        )
        if (resp.optInt("code", -1) != 0) return null
        val data = resp.optJSONObject("data") ?: return null
        val lrc = QrcDecrypt.decryptQrcHex(data.optString("lyric", ""))
            ?.takeIf { it.isNotBlank() } ?: return null
        val tlyric = QrcDecrypt.decryptQrcHex(data.optString("trans", ""))
            ?.takeIf { it.isNotBlank() }
        val romalrc = QrcDecrypt.decryptQrcHex(data.optString("roma", ""))
            ?.takeIf { it.isNotBlank() }
        return LyricPayload(lrc = lrc, tlyric = tlyric, romalrc = romalrc)
    }

    /**
     * QQ lyrics, line level only: the web GET endpoint returns base64
     * plaintext LRC, no decryption needed (Q0 spike verified: 晴天
     * retcode 0). Now the fallback when the CGI path fails or has no
     * lyric; LRC lines feed the same line-level sweep the item-30
     * fallback uses for YRC-less songs. Works without login
     * (loginUin=0). Returns an empty payload when the server has no
     * lyrics for the song.
     */
    private suspend fun lyricViaWeb(songmid: String): LyricPayload {
        val params = listOf(
            "songmid" to songmid, "format" to "json", "nobase64" to "0",
            "g_tk" to "5381", "loginUin" to "0", "hostUin" to "0",
            "platform" to "yqq.json", "needNewCode" to "0",
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val req = Request.Builder()
            .url("${hosts.lyric}/lyric/fcgi-bin/fcg_query_lyric_new.fcg?$params")
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://y.qq.com/")
            .build()
        val root = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(ApiErrorKind.HttpStatus, httpCode = resp.code, message = "QQ 歌词获取失败")
            JSONObject(resp.body?.string() ?: "")
        }
        if (root.optInt("retcode", -1) != 0) return LyricPayload()
        val lrc = runCatching {
            String(Base64.getDecoder().decode(root.optString("lyric", "")), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val tlyric = runCatching {
            String(Base64.getDecoder().decode(root.optString("trans", "")), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return LyricPayload(lrc = lrc, tlyric = tlyric)
    }

    // ---- read APIs (sixth round, batch 2: dual-source display) ----
    //
    // Shapes are taken from the L-1124 reference source, not from memory:
    // search body keys are item_song / singer / item_songlist (NOT
    // body.song.list — the Q0 summary's shorthand was wrong);
    // recommend playlists live at data.List[*].Playlist.basic;
    // toplist detail songs at req_0.songInfoList, info at req_0.data.
    // Every parser reads defensively (several candidate keys) because
    // the server adds fields without warning; an unparseable row is
    // skipped, never fabricated.

    /** QQ search type codes (L-1124 search.py SearchType). */
    enum class QqSearchType(val code: Int) { SONG(0), SINGER(1), PLAYLIST(3) }

    /** Minimal QQ artist result (search only — QQ artist detail is out of scope this round). */
    data class QqArtist(val mid: String, val name: String, val picUrl: String)

    /**
     * 0.1.15 第 2 项：QQ 歌手详情（GetHomepageHeader + GetSingerSongList +
     * GetAlbumList 三路并行）。albums 匿名态报 104403（推测需登录凭证，
     * 走带凭证的 cgi，登录态待真机验证），失败时为空列表不报错；
     * totalSongs 是歌手全部歌曲数（GetSingerSongList 的 totalNum）。
     */
    data class QqSingerDetail(
        val name: String,
        val picUrl: String,
        val fans: Long,
        val songs: List<Song>,
        val totalSongs: Int,
        val albums: List<Album>,
    )

    /**
     * 0.1.15 第 2 项：QQ 专辑详情（GetAlbumDetail + GetAlbumSongList）。
     * 歌曲列表匿名态报 104403（同上，登录态待真机验证），失败时为空
     * 列表；singerMid 供专辑页歌手名点进 QQ 歌手页。
     */
    data class QqAlbumDetail(val album: Album, val songs: List<Song>, val singerMid: String)

    /** Album cover (300px) from an album mid. */
    fun qqAlbumCoverUrl(albumMid: String): String =
        if (albumMid.isBlank()) "" else "https://y.qq.com/music/photo_new/T002R300x300M000${albumMid}_1.jpg"

    /** Singer avatar (300px) from a singer mid. */
    fun qqSingerCoverUrl(singerMid: String): String =
        if (singerMid.isBlank()) "" else "https://y.qq.com/music/photo_new/T001R300x300M000${singerMid}_1.jpg"

    /** One QQ Song object (base Song model: songname/songmid, singer[], album, interval in seconds). */
    internal fun parseQqSong(obj: JSONObject): Song? {
        val mid = obj.optString("songmid", "").ifBlank { obj.optString("mid", "") }
        if (mid.isBlank()) return null
        val name = firstString(obj, "songname", "name", "title").orEmpty()
        val artists = obj.optJSONArray("singer")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val s = arr.optJSONObject(i) ?: return@mapNotNull null
                val n = s.optString("name", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Artist(s.optLong("id", 0), n, mid = s.optString("mid", ""))
            }
        }.orEmpty()
        val albumObj = obj.optJSONObject("album")
        val albumMid = albumObj?.optString("mid", "").orEmpty()
        return Song(
            id = qqSongId(mid),
            name = name,
            artists = artists,
            albumName = albumObj?.optString("name", "").orEmpty(),
            picUrl = qqAlbumCoverUrl(albumMid),
            durationMs = obj.optInt("interval", 0) * 1000L,
            source = MusicSource.QQ,
            sourceId = mid,
        )
    }

    /** One QQ playlist object (dissname/dissid; cover at cover.default_url for recommendations).
     * 0.1.14 第 9 条：推荐歌单的 basic 里歌单 id 键是 tid，不是 dissid/
     * id——L-1124 的 SongList.id 接受 id/tid/dissid 三个别名，之前
     * 只认前两个，25 条推荐歌单全被丢弃（dissid=0 → null），主页 QQ
     * 歌单区一直是空的。实测确认：basic.tid=7572223780 时解析出歌单。 */
    internal fun parseQqPlaylist(obj: JSONObject): Playlist? {
        val dissid = obj.optLong("dissid", obj.optLong("id", obj.optLong("tid", 0)))
        if (dissid == 0L) return null
        val name = firstString(obj, "dissname", "title", "name").orEmpty()
        val cover = firstString(obj.optJSONObject("cover"), "default_url")
            ?: firstString(obj, "picurl", "cover", "imgurl", "logo")
            ?: ""
        val creator = obj.optJSONObject("creator")?.optString("nick", "").orEmpty()
        return Playlist(
            id = QqPlaylistIds.dissPlaylistId(dissid),
            name = name,
            coverUrl = cover,
            trackCount = obj.optLong("song_cnt", obj.optLong("songnum", 0)).toInt(),
            creatorName = creator,
            source = MusicSource.QQ,
            sourceId = dissid.toString(),
        )
    }

    /** searchid (L-1124 search.py getSearchID): "QQMusicSearch" + epoch seconds + random int. */
    private fun newSearchId(): String =
        "QQMusicSearch${System.currentTimeMillis() / 1000}${Random.nextInt(0, Int.MAX_VALUE)}"

    private suspend fun searchBody(keyword: String, type: QqSearchType, limit: Int, page: Int = 1): JSONObject? {
        val resp = cgi(
            module = "music.search.SearchCgiService",
            method = "DoSearchForQQMusicMobile",
            param = JSONObject()
                .put("searchid", newSearchId())
                .put("query", keyword)
                .put("search_type", type.code)
                .put("num_per_page", limit)
                .put("page_num", page)
                .put("highlight", 1)
                .put("grp", 1),
        )
        return resp.optJSONObject("data")?.optJSONObject("body")
    }

    /** QQ song search (no login needed). */
    suspend fun searchSongs(keyword: String, limit: Int = 20): List<Song> {
        val body = searchBody(keyword, QqSearchType.SONG, limit) ?: return emptyList()
        val arr = body.optJSONArray("item_song")
            ?: body.optJSONObject("song")?.optJSONArray("list")
            ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parseQqSong(arr.optJSONObject(it) ?: return@mapNotNull null) }
    }

    /** QQ artist search (no login needed). */
    suspend fun searchArtists(keyword: String, limit: Int = 10): List<QqArtist> {
        val body = searchBody(keyword, QqSearchType.SINGER, limit) ?: return emptyList()
        val arr = body.optJSONArray("singer") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val mid = o.optString("mid", "").ifBlank { o.optString("singerMid", "") }
            val name = o.optString("name", "").ifBlank { o.optString("singerName", "") }
            if (mid.isBlank() && name.isBlank()) null
            else QqArtist(mid, name, o.optString("singerPic", "").ifBlank { qqSingerCoverUrl(mid) })
        }
    }

    /** QQ playlist search (no login needed). */
    suspend fun searchPlaylists(keyword: String, limit: Int = 10): List<Playlist> {
        val body = searchBody(keyword, QqSearchType.PLAYLIST, limit) ?: return emptyList()
        val arr = body.optJSONArray("item_songlist")
            ?: body.optJSONObject("songlist")?.optJSONArray("list")
            ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parseQqPlaylist(arr.optJSONObject(it) ?: return@mapNotNull null) }
    }

    /** All QQ toplist categories, flattened in server order (no login needed). */
    suspend fun toplist(): List<Playlist> {
        val resp = cgi(module = "music.musicToplist.Toplist", method = "GetAll", param = null)
        val data = resp.optJSONObject("data") ?: return emptyList()
        val groups = data.optJSONArray("group") ?: return emptyList()
        val out = mutableListOf<Playlist>()
        for (i in 0 until groups.length()) {
            val list = groups.optJSONObject(i)?.optJSONArray("toplist") ?: continue
            for (j in 0 until list.length()) {
                val t = list.optJSONObject(j) ?: continue
                val topId = t.optLong("topId", 0)
                if (topId == 0L) continue
                out += Playlist(
                    id = QqPlaylistIds.toplistId(topId),
                    name = t.optString("title", ""),
                    coverUrl = t.optString("frontPicUrl", ""),
                    trackCount = t.optInt("totalNum", 0),
                    source = MusicSource.QQ,
                    sourceId = topId.toString(),
                )
            }
        }
        return out
    }

    /** One QQ toplist's title + songs (no login needed). */
    suspend fun toplistDetail(topId: Long, num: Int = 100): Pair<String, List<Song>> {
        val resp = cgi(
            module = "music.musicToplist.Toplist",
            method = "GetDetail",
            param = JSONObject().put("topId", topId).put("offset", 0).put("num", num).put("withTags", true),
        )
        val data = resp.optJSONObject("data")
        val arr = resp.optJSONArray("songInfoList")
            ?: data?.optJSONArray("songInfoList")
            ?: data?.optJSONArray("songlist")
        val songs = arr?.let { a ->
            (0 until a.length()).mapNotNull { parseQqSong(a.optJSONObject(it) ?: return@mapNotNull null) }
        }.orEmpty()
        return (data?.optString("title", "").orEmpty()) to songs
    }

    /** Recommended QQ playlists (no login needed). */
    suspend fun recommendPlaylists(from: Int = 0, size: Int = 25): List<Playlist> {
        val resp = cgi(
            module = "music.playlist.PlaylistSquare",
            method = "GetRecommendFeed",
            param = JSONObject().put("From", from).put("Size", size),
        )
        val data = resp.optJSONObject("data") ?: return emptyList()
        val arr = data.optJSONArray("List") ?: data.optJSONArray("songlists") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null
            val basic = item.optJSONObject("Playlist")?.optJSONObject("basic") ?: item
            parseQqPlaylist(basic)
        }
    }

    /** Recommended QQ new songs, type 5 = 最新 (no login needed). */
    suspend fun recommendNewSongs(type: Int = 5): List<Song> {
        val resp = cgi(
            module = "newsong.NewSongServer",
            method = "get_new_song_info",
            param = JSONObject().put("type", type),
        )
        val data = resp.optJSONObject("data") ?: return emptyList()
        val arr = data.optJSONArray("songlist") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parseQqSong(arr.optJSONObject(it) ?: return@mapNotNull null) }
    }

    /**
     * 0.1.15 第 15 项：QQ 每日推荐（music.radioProxy.MbTrackRadioSvr /
     * get_radio_track，即 L-1124 recommend.get_guess_recommend，
     * 猜你喜欢——QQ 没有网易云式的每日推荐接口，这是最接近的每日
     * 个性化歌单）。匿名态报 code 1000（探针实测），需要登录凭证；
     * 走带凭证的 cgi，未登录时返回空列表（主页不显示该卡片），
     * 登录态是否出歌待真机验证。
     * 0.1.18 第 8 项：服务端每页只给 5 首左右（L-1124 参考实现本身
     * 只请求 num=5；GuessRecommendResponse 只有 tracks，没有 total/
     * has_more），单次调用要 20 也只回 5 首。改成分页：from=0,5,10…
     * （每页 num=5）取满 30 首（卡片就叫 Daily 30）；页空 / 本页无新歌
     * （from 不翻页时防死循环）/ 6 页封顶即停。
     */
    suspend fun recommendDaily(num: Int = 30): List<Song> {
        val pages = mutableListOf<List<Song>>()
        var from = 0
        var prevSize = 0
        while (pages.size < 6) {
            val page = recommendDailyPage(from)
            if (page.isEmpty()) break
            pages += page
            val merged = mergeRecommendPages(pages, num)
            if (merged.size >= num) return merged
            if (merged.size == prevSize) break // 本页没带来新歌：from 没翻页，停。
            prevSize = merged.size
            from += 5
        }
        return mergeRecommendPages(pages, num)
    }

    /** 每日推荐单页：from 是翻页偏移，num 固定 5（L-1124 同款）。 */
    private suspend fun recommendDailyPage(from: Int): List<Song> {
        val resp = runCatching {
            cgi(
                module = "music.radioProxy.MbTrackRadioSvr",
                method = "get_radio_track",
                param = JSONObject().put("id", 99).put("num", 5).put("from", from).put("scene", 0)
                    .put("song_ids", JSONArray()),
            )
        }.getOrNull() ?: return emptyList()
        if (resp.optInt("code", -1) != 0) return emptyList()
        val arr = resp.optJSONObject("data")?.optJSONArray("tracks") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parseQqSong(arr.optJSONObject(it) ?: return@mapNotNull null) }
    }

    /**
     * 0.1.15 第 15 项：QQ 私人雷达（music.recommend.TrackRelationServer /
     * GetRadarSong，即 L-1124 recommend.get_radar_recommend）。匿名
     * 可用（探针实测 code 0、VecSongs 10 条）；FavSongs 为空时按热门
     * 推荐出歌。响应里的 Track 与 parseQqSong 的形状一致（mid / name /
     * singer[含 mid] / album / interval）。
     * 0.1.18 第 8 项：请求里根本没传数量，L-1124 用 PageStrategy +
     * has_more 翻页；之前只取第 1 页（登录态解析后剩 5 首）。改成分页：
     * Page=1,2,… 取满 20 首或 has_more 为 false；页空 / 本页无新歌 /
     * 5 页封顶即停。HasMore 缺字段时按 true 处理（L-1124 模型里它是
     * 必填字段，缺失属异常；继续翻到封顶比第一页就停更稳妥）。
     */
    suspend fun recommendRadar(num: Int = 20): List<Song> {
        val pages = mutableListOf<List<Song>>()
        var pageNo = 1
        var prevSize = 0
        while (pageNo <= 5) {
            val (page, hasMore) = recommendRadarPage(pageNo)
            if (page.isEmpty()) break
            pages += page
            val merged = mergeRecommendPages(pages, num)
            if (merged.size >= num) return merged
            if (merged.size == prevSize) break // 本页没带来新歌，停。
            prevSize = merged.size
            if (!hasMore) break
            pageNo++
        }
        return mergeRecommendPages(pages, num)
    }

    /** 雷达推荐单页：返回（歌曲，HasMore）。 */
    private suspend fun recommendRadarPage(page: Int): Pair<List<Song>, Boolean> {
        val resp = runCatching {
            cgi(
                module = "music.recommend.TrackRelationServer",
                method = "GetRadarSong",
                param = JSONObject().put("Page", page).put("ReqType", 0)
                    .put("FavSongs", JSONArray()).put("EntranceSongs", JSONArray()),
            )
        }.getOrNull() ?: return emptyList<Song>() to false
        if (resp.optInt("code", -1) != 0) return emptyList<Song>() to false
        val data = resp.optJSONObject("data") ?: return emptyList<Song>() to false
        val arr = data.optJSONArray("VecSongs") ?: return emptyList<Song>() to false
        val songs = (0 until arr.length()).mapNotNull { i ->
            val track = arr.optJSONObject(i)?.optJSONObject("Track") ?: return@mapNotNull null
            parseQqSong(track)
        }
        return songs to data.optBoolean("HasMore", true)
    }

    /**
     * 0.1.15 第 2 项：QQ 歌手详情。三路并行：歌手头
     * （GetHomepageHeader，匿名 code 0）、歌曲列表
     * （GetSingerSongList，匿名 code 0，songList 元素的歌在
     * songInfo 子对象里）、专辑列表（GetAlbumList，匿名报
     * 104403，登录态待真机验证）。歌手头失败返回 null；歌曲/
     * 专辑失败只影响对应列表。
     */
    suspend fun qqSingerDetail(mid: String, songNum: Int = 50, albumNum: Int = 10): QqSingerDetail? {
        val header = runCatching {
            cgi(
                module = "music.UnifiedHomepage.UnifiedHomepageSrv",
                method = "GetHomepageHeader",
                param = JSONObject().put("SingerMid", mid),
            )
        }.getOrNull() ?: return null
        if (header.optInt("code", -1) != 0) return null
        val info = header.optJSONObject("data")?.optJSONObject("Info") ?: return null
        val singer = info.optJSONObject("Singer")
        val base = info.optJSONObject("BaseInfo")
        val name = firstString(singer, "Name") ?: firstString(base, "Name") ?: return null
        val picUrl = firstString(base, "Avatar")?.takeIf { it.isNotBlank() } ?: qqSingerCoverUrl(mid)
        val fans = info.optJSONObject("FansNum")?.optLong("Num", 0) ?: 0
        val songsResp = runCatching {
            cgi(
                module = "musichall.song_list_server",
                method = "GetSingerSongList",
                param = JSONObject().put("singerMid", mid).put("order", 1).put("number", songNum).put("begin", 0),
            )
        }.getOrNull()
        val songsData = songsResp?.takeIf { it.optInt("code", -1) == 0 }?.optJSONObject("data")
        val totalSongs = songsData?.optInt("totalNum", 0) ?: 0
        val songs = songsData?.optJSONArray("songList")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                parseQqSong(item.optJSONObject("songInfo") ?: item)
            }
        }.orEmpty()
        val albumsResp = runCatching {
            cgi(
                module = "music.musichallAlbum.AlbumListServer",
                method = "GetAlbumList",
                param = JSONObject().put("singerMid", mid).put("order", 1).put("number", albumNum).put("begin", 0),
            )
        }.getOrNull()
        val albums = albumsResp?.takeIf { it.optInt("code", -1) == 0 }?.optJSONObject("data")
            ?.optJSONArray("albumList")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> parseQqAlbum(arr.optJSONObject(i) ?: return@mapNotNull null) }
            }.orEmpty()
        return QqSingerDetail(name, picUrl, fans, songs, totalSongs, albums)
    }

    /**
     * 0.1.15 第 2 项：QQ 专辑详情。专辑头（GetAlbumDetail，匿名
     * code 0）+ 歌曲列表（GetAlbumSongList，匿名报 104403，
     * 登录态待真机验证；songList 元素的歌在 songInfo 子对象里）。
     * 专辑头失败返回 null；歌曲列表失败只留空。
     */
    suspend fun qqAlbumDetail(mid: String, num: Int = 50): QqAlbumDetail? {
        val resp = runCatching {
            cgi(
                module = "music.musichallAlbum.AlbumInfoServer",
                method = "GetAlbumDetail",
                param = JSONObject().put("albumMId", mid),
            )
        }.getOrNull() ?: return null
        if (resp.optInt("code", -1) != 0) return null
        val basic = resp.optJSONObject("data")?.optJSONObject("basicInfo") ?: return null
        val albumName = firstString(basic, "albumName") ?: return null
        val singerObj = resp.optJSONObject("data")?.optJSONObject("singer")
            ?.optJSONArray("singerList")?.optJSONObject(0)
        val singerName = singerObj?.optString("name", "").orEmpty()
        val singerMid = singerObj?.optString("mid", "").orEmpty()
        val albumId = basic.optLong("albumID", basic.optLong("albumId", 0))
        val album = Album(
            id = albumId,
            name = albumName,
            coverUrl = qqAlbumCoverUrl(mid),
            artistText = singerName,
            trackCount = 0,
            mid = mid,
        )
        val songsResp = runCatching {
            cgi(
                module = "music.musichallAlbum.AlbumSongList",
                method = "GetAlbumSongList",
                param = JSONObject().put("albumMid", mid).put("begin", 0).put("num", num),
            )
        }.getOrNull()
        val songsData = songsResp?.takeIf { it.optInt("code", -1) == 0 }?.optJSONObject("data")
        val songs = songsData?.optJSONArray("songList")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                parseQqSong(item.optJSONObject("songInfo") ?: item)
            }
        }.orEmpty()
        return QqAlbumDetail(album.copy(trackCount = songsData?.optInt("totalNum", 0)?.takeIf { it > 0 } ?: songs.size), songs, singerMid)
    }

    /** One QQ album brief (L-1124 models/singer.py AlbumBrief：albumID / albumMid / albumName / total_num）。 */
    internal fun parseQqAlbum(obj: JSONObject): Album? {
        val mid = firstString(obj, "albumMid", "mid").orEmpty()
        val name = firstString(obj, "albumName", "name").orEmpty()
        if (mid.isBlank() && name.isBlank()) return null
        return Album(
            id = obj.optLong("albumID", obj.optLong("albumId", obj.optLong("id", 0))),
            name = name,
            coverUrl = firstString(obj, "pic", "cover", "imgurl").orEmpty().ifBlank { qqAlbumCoverUrl(mid) },
            artistText = firstString(obj, "singer_name", "singerName").orEmpty(),
            trackCount = obj.optInt("total_num", obj.optInt("totalNum", 0)),
            mid = mid,
        )
    }

    /** One QQ playlist's info + songs (no login needed; first page only). */
    suspend fun playlistDetail(dissid: Long, num: Int = 100): Pair<Playlist, List<Song>> {
        val resp = cgi(
            module = "music.srfDissInfo.DissInfo",
            method = "CgiGetDiss",
            param = JSONObject()
                .put("disstid", dissid).put("dirid", 0).put("tag", true)
                .put("song_begin", 0).put("song_num", num).put("userinfo", true)
                .put("orderlist", true).put("onlysonglist", false),
        )
        val data = resp.optJSONObject("data")
            ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ 歌单详情为空")
        val info = data.optJSONObject("dirinfo") ?: data
        val playlist = Playlist(
            id = QqPlaylistIds.dissPlaylistId(dissid),
            name = firstString(info, "dissname", "title", "name").orEmpty(),
            coverUrl = firstString(info, "picurl", "cover", "imgurl", "logo").orEmpty(),
            trackCount = data.optLong("total_song_num", 0).toInt(),
            creatorName = info.optJSONObject("creator")?.optString("nick", "").orEmpty(),
            source = MusicSource.QQ,
            sourceId = dissid.toString(),
        )
        val arr = data.optJSONArray("songlist")
        val songs = arr?.let { a ->
            (0 until a.length()).mapNotNull { parseQqSong(a.optJSONObject(it) ?: return@mapNotNull null) }
        }.orEmpty()
        return playlist to songs
    }

    // ---- QQ library sync (0.1.15 第 2 项补做) ----
    //
    // euin（加密 UIN）结论（L-1124 源码核实，非记忆）：
    // - L-1124 本版没有 get_euin 方法（credential.md 的例子引用
    //   的方法并不存在）；测试夹具直接从环境变量注入 ENCRYPT_UIN。
    //   euin 不能由 musicid+musickey 本地算出来，必须来自服务端：
    //   ① QQLogin 响应 data.encryptUin（L-1124 Credential 模型
    //   声明了 encryptUin 字段，_validate_result 原样透传响应，
    //   有就收下）——登录时 [credentialFromLoginResp] 落库；
    //   ② GetLoginUserInfo 响应（L-1124 不建模，只验 code）——
    //   [getLoginUserInfo] 防御性地读；
    //   ③ GetHomepageHeader 探针：响应 data.Info.BaseInfo.
    //   EncryptedUin（UserHomepageBaseInfo），L-1124 调它时传的
    //   uin 参数就是 euin；明文 UIN 能不能当 uin 传、服务端会不
    //   会回加密值，源码无法证实——只在前两路都落空时试一次，
    //   成败都写进 NOTES，真机验证。
    // - 自建歌单 GetPlaylistByUin 只需明文 UIN + 凭证，不用 euin，
    //   所以即使 euin 拿不到，自建歌单仍能同步。
    // - 四个端点（模块 / 方法 / 参数）均取自 L-1124 modules/user.py：
    //   GetPlaylistByUin / CgiGetPlaylistFavInfo / CgiGetDiss
    //   （disstid 0 + dirid 201 + enc_host_uin）/
    //   GetFollowSingerList（HostUin + From/Size）。
    // - 所有函数失败时返回空列表、不抛异常（资料库页不能因为
    //   QQ 接口失败就崩）；未登录时不发请求。

    /** QQ 资料库三件套（歌单 / 喜欢的歌曲 / 关注歌手）。 */
    data class QqLibrary(
        val playlists: List<Playlist> = emptyList(),
        val favSongs: List<Song> = emptyList(),
        val followedArtists: List<Artist> = emptyList(),
    )

    /** 解析 euin（带内存缓存，只探一次）：已存的优先；没有就走
     * GetLoginUserInfo 再读一次；仍没有就用明文 UIN 探一次
     * GetHomepageHeader（见上，未证实）。全失败返回 null；未登录
     * 直接返回 null，不发请求。 */
    suspend fun resolveEuin(): String? {
        if (euinCacheTried) return euinCache
        euinCacheTried = true
        euinCache = resolveEuinUncached()
        return euinCache
    }

    private suspend fun resolveEuinUncached(): String? {
        if (storedCredential() == null) return null
        store.qqEncryptUin.takeIf { it.isNotBlank() }?.let { return it }
        runCatching { getLoginUserInfo() }
        store.qqEncryptUin.takeIf { it.isNotBlank() }?.let { return it }
        val musicId = store.qqMusicId
        val resp = runCatching {
            cgi(
                module = "music.UnifiedHomepage.UnifiedHomepageSrv",
                method = "GetHomepageHeader",
                param = JSONObject().put("uin", musicId).put("IsQueryTabDetail", 1),
            )
        }.getOrNull() ?: return null
        if (resp.optInt("code", -1) != 0) return null
        val base = resp.optJSONObject("data")?.optJSONObject("Info")?.optJSONObject("BaseInfo")
        val euin = firstString(base, "EncryptedUin", "EncryptUin", "encrypted_uin", "encryptUin")
            ?.takeIf { it.isNotBlank() } ?: return null
        store.qqEncryptUin = euin
        return euin
    }

    /** One QQ user playlist (self-created via GetPlaylistByUin or
     * favourited via CgiGetPlaylistFavInfo — both are SongList shapes:
     * id/tid/dissid, title/dissname/name, picurl/cover/logo, songnum).
     * [subscribed] decides the library group (self-created vs
     * favourited); creator comes from the item's own uin/nick. */
    internal fun parseQqUserPlaylist(obj: JSONObject, subscribed: Boolean): Playlist? {
        val dissid = obj.optLong("dissid", obj.optLong("id", obj.optLong("tid", 0)))
        if (dissid == 0L) return null
        val name = firstString(obj, "title", "dissname", "name", "dirName").orEmpty()
        val cover = firstString(obj.optJSONObject("cover"), "default_url")
            ?: firstString(obj, "picurl", "cover", "logo", "picUrl")
            ?: ""
        val creatorObj = obj.optJSONObject("creator")
        val creatorName = creatorObj?.optString("nick", "").orEmpty().takeIf { it.isNotBlank() }
            ?: firstString(obj, "nick", "nickname").orEmpty()
        val creatorId = creatorObj?.optString("uin", "")?.toLongOrNull()
            ?: obj.optString("uin", "").toLongOrNull()
            ?: 0L
        return Playlist(
            id = QqPlaylistIds.dissPlaylistId(dissid),
            name = name,
            coverUrl = cover,
            trackCount = obj.optLong("songnum", obj.optLong("songNum", obj.optLong("song_cnt", 0))).toInt(),
            creatorName = creatorName,
            creatorId = creatorId,
            subscribed = subscribed,
            source = MusicSource.QQ,
            sourceId = dissid.toString(),
        )
    }

    /** QQ 歌单：自建（GetPlaylistByUin，明文 UIN + 凭证，不用 euin）
     * + 收藏（CgiGetPlaylistFavInfo，需 euin）。未登录返回空列表。 */
    suspend fun qqLibraryPlaylists(num: Int = 50): List<Playlist> {
        val credential = storedCredential() ?: return emptyList()
        val out = mutableListOf<Playlist>()
        runCatching {
            val resp = cgi(
                module = "music.musicasset.PlaylistBaseRead",
                method = "GetPlaylistByUin",
                param = JSONObject().put("uin", credential.musicId),
                credential = credential,
            )
            if (resp.optInt("code", -1) == 0) {
                val arr = resp.optJSONObject("data")?.optJSONArray("v_playlist")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        parseQqUserPlaylist(arr.optJSONObject(i) ?: continue, subscribed = false)?.let { out += it }
                    }
                }
            }
        }
        val euin = resolveEuin() ?: return out
        runCatching {
            val resp = cgi(
                module = "music.musicasset.PlaylistFavRead",
                method = "CgiGetPlaylistFavInfo",
                param = JSONObject().put("uin", euin).put("offset", 0).put("size", num),
                credential = credential,
            )
            if (resp.optInt("code", -1) == 0) {
                val arr = resp.optJSONObject("data")?.optJSONArray("v_list")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        parseQqUserPlaylist(arr.optJSONObject(i) ?: continue, subscribed = true)?.let { out += it }
                    }
                }
            }
        }
        return out
    }

    /** QQ 喜欢的歌曲：CgiGetDiss（disstid 0 + dirid 201 + enc_host_uin）。
     * 响应形状与歌单详情相同（data.songlist），复用 [parseQqSong]。
     * 无 euin 时返回空列表（不发请求）。 */
    suspend fun qqFavSongs(num: Int = 100): List<Song> {
        val credential = storedCredential() ?: return emptyList()
        val euin = resolveEuin() ?: return emptyList()
        val resp = runCatching {
            cgi(
                module = "music.srfDissInfo.DissInfo",
                method = "CgiGetDiss",
                param = JSONObject()
                    .put("disstid", 0).put("dirid", 201).put("tag", true)
                    .put("song_begin", 0).put("song_num", num).put("userinfo", true)
                    .put("orderlist", true).put("enc_host_uin", euin),
                credential = credential,
            )
        }.getOrNull() ?: return emptyList()
        if (resp.optInt("code", -1) != 0) return emptyList()
        val arr = resp.optJSONObject("data")?.optJSONArray("songlist") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parseQqSong(arr.optJSONObject(it) ?: return@mapNotNull null) }
    }

    /** QQ 关注的歌手：GetFollowSingerList（HostUin + From/Size）。
     * 无 euin 时返回空列表（不发请求）。 */
    suspend fun qqFollowSingers(num: Int = 50): List<Artist> {
        val credential = storedCredential() ?: return emptyList()
        val euin = resolveEuin() ?: return emptyList()
        val resp = runCatching {
            cgi(
                module = "music.concern.RelationList",
                method = "GetFollowSingerList",
                param = JSONObject().put("HostUin", euin).put("From", 0).put("Size", num),
                credential = credential,
            )
        }.getOrNull() ?: return emptyList()
        if (resp.optInt("code", -1) != 0) return emptyList()
        val arr = resp.optJSONObject("data")?.optJSONArray("List") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val mid = o.optString("MID", "").ifBlank { o.optString("mid", "") }
            val name = o.optString("Name", "").ifBlank { o.optString("name", "") }
            if (mid.isBlank() && name.isBlank()) null
            else Artist(
                qqArtistId(mid),
                name,
                picUrl = firstString(o, "AvatarUrl", "avatarUrl", "picUrl").orEmpty(),
                mid = mid,
            )
        }
    }

    /** QQ 资料库三件套一次取齐（各自失败只留空，不连累其他两路）。 */
    suspend fun qqLibrary(): QqLibrary {
        if (storedCredential() == null) return QqLibrary()
        return QqLibrary(
            playlists = qqLibraryPlaylists(),
            favSongs = qqFavSongs(),
            followedArtists = qqFollowSingers(),
        )
    }

    /** GetCdnDispatch (L-1124 song.py): CDN roots for joining with purl. */
    private suspend fun cdnDispatch(): List<String> {
        cachedSip?.let { return it }
        val list = runCatching {
            val resp = cgi(
                module = "music.audioCdnDispatch.cdnDispatch",
                method = "GetCdnDispatch",
                param = JSONObject()
                    .put("guid", store.qqDeviceId)
                    .put("uid", "0")
                    .put("use_new_domain", 1)
                    .put("use_ipv6", 1),
            )
            val sip = resp.optJSONObject("data")?.optJSONArray("sip")
                ?: return@runCatching emptyList<String>()
            (0 until sip.length()).mapNotNull { sip.optString(it).takeIf { s -> s.isNotBlank() } }
        }.getOrDefault(emptyList())
        cachedSip = list
        return list
    }

    private fun joinUrl(domain: String, purl: String): String =
        domain.trimEnd('/') + "/" + purl.trimStart('/')

    /** Clears only the QQ credential (logout clears one source at a time). */
    fun clearSession() {
        store.qqMusicId = ""
        store.qqMusicKey = ""
        store.qqEncryptUin = ""
        euinCacheTried = false
        euinCache = null
        cachedSession = null
    }

    // ---- CGI plumbing ----

    /** One musicu.fcg call; returns the req_0 object (caller reads code/data). */
    private suspend fun cgiRaw(
        module: String,
        method: String,
        param: JSONObject?,
        commExtra: Map<String, Any?> = emptyMap(),
        credential: QqCredential? = null,
        session: QqSession? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject()
        body.put("comm", buildComm(commExtra, credential, session))
        val req0 = JSONObject()
        req0.put("module", module)
        req0.put("method", method)
        if (param != null) req0.put("param", param)
        body.put("req_0", req0)
        val reqBuilder = Request.Builder()
            .url("${hosts.musicu}/cgi-bin/musicu.fcg")
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", CGI_UA)
        if (credential != null) reqBuilder.header("Cookie", credentialCookies(credential))
        val root = http.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(ApiErrorKind.HttpStatus, httpCode = resp.code, message = "QQ 请求失败")
            JSONObject(resp.body?.string() ?: "")
        }
        root.optJSONObject("req_0")
            ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ 响应缺少 req_0")
    }

    /** ANDROID comm (L-1124 core/versioning.py build_comm, ANDROID branch). */
    private fun buildComm(
        extra: Map<String, Any?>,
        credential: QqCredential?,
        session: QqSession?,
    ): JSONObject {
        val uidPart = credential?.musicId?.takeIf { it.isNotBlank() } ?: store.qqDeviceId
        val comm = JSONObject()
        comm.put("ct", CT)
        comm.put("cv", CV)
        comm.put("v", APP_VER)
        comm.put("chid", "10003505")
        comm.put("tmeAppID", "qqmusic")
        if (credential != null) {
            comm.put("qq", credential.musicId)
            comm.put("authst", credential.musicKey)
        }
        comm.put("traceid", "10002_${uidPart}_${System.currentTimeMillis() / 1000}")
        // 第六轮第 1 项：不移植 QIMEI；L-1124 无 QIMEI 缓存时同样传空串。
        comm.put("QIMEI36", "")
        comm.put("OpenUDID", store.qqDeviceId)
        comm.put("udid", store.qqDeviceId)
        comm.put("OpenUDID2", store.qqDeviceId2)
        comm.put("aid", store.qqAndroidId)
        comm.put("os_ver", OS_VER)
        comm.put("phonetype", PHONE_TYPE)
        if (session != null) {
            comm.put("uid", session.uid)
            comm.put("sid", session.sid)
        }
        for ((k, v) in extra) if (v != null) comm.put(k, v)
        return comm
    }

    /** Credential cookies (L-1124 core/executor.py: uin / qqmusic_uin / qm_keyst / qqmusic_key). */
    private fun credentialCookies(credential: QqCredential): String {
        val uin = credential.strMusicId.ifBlank { credential.musicId }
        return "uin=$uin; qqmusic_uin=$uin; qm_keyst=${credential.musicKey}; qqmusic_key=${credential.musicKey}"
    }

    private fun firstString(obj: JSONObject?, vararg keys: String): String? {
        if (obj == null) return null
        for (k in keys) {
            val v = obj.optString(k, "")
            if (v.isNotBlank()) return v
        }
        return null
    }
}
