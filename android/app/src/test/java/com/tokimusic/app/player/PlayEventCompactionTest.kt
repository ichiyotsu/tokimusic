package com.tokimusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** 0.1.21：播放事件月度聚合 + 瘦身后回顾合并（纯函数）。 */
class PlayEventCompactionTest {

    private fun ms(year: Int, month1: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month1 - 1, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun event(songId: Long, source: String = "NETEASE", artists: List<String> = listOf("歌手A"), atMs: Long, seconds: Long = 60) =
        PlayEvent(songId = songId, source = source, artistNames = artists, playedSeconds = seconds, playedAtMs = atMs)

    @Test
    fun aggregateGroupsByMonthSongAndArtist() {
        val oct = ms(2026, 10, 3)
        val nov = ms(2026, 11, 3)
        val events = listOf(
            event(1L, atMs = oct, seconds = 30),
            event(1L, atMs = oct + 1000, seconds = 50),
            event(1L, source = "QQ", atMs = oct, seconds = 20),
            event(2L, artists = listOf("歌手A", "歌手B"), atMs = nov, seconds = 40),
        )
        val (songs, artists) = aggregatePlayEvents(events)
        val octKey = Retrospective.monthKey(oct)
        val novKey = Retrospective.monthKey(nov)
        val s1 = songs.first { it.monthKey == octKey && it.songId == 1L && it.source == "NETEASE" }
        assertEquals(2, s1.plays)
        assertEquals(80L, s1.seconds)
        val sQq = songs.first { it.monthKey == octKey && it.source == "QQ" }
        assertEquals(1, sQq.plays)
        val aOct = artists.first { it.monthKey == octKey && it.artist == "歌手A" }
        assertEquals(3, aOct.plays) // 两次 NETEASE + 一次 QQ 都记歌手A
        val aNovB = artists.first { it.monthKey == novKey && it.artist == "歌手B" }
        assertEquals(1, aNovB.plays)
    }

    @Test
    fun startOfCurrentMonthIsFirstDayMidnight() {
        val start = startOfCurrentMonthMs(ms(2026, 11, 26))
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun monthlyTopMergedKeepsAggregatedMonths() {
        // 12 首不同歌在 10 月各播 1 次（过 ≥10 门槛），原始事件已被
        // 瘦身剪掉、只剩聚合——合并后 10 月回顾照样出数。
        val oct = ms(2026, 10, 10)
        val octKey = Retrospective.monthKey(oct)
        val aggs = (1L..12L).map { MonthlySongAgg(octKey, it, "NETEASE", 1, 60) }
        val merged = Retrospective.monthlyTopMerged(emptyList(), aggs, ms(2026, 11, 26))
        assertEquals(1, merged.size)
        assertEquals(octKey, merged[0].first)
        assertEquals(12, merged[0].second.size)
    }

    @Test
    fun monthlyTopMergedSumsRawAndAggregatedWithoutDoubleCount() {
        // 同一首歌：原始 2 次 + 聚合 3 次（不同事件的两部分）→ 5 次。
        val oct = ms(2026, 10, 10)
        val octKey = Retrospective.monthKey(oct)
        val raw = (1L..11L).flatMap { id ->
            if (id == 1L) listOf(event(1L, atMs = oct), event(1L, atMs = oct + 5)) else listOf(event(id, atMs = oct))
        }
        val aggs = listOf(MonthlySongAgg(octKey, 1L, "NETEASE", 3, 180))
        val merged = Retrospective.monthlyTopMerged(raw, aggs, ms(2026, 11, 26))
        val top = merged.first { it.first == octKey }.second
        assertEquals(5, top.first { it.songId == 1L }.plays)
        assertEquals(11, top.size)
    }

    @Test
    fun monthlyTopMergedExcludesCurrentMonth() {
        val now = ms(2026, 11, 26)
        val curKey = Retrospective.monthKey(now)
        val aggs = (1L..12L).map { MonthlySongAgg(curKey, it, "NETEASE", 1, 60) }
        assertEquals(0, Retrospective.monthlyTopMerged(emptyList(), aggs, now).size)
    }
}
