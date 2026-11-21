package com.tokimusic.app.ui.player

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.player.PlayerState
import com.tokimusic.app.player.rememberCastActive
import com.tokimusic.app.player.showCastChooser
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SectionTitle
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.TokiSheetContainer
import dev.chrisbanes.haze.HazeState
import com.tokimusic.app.ui.components.TokiLongPressLabel
import com.tokimusic.app.ui.components.formatDuration
import com.tokimusic.app.ui.pressScale
import com.tokimusic.app.ui.theme.CirroMiniPlayer
import com.tokimusic.app.ui.theme.CirroPlayKey
import com.tokimusic.app.ui.theme.CirroPlayKeyIcon
import com.tokimusic.app.ui.theme.CoverColors
import com.tokimusic.app.ui.theme.LocalCirroAppearance
import com.tokimusic.app.ui.theme.lyricFontFamily
import kotlinx.coroutines.delay

/**
 * Cirro 迷你播放器：深灰胶囊（#2C2C2E），16dp 边距，小封面 + 歌名/歌手 +
 * 播放/下一曲两钮，底部 2dp 细进度线。悬浮在导航胶囊上方。
 *
 * 第二轮第 1 项：迷你播放器之前是纯色卡片、完全没有模糊，亮背景下显透。
 * 现在与导航胶囊同配方——加强档磨砂（32dp 模糊 + 45% veil）；玻璃关闭时
 * 回退纯色 #2C2C2E（Cirro）/ surfaceContainerLow。
 */
@Composable
fun MiniPlayer(state: PlayerState, vm: AppViewModel, onOpen: () -> Unit, glass: Modifier = Modifier, glassOn: Boolean = false) {
    val song = state.current ?: return
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.index, state.isPlaying) { while (true) { pos = vm.player.currentPosition(); delay(500) } }
    val playLabel = if (state.isPlaying) "暂停" else "播放"
    val cirro = LocalCirroAppearance.current
    val scheme = MaterialTheme.colorScheme
    val shape = if (cirro) RoundedCornerShape(percent = 50) else MaterialTheme.shapes.extraLarge
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(shape)
            .then(if (glassOn) glass else Modifier.background(if (cirro) CirroMiniPlayer else scheme.surfaceContainerLow))
            .clickable(onClick = onOpen),
    ) {
        Column {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(song.picUrl, song.name, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(song.artistText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TokiLongPressLabel(playLabel) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(percent = 50)).clickable { vm.player.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = playLabel)
                    }
                }
                TokiLongPressLabel("下一首") {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(percent = 50)).clickable { vm.player.next() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
                    }
                }
            }
            LinearProgressIndicator(
                progress = { if (song.durationMs > 0) (pos.toFloat() / song.durationMs).coerceIn(0f, 1f) else 0f },
                Modifier.fillMaxWidth().height(2.dp),
            )
        }
    }
}

