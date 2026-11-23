package com.tokimusic.app.ui.theme

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日 / 韩 / 繁中必须走思源黑体 Bold：直接读 res/font 里的字体文件，解析
 * cmap 与 fvar 表，断言覆盖与 wght 轴。有人把完整版换回子集、或子集漏了
 * 谚文，这里会立刻失败（0.1.7 的 noto_sans_sc 只有 7,630 个字形、0 个谚文）。
 */
class FontCoverageTest {
    private fun fontFile(name: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val f = File(dir, "src/main/res/font/$name")
            if (f.isFile) return f
            dir = dir.parentFile
        }
        throw IllegalStateException("找不到字体文件 $name（从 ${System.getProperty("user.dir")} 向上找）")
    }

    private fun tableOffset(bytes: ByteArray, wantTag: String): Int {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val numTables = buf.getShort(4).toInt()
        for (i in 0 until numTables) {
            val base = 12 + i * 16
            val tag = ByteArray(4).also { buf.position(base); buf.get(it) }.toString(Charsets.US_ASCII)
            if (tag == wantTag) return buf.getInt(base + 8)
        }
        return -1
    }

    /** 最小的 TTF cmap 解析：只读 format 12 分组（退化到 format 4），返回覆盖的码位区间。 */
    private fun cmapRanges(bytes: ByteArray): List<IntRange> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val cmapOffset = tableOffset(bytes, "cmap")
        require(cmapOffset >= 0) { "没有 cmap 表" }
        val numSubtables = buf.getShort(cmapOffset + 2).toInt()
        var fmt12 = -1
        var fmt4 = -1
        for (i in 0 until numSubtables) {
            val off = cmapOffset + 4 + i * 8
            val sub = cmapOffset + buf.getInt(off + 4)
            when (buf.getShort(sub).toInt()) {
                12 -> if (fmt12 < 0) fmt12 = sub
                4 -> if (fmt4 < 0) fmt4 = sub
            }
        }
        if (fmt12 >= 0) {
            val nGroups = buf.getInt(fmt12 + 12)
            return (0 until nGroups).map {
                val base = fmt12 + 16 + it * 12
                buf.getInt(base)..buf.getInt(base + 4)
            }
        }
        require(fmt4 >= 0) { "没有 format 4/12 的 cmap 子表" }
        val segCount = buf.getShort(fmt4 + 6).toInt() / 2
        val endBase = fmt4 + 14
        val startBase = endBase + segCount * 2 + 2
        return (0 until segCount).map {
            val end = buf.getShort(endBase + it * 2).toInt() and 0xFFFF
            val start = buf.getShort(startBase + it * 2).toInt() and 0xFFFF
            start..end
        }.filter { it.first <= it.last && it.last != 0xFFFF }
    }

    /** fvar 表里 wght 轴的最大值（Bold 靠 variationSettings 取 700）。 */
    private fun maxWght(bytes: ByteArray): Float {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val off = tableOffset(bytes, "fvar")
        require(off >= 0) { "没有 fvar 表" }
        val axisCount = buf.getShort(off + 8).toInt()
        val axisSize = buf.getShort(off + 10).toInt()
        val axesBase = off + buf.getShort(off + 4)
        for (a in 0 until axisCount) {
            val base = axesBase + a * axisSize
            val axisTag = ByteArray(4).also { buf.position(base); buf.get(it) }.toString(Charsets.US_ASCII)
            // fvar 的值是 Fixed 16.16 定点数，不是 IEEE 754 浮点：
            // getFloat 会把 900.0 的定点位模式读成 ~5e-38。
            if (axisTag == "wght") return buf.getInt(base + 12) / 65536f
        }
        throw IllegalStateException("没有 wght 轴")
    }

    private fun covers(ranges: List<IntRange>, cp: Int): Boolean = ranges.any { cp in it }

    @Test
    fun notoSansScCoversJapaneseAndTraditional() {
        val bytes = fontFile("noto_sans_sc.ttf").readBytes()
        val ranges = cmapRanges(bytes)
        assertTrue(covers(ranges, 0x3042)) // あ
        assertTrue(covers(ranges, 0x30A2)) // ア
        assertTrue(covers(ranges, 0x8AAA)) // 說
        assertTrue(covers(ranges, 0x9AA8)) // 龍
        assertTrue(covers(ranges, 0x8FBB)) // 辻
        assertTrue(covers(ranges, 0x51EA)) // 凪
        assertTrue(covers(ranges, 0x4E2D)) // 中
        assertTrue(maxWght(bytes) >= 700f)
    }

    @Test
    fun notoSansKrCoversHangulSyllables() {
        val bytes = fontFile("noto_sans_kr.ttf").readBytes()
        val ranges = cmapRanges(bytes)
        for (cp in listOf(0xAC00, 0xAF00, 0xC5B4, 0xD7A3)) {
            assertTrue("U+%04X".format(cp), covers(ranges, cp))
        }
        assertTrue(covers(ranges, 0x1100)) // 谚文字母 ᄀ
        assertTrue(maxWght(bytes) >= 700f)
    }

    @Test
    fun notoSansScDoesNotCoverHangul() {
        // 注明分工：SC 连完整版也不含谚文音节，韩文必须走 KR 文件。
        val ranges = cmapRanges(fontFile("noto_sans_sc.ttf").readBytes())
        assertTrue(!covers(ranges, 0xAC00))
    }

    @Test
    fun notoSansJpSubsetCoversCompatibilityIdeographsScLacks() {
        // 第六轮第 4 项：JP 子集（假名 + F900–FAFF + 2F800–2FA1F，566KB）
        // 补 SC 所缺的兼容表意字；wght 轴保留，Bold 靠 variationSettings 取 700。
        val bytes = fontFile("noto_sans_jp_bold.ttf").readBytes()
        val ranges = cmapRanges(bytes)
        assertTrue(covers(ranges, 0x3042)) // あ
        assertTrue(covers(ranges, 0x30A2)) // ア
        assertTrue(covers(ranges, 0xFF76)) // ｶ 半角片假名
        assertTrue(covers(ranges, 0xFA10)) // 塚：SC 没有，JP 有
        assertTrue(covers(ranges, 0xFA6D)) // 舘：SC 没有，JP 有
        assertTrue(maxWght(bytes) >= 700f)
        // SC 的缺口对照：这两个兼容字确实不在 SC 里，JP 子集就是为它们补的。
        val scRanges = cmapRanges(fontFile("noto_sans_sc.ttf").readBytes())
        assertTrue(!covers(scRanges, 0xFA10))
        assertTrue(!covers(scRanges, 0xFA6D))
    }

    @Test
    fun cjkFallbackOrderIsScThenJpThenKr() {
        // 第六轮第 4 项：回退链顺序锁死——共用字形只取 SC 的形状，JP 只补缺口。
        assertEquals(
            listOf(com.tokimusic.app.R.font.noto_sans_sc, com.tokimusic.app.R.font.noto_sans_jp_bold, com.tokimusic.app.R.font.noto_sans_kr),
            cjkFallbackOrder,
        )
    }

    @Test
    fun fad0AndFad6AreAbsentFromEveryNotoFont() {
        // 已知缺失（第六轮第 4 项实测）：者 U+FAD6 与 﨑 U+FAD0 在 Google
        // Fonts 的 NotoSansJP 变量版与 Adobe 的 NotoSansCJKjp-Bold 完整
        // OTF 里都没有——Noto 全家都不含 FAD0–FAD9 这段尾巴，这两字仍走
        // 系统回退（常规字重）。本测试把该结论锁死，免得后人误以为“换
        // 个 Noto 字体就能补上”而反复折腾；若将来找到含这两字的免费
        // 字体，再把本测试改成覆盖断言。
        val ranges = cmapRanges(fontFile("noto_sans_jp_bold.ttf").readBytes())
        assertTrue(!covers(ranges, 0xFAD6))
        assertTrue(!covers(ranges, 0xFAD0))
    }
}
