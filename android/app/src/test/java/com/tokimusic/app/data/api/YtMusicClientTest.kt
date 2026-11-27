package com.tokimusic.app.data.api

import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.ytSongId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本轮（0124）第 19 项：YouTube Music 客户端纯函数——搜索响应解析与
 * 选流。fixture 按 spike 实测响应形状构造（musicResponsiveListItemRenderer /
 * streamingData adaptiveFormats）。
 */
class YtMusicClientTest {

    private fun runsOf(vararg texts: String): JSONObject {
        val runs = org.json.JSONArray()
        for (t in texts) runs.put(JSONObject().put("text", t))
        return JSONObject().put("runs", runs)
    }

    private fun flexColumn(texts: List<String>): JSONObject = JSONObject().put(
        "musicResponsiveListItemFlexColumnRenderer",
        JSONObject().put("text", runsOf(*texts.toTypedArray())),
    )

    private fun searchItem(videoId: String, title: String, byline: List<String>, thumbUrl: String): JSONObject {
        val thumbs = org.json.JSONArray().put(JSONObject().put("url", thumbUrl).put("width", 60).put("height", 60))
        val flexColumns = org.json.JSONArray()
            .put(flexColumn(listOf(title)))
            .put(flexColumn(byline))
        val renderer = JSONObject()
            .put("playlistItemData", JSONObject().put("videoId", videoId))
            .put("flexColumns", flexColumns)
            .put(
                "thumbnail",
                JSONObject().put(
                    "musicThumbnailRenderer",
                    JSONObject().put("thumbnail", JSONObject().put("thumbnails", thumbs)),
                ),
            )
        return JSONObject().put("musicResponsiveListItemRenderer", renderer)
    }

    private fun searchFixture(): JSONObject {
        val items = org.json.JSONArray()
            .put(searchItem("SJKoWAd5ySo", "晴天", listOf("周杰伦", " • ", "叶惠美", " • ", "4:29"), "https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"))
            .put(searchItem("ORrFJ63nlcA", "Perfect", listOf("Ed Sheeran", " • ", "4:23"), "https://lh3.googleusercontent.com/xyz=w60-h60-l90-rj"))
        val shelf = JSONObject().put("musicShelfRenderer", JSONObject().put("contents", items))
        val section = JSONObject().put("sectionListRenderer", JSONObject().put("contents", org.json.JSONArray().put(shelf)))
        val tab = JSONObject().put("tabRenderer", JSONObject().put("content", section))
        return JSONObject().put(
            "contents",
            JSONObject().put("tabbedSearchResultsRenderer", JSONObject().put("tabs", org.json.JSONArray().put(tab))),
        )
    }

    @Test
    fun searchResponseMapsToSongs() {
        val songs = parseYtSearchResponse(searchFixture())
        assertEquals(2, songs.size)
        val first = songs[0]
        assertEquals("晴天", first.name)
        assertEquals(ytSongId("SJKoWAd5ySo"), first.id)
        assertEquals("SJKoWAd5ySo", first.sourceId)
        assertEquals(MusicSource.YOUTUBEMUSIC, first.source)
        assertEquals("周杰伦", first.artists.single().name)
        assertEquals("叶惠美", first.albumName)
        assertEquals(269_000L, first.durationMs)
        assertTrue(first.picUrl.contains("=w540-h540"))
        val second = songs[1]
        assertEquals("Perfect", second.name)
        assertEquals("", second.albumName)
        assertEquals(263_000L, second.durationMs)
    }

    @Test
    fun durationParsing() {
        assertEquals(0L, parseYtDurationMs(""))
        assertEquals(0L, parseYtDurationMs("abc"))
        assertEquals(5_000L, parseYtDurationMs("0:05"))
        assertEquals(269_000L, parseYtDurationMs("4:29"))
        assertEquals(3_723_000L, parseYtDurationMs("1:02:03"))
    }

