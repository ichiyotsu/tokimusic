package com.tokimusic.app.ui.playlists
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.tokimusic.app.data.model.*
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.formatTotalDurationMs
import com.tokimusic.app.ui.theme.CirroBlack
import com.tokimusic.app.ui.theme.CirroPlayKey
import com.tokimusic.app.ui.theme.CirroPlayKeyIcon
import com.tokimusic.app.ui.theme.rememberCoverColors

/**
 * 歌单详情（第二轮第 8/9/10 项；第三轮第 4/5/6/7 项重排）：
 * - 标题区三行：小标题（作者）→ 大标题（headlineMedium 28sp Bold）→
 *   纯图标胶囊播放键（左）+ “<x 首 · x 分 y 秒>”（右）；颜色按封面亮度
 *   自适应（亮封面深字 / 暗封面白字）。总时长 = 全部曲目时长求和。
 * - 进页时封面占视口 1/4、完整显示；向上滑时封面按滚动距离等量收起，
 *   标题行滑出后封面高度为 0，歌单接管全页。标题行滑出可视区后，悬浮
 *   标题以淡入 + 上移动画出现（见 TokiMusicApp 的 detailHeaderVisible），
 *   返回 chip 一直显示（第 5 项）。
 * - 每日推荐 / 私人雷达是虚拟歌单（id -1/-2）：不调用歌单详情接口，
 *   直接用主页已加载的歌曲拼出（第 4 项）。
 * - 全页颜色封面自适应：页面背景是封面渐变（上端→下端→纯黑），
 *   上下淡出色跟随渐变。
 */
@Composable fun PlaylistDetailScreen(
    vm: AppViewModel,
    playlistId: Long,
    bottomPadding: Dp,
    onTitle: (String) -> Unit,
    onHeaderVisible: (Boolean) -> Unit = {},
    // Round 3 item 12: song-row artist names open the artist page.
    onOpenArtist: (Long) -> Unit = {},
) {
    var detail by remember { mutableStateOf<Pair<Playlist, List<Song>>?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    // 第 6 项：加歌 / 删歌成功后 AppViewModel  bump playlistVersion，这里重新拉取。
    val playlistVersion by vm.playlistVersion.collectAsState()
    val profile by vm.profile.collectAsState()
    LaunchedEffect(playlistId, reloadKey, playlistVersion) {
        loadError = false
        // 第三轮第 4 项：虚拟歌单（每日推荐 -1 / 私人雷达 -2）不调用任何
        // 歌单详情接口，直接用主页已加载的歌曲拼出歌单。
        if (playlistId == -1L || playlistId == -2L) {
            val songs = if (playlistId == -1L) vm.home.value.daily else vm.home.value.radar
            val name = if (playlistId == -1L) "每日推荐" else "私人雷达"
            detail = Playlist(
                id = playlistId,
                name = name,
                coverUrl = songs.firstOrNull()?.picUrl ?: "",
                trackCount = songs.size,
                creatorName = "Netease Music",
            ) to songs
        } else {
            detail = runCatching { vm.api.playlistDetail(playlistId) }.onFailure { loadError = true }.getOrNull()
        }
    }
    val (pl, tracks) = detail ?: (Playlist(playlistId, "加载中…") to emptyList())
    // 第 6 项：自己的歌单 = creator 是当前登录用户；虚拟歌单（creatorId 0）
    // 和“我喜欢的音乐”（取消喜欢走 /like，不走删歌接口）不算。
    val isOwnPlaylist = pl.creatorId != 0L && pl.creatorId == profile?.uid && !pl.name.contains("喜欢的音乐")
    LaunchedEffect(pl.name) { onTitle(pl.name) }
    val coverColors = rememberCoverColors(playlistId, pl.coverUrl)
    val listState = rememberLazyListState()
    // 标题行（第 0 项）滑出可视区后，通知外层显示悬浮标题。
    val showFloatingTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    LaunchedEffect(showFloatingTitle) { onHeaderVisible(showFloatingTitle) }
    // 第三轮第 7 项：进页时封面占视口 1/4，向上滑时按滚动距离等量收起；
    // 标题行高度用实测值参与累计滚动量，避免第 0 项 → 第 1 项切换时
    // 封面高度跳变（若真机仍不稳，退回固定 1/4 + 标题 morph）。
    var titleHeightPx by remember { mutableIntStateOf(0) }
    val totalMs = tracks.sumOf { it.durationMs }
    val count = if (tracks.isNotEmpty()) tracks.size else pl.trackCount
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(coverColors.top, coverColors.bottom, CirroBlack)))) {
        val density = LocalDensity.current
        val quarterPx = with(density) { maxHeight.toPx() * 0.25f }
        val scrollPx by remember {
            derivedStateOf {
                if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset
                else titleHeightPx + listState.firstVisibleItemScrollOffset
            }
        }
        val coverHeightPx = (quarterPx - scrollPx).coerceAtLeast(0f)
        Column(Modifier.fillMaxSize()) {
            // 封面：进页占 1/4、完整显示，下缘淡出到封面渐变的上端色；
            // 封面延伸到状态栏下，悬浮标题（滑动后出现）的磨砂盖在上面。
            Box(Modifier.fillMaxWidth().height(with(density) { coverHeightPx.toDp() })) {
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
            Box(Modifier.fillMaxWidth().weight(1f)) {
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
                        // 第三轮第 6 项：小标题（作者）→ 大标题 → 胶囊播放键 +
                        // 曲目统计；颜色按封面亮度自适应（亮封面深字 / 暗封面白字）。
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .onGloballyPositioned { titleHeightPx = it.size.height },
                        ) {
                            if (pl.creatorName.isNotBlank()) {
                                Text(
                                    pl.creatorName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = coverColors.onCoverTextDim(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                pl.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = coverColors.onCoverText(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (tracks.isNotEmpty()) {
                                    // 纯图标胶囊播放键（用户定）：56dp 高，最大圆角。
                                    Box(
                                        Modifier
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(CirroPlayKey)
                                            .clickable { vm.play(tracks, 0) }
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "播放全部", tint = CirroPlayKeyIcon, modifier = Modifier.size(28.dp))
                                    }
                                    Spacer(Modifier.width(16.dp))
                                }
                                Text(
                                    "$count 首 · ${formatTotalDurationMs(totalMs)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = coverColors.onCoverTextDim(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    itemsIndexed(tracks) { i, song -> SongRow(song, i, onOpenArtist = onOpenArtist, onLongPress = { vm.openSongActions(it, playlistId, isOwnPlaylist) }) { vm.play(tracks, i) } }
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
}
