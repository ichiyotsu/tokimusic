package com.tokimusic.app.data.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本轮（0129）：行锚定适配。合成扰动验机制（平移/间奏/缺行），
 * 真实跨版本对验 spike 结论——夹具同 QrcParserTest（spike 解密
 * 明文）：Lemon 录音室←Live 应过（覆盖 100%），孤勇者应过
 * （覆盖 54%），晴天录音室←Live 应因覆盖率 33% 被拒（Live 是
 * 缩编版，spike 判定该拒的是这一对）。
 */
class LineAnchoredAdaptTest {

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader(Charsets.UTF_8).readText()

    private fun line(start: Long, dur: Long, vararg words: Pair<String, Long>): LyricLine {
        var t = start
        val ws = words.map { (text, d) -> LyricWord(t, d, text).also { t += d } }
        return LyricLine(start, dur, ws)
    }

    private fun targetLine(start: Long, text: String): LyricLine =
        LyricLine(start, 0, listOf(LyricWord(start, 0, text)))

    @Test fun introShiftIsAnchoredBackToTargetStarts() {
        // 借词整体晚 4s（片头差）：锚定后词时回到目标行首附近。
        val targets = listOf(targetLine(1000, "你好世界"), targetLine(3000, "再见朋友"))
        val donors = listOf(
            line(5000, 2000, "你" to 500, "好" to 500, "世界" to 1000),
            line(7000, 1000, "再见" to 500, "朋友" to 500),
        )
        val out = LineAnchoredAdapt.adapt(targets, donors)
        assertNotNull(out)
        assertEquals(3, out!![0].words.size) // 真逐字适配，不是回退
        assertEquals(1000L, out[0].words.first().startMs)
        assertEquals(3000L, out[1].words.first().startMs)
    }

    @Test fun midSongBreakDoesNotDragLaterLines() {
        // 中段插入 8s 间奏：后半行整体后移，锚定后仍贴目标行首。
        val targets = listOf(targetLine(0, "第一行"), targetLine(4000, "第二行"), targetLine(8000, "第三行"))
        val donors = listOf(
            line(0, 3000, "第一行" to 3000),
            line(4000, 3000, "第二行" to 3000),
            line(16000, 3000, "第三行" to 3000),
        )
        val out = LineAnchoredAdapt.adapt(targets, donors)!!
        assertEquals(4000L, out[1].words.first().startMs)
        assertEquals(8000L, out[2].words.first().startMs)
    }

    @Test fun missingDonorLineFallsBackToLineLevel() {
        // 借词缺一行：该行保持目标行级原样（整行一词），其余行逐字。
        val targets = listOf(targetLine(0, "甲行"), targetLine(3000, "乙行"), targetLine(6000, "丙行"))
        val donors = listOf(
            line(0, 2000, "甲" to 1000, "行" to 1000),
            line(6000, 2000, "丙" to 1000, "行" to 1000),
        )
        val out = LineAnchoredAdapt.adapt(targets, donors)!!
        assertEquals(2, out[0].words.size)
        assertEquals(1, out[1].words.size)
        assertEquals("乙行", out[1].text)
        assertEquals(2, out[2].words.size)
    }

    @Test fun scaleOutOfRangeLineFallsBack() {
        // 目标行窗 3000ms vs 借词行长 1000ms → scale 3.0 越界，该行回退行级。
        val targets = listOf(targetLine(0, "短行"), targetLine(3000, "次行"))
        val donors = listOf(
            line(0, 1000, "短" to 500, "行" to 500),
            line(3000, 1000, "次" to 500, "行" to 500),
        )
        val out = LineAnchoredAdapt.adapt(targets, donors)!!
        assertEquals(1, out[0].words.size)
        assertEquals(2, out[1].words.size)
    }

    @Test fun coverageBelowHalfRejected() {
        val targets = (0 until 6).map { targetLine(it * 3000L, "第${it}行") }
        val donors = listOf(line(0, 1000, "第0行" to 1000))
        assertNull(LineAnchoredAdapt.adapt(targets, donors))
    }

