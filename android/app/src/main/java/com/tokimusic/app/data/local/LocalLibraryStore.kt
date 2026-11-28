package com.tokimusic.app.data.local
import com.tokimusic.app.data.AppStrings
import com.tokimusic.app.R

import android.content.Context
import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * 已导入本地文件的一条登记（0.1.20 第 7/8 项）：解密产物（filesDir）
 * 或原地引用（MediaStore/SAF 的 content URI）。origin* 记录它来自
 * 哪个源、源内 id 是什么——第 8 项本地播放靠它把「正在播的在线歌」
 * 匹配回本地文件。
 */
data class LocalTrack(
    /** "NETEASE" / "QQ" / ""（来源不明，如目录外的普通文件）。 */
    val originSource: String,
    /** 网易云 musicId / QQ songmid；取不到为空串（靠标题+歌手兜底匹配）。 */
    val originId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** 可直接播放的 URI（file:// 或 content://）。 */
    val uri: String,
    /** mp3 / flac / m4a / ogg / wav。 */
    val format: String,
    /** 封面 URI（本轮）：导入时从文件嵌入图提取落盘的 file://，或
     * MediaStore 相册图的 content://；读不到为空串。 */
    val cover: String = "",
    /** 歌词文件路径（本轮）：导入时把嵌入/旁挂歌词落盘到
     * filesDir/local_lyrics/ 的绝对路径；没有为空串。 */
    val lyric: String = "",
)

/**
 * 本地音乐库登记表（SharedPreferences JSON）：导入写入、本地页展示、
 * 播放解析查询，三处共用。条目按 uri 去重；putAll 是增量合并
 * （重新读取不会把已有条目冲掉）。
 */
class LocalLibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("tokimusic_local_library", Context.MODE_PRIVATE)

    fun all(): List<LocalTrack> {
        val raw = prefs.getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("uri", "")
                if (uri.isBlank()) return@mapNotNull null
                LocalTrack(
                    originSource = o.optString("originSource", ""),
                    originId = o.optString("originId", ""),
                    title = o.optString("title", ""),
                    artist = o.optString("artist", ""),
                    album = o.optString("album", ""),
                    durationMs = o.optLong("durationMs", 0),
                    uri = uri,
                    format = o.optString("format", ""),
                    cover = o.optString("cover", ""),
                    lyric = o.optString("lyric", ""),
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun putAll(tracks: List<LocalTrack>) {
        if (tracks.isEmpty()) return
        val byUri = LinkedHashMap<String, LocalTrack>()
        for (t in all()) byUri[t.uri] = t
        for (t in tracks) byUri[t.uri] = t
        save(byUri.values.toList())
    }

    @Synchronized
    fun remove(uri: String) {
        save(all().filterNot { it.uri == uri })
    }

    private fun save(tracks: List<LocalTrack>) {
        val arr = JSONArray()
        for (t in tracks) {
            arr.put(
                JSONObject()
                    .put("originSource", t.originSource)
                    .put("originId", t.originId)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("album", t.album)
                    .put("durationMs", t.durationMs)
                    .put("uri", t.uri)
                    .put("format", t.format)
                    .put("cover", t.cover)
                    .put("lyric", t.lyric),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /**
     * 第 8 项匹配：先按源 + 源内 id 精确命中（网易云比数字 id、QQ 比
     * songmid），再退到标题 + 主歌手归一化相等。匹配不到返回 null
     * （走网络取址）。
     */
    fun findFor(song: Song): LocalTrack? {
        val tracks = all()
        when (song.source) {
            MusicSource.NETEASE -> {
                tracks.firstOrNull {
                    it.originSource == "NETEASE" && it.originId.isNotBlank() && it.originId == song.id.toString()
                }?.let { return it }
            }
            MusicSource.QQ -> {
                if (song.sourceId.isNotBlank()) {
                    tracks.firstOrNull {
                        it.originSource == "QQ" && it.originId == song.sourceId
                    }?.let { return it }
                }
            }
            MusicSource.YOUTUBEMUSIC -> {
                // 本轮（0124）第 19 项：YT 与 QQ 同形——videoId 精确命中。
                if (song.sourceId.isNotBlank()) {
                    tracks.firstOrNull {
                        it.originSource == "YOUTUBEMUSIC" && it.originId == song.sourceId
                    }?.let { return it }
                }
            }
            MusicSource.LOCAL -> return null
        }
        val wantTitle = normalize(song.name)
        if (wantTitle.isBlank()) return null
        val wantArtist = normalize(song.artists.firstOrNull()?.name ?: "")
        return tracks.firstOrNull { track ->
            normalize(track.title) == wantTitle &&
                (wantArtist.isBlank() || normalize(track.artist.substringBefore(" / ").substringBefore(",")) == wantArtist)
        }
    }

    /** 登记条目 → 可播放的本地 Song（源记为 LOCAL，sourceId 即 URI 本身，与 MediaStore 本地歌同口径）。
     * 封面用登记时落盘/相册图的 URI（本轮）；没有就空串，展示侧走占位。 */
    fun toSong(track: LocalTrack): Song = Song(
        id = com.tokimusic.app.data.model.localSongId(track.uri),
        name = track.title.ifBlank { AppStrings.getOrFallback(R.string.local_unknown_song, "未知歌曲") },
        artists = track.artist.split(" / ").filter { it.isNotBlank() }.map { Artist(0, it) },
        albumName = track.album,
        picUrl = track.cover,
        durationMs = track.durationMs,
        source = MusicSource.LOCAL,
        sourceId = track.uri,
    )

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("\\s+"), "")

    private companion object {
        const val KEY = "tracks"
    }
}

/**
 * 登记表匹配键（0.1.21）：与 [LocalLibraryStore.findFor] 同口径的两类
 * 键——源 + 源内 id 精确键、标题 + 主歌手归一化键。列表置灰判断是
 * 同步渲染，不能逐行查登记表 + 文件存在性，所以把登记表压成键集合
 * 供 UI 同步查；播放门控仍以 findFor + 文件存在性为准（权威路径）。
 */
internal fun localTrackKeys(tracks: List<LocalTrack>): Set<String> {
    val keys = HashSet<String>()
    for (t in tracks) {
        if (t.originId.isNotBlank() && t.originSource.isNotBlank()) {
            keys += "id:${t.originSource}:${t.originId}"
        }
        val title = normalizeKeyText(t.title)
        if (title.isNotBlank()) {
            keys += "ta:$title|${normalizeKeyText(t.artist.substringBefore(" / ").substringBefore(","))}"
        }
    }
    return keys
}

/** 这首歌是否命中登记表（键口径，与 findFor 的两段匹配一致）。 */
internal fun isLocallyAvailable(song: Song, keys: Set<String>): Boolean {
    when (song.source) {
        MusicSource.NETEASE -> if ("id:NETEASE:${song.id}" in keys) return true
        MusicSource.QQ -> if (song.sourceId.isNotBlank() && "id:QQ:${song.sourceId}" in keys) return true
        MusicSource.YOUTUBEMUSIC -> if (song.sourceId.isNotBlank() && "id:YOUTUBEMUSIC:${song.sourceId}" in keys) return true
        MusicSource.LOCAL -> return true
    }
    val title = normalizeKeyText(song.name)
    if (title.isBlank()) return false
    return "ta:$title|${normalizeKeyText(song.artists.firstOrNull()?.name ?: "")}" in keys
}

private fun normalizeKeyText(text: String): String =
    text.lowercase().replace(Regex("\\s+"), "")
