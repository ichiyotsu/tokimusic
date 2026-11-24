package com.tokimusic.app.data.api

import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Playlist
import com.tokimusic.app.data.model.QqPlaylistIds
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.data.model.qqSongId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
    val ptlogin: String = "https://ssl.ptlogin2.qq.com",
    val graphPtlogin: String = "https://ssl.ptlogin2.graph.qq.com",
    val graph: String = "https://graph.qq.com",
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
    var qqFirstLoginMs: Long
    var neteaseEnabled: Boolean
    var qqEnabled: Boolean
}

/** QQ login credential (musicid / musickey), persisted in [QqSettings]. */
data class QqCredential(val musicId: String, val musicKey: String, val strMusicId: String = "")

/** QQ profile (from GetLoginUserInfo; response shape is only loosely modelled — see [QqClient.getLoginUserInfo]). */
data class QqProfile(val nickname: String, val avatarUrl: String?, val musicId: String)

/** Anonymous Android session (GetSession) — its uid/sid ride in the QQLogin comm. */
private data class QqSession(val uid: String, val sid: String)

/** check_sig result: the p_skey plus every cookie the response sets — graph
 * authorize needs the full check_sig cookie jar (L-1124 _authorize_qq_qr
 * forwards dict(payload.cookies)), not just p_skey. */
private data class CheckSigResult(val pSkey: String, val cookies: Map<String, String>)

/** QR polling outcome, mirroring the ptuiCB status codes. */
sealed interface QqQrCheck {
    /** 66: waiting for scan. */
    data object Waiting : QqQrCheck
    /** 67: scanned, waiting for confirm on the phone. */
    data object Scanned : QqQrCheck
    /** 65: QR expired — show a fresh one. */
    data object Expired : QqQrCheck
    /** 68: user refused on the phone. */
    data object Refused : QqQrCheck
    /** 0: done — [uin] and [sigx] feed the check_sig exchange. */
    data class Done(val uin: String, val sigx: String) : QqQrCheck
}

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

