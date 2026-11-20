package com.tokimusic.app.ui.playlists
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    Column(Modifier.fillMaxSize()) {
        // 顶部 1/5 区域：歌单封面，下缘淡出到页面底色；封面延伸到状态栏下，
        // 悬浮标题的磨砂盖在上面。
        Box(Modifier.fillMaxWidth().weight(0.2f)) {
            AsyncImage(pl.coverUrl, pl.name, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainer),
                    ),
                ),
            )
        }
        // 歌曲列表：上下各 32dp 淡出。淡出层没有触摸处理，不会挡住滚动。
        Box(Modifier.fillMaxWidth().weight(0.8f)) {
            if (loadError && detail == null) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("歌单加载失败", style = MaterialTheme.typography.titleMedium)
                    Text("请检查网络后重试", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { reloadKey++ }, Modifier.height(56.dp)) { Text("重试") }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
                    item { Text("${pl.trackCount} 首 · 创建者 ${pl.creatorName}", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    itemsIndexed(tracks) { i, song -> SongRow(song, i) { vm.play(tracks, i) } }
                }
                Box(
                    Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.surfaceContainer, Color.Transparent),
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                        ),
                    ),
                )
            }
        }
    }
}
