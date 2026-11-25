package com.tokimusic.app.ui.theme

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日 / 韩 / 繁中必须走粗体 CJK 文件：直接读 res/font 里的字体文件，解析
 * cmap 与 OS/2 表，断言覆盖与静态 Bold 字重。有人把完整版换回子集、或子集漏了
 * 谚文，这里会立刻失败（0.1.7 的 noto_sans_sc 只有 7,630 个字形、0 个谚文）。
 *
 * 0.1.15 第 8 项：三个 CJK 文件已由可变字体在 wght 700 处实例化为静态
 * Bold（usWeightClass 700，无 fvar/gvar）——Compose 资源字体加载不走
 * FontVariation，旧的“fvar 里 wght 最大值 ≥700”断言随之作废，改断
 * usWeightClass == 700 且无 fvar 表。
 *
 * 0.1.18 第 3 项：中文从思源黑体 SC 换成苹方 SC Bold（pingfang_bold.ttf）。
 * 该文件 OS/2.usWeightClass 写的是 400（族名虽是 Bold，元数据仍标 Regular），
 * 所以苹方不断 usWeightClass，改断族名 == "PingFang SC Bold" 且无 fvar 表。
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

    /** OS/2 表的 usWeightClass（0.1.15 第 8 项：静态 Bold 应为 700；同时断言无 fvar 表）。 */
    private fun usWeightClass(bytes: ByteArray): Int {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val off = tableOffset(bytes, "OS/2")
        require(off >= 0) { "没有 OS/2 表" }
        return buf.getShort(off + 4).toInt() and 0xFFFF
    }

    /** name 表的族名（nameID 1）：优先 Windows 平台 UTF-16BE 记录，退到 Mac 记录。 */
    private fun familyName(bytes: ByteArray): String {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val off = tableOffset(bytes, "name")
        require(off >= 0) { "没有 name 表" }
        val count = buf.getShort(off + 2).toInt()
        // stringOffset 是相对 name 表表头的偏移（不再加 6——多加 6 会让
        // UTF-16BE 族名前 3 个字符被切掉，实测读出 "gFang SC Bold; 1"）。
        val stringBase = off + (buf.getShort(off + 4).toInt() and 0xFFFF)
        var fallback: String? = null
        for (i in 0 until count) {
            val rec = off + 6 + i * 12
            val platform = buf.getShort(rec).toInt()
            val encoding = buf.getShort(rec + 2).toInt()
            val nameId = buf.getShort(rec + 6).toInt()
            if (nameId != 1) continue
            val len = buf.getShort(rec + 8).toInt()
            val strOff = stringBase + buf.getShort(rec + 10).toInt()
            val raw = bytes.copyOfRange(strOff, strOff + len)
            if (platform == 3 && encoding == 1) return String(raw, Charsets.UTF_16BE)
            if (fallback == null) fallback = String(raw, Charsets.US_ASCII)
        }
        return requireNotNull(fallback) { "name 表里没有族名记录" }
    }

    private fun assertStaticBold(bytes: ByteArray) {
        assertTrue("应为静态 Bold 文件（无 fvar 表）", tableOffset(bytes, "fvar") < 0)
        assertEquals(700, usWeightClass(bytes))
    }

    /** 苹方 Bold 的断言：无 fvar 表 + 族名即 "PingFang SC Bold"（其 OS/2
     * usWeightClass 写的是 400，不断它，见文件头注释）。 */
    private fun assertPingFangBold(bytes: ByteArray) {
        assertTrue("应为静态文件（无 fvar 表）", tableOffset(bytes, "fvar") < 0)
        assertEquals("PingFang SC Bold", familyName(bytes))
    }

    private fun covers(ranges: List<IntRange>, cp: Int): Boolean = ranges.any { cp in it }

    @Test
    fun pingFangScCoversJapaneseAndTraditional() {
        // 0.1.18 第 3 项：中文主字体换成苹方 SC Bold——假名、繁体、日文汉字都在内。
        val bytes = fontFile("pingfang_bold.ttf").readBytes()
        val ranges = cmapRanges(bytes)
        assertTrue(covers(ranges, 0x3042)) // あ
        assertTrue(covers(ranges, 0x30A2)) // ア
        assertTrue(covers(ranges, 0x8AAA)) // 說
        assertTrue(covers(ranges, 0x9AA8)) // 龍
        assertTrue(covers(ranges, 0x8FBB)) // 辻
        assertTrue(covers(ranges, 0x51EA)) // 凪
        assertTrue(covers(ranges, 0x4E2D)) // 中
        assertPingFangBold(bytes)
    }

    @Test
    fun notoSansKrCoversHangulSyllables() {
        val bytes = fontFile("noto_sans_kr.ttf").readBytes()
        val ranges = cmapRanges(bytes)
        for (cp in listOf(0xAC00, 0xAF00, 0xC5B4, 0xD7A3)) {
            assertTrue("U+%04X".format(cp), covers(ranges, cp))
        }
        assertTrue(covers(ranges, 0x1100)) // 谚文字母 ᄀ
        assertStaticBold(bytes)
    }

    @Test
    fun pingFangScDoesNotCoverHangul() {
        // 注明分工：苹方 SC 不含谚文音节，韩文必须走 KR 文件。
        val ranges = cmapRanges(fontFile("pingfang_bold.ttf").readBytes())
        assertTrue(!covers(ranges, 0xAC00))
    }

    @Test
    fun notoSansJpSubsetCoversWhatPingFangLacks() {
        // JP 子集的覆盖面（0.1.19 第 3 项起排回退链第一）：苹方没有半角
        // 片假名与 FA6D，只有 JP 子集补；共用字形（假名、FA10）现在也
        // 优先取 JP 的形状（日文默认 JP）。FA10 苹方自己有（SC 时代
        // 没有，JP 是为它补的）。
        // JP 子集（假名 + 半角片假名 + F900–FAFF + 2F800–2FA1F，1,074 字形），
        // 0.1.15 第 8 项起为静态 Bold（无 fvar）。
        val bytes = fontFile("noto_sans_jp_bold.ttf").readBytes()
        val ranges = cmapRanges(bytes)
        assertTrue(covers(ranges, 0x3042)) // あ
        assertTrue(covers(ranges, 0x30A2)) // ア
        assertTrue(covers(ranges, 0xFF76)) // ｶ 半角片假名
        assertTrue(covers(ranges, 0xFA10)) // 塚
        assertTrue(covers(ranges, 0xFA6D)) // 舘：苹方没有，JP 有
        assertStaticBold(bytes)
        // 苹方对照：FA10 在苹方里，FA6D 与半角片假名不在——JP 子集仍不可少。
        val pingFangRanges = cmapRanges(fontFile("pingfang_bold.ttf").readBytes())
        assertTrue(covers(pingFangRanges, 0xFA10))
        assertTrue(!covers(pingFangRanges, 0xFA6D))
        assertTrue(!covers(pingFangRanges, 0xFF76))
    }

    @Test
    fun cjkFallbackOrderIsJpThenPingFangThenKr() {
        // 0.1.19 第 3 项：回退链顺序锁死——JP 第一（日文假名/兼容字默认
        // 走 JP Bold）、苹方第二（一般汉字不在 JP 子集里，仍落苹方）、
        // KR 最后（谚文）。0.1.18 时是苹方 → JP → KR，假名被苹方抢走。
        assertEquals(
            listOf(com.tokimusic.app.R.font.noto_sans_jp_bold, com.tokimusic.app.R.font.pingfang_bold, com.tokimusic.app.R.font.noto_sans_kr),
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
