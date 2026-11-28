package com.tokimusic.app.data

import android.content.Context
import com.tokimusic.app.data.api.LyricPayload
import org.json.JSONObject

/**
 * 跨源借歌词的缓存——同一首歌不重复搜索匹配。YT 歌按 videoId、
 * 网易云歌按 "n:<id>" 存借到的歌词 payload（或未命中标记）：
 * - 命中长期有效（歌曲身份不会变）；
 * - 未命中 30 天过期（对方曲库可能后补这首歌）；
 * - 总量上限 [MAX_ENTRIES] 条，超出淘汰最旧（写入序记录）。
 * 全部本地（SharedPreferences），与歌词借用一样不外传任何数据。
 *
 * 本轮（0129）：条目带 schema 版本 [SCHEMA_VERSION]。0128 存的
 * 旧条目没有版本号且 QQ 命中只有行级 lrc——读到一律作废重拉，
 * 免得行级缓存挡住新 QRC 逐字；新条目多存 qrc 字段。
 */
class LyricBorrowCache(context: Context) {
    private val prefs = context.getSharedPreferences("tokimusic_lyric_borrow", Context.MODE_PRIVATE)

    sealed interface Entry {
        data class Hit(val source: String, val payload: LyricPayload) : Entry
        data object Miss : Entry
    }

    fun get(videoId: String): Entry? {
        val raw = prefs.getString("v:$videoId", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            if (o.optInt("ver", 1) < SCHEMA_VERSION) {
                prefs.edit().remove("v:$videoId").apply()
                return null
            }
            if (!o.optBoolean("found")) {
                val at = o.optLong("at", 0L)
                if (System.currentTimeMillis() - at > MISS_TTL_MS) {
                    prefs.edit().remove("v:$videoId").apply()
                    null
                } else {
                    Entry.Miss
                }
            } else {
                Entry.Hit(
                    source = o.optString("source"),
                    payload = LyricPayload(
                        yrc = o.optString("yrc").takeIf { it.isNotEmpty() },
                        lrc = o.optString("lrc").takeIf { it.isNotEmpty() },
                        tlyric = o.optString("tlyric").takeIf { it.isNotEmpty() },
                        romalrc = o.optString("romalrc").takeIf { it.isNotEmpty() },
                        qrc = o.optString("qrc").takeIf { it.isNotEmpty() },
                    ),
                )
            }
        }.getOrNull()
    }

    fun putHit(videoId: String, source: String, payload: LyricPayload) {
        val o = JSONObject()
            .put("ver", SCHEMA_VERSION)
            .put("found", true)
            .put("source", source)
            .put("yrc", payload.yrc ?: "")
            .put("lrc", payload.lrc ?: "")
            .put("tlyric", payload.tlyric ?: "")
            .put("romalrc", payload.romalrc ?: "")
            .put("qrc", payload.qrc ?: "")
            .put("at", System.currentTimeMillis())
        put(videoId, o)
    }

    fun putMiss(videoId: String) {
        val o = JSONObject().put("ver", SCHEMA_VERSION).put("found", false).put("at", System.currentTimeMillis())
        put(videoId, o)
    }

    private fun put(videoId: String, value: JSONObject) {
        val order = orderList().toMutableList()
        order.remove(videoId)
        order.add(videoId)
        val editor = prefs.edit().putString("v:$videoId", value.toString())
        while (order.size > MAX_ENTRIES) {
            val evicted = order.removeAt(0)
            editor.remove("v:$evicted")
        }
        editor.putString(ORDER_KEY, order.joinToString(",")).apply()
    }

    private fun orderList(): List<String> =
        (prefs.getString(ORDER_KEY, "") ?: "").split(",").filter { it.isNotEmpty() }

    companion object {
        private const val ORDER_KEY = "__order__"
        private const val MAX_ENTRIES = 60
        private const val MISS_TTL_MS = 30L * 24 * 60 * 60 * 1000
        /** 条目格式版本：1 = 0128（无 qrc），2 = 0129（带 qrc）；低于此一律作废。 */
        private const val SCHEMA_VERSION = 2
    }
}
