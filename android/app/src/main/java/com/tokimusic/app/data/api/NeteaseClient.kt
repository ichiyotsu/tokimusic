package com.tokimusic.app.data.api

import com.tokimusic.app.data.ApiSettings
import com.tokimusic.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.Proxy
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Result of a playback-URL lookup: the URL plus the rights info around it. */
data class SongUrlResult(val url: String?, val fee: Int = 0, val freeTrialEndSec: Int = 0) {
    /** Non-zero means only a trial snippet (that many seconds) is playable. */
    val isTrial: Boolean get() = freeTrialEndSec > 0
}

/** Connectivity probe result, for the in-app setup guide and test button. */
sealed interface ApiHealth {
    data object InvalidAddress : ApiHealth
    data class Unreachable(val reason: String) : ApiHealth
    data class Connected(val loggedIn: Boolean, val nickname: String? = null) : ApiHealth
}

/**
 * OkHttp implementation of [NeteaseApi] against a NeteaseCloudMusicApi-compatible service.
 *
 * Fixed against the 0.1.2 behaviour (see docs/API.md):
 * - The API server lives on the user's computer or LAN, so requests go direct
 *   ([Proxy.NO_PROXY]): routing them through any egress proxy breaks LAN and
 *   loopback addresses, and a real phone has no such proxy anyway.
 * - Cookies live in a real [CookieJar]: the QR flow's `Set-Cookie` (notably
 *   the NMTID anonymous device id from `/login/qr/key`) is kept, and the
 *   persisted cookie is re-synced from the jar after every response. The
 *   login cookie is sent as a `cookie` query parameter (the upstream
 *   convention), not duplicated into a `Cookie` header.
 * - The QR flow never sends the stored login cookie ([skipAuthCookie]): a
 *   stale cookie on `/login/qr/key` / `/login/qr/check` makes a fresh scan
 *   come back "expired" (800) after the user already confirmed on the phone.
 * - Login expiry is a first-class failure: a body `code` of 301 throws
 *   [ApiException] with [ApiErrorKind.NotLoggedIn] instead of collapsing
 *   into "not logged in" or an empty page.
 */
class NeteaseClient(private val store: ApiSettings) : NeteaseApi {
    companion object {
        /** Login-credential cookies: they travel ONLY as the `cookie` query
         * parameter (the upstream convention), never as a request header.
         * Anonymous cookies (notably NMTID) travel as a header instead. */
        private val LOGIN_COOKIE_NAMES = setOf("MUSIC_U", "__remember_me", "__csrf")
        private val COOKIE_ATTRIBUTES = setOf("Max-Age", "Expires", "Path", "Domain", "Secure", "HttpOnly", "SameSite")
    }

    private val jar = object : CookieJar {
        private val saved = mutableMapOf<String, MutableList<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val kept = saved.getOrPut(url.host) { mutableListOf() }
            for (c in cookies) {
                kept.removeAll { it.name == c.name }
                if (c.expiresAt >= System.currentTimeMillis()) kept.add(c)
            }
            syncPersistedCookie(url.host)
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            saved[url.host]?.filter { it.expiresAt >= System.currentTimeMillis() && it.name !in LOGIN_COOKIE_NAMES } ?: emptyList()
        fun seedFromPersisted(host: String, cookie: String) {
            if (cookie.isBlank()) return
            val now = System.currentTimeMillis()
            val kept = saved.getOrPut(host) { mutableListOf() }
            cookie.split(";").forEach { part ->
                val kv = part.trim().split("=", limit = 2)
                if (kv.size == 2 && kv[0].isNotBlank() && kv[0].trim() !in COOKIE_ATTRIBUTES) {
                    kept.removeAll { it.name == kv[0].trim() }
                    kept.add(Cookie.Builder().name(kv[0].trim()).value(kv[1].trim()).domain(host).path("/").expiresAt(now + 30L * 24 * 3600 * 1000).build())
                }
            }
        }
        fun cookiesFor(host: String): String =
            saved[host]?.filter { it.expiresAt >= System.currentTimeMillis() }
                ?.joinToString("; ") { "${it.name}=${it.value}" } ?: ""
        /** Only the login-credential cookies (for the 803 fallback: an NMTID
         * alone is an anonymous id, never a login). */
        fun loginCookiesFor(host: String): String =
            saved[host]?.filter { it.expiresAt >= System.currentTimeMillis() && it.name in LOGIN_COOKIE_NAMES }
                ?.joinToString("; ") { "${it.name}=${it.value}" } ?: ""
        fun clear() = saved.clear()
        private fun syncPersistedCookie(host: String) {
            val combined = cookiesFor(host)
            if (combined.isNotBlank()) store.cookie = combined
        }
    }

