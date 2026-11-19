package com.tokimusic.app.ui.playlists
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import com.tokimusic.app.data.model.*
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SongRow

@Composable fun PlaylistsScreen(vm: AppViewModel, topPadding: Dp, bottomPadding: Dp, onOpenPlaylist: (Long) -> Unit) {
    val mine by vm.myPlaylists.collectAsState(); val profile by vm.profile.collectAsState()
    // No Demo data: without a login there is simply nothing here yet.
    val all = mine
    val groups = remember(all, profile) { groupPlaylists(all, profile?.uid ?: 0) }
    var collapsed by remember { mutableStateOf(setOf<PlaylistGroup>()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)) {
        if (profile == null) item { Text("登录后查看我的歌单", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        groups.forEach { (group, list) ->
            if (list.isEmpty()) return@forEach
            item {
                Row(Modifier.fillMaxWidth().clickable { collapsed = if (group in collapsed) collapsed - group else collapsed + group }.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${list.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                    Icon(
                        if (group in collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (group in collapsed) "展开${group.title}" else "收起${group.title}",
                    )
                }
            }
            if (group !in collapsed) items(list) { pl ->
                Row(Modifier.fillMaxWidth().clickable { onOpenPlaylist(pl.id) }.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(pl.coverUrl, pl.name, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pl.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text("${pl.trackCount} 首 · ${pl.creatorName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
@Composable fun PlaylistDetailScreen(
    vm: AppViewModel,
    playlistId: Long,
    topPadding: Dp,
    bottomPadding: Dp,
    onTitle: (String) -> Unit,
) {
    var detail by remember { mutableStateOf<Pair<Playlist, List<Song>>?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(playlistId, reloadKey) {
        loadError = false
        // A failed load is an error page with a retry, never Demo data.
        detail = runCatching { vm.api.playlistDetail(playlistId) }.onFailure { loadError = true }.getOrNull()
    }
    val (pl, tracks) = detail ?: (Playlist(playlistId, "加载中…") to emptyList())
    LaunchedEffect(pl.name) { onTitle(pl.name) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)) {
        if (loadError && detail == null) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("歌单加载失败", style = MaterialTheme.typography.titleMedium)
                    Text("请检查 API 服务后重试", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { reloadKey++ }, Modifier.height(56.dp)) { Text("重试") }
                }
            }
            return@LazyColumn
        }
        item { Text("${pl.trackCount} 首 · 创建者 ${pl.creatorName}", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(tracks) { i, song -> SongRow(song, i) { vm.play(tracks, i) } }
    }
}
