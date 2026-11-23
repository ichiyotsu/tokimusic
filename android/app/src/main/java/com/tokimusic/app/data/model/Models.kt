package com.tokimusic.app.data.model
/** Music source (sixth round, item 19/20 + QQ playback + local player): the two login sources, plus LOCAL (device files, no network). */
enum class MusicSource { NETEASE, QQ, LOCAL }

/** Stable 64-bit id for a QQ song: QQ songmids are strings, but the app keys songs by Long id (queue, mediaId, url cache). FNV-1a 64 over "qq:$songmid" — deterministic across restarts, collision odds negligible; QQ songs never go through NetEase id-based APIs, so a synthetic id is safe. */
fun qqSongId(songmid: String): Long {
    var h = -3750763034362895579L // FNV-1a 64 offset basis (0xcbf29ce484222325)
    for (b in "qq:$songmid".toByteArray(Charsets.UTF_8)) {
        h = h xor (b.toLong() and 0xFF)
        h *= 1099511628211L // FNV-1a 64 prime
    }
    return h
}

/** Local-file song id (sixth round, batch 2, item 20): same FNV-1a 64 family as [qqSongId], different "local:" prefix so the two namespaces never collide. */
fun localSongId(contentUri: String): Long {
    var h = -3750763034362895579L
    for (b in "local:$contentUri".toByteArray(Charsets.UTF_8)) {
        h = h xor (b.toLong() and 0xFF)
        h *= 1099511628211L
    }
    return h
}

data class Artist(val id: Long, val name: String, val picUrl: String = "", val briefDesc: String = "", val musicSize: Int = 0, val albumSize: Int = 0)
data class Album(val id: Long, val name: String, val coverUrl: String = "", val artistText: String = "", val trackCount: Int = 0)
data class Song(val id: Long, val name: String, val artists: List<Artist>, val albumName: String = "", val picUrl: String = "", val durationMs: Long = 0, val fee: Int = 0, /** Which backend serves this song (sixth round, QQ playback). NetEase songs keep their real numeric id; QQ songs carry a synthetic id from [qqSongId] and the real songmid in [sourceId]. */ val source: MusicSource = MusicSource.NETEASE, val sourceId: String = "") {
    val artistText: String get() = artists.joinToString(" / ") { it.name }
}
data class Playlist(val id: Long, val name: String, val coverUrl: String = "", val trackCount: Int = 0, val creatorName: String = "", val creatorId: Long = 0, val subscribed: Boolean = false, val playCount: Long = 0, val description: String = "", /** Which backend serves this playlist (sixth round, batch 2): NetEase playlists keep their real numeric id; QQ playlists carry a synthetic negative id from [QqPlaylistIds] and the real dissid/topId in [sourceId]. */ val source: MusicSource = MusicSource.NETEASE, val sourceId: String = "")

/**
 * Synthetic negative ids for QQ playlists, following the existing -1/-2
 * virtual-playlist pattern (daily -1 / radar -2). QQ dissids and topIds
 * live in NetEase's positive id space, so they are shifted into two
 * non-overlapping negative ranges instead of threading a second id
 * through navigation: diss playlists at -(dissid + 1e9), toplists at
 * -(topId + 1e6). Decode with [dissidOf]/[topIdOf]; anything else is a
 * NetEase id (or -1/-2 virtual).
 */
object QqPlaylistIds {
    private const val DISS_OFFSET = 1_000_000_000L
    private const val TOP_OFFSET = 1_000_000L
    fun dissPlaylistId(dissid: Long): Long = -(dissid + DISS_OFFSET)
    fun toplistId(topId: Long): Long = -(topId + TOP_OFFSET)
    fun dissidOf(id: Long): Long? = if (id <= -DISS_OFFSET) -id - DISS_OFFSET else null
    fun topIdOf(id: Long): Long? = if (id in (-DISS_OFFSET + 1)..-TOP_OFFSET) -id - TOP_OFFSET else null
}
data class BannerItem(val picUrl: String, val title: String, val targetId: Long = 0)
data class UserProfile(val uid: Long, val nickname: String, val avatarUrl: String = "", val level: Int = 0, val follows: Int = 0, val followeds: Int = 0, val signature: String = "", /** 注册天数（/api/v1/user/detail 顶层 createDays；-1 = 未知）。第五轮第 24 项。 */ val createDays: Int = -1)
data class RecordItem(val song: Song, val playCount: Int, val score: Int = 0)
enum class PlaylistGroup(val title: String) { Liked("我喜欢的音乐"), Created("我创建的歌单"), Subscribed("我收藏的歌单") }
fun groupPlaylists(all: List<Playlist>, myUid: Long): Map<PlaylistGroup, List<Playlist>> {
    val liked = all.filter { it.name.contains("喜欢的音乐") }
    val created = all.filter { it !in liked && !it.subscribed && (myUid == 0L || it.creatorId == myUid) }
    val sub = all.filter { it !in liked && it.subscribed }
    return linkedMapOf(PlaylistGroup.Liked to liked, PlaylistGroup.Created to created, PlaylistGroup.Subscribed to sub)
}
