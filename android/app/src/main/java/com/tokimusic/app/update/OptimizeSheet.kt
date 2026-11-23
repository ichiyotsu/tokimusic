package com.tokimusic.app.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.components.TokiSheetContainer

/**
 * 第五轮第 15 项：更新完首次启动的优化二次确认（底部弹窗）。
 * result 为 null 时问“是否优化”，展示优化前数值与预计清理量；
 * 清理完后展示优化后数值与实际清理量。
 * 拒绝（“暂不优化”或上滑关闭）= 本次跳过，下次升版再问（调用方记下
 * 当前 versionCode，见 TokiMusicApp）。
 */
@Composable
fun OptimizeSheet(
    snapshot: OptimizeSnapshot,
    result: OptimizeResult?,
    cleaning: Boolean,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    TokiSheetContainer(onDismiss = onDecline) {
        if (result == null) {
            Text("优化软件", style = MaterialTheme.typography.headlineSmall)
            Text(
                "检测到已更新到新版本，旧安装包和图片缓存占用了空间，是否现在优化？",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OptimizeRow("缓存占用", AppOptimizer.formatBytes(snapshot.cacheTotalBytes))
                OptimizeRow("其中旧安装包", AppOptimizer.formatBytes(snapshot.updateApkBytes))
                OptimizeRow("其中图片缓存", AppOptimizer.formatBytes(snapshot.coilDiskBytes))
                OptimizeRow("预计可清理", AppOptimizer.formatBytes(snapshot.estimatedCleanBytes))
            }
            Button(
                onClick = onConfirm,
                enabled = !cleaning,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(if (cleaning) "正在优化…" else "立即优化")
            }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("暂不优化")
            }
        } else {
            Text("优化完成", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OptimizeRow("已清理", AppOptimizer.formatBytes(result.cleanedBytes))
                OptimizeRow("优化前缓存", AppOptimizer.formatBytes(result.before.cacheTotalBytes))
                OptimizeRow("优化后缓存", AppOptimizer.formatBytes(result.cacheTotalAfterBytes))
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("知道了")
            }
        }
    }
}

@Composable
private fun OptimizeRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
