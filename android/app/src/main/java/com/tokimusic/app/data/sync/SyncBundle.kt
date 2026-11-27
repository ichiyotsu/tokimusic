package com.tokimusic.app.data.sync

import org.json.JSONObject

/**
 * 本轮（0124）第 18 项：Drive 同步包（版本化 JSON，存用户自己 Drive 的
 * appDataFolder/tokimusic-sync.json）。每个 section 带自己的 updatedAt
 * 戳，合并按 section 取新（playlists 额外做并集，见 merge）。
 *
 * section 约定：
 * - "settings"：SettingsStore 的偏好快照（凭据类键不进同步包）。
 * - "level"：MusicLevelStore 状态（戳 = 最后一次得分时间）。
 * - "taste"：TasteSignalsStore 的计数（戳 = 最后一次记录时间）。
 * - "playlists"：已下载歌单结构（名字 + 歌曲身份，不含音频）。
 * - "liked"：各源喜欢歌曲身份列表。
 */
data class SyncSection(val updatedAt: Long, val data: JSONObject)

data class SyncBundle(
    val version: Int = SyncBundleCodec.CURRENT_VERSION,
    val sections: Map<String, SyncSection> = emptyMap(),
)

object SyncBundleCodec {
    const val CURRENT_VERSION = 1
    const val FILE_NAME = "tokimusic-sync.json"

    const val SECTION_SETTINGS = "settings"
    const val SECTION_LEVEL = "level"
    const val SECTION_TASTE = "taste"
    const val SECTION_PLAYLISTS = "playlists"
    const val SECTION_LIKED = "liked"

    fun encode(bundle: SyncBundle): String {
        val sections = JSONObject()
        for ((key, section) in bundle.sections) {
            sections.put(
                key,
                JSONObject()
                    .put("updatedAt", section.updatedAt)
                    .put("data", section.data),
            )
        }
        return JSONObject()
            .put("version", bundle.version)
            .put("sections", sections)
            .toString()
    }

    fun decode(json: String?): SyncBundle? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(json)
            val version = root.optInt("version", 0)
            if (version <= 0 || version > CURRENT_VERSION) return null
            val sectionsObj = root.optJSONObject("sections") ?: return SyncBundle(version)
            val sections = LinkedHashMap<String, SyncSection>()
            val keys = sectionsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val sectionObj = sectionsObj.optJSONObject(key) ?: continue
                sections[key] = SyncSection(
                    updatedAt = sectionObj.optLong("updatedAt", 0L),
                    data = sectionObj.optJSONObject("data") ?: JSONObject(),
                )
            }
            SyncBundle(version, sections)
        }.getOrNull()
    }

    /**
     * 合并规则（条 18 指定：下载优先、按 section 的 updatedAt 取新）：
     * - 一般 section：戳新的一方胜出；同戳保留 local。
     * - "playlists"：在胜出一方的基础上并集另一方独有的歌单条目
     *   （键 = source + playlistId），避免一台设备下载的歌单被另一台
     *   的旧包覆盖丢失。
     * - "liked"：两边的身份列表取并集（喜欢只增不减的语义最安全）。
     */
    fun merge(local: SyncBundle, remote: SyncBundle): SyncBundle {
        val merged = LinkedHashMap<String, SyncSection>()
        val keys = local.sections.keys + remote.sections.keys
        for (key in keys) {
            val l = local.sections[key]
            val r = remote.sections[key]
            merged[key] = when {
                l == null -> r!!
                r == null -> l
                key == SECTION_PLAYLISTS -> mergePlaylistsSection(l, r)
                key == SECTION_LIKED -> mergeLikedSection(l, r)
                r.updatedAt > l.updatedAt -> r
                else -> l
            }
        }
        return SyncBundle(CURRENT_VERSION, merged)
    }

    private fun mergePlaylistsSection(l: SyncSection, r: SyncSection): SyncSection {
        val winner = if (r.updatedAt > l.updatedAt) r else l
        val loser = if (winner === l) r else l
        val items = winner.data.optJSONArray("items") ?: org.json.JSONArray()
        val seen = HashSet<String>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            seen.add(item.optString("source") + ":" + item.optString("playlistId"))
        }
        val mergedItems = org.json.JSONArray()
        for (i in 0 until items.length()) mergedItems.put(items.get(i))
        val loserItems = loser.data.optJSONArray("items")
        if (loserItems != null) {
            for (i in 0 until loserItems.length()) {
                val item = loserItems.optJSONObject(i) ?: continue
                val key = item.optString("source") + ":" + item.optString("playlistId")
                if (seen.add(key)) mergedItems.put(item)
            }
        }
        return SyncSection(
            updatedAt = maxOf(l.updatedAt, r.updatedAt),
            data = JSONObject().put("items", mergedItems),
        )
    }

    private fun mergeLikedSection(l: SyncSection, r: SyncSection): SyncSection {
        val merged = JSONObject()
        val sources = listOf("netease", "qq", "youtube")
        for (source in sources) {
            val ids = LinkedHashSet<String>()
            for (section in listOf(l, r)) {
                val arr = section.data.optJSONArray(source) ?: continue
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i)
                    if (id.isNotBlank()) ids.add(id)
                }
            }
            val out = org.json.JSONArray()
            ids.forEach { out.put(it) }
            merged.put(source, out)
        }
        return SyncSection(maxOf(l.updatedAt, r.updatedAt), merged)
    }
}