    @Test
    fun selectFormatPrefers251Then140ThenHighestBitrate() {
        val formats = listOf(
            YtAudioFormat(140, "u140", 128_000, "audio/mp4; codecs=\"mp4a.40.2\""),
            YtAudioFormat(251, "u251", 148_000, "audio/webm; codecs=\"opus\""),
            YtAudioFormat(18, "u18", 96_000, "video/mp4"),
            YtAudioFormat(250, "", 64_000, "audio/webm; codecs=\"opus\""),
        )
        assertEquals("u251", selectYtAudioFormat(formats)?.url)
        assertEquals("u140", selectYtAudioFormat(formats.filter { it.itag != 251 })?.url)
        assertEquals(
            "u140",
            selectYtAudioFormat(formats.filter { it.itag != 251 && it.itag != 250 })?.url,
        )
        // 无直链音频（只有 signatureCipher 形态）→ null，绝不伪造。
        assertNull(selectYtAudioFormat(listOf(YtAudioFormat(251, "", 148_000, "audio/webm"))))
        assertNull(selectYtAudioFormat(emptyList()))
    }

    @Test
    fun playerFormatsParseSkipsCipherOnlyEntries() {
        val player = JSONObject(
            """
            {"playabilityStatus":{"status":"OK"},
             "streamingData":{
               "formats":[{"itag":18,"url":"v18","mimeType":"video/mp4","bitrate":500000}],
               "adaptiveFormats":[
                 {"itag":251,"url":"a251","mimeType":"audio/webm; codecs=\"opus\"","bitrate":148000},
                 {"itag":140,"signatureCipher":"s=abc","mimeType":"audio/mp4","bitrate":128000}
               ]}}
            """.trimIndent(),
        )
        val formats = parseYtFormats(player)
        assertEquals(3, formats.size)
        // 140 无 url（只有签名串）→ 选流时跳过，落到 251。
        assertEquals(251, selectYtAudioFormat(formats)?.itag)
    }

    @Test
    fun lyricsBrowseIdAndTextExtraction() {
        val tabWithId = JSONObject().put(
            "tabRenderer",
            JSONObject().put("endpoint", JSONObject().put("browseEndpoint", JSONObject().put("browseId", "MPLYt_nqKuADavt3c-3"))),
        )
        val tabPlain = JSONObject().put("tabRenderer", JSONObject().put("content", JSONObject()))
        val next = JSONObject().put(
            "contents",
            JSONObject().put("tabbedRenderer", JSONObject().put("tabs", org.json.JSONArray().put(tabPlain).put(tabWithId))),
        )
        assertEquals("MPLYt_nqKuADavt3c-3", findYtLyricsBrowseId(next))
        val shelf = JSONObject().put(
            "musicDescriptionShelfRenderer",
            JSONObject().put("description", runsOf("故事的小黄花\n从出生那年就飘着", "\n\nSource: Musixmatch")),
        )
        val browse = JSONObject().put("contents", JSONObject().put("sectionListRenderer", JSONObject().put("contents", org.json.JSONArray().put(shelf))))
        val text = parseYtLyricsText(browse)
        assertTrue(text.startsWith("故事的小黄花"))
        assertTrue(text.contains("Source: Musixmatch"))
        assertEquals("", parseYtLyricsText(JSONObject("{}")))
        assertNull(findYtLyricsBrowseId(JSONObject("{}")))
    }

    @Test
    fun plainTextLyricsSpreadByDurationWithoutWordTiming() {
        val lines = com.tokimusic.app.data.lyric.YrcParser.parsePlainText("第一行\n\n第二行\n第三行", 90_000)
        assertEquals(3, lines.size)
        assertEquals(0L, lines[0].startMs)
        assertEquals(30_000L, lines[1].startMs)
        assertEquals(60_000L, lines[2].startMs)
        // 每行只有一个整词（无逐字时间轴）。
        assertTrue(lines.all { it.words.size == 1 })
        assertEquals("第二行", lines[1].text)
        assertTrue(com.tokimusic.app.data.lyric.YrcParser.parsePlainText("  \n ", 90_000).isEmpty())
        assertTrue(com.tokimusic.app.data.lyric.YrcParser.parsePlainText(null, 90_000).isEmpty())
    }
}
