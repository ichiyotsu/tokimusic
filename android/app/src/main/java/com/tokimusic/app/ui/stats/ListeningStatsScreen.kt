package com.tokimusic.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.tokimusic.app.ui.components.SectionTitle
import com.tokimusic.app.ui.components.SettingsRow
import com.tokimusic.app.ui.components.TokiPillGroup
import com.tokimusic.app.ui.components.formatHours
import com.tokimusic.app.ui.theme.CirroBlack
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class Granularity(val label: String) { Day("日"), Month("月"), Year("年") }

/**
 * Round 3 item 13: listening-time page.
 * - "tokimusic 时长": locally accumulated real playback time (see
 *   PlaybackStatsStore) — only counts from when this feature shipped.
 * - Sixth round, batch 2, item 10: the treemap preview is embedded here
 *   (its range follows this page's granularity — no own selector), the
 *   old "网易云统计" section is deleted; the NetEase estimate now lives
 *   only in the 〈!〉 floating annotation ("Netease x h").
 * - Sixth round, batch 2, item 11: 日/月/年 uses the TokiUI pill group
 *   (28dp container + selected pill), not the M3 segmented buttons.
 * Default granularity is Month; prev/next browses past periods only.
 */
@Composable
fun ListeningStatsScreen(
    vm: AppViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
    /** Sixth round, batch 2, item 10: the 〈!〉 annotation, toggled from the title bar. */
    noteVisible: Boolean = false,
    onNoteDismiss: () -> Unit = {},
) {
    val mine by vm.mine.collectAsState()
    val store = vm.player.statsStore
    var granularity by remember { mutableStateOf(Granularity.Month) }
    // 0 = current period, negative = past; never positive (no future).
    var offset by remember { mutableIntStateOf(0) }
    // Refresh while open so the running total grows during playback.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    @Suppress("UNUSED_EXPRESSION")
    tick
    // Note-dismiss tap handling must not call remember conditionally.
    val noteInteractionSource = remember { MutableInteractionSource() }

    val today = LocalDate.now()
    val periodLabel = when (granularity) {
        Granularity.Day -> if (offset == 0) "今天" else today.plusDays(offset.toLong()).let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" }
        Granularity.Month -> if (offset == 0) "本月" else YearMonth.now().plusMonths(offset.toLong()).let { "${it.year}年${it.monthValue}月" }
        Granularity.Year -> if (offset == 0) "今年" else "${today.year + offset}年"
    }
    val totalMs = when (granularity) {
        Granularity.Day -> store.dayMillis(today.plusDays(offset.toLong()))
        Granularity.Month -> YearMonth.now().plusMonths(offset.toLong()).let { store.monthMillis(it.year, it.monthValue) }
        Granularity.Year -> store.yearMillis(today.year + offset)
    }
    val breakdown: List<Pair<String, Long>> = when (granularity) {
        Granularity.Day -> emptyList()
        Granularity.Month -> YearMonth.now().plusMonths(offset.toLong()).let { ym ->
            store.monthDays(ym.year, ym.monthValue).map { (d, ms) -> "${d.monthValue}月${d.dayOfMonth}日" to ms }
        }
        Granularity.Year -> store.yearMonths(today.year + offset).map { (m, ms) -> "${m}月" to ms }
    }
    // Treemap range follows this page's granularity (Q12-22): 日 → 最近 7 天，
    // 月 → 最近 30 日，年 → 最近 12 个月；no own selector anymore.
    val treemapRange = when (granularity) {
        Granularity.Day -> TreemapRange.Week
        Granularity.Month -> TreemapRange.Month
        Granularity.Year -> TreemapRange.Year
    }
    val treemapEntries = when (treemapRange) {
        TreemapRange.Week -> treemapDayEntries(today, 7) { store.dayMillis(it) }
        TreemapRange.Month -> treemapDayEntries(today, 30) { store.dayMillis(it) }
        TreemapRange.Year -> treemapMonthEntries(today, 12) { store.monthMillis(it.year, it.monthValue) }
    }

    // 第三轮第 14 项：列表上下边缘 32dp 淡出（与主页、歌单详情同款）。
    // 点空白区域消掉悬浮注释（第 10 项）：外层 Box 在注释可见时接住空白处点按。
    Box(
        Modifier.fillMaxSize()
            .let {
                if (noteVisible) it.clickable(
                    interactionSource = noteInteractionSource,
                    indication = null,
                    onClick = onNoteDismiss,
                ) else it
            },
    ) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            TokiPillGroup(
                options = Granularity.entries.map { it.label },
                selectedIndex = Granularity.entries.indexOf(granularity),
                onSelect = { granularity = Granularity.entries[it]; offset = 0 },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { offset-- }, enabled = true) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一期")
                }
                Text(periodLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { if (offset < 0) offset++ }, enabled = offset < 0) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "下一期")
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(formatLongDuration(totalMs), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "tokimusic 时长 · 本地累计",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "仅统计本机自安装后的播放时长；卸载或清除数据后清零。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (breakdown.isNotEmpty()) {
            // 第五轮第 23 项：按 TokiUI 设计规范重做——明细行走 SettingsRow
            //（64dp、titleMedium 标题 + 右侧数值），不再用裸 Row。
            items(breakdown) { (label, ms) ->
                SettingsRow(
                    headline = label,
                    trailing = { Text(formatLongDuration(ms), style = MaterialTheme.typography.bodyMedium) },
                )
            }
        } else {
            item {
                Text(
                    if (totalMs > 0) "该时段内暂无更细的记录" else "暂无记录，播放后开始统计",
                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item { SectionTitle("时长分布") }
        item {
            TreemapPreview(
                entries = treemapEntries,
                range = treemapRange,
            )
        }
    }
        // Sixth round, batch 2, item 10: 〈!〉 floating annotation — "Netease x h"
        // + small "按播放次数 × 时长估算", right-aligned under the 〈!〉 icon.
        if (noteVisible) {
            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(top = topPadding + 8.dp, end = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = onNoteDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Netease ${formatHours(mine.estimatedListeningMinutes)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "按播放次数 × 时长估算",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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

/**
 * Treemap preview (sixth round, batch 2, item 10): the old treemap page's
 * 380dp chart + detail rows, embedded in the listening-time page. Range
 * follows the page's granularity; no own selector.
 */
@Composable
private fun TreemapPreview(entries: List<TreemapEntry>, range: TreemapRange) {
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
    Column(Modifier.fillMaxWidth()) {
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
            // 第五轮第 23 项：与时长页同步——明细行走 SettingsRow，时长用 h 格式。
            Column(Modifier.fillMaxWidth()) {
                entries.sortedByDescending { it.millis }.forEach { entry ->
                    SettingsRow(
                        headline = entry.label,
                        trailing = { Text(formatLongDuration(entry.millis), style = MaterialTheme.typography.bodyMedium) },
                    )
                }
            }
        }
    }
}

internal fun formatLongDuration(ms: Long): String {
    val minutes = ms / 60_000
    // 第五轮第 23 项：满 1 小时用 h 格式（整小时 "1537h"，有零头一位小数
    // 如 "1537.5h"）；不足 1 小时仍写“X 分钟”（"0.5h" 不如分钟直观）。
    return if (minutes >= 60) formatHours(minutes) else "$minutes 分钟"
}
