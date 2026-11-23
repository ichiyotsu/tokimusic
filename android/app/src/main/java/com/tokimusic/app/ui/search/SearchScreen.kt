package com.tokimusic.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.PlaylistCard
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.theme.CirroBlack
import kotlinx.coroutines.delay

/**
 * 搜索独立页（第三轮第 11 项，推翻第二轮第 23 项的底部弹窗）：
 * 第二级页面形态（与歌单详情同级），悬浮模糊标题 + 返回 chip 由
 * TokiMusicApp 的 OverlayTitleHeader 提供；搜索框在内容区顶部，下方为
 * 歌曲 / 歌单分段结果。搜索逻辑不变：复用 vm.search()（400ms 防抖）与
 * HomeUiState 的搜索字段；点歌单后关闭搜索页并进详情页；点歌曲行歌手名
 * 进歌手页（第 12 项）。入口仍在底栏独立按钮 / NavigationRail。
 */
@Composable
fun SearchScreen(vm: AppViewModel, topPadding: Dp, bottomPadding: Dp, onOpenPlaylist: (Long) -> Unit, onOpenArtist: (Long) -> Unit) {
    val home by vm.home.collectAsState()
    var query by remember { mutableStateOf("") }
    // Debounced: one request per pause in typing, not one per keystroke.
    LaunchedEffect(query) { delay(400); vm.search(query) }
    Column(Modifier.fillMaxSize().padding(top = topPadding)) {
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            placeholder = { Text("搜索歌曲、歌手、歌单") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(percent = 50),
        )
        // 第三轮第 14 项：结果列表上下边缘 32dp 淡出（与主页同款）。
        Box(Modifier.weight(1f)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (home.searching) {
                item { Text("搜索中…", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (query.isNotBlank()) {
                if (home.searchResults.isNotEmpty()) {
                    item { Text("歌曲", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge) }
                    itemsIndexed(home.searchResults) { i, song -> SongRow(song, onOpenArtist = onOpenArtist, onLongPress = { vm.openSongActions(it) }) { vm.play(home.searchResults, i, "搜索结果") } }
                }
                if (home.searchPlaylists.isNotEmpty()) {
                    item { Text("歌单", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge) }
                    item {
                        LazyRow(
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
}
