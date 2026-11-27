package com.tokimusic.app.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 已下载歌单的一条登记（本轮 0124 第 9 项）：原歌单的来源/身份 +
 * 下载到本地的曲目 URI 顺序。音频文件本体在 filesDir/local_import/
 * download/，曲目登记进 LocalLibraryStore（带真实 originSource/
 * originId，本地优先播放靠它命中）；本表只记「哪个歌单下了哪些」，
 * 本地页据此把下载歌单按歌单格式镜像展示。
 */
data class DownloadedPlaylist(
    /** "NETEASE" / "QQ" / "YOUTUBEMUSIC"。 */
    val source: String,
    /** 源内歌单 id（网易云数字 id；QQ 为其合成 id）。 */
    val playlistId: Long,
    val name: String,
    val creator: String,
    val coverUrl: String,
    /** 本地详情路由用的虚拟 id（-(300000 + seq)，创建时分配、终身不变）。 */
    val virtualId: Long,
    /** 已成功落盘并登记的曲目 URI（保持歌单原顺序；失败的歌不在内）。 */
    val trackUris: List<String>,
    val updatedAtMs: Long,
)

/** 下载歌单虚拟 id 段基数（与本地三桶 -7/-8/-9、回顾 -(100+月键)、音乐记忆 -(700+月键) 错开）。 */
const val DOWNLOADED_ID_BASE = 300_000L

fun downloadedVirtualId(seq: Int): Long = -(DOWNLOADED_ID_BASE + seq)

fun isDownloadedPlaylistId(id: Long): Boolean =
    id <= -DOWNLOADED_ID_BASE && id > -(DOWNLOADED_ID_BASE + 1_000_000L)

/** 歌单下载状态（纯函数，JVM 单测覆盖；DOWNLOADING 是运行时态，不在此列）。 */
enum class PlaylistDownloadState { NOT_DOWNLOADED, PARTIAL, DOWNLOADED }

fun playlistDownloadState(entry: DownloadedPlaylist?, registeredUris: Set<String>): PlaylistDownloadState {
    if (entry == null || entry.trackUris.isEmpty()) return PlaylistDownloadState.NOT_DOWNLOADED
    val have = entry.trackUris.count { it in registeredUris }
    return when {
        have == 0 -> PlaylistDownloadState.NOT_DOWNLOADED
        have == entry.trackUris.size -> PlaylistDownloadState.DOWNLOADED
        else -> PlaylistDownloadState.PARTIAL
    }
}

/** 已下载首数（纯函数）：登记里仍在本地库中的曲目数。 */
fun downloadedHaveCount(entry: DownloadedPlaylist?, registeredUris: Set<String>): Int =
    entry?.trackUris?.count { it in registeredUris } ?: 0

/**
 * 下载歌单登记表（SharedPreferences JSON）：下载完成写入、本地页
 * 展示、详情路由查询。按 (source, playlistId) 唯一；重下载同一歌单
 * 更新原条目（virtualId 不变）。
 */
class DownloadedPlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("tokimusic_downloaded_playlists", Context.MODE_PRIVATE)

    fun all(): List<DownloadedPlaylist> {
        val raw = prefs.getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("items") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val uris = o.optJSONArray("trackUris") ?: JSONArray()
                DownloadedPlaylist(
                    source = o.optString("source", ""),
                    playlistId = o.optLong("playlistId", 0),
                    name = o.optString("name", ""),
                    creator = o.optString("creator", ""),
                    coverUrl = o.optString("coverUrl", ""),
                    virtualId = o.optLong("virtualId", 0),
                    trackUris = (0 until uris.length()).map { uris.optString(it) }.filter { it.isNotBlank() },
                    updatedAtMs = o.optLong("updatedAtMs", 0),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun find(source: String, playlistId: Long): DownloadedPlaylist? =
        all().firstOrNull { it.source == source && it.playlistId == playlistId }

    fun findByVirtualId(virtualId: Long): DownloadedPlaylist? =
        all().firstOrNull { it.virtualId == virtualId }

    /** 写入/更新一条；新歌单分配下一个 virtualId，已有歌单沿用原 id。返回落盘后的条目。 */
    @Synchronized
    fun put(
        source: String,
        playlistId: Long,
        name: String,
        creator: String,
        coverUrl: String,
        trackUris: List<String>,
        nowMs: Long,
    ): DownloadedPlaylist {
        val items = all().toMutableList()
        val existing = items.firstOrNull { it.source == source && it.playlistId == playlistId }
        val entry = DownloadedPlaylist(
            source = source,
            playlistId = playlistId,
            name = name,
            creator = creator,
            coverUrl = coverUrl,
            virtualId = existing?.virtualId ?: downloadedVirtualId(nextSeq()),
            trackUris = trackUris,
            updatedAtMs = nowMs,
        )
        if (existing != null) {
            items[items.indexOf(existing)] = entry
        } else {
            items += entry
        }
        save(items)
        return entry
    }

    @Synchronized
    fun remove(source: String, playlistId: Long) {
        save(all().filterNot { it.source == source && it.playlistId == playlistId })
    }

    private fun nextSeq(): Int {
        val raw = prefs.getString(KEY, "") ?: ""
        val stored = runCatching { JSONObject(raw).optInt("nextSeq", 0) }.getOrDefault(0)
        // 防御：seq 至少大于现存最大已用值，避免异常数据下撞 id。
        val maxUsed = all().maxOfOrNull { (-it.virtualId - DOWNLOADED_ID_BASE).toInt() } ?: -1
        return maxOf(stored, maxUsed + 1)
    }

    private fun save(items: List<DownloadedPlaylist>) {
        val arr = JSONArray()
        for (e in items) {
            val uris = JSONArray()
            e.trackUris.forEach { uris.put(it) }
            arr.put(
                JSONObject()
                    .put("source", e.source)
                    .put("playlistId", e.playlistId)
                    .put("name", e.name)
                    .put("creator", e.creator)
                    .put("coverUrl", e.coverUrl)
                    .put("virtualId", e.virtualId)
                    .put("trackUris", uris)
                    .put("updatedAtMs", e.updatedAtMs),
            )
        }
        val maxUsed = items.maxOfOrNull { (-it.virtualId - DOWNLOADED_ID_BASE).toInt() } ?: -1
        val root = JSONObject().put("items", arr).put("nextSeq", maxUsed + 1)
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    private companion object {
        const val KEY = "downloaded_playlists"
    }
}
