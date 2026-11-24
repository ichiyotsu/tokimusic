package com.tokimusic.app.player

import android.content.Context
import java.time.LocalDate

/**
 * Round 3 item 13: locally accumulated real playback time, bucketed by day.
 *
 * NetEase has no per-day/month/year listening-time endpoint (userRecord is
 * only a weekly/all-time song × play-count aggregate), so the per-period
 * durations on the stats page can only come from here. Limits, stated in
 * the UI: counts only from when this feature shipped, resets on uninstall /
 * clear data, and is not synced across devices. Shown as "tokimusic 时长".
 */
class PlaybackStatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tokimusic_playback_stats", Context.MODE_PRIVATE)

    /** Adds [ms] of real playback to the given day's bucket (default today). */
    fun addPlayed(ms: Long, date: LocalDate = LocalDate.now()) {
        if (ms <= 0) return
        val key = keyFor(date)
        prefs.edit().putLong(key, prefs.getLong(key, 0L) + ms).apply()
    }

    fun dayMillis(date: LocalDate): Long = prefs.getLong(keyFor(date), 0L)

    fun monthMillis(year: Int, month: Int): Long {
        val days = LocalDate.of(year, month, 1).lengthOfMonth()
        var total = 0L
        for (d in 1..days) total += dayMillis(LocalDate.of(year, month, d))
        return total
    }

    /** Days in the month that have data, newest first. */
    fun monthDays(year: Int, month: Int): List<Pair<LocalDate, Long>> {
        val days = LocalDate.of(year, month, 1).lengthOfMonth()
        return (1..days).map { LocalDate.of(year, month, it) }
            .map { it to dayMillis(it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.first }
    }

    fun yearMillis(year: Int): Long = (1..12).sumOf { monthMillis(year, it) }

    /**
     * 全部累计播放毫秒（0.1.13 第 23 项：离线模式的 tokimusic 听歌时长）——
     * 扫全部 "day:" 键求和；从功能上线起累计，卸载 / 清数据后归零。
     */
    fun totalMillis(): Long =
        prefs.all.entries.filter { (k, _) -> k.startsWith("day:") }
            .sumOf { (_, v) -> (v as? Long) ?: 0L }

    /** 全部累计播放分钟（totalMillis 的分钟数，向下取整）。 */
    fun totalMinutes(): Long = totalMillis() / 60_000L

    /** Months in the year that have data, newest first. */
    fun yearMonths(year: Int): List<Pair<Int, Long>> =
        (1..12).map { it to monthMillis(year, it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.first }

    private fun keyFor(date: LocalDate): String = "day:$date"
}
