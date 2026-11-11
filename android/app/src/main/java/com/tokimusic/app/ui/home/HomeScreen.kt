package com.tokimusic.app.ui.home
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.*

@Composable fun HomeScreen(vm: AppViewModel, bottomPadding: androidx.compose.ui.unit.Dp, onOpenLogin: () -> Unit, onOpenPlaylist: (Long) -> Unit) {
    val home by vm.home.collectAsState(); val profile by vm.profile.collectAsState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) { vm.search(query) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Capsule search box (left, weighted) + avatar login entry (top-right).
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("搜索歌曲、歌手、歌单") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(50))
                Spacer(Modifier.width(12.dp))
                // Logged-in: real avatar. Logged-out: default avatar (empty url) — both tap to login/profile.
                IconButton(onClick = onOpenLogin) { Avatar(profile?.avatarUrl ?: "", 40) }
            }
            if (profile == null) Text("未登录 · 点击右上角头像扫码登录", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (home.isDemo) AssistChip(onClick = {}, label = { Text("Demo 数据 · 未连接网易云 API 服务") }, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (home.searching) {
            item { Text("搜索结果", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(home.searchResults) { song -> SongRow(song) { vm.play(home.searchResults, home.searchResults.indexOf(song)) } }
            return@LazyColumn
        }
        if (home.banners.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(home.banners) { b -> Card(shape = RoundedCornerShape(26.dp)) { Box(Modifier.size(width = 320.dp, height = 140.dp), contentAlignment = Alignment.BottomStart) { coil.compose.AsyncImage(b.picUrl, b.title, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop); Text(b.title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) } } }
            }
        }
        item { Text("每日推荐", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(home.daily.take(5)) { song -> SongRow(song) { vm.play(home.daily, home.daily.indexOf(song)) } }
        item { Text("推荐歌单", Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(home.playlists) { PlaylistCard(it) { onOpenPlaylist(it.id) } } } }
        item { Text("推荐新歌", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(home.newSongs) { song -> SongRow(song) { vm.play(home.newSongs, home.newSongs.indexOf(song)) } }
    }
}
