package com.tokimusic.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SectionTitle
import com.tokimusic.app.ui.theme.CirroBlack
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.delay

private enum class Granularity(val label: String) { Day("日"), Month("月"), Year("年") }

/**
 * Round 3 item 13: listening-time page. Two sources, shown separately:
 * - "tokimusic 时长": locally accumulated real playback time (see
 *   PlaybackStatsStore) — only counts from when this feature shipped.
 * - "网易云统计": estimated from /user/record play counts; NetEase has no
 *   per-day/month/year endpoint, so this is a single all-time estimate.
 * Default granularity is Month; prev/next browses past periods only.
 */
@Composable
fun ListeningStatsScreen(vm: AppViewModel, topPadding: Dp, bottomPadding: Dp, onOpenTreemap: () -> Unit) {
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

    // 第三轮第 14 项：列表上下边缘 32dp 淡出（与主页、歌单详情同款）。
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Granularity.entries.forEachIndexed { i, g ->
                    SegmentedButton(
                        selected = granularity == g,
                        onClick = { granularity = g; offset = 0 },
                        shape = SegmentedButtonDefaults.itemShape(i, Granularity.entries.size),
                        label = { Text(g.label) },
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { offset-- }, enabled = true) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上一期")
                }
                Text(periodLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { if (offset < 0) offset++ }, enabled = offset < 0) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下一期")
                }
            }
        }
        item {
            // 第 5 项：总时长可点，点进树状图页。
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenTreemap() }
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
                Icon(Icons.Filled.ChevronRight, contentDescription = "查看分布")
            }
        }
        if (breakdown.isNotEmpty()) {
            items(breakdown) { (label, ms) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(formatLongDuration(ms), style = MaterialTheme.typography.bodyMedium)
                }
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
        item { SectionTitle("网易云统计") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("累计听歌（估算）", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${mine.estimatedListeningMinutes / 60} 小时", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Text(
                "按播放次数 × 歌曲时长估算；网易云不提供按日/月/年的时长数据。",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

internal fun formatLongDuration(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    return if (hours > 0) "$hours 小时 ${minutes % 60} 分" else "$minutes 分钟"
}
