package com.tokimusic.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.PlaylistCard
import com.tokimusic.app.ui.components.SongRow
import kotlinx.coroutines.delay

/**
 * Cirro 搜索 tab：搜索框 + 歌曲/歌单分段结果（阶段 6 补齐歌单分段）。
 */
@Composable
fun SearchScreen(vm: AppViewModel, topPadding: Dp, bottomPadding: Dp, onOpenPlaylist: (Long) -> Unit) {
    val home by vm.home.collectAsState()
    var query by remember { mutableStateOf("") }
    // Debounced: one request per pause in typing, not one per keystroke.
    LaunchedEffect(query) { delay(400); vm.search(query) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "搜索",
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
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
            item { Text("搜索中…", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (query.isNotBlank()) {
            if (home.searchResults.isNotEmpty()) {
                item { Text("歌曲", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge) }
                itemsIndexed(home.searchResults) { i, song -> SongRow(song) { vm.play(home.searchResults, i) } }
            }
            if (home.searchPlaylists.isNotEmpty()) {
                item { Text("歌单", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge) }
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(home.searchPlaylists) { playlist -> PlaylistCard(playlist) { onOpenPlaylist(playlist.id) } }
                    }
                }
            }
            if (home.searchResults.isEmpty() && home.searchPlaylists.isEmpty()) {
                item { Text("没有找到相关结果", Modifier.padding(horizontal = 24.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
