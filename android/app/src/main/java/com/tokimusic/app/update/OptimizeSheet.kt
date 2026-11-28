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
import androidx.compose.ui.res.stringResource
import com.tokimusic.app.R

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
            Text(stringResource(R.string.optimize_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.optimize_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OptimizeRow(stringResource(R.string.optimize_cache_size), AppOptimizer.formatBytes(snapshot.cacheTotalBytes))
                OptimizeRow(stringResource(R.string.optimize_old_package), AppOptimizer.formatBytes(snapshot.staleUpdateApkBytes))
                OptimizeRow(stringResource(R.string.optimize_play_cache), AppOptimizer.formatBytes(snapshot.otherCacheBytes))
                OptimizeRow(stringResource(R.string.optimize_estimated), AppOptimizer.formatBytes(snapshot.estimatedCleanBytes))
            }
            Button(
                onClick = onConfirm,
                enabled = !cleaning,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(if (cleaning) stringResource(R.string.optimize_running) else stringResource(R.string.optimize_now))
            }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.optimize_skip))
            }
        } else {
            Text(stringResource(R.string.optimize_done), style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OptimizeRow(stringResource(R.string.optimize_cleaned), AppOptimizer.formatBytes(result.cleanedBytes))
                OptimizeRow(stringResource(R.string.optimize_cache_before), AppOptimizer.formatBytes(result.before.cacheTotalBytes))
                OptimizeRow(stringResource(R.string.optimize_cache_after), AppOptimizer.formatBytes(result.cacheTotalAfterBytes))
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.optimize_ok))
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
