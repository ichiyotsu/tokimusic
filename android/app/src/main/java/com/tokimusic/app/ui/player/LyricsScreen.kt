package com.tokimusic.app.ui.player
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.lyric.YrcParser
import com.tokimusic.app.ui.components.TokiLongPressLabel
import com.tokimusic.app.ui.components.bounceTranslation
import com.tokimusic.app.ui.components.rememberBounceOverscrollEffect
import com.tokimusic.app.ui.theme.CoverColors
import com.tokimusic.app.ui.theme.InterFamily
import com.tokimusic.app.ui.theme.TokiBlurCapability
import com.tokimusic.app.ui.theme.tokiGlassFade
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Immersive lyrics — LEFT-ALIGNED on purpose (TokiMusic spec, unlike centred
 * karaoke apps). Drawn over the player's cover-color gradient, so every line
 * is white with distance-based alpha: the active line sits anchored about
 * 28% down the viewport, lines further away fade out. Word-by-word: sung
 * characters are solid white, unsung are dimmed. Without YRC the whole line
 * highlights at once. TokiMusic: active line 28sp Bold, translation 16sp below it.
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
    // 第五轮第 26 项：罗马音显示（默认关；有 romalrc 的行才显示罗马音行，
    // 无数据不显示；罗马音行不参与逐字高亮，只跟当前行的亮暗走）。
    showRomaji: Boolean = false,
    // 0.1.12：卡拉 OK 逐字高亮开关（播放器歌词区右侧话筒按钮同步，
    // 与设置里的 karaokeHighlight 同源）。关时当前行整行实色，
    // 不逐字扫过（歌词逐字不存在时也整行实色，见 LyricLineRow）。
    karaokeHighlight: Boolean = true,
    coverColors: CoverColors? = null,
    hazeState: HazeState? = null,
    // 第三轮第 8 项：切页时首屏行 stagger 入场（只触发一次）。
    animateEntrance: Boolean = false,
    // 第三轮第 16 项：歌词字体预设（默认 InterFamily，与主题一致）。
    fontFamily: FontFamily = InterFamily,
    /**
     * 底部淡出层的不透明端色。默认取 coverColors.bottom；调用方应传入歌词区
     * 底部位置的真实背景色（coverColors.colorAt(f)），否则淡出端比身后背景
     * 深，会露出一道黑条（0.1.7 真机反馈）。
     */
    bottomFadeColor: Color? = null,
    // 第五轮第 6 项：点歌词行跳到该行开头（行级跳转）。
    onSeek: (Long) -> Unit = {},
    // 第五轮第 17 项：沉浸式——控制区已隐藏，歌词区撑满全屏；
    // “回到当前行”按钮一并弹到最底（底部边距 4dp → 16dp，做动画）。
    immersive: Boolean = false,
    // 0.1.13 第 3 项（发热）：暂停时逐字轮询降频用。
    isPlaying: Boolean = true,
    // 0.1.15 第 6 项：逐字时序偏移（毫秒）——逐字高亮与播放之间的时间
    // 偏移，正值延后、负值提前，0 = 跟随原时间轴。作用在行进度与当前
    // 行判定共用的位置上（activeLineIndex + lineProgress 同源）。
    timingOffsetMs: Int = 0,
) {
    val textColor = coverColors?.onCoverText() ?: Color.White
    val dimColor = coverColors?.onCoverTextDim() ?: Color.White.copy(alpha = 0.7f)
    // 0.1.18 第 6 项：本页动画统一走 MD3E motionScheme（空间类 spatial、
    // 颜色/透明度类 effects）。
    val scheme = MaterialTheme.motionScheme
    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontFamily = fontFamily, color = dimColor)
        }
        return
    }
    // 逐字动画用 50ms 轮询；只有当前行读取位置状态，其他行不重组。
    // 0.1.13 第 3 项（发热）：暂停时降到 1000ms（暂停态进度不动，
    // 轮询只是为了暂停中 seek 后同步一次）。
    val posState = remember { mutableLongStateOf(positionProvider()) }
    LaunchedEffect(isPlaying) {
        while (true) {
            posState.longValue = positionProvider()
            delay(if (isPlaying) 50 else 1000)
        }
    }
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
    // 0.1.15 第 6 项：偏移后的位置（行进度与当前行判定共用）——
    // timingOffsetMs 是普通参数，derivedStateOf 不会追踪它，remember
    // 键里带上它，滑杆一动就重建。
    val effPosState = remember(timingOffsetMs) { derivedStateOf { posState.longValue + timingOffsetMs } }
    val active by remember(lines) { derivedStateOf { YrcParser.activeLineIndex(lines, effPosState.value) } }
    val listState = rememberLazyListState()
    var viewportH by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // 第六轮第 16 项：歌词锚点 28% → 1/5（0.20f）；contentPadding 顶部跟随锚点（下不变）。
    val anchorPx = (viewportH * 0.20f).toInt()
    // 第六轮第 16 项：首行弹簧入场——首次播放 / 切歌时，首行从锚点下约
    // 120dp、透明起步，弹簧落到锚点（阻尼 0.75、刚度 400，约 400ms）；
    // 弹簧期间行进度保持 0、自动跟随暂停，落定后才开始扫过与跟随。
    // 只在 lines 变化（切歌）与首次组成时触发一次，后续行走原跟随滚动。
    val springOffsetY = remember { Animatable(0f) }
    val springAlpha = remember { Animatable(1f) }
    var springRunning by remember { mutableStateOf(false) }
    LaunchedEffect(lines) {
        springRunning = true
        springOffsetY.snapTo(with(density) { 120.dp.toPx() })
        springAlpha.snapTo(0f)
        launch { springOffsetY.animateTo(0f, scheme.defaultSpatialSpec()) }
        springAlpha.animateTo(1f, scheme.defaultEffectsSpec())
        springRunning = false
    }
    // 第六轮第 26 项：按屏上位置淡化——上下 64dp 渐隐带内的行模糊/
    // 变淡（平滑 0→8px / →18% alpha）。用 layoutInfo + derivedStateOf
    // 算每行的（模糊，alpha），量化后才变，减少滚动时的重组。
    // 0.1.21：中间净区不再强制清晰——非当前行另按与当前行的距离
    // 分级模糊（lyricDistanceBlurPx，在 LyricLineRow 里与这里的位置
    // 模糊取大），这才是用户要的 Apple Music 式距离模糊。
    val fadeBandPx = with(density) { 64.dp.toPx() }
    // 0.1.15 第 1 项：下半段淡出与上半段同强度——结构上上下带本就对称，
    // 但面板背景越往下越暗，白字在下半段对比度更高、看起来淡得弱；
    // 下带加宽到 96dp（行淡化 + 渐变层 + 磨砂层同宽），让下半段看起来
    // 与上半段一样淡。顶带保持 64dp。
    val bottomFadeBandPx = with(density) { 96.dp.toPx() }
    val rowFade by remember {
        derivedStateOf {
            val vh = viewportH
            if (vh <= 0) return@derivedStateOf emptyMap<Int, Pair<Float, Float>>()
            val map = mutableMapOf<Int, Pair<Float, Float>>()
            for (item in listState.layoutInfo.visibleItemsInfo) {
                val center = (item.offset + item.size / 2).toFloat()
                val topF = ((fadeBandPx - center) / fadeBandPx).coerceIn(0f, 1f)
                val bottomF = ((center - (vh - bottomFadeBandPx)) / bottomFadeBandPx).coerceIn(0f, 1f)
                val f = maxOf(topF, bottomF)
                val blur = ((f * 8f).roundToInt().coerceIn(0, 8) / 2 * 2).toFloat()
                val alpha = ((1f - f * 0.82f) * 10).roundToInt() / 10f
                map[item.index] = blur to alpha.coerceIn(0.18f, 1f)
            }
            map
        }
    }
    // 第五轮第 7 项（A）：用户手动拖动后暂停自动跟随，直到点“回到当前行”。
    // 只认手指拖动（collectIsDraggedAsState），程序滚动（animateScrollToItem）
    // 不会误触发——isScrollInProgress 在两者下都为真，不能用它区分。
    var userBrowsing by remember { mutableStateOf(false) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) { if (isDragged) userBrowsing = true }
    LaunchedEffect(active, viewportH, userBrowsing, springRunning) {
        if (active >= 0 && viewportH > 0 && !userBrowsing && !springRunning) {
            // 0.1.21 第 6 项：跟随滚动改平滑——当前行已在视口内时用
            // animateScrollBy 按像素差走 450ms 缓动（MD3 emphasized
            // 曲线），不再 animateScrollToItem 先跳后滚的硬切感；目标
            // 位置与原规则一致（当前行顶端落在 20% 锚点）。当前行不在
            // 视口内（切歌/大幅跳行）仍走 animateScrollToItem 直接定位。
            // 只在跟随态执行：userBrowsing 时本 effect 整体不跑（上）。
            val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active }
            if (visible != null) {
                val delta = (visible.offset - anchorPx).toFloat()
                if (delta != 0f) {
                    listState.animateScrollBy(delta, tween(450, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
                }
            } else {
                listState.animateScrollToItem(active, scrollOffset = -anchorPx)
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        // 0.1.21：列表回弹（官方 OverscrollEffect 自实现）。
        val bounce = rememberBounceOverscrollEffect()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().bounceTranslation(bounce).onSizeChanged { viewportH = it.height },
            overscrollEffect = bounce,
            contentPadding = PaddingValues(
                top = if (viewportH > 0) with(density) { anchorPx.toDp() } else 120.dp,
                bottom = if (viewportH > 0) with(density) { (viewportH * 0.6f).toInt().toDp() } else 240.dp,
            ),
        ) {
            itemsIndexed(lines) { i, line ->
                val isActive = i == active
                val distance = if (active < 0) i + 1 else abs(i - active)
                LyricLineRow(
                    line, isActive, distance, effPosState, showTranslation, showRomaji, textColor, dimColor,
                    fontFamily = fontFamily,
                    karaokeHighlight = karaokeHighlight,
                    // 0.1.15 第 1 项：已播放过的行（当前行之前）透明度 75%，
                    // 未播放的行保持 0.85，当前行 1.0。
                    isPlayed = active >= 0 && i < active,
                    entranceWindow = entranceWindow,
                    // 以当前行为中心向外辐射，每行间隔 40ms（最多 6 行）。
                    entranceDelayMs = minOf(distance, 6) * 40L,
                    // 第六轮第 26 项：位置淡化（行中心在渐隐带内才淡）。
                    fade = rowFade[i] ?: (0f to 1f),
                    // 第六轮第 16 项：弹簧期间当前行进度保持 0。
                    freezeProgress = springRunning,
                    // 首行才接弹簧入场（其余行只走 stagger 入场）。
                    springOffset = if (i == 0) springOffsetY else null,
                    springAlpha = if (i == 0) springAlpha else null,
                    onSeek = onSeek,
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
                Modifier.fillMaxWidth().height(96.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, bottomFadeColor ?: coverColors.bottom)),
                ),
            )
            // Apple 歌词式模糊：上下各一段渐进磨砂（与底栏同配方），行滚过
            // 顶底部时带磨砂感；玻璃关闭时不画。
            if (hazeState != null && TokiBlurCapability.canSampleInWindow) {
                Box(
                    Modifier.fillMaxWidth().height(64.dp).align(Alignment.TopCenter)
                        .tokiGlassFade(hazeState, fadeFromTop = true),
                )
                Box(
                    Modifier.fillMaxWidth().height(96.dp).align(Alignment.BottomCenter)
                        .tokiGlassFade(hazeState, fadeFromTop = false),
                )
            }
        }
        // 第五轮第 7 项：手动翻看后暂停跟随，右下角出“回到当前行”按钮；
        // 点它恢复跟随（userBrowsing=false 后上面的 LaunchedEffect 自会滚回去）。
        // 第五轮第 17 项：沉浸式下按钮弹到最底（16dp，跟歌词区一起撑满全屏）。
        // 第六轮第 17 项：文字 pill 改 48dp 圆形图标钮——当前行在可视区上方
        // 用上箭头、下方用下箭头；长按提示“回到当前行”。
        // 0.1.18 第 4 项：与更多按钮同一条竖线。根因：歌词区容器本身已有
        // horizontal 24dp 边距（PlayerScreen.kt LyricsView 的 modifier），
        // 按钮再 end 24dp，右边缘落在屏右 −48dp、中心在屏右 −72dp；而标题
        // 行更多钮在控制区容器（horizontal 24dp）内，右边缘屏右 −24dp、
        // 中心屏右 −48dp——两中心 x 差 24dp。修法：end 改 0dp，按钮右边缘
        // 贴歌词区右边缘（屏右 −24dp），中心屏右 −48dp，与更多钮同竖线；
        // 沉浸/非沉浸歌词区横向边距相同，x 对齐两者都成立（bottom 边距
        // 仍按沉浸 16dp / 非沉浸 4dp 各自取）。
        // 0.1.13 第 15 项：非沉浸底边距 80dp → 4dp——按钮 48dp 高 + 4dp
        // 底边距 + 控制区 8dp 顶边距 = 到 More 按钮 12dp，与左侧收藏钮
        // 到 More 的 12dp 间距对齐。
        if (userBrowsing && active >= 0) {
            val backToCurrentBottom by animateDpAsState(
                targetValue = if (immersive) 16.dp else 4.dp,
                animationSpec = scheme.defaultSpatialSpec(),
                label = "backToCurrentBottom",
            )
            val arrowUp by remember {
                derivedStateOf {
                    val vh = viewportH
                    val info = listState.layoutInfo.visibleItemsInfo
                    val item = info.firstOrNull { it.index == active }
                    if (item != null) {
                        (item.offset + item.size / 2) < vh / 2
                    } else if (info.isNotEmpty()) {
                        active < info.first().index
                    } else {
                        true
                    }
                }
            }
            // 0.1.15 第 9 项：对齐必须落在普通 Box 上——TokiLongPressLabel
            // （TooltipBox）会把调用方的 BoxScope.align 吃掉（外层锚 Box
            // 是 TopStart），按钮实际落在左上角；现在 align + padding
            // 放在作为歌词区直接子元素的普通 Box 上，TooltipBox 只包按钮本身。
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 0.dp, bottom = backToCurrentBottom),
            ) {
                TokiLongPressLabel("回到当前行") {
                    Box(
                        Modifier.size(48.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(textColor.copy(alpha = 0.14f))
                            .clickable { userBrowsing = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (arrowUp) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "回到当前行",
                            tint = textColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 点歌词跳转的目标时间：行首（行级跳转，第五轮第 6 项）。纯函数，JVM 单测覆盖。 */
fun lyricSeekTargetMs(line: LyricLine): Long = line.startMs

/** 一行歌词里的一个视觉行（折行后的每一段），字符偏移按原行文本计。 */
data class VisualLineSegment(val startChar: Int, val endChar: Int)

/**
 * 第六轮第 31 项：折行歌词按视觉行分段扫过——每段的进度只看该段已唱字符数
 * 占段长的比例（纯函数，JVM 单测覆盖）。sungChars 是整行已唱字符数
 * （lineProgress × 行总字符数）；不折行时只有一段，与整行扫过一致。
 */
/**
 * 歌词距离模糊（0.1.21，纯函数，单测覆盖）：以当前行为焦点，非当前
 * 行按行距分级模糊——每远一行 +3.5px、封顶 14px；当前行恒 0（清晰，
 * 逐字扫动不受影响）。这就是 Apple Music 式「未播放区域看不到模糊」
 * 的修复：旧实现只有上下边缘的位置淡化带，中间区域无论离当前行
 * 多远都强制清晰。
 */
internal fun lyricDistanceBlurPx(distance: Int): Float =
    if (distance <= 0) 0f else minOf(distance * 3.5f, 14f)

fun visualLineProgresses(sungChars: Float, segments: List<VisualLineSegment>): List<Float> =
    segments.map { seg ->
        val len = (seg.endChar - seg.startChar).coerceAtLeast(1)
        ((sungChars - seg.startChar) / len).coerceIn(0f, 1f)
    }

/**
 * 一段视觉行的进度（0.1.21 第 5 项，字数口径，纯函数）：段内每个字的
 * 进度（YrcParser.charProgresses）的平均——扫动边界按字走，不再把整行
 * 摊成一个字数比例。无像素量测时的回退口径。
 */
fun segmentCharProgress(charProgresses: List<Float>, segment: VisualLineSegment): Float {
    val len = (segment.endChar - segment.startChar).coerceAtLeast(1)
    var sum = 0f
    for (i in segment.startChar until segment.endChar) sum += charProgresses.getOrElse(i) { 0f }
    return (sum / len).coerceIn(0f, 1f)
}

/**
 * 一段视觉行的进度（0.1.21 第 5 项，像素口径，纯函数）：扫动边界 =
 * Σ（每字进度 × 该字像素宽）/ 段总宽。边界落在「正在唱的字」内部的
 * 正确比例处：快字宽窄都按自己的窗口走完，拖长音的字边界在字内
 * 缓慢推进——与字数口径的区别在宽窄悬殊的字混排时最明显。
 */
fun segmentPixelProgress(charProgresses: List<Float>, charWidthsPx: List<Float>, segment: VisualLineSegment): Float {
    var edge = 0f
    var total = 0f
    for (i in segment.startChar until segment.endChar) {
        val w = charWidthsPx.getOrElse(i) { 0f }
        edge += charProgresses.getOrElse(i) { 0f } * w
        total += w
    }
    return if (total <= 0f) segmentCharProgress(charProgresses, segment) else (edge / total).coerceIn(0f, 1f)
}

/** 单行歌词：只有当前行订阅位置状态（50ms tick 只重组这一行）。 */
@Composable
private fun LyricLineRow(
    line: LyricLine,
    isActive: Boolean,
    distance: Int,
    posState: State<Long>,
    showTranslation: Boolean,
    showRomaji: Boolean,
    textColor: Color,
    dimColor: Color,
    fontFamily: FontFamily = InterFamily,
    // 0.1.12：卡拉 OK 逐字高亮开关（关时当前行整行实色，不逐字扫过；
    // 见 progress 处的 1f 强制）。
    karaokeHighlight: Boolean = true,
    // 0.1.15 第 1 项：是否已播放过的行（当前行之前）——非当前行时
    // 透明度 0.75（未播放的行 0.85）；当前行不受影响。
    isPlayed: Boolean = false,
    entranceWindow: Boolean = false,
    entranceDelayMs: Long = 0L,
    // 第六轮第 26 项：按屏上位置的（模糊 px，alpha），中间净区 (0f, 1f)。
    fade: Pair<Float, Float> = 0f to 1f,
    // 第六轮第 16 项：弹簧入场期间，当前行进度保持 0。
    freezeProgress: Boolean = false,
    // 第六轮第 16 项：首行弹簧入场的位移 / 透明度（只传首行）。
    springOffset: Animatable<Float, AnimationVector1D>? = null,
    springAlpha: Animatable<Float, AnimationVector1D>? = null,
    onSeek: (Long) -> Unit = {},
) {
    val pos = if (isActive) posState.value else 0L
    val density = LocalDensity.current
    // 0.1.18 第 6 项：本行动画统一走 MD3E motionScheme。
    val scheme = MaterialTheme.motionScheme
    // 第六轮第 27 项：文本实宽（onTextLayout 量），可点区 = 实宽 + 8dp、
    // 贴向文本一侧；右侧空白不接点击，透传给父级手势（单击回封面、双击沉浸）。
    var textWidthPx by remember { mutableIntStateOf(0) }
    var rowHeightPx by remember { mutableIntStateOf(0) }
    // 第六轮第 26 项：位置淡化（fade）；0.1.21 起再叠加距离模糊——
    // 两者取大：边缘带内的行走位置模糊，中间区域的行按与当前行的
    // 距离模糊。距离模糊随焦点移动用 300ms tween 平滑过渡（不跳
    // 变），且只在组合层算（视口附近的行才进组合）；施加时用
    // TileMode.Clamp 避免 Decal 在行边缘切字。
    val distanceBlur by animateFloatAsState(
        targetValue = lyricDistanceBlurPx(distance),
        animationSpec = tween(300),
        label = "lyricDistanceBlur",
    )
    val blurPx = maxOf(fade.first, distanceBlur)
    val rowAlpha = fade.second
    // 0.1.13 第 6 项：当前行进出时的缩放过渡——当前行 1f、非当前行
    // 约 0.8f，走 spatial spec（0.1.18 第 6 项：之前手写 tween(300)），锚点左中。
    // 0.1.14 第 11 条：缩放只做视觉，行高（排版）两态必须一致——之前
    // 非当前行用 22sp、当前行 28sp，行一激活整行行高变化，下面的行
    // 全跟着上下跳（截图里的间距箭头）。现在两态都按 28sp 排版，
    // 视觉大小差只由 graphicsLayer 的 0.8 缩放给，行距不再变。
    val rowScale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.8f,
        animationSpec = scheme.defaultSpatialSpec(),
        label = "lyricRowScale",
    )
    // 第三轮第 8 项：切页入场——窗口期内组成的行从透明 + 24px 下移
    // 逐行淡入；窗口期后才滚入的行直接以最终态出现（不重复入场）。
    val enterAlpha = remember { Animatable(if (entranceWindow) 0f else 1f) }
    val enterOffsetY = remember { Animatable(if (entranceWindow) 24f else 0f) }
    LaunchedEffect(Unit) {
        if (!entranceWindow) return@LaunchedEffect
        delay(entranceDelayMs)
        // 0.1.18 第 6 项：透明度走 effects spec、位移走 spatial spec。
        launch { enterAlpha.animateTo(1f, scheme.defaultEffectsSpec()) }
        launch { enterOffsetY.animateTo(0f, scheme.defaultSpatialSpec()) }
    }
    Box(
        Modifier.fillMaxWidth()
            .onSizeChanged { rowHeightPx = it.height }
            .graphicsLayer {
                alpha = enterAlpha.value * (springAlpha?.value ?: 1f) * rowAlpha
                translationY = enterOffsetY.value + (springOffset?.value ?: 0f)
                scaleX = rowScale
                scaleY = rowScale
                transformOrigin = TransformOrigin(0f, 0.5f)
                renderEffect = if (blurPx > 0f) BlurEffect(blurPx, blurPx, if (distanceBlur > 0f) TileMode.Clamp else TileMode.Decal) else null
            },
    ) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
            if (isActive) {
                // 第五轮第 18 项：当前行双层逐字高亮——底层整行 45% 透明 +
                // 顶层实色，顶层用横向渐变 Brush 做连续扫过的遮罩（实色到
                // 边界，末端 2dp 渐变到透明），边界随词时间轴像素级流动，
                // 不再按字符取整硬切。选 Brush 而非 clip 裁切 + DstIn：
                // DstIn 会把底层 45% 的文字也在边界处吃掉，留一道透底的
                // 缝；Brush 只改顶层自身的填充，两层排版天然一致。非当
                // 前行仍是单层 dim 色（第 18 项只重做当前行）。
                val activeStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp)
                BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    val widthPx = with(density) { maxWidth.toPx() }
                    // 第六轮第 31 项：折行按视觉行分段扫过——用 TextMeasurer
                    // 按 28sp 样式量出每段的字符范围，每段独立算进度、独立
                    // 画双层；不折行时只有一段，与原来完全一致。翻译/罗马音
                    // 行不参与逐字高亮（不走这段）。
                    val measurer = rememberTextMeasurer()
                    // 0.1.15 第 6 项：逐字扫过宽度——之前 SungSegmentText 的
                    // 渐变按容器宽（BoxWithConstraints maxWidth）算，短行
                    // （如 6 字日文行约占容器一半）在进度约 0.5 时就看起来
                    // 扫完了（视觉上快约 2 倍）。现在每段用实测的文本宽
                    // （TextLayoutResult.getLineRight - getLineLeft）做渐变宽，扫过边界
                    // 落在文本末端；同一量测顺带给出各段字符范围。
                    // 0.1.21 第 5 项：量测结果一并留住（measured.second），
                    // 逐字像素宽从它取（见下面 progresses）。
                    val measured: Pair<List<Pair<VisualLineSegment, Float>>, TextLayoutResult?> = remember(line.text, widthPx, fontFamily) {
                        if (widthPx <= 0f || line.text.isEmpty()) {
                            listOf(VisualLineSegment(0, line.text.length) to widthPx) to null
                        } else {
                            val result = measurer.measure(
                                text = line.text,
                                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily),
                                constraints = Constraints(maxWidth = widthPx.toInt()),
                            )
                            // 中间结果显式定型：ifEmpty / let 的 R 在 remember 的 if 两分支间
                            // 推不出（R 与 C 无约束），直接用显式分支 + 显式类型代替。
                            val mapped: List<Pair<VisualLineSegment, Float>> = (0 until result.lineCount).map { k ->
                                VisualLineSegment(result.getLineStart(k), result.getLineEnd(k)) to (result.getLineRight(k) - result.getLineLeft(k))
                            }.filter { it.first.endChar > it.first.startChar }
                            (if (mapped.isEmpty()) listOf(VisualLineSegment(0, line.text.length) to widthPx) else mapped) to result
                        }
                    }
                    val segmentsWithWidths = measured.first
                    val segments = segmentsWithWidths.map { it.first }
                    val segmentWidths = segmentsWithWidths.map { it.second }
                    // 0.1.21 第 5 项：逐字进度——每个字的填充由它自己的
                    // 时间窗驱动（YrcParser.charProgresses：每词先过封顶、
                    // 多字词按字均分窗口），段进度 = Σ（字进度 × 字像素宽）
                    // / 段宽（segmentPixelProgress），扫动边界落在正在唱的
                    // 字内部的正确比例处；不再走 lineProgress 的「字数比例
                    // × 段宽」线性换算（宽窄字被同一比例摊平）。无量测时
                    // 退字数口径（segmentCharProgress）；LRC 行 charProgresses
                    // 为空，继续 lineProgress 行级回退，行为不变。
                    // 第六轮第 16 项：弹簧期间进度保持 0；0.1.12：话筒关时
                    // 整行实色——这两态仍走 visualLineProgresses 的 0/满格。
                    val progresses: List<Float> = when {
                        freezeProgress -> visualLineProgresses(0f, segments)
                        !karaokeHighlight -> visualLineProgresses(line.text.length.toFloat(), segments)
                        else -> {
                            val charProgs = YrcParser.charProgresses(line, pos)
                            val layout = measured.second
                            when {
                                charProgs.isEmpty() -> visualLineProgresses(YrcParser.lineProgress(line, pos) * line.text.length, segments)
                                layout == null -> segments.map { seg -> segmentCharProgress(charProgs, seg) }
                                else -> {
                                    val charWidthsPx = (0 until line.text.length).map { ci ->
                                        (layout.getHorizontalPosition(ci + 1, true) - layout.getHorizontalPosition(ci, true)).coerceAtLeast(0f)
                                    }
                                    segments.map { seg -> segmentPixelProgress(charProgs, charWidthsPx, seg) }
                                }
                            }
                        }
                    }
                    // 第 27 项的文本实宽取最长视觉行的宽。
                    val onSegmentLayout: (TextLayoutResult) -> Unit = { textWidthPx = maxOf(textWidthPx, it.size.width) }
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
                        segments.forEachIndexed { k, seg ->
                            SungSegmentText(
                                line.text.substring(seg.startChar, seg.endChar),
                                progresses[k], segmentWidths[k], activeStyle, fontFamily, textColor,
                                onTextLayout = onSegmentLayout,
                            )
                        }
                    }
                }
            } else {
                // 0.1.14 第 11 条：非当前行也按 28sp 排版（与当前行同
                // 高），视觉缩小只靠外层 graphicsLayer 的 0.8 缩放，
                // 这样行激活时行高不变，下面的行不跳。颜色仍是 dim 色。
                Text(
                    line.text,
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    // 第六轮第 26 项：中间净区非当前行固定 0.85（不再按距离变淡），
                    // 渐隐带内的变淡走整行的 rowAlpha。0.1.15 第 1 项：已播放
                    // 过的行 0.75（未播放的行 0.85）。
                    color = textColor.copy(alpha = if (isPlayed) 0.75f else 0.85f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    onTextLayout = { textWidthPx = it.size.width },
                )
            }
            // 第五轮第 26 项：罗马音行夹在原文与翻译之间（原文 → 罗马音 →
            // 翻译），与翻译行同款式（bodyLarge 粗体、同色同透明度），不参与逐字高亮；
            // 0.1.17 第 1 项：三行之间不另加间距——罗马音/翻译行不再带
            // 下边距，行与行之间只剩正常行高；开关罗马音/翻译时条件组合
            // 本来就会收掉/撑开那行，块间距只由主文本的 vertical padding 给。
            if (showRomaji && !line.romaji.isNullOrBlank()) Text(
                line.romaji!!,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                // 0.1.15 第 1 项：已播放行的翻译/罗马音同比例再淡（0.4 → 0.3，
                // 即整行透明度 ×0.75）。
                color = if (isActive) dimColor else textColor.copy(alpha = if (isPlayed) 0.3f else 0.4f),
                modifier = Modifier.fillMaxWidth(),
            )
            if (showTranslation && !line.translation.isNullOrBlank()) Text(
                line.translation!!,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                color = if (isActive) dimColor else textColor.copy(alpha = if (isPlayed) 0.3f else 0.4f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // 第六轮第 27 项：点歌词行跳到行首（行级跳转，第五轮第 6 项）——
        // 但只有文本区可点（实宽 + 8dp 容差）；右侧空白留给父级手势，
        // 不再整行接点击。无水波纹，避免破坏歌词排版。
        if (textWidthPx > 0 && rowHeightPx > 0) {
            Box(
                Modifier.align(Alignment.TopStart)
                    .width(with(density) { (textWidthPx + 8.dp.toPx()).toDp() })
                    .height(with(density) { rowHeightPx.toDp() })
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSeek(lyricSeekTargetMs(line)) },
            )
        }
    }
}

/**
 * 一段视觉行的双层逐字高亮（第六轮第 31 项）：底层整段 45% 透明 + 顶层实色，
 * 顶层用横向渐变 Brush 做连续扫过的遮罩（与原来整行的画法一致，只是按段
 * 独立算进度）。翻译/罗马音行不参与逐字高亮，不走这里。
 */
@Composable
private fun SungSegmentText(
    text: String,
    progress: Float,
    widthPx: Float,
    style: TextStyle,
    fontFamily: FontFamily,
    textColor: Color,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val density = LocalDensity.current
    // 0.1.15 第 6 项：渐变范围按文本实宽（调用方传入的 widthPx 已是
    // 该段的实测文本宽，不再是容器宽）——startX/endX 显式给出，
    // 扫过边界落在文本末端；宽 ≤0 时（未量到）全透明。
    val sungBrush = if (widthPx <= 0f) {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    } else {
        val fadePx = with(density) { 2.dp.toPx() }
        val w = (widthPx * progress).coerceIn(0f, widthPx)
        val fadeStart = ((w - fadePx).coerceAtLeast(0f) / widthPx).coerceIn(0f, 1f)
        val fadeEnd = (w / widthPx).coerceIn(0f, 1f)
        Brush.horizontalGradient(
            0f to textColor,
            fadeStart to textColor,
            fadeEnd to textColor.copy(alpha = 0f),
            1f to textColor.copy(alpha = 0f),
            startX = 0f,
            endX = widthPx,
        )
    }
    Box(Modifier.fillMaxWidth()) {
        Text(text, textAlign = TextAlign.Start, style = style, fontWeight = FontWeight.Bold, fontFamily = fontFamily, color = textColor.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth(), onTextLayout = onTextLayout)
        Text(text, textAlign = TextAlign.Start, style = style.copy(brush = sungBrush), fontWeight = FontWeight.Bold, fontFamily = fontFamily, modifier = Modifier.fillMaxWidth())
    }
}
