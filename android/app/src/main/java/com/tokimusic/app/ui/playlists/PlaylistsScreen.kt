package com.tokimusic.app.ui.playlists
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
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
        // 悬浮标题的磨砂盖在上面。Cirro 模式下页面是纯黑，淡出色改用黑。
        val topColor = com.tokimusic.app.ui.theme.pageTopColor()
        Box(Modifier.fillMaxWidth().weight(0.2f)) {
            AsyncImage(pl.coverUrl, pl.name, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, topColor),
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
                            listOf(topColor, Color.Transparent),
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
