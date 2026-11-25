package com.tokimusic.app.ui.player

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SliderState
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.player.LiveLyricLine
import com.tokimusic.app.player.PlayMode
import com.tokimusic.app.player.PlayerState
import com.tokimusic.app.player.nextModeAfterTap
import com.tokimusic.app.player.queueRowState
import com.tokimusic.app.player.QueueRowState
import com.tokimusic.app.player.rememberCastActive
import com.tokimusic.app.player.showCastChooser
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.fadingEdges
import dev.chrisbanes.haze.HazeState
import com.tokimusic.app.ui.components.TokiLongPressLabel
import com.tokimusic.app.ui.components.formatDuration
import com.tokimusic.app.ui.pressRebound
import com.tokimusic.app.ui.pressScale
import com.tokimusic.app.ui.theme.TokiMiniPlayer
import com.tokimusic.app.ui.theme.TokiPlayKey
import com.tokimusic.app.ui.theme.TokiPlayKeyIcon
import com.tokimusic.app.ui.theme.CoverColors
import com.tokimusic.app.ui.theme.TokiScrollChipBackdrop
import com.tokimusic.app.ui.theme.TokiBlack
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * TokiMusic 迷你播放器：深灰胶囊（#2C2C2E），16dp 边距，小封面 + 歌名/歌手 +
 * 播放/下一曲两钮，底部 2dp 细进度线。悬浮在导航胶囊上方。
 *
 * 第二轮第 1 项：迷你播放器之前是纯色卡片、完全没有模糊，亮背景下显透。
 * 曾与导航胶囊同配方（加强档磨砂 32dp + 45% veil）。
 * 0.1.18 第 25 项：拆出自己的玻璃（调用方传 tokiContentGlassMiniPlayer）——
 * 85dp 强模糊、不上色（无 tint、无 veil），纯模糊背后内容；玻璃关闭时
 * 回退纯色 #2C2C2E（[glass] 只在 glassOn 时才被套用）。
 */
@Composable
fun MiniPlayer(state: PlayerState, vm: AppViewModel, onOpen: () -> Unit, glass: Modifier = Modifier, glassOn: Boolean = false, coverColors: CoverColors? = null) {
    val song = state.current ?: return
    var pos by remember { mutableLongStateOf(0L) }
    // 0.1.13 第 3 项（发热）：暂停时把 500ms 轮询降到 2000ms（暂停态
    // 进度不动，轮询只是为了暂停中 seek 后同步一次）。
    LaunchedEffect(state.index, state.isPlaying) {
        while (true) {
            pos = vm.player.currentPosition()
            delay(if (state.isPlaying) 500 else 2000)
        }
    }
    val playLabel = if (state.isPlaying) "暂停" else "播放"
    val scheme = MaterialTheme.colorScheme
    // 0.1.18 第 6 项：播放/暂停 icon 切换补淡入淡出 + 缩放（之前瞬切）。
    val motion = MaterialTheme.motionScheme
    val shape = RoundedCornerShape(percent = 50)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(shape)
            .then(if (glassOn) glass else Modifier.background(TokiMiniPlayer))
            .clickable(onClick = onOpen),
    ) {
        Column {
            Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 0.1.13 第 9 项：迷你封面 48dp → 56dp。
                // 0.1.18 第 7 项：封面圆角改回 0.1.16 的 14dp（0.1.17 第 5 项
                // 改成的 percent 50 近圆形撤回；外层 shape 本就是 0.1.16 的
                // percent 50，未动）；高度降 10%：封面 56dp → 50dp、Row
                // padding 10dp → 9dp，总高 78dp → 70dp（-10.3%）。
                // 0.1.18 第 19 项：播放钮 48dp → 56dp、图标 28dp → 32dp；
                // Row 纵向 padding 9dp → 6dp（横向仍 9dp），总高仍 70dp
                //（6+56+6+2）；下一首钮 offset x -6dp 左移（与播放钮间距
                // 10dp → 4dp 视觉），封面/歌名区不动。
                AsyncImage(song.picUrl, song.name, Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    // 0.1.17 第 5 项：歌手名颜色 #d9d9d9。
                    Text(song.artistText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD9D9D9))
                }
                // 第六轮第 32 项：迷你播放器的播放 / 下一曲钮也按压回弹（计划里
                // 写的“星标/更多”在 MiniPlayer 里不存在——这里只有播放与下一曲
                // 两个圆钮；星标/更多在播放器标题行，走 CircleArtButton）。
                TokiLongPressLabel(playLabel) {
                    val playInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(percent = 50))
                            .clickable(interactionSource = playInteraction, indication = null) { vm.player.toggle() }
                            .pressRebound(playInteraction),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = state.isPlaying,
                            transitionSpec = {
                                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.8f)) togetherWith
                                    (fadeOut(motion.defaultEffectsSpec()) + scaleOut(motion.defaultSpatialSpec(), targetScale = 0.8f))
                            },
                            label = "miniPlayIcon",
                        ) { playing ->
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = playLabel, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                TokiLongPressLabel("下一首") {
                    val nextInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier.size(48.dp).offset(x = (-6).dp).clip(RoundedCornerShape(percent = 50))
                            .clickable(interactionSource = nextInteraction, indication = null) { vm.player.next() }
                            .pressRebound(nextInteraction),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", modifier = Modifier.size(28.dp))
                    }
                }
            }
            // 第五轮第 9 项：细进度线用当前播放歌曲的封面取色（与播放器页
            // 滑杆同款逻辑）；没传取色时走默认主题色。
            // 0.1.19 第 2 项：改前进度条 fillMaxWidth 通栏——胶囊两端是
            // 半圆弧，条的两端伸进弧区、视觉上漏出胶囊轮廓（玻璃开时
            // 85dp 无色模糊下更明显）。改成横向内收：start 34dp = Row
            // 横向 padding 9dp + 封面 50dp 的一半，即 0% 正对封面竖直
            // 中线（也正好是胶囊左半圆的弧心 x≈35dp 处，条从胶囊全高
            // 位置起步）；end 9dp 与 Row 右侧 padding 一致，100% 停在
            // 内容内侧右缘。条高 2dp 与颜色不动，外层 Box 的 clip 兜底。
            LinearProgressIndicator(
                progress = { if (song.durationMs > 0) (pos.toFloat() / song.durationMs).coerceIn(0f, 1f) else 0f },
                Modifier.fillMaxWidth().padding(start = 34.dp, end = 9.dp).height(2.dp),
                color = coverColors?.onCoverText() ?: ProgressIndicatorDefaults.linearColor,
                trackColor = coverColors?.onCoverText()?.copy(alpha = 0.3f) ?: ProgressIndicatorDefaults.linearTrackColor,
            )
        }
    }
}

