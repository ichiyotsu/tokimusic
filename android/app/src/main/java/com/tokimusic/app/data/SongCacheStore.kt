package com.tokimusic.app.data

import android.content.Context
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song

/**
 * 歌曲对象缓存（0.1.20）：PlayEventLog 只存歌曲 id/源/歌手，回顾歌单
 * 与重温回顾要把 id 还原成完整 [Song]（歌名/封面）。本缓存由
 * AppViewModel 在每次 play/playNext 时写入队列里的歌——事件只来自
 * 播放，所以「播过的歌」必在这里（LRU 挤掉的除外，那时网易云歌还可
 * 经 songDetail 兜底，QQ/本地歌跳过，见本轮 NOTES 的覆盖限制）。
 *
 * 存储与队列持久化同格式（encodeQueue 的 JSON 数组），上限
 * [MAX_ENTRIES] 条，新的在前、按 (source,id) 去重、尾部淘汰。
 */
class SongCacheStore(context: Context) {
    private val prefs = context.getSharedPreferences("tokimusic_song_cache", Context.MODE_PRIVATE)

    @Synchronized
    fun putAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        val existing = all()
        val seen = HashSet<Pair<MusicSource, Long>>()
        val merged = ArrayList<Song>(songs.size + existing.size)
        for (song in songs + existing) {
            if (seen.add(song.source to song.id)) merged.add(song)
            if (merged.size >= MAX_ENTRIES) break
        }
        prefs.edit().putString(KEY, encodeQueue(merged)).apply()
    }

    fun get(source: MusicSource, id: Long): Song? =
        all().firstOrNull { it.source == source && it.id == id }

    fun all(): List<Song> = decodeQueue(prefs.getString(KEY, "") ?: "")

    private companion object {
        const val KEY = "songs"
        const val MAX_ENTRIES = 800
    }
}
