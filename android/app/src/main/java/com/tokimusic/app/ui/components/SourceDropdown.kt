package com.tokimusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.theme.NeteaseRed
import com.tokimusic.app.ui.theme.QqGreen

/**
 * 源切换下拉（第六轮第 1/20 项）：主页与资料库左上角的源入口——只在
 * 双源都启用时出现。入口是 48dp 圆钮，红绿两枚实心音符叠放；点开是
 * 28dp 大圆角下拉，网易云在上、QQ 在下，每行 logo + 选中态（未选中
 * 行置灰），点行即切换选中态，点空白处收起。与设置里的开关翻同一个
 * 启用标志（双源都选 = 两源内容都显示；都不选 = 纯本地播放器）。
 */
@Composable
fun SourceDropdownButton(
    neteaseEnabled: Boolean,
    qqEnabled: Boolean,
    onToggleNetease: () -> Unit,
    onToggleQq: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = "切换音乐源",
                tint = NeteaseRed,
                modifier = Modifier.size(20.dp).offset((-4).dp, (-3).dp),
            )
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = QqGreen,
                modifier = Modifier.size(20.dp).offset(4.dp, 3.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            SourceDropdownRow(
                name = "网易云音乐",
                tint = NeteaseRed,
                selected = neteaseEnabled,
                onClick = { onToggleNetease(); expanded = false },
            )
            SourceDropdownRow(
                name = "QQ音乐",
                tint = QqGreen,
                selected = qqEnabled,
                onClick = { onToggleQq(); expanded = false },
            )
        }
    }
}

@Composable
private fun SourceDropdownRow(name: String, tint: Color, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(name, Modifier.alpha(if (selected) 1f else 0.5f)) },
        onClick = onClick,
        leadingIcon = {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = tint, modifier = Modifier.alpha(if (selected) 1f else 0.5f))
        },
        trailingIcon = {
            if (selected) Icon(Icons.Filled.Check, contentDescription = "已选中")
        },
    )
}
