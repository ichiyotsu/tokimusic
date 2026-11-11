package com.tokimusic.app.data.lyric
import org.junit.Assert.*
import org.junit.Test
class YrcParserTest {
    private val yrc = "[1000,2000](1000,500,0)你(1500,500,0)好(2000,1000,0)世界\n[5000,1000](5000,1000,0)再见"
    @Test fun parsesWordTimings() {
        val lines = YrcParser.parseYrc(yrc)
        assertEquals(2, lines.size); assertEquals(1000L, lines[0].startMs); assertEquals(2000L, lines[0].durationMs)
        assertEquals(listOf("你", "好", "世界"), lines[0].words.map { it.text })
        assertEquals(1500L, lines[0].words[1].startMs); assertEquals("你好世界", lines[0].text)
    }
    @Test fun wordProgressClamps() {
        val w = LyricWord(1000, 500, "你")
        assertEquals(0f, YrcParser.wordProgress(w, 900), 0.001f); assertEquals(0.5f, YrcParser.wordProgress(w, 1250), 0.001f); assertEquals(1f, YrcParser.wordProgress(w, 2000), 0.001f)
    }
    @Test fun lrcFallbackAndTranslation() {
        val lines = YrcParser.parse(null, "[00:01.00]hello\n[00:03.50]world", "[00:01.00]你好")
        assertEquals(2, lines.size); assertEquals(1000L, lines[0].startMs); assertEquals(2500L, lines[0].durationMs); assertEquals("你好", lines[0].translation)
    }
    @Test fun skipsMetadataLines() { assertTrue(YrcParser.parseYrc("{\"t\":0,\"c\":[]}").isEmpty()) }
}
