package com.tokimusic.app.data.api
import com.tokimusic.app.data.AppStrings
import com.tokimusic.app.R

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.tokimusic.app.data.ApiSettings
import com.tokimusic.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Proxy
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Result of a playback-URL lookup: the URL plus the rights info around it. */
data class SongUrlResult(val url: String?, val fee: Int = 0, val freeTrialEndSec: Int = 0) {
    /** Non-zero means only a trial snippet (that many seconds) is playable. */
    val isTrial: Boolean get() = freeTrialEndSec > 0
}

/** Direct-connection hosts; tests point both at a MockWebServer. */
data class NeteaseHosts(
    val music: String = "https://music.163.com",
    val api: String = "https://interface.music.163.com",
)

private data class DirectResponse(val body: JSONObject, val setCookies: List<StoredCookie>)
private data class StoredCookie(val name: String, val value: String, val expiresAt: Long)

/**
 * OkHttp implementation of [NeteaseApi] that talks to NetEase directly —
 * no self-hosted NeteaseCloudMusicApi server needed.
 *
 * Faithful native port of NeteaseCloudMusicApi 4.32.0 (`util/request.js` +
 * the endpoint modules): per-endpoint crypto (weapi/eapi), the exact header
 * and cookie construction the upstream server did, and the same response
 * shapes the rest of the app already parses. See docs/API.md for the
 * endpoint table and the verification story.
 *
 * Behaviours kept from the server-based client:
 * - Cookies live in a manual in-memory store (NOT an OkHttp CookieJar):
 *   the QR flow's `Set-Cookie` (notably the NMTID anonymous device id from
 *   `/login/qr/key`) is kept, and the persisted cookie is re-synced from
 *   the store after every response. The store is seeded from
 *   [ApiSettings.cookie] on launch, so a login survives restarts.
 * - The QR flow never sends the stored login cookie: a stale cookie on
 *   `/login/qr/key` / `/login/qr/check` makes a fresh scan come back
 *   "expired" (800) after the user already confirmed on the phone.
 * - Login expiry is a first-class failure: a body `code` of 301 throws
 *   [ApiException] with [ApiErrorKind.NotLoggedIn] instead of collapsing
 *   into "not logged in" or an empty page.
 * - The anonymous identity (device id + MUSIC_A) is registered lazily via
 *   `/api/register/anonimous` the first time a request needs it, exactly
 *   as the server did at startup; a failed registration never blocks the
 *   request, it just proceeds without MUSIC_A.
 */
