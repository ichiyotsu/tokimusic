package com.tokimusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 0.1.20 第 6 项：回顾歌单纯函数（月份键/文案/虚拟 id/聚合/门槛/渐变）。 */
class RetrospectiveTest {

    private fun ms(year: Int, month1: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month1 - 1, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun event(songId: Long, source: String = "NETEASE", atMs: Long, seconds: Long = 60) =
        PlayEvent(songId = songId, source = source, artistNames = listOf("歌手"), playedSeconds = seconds, playedAtMs = atMs)

    @Test
    fun monthKeyLabelAndIdRoundTrip() {
        val key = Retrospective.monthKey(ms(2026, 10, 15))
        assertEquals(2026, Retrospective.yearOf(key))
        assertEquals(10, Retrospective.monthOf(key))
        assertEquals("10'26", Retrospective.monthLabel(key))
        assertEquals("3'26", Retrospective.monthLabel(Retrospective.monthKey(ms(2026, 3, 1))))
        val id = Retrospective.playlistId(key)
        assertEquals(key, Retrospective.monthKeyOfPlaylist(id))
        // 既有虚拟 id 与普通 id 不被误判为回顾歌单。
        assertNull(Retrospective.monthKeyOfPlaylist(-1L))
        assertNull(Retrospective.monthKeyOfPlaylist(-5L))
        assertNull(Retrospective.monthKeyOfPlaylist(12345L))
    }

    @Test
    fun topSongsAggregatesByPlaysThenSeconds() {
        val at = ms(2026, 10, 3)
        val events = listOf(
            event(1, atMs = at, seconds = 30),
            event(1, atMs = at, seconds = 30),
            event(2, atMs = at, seconds = 500),
            event(3, atMs = at, seconds = 10),
            event(3, atMs = at, seconds = 10),
        )
        val top = Retrospective.topSongs(events, 10)
        // 1 与 3 都是 2 次：1 的累计秒数（60）大于 3（20）排前面；2 只有 1 次垫底。
        assertEquals(listOf(1L, 3L, 2L), top.map { it.songId })
        assertEquals(2, top[0].plays)
        assertEquals(60L, top[0].seconds)
    }

    @Test
    fun monthlyTopExcludesCurrentMonthAndThinMonths() {
        val now = ms(2026, 11, 20)
        val october = (1L..12L).map { event(it, atMs = ms(2026, 10, 5)) } // 12 首不同歌，够门槛
        val september = (1L..5L).map { event(it, atMs = ms(2026, 9, 5)) } // 只有 5 首，不够
        val november = (1L..15L).map { event(it, atMs = ms(2026, 11, 5)) } // 当前月，排除
        val monthly = Retrospective.monthlyTop(october + september + november, now)
        assertEquals(1, monthly.size)
        assertEquals(Retrospective.monthKey(ms(2026, 10, 1)), monthly[0].first)
        assertEquals(12, monthly[0].second.size)
    }

    @Test
    fun gradientIsDeterministicAndHasThreeColors() {
        val key = Retrospective.monthKey(ms(2026, 10, 1))
        val a = Retrospective.gradientColors(key)
        val b = Retrospective.gradientColors(key)
        assertEquals(a, b)
        assertEquals(3, a.size)
        assertTrue(a.all { it in Retrospective.MONET_PALETTE })
    }
}