    private val http = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .cookieJar(jar)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    private fun apiHost(): HttpUrl =
        store.apiBase.toHttpUrlOrNull() ?: throw ApiException(ApiErrorKind.InvalidAddress)

    private fun storedCookie(): String {
        val host = apiHost().host
        return jar.cookiesFor(host).ifBlank { store.cookie }
    }

    private suspend fun get(path: String, skipAuthCookie: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val base = apiHost()
        val cookie = if (skipAuthCookie) "" else storedCookie()
        val sep = if (path.contains('?')) "&" else "?"
        val url = store.apiBase.trimEnd('/') + path + if (cookie.isNotBlank()) sep + "cookie=" + enc(cookie) else ""
        val req = Request.Builder().url(url).build()
        val res = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw ApiException(ApiErrorKind.Unreachable, message = e.message)
        }
        res.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                if (it.code == 460 || it.code == 503) throw ApiException(ApiErrorKind.RateLimited, httpCode = it.code)
                throw ApiException(ApiErrorKind.HttpStatus, httpCode = it.code)
            }
            val json = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw ApiException(ApiErrorKind.Unknown, message = "服务返回了无法解析的内容")
            }
            if (json.optInt("code") == 301) throw ApiException(ApiErrorKind.NotLoggedIn, apiCode = 301)
            json
        }
    }

    private fun JSONObject.arr(key: String): JSONArray = if (has(key) && !isNull(key)) getJSONArray(key) else JSONArray()
    private fun parseSong(o: JSONObject): Song {
        val ar = o.optJSONArray("ar") ?: o.optJSONArray("artists") ?: JSONArray()
        val artists = (0 until ar.length()).map { Artist(ar.getJSONObject(it).optLong("id"), ar.getJSONObject(it).optString("name")) }
        val al = o.optJSONObject("al") ?: o.optJSONObject("album")
        return Song(o.optLong("id"), o.optString("name"), artists, al?.optString("name") ?: "", al?.optString("picUrl") ?: "", o.optLong("dt", o.optLong("duration")), o.optInt("fee"))
    }
    private fun parsePlaylist(o: JSONObject): Playlist {
        val creator = o.optJSONObject("creator")
        return Playlist(o.optLong("id"), o.optString("name"), o.optString("coverImgUrl", o.optString("picUrl")), o.optInt("trackCount"), creator?.optString("nickname") ?: "", creator?.optLong("userId") ?: 0, o.optBoolean("subscribed"), o.optLong("playCount"), o.optString("description"))
    }

    override suspend fun qrKey(): String = get("/login/qr/key?timestamp=${System.currentTimeMillis()}", skipAuthCookie = true).getJSONObject("data").getString("unikey")
    override suspend fun qrCreate(key: String): String {
        val img = get("/login/qr/create?key=${enc(key)}&qrimg=true", skipAuthCookie = true).getJSONObject("data").optString("qrimg")
        if (img.isBlank()) throw ApiException(ApiErrorKind.Unknown, message = "二维码生成失败，请检查 API 服务版本后重试")
        return img
    }
    override suspend fun qrCheck(key: String): QrCheck {
        val o = get("/login/qr/check?key=${enc(key)}&timestamp=${System.currentTimeMillis()}", skipAuthCookie = true)
        val code = o.optInt("code")
        if (code == 803) {
            // The credential may arrive in the body, in Set-Cookie, or both.
            // A "success" with no credential anywhere is a failure, not a login.
            var cookie = o.optString("cookie")
            if (cookie.isBlank()) cookie = jar.loginCookiesFor(apiHost().host)
            if (cookie.isBlank()) throw ApiException(ApiErrorKind.Unknown, message = "登录成功但未拿到凭证，请重新扫码")
            // Merge the body credential into the jar so later requests carry
            // both it and the NMTID the key step set (neither may be dropped),
            // then persist the combined set — not the raw body string.
            val host = apiHost().host
            jar.seedFromPersisted(host, cookie)
            store.cookie = jar.cookiesFor(host).ifBlank { cookie }
            return QrCheck(code, store.cookie, o.optString("message"))
        }
        return QrCheck(code, "", o.optString("message"))
    }
    override fun clearSession() {
        jar.clear()
        store.cookie = ""
    }
    override suspend fun loginStatus(): UserProfile? {
        // /login/status is the authority: data.code 200 = logged in, 301 = expired.
        val status = get("/login/status")
        if (status.optJSONObject("data")?.optInt("code") != 200) throw ApiException(ApiErrorKind.NotLoggedIn, apiCode = 301)
        val profile = get("/user/account").optJSONObject("profile") ?: return null
        return UserProfile(profile.optLong("userId"), profile.optString("nickname"), profile.optString("avatarUrl"))
    }
    override suspend fun refreshLogin() {
        runCatching { get("/login/refresh") }
    }
    override suspend fun apiHealth(): ApiHealth {
        val host = try {
            apiHost().host
        } catch (e: ApiException) {
            return ApiHealth.InvalidAddress
        }
        return try {
            val status = get("/login/status")
            val loggedIn = status.optJSONObject("data")?.optInt("code") == 200
            val nickname = if (loggedIn) runCatching { get("/user/account").optJSONObject("profile")?.optString("nickname") }.getOrNull() else null
            ApiHealth.Connected(loggedIn, nickname?.ifBlank { null })
        } catch (e: ApiException) {
            when (e.kind) {
                ApiErrorKind.NotLoggedIn -> ApiHealth.Connected(false, null)
                ApiErrorKind.InvalidAddress -> ApiHealth.InvalidAddress
                else -> ApiHealth.Unreachable(apiErrorText(e))
            }
        } catch (e: Exception) {
            ApiHealth.Unreachable(e.message ?: "未知错误")
        }
    }
    override suspend fun banners(): List<BannerItem> = get("/banner?type=2").arr("banners").let { a -> (0 until a.length()).map { val o = a.getJSONObject(it); BannerItem(o.optString("pic"), o.optString("typeTitle"), o.optLong("targetId")) } }
    override suspend fun personalized(limit: Int): List<Playlist> = get("/personalized?limit=$limit").arr("result").let { a -> (0 until a.length()).map { parsePlaylist(a.getJSONObject(it)) } }
    override suspend fun newSongs(): List<Song> = get("/personalized/newsong").arr("result").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it).optJSONObject("song") ?: a.getJSONObject(it)) } }
    override suspend fun dailySongs(): List<Song> = runCatching { get("/recommend/songs").getJSONObject("data").arr("dailySongs").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } } }.getOrDefault(emptyList())
    override suspend fun search(keywords: String, type: Int, limit: Int): List<Song> = get("/cloudsearch?keywords=${enc(keywords)}&type=$type&limit=$limit").getJSONObject("result").arr("songs").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } }
    override suspend fun searchPlaylists(keywords: String): List<Playlist> = get("/cloudsearch?keywords=${enc(keywords)}&type=1000&limit=20").getJSONObject("result").arr("playlists").let { a -> (0 until a.length()).map { parsePlaylist(a.getJSONObject(it)) } }
    override suspend fun userPlaylists(uid: Long): List<Playlist> = get("/user/playlist?uid=$uid&limit=100").arr("playlist").let { a -> (0 until a.length()).map { parsePlaylist(a.getJSONObject(it)) } }
    override suspend fun playlistDetail(id: Long): Pair<Playlist, List<Song>> {
        val o = get("/playlist/detail?id=$id"); val pl = parsePlaylist(o.getJSONObject("playlist"))
        val tracks = o.getJSONObject("playlist").arr("tracks").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } }
        return pl to tracks
    }
    override suspend fun playlistCatlist(): List<String> = get("/playlist/catlist").arr("sub").let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } }
    override suspend fun userDetail(uid: Long): UserProfile {
        val o = get("/user/detail?uid=$uid"); val p = o.getJSONObject("profile")
        return UserProfile(uid, p.optString("nickname"), p.optString("avatarUrl"), o.optInt("level"), p.optInt("follows"), p.optInt("followeds"), p.optString("signature"))
    }
    override suspend fun likeList(uid: Long): List<Long> = get("/likelist?uid=$uid").arr("ids").let { a -> (0 until a.length()).map { a.getLong(it) } }
    override suspend fun like(id: Long, like: Boolean) {
        val code = get("/like?id=$id&like=$like").optInt("code")
        if (code != 200) throw ApiException(ApiErrorKind.Unknown, message = "喜欢失败（code $code）")
    }
    override suspend fun userRecord(uid: Long, weekly: Boolean): List<RecordItem> {
        val o = get("/user/record?uid=$uid&type=${if (weekly) 1 else 0}"); val a = o.arr(if (weekly) "weekData" else "allData")
        return (0 until a.length()).map { val it = a.getJSONObject(it); RecordItem(parseSong(it.getJSONObject("song")), it.optInt("playCount"), it.optInt("score")) }
    }
    override suspend fun recentSongs(): List<Song> = runCatching { get("/history/recent/songs?limit=30").getJSONObject("data").arr("list").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it).getJSONObject("data")) } } }.getOrDefault(emptyList())
    override suspend fun songUrl(id: Long): SongUrlResult {
        // standard first, exhigh as fallback; a blank URL means no playable
        // copy at that level (no copyright / VIP), not a network error.
        var last = SongUrlResult(null)
        for (level in listOf("standard", "exhigh")) {
            val d = get("/song/url/v1?id=$id&level=$level").arr("data").optJSONObject(0) ?: continue
            val url = d.optString("url").ifBlank { null }
            val fee = d.optInt("fee")
            val trialEnd = d.optJSONObject("freeTrialInfo")?.optInt("end")
                ?: d.optJSONObject("freeTrialPrivilege")?.optInt("end") ?: 0
            last = SongUrlResult(url, fee, trialEnd)
            if (url != null) return last
        }
        return last
    }
    override suspend fun songDetail(ids: List<Long>): List<Song> = if (ids.isEmpty()) emptyList() else get("/song/detail?ids=${ids.joinToString(",")}").arr("songs").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } }
    override suspend fun lyric(id: Long): LyricPayload {
        // /lyric/new can answer HTTP 200 with code -460 (rate-limited) or with
        // no lyric at all — that is a miss, not a success: fall back to /lyric.
        val first = runCatching { get("/lyric/new?id=$id") }.getOrNull()
        val payload = first?.let { o -> LyricPayload(o.optJSONObject("yrc")?.optString("lyric"), o.optJSONObject("lrc")?.optString("lyric"), o.optJSONObject("tlyric")?.optString("lyric")) }
        if (payload != null && (!payload.yrc.isNullOrBlank() || !payload.lrc.isNullOrBlank())) return payload
        val o = get("/lyric?id=$id")
        return LyricPayload(null, o.optJSONObject("lrc")?.optString("lyric"), o.optJSONObject("tlyric")?.optString("lyric"))
    }
}
