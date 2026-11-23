package com.tokimusic.app.ui.components
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatHelpersTest {
    // 第五轮第 23 项：整小时只显示 "1537h"，有零头时带一位小数。
    @Test fun formatHoursWhole() {
        assertEquals("1537h", formatHours(92_220L))
        assertEquals("0h", formatHours(0L))
        assertEquals("1h", formatHours(60L))
    }
    @Test fun formatHoursOneDecimal() {
        assertEquals("1537.5h", formatHours(92_250L))
        assertEquals("1.5h", formatHours(90L))
        assertEquals("0.5h", formatHours(30L))
    }
    @Test fun formatHoursRoundsToNearestTenth() {
        assertEquals("1.6h", formatHours(95L))
        assertEquals("1h", formatHours(59L))
    }
    // 第五轮第 24 项：天数向下取整，当天 0 天；时钟回拨时不为负。
    @Test fun daysSinceBoundaries() {
        assertEquals(0L, daysSince(1_000L, 1_000L))
        assertEquals(0L, daysSince(1_000L, 1_000L + 86_400_000L - 1))
        assertEquals(1L, daysSince(1_000L, 1_000L + 86_400_000L))
        assertEquals(1065L, daysSince(1_000L, 1_000L + 1065L * 86_400_000L))
        assertEquals(0L, daysSince(2_000L, 1_000L))
    }
}
