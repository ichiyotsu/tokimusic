package com.tokimusic.app.ui.settings

import com.tokimusic.app.R
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.1.13 第 12 项：成就页的加入天数行。 */
class AchievementsTest {

    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    @Test
    fun emptyWhenNothingLoggedIn() {
        assertEquals(emptyList<JoinedDayEntry>(), joinedDaysEntries(0L, null, 0L, false, now))
    }

    @Test
    fun tokiMusicOnly() {
        val entries = joinedDaysEntries(now - 3 * day, null, 0L, false, now)
        assertEquals(listOf(JoinedDayEntry("TokiMusic", R.drawable.ic_launcher_foreground, 3)), entries)
    }

    @Test
    fun neteaseNegativeCreateDaysShowsDash() {
        val entries = joinedDaysEntries(0L, -1, 0L, false, now)
        assertEquals(1, entries.size)
        assertEquals("NeteaseMusic", entries[0].label)
        assertEquals(null, entries[0].days)
    }

    @Test
    fun qqUsesManualRegisterDate() {
        // 0.1.13 第 12 条返工：QQ 接口不返回注册时间，首次登录时间已被用户
        // 否决，改由用户手动填写注册日期。
        val entries = joinedDaysEntries(0L, null, now - 10 * day, true, now)
        assertEquals(1, entries.size)
        assertEquals("QQMusic", entries[0].label)
        assertEquals(10, entries[0].days)
        assertTrue(entries[0].editable)
    }

    @Test
    fun qqShowsDashAndEditableWhenDateUnset() {
        val entries = joinedDaysEntries(0L, null, 0L, true, now)
        assertEquals(1, entries.size)
        assertEquals("QQMusic", entries[0].label)
        assertEquals(null, entries[0].days)
        assertTrue(entries[0].editable)
    }

    @Test
    fun qqHiddenWhenNotLoggedIn() {
        assertEquals(emptyList<JoinedDayEntry>(), joinedDaysEntries(0L, null, now - 10 * day, false, now))
    }

    @Test
    fun allThreeInOrder() {
        val entries = joinedDaysEntries(now - 5 * day, 100, now - 2 * day, true, now)
        assertEquals(listOf("TokiMusic", "NeteaseMusic", "QQMusic"), entries.map { it.label })
        assertEquals(listOf(5, 100, 2), entries.map { it.days })
    }

    @Test
    fun qqRegisterDateValidation() {
        // 今天有效，过去的有效，未来与 1999 年之前无效，0 无效。
        assertTrue(isValidQqRegisterDate(normalizeToLocalStartOfDay(now), now))
        assertTrue(isValidQqRegisterDate(now - 100 * day, now))
        assertFalse(isValidQqRegisterDate(now + 2 * day, now))
        assertFalse(isValidQqRegisterDate(QQ_REGISTER_EARLIEST_MS - 1, now))
        assertFalse(isValidQqRegisterDate(0L, now))
    }

    @Test
    fun normalizeToLocalStartOfDayIsIdempotentAndWithinDay() {
        val x = now + 13 * 3_600_000L + 1_000L
        val normalized = normalizeToLocalStartOfDay(x)
        assertTrue(normalized <= x)
        assertTrue(x - normalized < day)
        assertEquals(normalized, normalizeToLocalStartOfDay(normalized))
    }

    @Test
    fun utcDayRoundTripKeepsCalendarDate() {
        // UTC 日起点 → 本地起点 → UTC 日起点，往返后是同一天。
        val utcDay = 1_700_000_000_000L - (1_700_000_000_000L % day)
        val local = utcDayStartToLocalStartOfDay(utcDay)
        assertEquals(utcDay, localStartOfDayToUtcDayStart(local))
        // 往返后的本地日期与原 UTC 日期一致。
        val zone = ZoneId.systemDefault()
        assertEquals(
            Instant.ofEpochMilli(utcDay).atZone(java.time.ZoneOffset.UTC).toLocalDate(),
            Instant.ofEpochMilli(local).atZone(zone).toLocalDate(),
        )
    }
}
