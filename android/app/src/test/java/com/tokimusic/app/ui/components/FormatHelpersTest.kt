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
    // 大排查 B7：时长格式化显式 Locale.US——“1:01”“2分05秒”在任何
    // 系统语言下都不变（S11 同类：默认 locale 下阿拉伯语地区会把
    // 数字变形成本地数字）。用阿拉伯语 locale 锁定这一行为。
    @Test fun formatDurationBasic() {
        assertEquals("1:01", formatDuration(61_000L))
        assertEquals("0:00", formatDuration(0L))
        assertEquals("10:05", formatDuration(605_000L))
    }
    @Test fun formatTotalDurationMsBasic() {
        assertEquals("62分00秒", formatTotalDurationMs(3_720_000L))
        assertEquals("0分05秒", formatTotalDurationMs(5_000L))
    }
    @Test fun formatDurationIgnoresDefaultLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("ar", "EG"))
            assertEquals("2:05", formatDuration(125_000L))
            assertEquals("2分05秒", formatTotalDurationMs(125_000L))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
