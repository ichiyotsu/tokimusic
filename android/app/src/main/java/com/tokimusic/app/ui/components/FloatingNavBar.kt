package com.tokimusic.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tokimusic.app.R
import com.tokimusic.app.ui.Dest
import com.tokimusic.app.ui.pressScale
import com.tokimusic.app.ui.theme.tokiPillColor

data class NavBarEntry(val dest: Dest, val icon: ImageVector, val selectedIcon: ImageVector, val label: Int)

/**
 * 底部悬浮胶囊 tab（纯图标无文字）。0.1.21 第 1 项：恢复独立搜索
 * 圆钮——搜索不在 tab 里，走胶囊旁的 TokiSideActionButton（打开
 * 0.1.20 的独立搜索页 Dest.Search）；0.1.20 第 11 项曾把搜索并进
 * 胶囊第三 tab 并删掉圆钮，本轮按用户要求恢复 0.1.19 形态。胶囊
 * 回到主页 / 资料库 2 tab；同一列表也驱动 rail，rail 的搜索同样
 * 是条目之后的独立项，两处不会漂移。
 */
val navEntries = listOf(
    NavBarEntry(Dest.Home, Icons.Outlined.Home, Icons.Rounded.Home, R.string.nav_home),
    NavBarEntry(Dest.Library, Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic, R.string.nav_library),
)

/**
 * Narrow-window capsule, TokiStudio 0.1.10 structure.
 *
 * Size animation and clipping are separated: the glass lives on a
 * matchParentSize sibling (the ONLY regular child — the item row — is what
 * sizes the capsule; two matchParentSize children would collapse it to 0,
 * the 0.1.7 vanishing-bar bug). The label expands with a fast spring and
 * overshoots, so the clip guard sits on the item row: it clips to the row
 * width plus an 8dp slack into the gap before the side button, never on the
 * outer box (clipping the glass box would blacken its edge mid-animation).
 *
 * Glass: frost only, no thick stroke. 0.1.21 第 4 项：玻璃关的实色
 * 适配（3dp primary 描边 + 加宽内边距）已随实色预设整套删除。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingNavBar(
    current: Dest,
    onSelect: (Dest) -> Unit,
    modifier: Modifier = Modifier,
    glass: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val inset = 8.dp
    Box(
        modifier = modifier
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .then(glass),
        )
        Row(
            modifier = Modifier
                .padding(horizontal = inset, vertical = inset)
                .drawWithContent {
                    val slack = 8.dp.toPx()
                    clipRect(left = -slack, top = 0f, right = size.width + slack, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navEntries.forEach { entry -> NavItem(entry, current == entry.dest, onSelect) }
        }
    }
}

/**
 * Independent round glass button beside the capsule (TokiStudio 0.1.10,
 * after TokiNote's standalone button): 56dp circle, 8dp from the capsule,
 * same glass layer as the capsule. 0.1.21 第 4 项：玻璃关时整圆填
 * primary 的实色形态已随实色预设删除，只剩玻璃形态。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TokiSideActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    glass: Modifier,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    // Layout and hit area lock inside the 56dp circle. Blur and press-scale
    // must not draw outside it, or the animation would cover the capsule.
    // The visible 8dp gap still comes from the outer row.
    Box(modifier.requiredSize(56.dp).clipToBounds()) {
        TokiLongPressLabel(contentDescription) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .then(glass)
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                    .pressScale(interaction),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * 导航项：纯图标，无文字标签。选中项落在浅灰紫 pill 上，未选中直接落在
 * 底栏上；两个 icon 都是白色（0.1.19 第 1 项；0.1.18 曾用浅紫），
 * 选中态靠 pill + 实心图标区分。宽 64dp、高 56dp，图标 24dp。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NavItem(entry: NavBarEntry, selected: Boolean, onSelect: (Dest) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(entry.label)
    val pillColor = tokiPillColor()
    // 0.1.19 第 1 项：选中与未选中 icon 同色（0.1.18 是浅紫 #D0A9E8）；
    // 选中态靠 pill + 实心 icon 形状区分。0.1.21 第 7 项：icon 色走
    // colorScheme.onSurface——深色方案 onSurface = 白（与原 TokiNavIcon
    // 一字不变），浅色方案 = 墨色，白底栏上不再白 icon 白底。
    val iconTint = scheme.onSurface
    TokiLongPressLabel(label) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 56.dp)
                .clip(CircleShape)
                .background(if (selected) pillColor else Color.Transparent)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = scheme.primary.copy(alpha = 0.28f)),
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(entry.dest)
                }
                .pressScale(interaction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) entry.selectedIcon else entry.icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
