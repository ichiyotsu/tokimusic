package com.tokimusic.app.ui.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tokimusic.app.player.formatPointCount
import com.tokimusic.app.player.levelEarningRows
import androidx.compose.ui.res.stringResource
import com.tokimusic.app.R
import com.tokimusic.app.player.LevelEarningLabels
import com.tokimusic.app.player.MusicLevel
import com.tokimusic.app.ui.AppViewModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 音乐等级详情页（本轮 0124 第 15b 项）：从资料库等级块点入的二级页
 * （返回走 TokiMusicApp 的二级页返回链，回到资料库一级）。
 * 内容：大字号当前等级 → 本级进度条（三标注：当前总积分压条上带
 * 实色底衬、条下左本级下限、条下右下一级门槛，满级显示满级态）→
 * 「达成条件 + 达成进度」（目前只有听歌来源：听歌时长 / 整首听完
 * 奖励两行 + 今日总积分 vs 每日上限）。
 * 本轮（0125）第 3 项：beta 徽章移进悬浮标题栏（标题「音乐等级」
 * 正后方，见 TokiMusicApp / OverlayTitleHeader 的 titleBadge），
 * 内容区不再单独放标题行。第 4 项：进度条上的积分数值标签跟随
 * 填充占比移动（见 [levelProgressLabelOffsetPx]）。
 * 数字全部来自 MusicLevel 纯函数与 MusicLevelStore 快照，不另算。
 */

/**
 * 进度条数值标签的水平偏移（px，本轮 0125 第 4 项 + 14:01 细化）：
 * 标签中心锚在填充前缘（progress × 条宽），再钳制到
 * [留白, 条宽 − 标签宽 − 留白]——标签整体始终留在条内，且两端都
 * 不贴边：占比 0 时左缘距条左端恰为留白，满格时右缘距条右端同为
 * 留白。条太窄放不下两侧留白时留白对称收缩（留白 ≤ 可用行程的一半）。
 */
fun levelProgressLabelOffsetPx(barWidthPx: Int, labelWidthPx: Int, progress: Float, insetPx: Int = 0): Int {
    if (barWidthPx <= 0 || labelWidthPx <= 0) return 0
    val travel = max(0, barWidthPx - labelWidthPx)
    val inset = min(insetPx.coerceAtLeast(0), travel / 2)
    val fillEdge = (barWidthPx * progress.coerceIn(0f, 1f)).roundToInt()
    return (fillEdge - labelWidthPx / 2).coerceIn(inset, travel - inset)
}

@Composable
fun LevelDetailScreen(
    vm: AppViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    val snapshot = remember { vm.musicLevelSnapshot() }
    // 0127 第 9 条：赚分规则标签在组合作用域先拼好（LazyListScope 里不能调 stringResource）。
    val earningLabels = LevelEarningLabels(
        listenTitle = stringResource(R.string.level_rule_listen_title),
        listenDesc = stringResource(R.string.level_rule_listen_desc, MusicLevel.POINTS_PER_MINUTE),
        listenProgressTemplate = stringResource(R.string.level_rule_listen_progress),
        completeTitle = stringResource(R.string.level_rule_complete_title),
        completeDesc = stringResource(R.string.level_rule_complete_desc, MusicLevel.COMPLETION_BONUS),
        completeProgressTemplate = stringResource(R.string.level_rule_complete_progress),
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        item {
            // 大字号当前等级。
            Text(
                if (snapshot.isMaxLevel) stringResource(R.string.level_max_suffix, snapshot.level) else "Lv.${snapshot.level}",
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                // 本级进度条：填充 = 本级下限 → 下一级门槛的进度；
                // 当前总积分数值标签跟随填充占比（0125 第 4 项）：中心
                // 锚在填充前缘、随占比左右移动，两端带 6dp 留白钳制
                // （不出条、不贴边）；实色底衬保证可读。
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    val density = LocalDensity.current
                    val barWidthPx = with(density) { maxWidth.roundToPx() }
                    val labelInsetPx = with(density) { 6.dp.roundToPx() }
                    var labelWidthPx by remember { mutableStateOf(0) }
                    Box(
                        Modifier.fillMaxWidth(snapshot.progress)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Box(
                        Modifier.align(Alignment.CenterStart)
                            .offset {
                                IntOffset(
                                    levelProgressLabelOffsetPx(barWidthPx, labelWidthPx, snapshot.progress, labelInsetPx),
                                    0,
                                )
                            }
                            .onSizeChanged { labelWidthPx = it.width }
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            formatPointCount(snapshot.totalPoints),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        formatPointCount(snapshot.levelFloor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (snapshot.isMaxLevel) stringResource(R.string.level_max) else formatPointCount(snapshot.nextLevelFloor ?: snapshot.levelFloor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(stringResource(R.string.level_requirements), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.level_today_total, snapshot.todayPoints, snapshot.dailyCap),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 达成条件逐行（列表式，将来新增积分来源往 levelEarningRows 加行）。
        for (row in levelEarningRows(snapshot, earningLabels)) {
            item(key = row.title) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                row.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(row.progressText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { row.fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}
