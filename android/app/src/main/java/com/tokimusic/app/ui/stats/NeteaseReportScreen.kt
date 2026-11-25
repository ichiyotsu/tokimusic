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
import com.tokimusic.app.ui.components.TokiPillGroup
import com.tokimusic.app.ui.theme.TokiBlack

private enum class ReportRange(val label: String) { Week("周"), Month("月"), Year("年") }

/**
 * 0.1.17 第 17 项：网易云收听报告——资料库网易云听歌时长入口点进来的页。
 * 框架沿用原先的 userRecord + 听歌排行：周 = 本周榜单（/user/record
 * type=1，weekData），年 = 累计榜单（type=0，allData）；网易云接口没有
 * 按月切片，月档位显示累计榜单并在页内注明。
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
    // 月档位无接口：显示累计榜单（与年档位同一份），页内注明。
    val records = when (range) {
        ReportRange.Week -> weekly
        ReportRange.Month -> mine.record
        ReportRange.Year -> mine.record
    }
    val totalMs = records.sumOf { it.song.durationMs * it.playCount }
    val totalLabel = when (range) {
        ReportRange.Week -> "本周预估听歌时长"
        ReportRange.Month -> "累计预估听歌时长"
        ReportRange.Year -> "累计预估听歌时长"
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
                    options = ReportRange.entries.map { it.label },
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
                        "按播放次数 × 时长估算",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (range == ReportRange.Month) {
                        Text(
                            "网易云接口只提供本周与累计两档记录，没有按月切片；这里显示累计榜单。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (records.isEmpty()) {
                item {
                    Text(
                        "暂无收听记录",
                        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                itemsIndexed(records) { i, r ->
                    SongRow(
                        r.song, i,
                        trailingText = "${r.playCount} 次",
                        onOpenArtist = onOpenArtist,
                        onLongPress = { vm.openSongActions(it) },
                    ) { vm.play(records.map { it.song }, i, "听歌报告") }
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
