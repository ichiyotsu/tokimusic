package com.tokimusic.app.data

import android.content.Context
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Playlist
import com.tokimusic.app.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * 歌单详情快照（0.1.21）：在线成功打开过的歌单详情落盘到这里。
 * 用途只有一个——源被关闭 / 本地模式时，歌单详情页用快照呈现曲目，
 * 绝不为了凑全而联网（本地模式零连接底线）。没有快照的歌单在关源
 * 时只有列表元数据可用（调用方如实标注无缓存曲目）。
 *
 * 存储：SharedPreferences JSON 数组，新的在前，按歌单 id 去重，
 * 上限 [MAX_ENTRIES] 条，尾部淘汰。歌曲列表用 [encodeQueue] 的
 * 同款格式（与队列/歌曲缓存一致）。
 */
class PlaylistSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("tokimusic_playlist_snapshots", Context.MODE_PRIVATE)

    @Synchronized
    fun put(playlist: Playlist, tracks: List<Song>) {
        val rest = all().filterNot { it.first.id == playlist.id }
        save((listOf(playlist to tracks) + rest).take(MAX_ENTRIES))
    }

    fun get(id: Long): Pair<Playlist, List<Song>>? = all().firstOrNull { it.first.id == id }

    fun all(): List<Pair<Playlist, List<Song>>> = decodeSnapshots(prefs.getString(KEY, "") ?: "")

    private fun save(entries: List<Pair<Playlist, List<Song>>>) {
        prefs.edit().putString(KEY, encodeSnapshots(entries)).apply()
    }

    private companion object {
        const val KEY = "snapshots"
        const val MAX_ENTRIES = 60
    }
}

/** 快照编解码（纯函数，单测覆盖）：条目 = {playlist: {...}, songs: "<encodeQueue JSON>"}。 */
internal fun encodeSnapshots(entries: List<Pair<Playlist, List<Song>>>): String {
    val arr = JSONArray()
    for ((pl, tracks) in entries) {
        arr.put(
            JSONObject()
                .put(
                    "playlist",
                    JSONObject()
                        .put("id", pl.id)
                        .put("name", pl.name)
                        .put("coverUrl", pl.coverUrl)
                        .put("trackCount", pl.trackCount)
                        .put("creatorName", pl.creatorName)
                        .put("creatorId", pl.creatorId)
                        .put("subscribed", pl.subscribed)
                        .put("playCount", pl.playCount)
                        .put("description", pl.description)
                        .put("source", pl.source.name)
                        .put("sourceId", pl.sourceId),
                )
                .put("songs", encodeQueue(tracks)),
        )
    }
    return arr.toString()
}

internal fun decodeSnapshots(json: String): List<Pair<Playlist, List<Song>>> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val p = o.optJSONObject("playlist") ?: return@mapNotNull null
            val name = p.optString("name", "")
            if (name.isBlank()) return@mapNotNull null
            val source = runCatching { MusicSource.valueOf(p.optString("source", "NETEASE")) }
                .getOrDefault(MusicSource.NETEASE)
            Playlist(
                id = p.optLong("id", 0),
                name = name,
                coverUrl = p.optString("coverUrl", ""),
                trackCount = p.optInt("trackCount", 0),
                creatorName = p.optString("creatorName", ""),
                creatorId = p.optLong("creatorId", 0),
                subscribed = p.optBoolean("subscribed", false),
                playCount = p.optLong("playCount", 0),
                description = p.optString("description", ""),
                source = source,
                sourceId = p.optString("sourceId", ""),
            ) to decodeQueue(o.optString("songs", ""))
        }
    }.getOrDefault(emptyList())
}
