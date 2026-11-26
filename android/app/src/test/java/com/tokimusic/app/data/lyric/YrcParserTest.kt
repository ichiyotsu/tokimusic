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
    // 第五轮第 26 项：罗马音与翻译同一套对齐（时间戳最近行，≤500ms 才贴）。
    @Test fun romalrcAlignsToNearestLineWithin500ms() {
        val lines = YrcParser.parse(null, "[00:01.00]ただ声一つ\n[00:05.00]次の行", null, "[00:01.20]tsu zu ku ji ka n no ka ke ra")
        assertEquals("tsu zu ku ji ka n no ka ke ra", lines[0].romaji)
        assertNull(lines[1].romaji)
    }
    @Test fun romalrcAlignsOnYrcLines() {
        val lines = YrcParser.parse(yrc, null, null, "[00:01.20]ni hao\n[00:05.00]zai jian")
        assertEquals("ni hao", lines[0].romaji)
        assertEquals("zai jian", lines[1].romaji)
    }
    @Test fun romalrcTooFarAwayDoesNotAttach() {
        val lines = YrcParser.parse(null, "[00:01.00]ただ声一つ", null, "[00:02.00]tooku sugiru")
        assertNull(lines[0].romaji)
    }
    @Test fun romalrcMissingLeavesRomajiNull() {
        val lines = YrcParser.parse(null, "[00:01.00]ただ声一つ", null, null)
        assertNull(lines[0].romaji)
    }
    // 第五轮第 18 项：行进度分数——行首 0、行尾 1、词间空隙处边界停住、
    // LRC 单词整行扫过、零时长词二值翻色、空行 0。
    @Test fun lineProgressBoundaries() {
        val line = LyricLine(1000, 3000, listOf(LyricWord(1000, 1000, "ab"), LyricWord(2000, 1000, "cd")))
        assertEquals(0f, YrcParser.lineProgress(line, 1000), 0.001f)
        assertEquals(0.25f, YrcParser.lineProgress(line, 1500), 0.001f)
        assertEquals(1f, YrcParser.lineProgress(line, 3000), 0.001f)
        assertEquals(1f, YrcParser.lineProgress(line, 9999), 0.001f)
        assertEquals(0f, YrcParser.lineProgress(line, 500), 0.001f)
    }
    @Test fun lineProgressHoldsInWordGap() {
        // 词 1 在 1000–1500 唱完（2 字），词 2 从 2500 才开始：1500–2500
        // 的空隙里边界停在 2/4 = 0.5，不往前走。
        val line = LyricLine(1000, 3000, listOf(LyricWord(1000, 500, "ab"), LyricWord(2500, 500, "cd")))
        assertEquals(0.5f, YrcParser.lineProgress(line, 1500), 0.001f)
        assertEquals(0.5f, YrcParser.lineProgress(line, 2400), 0.001f)
        assertEquals(0.75f, YrcParser.lineProgress(line, 2750), 0.001f)
    }
    @Test fun lineProgressLrcSingleWordSweepsLine() {
        val line = LyricLine(1000, 4000, listOf(LyricWord(1000, 4000, "abcd")))
        assertEquals(0.5f, YrcParser.lineProgress(line, 3000), 0.001f)
    }
    // 第六轮第 30 项（B 方案）：无逐字时间的 LRC 行线性扫过（行首 0、行中
    // 0.5、行尾 1，行外钳制）；带真实时长的词仍走原来的词时间轴。
    @Test fun lineProgressLrcLineSweepsLinearly() {
        val line = LyricLine(1000, 4000, listOf(LyricWord(1000, 0, "abcd")))
        assertEquals(0f, YrcParser.lineProgress(line, 500), 0.001f)
        assertEquals(0f, YrcParser.lineProgress(line, 1000), 0.001f)
        assertEquals(0.5f, YrcParser.lineProgress(line, 3000), 0.001f)
        assertEquals(1f, YrcParser.lineProgress(line, 5000), 0.001f)
        assertEquals(1f, YrcParser.lineProgress(line, 9999), 0.001f)
    }
    @Test fun lineProgressLrcLineFromParse() {
        // parseLrc 造的行就是零时长词 + 行长 = 下一行间隔。
        val lines = YrcParser.parseLrc("[00:01.00]hello\n[00:05.00]world")
        assertEquals(0.5f, YrcParser.lineProgress(lines[0], 3000), 0.001f)
        assertEquals(0f, YrcParser.lineProgress(lines[0], 1000), 0.001f)
    }
    @Test fun lineProgressZeroDurationWordFlipsBinary() {
        val line = LyricLine(1000, 2000, listOf(LyricWord(1000, 0, "ab"), LyricWord(2000, 1000, "cd")))
        assertEquals(0.5f, YrcParser.lineProgress(line, 1000), 0.001f)
        assertEquals(0.75f, YrcParser.lineProgress(line, 2500), 0.001f)
    }
    @Test fun lineProgressEmptyLineIsZero() {
        assertEquals(0f, YrcParser.lineProgress(LyricLine(1000, 1000, emptyList()), 1500), 0.001f)
    }
    // 0.1.20 第 14 项：词 duration 含尾部静默（灌水到整个空隙）时封顶——
    // 扫动不晚于下一词开始完成，行尾词长尾封顶到行结束，不再匀速慢爬。
    @Test fun effectiveDurationCapsInflatedWordAtNextWordStart() {
        val line = LyricLine(1000, 3000, listOf(LyricWord(1000, 3000, "ab"), LyricWord(2500, 500, "cd")))
        assertEquals(1500L, YrcParser.effectiveWordDurationMs(line, 0))
        assertEquals(500L, YrcParser.effectiveWordDurationMs(line, 1))
    }
    @Test fun lineProgressInflatedDurationDoesNotCrawlAcrossGap() {
        // 词 1 自带 duration 3000（包住 1500 的空隙），实际 1500 就该唱完：
        // 1750 时词 1 已半（旧算法只有 0.25 的行进度），2500 下一词开始时词 1 满格。
        val line = LyricLine(1000, 3000, listOf(LyricWord(1000, 3000, "ab"), LyricWord(2500, 500, "cd")))
        assertEquals(0.25f, YrcParser.lineProgress(line, 1750), 0.001f)
        assertEquals(0.5f, YrcParser.lineProgress(line, 2500), 0.001f)
        assertEquals(0.75f, YrcParser.lineProgress(line, 2750), 0.001f)
    }
    @Test fun lineProgressLastWordTailCapsAtLineEnd() {
        // 行尾词自带 duration 4000、越过行尾（行 1000–3000）伸进行间空隙：
        // 封顶到行结束 1500，行尾时刻整行满格（旧算法只有 0.6875）。
        val line = LyricLine(1000, 2000, listOf(LyricWord(1000, 500, "ab"), LyricWord(1500, 4000, "cd")))
        assertEquals(1500L, YrcParser.effectiveWordDurationMs(line, 1))
        assertEquals(0.75f, YrcParser.lineProgress(line, 2250), 0.001f)
        assertEquals(1f, YrcParser.lineProgress(line, 3000), 0.001f)
    }
}