    @Test fun repeatedLinesMatchInOrder() {
        // 重复副歌行按出现次序对位：第二遍不能对到第一遍的锚点。
        val targets = listOf(targetLine(0, "副歌"), targetLine(5000, "主歌"), targetLine(10000, "副歌"))
        val donors = listOf(
            line(2000, 4000, "副" to 2000, "歌" to 2000),
            line(7000, 4000, "主" to 2000, "歌" to 2000),
            line(12000, 4000, "副" to 2000, "歌" to 2000),
        )
        val out = LineAnchoredAdapt.adapt(targets, donors)!!
        assertEquals(0L, out[0].words.first().startMs)
        assertEquals(10000L, out[2].words.first().startMs)
    }

    // ---- 真实跨版本对（目标 = 录音室版自身 LRC 行轴，借词 = Live 版 QRC） ----

    private fun realPair(targetLrc: String, donorQrc: String): Pair<List<LyricLine>, List<LyricLine>> =
        YrcParser.parseLrc(resource(targetLrc)) to QrcParser.parseQrc(resource(donorQrc))

    /** 适配输出与真值（目标版自己的 QRC）逐词起始误差中位数。 */
    private fun medianWordStartError(adapted: List<LyricLine>, truth: List<LyricLine>): Long {
        val occ = HashMap<String, MutableList<LyricLine>>()
        for (l in truth) occ.getOrPut(LineAnchoredAdapt.normalizeLineText(l.text)) { ArrayList() }.add(l)
        val used = HashMap<String, Int>()
        val errors = ArrayList<Long>()
        for (line in adapted) {
            if (line.words.size <= 1) continue
            val key = LineAnchoredAdapt.normalizeLineText(line.text)
            val k = used.getOrDefault(key, 0)
            used[key] = k + 1
            val truthLine = occ[key]?.getOrNull(k) ?: continue
            if (truthLine.words.size != line.words.size) continue
            for ((a, b) in line.words.zip(truthLine.words)) {
                errors += kotlin.math.abs(a.startMs - b.startMs)
            }
        }
        assertTrue("可比词太少: ${errors.size}", errors.size > 100)
        return errors.sorted()[errors.size / 2]
    }

    @Test fun lemonLiveBorrowPassesWithSmallError() {
        val (targets, donors) = realPair("lrc-lemon-studio.txt", "qrc-lemon-live.txt")
        val out = LineAnchoredAdapt.adapt(targets, donors)
        assertNotNull("Lemon 覆盖率应过线", out)
        val wordLines = out!!.count { it.words.size > 1 }
        assertTrue("逐字行占比过低: $wordLines/${out.size}", wordLines.toDouble() / out.size >= 0.9)
        val median = medianWordStartError(out, QrcParser.parseQrc(resource("qrc-lemon-studio.txt")))
        assertTrue("Lemon 适配中位误差过大: ${median}ms", median < 150)
    }

    @Test fun guyongzheLiveBorrowPasses() {
        val (targets, donors) = realPair("lrc-guyongzhe-studio.txt", "qrc-guyongzhe-live.txt")
        val out = LineAnchoredAdapt.adapt(targets, donors)
        assertNotNull("孤勇者覆盖率 54% 应过线", out)
        val median = medianWordStartError(out!!, QrcParser.parseQrc(resource("qrc-guyongzhe-studio.txt")))
        assertTrue("孤勇者适配中位误差过大: ${median}ms", median < 300)
    }

    @Test fun qingtianLiveBorrowRejectedOnCoverage() {
        // 晴天 Live 是缩编版（63 行目标只对上 21 行，覆盖 33%）→ 整首拒绝。
        val (targets, donors) = realPair("lrc-qingtian-studio.txt", "qrc-qingtian-live.txt")
        assertNull(LineAnchoredAdapt.adapt(targets, donors))
    }
}
