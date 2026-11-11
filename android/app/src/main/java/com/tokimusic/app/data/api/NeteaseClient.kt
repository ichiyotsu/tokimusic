package com.tokimusic.app.data.api
import com.tokimusic.app.data.SettingsStore
import com.tokimusic.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** OkHttp implementation of [NeteaseApi] against a NeteaseCloudMusicApi-compatible service. */
class NeteaseClient(private val store: SettingsStore) : NeteaseApi {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val sep = if (path.contains('?')) "&" else "?"
        val url = store.apiBase + path + if (store.cookie.isNotBlank()) sep + "cookie=" + enc(store.cookie) else ""
        val req = Request.Builder().url(url).header("Cookie", store.cookie).build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}: $path")
            JSONObject(body)
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
    override suspend fun qrKey(): String = get("/login/qr/key?timestamp=${System.currentTimeMillis()}").getJSONObject("data").getString("unikey")
    override suspend fun qrCreate(key: String): String = get("/login/qr/create?key=${enc(key)}&qrimg=true").getJSONObject("data").optString("qrimg")
    override suspend fun qrCheck(key: String): QrCheck {
        val o = get("/login/qr/check?key=${enc(key)}&timestamp=${System.currentTimeMillis()}")
        val cookie = o.optString("cookie"); if (o.optInt("code") == 803 && cookie.isNotBlank()) store.cookie = cookie
        return QrCheck(o.optInt("code"), cookie, o.optString("message"))
    }
    override suspend fun loginStatus(): UserProfile? {
        if (store.cookie.isBlank()) return null
        val profile = get("/user/account").optJSONObject("profile") ?: return null
        return UserProfile(profile.optLong("userId"), profile.optString("nickname"), profile.optString("avatarUrl"))
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
    override suspend fun userRecord(uid: Long, weekly: Boolean): List<RecordItem> {
        val o = get("/user/record?uid=$uid&type=${if (weekly) 1 else 0}"); val a = o.arr(if (weekly) "weekData" else "allData")
        return (0 until a.length()).map { val it = a.getJSONObject(it); RecordItem(parseSong(it.getJSONObject("song")), it.optInt("playCount"), it.optInt("score")) }
    }
    override suspend fun recentSongs(): List<Song> = runCatching { get("/history/recent/songs?limit=30").getJSONObject("data").arr("list").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it).getJSONObject("data")) } } }.getOrDefault(emptyList())
    override suspend fun songUrl(id: Long): String? = get("/song/url/v1?id=$id&level=standard").arr("data").optJSONObject(0)?.optString("url")?.ifBlank { null }
    override suspend fun songDetail(ids: List<Long>): List<Song> = if (ids.isEmpty()) emptyList() else get("/song/detail?ids=${ids.joinToString(",")}").arr("songs").let { a -> (0 until a.length()).map { parseSong(a.getJSONObject(it)) } }
    override suspend fun lyric(id: Long): LyricPayload {
        val o = runCatching { get("/lyric/new?id=$id") }.getOrElse { get("/lyric?id=$id") }
        return LyricPayload(o.optJSONObject("yrc")?.optString("lyric"), o.optJSONObject("lrc")?.optString("lyric"), o.optJSONObject("tlyric")?.optString("lyric"))
    }
}
