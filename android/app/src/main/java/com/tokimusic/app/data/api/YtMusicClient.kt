package com.tokimusic.app.data.api
import com.tokimusic.app.data.AppStrings
import com.tokimusic.app.R

import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.data.model.ytSongId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * YouTube Music 客户端（本轮 0124 第 19 项）——按 spike 报告
 * （~/workspace/work/youtubemusic-spike/REPORT.md，2026-11-27 本机
 * 实测）手写实现（clean-room，只按协议，不用任何 GPL 代码）：
 * - 搜索：WEB_REMIX 上下文（music.youtube.com/youtubei/v1/search，
 *   歌曲过滤 params），结果 musicResponsiveListItemRenderer 映射 Song。
 * - 播放：先取匿名 visitorData（visitor_id，WEB 上下文，可缓存），
 *   再用 VISIONOS 上下文取直链（无需解签），失败回退 ANDROID_VR；
 *   选流 itag 251(opus) 优先、140(AAC) 回退、再最高码率音频。
 *   LOGIN_REQUIRED / UNPLAYABLE 一律 Unavailable，绝不伪造 URL。
 * - 歌词：next → 歌词 browseId（MPLYt_…）→ browse →
 *   musicDescriptionShelfRenderer 纯文本（无时间轴，调用方按纯文本
 *   降级展示）。
 * - 全程无登录、无凭据；唯一的会话状态是 visitorData。
 * 客户端版本集中在 [YtClients]——版本滚动快，腐烂时只改这一处。
 */
object YtClients {
    /** YouTube 自家页面内嵌的公开 Web 客户端 key（ytcfg INNERTUBE_API_KEY，非用户凭据）。 */
    const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    const val WEB_REMIX_VERSION = "1.20261124.13.00"
    const val WEB_VERSION = "2.20251120.01.00"
    const val VISIONOS_VERSION = "1.02"
    const val ANDROID_VR_VERSION = "1.65.10"
    /** 搜索的「歌曲」过滤参数（spike 实测值，原样发送）。 */
    const val SONG_PARAMS = "EgWKAQIIAWoOEAMQBBAJEA4QChAFEBU%3D"
    const val UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    const val UA_VISIONOS = "com.apple.tv/1.02 (RealityDevice14,1; visionOS 18.0)"
    const val UA_ANDROID_VR = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
}

/** visitorData 的持久层（SettingsStore 实现）。 */
interface YtVisitorStore {
    var ytVisitorData: String
}

sealed interface YtSongUrlResult {
    data class Url(val url: String) : YtSongUrlResult
    data class Unavailable(val reason: String) : YtSongUrlResult
}

data class YtAudioFormat(val itag: Int, val url: String, val bitrate: Int, val mimeType: String)

/** 选流（纯函数，JVM 单测覆盖）：251(opus) → 140(AAC) → 最高码率；只认带直链的音频格式（无 url 的是 signatureCipher 格式，跳过）。 */
fun selectYtAudioFormat(formats: List<YtAudioFormat>): YtAudioFormat? {
    val audio = formats.filter { it.url.isNotBlank() && it.mimeType.startsWith("audio") }
    if (audio.isEmpty()) return null
    audio.firstOrNull { it.itag == 251 }?.let { return it }
    audio.firstOrNull { it.itag == 140 }?.let { return it }
    return audio.maxByOrNull { it.bitrate }
}

/** player 响应 → 格式列表（纯函数，单测覆盖）。 */
fun parseYtFormats(player: JSONObject): List<YtAudioFormat> {
    val streaming = player.optJSONObject("streamingData") ?: return emptyList()
    val out = ArrayList<YtAudioFormat>()
    for (key in listOf("formats", "adaptiveFormats")) {
        val arr = streaming.optJSONArray(key) ?: continue
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            out += YtAudioFormat(
                itag = f.optInt("itag", 0),
                url = f.optString("url", ""),
                bitrate = f.optInt("bitrate", 0),
                mimeType = f.optString("mimeType", ""),
            )
        }
    }
    return out
}

private val ytDurationText = Regex("^\\d{1,3}:\\d{2}(:\\d{2})?$")

/** 时长文本 "m:ss" / "h:mm:ss" → 毫秒（纯函数）。 */
fun parseYtDurationMs(text: String): Long {
    if (!ytDurationText.matches(text.trim())) return 0
    val parts = text.trim().split(":").map { it.toLongOrNull() ?: return 0 }
    return parts.fold(0L) { acc, v -> acc * 60 + v } * 1000
}

