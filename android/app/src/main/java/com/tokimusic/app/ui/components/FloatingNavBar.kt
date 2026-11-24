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
import com.tokimusic.app.ui.theme.CirroPill
import com.tokimusic.app.ui.theme.CirroPillIcon
import com.tokimusic.app.ui.theme.LocalCirroAppearance
import com.tokimusic.app.ui.theme.TokiChromeStroke

data class NavBarEntry(val dest: Dest, val icon: ImageVector, val selectedIcon: ImageVector, val label: Int)

/** Cirro：底部悬浮胶囊 2 tab，纯图标无文字（主页 / 资料库）。第六轮第 33 项删发现页；搜索不在 tab 里，改走胶囊旁的独立搜索按钮（第二轮第 23 项）。 */
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
 * Glass on: frost only, no thick stroke. Glass off: opaque surface passed in
 * as [glass], plus a 3dp primary stroke and a 6dp gap before the selected pill.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingNavBar(
    current: Dest,
    onSelect: (Dest) -> Unit,
    modifier: Modifier = Modifier,
    glass: Modifier = Modifier,
    solidStroke: Boolean = false,
) {
    val shape = RoundedCornerShape(percent = 50)
    val inset = if (solidStroke) TokiChromeStroke.width + TokiChromeStroke.barGap else 8.dp
    Box(
        modifier = modifier
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .then(glass)
                .then(
                    if (solidStroke) {
                        Modifier.border(TokiChromeStroke.width, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    },
                ),
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
 * same glass layer as the capsule. Glass off （莫奈悬浮底栏）: no glass, no
 * stroke — the whole circle fills with the bar's border colour (primary)
 * and the icon turns onPrimary. Callers pass [solidFill] with the same
 * state as the bar's [FloatingNavBar.solidStroke].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TokiSideActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    glass: Modifier,
    modifier: Modifier = Modifier,
    solidFill: Boolean = false,
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
                    .then(if (solidFill) Modifier.background(MaterialTheme.colorScheme.primary) else glass)
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
                    tint = if (solidFill) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Cirro 导航项：纯图标，无文字标签。选中项落在浅灰紫 pill 上（Cirro 外观），
 * 图标为深色；未选中为细线图标。宽 64dp、高 56dp，图标 24dp。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NavItem(entry: NavBarEntry, selected: Boolean, onSelect: (Dest) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(entry.label)
    val cirro = LocalCirroAppearance.current
    val pillColor = if (cirro) CirroPill else scheme.primary
    val selectedTint = if (cirro) CirroPillIcon else scheme.onPrimary
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
                tint = if (selected) selectedTint else scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
