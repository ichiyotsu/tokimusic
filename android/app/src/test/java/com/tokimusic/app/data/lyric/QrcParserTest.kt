package com.tokimusic.app.data.lyric

import com.tokimusic.app.data.api.QrcDecrypt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本轮（0129）：QRC 逐字解析。夹具是 spike 在沙箱实抓并解密的
 * 真实 QQ 歌词（~/workspace/work/qqmusic-qrc-spike/out/ 的解密
 * 明文），行数/词时断言与 spike 报告的校验表一致。
 */
class QrcParserTest {

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader(Charsets.UTF_8).readText()

    @Test fun parsesQingtianWordTimings() {
        val lines = QrcParser.parseQrc(resource("qrc-qingtian-studio.txt"))
        // spike 校验表：晴天录音室版 63 行（与 spike analyze_qrc 一致）。
        assertEquals(63, lines.size)
        // 元数据行（[ti:]/[ar:]/[al:]）被跳过：专辑名不出现在任何行里。
        assertTrue(lines.none { it.text.contains("叶惠美") })
        // 第一句正文的逐字时值（spike 解析样本逐字一致）。
        val body = lines.first { it.text.startsWith("故事的小黄花") }
        assertEquals(29264L, body.startMs)
        assertEquals(3446L, body.durationMs)
        assertEquals(listOf("故", "事", "的", "小", "黄", "花"), body.words.map { it.text })
        assertEquals(29264L, body.words[0].startMs)
        assertEquals(390L, body.words[0].durationMs)
        // 「小」拖长音 922ms——真逐字、非均分。
        assertEquals(30494L, body.words[3].startMs)
        assertEquals(922L, body.words[3].durationMs)
        assertEquals(504L, body.words[5].durationMs)
    }

    @Test fun wordTimingsAreMonotonicAndInsideLineWindow() {
        for (name in listOf("qrc-qingtian-studio.txt", "qrc-lemon-studio.txt", "qrc-guyongzhe-studio.txt", "qrc-lemon-live.txt")) {
            val lines = QrcParser.parseQrc(resource(name))
            assertTrue("$name 行数异常", lines.size > 20)
            for (line in lines) {
                var prev = Long.MIN_VALUE
                for (w in line.words) {
                    assertTrue("$name 词时倒退: ${w.startMs} < $prev", w.startMs >= prev)
                    assertTrue("$name 词越出行首: ${w.startMs} < ${line.startMs}", w.startMs >= line.startMs)
                    prev = w.startMs
                }
            }
        }
    }

    @Test fun decryptThenParseEndToEnd() {
        // CGI 密文（QrcDecryptTest 同一夹具）→ 解密 → QRC 解析全链。
        val hex = resource("qq-lyric-qrc1-hex.txt").trim()
        val plain = QrcDecrypt.decryptQrcHex(hex)
        assertNotNull(plain)
        val lines = QrcParser.parse(plain)
        assertEquals(63, lines.size)
        assertTrue(lines.first { it.text.startsWith("故事的小黄花") }.words.size == 6)
    }

    @Test fun romajiQrcAttachesToJapaneseLines() {
        // 现存 bug 回归：QQ 日语歌 roma 是 QRC XML，旧路径当 LRC
        // 解析得空、罗马音静默缺失。修复后按行首贴附。
        val lines = QrcParser.parse(resource("qrc-lemon-studio.txt"), null, resource("qrc-lemon-roma.txt"))
        val attached = lines.count { it.romaji != null }
        assertTrue("罗马音贴附行数过少: $attached", attached >= 50)
        val line = lines.first { it.startMs == 1547L }
        assertEquals("yumenaraba", line.romaji!!.replace(" ", "").trim())
    }

    @Test fun translationLrcAttachesByNearestLine() {
        val lines = QrcParser.parse(resource("qrc-shape-studio.txt"), resource("lrc-shape-trans.txt"), null)
        val line = lines.first { it.startMs == 9551L }
        assertEquals("俱乐部不是找另一半的绝佳场所", line.translation)
    }

    @Test fun sniffingDistinguishesQrcFromLrc() {
        assertTrue(QrcParser.isQrc(resource("qrc-lemon-studio.txt")))
        assertFalse(QrcParser.isQrc(resource("lrc-lemon-studio.txt")))
        assertFalse(QrcParser.isQrc("[00:01.00]hello"))
    }

    @Test fun toLrcTextRoundTripsLineStarts() {
        val qrc = resource("qrc-qingtian-studio.txt")
        val lrcText = QrcParser.toLrcText(qrc)
        val back = YrcParser.parseLrc(lrcText)
        val original = QrcParser.parseQrc(qrc)
        assertEquals(original.size, back.size)
        // LRC 只到厘秒，行首误差 ≤10ms。
        for ((a, b) in original.zip(back)) {
            assertTrue(kotlin.math.abs(a.startMs - b.startMs) <= 10)
            // LRC 解析会 trim 行首尾空白，QRC 词间的空格词在行尾时
            // 有一格尾空格差异，按 trim 后比对。
            assertEquals(a.text.trim(), b.text.trim())
        }
    }
}
