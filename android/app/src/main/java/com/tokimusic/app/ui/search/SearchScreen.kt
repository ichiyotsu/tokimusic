package com.tokimusic.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.PlaylistCard
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.TokiSheetContainer
import kotlinx.coroutines.delay

/**
 * 搜索弹窗（第二轮第 23 项）：底栏独立搜索钮打开的底部弹窗
 * （TokiSheetContainer 配方）。搜索逻辑不变：复用 vm.search()
 *（400ms 防抖）与 HomeUiState 的搜索字段；点歌单后关闭弹窗并进详情页。
 */
@Composable
fun SearchSheet(vm: AppViewModel, onOpenPlaylist: (Long) -> Unit, onDismiss: () -> Unit) {
    val home by vm.home.collectAsState()
    var query by remember { mutableStateOf("") }
    // Debounced: one request per pause in typing, not one per keystroke.
    LaunchedEffect(query) { delay(400); vm.search(query) }
    TokiSheetContainer(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "搜索",
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                placeholder = { Text("搜索歌曲、歌手、歌单") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(percent = 50),
            )
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (home.searching) {
                    item { Text("搜索中…", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else if (query.isNotBlank()) {
                    if (home.searchResults.isNotEmpty()) {
                        item { Text("歌曲", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge) }
                        itemsIndexed(home.searchResults) { i, song -> SongRow(song) { vm.play(home.searchResults, i) } }
                    }
                    if (home.searchPlaylists.isNotEmpty()) {
                        item { Text("歌单", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge) }
                        item {
                            androidx.compose.foundation.lazy.LazyRow(
                                Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(home.searchPlaylists) { playlist -> PlaylistCard(playlist) { onOpenPlaylist(playlist.id) } }
                            }
                        }
                    }
                    if (home.searchResults.isEmpty() && home.searchPlaylists.isEmpty()) {
                        item { Text("没有找到相关结果", Modifier.padding(horizontal = 4.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
