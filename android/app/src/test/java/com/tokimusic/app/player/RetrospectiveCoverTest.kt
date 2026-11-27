package com.tokimusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本轮（0125）第 6 项：音乐记忆生成封面的文案与选色纯函数（按年月种子、同月恒定、深浅两套）。 */
class RetrospectiveCoverTest {

    private fun key(year: Int, month: Int): Int = year * 12 + (month - 1)

    @Test
    fun coverTitleUsesUppercaseAbbrAndShortYear() {
        assertEquals("OCT'26", Retrospective.coverTitle(key(2026, 10)))
        assertEquals("SEP'26", Retrospective.coverTitle(key(2026, 9)))
        assertEquals("JAN'25", Retrospective.coverTitle(key(2025, 1)))
        assertEquals("DEC'24", Retrospective.coverTitle(key(2024, 12)))
    }

    @Test
    fun coverSubtitleNamesMemoryAndMonth() {
        assertEquals("音乐记忆 · 10月", Retrospective.coverSubtitle(key(2026, 10)))
        assertEquals("音乐记忆 · 9月", Retrospective.coverSubtitle(key(2026, 9)))
        assertEquals("音乐记忆 · 1月", Retrospective.coverSubtitle(key(2025, 1)))
    }

    @Test
    fun allTwelveMonthAbbreviations() {
        val expected = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        for (m in 1..12) {
            assertEquals(expected[m - 1], Retrospective.monthAbbr(key(2026, m)))
        }
    }

    @Test
    fun coverColorsAreStableForSameMonth() {
        val k = key(2026, 10)
        assertEquals(Retrospective.coverColors(k, darkTheme = true), Retrospective.coverColors(k, darkTheme = true))
        assertEquals(Retrospective.coverColors(k, darkTheme = false), Retrospective.coverColors(k, darkTheme = false))
        assertEquals(3, Retrospective.coverColors(k, darkTheme = true).size)
        assertEquals(3, Retrospective.coverColors(k, darkTheme = false).size)
    }

    @Test
    fun coverColorsComeFromThemedFamilySets() {
        // 任意月份选出的 3 色必须整族来自对应主题的色族表（同色相的一组）。
        for (m in 1..12) {
            val k = key(2026, m)
            assertTrue(Retrospective.coverColors(k, darkTheme = true) in Retrospective.COVER_FAMILIES_DARK)
            assertTrue(Retrospective.coverColors(k, darkTheme = false) in Retrospective.COVER_FAMILIES_LIGHT)
        }
        // 深浅两套同序同族数，同一月在两套里选中的是同色相的一族（同下标）。
        assertEquals(Retrospective.COVER_FAMILIES_DARK.size, Retrospective.COVER_FAMILIES_LIGHT.size)
        val k = key(2026, 10)
        assertEquals(
            Retrospective.COVER_FAMILIES_DARK.indexOf(Retrospective.coverColors(k, darkTheme = true)),
            Retrospective.COVER_FAMILIES_LIGHT.indexOf(Retrospective.coverColors(k, darkTheme = false)),
        )
        assertNotEquals(Retrospective.coverColors(k, darkTheme = true), Retrospective.coverColors(k, darkTheme = false))
    }
}
