package com.tokimusic.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SongRow
import androidx.compose.ui.res.stringResource
import com.tokimusic.app.R
import com.tokimusic.app.ui.components.TokiPillGroup
import com.tokimusic.app.ui.theme.TokiBlack

private enum class ReportRange(@androidx.annotation.StringRes val labelRes: Int) { Week(R.string.stats_week), Year(R.string.stats_year) }

/**
 * 0.1.17 第 17 项：网易云收听报告——资料库网易云听歌时长入口点进来的页。
 * 框架沿用原先的 userRecord + 听歌排行：周 = 本周榜单（/user/record
 * type=1，weekData），年 = 累计榜单（type=0，allData）。0.1.18 第 5 项：
 * 月档位删除——网易云接口只提供本周与累计两档，没有按月切片，月档位之前
 * 是累计榜单 + 一段说明，现在整个档位拿掉，只留周、年。
 * 内容以接口实际返回为准：时长是按播放次数 × 时长估算的（与资料库数字
 * 同款算法），榜单是歌曲排行（右侧播放次数）——接口不返回歌单，所以
 * 没有“歌单”一栏；用户猜的“时长和歌单”里只有时长能从接口拿到。
 */
@Composable
fun NeteaseReportScreen(
    vm: AppViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
    onOpenArtist: (Long) -> Unit = {},
) {
    val mine by vm.mine.collectAsState()
    val weekly by vm.weeklyRecord.collectAsState()
    var range by remember { mutableStateOf(ReportRange.Week) }
    LaunchedEffect(Unit) { vm.loadWeeklyRecord() }
    val records = when (range) {
        ReportRange.Week -> weekly
        ReportRange.Year -> mine.record
    }
    val totalMs = records.sumOf { it.song.durationMs * it.playCount }
    // 0127 第 9 条：播放上下文文案先在组合作用域解析（行点击 lambda 里用）。
    val reportPlayContext = stringResource(R.string.stats_report_title)
    val totalLabel = when (range) {
        ReportRange.Week -> stringResource(R.string.stats_week_estimate)
        ReportRange.Year -> stringResource(R.string.stats_total_estimate)
    }

    // 第三轮第 14 项：列表上下边缘 32dp 淡出（与主页、歌单详情同款）。
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                TokiPillGroup(
                    options = ReportRange.entries.map { stringResource(it.labelRes) },
                    selectedIndex = ReportRange.entries.indexOf(range),
                    onSelect = { range = ReportRange.entries[it] },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(formatLongDuration(totalMs), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        totalLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.stats_estimate_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (records.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.stats_no_listen_records),
                        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                itemsIndexed(records) { i, r ->
                    SongRow(
                        r.song, i,
                        trailingText = stringResource(R.string.stats_play_times, r.playCount),
                        onOpenArtist = onOpenArtist,
                        onLongPress = { vm.openSongActions(it) },
                    ) { vm.play(records.map { it.song }, i, reportPlayContext) }
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(listOf(TokiBlack, Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, TokiBlack)),
            ),
        )
    }
}
