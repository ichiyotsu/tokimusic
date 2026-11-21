package com.tokimusic.app.ui.player

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.TokiScrollBackChip
import dev.chrisbanes.haze.HazeState
import com.tokimusic.app.ui.components.TokiLongPressLabel
import com.tokimusic.app.ui.components.formatDuration
import com.tokimusic.app.ui.pressScale
import com.tokimusic.app.ui.theme.CirroMiniPlayer
import com.tokimusic.app.ui.theme.CirroPlayKey
import com.tokimusic.app.ui.theme.CirroPlayKeyIcon
import com.tokimusic.app.ui.theme.LocalCirroAppearance
import com.tokimusic.app.ui.theme.rememberCoverColors
import kotlinx.coroutines.delay

/**
 * Cirro 迷你播放器：深灰胶囊（#2C2C2E），16dp 边距，小封面 + 歌名/歌手 +
 * 播放/下一曲两钮，底部 2dp 细进度线。悬浮在导航胶囊上方。
 */
@Composable
fun MiniPlayer(state: PlayerState, vm: AppViewModel, onOpen: () -> Unit) {
    val song = state.current ?: return
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.index, state.isPlaying) { while (true) { pos = vm.player.currentPosition(); delay(500) } }
    val playLabel = if (state.isPlaying) "暂停" else "播放"
    val cirro = LocalCirroAppearance.current
    val scheme = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onOpen),
        shape = if (cirro) RoundedCornerShape(percent = 50) else MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (cirro) CirroMiniPlayer else scheme.surfaceContainerLow,
        ),
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
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, onBack: () -> Unit, hazeState: HazeState, backdropBlur: Boolean) {
    val state by vm.player.state.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val likedIds by vm.likedIds.collectAsState()
    val song = state.current
    var pos by remember { mutableLongStateOf(0L) }
    var showLyrics by remember { mutableStateOf(false) }
    var karaoke by remember { mutableStateOf(vm.store.karaokeHighlight) }
    var showQueue by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(state.index) { song?.let { vm.loadLyrics(it.id) } }
    LaunchedEffect(state.isPlaying, state.index) { while (true) { pos = vm.player.currentPosition(); delay(200) } }
    if (song == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Text("暂无播放中的歌曲", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val playLabel = if (state.isPlaying) "暂停" else "播放"
    val liked = song.id in likedIds
    val scheme = MaterialTheme.motionScheme
    // Cirro：背景是封面取色的暖渐变（不是模糊封面 + 黑罩）。
    val coverColors = rememberCoverColors(song.id, song.picUrl)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(coverColors.toBrush()))
        // 0.1.10: every second-level page gets a floating back chip, even the
        // immersive player — 56dp glass circle, top-left over the cover.
        Box(
            Modifier.fillMaxSize().statusBarsPadding().padding(start = 24.dp, top = 4.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            TokiScrollBackChip(backdropBlur, hazeState, onClick = onBack)
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        (fadeIn(scheme.defaultEffectsSpec()) + scaleIn(scheme.defaultSpatialSpec(), 0.96f)) togetherWith
                            (fadeOut(scheme.defaultEffectsSpec()) + scaleOut(scheme.defaultSpatialSpec(), 0.96f))
                    },
                    label = "coverLyrics",
                    modifier = Modifier.fillMaxSize(),
                ) { lyricsMode ->
                    if (lyricsMode) {
                        Box(Modifier.fillMaxSize()) {
                            LyricsView(lyrics, pos, Modifier.fillMaxSize().padding(horizontal = 24.dp), karaoke = karaoke, showTranslation = vm.store.showTranslation, coverColors = coverColors)
                            Box(
                                Modifier.fillMaxSize().padding(end = 16.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                TokiLongPressLabel(if (karaoke) "关闭逐字高亮" else "开启逐字高亮") {
                                    CircleArtButton(Icons.Filled.Mic, if (karaoke) Color.White else Color.White.copy(alpha = 0.55f)) {
                                        karaoke = !karaoke
                                        vm.store.karaokeHighlight = karaoke
                                    }
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
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
                Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showLyrics) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(song.picUrl, song.name, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        SongTitleBlock(song, Modifier.weight(1f))
                        TitleActions(liked, { vm.toggleLike(song.id) }) { showMore = true }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SongTitleBlock(song, Modifier.weight(1f))
                        TitleActions(liked, { vm.toggleLike(song.id) }) { showMore = true }
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
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        interactionSource = sliderInteraction,
                        thumb = {
                            Box(
                                Modifier.size(width = 4.dp, height = 28.dp)
                                    .background(Color.White, RoundedCornerShape(2.dp)),
                            )
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatClock(pos), style = MaterialTheme.typography.labelLarge, color = Color.White)
                        Text(formatClock(song.durationMs), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.6f))
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TokiLongPressLabel("上一首") {
                        CircleArtButton(Icons.Filled.SkipPrevious, Color.White, size = 72.dp, iconSize = 36.dp) { vm.player.previous() }
                    }
                    Spacer(Modifier.width(28.dp))
                    TokiLongPressLabel(playLabel) {
                        val interaction = remember { MutableInteractionSource() }
                        val cirro = LocalCirroAppearance.current
                        Box(
                            Modifier.size(width = 128.dp, height = 84.dp)
                                .clip(RoundedCornerShape(28.dp))
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
                        CircleArtButton(Icons.Filled.SkipNext, Color.White, size = 72.dp, iconSize = 36.dp) { vm.player.next() }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TokiLongPressLabel(if (showLyrics) "返回封面" else "歌词") {
                        if (showLyrics) {
                            CircleArtButton(Icons.Filled.FormatQuote, Color.White) { showLyrics = false }
                        } else {
                            PlainArtButton(Icons.Filled.FormatQuote, "歌词") { showLyrics = true }
                        }
                    }
                    TokiLongPressLabel("投放") {
                        PlainArtButton(Icons.Filled.Cast, "投放") {
                            Toast.makeText(context, "投放功能尚未接入", Toast.LENGTH_SHORT).show()
                        }
                    }
                    TokiLongPressLabel("播放队列") {
                        PlainArtButton(Icons.Filled.QueueMusic, "播放队列") { showQueue = true }
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
private fun SongTitleBlock(song: Song, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(song.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artistText, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowScope.TitleActions(liked: Boolean, onToggleLike: () -> Unit, onMore: () -> Unit) {
    TokiLongPressLabel(if (liked) "取消喜欢" else "喜欢") {
        CircleArtButton(if (liked) Icons.Filled.Star else Icons.Filled.StarBorder, Color.White, onClick = onToggleLike)
    }
    Spacer(Modifier.width(12.dp))
    TokiLongPressLabel("更多") {
        CircleArtButton(Icons.Filled.MoreHoriz, Color.White, onClick = onMore)
    }
}

/** Round translucent button used over the artwork background. */
@Composable
private fun CircleArtButton(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp = 48.dp, iconSize: androidx.compose.ui.unit.Dp = 24.dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(interactionSource = interaction, indication = ripple()) { onClick() }
            .pressScale(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Plain icon button used over the artwork background (no circle). */
@Composable
private fun PlainArtButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(interactionSource = interaction, indication = ripple()) { onClick() }
            .pressScale(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(state: PlayerState, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("播放队列", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxHeight(0.6f)) {
            itemsIndexed(state.queue) { i, song ->
                Box(
                    Modifier.fillMaxWidth()
                        .background(if (i == state.index) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent),
                ) {
                    SongRow(song) { onJump(i); onDismiss() }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
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
