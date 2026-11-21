package com.tokimusic.app.ui.player
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.lyric.YrcParser
import com.tokimusic.app.ui.theme.CoverColors
import com.tokimusic.app.ui.theme.InterFamily
import com.tokimusic.app.ui.theme.chromeBlurActive
import com.tokimusic.app.ui.theme.tokiGlassFade
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Immersive lyrics — LEFT-ALIGNED on purpose (TokiMusic spec, unlike centred
 * karaoke apps). Drawn over the player's cover-color gradient, so every line
 * is white with distance-based alpha: the active line sits anchored about
 * 28% down the viewport, lines further away fade out. Word-by-word: sung
 * characters are solid white, unsung are dimmed. Without YRC the whole line
 * highlights at once. Cirro: active line 28sp Bold, translation 16sp below it.
 *
 * 第二轮：
 * - 第 15 项：逐字高亮固定开启（无开关）；动画优化——位置轮询 200ms → 50ms，
 *   且只有当前行订阅位置（derivedStateOf 算当前行 + 行级 remember），
 *   不再每 tick 重组整个列表（思路参考 Local Player / Prism 等开源歌词
 *   项目的逐行刷新做法，未直接复制 GPL 代码）。
 * - 第 16 项：歌词与翻译全部 Bold（Inter + 思源黑体）。
 * - 第 17 项：顶部底部淡入淡出 32dp → 64dp，并加 Apple 歌词式模糊——
 *   列表上下各一段与底栏同配方的渐进磨砂 + 非当前行按距离轻微模糊
 *   （RenderEffect，当前行始终清晰）。
 * - 第 12 项：文字色跟随封面取色（亮封面深字、暗封面白字）。
 */
@Composable fun LyricsView(
    lines: List<LyricLine>,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
    coverColors: CoverColors? = null,
    hazeState: HazeState? = null,
    backdropBlur: Boolean = true,
    // 第三轮第 8 项：切页时首屏行 stagger 入场（只触发一次）。
    animateEntrance: Boolean = false,
    // 第三轮第 16 项：歌词字体预设（默认 InterFamily，与主题一致）。
    fontFamily: FontFamily = InterFamily,
) {
    val textColor = coverColors?.onCoverText() ?: Color.White
    val dimColor = coverColors?.onCoverTextDim() ?: Color.White.copy(alpha = 0.7f)
    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontFamily = fontFamily, color = dimColor)
        }
        return
    }
    // 逐字动画用 50ms 轮询；只有当前行读取位置状态，其他行不重组。
    val posState = remember { mutableLongStateOf(positionProvider()) }
    LaunchedEffect(Unit) { while (true) { posState.longValue = positionProvider(); delay(50) } }
    // 第三轮第 8 项：切页入场窗口只在切入后约 700ms 内打开；窗口期内
    // 首屏行逐行淡入 + 上移，窗口期后才滚入的行直接以最终态出现，
    // 滚动时不重复入场。
    var entranceWindow by remember { mutableStateOf(false) }
    LaunchedEffect(animateEntrance) {
        if (!animateEntrance) return@LaunchedEffect
        entranceWindow = true
        delay(700)
        entranceWindow = false
    }
    val active by remember(lines) { derivedStateOf { YrcParser.activeLineIndex(lines, posState.longValue) } }
    val listState = rememberLazyListState()
    var viewportH by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val anchorPx = (viewportH * 0.28f).toInt()
    LaunchedEffect(active, viewportH) {
        if (active >= 0 && viewportH > 0) listState.animateScrollToItem(active, scrollOffset = -anchorPx)
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().onSizeChanged { viewportH = it.height },
            contentPadding = PaddingValues(
                top = if (viewportH > 0) with(density) { anchorPx.toDp() } else 120.dp,
                bottom = if (viewportH > 0) with(density) { (viewportH * 0.6f).toInt().toDp() } else 240.dp,
            ),
        ) {
            itemsIndexed(lines) { i, line ->
                val isActive = i == active
                val distance = if (active < 0) i + 1 else abs(i - active)
                LyricLineRow(
                    line, isActive, distance, posState, showTranslation, textColor, dimColor,
                    fontFamily = fontFamily,
                    entranceWindow = entranceWindow,
                    // 以当前行为中心向外辐射，每行间隔 40ms（最多 6 行）。
                    entranceDelayMs = minOf(distance, 6) * 40L,
                )
            }
        }
        if (coverColors != null) {
            // 上下 64dp 淡出到封面渐变的上下端色；淡出层不处理触摸，不挡滚动。
            Box(
                Modifier.fillMaxWidth().height(64.dp).align(Alignment.TopCenter).background(
                    Brush.verticalGradient(listOf(coverColors.top, Color.Transparent)),
                ),
            )
            Box(
                Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, coverColors.bottom)),
                ),
            )
            // Apple 歌词式模糊：上下各一段渐进磨砂（与底栏同配方），行滚过
            // 顶底部时带磨砂感；玻璃关闭时不画。
            if (hazeState != null && chromeBlurActive(backdropBlur)) {
                Box(
                    Modifier.fillMaxWidth().height(64.dp).align(Alignment.TopCenter)
                        .tokiGlassFade(hazeState, fadeFromTop = true),
                )
                Box(
                    Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter)
                        .tokiGlassFade(hazeState, fadeFromTop = false),
                )
            }
        }
    }
}