/**
 * Immersive full-screen player (a second-level page: no top bar, no bottom bar —
 * the app chrome hides while it is open). The top area flips between the large
 * cover and the full-screen lyrics; the control cluster below is shared.
 * Background is the cover-color warm gradient (Cirro), not blurred artwork.
 *
 * 第二轮：圆角面板形态（面板在 TokiMusicApp 里包，顶部圆角 32dp、高度略低
 * 于全屏）；左上返回按钮已删，点空白区域返回（封面/歌词区与控制区有
 * 无操作点击屏蔽，不会误触）；逐字高亮固定开启，麦克风按钮已删；
 * 文字与按钮底色全部跟随封面取色。
 * 第三轮第 8 项：封面↔歌词切换按方向区分（进歌词：封面缩小淡出、歌词
 * 淡入上移；回封面反向），歌词行切页时 stagger 入场（只触发一次）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, onBack: () -> Unit, hazeState: HazeState, backdropBlur: Boolean, coverColors: CoverColors) {
    val state by vm.player.state.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val likedIds by vm.likedIds.collectAsState()
    val song = state.current
    var pos by remember { mutableLongStateOf(0L) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    // 投放选中态：第 18 项，订阅 MediaRouter 回调（当前路由非默认时为真）。
    val castActive = rememberCastActive()
    val context = LocalContext.current
    LaunchedEffect(state.index) { song?.let { vm.loadLyrics(it.id) } }
    LaunchedEffect(state.isPlaying, state.index) { while (true) { pos = vm.player.currentPosition(); delay(200) } }
    if (song == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Text("暂无播放中的歌曲", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        return
    }
    val playLabel = if (state.isPlaying) "暂停" else "播放"
    val liked = song.id in likedIds
    val scheme = MaterialTheme.motionScheme
    // Cirro：背景是封面取色的暖渐变（不是模糊封面 + 黑罩）。取色由
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
    // 第 11 项：点空白区域返回。根布局接点击；封面/歌词区与控制区各放
    // 一个无操作点击屏蔽，点它们不返回（按钮/滑块/歌词滚动照常）。
    val blankBack = remember { MutableInteractionSource() }
    val shield = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize()
            .clickable(interactionSource = blankBack, indication = null) { onBack() },
    ) {
        Box(Modifier.fillMaxSize().background(coverColors.toBrush()))
        Column(Modifier.fillMaxSize().padding(top = 8.dp).navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        // 第三轮第 8 项：按方向区分——进歌词时封面缩小淡出、
                        // 歌词淡入上移；回封面时反向。淡入淡出/缩放走
                        // motionScheme 的 expressive spec，位移 300ms
                        //（与第 5 项标题动画同量级）。
                        if (targetState) {
                            (fadeIn(scheme.defaultEffectsSpec()) + slideInVertically(tween(300)) { it / 12 }) togetherWith
                                (fadeOut(scheme.defaultEffectsSpec()) + scaleOut(scheme.defaultSpatialSpec(), 0.96f))
                        } else {
                            (fadeIn(scheme.defaultEffectsSpec()) + scaleIn(scheme.defaultSpatialSpec(), 0.96f)) togetherWith
                                (fadeOut(scheme.defaultEffectsSpec()) + slideOutVertically(tween(300)) { it / 12 })
                        }
                    },
                    label = "coverLyrics",
                    modifier = Modifier.fillMaxSize(),
                ) { lyricsMode ->
                    if (lyricsMode) {
                        Box(
                            Modifier.fillMaxSize()
                                .clickable(interactionSource = shield, indication = null) {},
                        ) {
                            LyricsView(
                                lyrics,
                                positionProvider = { vm.player.currentPosition() },
                                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                showTranslation = vm.store.showTranslation,
                                coverColors = coverColors,
                                hazeState = hazeState,
                                backdropBlur = backdropBlur,
                                // 第三轮第 8 项：切页时行 stagger 入场。
                                animateEntrance = true,
                                // 第三轮第 16 项：歌词字体预设（设置 → 歌词）。
                                fontFamily = lyricFontFamily(vm.store.lyricFontPreset),
                            )
                        }
                    } else {
                        Box(
                            Modifier.fillMaxSize().padding(horizontal = 24.dp)
                                .clickable(interactionSource = shield, indication = null) {},
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                song.picUrl,
                                song.name,
                                Modifier.fillMaxWidth().aspectRatio(1f)
                                    .shadow(24.dp, RoundedCornerShape(20.dp), clip = false)
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 8.dp)
                    .clickable(interactionSource = shield, indication = null) {},
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showLyrics) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(song.picUrl, song.name, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        SongTitleBlock(song, Modifier.weight(1f), textColor = onCover, dimColor = onCoverDim)
                        TitleActions(liked, { vm.toggleLike(song.id) }, buttonTint = onCover, buttonBg = buttonBg) { showMore = true }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SongTitleBlock(song, Modifier.weight(1f), textColor = onCover, dimColor = onCoverDim)
                        TitleActions(liked, { vm.toggleLike(song.id) }, buttonTint = onCover, buttonBg = buttonBg) { showMore = true }
                    }
                }
                // Cirro 进度条：T 形滑块（竖线手柄穿过横线）。SliderState 版才有
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
                        CircleArtButton(Icons.Filled.SkipPrevious, onCover, size = 72.dp, iconSize = 36.dp, bgColor = buttonBg) { vm.player.previous() }
                    }
                    Spacer(Modifier.width(28.dp))
                    TokiLongPressLabel(playLabel) {
                        val interaction = remember { MutableInteractionSource() }
                        val cirro = LocalCirroAppearance.current
                        Box(
                            Modifier.size(width = 128.dp, height = 84.dp)
                                .clip(RoundedCornerShape(playCorner))
                                .background(if (cirro) CirroPlayKey else Color.White)
                                .clickable(interactionSource = interaction, indication = ripple()) { vm.player.toggle() }
                                .pressScale(interaction),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = playLabel,
                                tint = if (cirro) CirroPlayKeyIcon else Color.Black,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(28.dp))
                    TokiLongPressLabel("下一首") {
                        CircleArtButton(Icons.Filled.SkipNext, onCover, size = 72.dp, iconSize = 36.dp, bgColor = buttonBg) { vm.player.next() }
                    }
                }
                // 第 14 项：底部三钮各备空心/实心两套图标，选中态用实心。
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TokiLongPressLabel(if (showLyrics) "返回封面" else "歌词") {
                        PlainArtButton(if (showLyrics) Icons.Filled.FormatQuote else Icons.Outlined.FormatQuote, "歌词", tint = onCover) { showLyrics = !showLyrics }
                    }
                    TokiLongPressLabel("投放") {
                        PlainArtButton(if (castActive) Icons.Filled.Cast else Icons.Outlined.Cast, "投放", tint = onCover) {
                            // 第 18 项：打开安卓原生投放窗；选路后音频立即切换。
                            val activity = context as? androidx.fragment.app.FragmentActivity
                            if (activity != null) showCastChooser(activity)
                            else Toast.makeText(context, "投放功能尚未接入", Toast.LENGTH_SHORT).show()
                        }
                    }
                    TokiLongPressLabel("播放队列") {
                        PlainArtButton(if (showQueue) Icons.Filled.QueueMusic else Icons.Outlined.QueueMusic, "播放队列", tint = onCover) { showQueue = true }
                    }
                }
            }
        }
    }
    if (showQueue) QueueSheet(state, onJump = { vm.player.jumpTo(it) }, onDismiss = { showQueue = false })
    if (showMore) MoreSheet(
        song = song,
        liked = liked,
        onToggleLike = { vm.toggleLike(song.id); showMore = false },
        onPlayNext = {
            vm.playNext(song)
            Toast.makeText(context, "已加入下一首播放", Toast.LENGTH_SHORT).show()
            showMore = false
        },
        onDismiss = { showMore = false },
    )
}

@Composable
private fun SongTitleBlock(song: Song, modifier: Modifier = Modifier, textColor: Color = Color.White, dimColor: Color = Color.White.copy(alpha = 0.7f)) {
    Column(modifier) {
        Text(song.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artistText, style = MaterialTheme.typography.titleMedium, color = dimColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowScope.TitleActions(liked: Boolean, onToggleLike: () -> Unit, buttonTint: Color = Color.White, buttonBg: Color = Color.White.copy(alpha = 0.14f), onMore: () -> Unit) {
    TokiLongPressLabel(if (liked) "取消喜欢" else "喜欢") {
        CircleArtButton(if (liked) Icons.Filled.Star else Icons.Filled.StarBorder, buttonTint, bgColor = buttonBg, onClick = onToggleLike)
    }
    Spacer(Modifier.width(12.dp))
    TokiLongPressLabel("更多") {
        CircleArtButton(Icons.Filled.MoreHoriz, buttonTint, bgColor = buttonBg, onClick = onMore)
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
            .pressScale(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Plain icon button used over the artwork background (no circle). */
