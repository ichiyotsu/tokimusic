package com.tokimusic.app.ui.stats

import java.time.LocalDate
import com.tokimusic.app.R
import com.tokimusic.app.data.AppStrings
import java.time.YearMonth

/** 树状图的一块：标签 + 时长（毫秒）。面积按时长占比分。 */
data class TreemapEntry(val label: String, val millis: Long)

/** 树状图一块在整图中的位置（0..1 的比例坐标）。 */
data class TreemapRect(val entry: TreemapEntry, val left: Float, val top: Float, val right: Float, val bottom: Float)

enum class TreemapRange(val label: String, val buckets: Int) { Week("日", 7), Month("月", 30), Year("年", 12) }

/**
 * 最近 N 天的日时长（纯函数，可单测）：无记录的天返回 0，由调用方决定
 * 不占块；不编造历史数据。
 */
fun treemapDayEntries(today: LocalDate, count: Int, dayMillis: (LocalDate) -> Long): List<TreemapEntry> {
    val days = (0 until count).map { today.minusDays(it.toLong()) }
    return days.map { d ->
        val label = if (d == today) AppStrings.getOrFallback(R.string.stats_today, "今天") else AppStrings.getOrFallback(R.string.stats_month_day, "%1\$d月%2\$d日", d.monthValue, d.dayOfMonth)
        TreemapEntry(label, dayMillis(d))
    }.filter { it.millis > 0 }
}

/** 最近 N 个月的月时长（纯函数，可单测）；跨年的月份带年份。 */
fun treemapMonthEntries(today: LocalDate, count: Int, monthMillis: (YearMonth) -> Long): List<TreemapEntry> {
    val months = (0 until count).map { YearMonth.from(today).minusMonths(it.toLong()) }
    return months.map { ym ->
        val label = if (ym.year == today.year) AppStrings.getOrFallback(R.string.stats_month_only, "%1\$d月", ym.monthValue) else AppStrings.getOrFallback(R.string.stats_month_label, "%1\$d年%2\$d月", ym.year, ym.monthValue)
        TreemapEntry(label, monthMillis(ym))
    }.filter { it.millis > 0 }
}

/**
 * 简单二分树状图（纯函数，可单测）：按时长降序，在累计过半处切分，
 * 横竖交替切；每块面积 ∝ 时长，整图铺满、无重叠。
 */
fun treemapLayout(entries: List<TreemapEntry>): List<TreemapRect> {
    val sorted = entries.filter { it.millis > 0 }.sortedByDescending { it.millis }
    val total = sorted.sumOf { it.millis }
    if (sorted.isEmpty() || total <= 0) return emptyList()
    val out = mutableListOf<TreemapRect>()
    fun split(list: List<TreemapEntry>, left: Float, top: Float, right: Float, bottom: Float, horizontal: Boolean) {
        if (list.isEmpty()) return
        if (list.size == 1) {
            out += TreemapRect(list[0], left, top, right, bottom)
            return
        }
        val sum = list.sumOf { it.millis }.toFloat()
        var acc = 0f
        var cut = 1
        for (i in list.indices) {
            acc += list[i].millis
            if (acc * 2 >= sum) {
                cut = i + 1
                break
            }
        }
        cut = cut.coerceIn(1, list.size - 1)
        val firstSum = list.take(cut).sumOf { it.millis }.toFloat()
        val frac = firstSum / sum
        if (horizontal) {
            val mid = left + (right - left) * frac
            split(list.take(cut), left, top, mid, bottom, !horizontal)
            split(list.drop(cut), mid, top, right, bottom, !horizontal)
        } else {
            val mid = top + (bottom - top) * frac
            split(list.take(cut), left, top, right, mid, !horizontal)
            split(list.drop(cut), left, mid, right, bottom, !horizontal)
        }
    }
    split(sorted, 0f, 0f, 1f, 1f, horizontal = true)
    return out
}
