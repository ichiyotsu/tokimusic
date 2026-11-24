package com.tokimusic.app.data.api
import com.tokimusic.app.data.model.*
/** Isomorphic contract implemented on all three platforms. On Android the
 * calls go straight to music.163.com (weapi/eapi, see NeteaseClient);
 * iOS/Windows still go through a self-hosted NeteaseCloudMusicApi service. */
interface NeteaseApi {
    suspend fun qrKey(): String                       // /login/qr/key
    suspend fun qrCreate(key: String): String         // /login/qr/create -> qrimg base64
    suspend fun qrCheck(key: String): QrCheck         // /login/qr/check 800/801/802/803
    suspend fun loginStatus(): UserProfile?           // /login/status (authority) + /user/account; throws ApiException(NotLoggedIn) when expired, Unreachable when offline
    suspend fun refreshLogin()                        // /login/refresh — best effort, failures ignored
    suspend fun apiHealth(): ApiHealth                // connectivity probe for the setup guide
    fun clearSession()                                // forget the login cookie AND the in-memory session cookies (logout / expiry)
    suspend fun banners(): List<BannerItem>           // /banner
    suspend fun personalized(limit: Int = 12): List<Playlist>   // /personalized
    suspend fun newSongs(): List<Song>                // /personalized/newsong
    suspend fun dailySongs(): List<Song>              // /recommend/songs (logged in)
    suspend fun search(keywords: String, type: Int = 1, limit: Int = 30): List<Song> // /cloudsearch
    suspend fun searchArtists(keywords: String): List<Artist> // /cloudsearch type=100 （第六轮第二批，搜索加歌手区）
    suspend fun searchPlaylists(keywords: String): List<Playlist>
    suspend fun userPlaylists(uid: Long): List<Playlist>        // /user/playlist
    suspend fun playlistDetail(id: Long): Pair<Playlist, List<Song>> // /playlist/detail
    suspend fun playlistCatlist(): List<String>       // /playlist/catlist
    suspend fun userDetail(uid: Long): UserProfile    // /user/detail
    suspend fun likeList(uid: Long): List<Long>       // /likelist
    suspend fun like(id: Long, like: Boolean)          // /like — add/remove from "我喜欢的音乐"
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) // /playlist/track/add (weapi)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) // /playlist/track/delete (weapi)
    suspend fun userRecord(uid: Long, weekly: Boolean): List<RecordItem> // /user/record?type=
    suspend fun userLevel(): Int                   // /api/user/level (weapi) — data.level
    suspend fun recentSongs(): List<Song>             // /history/recent/songs
    suspend fun artistSublist(): List<Artist>         // /artist/sublist （我关注的歌手）
    /** 关注 / 取消关注歌手（0.1.13 第 24 项）：/api/artist/sub|unsub（weapi，上游 artist_sub 模块）；返回是否成功。 */
    suspend fun artistSub(id: Long, subscribe: Boolean): Boolean
    suspend fun artistDetail(id: Long): Artist        // /artist/head/info/get （第三轮第 12 项，weapi；上游模块未指定 crypto，实测 weapi 可用）
    suspend fun artistTopSongs(id: Long): List<Song>  // /artist/top/song （第三轮第 12 项，weapi，热门 50 首）
    suspend fun artistSongs(id: Long): List<Song>   // /v1/artist/songs （第五轮第 13 项，eapi，分页拉全量 + 去 live + 原唱/单曲优先排序）
    suspend fun artistAlbums(id: Long): List<Album>   // /artist/albums/{id} （第三轮第 12 项，weapi，首屏 30 张）
    suspend fun albumDetail(id: Long): Pair<Album, List<Song>> // /v1/album/{id} （第三轮第 12 项，weapi）
    suspend fun albumSublist(): List<Album>           // /album/sublist （我收藏的专辑）
    suspend fun toplist(): List<Playlist>             // /toplist （所有榜单）
    suspend fun songUrl(id: Long): SongUrlResult      // multi-route: EAPI /song/url/v1, then WEAPI /song/url/v1, then classic /song/url; -460 on every route throws RateLimited, url null = no playable copy
    suspend fun songDetail(ids: List<Long>): List<Song> // /song/detail
    suspend fun lyric(id: Long): LyricPayload         // /lyric/new preferred, /lyric fallback (also when /lyric/new answers empty)
}
data class QrCheck(val code: Int, val cookie: String = "", val message: String = "")
data class LyricPayload(val yrc: String? = null, val lrc: String? = null, val tlyric: String? = null, val romalrc: String? = null)
