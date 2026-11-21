package com.tokimusic.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.pressScale
import com.tokimusic.app.ui.theme.TokiScrollChipBackdrop
import com.tokimusic.app.ui.theme.tokiSpace
import dev.chrisbanes.haze.HazeState

/**
 * The one title header for the whole app (TokiStudio 0.1.10).
 * Title uses one text role (headlineMedium Bold); the back key is a 56dp
 * round chip with a 24dp icon. Back chip and title carry the same frost (or
 * the solid fallback) BY DEFAULT — they no longer appear/disappear with
 * scrolling, and the back↔title gap is a fixed 12dp. Text stays onSurface.
 *
 * [strong]（第二轮第 3 项）：标题与底栏同配方——32dp 模糊 + 45% veil。
 * [trailing]：标题行右侧的附加内容（tab 页的头像入口）；歌单详情页不传。
 * [titleAlpha]/[titleOffsetY]（第三轮第 5 项）：只作用在标题 chip 上（淡入 +
 * 上移动画），返回 chip 不参与——歌单详情页滑动时返回按钮一直显示。
 */
@Composable
fun OverlayTitleHeader(
    title: String,
    onBack: (() -> Unit)?,
    onHeightMeasured: (Dp) -> Unit,
    backdropBlur: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    titleAlpha: Float = 1f,
    titleOffsetY: Float = 0f,
    trailing: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val content = MaterialTheme.colorScheme.onSurface
    val titleShape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightMeasured(with(density) { it.height.toDp() }) }
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(horizontal = tokiSpace("xl")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TokiScrollBackChip(backdropBlur, hazeState, onBack, strong = strong)
            Spacer(Modifier.width(tokiSpace("md")))
        }
        Box(
            Modifier
                .weight(1f, fill = false)
                .heightIn(min = 56.dp)
                .clip(titleShape)
                .graphicsLayer { alpha = titleAlpha; translationY = titleOffsetY },
            contentAlignment = Alignment.CenterStart,
        ) {
            TokiScrollChipBackdrop(
                shape = titleShape,
                backdropBlur = backdropBlur,
                hazeState = hazeState,
                strong = strong,
                modifier = Modifier.matchParentSize(),
            )
            // Round 3 item 20: the title text crossfades/slides up when the
            // top-bar title switches (Home sections: 主页 / 推荐歌单 / 推荐新歌).
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }) togetherWith
                        (fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 4 })
                },
                label = "headerTitle",
            ) { currentTitle ->
                Text(
                    currentTitle,
                    modifier = Modifier.padding(horizontal = tokiSpace("lg")),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            // Round 3 item 3: push the trailing (avatar) to the right edge.
            // The title Box is weight(1f, fill = false), so without this the
            // avatar hugs the title chip instead of the right edge.
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(tokiSpace("md")))
            trailing()
        }
    }
}

/** Back key: its own 56dp round chip, same frost as the title, always on. */
@Composable
fun TokiScrollBackChip(
    backdropBlur: Boolean,
    hazeState: HazeState,
    onClick: () -> Unit,
    strong: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val content = MaterialTheme.colorScheme.onSurface
    TokiLongPressLabel("返回") {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = ripple()) { onClick() }
                .pressScale(interaction),
            contentAlignment = Alignment.Center,
        ) {
            TokiScrollChipBackdrop(
                shape = CircleShape,
                backdropBlur = backdropBlur,
                hazeState = hazeState,
                strong = strong,
                modifier = Modifier.matchParentSize(),
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = content,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
