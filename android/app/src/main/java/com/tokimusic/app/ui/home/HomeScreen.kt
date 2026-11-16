package com.tokimusic.app.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.data.model.Playlist
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.TokiLongPressLabel

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
    LaunchedEffect(query) { vm.search(query) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.weight(1f),
                    placeholder = { Text("搜索歌曲、歌手、歌单") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(percent = 50),
                )
                Spacer(Modifier.width(12.dp))
                TokiLongPressLabel(if (profile == null) "登录" else "我的") {
                    IconButton(onClick = onOpenLogin) {
                        Avatar(profile?.avatarUrl ?: "", 40)
                    }
                }
            }
            if (profile == null) {
                Text(
                    "未登录 · 点击右上角头像扫码登录",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (home.isDemo) AssistChip(onClick = {}, label = { Text("Demo 数据 · 未连接网易云 API 服务") }, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (home.searching) {
            item { Text("搜索结果", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge) }
            items(home.searchResults) { song ->
                SongRow(song) { vm.play(home.searchResults, home.searchResults.indexOf(song)) }
            }
            return@LazyColumn
        }
        if (home.banners.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(home.banners) { banner ->
                        Card(shape = RoundedCornerShape(28.dp)) {
                            Box(Modifier.size(width = 280.dp, height = 140.dp), contentAlignment = Alignment.BottomStart) {
                                AsyncImage(
                                    banner.picUrl,
                                    banner.title,
                                    Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                Text(
                                    banner.title,
                                    Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (home.daily.isNotEmpty()) {
            item {
                Text("继续收听", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    home.daily.take(8).forEach { song ->
                        ContinueCard(song) { vm.play(home.daily, home.daily.indexOf(song)) }
                    }
                }
            }
        }
        if (home.playlists.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("歌单", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
            }
            items(home.playlists) { playlist ->
                AlbumRow(playlist) { onOpenPlaylist(playlist.id) }
            }
        }
        if (home.newSongs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("歌曲", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
            }
            items(home.newSongs) { song ->
                SongRow(song) { vm.play(home.newSongs, home.newSongs.indexOf(song)) }
            }
        }
    }
}

@Composable
private fun ContinueCard(song: Song, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(148.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsyncImage(
                song.picUrl,
                song.name,
                Modifier.size(124.dp).clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(song.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artistText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AlbumRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            playlist.coverUrl,
            playlist.name,
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.trackCount} 首 · ${playlist.creatorName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
