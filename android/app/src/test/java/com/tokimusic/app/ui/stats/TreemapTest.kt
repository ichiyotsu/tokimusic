package com.tokimusic.app.ui.stats

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreemapTest {

    @Test
    fun `day entries cover last N days and label today`() {
        val today = LocalDate.of(2026, 11, 22)
        val data = mapOf(
            LocalDate.of(2026, 11, 22) to 3_600_000L,
            LocalDate.of(2026, 11, 20) to 1_800_000L,
        )
        val entries = treemapDayEntries(today, 7) { data[it] ?: 0L }
        assertEquals(listOf("今天", "11月20日"), entries.map { it.label })
        assertEquals(listOf(3_600_000L, 1_800_000L), entries.map { it.millis })
    }

    @Test
    fun `zero days take no block`() {
        val today = LocalDate.of(2026, 11, 22)
        val entries = treemapDayEntries(today, 7) { 0L }
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `month entries label cross-year months with the year`() {
        val today = LocalDate.of(2026, 1, 5)
        val entries = treemapMonthEntries(today, 3) { ym ->
            when (ym) {
                YearMonth.of(2026, 1) -> 1_000L
                YearMonth.of(2025, 12) -> 2_000L
                else -> 0L
            }
        }
        assertEquals(listOf("1月", "2025年12月"), entries.map { it.label })
    }

    @Test
    fun `layout tiles the unit square with area proportional to millis`() {
        val entries = listOf(
            TreemapEntry("a", 400L),
            TreemapEntry("b", 300L),
            TreemapEntry("c", 200L),
            TreemapEntry("d", 100L),
        )
        val rects = treemapLayout(entries)
        assertEquals(4, rects.size)
        // 铺满、无重叠：总面积 = 1。
        val totalArea = rects.sumOf { ((it.right - it.left) * (it.bottom - it.top)).toDouble() }
        assertEquals(1.0, totalArea, 1e-5)
        // 每块面积 ∝ 时长。
        rects.forEach { r ->
            val area = (r.right - r.left) * (r.bottom - r.top)
            assertEquals(r.entry.millis / 1000f, area, 1e-5f)
        }
        // 坐标都在单位正方形内。
        rects.forEach { r ->
            assertTrue(r.left >= 0f && r.top >= 0f && r.right <= 1f && r.bottom <= 1f)
            assertTrue(r.right > r.left && r.bottom > r.top)
        }
    }

    @Test
    fun `layout with a single entry fills the whole square`() {
        val rects = treemapLayout(listOf(TreemapEntry("only", 60_000L)))
        assertEquals(1, rects.size)
        assertEquals(TreemapRect(rects[0].entry, 0f, 0f, 1f, 1f), rects[0])
    }

    @Test
    fun `layout drops zero entries and empty input`() {
        assertTrue(treemapLayout(emptyList()).isEmpty())
        assertTrue(treemapLayout(listOf(TreemapEntry("z", 0L))).isEmpty())
        val rects = treemapLayout(listOf(TreemapEntry("a", 100L), TreemapEntry("z", 0L)))
        assertEquals(1, rects.size)
    }
}
