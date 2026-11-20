package com.tokimusic.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.PlaylistCard
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.TokiLongPressLabel
import kotlinx.coroutines.delay

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
    onOpenLogin: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val home by vm.home.collectAsState()
    val profile by vm.profile.collectAsState()
    var query by remember { mutableStateOf("") }
    // Debounced: one request per pause in typing, not one per keystroke.
    LaunchedEffect(query) { delay(400); vm.search(query) }
    var entered by remember { mutableStateOf(false) }
    val enterAlpha by animateFloatAsState(if (entered) 1f else 0f, animationSpec = tween(300), label = "homeEnter")
    LaunchedEffect(Unit) { entered = true }
    val hasContent = home.daily.isNotEmpty() || home.radar.isNotEmpty() || home.recent.isNotEmpty() ||
        home.playlists.isNotEmpty() || home.newSongs.isNotEmpty()
    LazyColumn(
        Modifier.fillMaxSize().graphicsLayer { alpha = enterAlpha },
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("主页", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TokiLongPressLabel(if (profile == null) "登录" else "我的") {
                    IconButton(onClick = onOpenLogin) {
                        Avatar(profile?.avatarUrl ?: "", 40)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                placeholder = { Text("搜索歌曲、歌手、歌单") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(percent = 50),
            )
        }
        if (home.searching) {
            item { Text("搜索结果", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge) }
            items(home.searchResults) { song ->
                SongRow(song) { vm.play(home.searchResults, home.searchResults.indexOf(song)) }
            }
            return@LazyColumn
        }
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
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showDaily) {
                        RecommendCard(
                            title = "每日推荐",
                            songs = home.daily,
                            loggedIn = home.loggedIn,
                            onLogin = onOpenLogin,
                            onPlay = { vm.play(home.daily, 0) },
                        )
                    }
                    if (showRadar) {
                        RecommendCard(
                            title = "私人雷达",
                            songs = home.radar,
                            loggedIn = home.loggedIn,
                            onLogin = onOpenLogin,
                            onPlay = { vm.play(home.radar, 0) },
                        )
                    }
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
                Text("推荐歌单", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
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
                Text("推荐新歌", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
            }
            items(home.newSongs) { song ->
                SongRow(song) { vm.play(home.newSongs, home.newSongs.indexOf(song)) }
            }
        }
    }
}

/** Tall recommendation card: cover art full-bleed with a scrim, label + artists at the bottom. */
@Composable
private fun RecommendCard(
    title: String,
    songs: List<Song>,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onPlay: () -> Unit,
) {
    Card(
        onClick = if (loggedIn) onPlay else onLogin,
        modifier = Modifier.size(width = 200.dp, height = 260.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            if (loggedIn && songs.isNotEmpty()) {
                AsyncImage(
                    songs.first().picUrl,
                    title,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.8f)),
                        ),
                    ),
                )
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        artistLine(songs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
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
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        AsyncImage(
            song.picUrl,
            song.name,
            Modifier.size(140.dp).clip(MaterialTheme.shapes.extraLarge),
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
