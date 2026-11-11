package com.tokimusic.app.data.api
import com.tokimusic.app.data.model.*
/** Isomorphic contract implemented on all three platforms. Base URL configurable, default http://127.0.0.1:3000. */
interface NeteaseApi {
    suspend fun qrKey(): String                       // /login/qr/key
    suspend fun qrCreate(key: String): String         // /login/qr/create -> qrimg base64 / url
    suspend fun qrCheck(key: String): QrCheck         // /login/qr/check 800/801/802/803
    suspend fun loginStatus(): UserProfile?           // /login/status + /user/account
    suspend fun banners(): List<BannerItem>           // /banner
    suspend fun personalized(limit: Int = 12): List<Playlist>   // /personalized
    suspend fun newSongs(): List<Song>                // /personalized/newsong
    suspend fun dailySongs(): List<Song>              // /recommend/songs (logged in)
    suspend fun search(keywords: String, type: Int = 1, limit: Int = 30): List<Song> // /cloudsearch
    suspend fun searchPlaylists(keywords: String): List<Playlist>
    suspend fun userPlaylists(uid: Long): List<Playlist>        // /user/playlist
    suspend fun playlistDetail(id: Long): Pair<Playlist, List<Song>> // /playlist/detail
    suspend fun playlistCatlist(): List<String>       // /playlist/catlist
    suspend fun userDetail(uid: Long): UserProfile    // /user/detail
    suspend fun likeList(uid: Long): List<Long>       // /likelist
    suspend fun userRecord(uid: Long, weekly: Boolean): List<RecordItem> // /user/record?type=
    suspend fun recentSongs(): List<Song>             // /history/recent/songs
    suspend fun songUrl(id: Long): String?            // /song/url/v1?level=standard
    suspend fun songDetail(ids: List<Long>): List<Song> // /song/detail
    suspend fun lyric(id: Long): LyricPayload         // /lyric/new preferred, /lyric fallback
}
data class QrCheck(val code: Int, val cookie: String = "", val message: String = "")
data class LyricPayload(val yrc: String? = null, val lrc: String? = null, val tlyric: String? = null)