/**
 * Immersive full-screen player (a second-level page: no top bar, no bottom bar —
 * the app chrome hides while it is open). The top area flips between the large
 * cover and the full-screen lyrics; the control cluster below is shared.
 * Background is the cover-color warm gradient, not blurred artwork.
 *
 * 第二轮：圆角面板形态（面板在 TokiMusicApp 里包，顶部圆角 32dp、高度略低
 * 于全屏）；左上返回按钮已删，点空白区域返回（封面/歌词区与控制区有
 * 无操作点击屏蔽，不会误触）；逐字高亮固定开启，麦克风按钮已删；
 * 文字与按钮底色全部跟随封面取色。
 * 第三轮第 8 项：封面↔歌词切换按方向区分（进歌词：封面缩小淡出、歌词
 * 淡入上移；回封面反向），歌词行切页时 stagger 入场（只触发一次）。
 */
/**
 * 播放器上半区三视图（0.1.12 播放器升级）：封面 / 歌词 / 队列。
 * 第六轮第 29 项的歌词视图 / 队列可保存要求由它继承（rememberSaveable，
 * 枚举可直接保存）；队列以前是底部弹窗，现在是与封面、歌词平级的全屏视图。
 */
private enum class PlayerView { Cover, Lyrics, Queue }

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, onBack: () -> Unit, hazeState: HazeState, backdropBlur: Boolean, coverColors: CoverColors, onOpenArtist: (Long) -> Unit = {}, onOpenQqArtist: (String) -> Unit = {}) {
    val state by vm.player.state.collectAsState()
    val playMode by vm.player.playMode.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val likedIds by vm.likedIds.collectAsState()
    val song = state.current
    var pos by remember { mutableLongStateOf(0L) }
    // 0.1.12：上半区三视图（封面 / 歌词 / 队列），底部三钮切换；
    // 歌词视图 / 队列 / 更多 / 沉浸态可保存——从通知栏媒体卡片点进或
    // 进程重建后，回到播放器页时视图状态保留（第六轮第 29 项）。
    var playerView by rememberSaveable { mutableStateOf(PlayerView.Cover) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    // 第五轮第 17 项：沉浸式歌词——隐藏整个控制区、仅留歌词（双击进，
    // 见第 22 项）。仅在歌词视图有意义，切回封面或切歌时复位。
    var immersiveLyrics by rememberSaveable { mutableStateOf(false) }
    // 投放选中态：第 18 项，订阅 MediaRouter 回调（当前路由非默认时为真）。
    val castActive = rememberCastActive()
    val context = LocalContext.current
    LaunchedEffect(state.index) {
        song?.let { vm.loadLyrics(it) }
        // 切歌时沉浸式复位（视图不复位——第 21 项用户确认“切歌不用复位”）。
        immersiveLyrics = false
    }
    // 切回封面视图时沉浸式同步复位。
    LaunchedEffect(playerView) { if (playerView != PlayerView.Lyrics) immersiveLyrics = false }
    // 0.1.13 第 5 项：逐字高亮默认开（设置里的 karaokeHighlight），
    // 话筒开关按钮已删，不留本地 karaokeOn 状态。
    // 0.1.13 第 3 项（发热）：暂停时把 200ms 轮询降到 1000ms（暂停中
    // seek 后同步一次即可）。
    LaunchedEffect(state.isPlaying, state.index) {
        while (true) {
            pos = vm.player.currentPosition()
            delay(if (state.isPlaying) 200 else 1000)
        }
    }
    if (song == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Text("暂无播放中的歌曲", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        return
    }
    val playLabel = if (state.isPlaying) "暂停" else "播放"
    val liked = song.id in likedIds
    val scheme = MaterialTheme.motionScheme
    // 背景是封面取色的暖渐变（不是模糊封面 + 黑罩）。取色由
    // TokiMusicApp 传入（第三轮第 21 项：外层满屏 Box 共用同一份取色）。
    // 第 12 项：播放器页文字与按钮底色全部跟随封面取色。
    val onCover = coverColors.onCoverText()
    val onCoverDim = coverColors.onCoverTextDim()
    val buttonBg = coverColors.onCoverButtonBg()
    // 第 13 项：播放键圆角随播放态做动画——播放时全圆角胶囊，暂停时 28dp。
    val playCorner by animateDpAsState(
        targetValue = if (state.isPlaying) 42.dp else 28.dp,
        animationSpec = scheme.defaultSpatialSpec(),
        label = "playKeyCorner",
    )
    // 第五轮第 20(a) 项：封面圆角随视图切换插值（大封面 20dp ↔ 迷你 12dp，
    // 与视图切换同 spec；0.1.18 第 6 项：之前手写 tween(300)，改 spatial spec）。
    val coverCorner by animateDpAsState(
        targetValue = if (playerView == PlayerView.Lyrics) 12.dp else 20.dp,
        animationSpec = scheme.defaultSpatialSpec(),
        label = "coverCorner",
    )
    // 第五轮第 21 项：封面点按的独立按压源（与控制区 shield 分开，避免
    // 按播放键时封面也跟着缩放）。
    val coverPress = remember { MutableInteractionSource() }
    // 歌词区底部淡出端色（0.1.7 真机反馈的黑条）：面板渐变从面板顶画到底，
    // 淡出层却只到歌词区底部（约 60% 高），直接用 coverColors.bottom 会比
    // 身后背景深一截。量出面板高与歌词区高，取该位置的真实渐变色。
    // 布局量到之前先按 0.6 估算，首帧就接近正确值。
    val density = LocalDensity.current
    var panelHeightPx by remember { mutableIntStateOf(0) }
    var lyricsBottomFraction by remember { mutableFloatStateOf(0.6f) }
    val bottomFadeColor = coverColors.colorAt(lyricsBottomFraction)
    // 第 11 项：点空白区域返回。根布局接点击；封面/歌词区与控制区各放
    // 一个无操作点击屏蔽，点它们不返回（按钮/滑块/歌词滚动照常）。
    val blankBack = remember { MutableInteractionSource() }
    val shield = remember { MutableInteractionSource() }
    // 第六轮第 36 项：播放器页歌手名可点进歌手页；QQ 歌的歌手按 mid
    // 进 QQ 歌手页（0.1.15 第 2 项，之前是“暂不支持”提示）；QQ 歌
    // 没有歌手 mid 时仍提示（不进网易云页，id 不是同一空间）。
    val openPlayerArtist: (Long) -> Unit = { artistId ->
        if (song.source == MusicSource.QQ) {
            val mid = song.artists.firstOrNull { it.id == artistId }?.mid?.takeIf { it.isNotBlank() }
                ?: song.artists.firstOrNull()?.mid?.takeIf { it.isNotBlank() }
            if (mid != null) onOpenQqArtist(mid)
            else Toast.makeText(context, "QQ 歌手详情暂不支持", Toast.LENGTH_SHORT).show()
        } else {
            onOpenArtist(artistId)
        }
    }
    // 0.1.13 第 13 项：共享元素过渡已删——封面↔歌词切换时封面
    // 飞向左下迷你封面的共享元素飞行是闪烁的来源；改走显式淡入
    // 淡出 + 缩放（大封面退出淡出 + 缩到 0.94，迷你条淡入 + 从
    // 0.94 放大，见标题行的 AnimatedContent）。
    Box(
        Modifier.fillMaxSize()
            .clickable(interactionSource = blankBack, indication = null) { onBack() },
    ) {
        Box(Modifier.fillMaxSize().background(coverColors.toBrush()).onSizeChanged { panelHeightPx = it.height })
        Column(Modifier.fillMaxSize().padding(top = 8.dp).navigationBarsPadding()) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    // 0.1.12：左右滑动只切封面视图 ↔ 歌词视图（队列视图不参与
                    // 滑动，由底部三钮进出）。只认横滑（与歌词列表的竖滑不同
                    // 轴，不冲突）；点按仍走点击。滑动只做切换触发，不跟手——
                    // 松手后按方向切视图，走现有的 AnimatedContent 过渡；
                    // 播放器面板本身没有下滑关闭手势，无方向冲突。
                    .pointerInput(playerView) {
                        var totalX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalX = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalX += dragAmount },
                            onDragEnd = {
                                val threshold = with(density) { 60.dp.toPx() }
                                if (totalX < -threshold && playerView == PlayerView.Cover) playerView = PlayerView.Lyrics
                                else if (totalX > threshold && playerView == PlayerView.Lyrics) playerView = PlayerView.Cover
                            },
                        )
                    }
                    .onSizeChanged {
                    if (panelHeightPx > 0 && it.height > 0) {
                        lyricsBottomFraction =
                            ((with(density) { 8.dp.toPx() } + it.height) / panelHeightPx).coerceIn(0f, 1f)
                    }
                },
            ) {
                AnimatedContent(
                    targetState = playerView,
                    transitionSpec = {
                        // 0.1.13 第 13 项：共享元素已删——大封面退出时淡出 +
                        // 缩到 0.94（替代原来的共享元素飞行），回封面时淡入 +
                        // 从 0.94 放大；歌词/队列仍淡入上移。0.1.18 第 6 项：
                        // 缩放/位移走 spatial spec（之前手写 tween(300)）。
                        if (targetState == PlayerView.Cover) {
                            (fadeIn(scheme.defaultEffectsSpec()) + scaleIn(scheme.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                                (fadeOut(scheme.defaultEffectsSpec()) + slideOutVertically(scheme.defaultSpatialSpec()) { it / 12 })
                        } else if (initialState == PlayerView.Cover) {
                            (fadeIn(scheme.defaultEffectsSpec()) + slideInVertically(scheme.defaultSpatialSpec()) { it / 12 }) togetherWith
                                (fadeOut(scheme.defaultEffectsSpec()) + scaleOut(scheme.defaultSpatialSpec(), targetScale = 0.94f))
                        } else {
                            (fadeIn(scheme.defaultEffectsSpec()) + slideInVertically(scheme.defaultSpatialSpec()) { it / 12 }) togetherWith
                                fadeOut(scheme.defaultEffectsSpec())
                        }
                    },
                    label = "coverLyricsQueue",
                    modifier = Modifier.fillMaxSize(),
                ) { view ->
                    if (view == PlayerView.Lyrics) {
                        // 第五轮第 22 项：歌词区空白手势——单击回封面视图、
                        // 双击进/出沉浸式；沉浸式下单击逐级退回普通歌词视图
                        // （再单击才回封面）。行点击与“回到当前行”按钮是子级，
                        // 优先消费点击；滚动手势不被 detectTapGestures 消费。
                        // 固有代价：onTap 要等约 300ms 双击超时确认不是双击
                        // 才触发，单击回封面体感约慢半秒；若真机觉得拖沓，
                        // 退路是取消双击、改回单击进沉浸（第 17 项原方案）。
                        Box(
                            Modifier.fillMaxSize()
                                .pointerInput(immersiveLyrics) {
                                    detectTapGestures(
                                        onTap = {
                                            if (immersiveLyrics) immersiveLyrics = false
                                            else playerView = PlayerView.Cover
                                        },
                                        onDoubleTap = { immersiveLyrics = !immersiveLyrics },
                                    )
                                },
                        ) {
                            LyricsView(
                                lyrics,
                                positionProvider = { vm.player.currentPosition() },
                                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                showTranslation = vm.store.showTranslation,
                                // 第五轮第 26 项：罗马音显示（设置 → 歌词，默认关）。
                                showRomaji = vm.store.showRomaji,
                                // 0.1.13 第 5 项：逐字高亮默认开（设置里的
                                // karaokeHighlight），话筒开关按钮已删。
                                karaokeHighlight = vm.store.karaokeHighlight,
                                // 0.1.15 第 6 项：逐字时序偏移（更多页可调，
                                // -500..+500ms，0 = 跟随原时间轴）。
                                timingOffsetMs = vm.store.lyricTimingOffsetMs,
                                // 0.1.13 第 3 项：暂停时逐字轮询降频。
                                isPlaying = state.isPlaying,
                                coverColors = coverColors,
                                hazeState = hazeState,
                                backdropBlur = backdropBlur,
                                // 第五轮第 17 项：沉浸式下歌词区撑满，“回到当前行”
                                // 按钮一并弹到最底（LyricsView 内按 immersive 调整）。
                                immersive = immersiveLyrics,
                                // 第三轮第 8 项：切页时行 stagger 入场。
                                animateEntrance = true,
                                // 0.1.17 第 8 项：歌词字体功能删除——歌词默认
                                // Inter（LyricsView 的 fontFamily 默认值）。
                                // 底部淡出端色取歌词区底部位置的真实背景色（黑条修复）。
                                bottomFadeColor = bottomFadeColor,
                                // 第五轮第 6 项：点歌词行跳到该行开头；暂停时只
                                // 跳转、不自动播放（seekTo 在暂停态同样有效）。
                                onSeek = { vm.player.seekTo(it) },
                            )
                        }
                    } else if (view == PlayerView.Queue) {
                        // 0.1.12：队列是与封面、歌词平级的全屏视图（参考图
                        // 同款：顶部三个模式 pill + “继续播放” + 队列列表，
                        // 底部仍是迷你信息 + 进度条 + 播放控制 + 底部三钮）。
                        QueueView(
                            state = state,
                            playMode = playMode,
                            onModeTap = { vm.player.setPlayMode(nextModeAfterTap(playMode, it)) },
                            coverColors = coverColors,
                            onJump = { vm.player.jumpTo(it) },
                            onLongPressSong = { vm.openSongActions(it) },
                        )
                    } else {
                        // 第五轮第 21 项：点封面进歌词视图（轻微缩放反馈、无水波纹）。
                        Box(
                            Modifier.fillMaxSize().padding(horizontal = 24.dp)
                                .clickable(interactionSource = coverPress, indication = null) { playerView = PlayerView.Lyrics },
                            contentAlignment = Alignment.Center,
                        ) {
                            // 0.1.13 第 9 项：封面背后垫一块同尺寸深色底——
                            // 封面圆角抗锯齿边缘在浅色渐变上会透出一条白边，
                            // 深色底让边缘像素向深色过渡，白边消失（待真机验）。
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1f)
                                    .shadow(24.dp, RoundedCornerShape(coverCorner), clip = false)
                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(coverCorner))
                                    .clip(RoundedCornerShape(coverCorner))
                                    .pressScale(coverPress),
                            ) {
                                AsyncImage(
                                    song.picUrl,
                                    song.name,
                                    Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
            // 第五轮第 17 项：沉浸式歌词——整个控制区（标题行 + 进度条 +
            // 播放控制 + 底部三钮）淡出并纵向收起，歌词区 weight(1f)
            // 自动撑满全屏；再点空白恢复。fade + 纵向收放同走
            // motionScheme（只 fade 会在动画结束时尺寸突变跳一下）。
            AnimatedVisibility(
                visible = !immersiveLyrics,
                enter = fadeIn(scheme.defaultEffectsSpec()) + expandVertically(scheme.defaultSpatialSpec()),
                exit = fadeOut(scheme.defaultEffectsSpec()) + shrinkVertically(scheme.defaultSpatialSpec()),
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 8.dp),
            ) {
            Column(
                Modifier.clickable(interactionSource = shield, indication = null) {},
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            // 0.1.13 第 13 项：标题行（迷你条 / 大标题）用 AnimatedContent
            // 切换——迷你条淡入 + 从 0.94 放大（封面“缩到左下”的落点），
            // 大标题反向淡出 + 缩小；共享元素已删。
            AnimatedContent(
                targetState = playerView != PlayerView.Cover,
                transitionSpec = {
                    // 0.1.18 第 6 项：缩放走 spatial spec（之前手写 tween(300)）。
                    (fadeIn(scheme.defaultEffectsSpec()) + scaleIn(scheme.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                        (fadeOut(scheme.defaultEffectsSpec()) + scaleOut(scheme.defaultSpatialSpec(), targetScale = 0.94f))
                },
                label = "titleRow",
            ) { mini ->
                if (mini) {
                    // 0.1.12：歌词与队列视图下标题行收成迷你条（小封面 + 歌名/
                    // 歌手 + 喜欢/更多），封面视图用大标题块；参考图三视图同款。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 第五轮第 20(a) 项：迷你封面（现有位置，不新增元素）；
                        // 第 21 项：点它回封面视图。
                        AsyncImage(
                            song.picUrl,
                            song.name,
                            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { playerView = PlayerView.Cover },
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(12.dp))
                        SongTitleBlock(
                            song,
                            Modifier.weight(1f),
                            textColor = onCover, dimColor = onCoverDim,
                            onOpenArtist = openPlayerArtist,
                        )
                        TitleActions(liked, { vm.toggleLike(song.id) }, buttonTint = onCover, buttonBg = buttonBg) { showMore = true }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SongTitleBlock(
                            song,
                            Modifier.weight(1f),
                            textColor = onCover, dimColor = onCoverDim,
                            onOpenArtist = openPlayerArtist,
                        )
                        TitleActions(liked, { vm.toggleLike(song.id) }, buttonTint = onCover, buttonBg = buttonBg) { showMore = true }
                    }
                }
            }
                // 进度条：T 形滑块（竖线手柄穿过横线）。SliderState 版才有
                // thumb 插槽；拖拽中不同步播放进度，松手后恢复跟随。
                val sliderInteraction = remember { MutableInteractionSource() }
                val sliderDragging by sliderInteraction.collectIsDraggedAsState()
                val sliderState = remember(song.id, song.durationMs) {
                    SliderState(0f, 0, 0f..song.durationMs.coerceAtLeast(1).toFloat())
                }
                LaunchedEffect(pos) { if (!sliderDragging) sliderState.value = pos.toFloat() }
                Column {
                    Slider(
                        state = sliderState,
                        onValueChange = { vm.player.seekTo(it.toLong()); pos = it.toLong() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = onCover,
                            activeTrackColor = onCover,
                            inactiveTrackColor = onCover.copy(alpha = 0.3f),
                        ),
                        interactionSource = sliderInteraction,
                        thumb = {
                            Box(
                                Modifier.size(width = 4.dp, height = 28.dp)
                                    .background(onCover, RoundedCornerShape(2.dp)),
                            )
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatClock(pos), style = MaterialTheme.typography.labelLarge, color = onCover)
                        Text(formatClock(song.durationMs), style = MaterialTheme.typography.labelLarge, color = onCoverDim)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TokiLongPressLabel("上一首") {
                        CircleArtButton(Icons.Rounded.SkipPrevious, onCover, size = 72.dp, iconSize = 36.dp, bgColor = buttonBg) { vm.player.previous() }
                    }
                    Spacer(Modifier.width(28.dp))
                    TokiLongPressLabel(playLabel) {
                        val interaction = remember { MutableInteractionSource() }
                        Box(
                            Modifier.size(width = 128.dp, height = 84.dp)
                                .clip(RoundedCornerShape(playCorner))
                                .background(TokiPlayKey)
                                .clickable(interactionSource = interaction, indication = ripple()) { vm.player.toggle() }
                                .pressRebound(interaction),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 0.1.18 第 6 项：播放/暂停 icon 切换补淡入淡出 + 缩放（之前瞬切）。
                            AnimatedContent(
                                targetState = state.isPlaying,
                                transitionSpec = {
                                    (fadeIn(scheme.defaultEffectsSpec()) + scaleIn(scheme.defaultSpatialSpec(), initialScale = 0.8f)) togetherWith
                                        (fadeOut(scheme.defaultEffectsSpec()) + scaleOut(scheme.defaultSpatialSpec(), targetScale = 0.8f))
                                },
                                label = "playKeyIcon",
                            ) { playing ->
                                Icon(
                                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = playLabel,
                                    tint = TokiPlayKeyIcon,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(28.dp))
                    TokiLongPressLabel("下一首") {
                        CircleArtButton(Icons.Rounded.SkipNext, onCover, size = 72.dp, iconSize = 36.dp, bgColor = buttonBg) { vm.player.next() }
                    }
                }
                // 第六轮第 28 项：恢复歌词 + 队列两个底钮（投放留在 More）——
                // 在播放控制行下方，SpaceBetween、16dp 横向、6dp 纵向；
                // 歌词钮恒实心引号，长按提示随视图切“歌词/返回封面”；队列钮
                // 实心，点开队列；沉浸时随控制区一起淡出。
                // 0.1.11 修复轮：两个底钮去掉背景（只留图标，不动上一首/
                // 下一首和标题行按钮）；中间加声音钮，点一下调安卓原生
                // 音量面板（AudioManager FLAG_SHOW_UI；One UI 的面板自带
                // 媒体输出切换，可连蓝牙耳机），不自画音量条、不跳蓝牙设置页。
                // 0.1.13 第 14 项：底部三钮去掉选中态圆形底（0.1.12
                // 刚加的选中底去掉，三个钮永远只有图标，无背景）；
                // 声音钮点一下弹安卓原生音量面板（0.1.11 做法不变）。
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TokiLongPressLabel(if (playerView == PlayerView.Lyrics) "返回封面" else "歌词") {
                        CircleArtButton(
                            Icons.Rounded.FormatQuote, onCover,
                            bgColor = Color.Transparent,
                        ) { playerView = if (playerView == PlayerView.Lyrics) PlayerView.Cover else PlayerView.Lyrics }
                    }
                    TokiLongPressLabel("声音") {
                        CircleArtButton(Icons.Rounded.VolumeUp, onCover, bgColor = Color.Transparent) {
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                        }
                    }
                    TokiLongPressLabel("播放队列") {
                        CircleArtButton(
                            Icons.Rounded.QueueMusic, onCover,
                            bgColor = Color.Transparent,
                        ) { playerView = if (playerView == PlayerView.Queue) PlayerView.Cover else PlayerView.Queue }
                    }
                }
            }
            }
        }
    }
    if (showMore) MoreSheet(
        song = song,
        vm = vm,
        liked = liked,
        castActive = castActive,
        onToggleLike = { vm.toggleLike(song.id); showMore = false },
        onPlayNext = {
            vm.playNext(song)
            Toast.makeText(context, "已加入下一首播放", Toast.LENGTH_SHORT).show()
            showMore = false
        },
        onCast = {
            showMore = false
            // 第 18 项：打开安卓原生投放窗；选路后音频立即切换。
            // 第五轮第 4 项：打不开时 Toast 提示，不再闪退。
            val activity = context as? androidx.fragment.app.FragmentActivity
            if (activity != null) {
                if (!showCastChooser(activity)) Toast.makeText(context, "投放窗打开失败，请重试", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(context, "投放功能尚未接入", Toast.LENGTH_SHORT).show()
        },
        onDismiss = { showMore = false },
    )
}

// 第五轮第 20(b) 项：歌名 / 歌手过长自动轮播——歌名快（60.dp/s）、
// 歌手慢（30.dp/s，用户确认），初次延迟 1s、每轮间隔 3s；短文本不溢出
// 时自动静止；只改 SongTitleBlock（封面视图与歌词迷你条同时生效；
// MoreSheet 的标题行不动）。
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongTitleBlock(song: Song, modifier: Modifier = Modifier, textColor: Color = Color.White, dimColor: Color = Color.White.copy(alpha = 0.7f), onOpenArtist: ((Long) -> Unit)? = null) {
    // 0.1.14 第 10 条：边缘淡入淡出只在歌名真正溢出时加——之前无条件
    // 加，3 个字的短歌名（“我可以”）两端也被 24dp 渐隐吃掉，还叠加
    // fadingEdges 的黑块，看着突兀。basicMarquee 把文本按全宽量，
    // onTextLayout 的 size.width 就是歌名实宽，与容器实宽（外层
    // onSizeChanged）比，实宽更大才溢出；fadingEdges 本身不改布局，
    // 开关切换不会引起重排循环。
    var titleTextWidthPx by remember { mutableIntStateOf(0) }
    var titleBoxWidthPx by remember { mutableIntStateOf(0) }
    val titleOverflow = titleTextWidthPx > 0 && titleBoxWidthPx > 0 && titleTextWidthPx > titleBoxWidthPx
    // 0.1.18 第 16 项：实时歌词——每秒把当前歌词行替换歌名显示；无歌词 /
    // 纯音乐时回歌名。行来源复用 0.1.17 第 12 项的 LiveLyricLine（AppViewModel
    // 的 500ms 循环推），这里只做 1 秒 tick 采样，不另算行。SongTitleBlock
    // 是封面大标题与歌词/队列迷你条共用的标题组件，两处同走这一逻辑；
    // MiniPlayer 与 MoreSheet 的歌名不是这个组件，保持歌名不动。
    var lyricLine by remember(song.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(song.id) {
        while (true) {
            lyricLine = LiveLyricLine.current?.takeIf { it.isNotBlank() }
            delay(1000)
        }
    }
    val titleText = lyricLine ?: song.name
    Column(modifier) {
        Text(
            titleText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            onTextLayout = { titleTextWidthPx = it.size.width },
            // 0.1.13 第 16 项：歌名过长时左右边缘淡入淡出（轮播滚动到
            // 边缘时不硬切）。0.1.14 第 10 条：只在溢出时加。
            modifier = Modifier.onSizeChanged { titleBoxWidthPx = it.width }
                .basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 3000, initialDelayMillis = 1000, velocity = 60.dp)
                .then(if (titleOverflow) Modifier.fadingEdges(horizontal = true, edge = 24.dp) else Modifier),
        )
        // 第六轮第 36 项：歌手行可点进歌手页（多歌手时进第一个；点按进页、
        // 轮播滚动不触发——basicMarquee 只是视觉滚动，不接手势）。不传
        // onOpenArtist 时是普通文本。
        val artistModifier = if (onOpenArtist != null && song.artists.isNotEmpty()) {
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                song.artists.firstOrNull()?.let { onOpenArtist(it.id) }
            }
        } else {
            Modifier
        }
        Text(
            song.artistText,
            style = MaterialTheme.typography.titleMedium,
            color = dimColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 3000, initialDelayMillis = 1000, velocity = 30.dp).then(artistModifier),
        )
    }
}

@Composable
private fun RowScope.TitleActions(liked: Boolean, onToggleLike: () -> Unit, buttonTint: Color = Color.White, buttonBg: Color = Color.White.copy(alpha = 0.14f), onMore: () -> Unit) {
    TokiLongPressLabel(if (liked) "取消喜欢" else "喜欢") {
        CircleArtButton(if (liked) Icons.Rounded.Star else Icons.Rounded.StarBorder, buttonTint, bgColor = buttonBg, onClick = onToggleLike)
    }
    Spacer(Modifier.width(12.dp))
    TokiLongPressLabel("更多") {
        CircleArtButton(Icons.Rounded.MoreHoriz, buttonTint, bgColor = buttonBg, onClick = onMore)
    }
}

/** Round translucent button used over the artwork background. */
@Composable
private fun CircleArtButton(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp = 48.dp, iconSize: androidx.compose.ui.unit.Dp = 24.dp, bgColor: Color = Color.White.copy(alpha = 0.14f), onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(percent = 50))
            .background(bgColor)
            .clickable(interactionSource = interaction, indication = ripple()) { onClick() }
            // 第六轮第 32 项：按压回弹（上一首/下一首、标题行喜欢/更多、恢复的
            // 歌词/队列底钮都走这个共用钮；进度条拇指不动）。
            .pressRebound(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * 队列视图（0.1.12 播放器升级）：与封面、歌词平级的全屏视图（参考图
 * 同款）——顶部三个模式 pill（顺序 / 随机 / 倒序，同一时间只能选一个，
 * 点已激活的回顺序播放；0.1.18 第 10 项：列表循环 / 单曲循环从顶栏拿掉，
 * 枚举与 Media3 映射保留，入口见歌单页单按钮的图标映射）+ “继续播放” +
 * 来源副标题 + 队列列表（上下边缘淡入淡出）；底部仍是迷你信息 + 进度条 +
 * 播放控制 + 底部三钮（控制区在 PlayerScreen 里共用）。点行跳转后留在本视图。
 * 旧的 QueueSheet 底部弹窗已删。
 */
@Composable
private fun QueueView(
    state: PlayerState,
    playMode: PlayMode,
    onModeTap: (PlayMode) -> Unit,
    coverColors: CoverColors,
    onJump: (Int) -> Unit,
    onLongPressSong: (Song) -> Unit = {},
) {
    val onCover = coverColors.onCoverText()
    val onCoverDim = coverColors.onCoverTextDim()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        // 0.1.18 第 10 项：顶部三个模式 pill（顺序 / 随机 / 倒序）——
        // 点未激活的切到它，点已激活的回顺序播放（互斥单选由单一
        // playMode 状态保证，见 nextModeAfterTap）；倒序图标用 SwapVert
        //（上下互换，表顺序反向）。激活态白底 + 深色图标，未激活半透明底。
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModePill(PlayMode.Sequential, Icons.Rounded.FormatListNumbered, "顺序播放", playMode == PlayMode.Sequential, onCover, coverColors.bottom) { onModeTap(PlayMode.Sequential) }
            ModePill(PlayMode.Shuffle, Icons.Rounded.Shuffle, "随机播放", playMode == PlayMode.Shuffle, onCover, coverColors.bottom) { onModeTap(PlayMode.Shuffle) }
            ModePill(PlayMode.Reverse, Icons.Rounded.SwapVert, "倒序播放", playMode == PlayMode.Reverse, onCover, coverColors.bottom) { onModeTap(PlayMode.Reverse) }
        }
        Spacer(Modifier.height(16.dp))
        Text("继续播放", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = onCover)
        if (state.sourceName != null) {
            Text(
                "来自 ${state.sourceName}",
                style = MaterialTheme.typography.bodyMedium,
                color = onCoverDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        // 0.1.13 第 17 项：队列列表上下边缘淡入淡出。
        // 0.1.18 第 11 项：顶部淡出只在列表已滚动（有内容滚出顶部）时
        // 才生效——停在顶部时第一行完全不透明；底部淡出保持原样。
        val queueListState = rememberLazyListState()
        val queueScrolled = queueListState.firstVisibleItemIndex > 0 || queueListState.firstVisibleItemScrollOffset > 0
        LazyColumn(
            Modifier.weight(1f).fadingEdges(vertical = true, edge = 32.dp, top = queueScrolled),
            state = queueListState,
        ) {
            itemsIndexed(state.queue) { i, song ->
                QueueListRow(
                    song = song,
                    rowState = queueRowState(state.index, i, reverse = playMode == PlayMode.Reverse),
                    isPlaying = state.isPlaying,
                    onCover = onCover,
                    onCoverDim = onCoverDim,
                    highlightBg = coverColors.onCoverButtonBg(),
                    onClick = { onJump(i) },
                    onLongPress = { onLongPressSong(song) },
                )
            }
        }
    }
}

/** 队列顶栏的模式 pill：圆角胶囊，56dp 高，三等分（0.1.18 第 10 项：三个模式）。 */
@Composable
private fun RowScope.ModePill(
    mode: PlayMode,
    icon: ImageVector,
    label: String,
    active: Boolean,
    onCover: Color,
    activeIconColor: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    TokiLongPressLabel(label, Modifier.weight(1f)) {
        Box(
            Modifier.fillMaxWidth().height(56.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (active) onCover else onCover.copy(alpha = 0.22f))
                .clickable(interactionSource = interaction, indication = ripple()) { onClick() }
                .pressRebound(interaction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (active) activeIconColor else onCover, modifier = Modifier.size(28.dp))
        }
    }
}

/**
 * 队列列表行（0.1.12）：56dp 封面 + 歌名粗体 + 歌手名（参考图同款，
 * 不显示序号与时长）；正在播的行封面上有均衡器动画 + 圆角高亮，
 * 已播放的行变暗（沿用旧弹窗的 queueRowState 语义）。
 */
@Composable
private fun QueueListRow(
    song: Song,
    rowState: QueueRowState,
    isPlaying: Boolean,
    onCover: Color,
    onCoverDim: Color,
    highlightBg: Color,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val isCurrent = rowState == QueueRowState.Current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (isCurrent) highlightBg else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .alpha(if (rowState == QueueRowState.Played) 0.45f else 1f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))) {
            AsyncImage(song.picUrl, song.name, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            if (isCurrent) {
                Box(
                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) { EqualizerBars(active = isPlaying) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onCover, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistText, style = MaterialTheme.typography.bodySmall, color = onCoverDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 封面上的均衡器动画（0.1.12 队列视图）：4 根白条错峰跳动；暂停时静止
 * 在低位。纯视觉，无单测，真机验。
 */
@Composable
private fun EqualizerBars(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bar0 by transition.animateFloat(4f, 16f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "eq0")
    val bar1 by transition.animateFloat(4f, 14f, infiniteRepeatable(tween(640, delayMillis = 130), RepeatMode.Reverse), label = "eq1")
    val bar2 by transition.animateFloat(4f, 17f, infiniteRepeatable(tween(470, delayMillis = 260), RepeatMode.Reverse), label = "eq2")
    val bar3 by transition.animateFloat(4f, 13f, infiniteRepeatable(tween(580, delayMillis = 90), RepeatMode.Reverse), label = "eq3")
    val heights = listOf(bar0, bar1, bar2, bar3)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(18.dp),
    ) {
        heights.forEach { h ->
            Box(
                Modifier.width(3.dp).height((if (active) h else 4f).dp)
                    .background(Color.White, RoundedCornerShape(1.5.dp)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    song: Song,
    vm: AppViewModel,
    liked: Boolean,
    castActive: Boolean,
    onToggleLike: () -> Unit,
    onPlayNext: () -> Unit,
    // 第 18 项：打开安卓原生投放窗（调用方先关弹窗再执行）。
    onCast: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 第六轮第 25 项：与设置弹窗同款 skipPartiallyExpanded = true，内容
    // 加长时也不会先停在 50% 半展开锚点（内容短时无视觉变化）。
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(song.picUrl, song.name, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(song.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // 0.1.17 第 6 项：时长放歌手名后面（“歌手名 · 时长”），
                    // 专辑退出这行；独立的时长行删掉。时长为 0 时只有歌手名。
                    Text(
                        listOf(song.artistText, if (song.durationMs > 0) formatDuration(song.durationMs) else "").filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // 第六轮第 28 项：歌词 / 队列已恢复到底部按钮行，这里去重，只留
            // 投放 / 喜欢 / 下一首播放；投放图标沿用投放中实心 / 未投放空心
            // 的状态含义。
            MoreAction(if (castActive) Icons.Rounded.Cast else Icons.Outlined.Cast, "投放", onCast)
            MoreAction(if (liked) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (liked) "取消喜欢" else "喜欢", onToggleLike)
            MoreAction(Icons.Rounded.PlaylistAdd, "下一首播放", onPlayNext)
            // 0.1.15 第 5 项：歌词设置放进更多页（与设置 → 歌词同一开关，
            // 改完立即生效）；第 6 项：逐字时序偏移滑杆。
            // 0.1.17 第 1 项：“歌词”小标题去掉（开关本身保留）。
            Spacer(Modifier.height(12.dp))
            // 0.1.17 第 6 项：四个设定行各加一个 icon，与上面 MoreAction
            // 的行对齐（Icon + 16dp 间隔 + label）。
            MoreSwitch(Icons.Rounded.Translate, "翻译", vm.store.showTranslation) { vm.store.showTranslation = it }
            MoreSwitch(Icons.Rounded.TextFields, "罗马音", vm.store.showRomaji) { vm.store.showRomaji = it }
            MoreSwitch(Icons.Rounded.Highlight, "逐字高亮", vm.store.karaokeHighlight) { vm.store.karaokeHighlight = it }
            // 0.1.17 第 6 项：时序行也加 Timer icon，与三行对齐；纵向
            // padding 8dp → 4dp——switch→switch 的文字间隔是 4 + 4 + 4
            // = 12dp，switch→时序原来是 4 + 4 + 8 = 16dp，改后两段一致。
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Rounded.Timer, contentDescription = null)
                Text("逐字时序", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    formatTimingOffset(vm.store.lyricTimingOffsetMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = vm.store.lyricTimingOffsetMs.toFloat(),
                onValueChange = { vm.store.lyricTimingOffsetMs = ((it / 50f).roundToInt() * 50).coerceIn(-500, 500) },
                valueRange = -500f..500f,
                steps = 19,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Text(
                "正值让逐字高亮延后、负值提前；0 为跟随原时间轴。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** 歌词时序偏移的显示值（纯函数，JVM 单测覆盖）：0 显示“0ms”，其余带正负号。 */
fun formatTimingOffset(offsetMs: Int): String =
    if (offsetMs == 0) "0ms" else "${if (offsetMs > 0) "+" else ""}$offsetMs" + "ms"

@Composable
private fun MoreSwitch(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onCheckedChange(!checked) }.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MoreAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

private fun formatClock(ms: Long): String {
    val s = ms / 1000
    // 大排查 B7：显式 Locale.US——与 formatDuration / formatBytes
    // 同款（S11 同类），免得数字按系统语言变形成本地数字。
    return "%02d:%02d".format(java.util.Locale.US, s / 60, s % 60)
}