class NeteaseClient(
    private val store: ApiSettings,
    private val hosts: NeteaseHosts = NeteaseHosts(),
) : NeteaseApi {
    companion object {
        /** Login-credential cookies: they travel only as the login cookie,
         * never as an anonymous cookie. */
        private val LOGIN_COOKIE_NAMES = setOf("MUSIC_U", "__remember_me", "__csrf")
        private val COOKIE_ATTRIBUTES = setOf("Max-Age", "Expires", "Path", "Domain", "Secure", "HttpOnly", "SameSite")
        private const val WEAPI_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"
        private const val EAPI_UA = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
        private const val PC_OSVER = "Microsoft-Windows-10-Professional-build-19045-64bit"
        private const val PC_APPVER = "3.1.17.204416"
        /**
         * 歌单订阅的 checkToken（0.1.17 第 3 项）：上游
         * NeteaseCloudMusicApi `util/config.json` 里公开的
         * APP_CONF.checkToken（每个部署都用同一个公开值，不是账号凭证）；
         * 上游 playlist_subscribe 模块订阅时带它、取消订阅不带。
         */
        private const val PLAYLIST_CHECK_TOKEN = "9ca17ae2e6ffcda170e2e6ee8af14fbabdb988f225b3868eb2c15a879b9a83d274a790ac8ff54a97b889d5d42af0feaec3b92af58cff99c470a7eafd88f75e839a9ea7c14e909da883e83fb692a3abdb6b92adee9e"
        /** Login URL the QR code encodes (upstream login_qr_create, pc platform). */
        fun qrLoginUrl(key: String): String = "https://music.163.com/login?codekey=$key"
    }

    private enum class Crypto { WEAPI, EAPI }

    /** In-memory cookie store, seeded from the persisted login cookie. */
    private val cookies = object {
        private val saved = linkedMapOf<String, StoredCookie>()
        init { seed(store.cookie) }
        fun seed(combined: String) {
            if (combined.isBlank()) return
            val now = System.currentTimeMillis()
            combined.split(";").forEach { part ->
                val kv = part.trim().split("=", limit = 2)
                if (kv.size == 2 && kv[0].isNotBlank() && kv[0].trim() !in COOKIE_ATTRIBUTES) {
                    saved[kv[0].trim()] = StoredCookie(kv[0].trim(), kv[1].trim(), now + 30L * 24 * 3600 * 1000)
                }
            }
        }
        fun merge(setCookies: List<StoredCookie>) {
            for (c in setCookies) saved[c.name] = c
        }
        fun all(): List<StoredCookie> =
            saved.values.filter { it.expiresAt >= System.currentTimeMillis() }
        fun has(name: String): Boolean = all().any { it.name == name }
        fun value(name: String): String? = all().firstOrNull { it.name == name }?.value
        fun loginValue(): String = all()
            .filter { it.name in LOGIN_COOKIE_NAMES }
            .joinToString("; ") { "${it.name}=${it.value}" }
        fun combinedValue(): String = all().joinToString("; ") { "${it.name}=${it.value}" }
        fun clear() = saved.clear()
    }

    private val http = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** WNMCID is computed once per process upstream; once per client here. */
    private val wnmcid: String = run {
        val letters = (0 until 6).map { ('a'..'z').random() }.joinToString("")
        "$letters.${System.currentTimeMillis()}.01.0"
    }
    private var anonymousAttempted = false

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
    private fun randomHexBytes(n: Int): String =
        ByteArray(n) { Random.nextInt(256).toByte() }.joinToString("") { "%02x".format(it) }

    private fun obj(vararg pairs: Pair<String, Any>): JSONObject =
        JSONObject().apply { for ((k, v) in pairs) put(k, v) }

    private fun Map<String, String>.toCookieHeader(): String =
        entries.joinToString("; ") { "${enc(it.key)}=${enc(it.value)}" }

    /**
     * Upstream `processCookieObject`: defaults + generated ids, on top of
     * the stored cookies. [skipAuthCookie] keeps anonymous cookies only.
     */
    private fun cookieContext(uri: String, skipAuthCookie: Boolean): LinkedHashMap<String, String> {
        val ctx = linkedMapOf<String, String>()
        for (c in cookies.all()) {
            if (skipAuthCookie && c.name in LOGIN_COOKIE_NAMES) continue
            ctx[c.name] = c.value
        }
        ctx["__remember_me"] = "true"
        ctx["ntes_kaola_ad"] = "1"
        val nuid = ctx["_ntes_nuid"] ?: randomHexBytes(32).also { ctx["_ntes_nuid"] = it }
        ctx["_ntes_nnid"] = ctx["_ntes_nnid"] ?: "$nuid,${System.currentTimeMillis()}"
        ctx["WNMCID"] = ctx["WNMCID"] ?: wnmcid
        ctx["WEVNSM"] = ctx["WEVNSM"] ?: "1.0.0"
        ctx["osver"] = ctx["osver"] ?: PC_OSVER
        ctx["deviceId"] = ctx["deviceId"] ?: store.deviceId
        ctx["os"] = ctx["os"] ?: "pc"
        ctx["channel"] = ctx["channel"] ?: "netease"
        ctx["appver"] = ctx["appver"] ?: PC_APPVER
        // Upstream regenerates NMTID on every non-login request.
        if (!uri.contains("login")) ctx["NMTID"] = randomHexBytes(16)
        return ctx
    }

    /** Upstream eapi header object (also becomes the eapi Cookie header). */
    private fun eapiHeader(ctx: Map<String, String>, csrf: String): LinkedHashMap<String, String> {
        val now = System.currentTimeMillis()
        val header = linkedMapOf<String, String>()
        header["osver"] = ctx["osver"] ?: PC_OSVER
        header["deviceId"] = ctx["deviceId"] ?: store.deviceId
        header["os"] = ctx["os"] ?: "pc"
        header["appver"] = ctx["appver"] ?: PC_APPVER
        header["versioncode"] = ctx["versioncode"] ?: "140"
        header["mobilename"] = ctx["mobilename"] ?: ""
        header["buildver"] = ctx["buildver"] ?: now.toString().take(10)
        header["resolution"] = ctx["resolution"] ?: "1920x1080"
        header["__csrf"] = csrf
        header["channel"] = ctx["channel"] ?: "netease"
        header["requestId"] = "${now}_${Random.nextInt(1000).toString().padStart(4, '0')}"
        ctx["MUSIC_U"]?.let { header["MUSIC_U"] = it }
        ctx["MUSIC_A"]?.let { header["MUSIC_A"] = it }
        return header
    }

    private fun syncPersisted() {
        store.cookie = cookies.combinedValue()
    }

    /** Lazy anonymous registration (upstream register_anonimous, weapi). */
    private suspend fun ensureAnonymousIfNeeded() {
        if (anonymousAttempted || cookies.has("MUSIC_U") || cookies.has("MUSIC_A")) return
        anonymousAttempted = true
        runCatching {
            val deviceId = store.deviceId
            val username = java.util.Base64.getEncoder().encodeToString(
                "$deviceId ${NeteaseCrypto.cloudmusicDllEncodeId(deviceId)}".toByteArray(Charsets.UTF_8),
            )
            val ctx = cookieContext("/api/register/anonimous", skipAuthCookie = true)
            val payload = obj("username" to username).apply {
                put("csrf_token", ctx["__csrf"] ?: "")
                put("e_r", false)
            }
            val w = NeteaseCrypto.weapi(payload.toString())
            val res = execute(
                hosts.music + "/weapi/register/anonimous",
                "params=${enc(w.params)}&encSecKey=${w.encSecKey}",
                mapOf(
                    "Referer" to hosts.music,
                    "User-Agent" to WEAPI_UA,
                    "Cookie" to ctx.toCookieHeader(),
                ),
            )
            val fromBody = res.body.optString("cookie")
            if (fromBody.isNotBlank()) cookies.seed(fromBody)
            syncPersisted()
        }
    }

    /** One encrypted POST to NetEase; returns the upstream body + Set-Cookies. */
    private suspend fun post(
        uri: String,
        data: JSONObject,
        crypto: Crypto,
        skipAuthCookie: Boolean = false,
    ): DirectResponse = withContext(Dispatchers.IO) {
        ensureAnonymousIfNeeded()
        val ctx = cookieContext(uri, skipAuthCookie)
        val csrf = ctx["__csrf"] ?: ""
        val (url, body, headers) = when (crypto) {
            Crypto.WEAPI -> {
                data.put("csrf_token", csrf)
                data.put("e_r", false)
                val w = NeteaseCrypto.weapi(data.toString())
                Triple(
                    hosts.music + "/weapi/" + uri.removePrefix("/api/"),
                    "params=${enc(w.params)}&encSecKey=${w.encSecKey}",
                    mapOf(
                        "Referer" to hosts.music,
                        "User-Agent" to WEAPI_UA,
                        "Cookie" to ctx.toCookieHeader(),
                    ),
                )
            }
            Crypto.EAPI -> {
                val header = eapiHeader(ctx, csrf)
                data.put("header", JSONObject(header))
                data.put("e_r", false)
                val e = NeteaseCrypto.eapi(uri, data.toString())
                Triple(
                    hosts.api + "/eapi/" + uri.removePrefix("/api/"),
                    "params=${enc(e.params)}",
                    mapOf(
                        "User-Agent" to EAPI_UA,
                        "Cookie" to header.toCookieHeader(),
                    ),
                )
            }
        }
        execute(url, body, headers)
    }

    private fun execute(url: String, body: String, headers: Map<String, String>): DirectResponse {
        val req = Request.Builder().url(url)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        val res = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw ApiException(ApiErrorKind.Unreachable, message = e.message)
        }
        res.use {
            val respBody = it.body?.string().orEmpty()
            val setCookies = it.headers("Set-Cookie").mapNotNull { parseSetCookie(it) }
            if (setCookies.isNotEmpty()) {
                cookies.merge(setCookies)
                syncPersisted()
            }
            if (!it.isSuccessful) {
                if (it.code == 460 || it.code == 503) throw ApiException(ApiErrorKind.RateLimited, httpCode = it.code)
                // NetEase answers a blocked route with HTTP 400 carrying body
                // code -460 (seen live from a flagged IP): that is a rate
                // limit, not a generic HTTP error — surface it as RateLimited
                // so the song-URL fallback can count it and report it honestly.
                val blocked = runCatching { JSONObject(respBody).optInt("code") == -460 }.getOrDefault(false)
                if (blocked) throw ApiException(ApiErrorKind.RateLimited, httpCode = it.code, apiCode = -460)
                throw ApiException(ApiErrorKind.HttpStatus, httpCode = it.code)
            }
            val json = try {
                JSONObject(respBody)
            } catch (e: Exception) {
                throw ApiException(ApiErrorKind.Unknown, message = AppStrings.getOrFallback(R.string.err_netease_unparsable, "网易返回了无法解析的内容"))
            }
            if (json.optInt("code") == 301) throw ApiException(ApiErrorKind.NotLoggedIn, apiCode = 301)
            return DirectResponse(json, setCookies)
        }
    }

    private fun parseSetCookie(raw: String): StoredCookie? {
        val parts = raw.split(";").map { it.trim() }
        if (parts.isEmpty()) return null
        val kv = parts[0].split("=", limit = 2)
        if (kv.size != 2 || kv[0].isBlank()) return null
        var expiresAt = System.currentTimeMillis() + 30L * 24 * 3600 * 1000
        for (attr in parts.drop(1)) {
            val akv = attr.split("=", limit = 2)
            when (akv[0].lowercase(Locale.US)) {
                "max-age" -> akv.getOrNull(1)?.toLongOrNull()?.let {
                    expiresAt = System.currentTimeMillis() + it * 1000
                }
                "expires" -> akv.getOrNull(1)?.let { date ->
                    runCatching {
                        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                            .parse(date)?.time
                    }.getOrNull()?.let { expiresAt = it }
                }
            }
        }
        return StoredCookie(kv[0].trim(), kv[1].trim(), expiresAt)
    }

    private fun JSONObject.arr(key: String): JSONArray = if (has(key) && !isNull(key)) getJSONArray(key) else JSONArray()
    private fun parseSong(o: JSONObject): Song {
        val ar = o.optJSONArray("ar") ?: o.optJSONArray("artists") ?: JSONArray()
        val artists = (0 until ar.length()).map { Artist(ar.getJSONObject(it).optLong("id"), ar.getJSONObject(it).optString("name")) }
        val al = o.optJSONObject("al") ?: o.optJSONObject("album")
        return Song(o.optLong("id"), o.optString("name"), artists, al?.optString("name") ?: "", al?.optString("picUrl") ?: "", o.optLong("dt", o.optLong("duration")), o.optInt("fee"), publishTime = o.optLong("publishTime", 0))
    }
    private fun parsePlaylist(o: JSONObject): Playlist {
        val creator = o.optJSONObject("creator")
        return Playlist(o.optLong("id"), o.optString("name"), o.optString("coverImgUrl", o.optString("picUrl")), o.optInt("trackCount"), creator?.optString("nickname") ?: "", creator?.optLong("userId") ?: 0, o.optBoolean("subscribed"), o.optLong("playCount"), o.optString("description"))
    }
    private fun parseAlbum(o: JSONObject): Album {
        val ar = o.optJSONArray("artists") ?: JSONArray()
        val artistText = (0 until ar.length()).joinToString(" / ") { ar.getJSONObject(it).optString("name") }
        return Album(o.optLong("id"), o.optString("name"), o.optString("picUrl"), artistText, o.optInt("size"))
    }

    override suspend fun qrKey(): String =
        post("/api/login/qrcode/unikey", obj("type" to 3), Crypto.EAPI, skipAuthCookie = true)
            .body.getString("unikey")

    override suspend fun qrCreate(key: String): String {
        // QR image is generated in-app now (upstream login_qr_create only
        // built this URL and rendered it with the `qrcode` npm package).
        val matrix = QRCodeWriter().encode(qrLoginUrl(key), BarcodeFormat.QR_CODE, 512, 512)
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        for (x in 0 until 512) for (y in 0 until 512) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray())
    }

    override suspend fun qrCheck(key: String): QrCheck {
        val o = post(
            "/api/login/qrcode/client/login",
            obj("key" to key, "type" to 3),
            Crypto.EAPI,
            skipAuthCookie = true,
        ).body
        val code = o.optInt("code")
        if (code == 803) {
            // The credential may arrive in the body, in Set-Cookie, or both.
            // A "success" with no credential anywhere is a failure, not a login.
            var cookie = o.optString("cookie")
            if (cookie.isBlank()) cookie = cookies.loginValue()
            if (cookie.isBlank()) throw ApiException(ApiErrorKind.Unknown, message = AppStrings.getOrFallback(R.string.err_login_no_credential, "登录成功但未拿到凭证，请重新扫码"))
            // Merge the body credential into the store so later requests carry
            // both it and the NMTID the key step set (neither may be dropped).
            if (o.optString("cookie").isNotBlank()) cookies.seed(o.optString("cookie"))
            store.cookie = cookies.combinedValue().ifBlank { cookie }
            return QrCheck(code, store.cookie, o.optString("message"))
        }
        return QrCheck(code, "", o.optString("message"))
    }

    override fun clearSession() {
        cookies.clear()
        store.cookie = ""
    }

    override suspend fun loginStatus(): UserProfile? {
        // /api/w/nuser/account/get is the authority: code 200 = logged in.
        val status = post("/api/w/nuser/account/get", obj(), Crypto.WEAPI).body
        if (status.optInt("code") != 200) throw ApiException(ApiErrorKind.NotLoggedIn, apiCode = 301)
        val profile = post("/api/nuser/account/get", obj(), Crypto.WEAPI).body.optJSONObject("profile") ?: return null
        return UserProfile(profile.optLong("userId"), profile.optString("nickname"), profile.optString("avatarUrl"))
    }

    override suspend fun refreshLogin() {
        runCatching { post("/api/login/token/refresh", obj(), Crypto.EAPI) }
    }

    override suspend fun banners(): List<BannerItem> =
        post("/api/v2/banner/get", obj("clientType" to "iphone"), Crypto.EAPI)
            .body.arr("banners").let { a -> (0 until a.length()).map { val o = a.getJSONObject(it); BannerItem(o.optString("pic"), o.optString("typeTitle"), o.optLong("targetId")) } }

    override suspend fun personalized(limit: Int): List<Playlist> =
        post("/api/personalized/playlist", obj("limit" to limit, "total" to true, "n" to 1000), Crypto.WEAPI)
            .body.arr("result").let { a -> (0 until a.length()).map { parsePlaylist(a.getJSONObject(it)) } }

    override suspend fun newSongs(): List<Song> =
        post("/api/personalized/newsong", obj("type" to "recommend", "limit" to 10, "areaId" to 0), Crypto.WEAPI)
            .body.arr("result").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it).optJSONObject("song") ?: a.getJSONObject(it)) } }

    override suspend fun dailySongs(): List<Song> = runCatching {
        post("/api/v3/discovery/recommend/songs", obj(), Crypto.WEAPI)
            .body.getJSONObject("data").arr("dailySongs").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } }
    }.getOrDefault(emptyList())

    override suspend fun search(keywords: String, type: Int, limit: Int): List<Song> =
        post("/api/cloudsearch/pc", obj("s" to keywords, "type" to type, "limit" to limit, "offset" to 0, "total" to true), Crypto.EAPI)
            .body.getJSONObject("result").arr("songs").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } }

    override suspend fun searchPlaylists(keywords: String): List<Playlist> =
        post("/api/cloudsearch/pc", obj("s" to keywords, "type" to 1000, "limit" to 20, "offset" to 0, "total" to true), Crypto.EAPI)
            .body.getJSONObject("result").arr("playlists").let { a -> (0 until a.length()).map { parsePlaylist(a.getJSONObject(it)) } }

    override suspend fun searchArtists(keywords: String): List<Artist> =
        post("/api/cloudsearch/pc", obj("s" to keywords, "type" to 100, "limit" to 10, "offset" to 0, "total" to true), Crypto.EAPI)
            .body.getJSONObject("result").arr("artists").let { a ->
                (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    Artist(o.optLong("id"), o.optString("name"), o.optString("img1v1Url", o.optString("picUrl")), o.optString("briefDesc"), o.optInt("musicSize"), o.optInt("albumSize"))
                }
            }

    override suspend fun userPlaylists(uid: Long): List<Playlist> =
        post("/api/user/playlist", obj("uid" to uid, "limit" to 100, "offset" to 0, "includeVideo" to true), Crypto.WEAPI)
            .body.arr("playlist").let { a -> (0 until a.length()).map { parsePlaylist(a.getJSONObject(it)) } }

    override suspend fun playlistDetail(id: Long): Pair<Playlist, List<Song>> {
        val o = post("/api/v6/playlist/detail", obj("id" to id, "n" to 100000, "s" to 8), Crypto.EAPI).body
        val pl = parsePlaylist(o.getJSONObject("playlist"))
        val tracks = o.getJSONObject("playlist").arr("tracks").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } }
        return pl to tracks
    }

    override suspend fun playlistCatlist(): List<String> =
        post("/api/playlist/catalogue", obj(), Crypto.EAPI)
            .body.arr("sub").let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } }

    override suspend fun userDetail(uid: Long): UserProfile {
        val o = post("/api/v1/user/detail/$uid", obj(), Crypto.WEAPI).body
        val p = o.getJSONObject("profile")
        // 第五轮第 24 项：注册天数取响应顶层的 createDays（服务端已算好）；
        // profile.createTime 与顶层 createTime 对该账号均为 -1，不能用。
        return UserProfile(uid, p.optString("nickname"), p.optString("avatarUrl"), o.optInt("level"), p.optInt("follows"), p.optInt("followeds"), p.optString("signature"), o.optInt("createDays", -1))
    }

    override suspend fun likeList(uid: Long): List<Long> =
        post("/api/song/like/get", obj("uid" to uid), Crypto.EAPI)
            .body.arr("ids").let { a -> (0 until a.length()).map { a.getLong(it) } }

    override suspend fun like(id: Long, like: Boolean) {
        val code = post(
            "/api/radio/like",
            obj("alg" to "itembased", "trackId" to id, "like" to like, "time" to "3"),
            Crypto.WEAPI,
        ).body.optInt("code")
        if (code != 200) throw ApiException(ApiErrorKind.Unknown, message = AppStrings.getOrFallback(R.string.err_like_code, "喜欢失败（code %1\$d）", code))
    }

    // 第 6 项：加歌 / 删歌。上游 4.32.0 playlist_track_add / playlist_track_delete：
    // POST /api/playlist/track/add|delete（weapi），body {id: pid,
    // tracks: "[{\"type\":3,\"id\":\"<id>\"}]"}（id 是字符串，上游把 ids split
    // 后的字符串原样放进 tracks）。成功 code 200，否则抛错。
    override suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        val code = post(
            "/api/playlist/track/add",
            obj("id" to playlistId, "tracks" to "[{\"type\":3,\"id\":\"$trackId\"}]"),
            Crypto.WEAPI,
        ).body.optInt("code")
        if (code != 200) throw ApiException(ApiErrorKind.Unknown, message = AppStrings.getOrFallback(R.string.err_add_playlist_code, "添加到歌单失败（code %1\$d）", code))
    }

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        val code = post(
            "/api/playlist/track/delete",
            obj("id" to playlistId, "tracks" to "[{\"type\":3,\"id\":\"$trackId\"}]"),
            Crypto.WEAPI,
        ).body.optInt("code")
        if (code != 200) throw ApiException(ApiErrorKind.Unknown, message = AppStrings.getOrFallback(R.string.err_remove_playlist_code, "从歌单移除失败（code %1\$d）", code))
    }

    // 第 7 项：音乐等级。上游 4.32.0 user_level：POST /api/user/level
    //（weapi，空 body），返回 {"code":200,"data":{"level":N,...}}。
    override suspend fun userLevel(): Int {
        val body = post("/api/user/level", obj(), Crypto.WEAPI).body
        val inData = body.optJSONObject("data")?.optInt("level", -1) ?: -1
        return if (inData >= 0) inData else body.optInt("level")
    }

    override suspend fun userRecord(uid: Long, weekly: Boolean): List<RecordItem> {
        val o = post("/api/v1/play/record", obj("uid" to uid, "type" to if (weekly) 1 else 0), Crypto.WEAPI).body
        val a = o.arr(if (weekly) "weekData" else "allData")
        return (0 until a.length()).map { val it = a.getJSONObject(it); RecordItem(parseSong(it.getJSONObject("song")), it.optInt("playCount"), it.optInt("score")) }
    }

    override suspend fun recentSongs(): List<Song> = runCatching {
        post("/api/discovery/recommend/songs/history/recent", obj(), Crypto.WEAPI)
            .body.getJSONObject("data").arr("list").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it).getJSONObject("data")) } }
    }.getOrDefault(emptyList())

    override suspend fun artistSublist(): List<Artist> =
        post("/api/artist/sublist", obj("limit" to 100, "offset" to 0, "total" to true), Crypto.WEAPI)
            .body.arr("data").let { a ->
                (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    Artist(o.optLong("id"), o.optString("name"), o.optString("img1v1Url", o.optString("picUrl")))
                }
            }

    // 0.1.13 第 24 项：关注 / 取消关注歌手（上游 artist_sub 模块：
    // /api/artist/sub|unsub，weapi，参数 artistId + artistIds "[id]"；
    // 失败（未登录 / 网络）返回 false，不抛异常）。
    override suspend fun artistSub(id: Long, subscribe: Boolean): Boolean =
        runCatching {
            post(
                if (subscribe) "/api/artist/sub" else "/api/artist/unsub",
                obj("artistId" to id, "artistIds" to "[$id]"),
                Crypto.WEAPI,
            ).body.optInt("code") == 200
        }.getOrDefault(false)

    // 0.1.17 第 3 项：收藏 / 取消收藏歌单（上游 playlist_subscribe 模块：
    // /api/playlist/subscribe|unsubscribe，eapi；订阅时带 checkToken，
    // 取消订阅不带；失败返回 false，不抛异常）。
    override suspend fun playlistSub(id: Long, subscribe: Boolean): Boolean =
        runCatching {
            val data = if (subscribe) obj("id" to id, "checkToken" to PLAYLIST_CHECK_TOKEN) else obj("id" to id)
            post(
                if (subscribe) "/api/playlist/subscribe" else "/api/playlist/unsubscribe",
                data,
                Crypto.EAPI,
            ).body.optInt("code") == 200
        }.getOrDefault(false)

    // 0.1.17 第 13 项：听歌打卡（上游 scrobble 模块：/api/feedback/weblog，
    // weapi，logs 是 JSON 字符串；失败返回 false，不抛异常——调用方静默）。
    override suspend fun scrobble(id: Long, sourceId: Long, timeSeconds: Int): Boolean =
        runCatching {
            post("/api/feedback/weblog", obj("logs" to scrobbleLogsJson(id, sourceId, timeSeconds)), Crypto.WEAPI)
                .body.optInt("code") == 200
        }.getOrDefault(false)

    // 第三轮第 12 项：歌手详情 / 热门歌曲 / 专辑列表 / 专辑详情。
    // artist_detail 上游模块未指定 crypto，实测 weapi 可用（2026-11-21，
    // 上游模块直连 music.163.com 验证：plain 与 weapi 均返回 code 200）。
    override suspend fun artistDetail(id: Long): Artist {
        val a = post("/api/artist/head/info/get", obj("id" to id), Crypto.WEAPI)
            .body.getJSONObject("data").getJSONObject("artist")
        val pic = a.optString("avatar").ifBlank { a.optString("cover") }.ifBlank { a.optString("picUrl") }
        return Artist(a.optLong("id"), a.optString("name"), pic, a.optString("briefDesc"), a.optInt("musicSize"), a.optInt("albumSize"))
    }

    override suspend fun artistTopSongs(id: Long): List<Song> =
        post("/api/artist/top/song", obj("id" to id), Crypto.WEAPI)
            .body.arr("songs").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } }

    // 第五轮第 13 项：歌手全部歌曲。/api/v1/artist/songs（上游 artist_songs
    // 模块未指定 crypto，默认 eapi；2026-11-23 上游直连实测 eapi 返回
    // code 200，total 589、more=true）。分页拉全量（limit 100），再用
    // filterAndSortArtistSongs 去 live + 原唱/单曲优先排序（见 ArtistSongs.kt）。
    override suspend fun artistSongs(id: Long): List<Song> {
        val all = mutableListOf<Song>()
        var offset = 0
        var pages = 0
        while (pages < 50) {
            pages++
            val body = post(
                "/api/v1/artist/songs",
                obj("id" to id, "private_cloud" to "true", "work_type" to 1, "order" to "hot", "offset" to offset, "limit" to 100),
                Crypto.EAPI,
            ).body
            val page = body.arr("songs").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } }
            if (page.isEmpty()) break
            all += page
            offset += page.size
            if (!body.optBoolean("more", false)) break
        }
        return filterAndSortArtistSongs(all, id)
    }

    override suspend fun artistAlbums(id: Long): List<Album> =
        post("/api/artist/albums/$id", obj("limit" to 30, "offset" to 0, "total" to true), Crypto.WEAPI)
            .body.arr("hotAlbums").let { a -> (0 until a.length()).map { parseAlbum(a.getJSONObject(it)) } }

    override suspend fun albumDetail(id: Long): Pair<Album, List<Song>> {
        val o = post("/api/v1/album/$id", obj(), Crypto.WEAPI).body
        val album = parseAlbum(o.getJSONObject("album"))
        val tracks = o.arr("songs").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } }
        return album to tracks
    }

    override suspend fun albumSublist(): List<Album> =
        post("/api/album/sublist", obj("limit" to 100, "offset" to 0, "total" to true), Crypto.WEAPI)
            .body.arr("data").let { a -> (0 until a.length()).map { parseAlbum(a.getJSONObject(it)) } }

    override suspend fun toplist(): List<Playlist> =
        post("/api/toplist", obj(), Crypto.EAPI)
            .body.arr("list").let { a -> (0 until a.length()).map { parsePlaylist(a.getJSONObject(it)) } }

    override suspend fun songUrl(id: Long): SongUrlResult {
        // Multi-route fallback: the EAPI v1 endpoint is the one NetEase
        // rate-limits most aggressively — a body code of -460 means the
        // route itself is blocked, NOT that the song has no copyright, so
        // it must try the next route instead of giving up (0.1.4 treated
        // -460 exactly like a blank URL, which is why "无播放地址" showed
        // up even for ordinary songs). Routes mirror upstream
        // NeteaseCloudMusicApi: /song/url/v1 (eapi, then weapi) and the
        // classic /song/url (weapi, bitrate ladder). A blank URL means no
        // playable copy at that level (no copyright / VIP), not a network
        // error — that result is kept and returned at the end.
        var sawRateLimit = false
        var last = SongUrlResult(null)

        // One attempt: false = no playable URL from this route, keep going.
        suspend fun attempt(crypto: Crypto, uri: String, data: JSONObject): Boolean {
            val body = try {
                post(uri, data, crypto).body
            } catch (e: ApiException) {
                if (e.kind == ApiErrorKind.RateLimited) { sawRateLimit = true; return false }
                // Login expiry / offline is not a route problem: surface it.
                if (e.kind == ApiErrorKind.NotLoggedIn || e.kind == ApiErrorKind.Unreachable) throw e
                return false
            }
            if (body.optInt("code") == -460) { sawRateLimit = true; return false }
            val d = body.arr("data").optJSONObject(0) ?: return false
            val url = d.optString("url").ifBlank { null }
            val fee = d.optInt("fee")
            val trialEnd = d.optJSONObject("freeTrialInfo")?.optInt("end")
                ?: d.optJSONObject("freeTrialPrivilege")?.optInt("end") ?: 0
            last = SongUrlResult(url, fee, trialEnd)
            return url != null
        }

        // 音质偏好决定先试哪个 level；standard 保持原来的顺序（现有单测依赖它）。
        val (eapiLevels, weapiLevels) = when (store.audioQuality) {
            "higher" -> listOf("higher", "exhigh") to listOf("higher", "exhigh", "lossless")
            "exhigh" -> listOf("exhigh") to listOf("exhigh", "lossless")
            "lossless" -> listOf("lossless", "exhigh") to listOf("lossless", "exhigh", "higher", "standard")
            else -> listOf("standard", "exhigh") to listOf("standard", "higher", "exhigh", "lossless")
        }
        for (level in eapiLevels) {
            if (attempt(Crypto.EAPI, "/api/song/enhance/player/url/v1",
                    obj("ids" to "[$id]", "level" to level, "encodeType" to "flac"))) return last
        }
        for (level in weapiLevels) {
            if (attempt(Crypto.WEAPI, "/api/song/enhance/player/url/v1",
                    obj("ids" to "[$id]", "level" to level, "encodeType" to "flac"))) return last
        }
        if (attempt(Crypto.WEAPI, "/api/song/enhance/player/url",
                obj("ids" to "[$id]", "br" to 999000))) return last
        // Every route answered -460 (or HTTP 460/503): say so, instead of
        // blaming the song's copyright — the UI already maps RateLimited
        // to "请求太频繁，被网易暂时限制了，稍后再试".
        if (sawRateLimit) throw ApiException(ApiErrorKind.RateLimited, apiCode = -460)
        return last
    }

    override suspend fun songDetail(ids: List<Long>): List<Song> =
        if (ids.isEmpty()) emptyList() else post(
            "/api/v3/song/detail",
            obj("c" to "[" + ids.joinToString(",") { """{"id":$it}""" } + "]"),
            Crypto.WEAPI,
        ).body.arr("songs").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } }

    override suspend fun lyric(id: Long): LyricPayload {
        // /lyric/v1 can answer HTTP 200 with code -460 (rate-limited) or with
        // no lyric at all — that is a miss, not a success: fall back to /lyric.
        val first = runCatching {
            post(
                "/api/song/lyric/v1",
                obj("id" to id, "cp" to false, "tv" to 0, "lv" to 0, "rv" to 0, "kv" to 0, "yv" to 0, "ytv" to 0, "yrv" to 0),
                Crypto.EAPI,
            ).body
        }.getOrNull()
        val payload = first?.let { o -> LyricPayload(o.optJSONObject("yrc")?.optString("lyric"), o.optJSONObject("lrc")?.optString("lyric"), o.optJSONObject("tlyric")?.optString("lyric"), o.optJSONObject("romalrc")?.optString("lyric")) }
        if (payload != null && (!payload.yrc.isNullOrBlank() || !payload.lrc.isNullOrBlank())) return payload
        val o = post(
            "/api/song/lyric",
            obj("id" to id, "tv" to -1, "lv" to -1, "rv" to -1, "kv" to -1, "_nmclfl" to 1),
            Crypto.EAPI,
        ).body
        return LyricPayload(null, o.optJSONObject("lrc")?.optString("lyric"), o.optJSONObject("tlyric")?.optString("lyric"), o.optJSONObject("romalrc")?.optString("lyric"))
    }
}

/**
 * scrobble 的 logs 请求体（0.1.17 第 13 项，纯函数，可单测）：上游
 * scrobble 模块的 data.logs——JSON 字符串，内含一个 action=play 的条目
 * （download 0、end playend、id、sourceId、time 已播秒数、type song、
 * wifi 0、source list、mainsite 1、content 空）。
 */
internal fun scrobbleLogsJson(id: Long, sourceId: Long, timeSeconds: Int): String {
    val json = JSONObject()
        .put("download", 0)
        .put("end", "playend")
        .put("id", id)
        .put("sourceId", sourceId)
        .put("time", timeSeconds)
        .put("type", "song")
        .put("wifi", 0)
        .put("source", "list")
        .put("mainsite", 1)
        .put("content", "")
    return JSONArray().put(JSONObject().put("action", "play").put("json", json)).toString()
}
