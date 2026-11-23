package com.tokimusic.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
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
 * [trailing]：标题行右侧的附加内容（tab 页的头像入口）；结构上贴右边缘——
 * 外层 Row 用 SpaceBetween 分左右两组，标题组贴左、trailing 组贴右，不再
 * 用 weight Spacer 把头像顶过去。
 * [titleAlpha]/[titleOffsetY]（第三轮第 5 项）：只作用在标题 chip 上（淡入 +
 * 上移动画），返回 chip 不参与——歌单详情页滑动时返回按钮一直显示。
 * [titleSolidColor]（第五轮第 25 项）：非空时标题 chip 在磨砂之下先铺一层
 * 不透明底色——详情页滚动后，封面渐变的上端色会透过半透明磨砂让顶栏
 * 看起来还留着封面；不透明底按页面底色（CirroBlack）盖住它。
 * 第六轮第 3 项根因：底色修饰符必须放在 graphicsLayer 之内（之后），
 * 否则 titleAlpha 的淡入淡出只作用于文字、底色永远不透明——进歌单时
 * （alpha 0）文字看不见、黑底整块留在顶栏，就是用户截图里的空黑框。
 * 修饰符顺序是本函数的关键不变量：clip → graphicsLayer → background。
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
    titleSolidColor: Color? = null,
    // 第六轮第 1 项：标题组最左侧的插槽（主页左上角源切换下拉入口）——
    // 只在主页双源都启用时传，放在返回 chip 之前，与标题 chip 同样 12dp 间距。
    leading: (@Composable () -> Unit)? = null,
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标题组贴左：返回 chip 与标题 chip 固定 12dp 间距；标题 chip 只取
        // 文字所需宽度（weight fill=false），长标题在组内截断。
        Row(
            Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(tokiSpace("md")))
            }
            if (onBack != null) {
                TokiScrollBackChip(backdropBlur, hazeState, onBack, strong = strong)
                Spacer(Modifier.width(tokiSpace("md")))
            }
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 56.dp)
                    .clip(titleShape)
                    // 第六轮第 3 项：底色在 graphicsLayer 之内——与文字一起淡入淡出、
                    // 一起上移；放外面时 alpha 管不到底色，进页会留一个空黑框。
                    .graphicsLayer { alpha = titleAlpha; translationY = titleOffsetY }
                    .let { if (titleSolidColor != null) it.background(titleSolidColor, titleShape) else it },
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
        }
        if (trailing != null) {
            // 头像组贴右：与标题组之间至少留 12dp，其余空间由 SpaceBetween 分配。
            Box(Modifier.padding(start = tokiSpace("md"))) {
                trailing()
            }
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
