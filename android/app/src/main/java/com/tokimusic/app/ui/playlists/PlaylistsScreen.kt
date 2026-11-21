package com.tokimusic.app.ui.playlists
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import com.tokimusic.app.data.model.*
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.formatTotalDurationMs
import com.tokimusic.app.ui.theme.CirroBlack
import com.tokimusic.app.ui.theme.CirroPlayKey
import com.tokimusic.app.ui.theme.CirroPlayKeyIcon
import com.tokimusic.app.ui.theme.rememberCoverColors

/**
 * 歌单详情（第二轮第 8/9/10 项）：
 * - 标题不用 TokiUI 悬浮标题：封面下方居左展示（titleLarge 22sp Bold），
 *   右侧是圆形底色播放按钮（从第一首播整个歌单）；元信息行左侧
 *   “<x 首 · x 分 y 秒>”（总时长 = 全部曲目时长求和，满 1 小时仍写分秒），
 *   同行居右只显示创建者名字；标题行滑出可视区后，悬浮标题以淡入 +
 *   上移动画出现（见 TokiMusicApp 的 detailHeaderVisible）。
 * - 全页颜色封面自适应：页面背景是封面渐变（上端→下端→纯黑），
 *   上下淡出色跟随渐变。
 */
@Composable fun PlaylistDetailScreen(
    vm: AppViewModel,
    playlistId: Long,
    bottomPadding: Dp,
    onTitle: (String) -> Unit,
    onHeaderVisible: (Boolean) -> Unit = {},
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
    val coverColors = rememberCoverColors(playlistId, pl.coverUrl)
    val listState = rememberLazyListState()
    // 标题行（第 0 项）滑出可视区后，通知外层显示悬浮标题。
    val showFloatingTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    LaunchedEffect(showFloatingTitle) { onHeaderVisible(showFloatingTitle) }
    val totalMs = tracks.sumOf { it.durationMs }
    val count = if (tracks.isNotEmpty()) tracks.size else pl.trackCount
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(coverColors.top, coverColors.bottom, CirroBlack)))) {
        // 顶部 1/5 区域：歌单封面，下缘淡出到封面渐变的上端色；封面延伸到状态栏下，
        // 悬浮标题（滑动后出现）的磨砂盖在上面。
        Box(Modifier.fillMaxWidth().weight(0.2f)) {
            AsyncImage(pl.coverUrl, pl.name, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, coverColors.top),
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
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    pl.name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (tracks.isNotEmpty()) {
                                    Spacer(Modifier.width(12.dp))
                                    Box(
                                        Modifier.size(56.dp).clip(CircleShape).background(CirroPlayKey)
                                            .clickable { vm.play(tracks, 0) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "播放全部", tint = CirroPlayKeyIcon, modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$count 首 · ${formatTotalDurationMs(totalMs)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                                Text(
                                    pl.creatorName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    itemsIndexed(tracks) { i, song -> SongRow(song, i) { vm.play(tracks, i) } }
                }
                Box(
                    Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                        Brush.verticalGradient(
                            listOf(coverColors.top, Color.Transparent),
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, CirroBlack),
                        ),
                    ),
                )
            }
        }
    }
}
