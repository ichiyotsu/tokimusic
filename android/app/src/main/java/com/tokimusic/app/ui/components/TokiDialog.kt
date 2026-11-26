package com.tokimusic.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 全应用统一弹窗（0.1.21）：结构固定——
 * 左上角 icon → 标题 → 副标题（可省略）→ 正文 → 右下角双按钮
 * （取消 / 确认）。单按钮或带输入的弹窗也走这个组件（正文区放
 * 输入控件，按钮按需只给一个），不留旧 AlertDialog 样式孤岛。
 * 配色全走主题（surfaceContainerHigh / onSurface / error），
 * 深浅模式都成立。
 */
@Composable
fun TokiDialog(
    icon: ImageVector,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissText: String? = "取消",
    confirmDestructive: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(24.dp)) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (confirmDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                content()
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (dismissText != null) {
                        TextButton(onClick = onDismiss) { Text(dismissText) }
                    }
                    if (confirmText != null && onConfirm != null) {
                        TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                            Text(
                                confirmText,
                                color = if (confirmDestructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
