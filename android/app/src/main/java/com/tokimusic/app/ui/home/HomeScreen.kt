package com.tokimusic.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.PlaylistCard
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.theme.CirroBlack
import com.tokimusic.app.ui.theme.tokiGlassFade

/**
 * Home, rebuilt from the reference: a large in-content title with the avatar at
 * the top right, a capsule search, tall recommendation cards （每日推荐 / 私人雷达）,
 * a recently-played album row, recommended playlists and new songs. Every
 * section is fed by the NetEase API service — no Demo data: login-gated
 * sections ask the user to sign in, and a dead service shows an error + retry.
 * The pinned overlay title is hidden on this page (see TokiMusicApp).
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
    hazeState: dev.chrisbanes.haze.HazeState,
    backdropBlur: Boolean,
    onOpenLogin: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    // Round 3 item 12: song-row artist names open the artist page.
    onOpenArtist: (Long) -> Unit = {},
    // Round 3 item 20: reports the section whose in-page title has scrolled
    // under the top bar ("主页" / "推荐歌单" / "推荐新歌"); TokiMusicApp swaps
    // the floating title chip to match.
    onSectionTitle: (String) -> Unit = {},
) {
    val home by vm.home.collectAsState()
    var entered by remember { mutableStateOf(false) }
    val enterAlpha by animateFloatAsState(if (entered) 1f else 0f, animationSpec = tween(300), label = "homeEnter")
    LaunchedEffect(Unit) { entered = true }
    // Round 3 item 20: track the two section titles' top edges; the last one
    // at or above the top bar's bottom edge owns the floating title.
    val density = LocalDensity.current
    var playlistsTitleY by remember { mutableStateOf(Float.MAX_VALUE) }
    var newSongsTitleY by remember { mutableStateOf(Float.MAX_VALUE) }
    val topBarBottomPx = with(density) { topPadding.toPx() }
    val activeSection by remember {
        derivedStateOf {
            when {
                newSongsTitleY <= topBarBottomPx -> "推荐新歌"
                playlistsTitleY <= topBarBottomPx -> "推荐歌单"
                else -> "主页"
            }
        }
    }
    LaunchedEffect(activeSection) { onSectionTitle(activeSection) }
    val hasContent = home.daily.isNotEmpty() || home.radar.isNotEmpty() || home.recent.isNotEmpty() ||
        home.playlists.isNotEmpty() || home.newSongs.isNotEmpty()
    // 第三轮第 14 项：列表上下边缘 32dp 淡出（内容滑到悬浮标题 / 底栏
    // 下方时柔和消失，与歌单详情同款；淡出层不处理触摸，不挡滚动）。
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().graphicsLayer { alpha = enterAlpha },
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 页内大标题已删（第二轮第 3 项）：标题走 TokiUI 悬浮模糊标题，
        // 头像入口在标题行右侧（见 TokiMusicApp）。
        if (home.loading && !hasContent) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }
        if (home.error && !hasContent) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("无法连接网易云", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "请检查网络后重试",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { vm.refreshHome() }, Modifier.height(56.dp)) { Text("重试") }
                    FilledTonalButton(onClick = onOpenLogin, Modifier.height(56.dp)) { Text("去登录") }
                }
            }
            return@LazyColumn
        }
        val showDaily = !home.loggedIn || home.daily.isNotEmpty()
        val showRadar = !home.loggedIn || home.radar.isNotEmpty()
        if (showDaily || showRadar) {
            item {
                // Cirro：每日推荐 / 私人雷达双列并排占满宽（不是横向滚动）。
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showDaily) {
                        RecommendCard(
                            title = "每日推荐",
                            songs = home.daily,
                            loggedIn = home.loggedIn,
                            onLogin = onOpenLogin,
                            onOpen = { onOpenPlaylist(-1L) },
                            hazeState = hazeState,
                            backdropBlur = backdropBlur,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (showRadar) {
                        RecommendCard(
                            title = "私人雷达",
                            songs = home.radar,
                            loggedIn = home.loggedIn,
                            onLogin = onOpenLogin,
                            onOpen = { onOpenPlaylist(-2L) },
                            hazeState = hazeState,
                            backdropBlur = backdropBlur,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!showDaily || !showRadar) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (!home.loggedIn || home.recent.isNotEmpty()) {
            item {
                Text("最近播放", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                if (!home.loggedIn) {
                    Card(
                        onClick = onOpenLogin,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Text(
                            "登录后查看最近播放",
                            Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(home.recent) { index, song ->
                            RecentCard(song) { vm.play(home.recent, index) }
                        }
                    }
                }
            }
        }
        if (home.playlists.isNotEmpty()) {
            item {
                Text(
                    "推荐歌单",
                    Modifier.padding(horizontal = 24.dp)
                        .onGloballyPositioned { playlistsTitleY = it.positionInRoot().y },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(home.playlists) { playlist ->
                        PlaylistCard(playlist) { onOpenPlaylist(playlist.id) }
                    }
                }
            }
        }
        if (home.newSongs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "推荐新歌",
                    Modifier.padding(horizontal = 24.dp)
                        .onGloballyPositioned { newSongsTitleY = it.positionInRoot().y },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(home.newSongs) { song ->
                SongRow(song, onOpenArtist = onOpenArtist) { vm.play(home.newSongs, home.newSongs.indexOf(song)) }
            }
        }
    }
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(listOf(CirroBlack, Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, CirroBlack)),
            ),
        )
    }
}

/** Tall recommendation card: cover art full-bleed with a scrim, label + artists at the bottom.
 *
 * 第二轮第 7 项：圆角 28dp → 20dp；标题与介绍文字读封面取色自适应
 * （亮封面深字、暗封面白字）；文字背景用淡出的模糊层（底部渐进磨砂，
 * 与底栏同配方），黑罩只留少量兜底；图片仍全幅铺满。
 */
@Composable
private fun RecommendCard(
    title: String,
    songs: List<Song>,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onOpen: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
    backdropBlur: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasCover = loggedIn && songs.isNotEmpty()
    val coverColors = if (hasCover) com.tokimusic.app.ui.theme.rememberCoverColors(songs.first().id, songs.first().picUrl)
        else null
    val textColor = coverColors?.onCoverText() ?: Color.White
    val dimColor = coverColors?.onCoverTextDim() ?: Color.White.copy(alpha = 0.8f)
    val blurOn = com.tokimusic.app.ui.theme.chromeBlurActive(backdropBlur)
    Card(
        onClick = if (loggedIn) onOpen else onLogin,
        modifier = modifier.fillMaxWidth().aspectRatio(200f / 260f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            if (hasCover) {
                AsyncImage(
                    songs.first().picUrl,
                    title,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                // 淡出的模糊层：只盖底部文字区，自下而上渐弱；玻璃关闭时不画。
                if (blurOn) {
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(132.dp)
                            .tokiGlassFade(hazeState, fadeFromTop = false),
                    )
                }
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            if (blurOn) listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.32f))
                            else listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.8f)),
                        ),
                    ),
                )
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = textColor)
                    Text(
                        artistLine(songs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = dimColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "登录后查看",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun artistLine(songs: List<Song>): String {
    val artists = songs.flatMap { it.artists }.map { it.name }.distinct()
    return if (artists.size > 4) artists.take(4).joinToString("，") + "等" else artists.joinToString("，")
}

@Composable
private fun RecentCard(song: Song, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).clickable(onClick = onClick)) {
        AsyncImage(
            song.picUrl,
            song.name,
            Modifier.size(112.dp).clip(MaterialTheme.shapes.extraLarge),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(8.dp))
        Text(song.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            song.artistText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
