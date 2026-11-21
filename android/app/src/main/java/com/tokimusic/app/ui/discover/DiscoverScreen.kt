package com.tokimusic.app.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.PlaylistCard
import com.tokimusic.app.ui.theme.CirroBlack

/**
 * Cirro 网格 tab：排行榜 + 推荐歌单。待用户补网格 tab 截图后再按图重做。
 */
@Composable
fun DiscoverScreen(vm: AppViewModel, topPadding: Dp, bottomPadding: Dp, onOpenPlaylist: (Long) -> Unit) {
    val home by vm.home.collectAsState()
    val toplist by vm.toplist.collectAsState()
    LaunchedEffect(Unit) { vm.loadToplist() }
    // 第三轮第 14 项：列表上下边缘 32dp 淡出（与主页、歌单详情同款）。
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 页内大标题已删（第二轮第 3 项）：标题走 TokiUI 悬浮模糊标题。
        if (toplist.isNotEmpty()) {
            item {
                Text("排行榜", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(toplist) { playlist ->
                        PlaylistCard(playlist) { onOpenPlaylist(playlist.id) }
                    }
                }
            }
        }
        if (home.playlists.isNotEmpty()) {
            item {
                Text("推荐歌单", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
            }
            item {
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
