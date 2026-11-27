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

    /** 条 18 同步：最后一次记录时间（合并戳）与计数导出/合并。 */
    fun lastRecordMs(): Long = prefs.getLong(KEY_LAST_RECORD, 0L)

    fun exportCountsJson(): JSONObject = JSONObject()
        .put("search", countsToJson(load(KEY_SEARCH)))
        .put("artist", countsToJson(load(KEY_ARTIST)))

    /** 远端计数按 key 取 max 合并（计数只增，取大不丢）。 */
    @Synchronized
    fun mergeCountsJson(json: JSONObject) {
        mergeKind(KEY_SEARCH, json.optJSONObject("search"))
        mergeKind(KEY_ARTIST, json.optJSONObject("artist"))
    }

    private fun countsToJson(counts: Map<String, Int>): JSONObject {
        val o = JSONObject()
        for ((k, v) in counts) o.put(k, v)
        return o
    }

    private fun mergeKind(kind: String, remote: JSONObject?) {
        if (remote == null) return
        val counts = load(kind).toMutableMap()
        val keys = remote.keys()
        var changed = false
        while (keys.hasNext()) {
            val k = keys.next()
            val v = remote.optInt(k, 0)
            if (v > (counts[k] ?: 0)) { counts[k] = v; changed = true }
        }
        if (changed) {
            prefs.edit().putString(kind, countsToJson(counts).toString()).apply()
        }
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
        prefs.edit().putLong(KEY_LAST_RECORD, System.currentTimeMillis()).apply()
        val counts = load(kind).toMutableMap()
        counts[key] = (counts[key] ?: 0) + 1
        val o = JSONObject()
        for ((k, v) in counts) o.put(k, v)
        prefs.edit().putString(kind, o.toString()).apply()
    }

    private companion object {
        const val KEY_LAST_RECORD = "last_record_ms"
        const val KEY_SEARCH = "search_counts"
        const val KEY_ARTIST = "artist_counts"
    }
}
