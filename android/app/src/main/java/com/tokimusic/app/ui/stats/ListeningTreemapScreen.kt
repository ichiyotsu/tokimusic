package com.tokimusic.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SettingsRow
import com.tokimusic.app.ui.theme.CirroBlack
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
        val label = if (d == today) "今天" else "${d.monthValue}月${d.dayOfMonth}日"
        TreemapEntry(label, dayMillis(d))
    }.filter { it.millis > 0 }
}

/** 最近 N 个月的月时长（纯函数，可单测）；跨年的月份带年份。 */
fun treemapMonthEntries(today: LocalDate, count: Int, monthMillis: (YearMonth) -> Long): List<TreemapEntry> {
    val months = (0 until count).map { YearMonth.from(today).minusMonths(it.toLong()) }
    return months.map { ym ->
        val label = if (ym.year == today.year) "${ym.monthValue}月" else "${ym.year}年${ym.monthValue}月"
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

/**
 * 听歌时长树状图（二级页，从时长页的总数点进来）：日 / 月 / 年三档，
 * 分别是最近 7 天 / 最近 30 日 / 最近 12 个月；每块按时长大小排布，
 * 标注日期与时长；无记录的日期不占块。数据源是本地 PlaybackStatsStore
 * 的按日累计（只统计安装后，无历史数据可补）。
 */
@Composable
fun ListeningTreemapScreen(vm: AppViewModel, topPadding: Dp, bottomPadding: Dp) {
    val store = vm.player.statsStore
    var range by remember { mutableStateOf(TreemapRange.Week) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    @Suppress("UNUSED_EXPRESSION")
    tick

    val today = LocalDate.now()
    val entries = when (range) {
        TreemapRange.Week -> treemapDayEntries(today, 7) { store.dayMillis(it) }
        TreemapRange.Month -> treemapDayEntries(today, 30) { store.dayMillis(it) }
        TreemapRange.Year -> treemapMonthEntries(today, 12) { store.monthMillis(it.year, it.monthValue) }
    }
    val totalMs = entries.sumOf { it.millis }
    val emptyBuckets = range.buckets - entries.size
    val rangeLabel = when (range) {
        TreemapRange.Week -> "最近 7 天"
        TreemapRange.Month -> "最近 30 日"
        TreemapRange.Year -> "最近 12 个月"
    }
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.surfaceContainerHighest to scheme.onSurface,
        scheme.surfaceContainerHigh to scheme.onSurface,
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    TreemapRange.entries.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = range == r,
                            onClick = { range = r },
                            shape = SegmentedButtonDefaults.itemShape(i, TreemapRange.entries.size),
                            label = { Text(r.label) },
                        )
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text("$rangeLabel 共 ${formatLongDuration(totalMs)}", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        if (emptyBuckets > 0) "其中 $emptyBuckets 个无记录，不占块" else "每天 / 每月都有记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        "仅统计本机自安装后的播放时长；卸载或清除数据后清零。",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            item {
                if (entries.isEmpty()) {
                    Text(
                        "暂无记录，播放后开始统计",
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    val rects = treemapLayout(entries)
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 24.dp),
                    ) {
                        val density = LocalDensity.current
                        val wPx = with(density) { maxWidth.toPx() }
                        val hPx = with(density) { 380.dp.toPx() }
                        rects.forEachIndexed { index, rect ->
                            val (bg, fg) = palette[index % palette.size]
                            val w = (rect.right - rect.left) * wPx
                            val h = (rect.bottom - rect.top) * hPx
                            val wDp = with(density) { w.toDp() }
                            val hDp = with(density) { h.toDp() }
                            Box(
                                Modifier
                                    .offset { IntOffset((rect.left * wPx).roundToInt() + 2, (rect.top * hPx).roundToInt() + 2) }
                                    .size((wDp - 4.dp).coerceAtLeast(0.dp), (hDp - 4.dp).coerceAtLeast(0.dp))
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bg)
                                    .padding(8.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                if (wDp > 76.dp && hDp > 52.dp) {
                                    Column {
                                        Text(rect.entry.label, style = MaterialTheme.typography.titleMedium, color = fg)
                                        Text(formatLongDuration(rect.entry.millis), style = MaterialTheme.typography.bodySmall, color = fg)
                                    }
                                } else if (wDp > 44.dp && hDp > 28.dp) {
                                    Text(rect.entry.label, style = MaterialTheme.typography.bodySmall, color = fg)
                                }
                            }
                        }
                    }
                }
            }
            if (entries.isNotEmpty()) {
                // 第五轮第 23 项：与时长页同步——明细行走 SettingsRow（与
                // ListeningStatsScreen 同款），时长用 h 格式（formatLongDuration）。
                items(entries.sortedByDescending { it.millis }) { entry ->
                    SettingsRow(
                        headline = entry.label,
                        trailing = { Text(formatLongDuration(entry.millis), style = MaterialTheme.typography.bodyMedium) },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(listOf(CirroBlack, Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, CirroBlack)),
            ),
        )
    }
}