/** 搜索响应解析（纯函数，JVM 单测覆盖）：递归收集 musicResponsiveListItemRenderer 并映射为 Song。 */
fun parseYtSearchResponse(root: JSONObject): List<Song> {
    val renderers = ArrayList<JSONObject>()
    fun walk(node: Any?) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { renderers += it }
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()))
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
        }
    }
    walk(root)
    return renderers.mapNotNull { item -> parseYtSearchItem(item) }
}

private fun parseYtSearchItem(item: JSONObject): Song? {
    val videoId = item.optJSONObject("playlistItemData")?.optString("videoId", "")?.takeIf { it.isNotBlank() }
        ?: return null
    val flex = item.optJSONArray("flexColumns") ?: return null
    fun columnText(index: Int): List<String> {
        val col = flex.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs") ?: return emptyList()
        return (0 until col.length()).map { col.optJSONObject(it)?.optString("text", "") ?: "" }
    }
    val title = columnText(0).firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
    val segments = columnText(1).map { it.trim() }.filter { it.isNotBlank() && it != "•" }
    val durationText = segments.lastOrNull { ytDurationText.matches(it) }
    val artist = segments.firstOrNull() ?: ""
    val album = segments.getOrNull(1)?.takeIf { it != durationText } ?: ""
    val thumbs = item.optJSONObject("thumbnail")
        ?.optJSONObject("musicThumbnailRenderer")
        ?.optJSONObject("thumbnail")
        ?.optJSONArray("thumbnails")
    var cover = ""
    if (thumbs != null && thumbs.length() > 0) {
        cover = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
        // 缩略图默认 60px，改取 540px（YT 图片服务的尺寸参数）。
        cover = cover.replace(Regex("=w\\d+-h\\d+"), "=w540-h540")
    }
    return Song(
        id = ytSongId(videoId),
        name = title,
        artists = if (artist.isBlank()) emptyList() else listOf(Artist(0, artist)),
        albumName = album,
        picUrl = cover,
        durationMs = durationText?.let { parseYtDurationMs(it) } ?: 0,
        source = MusicSource.YOUTUBEMUSIC,
        sourceId = videoId,
    )
}

/** browse 响应 → 歌词纯文本（纯函数，单测覆盖）：找 musicDescriptionShelfRenderer 的 description runs 拼接。 */
fun parseYtLyricsText(root: JSONObject): String {
    var found: JSONObject? = null
    fun walk(node: Any?) {
        if (found != null) return
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicDescriptionShelfRenderer")?.let { found = it; return }
                val keys = node.keys()
                while (keys.hasNext()) {
                    walk(node.opt(keys.next()))
                    if (found != null) return
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                walk(node.opt(i))
                if (found != null) return
            }
        }
    }
    walk(root)
    val shelf = found ?: return ""
    val runs = shelf.optJSONObject("description")?.optJSONArray("runs") ?: return ""
    val sb = StringBuilder()
    for (i in 0 until runs.length()) {
        sb.append(runs.optJSONObject(i)?.optString("text", "") ?: "")
    }
    return sb.toString().trim()
}

