package com.tokimusic.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        val pSkey = checkSig(uin, sigx)
        val code = authorize(pSkey)
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

    /** check_sig: exchanges ptsigx for the p_skey cookie (no redirects — the 302 carries the cookie). */
    private suspend fun checkSig(uin: String, sigx: String): String = withContext(Dispatchers.IO) {
        val params = listOf(
            "pttype" to "1", "uin" to uin, "service" to "ptqrlogin", "nodirect" to "0",
            "ptsigx" to sigx, "s_url" to "https://graph.qq.com/oauth2.0/login_jump",
            "ptlang" to "2052", "ptredirect" to "100",
            "aid" to QR_APPID.toString(), "daid" to QR_DAID.toString(),
            "j_later" to "0", "low_login_hour" to "0", "regmaster" to "0",
            "pt_login_type" to "3", "pt_aid" to "0", "pt_aaid" to "16",
            "pt_light" to "0", "pt_3rd_aid" to OAUTH_CLIENT_ID.toString(),
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val req = Request.Builder().url("${hosts.graphPtlogin}/check_sig?$params").build()
        val pSkey = http.newCall(req).execute().use { resp ->
            resp.headers("Set-Cookie").firstNotNullOfOrNull { parseCookie(it)["p_skey"] }
        } ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ 登录失败：check_sig 未返回 p_skey")
        pSkey
    }

    /** graph authorize: POSTs the OAuth form; the 302 Location header carries the code. */
    private suspend fun authorize(pSkey: String): String = withContext(Dispatchers.IO) {
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
            .header("Cookie", "p_skey=$pSkey")
            .build()
        val location = http.newCall(req).execute().use { resp ->
            resp.header("Location") ?: ""
        }
        Regex("""(?<=code=)(.+?)(?=&)""").find(location)?.groupValues?.getOrNull(1)
            ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ 登录失败：authorize 未返回 code")
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