/** 单行歌词：只有当前行订阅位置状态（50ms tick 只重组这一行）。 */
@Composable
private fun LyricLineRow(
    line: LyricLine,
    isActive: Boolean,
    distance: Int,
    posState: State<Long>,
    showTranslation: Boolean,
    textColor: Color,
    dimColor: Color,
    fontFamily: FontFamily = InterFamily,
    entranceWindow: Boolean = false,
    entranceDelayMs: Long = 0L,
) {
    val pos = if (isActive) posState.value else 0L
    val dim = lineAlpha(distance)
    val annotated = remember(line, isActive, pos, textColor, dim) {
        buildAnnotatedString {
            for (word in line.words) {
                when {
                    isActive -> {
                        val progress = YrcParser.wordProgress(word, pos)
                        val sungChars = (word.text.length * progress).toInt().coerceIn(0, word.text.length)
                        if (sungChars > 0) withStyle(SpanStyle(color = textColor)) { append(word.text.substring(0, sungChars)) }
                        if (sungChars < word.text.length) withStyle(SpanStyle(color = textColor.copy(alpha = 0.45f))) { append(word.text.substring(sungChars)) }
                    }
                    else -> withStyle(SpanStyle(color = textColor.copy(alpha = dim))) { append(word.text) }
                }
            }
        }
    }
    // 非当前行按距离轻微模糊，越远越糊；当前行始终清晰（第 17 项）。
    val blurPx = when (distance) {
        0 -> 0f
        1 -> 2f
        2 -> 4f
        3 -> 6f
        else -> 8f
    }
    // 第三轮第 8 项：切页入场——窗口期内组成的行从透明 + 24px 下移
    // 逐行淡入；窗口期后才滚入的行直接以最终态出现（不重复入场）。
    val enterAlpha = remember { Animatable(if (entranceWindow) 0f else 1f) }
    val enterOffsetY = remember { Animatable(if (entranceWindow) 24f else 0f) }
    LaunchedEffect(Unit) {
        if (!entranceWindow) return@LaunchedEffect
        delay(entranceDelayMs)
        launch { enterAlpha.animateTo(1f, tween(300)) }
        launch { enterOffsetY.animateTo(0f, tween(300)) }
    }
    Box(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                alpha = enterAlpha.value
                translationY = enterOffsetY.value
                renderEffect = if (blurPx > 0f) BlurEffect(blurPx, blurPx, TileMode.Decal) else null
            },
    ) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
            Text(
                annotated,
                textAlign = TextAlign.Start,
                style = if (isActive) MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp) else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
            if (showTranslation && !line.translation.isNullOrBlank()) Text(
                line.translation!!,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                color = if (isActive) dimColor else textColor.copy(alpha = (dim * 0.5f).coerceAtMost(0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
        }
    }
}

private fun lineAlpha(distance: Int): Float = when (distance) {
    0 -> 1f
    1 -> 0.6f
    2 -> 0.4f
    3 -> 0.28f
    else -> 0.18f
}