/** next 响应 → 歌词 browseId（纯函数，单测覆盖）：递归找以 MPLYt 开头的 browseId。 */
fun findYtLyricsBrowseId(root: JSONObject): String? {
    var found: String? = null
    fun walk(node: Any?) {
        if (found != null) return
        when (node) {
            is JSONObject -> {
                val id = node.optString("browseId", "")
                if (id.startsWith("MPLYt")) {
                    found = id
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    walk(node.opt(keys.next()))
                    if (found != null) return
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                walk(node.opt(i))
                if (found != null) return
            }
        }
    }
    walk(root)
    return found
}

class YtMusicClient(private val store: YtVisitorStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun webRemixContext(): JSONObject = JSONObject()
        .put("clientName", "WEB_REMIX")
        .put("clientVersion", YtClients.WEB_REMIX_VERSION)
        .put("hl", "en")
        .put("gl", "US")

    private suspend fun post(path: String, host: String, context: JSONObject, ua: String, extra: JSONObject.() -> Unit = {}): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("context", JSONObject().put("client", context))
                .apply(extra)
            val request = Request.Builder()
                .url("https://$host/youtubei/v1/$path?key=${YtClients.API_KEY}&prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("User-Agent", ua)
                .apply {
                    if (host == "music.youtube.com") {
                        header("Origin", "https://music.youtube.com")
                        header("Referer", "https://music.youtube.com/")
                    }
                }
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException(ApiErrorKind.Unreachable, httpCode = resp.code, message = "YouTube HTTP ${resp.code}")
                JSONObject(text)
            }
        }

    suspend fun searchSongs(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        val root = post("search", "music.youtube.com", webRemixContext(), YtClients.UA_WEB) {
            put("query", query)
            put("params", YtClients.SONG_PARAMS)
        }
        return parseYtSearchResponse(root)
    }

    private suspend fun ensureVisitorData(forceRefresh: Boolean = false): String {
        if (!forceRefresh && store.ytVisitorData.isNotBlank()) return store.ytVisitorData
        val context = JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", YtClients.WEB_VERSION)
            .put("hl", "en")
            .put("gl", "US")
        val root = post("visitor_id", "www.youtube.com", context, YtClients.UA_WEB)
        val vd = root.optJSONObject("responseContext")?.optString("visitorData", "").orEmpty()
        if (vd.isNotBlank()) store.ytVisitorData = vd
        return vd
    }

    private fun visionOsContext(visitorData: String): JSONObject = JSONObject()
        .put("clientName", "VISIONOS")
        .put("clientVersion", YtClients.VISIONOS_VERSION)
        .put("deviceMake", "Apple")
        .put("deviceModel", "RealityDevice14,1")
        .put("osName", "visionOS")
        .put("osVersion", "18.0.20R239")
        .put("hl", "en")
        .put("gl", "US")
        .put("visitorData", visitorData)

    private fun androidVrContext(visitorData: String): JSONObject = JSONObject()
        .put("clientName", "ANDROID_VR")
        .put("clientVersion", YtClients.ANDROID_VR_VERSION)
        .put("deviceMake", "Oculus")
        .put("deviceModel", "Quest 3")
        .put("androidSdkVersion", 32)
        .put("osName", "Android")
        .put("osVersion", "12L")
        .put("hl", "en")
        .put("gl", "US")
        .put("visitorData", visitorData)

    private suspend fun playerOnce(videoId: String, context: JSONObject, ua: String): YtSongUrlResult {
        val root = post("player", "www.youtube.com", context, ua) {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val status = root.optJSONObject("playabilityStatus")
        val state = status?.optString("status", "") ?: ""
        if (state != "OK") {
            val reason = status?.optString("reason", "").orEmpty()
            return YtSongUrlResult.Unavailable(if (reason.isBlank()) state else "$state: $reason")
        }
        val format = selectYtAudioFormat(parseYtFormats(root))
            ?: return YtSongUrlResult.Unavailable(AppStrings.getOrFallback(R.string.err_yt_no_stream, "无可用音频流"))
        return YtSongUrlResult.Url(format.url)
    }

    /**
     * 取播放直链：VISIONOS（+visitorData）→ 失败换新 visitorData 重试
     * 一次 → ANDROID_VR 回退 → Unavailable。LOGIN_REQUIRED 等状态原样
     * 进 reason 供排障（报告第 8 项：错误要带 playabilityStatus 原文）。
     */
    suspend fun songUrl(videoId: String): YtSongUrlResult {
        if (videoId.isBlank()) return YtSongUrlResult.Unavailable(AppStrings.getOrFallback(R.string.err_yt_empty_id, "videoId 为空"))
        return try {
            var vd = ensureVisitorData()
            var result = playerOnce(videoId, visionOsContext(vd), YtClients.UA_VISIONOS)
            if (result is YtSongUrlResult.Unavailable && result.reason.startsWith("LOGIN_REQUIRED")) {
                vd = ensureVisitorData(forceRefresh = true)
                result = playerOnce(videoId, visionOsContext(vd), YtClients.UA_VISIONOS)
            }
            if (result is YtSongUrlResult.Unavailable) {
                val vr = runCatching { playerOnce(videoId, androidVrContext(vd), YtClients.UA_ANDROID_VR) }
                    .getOrElse { YtSongUrlResult.Unavailable(it.message ?: AppStrings.getOrFallback(R.string.err_yt_vr_failed, "ANDROID_VR 失败")) }
                if (vr is YtSongUrlResult.Url) return vr
            }
            result
        } catch (e: Exception) {
            YtSongUrlResult.Unavailable(e.message ?: AppStrings.getOrFallback(R.string.err_yt_network, "网络错误"))
        }
    }

    /** 歌词纯文本（无时间轴）：next 找歌词 browseId → browse 取全文；没有返回空 payload。 */
    suspend fun lyric(videoId: String): LyricPayload {
        if (videoId.isBlank()) return LyricPayload()
        val next = post("next", "music.youtube.com", webRemixContext(), YtClients.UA_WEB) {
            put("videoId", videoId)
        }
        val browseId = findYtLyricsBrowseId(next) ?: return LyricPayload()
        val page = post("browse", "music.youtube.com", webRemixContext(), YtClients.UA_WEB) {
            put("browseId", browseId)
        }
        val text = parseYtLyricsText(page)
        return if (text.isBlank()) LyricPayload() else LyricPayload(lrc = text)
    }
}
