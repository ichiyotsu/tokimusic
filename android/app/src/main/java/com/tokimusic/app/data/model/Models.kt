package com.tokimusic.app.data.model
data class Artist(val id: Long, val name: String)
data class Song(val id: Long, val name: String, val artists: List<Artist>, val albumName: String = "", val picUrl: String = "", val durationMs: Long = 0, val fee: Int = 0) {
    val artistText: String get() = artists.joinToString(" / ") { it.name }
}
data class Playlist(val id: Long, val name: String, val coverUrl: String = "", val trackCount: Int = 0, val creatorName: String = "", val creatorId: Long = 0, val subscribed: Boolean = false, val playCount: Long = 0, val description: String = "")
data class BannerItem(val picUrl: String, val title: String, val targetId: Long = 0)
data class UserProfile(val uid: Long, val nickname: String, val avatarUrl: String = "", val level: Int = 0, val follows: Int = 0, val followeds: Int = 0, val signature: String = "")
data class RecordItem(val song: Song, val playCount: Int, val score: Int = 0)
enum class PlaylistGroup(val title: String) { Liked("我喜欢的音乐"), Created("我创建的歌单"), Subscribed("我收藏的歌单") }
fun groupPlaylists(all: List<Playlist>, myUid: Long): Map<PlaylistGroup, List<Playlist>> {
    val liked = all.filter { it.name.contains("喜欢的音乐") }
    val created = all.filter { it !in liked && !it.subscribed && (myUid == 0L || it.creatorId == myUid) }
    val sub = all.filter { it !in liked && it.subscribed }
    return linkedMapOf(PlaylistGroup.Liked to liked, PlaylistGroup.Created to created, PlaylistGroup.Subscribed to sub)
}
