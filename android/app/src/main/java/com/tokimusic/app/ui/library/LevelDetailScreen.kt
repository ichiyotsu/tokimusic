package com.tokimusic.app.ui.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokimusic.app.player.formatPointCount
import com.tokimusic.app.player.levelEarningRows
import com.tokimusic.app.ui.AppViewModel

/**
 * 音乐等级详情页（本轮 0124 第 15b 项）：从资料库等级块点入的二级页
 * （返回走 TokiMusicApp 的二级页返回链，回到资料库一级）。
 * 内容：标题 + beta 徽章 → 大字号当前等级 → 本级进度条（三标注：
 * 当前总积分压条上带实色底衬、条下左本级下限、条下右下一级门槛，
 * 满级显示满级态）→「达成条件 + 达成进度」（目前只有听歌来源：
 * 听歌时长 / 整首听完奖励两行 + 今日总积分 vs 每日上限）。
 * 数字全部来自 MusicLevel 纯函数与 MusicLevelStore 快照，不另算。
 */
@Composable
fun LevelDetailScreen(
    vm: AppViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    val snapshot = remember { vm.musicLevelSnapshot() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        item {
            // 标题行：标题文字正后方带 beta 小徽章（等级功能测试中，
            // 用户 2026-11-27 口径；样式仿应用内小徽章：小圆角底 + labelSmall）。
            Row(
                Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "音乐等级",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "beta",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            // 大字号当前等级。
            Text(
                if (snapshot.isMaxLevel) "Lv.${snapshot.level} · 满级" else "Lv.${snapshot.level}",
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                // 本级进度条：填充 = 本级下限 → 下一级门槛的进度；
                // 当前总积分压在条上（居中、实色底衬保证可读）。
                Box(
                    Modifier.fillMaxWidth().height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier.fillMaxWidth(snapshot.progress)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Box(
                        Modifier.align(Alignment.Center)
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
                        if (snapshot.isMaxLevel) "满级" else formatPointCount(snapshot.nextLevelFloor ?: snapshot.levelFloor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("达成条件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "今日总积分 ${snapshot.todayPoints} / ${snapshot.dailyCap} 分（每日上限）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 达成条件逐行（列表式，将来新增积分来源往 levelEarningRows 加行）。
        for (row in levelEarningRows(snapshot)) {
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
