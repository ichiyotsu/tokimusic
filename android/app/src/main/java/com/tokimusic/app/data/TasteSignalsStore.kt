package com.tokimusic.app.data

import android.content.Context
import org.json.JSONObject

/**
 * 口味训练信号（本轮 0124 第 13 项）：记录「常搜 / 常看」行为供口味
 * 画像加权——搜索关键词计数、歌手浏览计数（歌手页与专辑页都折算到
 * 歌手名）。只存本机（SharedPreferences），不进仓库、不上传（用户
 * 2026-11-27 定：经验只存本地 App 内）。
 * 一次性行为不计入画像：加权在 TasteProfile.signalBoosts 里按
 * ≥2 次门槛过滤，这里只如实计数。
 */
class TasteSignalsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tokimusic_taste_signals", Context.MODE_PRIVATE)

    @Synchronized
    fun recordSearch(keyword: String) {
        val key = keyword.trim()
        if (key.isNotBlank()) bump(KEY_SEARCH, key)
    }

    @Synchronized
    fun recordArtistView(artistName: String) {
        val key = artistName.trim()
        if (key.isNotBlank()) bump(KEY_ARTIST, key)
    }

    /** 专辑浏览折算到专辑歌手（专辑页打开时调用）。 */
    @Synchronized
    fun recordAlbumView(artistName: String) {
        val key = artistName.trim()
        if (key.isNotBlank()) bump(KEY_ARTIST, key)
    }

    fun searchCounts(): Map<String, Int> = load(KEY_SEARCH)

    fun artistCounts(): Map<String, Int> = load(KEY_ARTIST)

    private fun load(kind: String): Map<String, Int> {
        val raw = prefs.getString(kind, "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            val out = LinkedHashMap<String, Int>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = o.optInt(k, 0)
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun bump(kind: String, key: String) {
        val counts = load(kind).toMutableMap()
        counts[key] = (counts[key] ?: 0) + 1
        val o = JSONObject()
        for ((k, v) in counts) o.put(k, v)
        prefs.edit().putString(kind, o.toString()).apply()
    }

    private companion object {
        const val KEY_SEARCH = "search_counts"
        const val KEY_ARTIST = "artist_counts"
    }
}
