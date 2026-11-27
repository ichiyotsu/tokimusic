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

    @Test
    fun recentDistinctKeepsLatestPerSong() {
        // 本轮（0124）第 22 项：倒序输入、同歌去重保留最近一次、跨源同 id 不误杀。
        val t = ms(2026, 11, 20)
        val events = listOf(
            event(7, "NETEASE", t + 3000),
            event(8, "QQ", t + 2000),
            event(7, "NETEASE", t + 1000),
            event(7, "QQ", t + 500),
        )
        val out = recentDistinct(events, 10)
        assertEquals(listOf(7L to "NETEASE", 8L to "QQ", 7L to "QQ"), out.map { it.songId to it.source })
        assertEquals(t + 3000, out.first().playedAtMs)
        assertEquals(2, recentDistinct(events, 2).size)
        assertEquals(0, recentDistinct(events, 0).size)
    }

    @Test
    fun monthTopCountsHasNoThresholdAndMergesAggregates() {
        // 本轮（0124）第 11 项：指定月份不设门槛，事件 + 聚合合并计数。
        val key = Retrospective.monthKey(ms(2026, 10, 1))
        val events = listOf(
            event(1, "NETEASE", ms(2026, 10, 3)),
            event(1, "NETEASE", ms(2026, 10, 4)),
            event(2, "QQ", ms(2026, 10, 5)),
            event(3, "NETEASE", ms(2026, 9, 5)),
        )
        val aggregates = listOf(
            MonthlySongAgg(monthKey = key, songId = 2, source = "QQ", plays = 5, seconds = 900),
        )
        val top = Retrospective.monthTopCounts(events, aggregates, key)
        assertEquals(2, top.size)
        assertEquals(2L, top[0].songId)
        assertEquals(6, top[0].plays)
        assertEquals(1L, top[1].songId)
        assertEquals(2, top[1].plays)
        // 记忆歌单 id 与回顾 id 互不误判。
        assertEquals(key, Retrospective.memoryMonthKeyOf(Retrospective.memoryPlaylistId(key)))
        assertNull(Retrospective.memoryMonthKeyOf(Retrospective.playlistId(key)))
    }

    @Test
    fun topSongsInRangeMergesRawAndFullMonthAggregates() {
        // 本轮（0124）第 10 项：区间内原始事件逐条计；整月落在区间内
        // 的聚合才并入（半月聚合不并，避免与原始事件重复/越界）。
        val octStart = Retrospective.monthStartMs(Retrospective.monthKey(ms(2026, 10, 1)))
        val novStart = Retrospective.monthStartMs(Retrospective.monthKey(ms(2026, 11, 1)))
        val decStart = Retrospective.monthStartMs(Retrospective.monthKey(ms(2026, 12, 1)))
        val events = listOf(
            event(1, "NETEASE", ms(2026, 11, 3)),
            event(1, "NETEASE", ms(2026, 11, 4)),
            event(2, "QQ", ms(2026, 11, 5)),
            event(9, "NETEASE", ms(2026, 9, 5)),
        )
        val aggregates = listOf(
            MonthlySongAgg(Retrospective.monthKey(ms(2026, 10, 1)), 3, "NETEASE", 7, 700),
            MonthlySongAgg(Retrospective.monthKey(ms(2026, 11, 1)), 4, "NETEASE", 9, 900),
        )
        // 区间 = 11 月整月：10 月聚合不入（整月不在区间）、11 月聚合
        // 本应入——但注意 11 月聚合与 11 月原始事件在真实数据里互斥
        // （瘦身不变量），这里只验区间过滤：聚合按整月判定。
        val november = Retrospective.topSongsInRange(events, aggregates, novStart, decStart)
        assertEquals(listOf(4L, 1L, 2L), november.map { it.songId })
        assertEquals(9, november[0].plays)
        // 区间 = 10 月整月：只有 10 月聚合一首。
        val october = Retrospective.topSongsInRange(events, aggregates, octStart, novStart)
        assertEquals(listOf(3L), october.map { it.songId })
        // 日区间（11 月 3 日当天）：只有原始事件、无聚合。
        val day = Retrospective.topSongsInRange(events, aggregates, ms(2026, 11, 3) - 12 * 3600_000L, ms(2026, 11, 3) + 12 * 3600_000L)
        assertEquals(listOf(1L), day.map { it.songId })
    }
}