@Composable
private fun PlainArtButton(icon: ImageVector, label: String, tint: Color = Color.White.copy(alpha = 0.85f), onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(interactionSource = interaction, indication = ripple()) { onClick() }
            .pressScale(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
    }
}

/**
 * 播放队列弹窗（第二轮第 19 项，TokiUI 设计）：TokiSheetContainer 配方
 * （顶部圆角 32dp、surfaceContainerLow、标题 + 36dp 关闭钮），
 * SectionTitle 分段——正在播放 / 接下来，当前曲用 primaryContainer
 * 35% 高亮。点行跳转并关闭弹窗。
 */
@Composable
private fun QueueSheet(state: PlayerState, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    TokiSheetContainer(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "播放队列",
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
            // 第三轮第 14 项：队列弹窗内容区上下边缘 24dp 淡出（淡出色取
            // 弹窗底色 surfaceContainerLow；淡出层不处理触摸，不挡滚动）。
            val sheetBg = MaterialTheme.colorScheme.surfaceContainerLow
            Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (state.queue.isNotEmpty() && state.index in state.queue.indices) {
                    item { SectionTitle("正在播放") }
                    item {
                        Box(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        ) {
                            SongRow(state.queue[state.index]) { onJump(state.index); onDismiss() }
                        }
                    }
                    if (state.index + 1 < state.queue.size) {
                        item { SectionTitle("接下来") }
                        itemsIndexed(state.queue) { i, song ->
                            if (i > state.index) SongRow(song) { onJump(i); onDismiss() }
                        }
                    }
                } else {
                    itemsIndexed(state.queue) { i, song -> SongRow(song) { onJump(i); onDismiss() } }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(24.dp).align(Alignment.TopCenter).background(
                    Brush.verticalGradient(listOf(sheetBg, Color.Transparent)),
                ),
            )
            Box(
                Modifier.fillMaxWidth().height(24.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, sheetBg)),
                ),
            )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(song: Song, liked: Boolean, onToggleLike: () -> Unit, onPlayNext: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(song.picUrl, song.name, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(song.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(song.artistText, song.albumName).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            MoreAction(if (liked) Icons.Filled.Star else Icons.Filled.StarBorder, if (liked) "取消喜欢" else "喜欢", onToggleLike)
            MoreAction(Icons.Filled.PlaylistAdd, "下一首播放", onPlayNext)
            if (song.durationMs > 0) {
                Text(
                    "时长 ${formatDuration(song.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
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
    return "%02d:%02d".format(s / 60, s % 60)
}
