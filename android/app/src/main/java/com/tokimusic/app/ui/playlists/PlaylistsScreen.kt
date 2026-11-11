package com.tokimusic.app.ui.playlists
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.tokimusic.app.data.api.MockData
import com.tokimusic.app.data.model.*
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SongRow

@Composable fun PlaylistsScreen(vm: AppViewModel, bottomPadding: androidx.compose.ui.unit.Dp, onOpenPlaylist: (Long) -> Unit) {
    val mine by vm.myPlaylists.collectAsState(); val profile by vm.profile.collectAsState(); val home by vm.home.collectAsState()
    val all = if (mine.isNotEmpty()) mine else if (home.isDemo) MockData.playlists else emptyList()
    val groups = remember(all, profile) { groupPlaylists(all, profile?.uid ?: 0) }
    var collapsed by remember { mutableStateOf(setOf<PlaylistGroup>()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        item { Text("歌单", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        if (profile == null && !home.isDemo) item { Text("登录后查看我的歌单（Demo 模式下显示示例分组）", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        groups.forEach { (group, list) ->
            if (list.isEmpty()) return@forEach
            item {
                Row(Modifier.fillMaxWidth().clickable { collapsed = if (group in collapsed) collapsed - group else collapsed + group }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${list.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(if (group in collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null)
                }
            }
            if (group !in collapsed) items(list) { pl ->
                Row(Modifier.fillMaxWidth().clickable { onOpenPlaylist(pl.id) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(pl.coverUrl, pl.name, Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(pl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1); Text("${pl.trackCount} 首 · ${pl.creatorName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
@Composable fun PlaylistDetailScreen(vm: AppViewModel, playlistId: Long, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<Pair<Playlist, List<Song>>?>(null) }
    LaunchedEffect(playlistId) { detail = runCatching { vm.api.playlistDetail(playlistId) }.getOrElse { MockData.playlists.firstOrNull { it.id == playlistId }?.let { it to MockData.songs } ?: (MockData.playlists.first() to MockData.songs) } }
    val (pl, tracks) = detail ?: (Playlist(playlistId, "加载中…") to emptyList())
    LazyColumn(Modifier.fillMaxSize()) {
        item { TextButton(onClick = onBack) { Text("‹ 返回") }; Text(pl.name, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${pl.trackCount} 首 · 创建者 ${pl.creatorName}", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(tracks) { i, song -> SongRow(song, i) { vm.play(tracks, i) } }
    }
}