/** QQ hash33 (L-1124 utils/common.py): h = (h << 5) + h + ord(c), masked to 31 bits at the end. */
internal fun hash33(s: String, seed: Int = 0): Int {
    var h = seed
    for (c in s) h = (h shl 5) + h + c.code
    return 0x7FFFFFFF and h
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
        private const val QR_APPID = 716027609L
        private const val QR_DAID = 383
        private const val OAUTH_CLIENT_ID = 100497308L
        /** CGI user-agent (probe-android.js): "QQMusic 20090008(android 15)". */
        private const val CGI_UA = "QQMusic 20090008(android 15)"
        /** ptlogin user-agent (probe8b.js). */
        private const val PTLOGIN_UA = "Mozilla/5.0 (Linux; Android 15; V2408A) AppleWebKit/537.36 QQMusic/20.9.0.8"
        private const val PTLOGIN_REFERER = "https://xui.ptlogin2.qq.com/"
        private const val PHONE_TYPE = "V2408A"
        private const val OS_VER = "15"
        private const val CT = 11
        private const val CV = 20090008
        private const val APP_VER = 20090008
        /** Stream fallback domain when neither the GetVkey sip nor GetCdnDispatch returns a CDN (L-1124 song.py _SONG_URL_FALLBACK_DOMAIN). */
        private const val FALLBACK_CDN = "https://isure.stream.qqmusic.qq.com/"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Server-rendered QR PNG bytes → the same data-URI shape the NetEase login dialog already decodes. */
        fun qrImageDataUri(png: ByteArray): String =
            "data:image/png;base64," + Base64.getEncoder().encodeToString(png)
    }

    private val http = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    // ---- QR flow ----

    /** ptqrshow: returns the server-rendered QR PNG bytes plus the qrsig cookie that identifies this QR. */
    suspend fun qrShow(): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val url = "${hosts.ptlogin}/ptqrshow?appid=$QR_APPID&e=2&l=M&s=3&d=72&v=4&t=${Random.nextLong(1, 1_000_000_000)}&daid=$QR_DAID&pt_3rd_aid=$OAUTH_CLIENT_ID"
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(ApiErrorKind.HttpStatus, httpCode = resp.code, message = "QQ 二维码获取失败")
            val qrsig = resp.headers("Set-Cookie")
                .firstNotNullOfOrNull { parseCookie(it)["qrsig"] }
                ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ 二维码缺少 qrsig")
            val png = resp.body?.bytes() ?: ByteArray(0)
            if (png.isEmpty()) throw ApiException(ApiErrorKind.Unknown, message = "QQ 二维码图片为空")
            png to qrsig
        }
    }

    /** ptqrlogin polling: one poll per call; the caller repeats every ~2 s. */
    suspend fun qrCheck(qrsig: String): QqQrCheck = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val params = listOf(
            "u1" to "https://graph.qq.com/oauth2.0/login_jump",
            "ptqrtoken" to hash33(qrsig).toString(),
            "ptredirect" to "0", "h" to "1", "t" to "1", "g" to "1",
            "from_ui" to "1", "ptlang" to "2052",
            "action" to "0-0-$now",
            "js_ver" to "20102616", "js_type" to "1", "pt_uistyle" to "40",
            "aid" to QR_APPID.toString(), "daid" to QR_DAID.toString(),
            "pt_3rd_aid" to OAUTH_CLIENT_ID.toString(), "has_onekey" to "1",
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val req = Request.Builder()
            .url("${hosts.ptlogin}/ptqrlogin?$params")
            .header("User-Agent", PTLOGIN_UA)
            .header("Referer", PTLOGIN_REFERER)
            .header("Cookie", "qrsig=$qrsig")
            .build()
        val body = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(ApiErrorKind.HttpStatus, httpCode = resp.code, message = "QQ 扫码状态查询失败")
            resp.body?.string() ?: ""
        }
        // ptuiCB('66','0','','0','未失效','...') — status is args[0]; on '0'
        // args[2] is the redirect URL carrying ptsigx + uin.
        val code = Regex("""ptuiCB\('(\d+)'""").find(body)?.groupValues?.getOrNull(1)
            ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ 扫码状态无法解析")
        when (code) {
            "66" -> QqQrCheck.Waiting
            "67" -> QqQrCheck.Scanned
            "65" -> QqQrCheck.Expired
            "68" -> QqQrCheck.Refused
            "0" -> {
                val url = Regex("""ptuiCB\('0','\d*','([^']*)'""").find(body)?.groupValues?.getOrNull(1)
                    ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ 扫码成功但缺少跳转地址")
                val sigx = Regex("""ptsigx=([^&'"]+)""").find(url)?.groupValues?.getOrNull(1)
                    ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ 扫码成功但缺少 ptsigx")
                val uin = Regex("""uin=([^&'"]+)""").find(url)?.groupValues?.getOrNull(1)
                    ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ 扫码成功但缺少 uin")
                QqQrCheck.Done(uin, sigx)
            }
            else -> throw ApiException(ApiErrorKind.Unknown, message = "QQ 扫码状态未知（$code）")
        }
    }

    /**
     * Full QR exchange after [QqQrCheck.Done]: check_sig (p_skey) →
     * graph authorize (code) → anonymous GetSession (uid/sid for the
     * QQLogin comm, best effort) → QQLogin CGI (musicid/musickey).
     * Persists the credential into [QqSettings]; the caller sets
     * qqEnabled / qqFirstLoginMs.
     */
    suspend fun exchangeQrLogin(uin: String, sigx: String): QqCredential = withContext(Dispatchers.IO) {
        val checkSigResult = checkSig(uin, sigx)
        val code = authorize(checkSigResult.pSkey, checkSigResult.cookies)
        val session = runCatching { getSession() }.getOrNull()
        val resp = cgi(
            module = "QQConnectLogin.LoginServer",
            method = "QQLogin",
            param = JSONObject().put("code", code),
            commExtra = mapOf("tmeLoginType" to 2),
            session = session,
        )
        val data = resp.optJSONObject("data") ?: resp
        val musicId = data.opt("musicid")?.toString().orEmpty()
        val musicKey = data.optString("musickey", "")
        if (musicId.isBlank() || musicKey.isBlank()) {
            throw ApiException(ApiErrorKind.Unknown, message = "QQ 登录失败：服务端未返回凭证")
        }
        val credential = QqCredential(musicId, musicKey, data.optString("str_musicid", musicId))
        store.qqMusicId = credential.musicId
        store.qqMusicKey = credential.musicKey
        credential
    }

    /** check_sig: exchanges ptsigx for the p_skey cookie (no redirects — the 302 carries the cookie).
     * Returns the p_skey plus every cookie the response sets: graph authorize
     * needs the full check_sig cookie jar (L-1124 _authorize_qq_qr forwards
     * dict(payload.cookies)), not just p_skey — sending only p_skey is what
     * made authorize return no code on a real device (0.1.10 feedback). */
    private suspend fun checkSig(uin: String, sigx: String): CheckSigResult = withContext(Dispatchers.IO) {
        val params = listOf(
            "pttype" to "1", "uin" to uin, "service" to "ptqrlogin", "nodirect" to "0",
            "ptsigx" to sigx, "s_url" to "https://graph.qq.com/oauth2.0/login_jump",
            "ptlang" to "2052", "ptredirect" to "100",
            "aid" to QR_APPID.toString(), "daid" to QR_DAID.toString(),
            "j_later" to "0", "low_login_hour" to "0", "regmaster" to "0",
            "pt_login_type" to "3", "pt_aid" to "0", "pt_aaid" to "16",
            "pt_light" to "0", "pt_3rd_aid" to OAUTH_CLIENT_ID.toString(),
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val req = Request.Builder().url("${hosts.graphPtlogin}/check_sig?$params")
            .header("Referer", PTLOGIN_REFERER)
            .build()
        val cookies = http.newCall(req).execute().use { resp ->
            resp.headers("Set-Cookie").fold(mutableMapOf<String, String>()) { acc, header ->
                parseCookie(header).forEach { (k, v) -> acc[k] = v }
                acc
            }
        }
        val pSkey = cookies["p_skey"]
            ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ 登录失败：check_sig 未返回 p_skey")
        CheckSigResult(pSkey, cookies)
    }

    /** graph authorize: POSTs the OAuth form; the 302 Location header carries the code.
     * Sends the full check_sig cookie jar (not just p_skey); g_tk is still
     * computed from p_skey alone. The failure message carries the HTTP status
     * and whether a Location header came back, so the next failure is
     * diagnosable from the toast alone. */
    private suspend fun authorize(pSkey: String, cookies: Map<String, String>): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("response_type", "code")
            .add("client_id", OAUTH_CLIENT_ID.toString())
            .add("redirect_uri", "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/")
            .add("scope", "get_user_info,get_app_friends")
            .add("state", "state")
            .add("switch", "")
            .add("from_ptlogin", "1")
            .add("src", "1")
            .add("update_auth", "1")
            .add("openapi", "1010_1030")
            .add("g_tk", hash33(pSkey, 5381).toString())
            .add("auth_time", System.currentTimeMillis().toString())
            .add("ui", java.util.UUID.randomUUID().toString())
            .build()
        val req = Request.Builder()
            .url("${hosts.graph}/oauth2.0/authorize")
            .post(form)
            .header("Cookie", cookies.entries.joinToString("; ") { (k, v) -> "$k=$v" })
            .build()
        val (httpCode, location) = http.newCall(req).execute().use { resp ->
            resp.code to (resp.header("Location") ?: "")
        }
        Regex("""code=([^&]+)""").find(location)?.groupValues?.getOrNull(1)
            ?: throw ApiException(
                ApiErrorKind.Unknown,
                message = "QQ 登录失败：authorize 未返回 code（HTTP $httpCode/${if (location.isBlank()) "无 Location" else "有 Location"}）",
            )
    }

    // ---- session / profile ----

    /** Anonymous GetSession (spike-verified): returns the uid/sid the QQLogin comm carries. */
    private suspend fun getSession(): QqSession {
        val resp = cgi(
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
        return QqProfile(nickname, avatar?.takeIf { it.isNotBlank() }, musicId)
    }

    // ---- playback (sixth round, QQ playback) ----

    /** In-memory CDN list from GetCdnDispatch (one call per client lifetime). */
    private var cachedSip: List<String>? = null

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
     * QQ lyrics, line level only: the web GET endpoint returns base64
     * plaintext LRC, no decryption needed (Q0 spike verified: 晴天
     * retcode 0). QRC word-level lyrics need the custom Triple-DES port
     * — not this round; LRC lines feed the same line-level sweep the
     * item-30 fallback uses for YRC-less songs. Works without login
     * (loginUin=0). Returns an empty payload when the server has no
     * lyrics for the song.
     */
    suspend fun lyric(songmid: String): LyricPayload = withContext(Dispatchers.IO) {
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
        if (root.optInt("retcode", -1) != 0) return@withContext LyricPayload()
        val lrc = runCatching {
            String(Base64.getDecoder().decode(root.optString("lyric", "")), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }
        LyricPayload(lrc = lrc)
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
                Artist(s.optLong("id", 0), n)
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

    /** One QQ playlist object (dissname/dissid; cover at cover.default_url for recommendations). */
    internal fun parseQqPlaylist(obj: JSONObject): Playlist? {
        val dissid = obj.optLong("dissid", obj.optLong("id", 0))
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
    }

    // ---- CGI plumbing ----

    /** One musicu.fcg call; returns the req_0 object (caller reads code/data). */
    private suspend fun cgi(
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

    private fun parseCookie(setCookie: String): Map<String, String> {
        val kv = setCookie.split(";").firstOrNull()?.trim()?.split("=", limit = 2) ?: return emptyMap()
        if (kv.size != 2 || kv[0].isBlank()) return emptyMap()
        return mapOf(kv[0].trim() to kv[1].trim())
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
