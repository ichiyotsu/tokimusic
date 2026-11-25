package com.tokimusic.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.tokimusic.app.R
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Playlist
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.PlaylistCard
import com.tokimusic.app.ui.components.SmallRoundPlayButton
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.SourceBadge
import com.tokimusic.app.ui.components.SourceIcon
import com.tokimusic.app.ui.components.resolveArtistTap
import com.tokimusic.app.ui.theme.TokiBlack
import com.tokimusic.app.ui.theme.tokiGlassFade

/**
 * Home, rebuilt from the reference: a large in-content title with the avatar at
 * the top right, a capsule search, tall recommendation cards （每日推荐 / 私人雷达）,
 * a recently-played album row, recommended playlists and new songs. Every
 * section is fed by the NetEase API service — no Demo data: login-gated
 * sections ask the user to sign in, and a dead service shows an error + retry.
 * The pinned overlay title is hidden on this page (see TokiMusicApp).
 *
 * Sixth round, batch 2: dual-source display + Home rearrangement —
 * order 每日推荐 → 最近播放 → 推荐歌单 3 行 → 推荐新歌 2 列（0.1.18
 * 第 13 项：排行榜分区已从主页删除）；推荐歌单 3 行横向网格（网易云 +
 * QQ 同一网格，卡片带源角标）；推荐新歌 2 列横向网格（时长 → 播放钮 →
 * 来源 icon）；下拉刷新（PullToRefreshBox）与冷启动共用 loadHome；
 * 两源都关时只留本地音乐区（MediaStore 扫描 + 本地播放）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
    hazeState: dev.chrisbanes.haze.HazeState,
    backdropBlur: Boolean,
    onOpenLogin: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    // Round 3 item 12: song-row artist names open the artist page.
    onOpenArtist: (Long) -> Unit = {},
    // 0.1.15 第 2 项：QQ 歌曲的歌手按 mid 进 QQ 歌手页。
    onOpenQqArtist: (String) -> Unit = {},
    // Round 3 item 20: reports the section whose in-page title has scrolled
    // under the top bar ("主页" / "推荐歌单" / "推荐新歌"); TokiMusicApp swaps
    // the floating title chip to match.
    onSectionTitle: (String) -> Unit = {},
) {
    val home by vm.home.collectAsState()
    val neteaseEnabled by vm.neteaseEnabled.collectAsState()
    val qqEnabled by vm.qqEnabled.collectAsState()
    // 0.1.18 第 15 项：算法推荐（开关 + 结果在组合层收集；候选或开关
    // 变化时重算，VM 里开关关时直接返回）。
    val algorithmEnabled by vm.algorithmRecommendEnabled.collectAsState()
    val algorithmPlaylists by vm.algorithmPlaylists.collectAsState()
    LaunchedEffect(algorithmEnabled, home.playlists, home.qqPlaylists) {
        if (algorithmEnabled) vm.refreshAlgorithmRecommendations()
    }
    val bothEnabled = neteaseEnabled && qqEnabled
    var entered by remember { mutableStateOf(false) }
    // 0.1.18 第 6 项：进场淡入走 effects spec（之前手写 tween(300)）。
    val enterAlpha by animateFloatAsState(if (entered) 1f else 0f, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "homeEnter")
    LaunchedEffect(Unit) { entered = true }
    // Round 3 item 20: track the section titles' top edges; the last one
    // at or above the top bar's bottom edge owns the floating title.
    val density = LocalDensity.current
    var playlistsTitleY by remember { mutableStateOf(Float.MAX_VALUE) }
    var newSongsTitleY by remember { mutableStateOf(Float.MAX_VALUE) }
    val topBarBottomPx = with(density) { topPadding.toPx() }
    val activeSection by remember {
        derivedStateOf {
            when {
                newSongsTitleY <= topBarBottomPx -> "推荐新歌"
                playlistsTitleY <= topBarBottomPx -> "推荐歌单"
                else -> "主页"
            }
        }
    }
    LaunchedEffect(activeSection) { onSectionTitle(activeSection) }
    val hasContent = home.daily.isNotEmpty() || home.radar.isNotEmpty() || home.recent.isNotEmpty() ||
        home.playlists.isNotEmpty() || home.newSongs.isNotEmpty() ||
        home.qqPlaylists.isNotEmpty() || home.qqNewSongs.isNotEmpty() ||
        home.localMode
    val pullState = rememberPullToRefreshState()
    // Sixth round, batch 2, item 20: local-mode audio permission + scan.
    // The launcher lives here (not in the section) because the local song
    // rows are LazyColumn items, not a nested composable.
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasLocalPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val localPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocalPermission = granted
        if (granted) vm.scanLocalSongs()
    }
    LaunchedEffect(home.localMode, hasLocalPermission) {
        if (home.localMode && hasLocalPermission) vm.scanLocalSongs()
    }
    // Sixth round, batch 2, item 34: pull-to-refresh shares loadHome with
    // cold start; the indicator only shows on a user pull (home.loading
    // is the cold-start spinner, home.isPullRefreshing is the pull one).
    PullToRefreshBox(
        isRefreshing = home.isPullRefreshing,
        onRefresh = { vm.refreshHome() },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
    // 第三轮第 14 项：列表上下边缘 32dp 淡出（内容滑到悬浮标题 / 底栏
    // 下方时柔和消失，与歌单详情同款；淡出层不处理触摸，不挡滚动）。
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().graphicsLayer { alpha = enterAlpha },
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 页内大标题已删（第二轮第 3 项）：标题走 TokiUI 悬浮模糊标题，
        // 头像入口在标题行右侧（见 TokiMusicApp）。
        if (home.loading && !hasContent) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }
        if (home.error && !hasContent) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("无法连接网易云", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "请检查网络后重试",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { vm.refreshHome() }, Modifier.height(56.dp)) { Text("重试") }
                    FilledTonalButton(onClick = onOpenLogin, Modifier.height(56.dp)) { Text("去登录") }
                }
            }
            return@LazyColumn
        }
        // Sixth round, batch 2, item 20: both network sources off → only
        // the local player remains (scan + play on-device files).
        if (home.localMode) {
            item {
                LocalMusicHeader(
                    hasSongs = home.localSongs.isNotEmpty(),
                    hasPermission = hasLocalPermission,
                    onRequestPermission = { localPermissionLauncher.launch(audioPermission) },
                    onRescan = { vm.scanLocalSongs() },
                )
            }
            if (hasLocalPermission) {
                itemsIndexed(home.localSongs) { index, song ->
                    SongRow(song, onLongPress = { vm.openSongActions(it) }) { vm.play(home.localSongs, index, "本地音乐") }
                }
            }
        }
        val showDaily = neteaseEnabled && (!home.loggedIn || home.daily.isNotEmpty())
        val showRadar = neteaseEnabled && (!home.loggedIn || home.radar.isNotEmpty())
        if (showDaily || showRadar) {
            item {
                // 每日推荐 / 私人雷达双列并排占满宽（不是横向滚动）。
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showDaily) {
                        RecommendCard(
                            title = "每日推荐",
                            songs = home.daily,
                            loggedIn = home.loggedIn,
                            onLogin = onOpenLogin,
                            onOpen = { onOpenPlaylist(-1L) },
                            hazeState = hazeState,
                            backdropBlur = backdropBlur,
                            showSourceBadge = bothEnabled,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (showRadar) {
                        RecommendCard(
                            title = "私人雷达",
                            songs = home.radar,
                            loggedIn = home.loggedIn,
                            onLogin = onOpenLogin,
                            onOpen = { onOpenPlaylist(-2L) },
                            hazeState = hazeState,
                            backdropBlur = backdropBlur,
                            showSourceBadge = bothEnabled,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!showDaily || !showRadar) Spacer(Modifier.weight(1f))
                }
            }
        }
        // 0.1.15 第 15 项：QQ 每日推荐 / 私人雷达（与网易云双列同款
        // 布局，单独一行；只在对应列表非空时出现。每日需 QQ 登录，
        // 未登录时 qqDaily 为空所以不显示；雷达匿名可用。0.1.18 第 8 项：
        // QQ 两张卡改名 Daily 30 / For You（网易云卡片仍叫每日推荐 /
        // 私人雷达）；源徽标固定显示（QQ），与网易云卡片区分；
        // 点开走虚拟歌单 -3（QQ 每日）/ -4（QQ 雷达）。）
        val showQqDaily = qqEnabled && home.qqDaily.isNotEmpty()
        val showQqRadar = qqEnabled && home.qqRadar.isNotEmpty()
        if (showQqDaily || showQqRadar) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showQqDaily) {
                        RecommendCard(
                            title = "Daily 30",
                            songs = home.qqDaily,
                            loggedIn = true,
                            onLogin = onOpenLogin,
                            onOpen = { onOpenPlaylist(-3L) },
                            hazeState = hazeState,
                            backdropBlur = backdropBlur,
                            showSourceBadge = true,
                            source = MusicSource.QQ,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (showQqRadar) {
                        RecommendCard(
                            title = "For You",
                            songs = home.qqRadar,
                            loggedIn = true,
                            onLogin = onOpenLogin,
                            onOpen = { onOpenPlaylist(-4L) },
                            hazeState = hazeState,
                            backdropBlur = backdropBlur,
                            showSourceBadge = true,
                            source = MusicSource.QQ,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!showQqDaily || !showQqRadar) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (neteaseEnabled && (!home.loggedIn || home.recent.isNotEmpty())) {
            item {
                Text("最近播放", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                if (!home.loggedIn) {
                    Card(
                        onClick = onOpenLogin,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Text(
                            "登录后查看最近播放",
                            Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(home.recent) { index, song ->
                            RecentCard(song) { vm.play(home.recent, index, "最近播放") }
                        }
                    }
                }
            }
        }
        // Sixth round, batch 2, item 33 (overrides item 8's 2 rows):
        // recommended playlists in a 3-row horizontal grid, each card
        // badged with its source when both are on. 0.1.18 第 14 项：
        // 两源歌单 round-robin 交错进同一网格，不再网易云一块、QQ 一块。
        val allPlaylists = interleavePlaylists(home.playlists, home.qqPlaylists)
        if (allPlaylists.isNotEmpty()) {
            item {
                Text(
                    "推荐歌单",
                    Modifier.padding(horizontal = 24.dp)
                        .onGloballyPositioned { playlistsTitleY = it.positionInRoot().y },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height(644.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(allPlaylists, key = { it.id }) { playlist ->
                        PlaylistCard(playlist, showSourceBadge = bothEnabled) { onOpenPlaylist(playlist.id) }
                    }
                }
            }
        }
        // 0.1.18 第 15 项：算法推荐（测试功能）——开关开且有推荐结果时
        // 才出现；内容是按本地口味画像（播放事件 + 喜欢 + 关注）给两源
        // 推荐歌单打分选出的歌单，卡片与推荐歌单同款（收集与重算触发
        // 在组合层，见 HomeScreen 开头）。
        if (algorithmEnabled && algorithmPlaylists.isNotEmpty()) {
            item {
                Text("算法推荐", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(1),
                    modifier = Modifier.fillMaxWidth().height(204.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(algorithmPlaylists, key = { it.id }) { playlist ->
                        PlaylistCard(playlist, showSourceBadge = bothEnabled) { onOpenPlaylist(playlist.id) }
                    }
                }
            }
        }
        // Sixth round, batch 2, items 8 + 21: recommended new songs.
        // 0.1.18 第 2 项：改回多列横向网格（0.1.17 第 9 项的 10 行垂直列表
        // 只有一列、右滑不出更多列）——恢复 0.1.16 的 LazyHorizontalGrid
        // Fixed(6)，取全部歌曲，右滑出更多列；每行仍是 0.1.17 的 SongRow
        // 形态（来源 icon 无背景放标题前、尾部只留圆形播放钮）。
        val allNewSongs = home.newSongs + home.qqNewSongs
        if (allNewSongs.isNotEmpty()) {
            item {
                Text(
                    "推荐新歌",
                    Modifier.padding(horizontal = 24.dp)
                        .onGloballyPositioned { newSongsTitleY = it.positionInRoot().y },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                NewSongsGrid(allNewSongs, vm, onOpenArtist, onOpenQqArtist)
            }
        }
    }
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(listOf(TokiBlack, Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, TokiBlack)),
            ),
        )
    }
    }
}


/** Recommended new songs: multi-column horizontal grid (0.1.18 第 2 项：
 * 恢复 0.1.16 的 LazyHorizontalGrid Fixed(6)，取全部歌曲、右滑出更多列；
 * 0.1.17 的 10 行垂直列表只有一列）。一列是一整行宽的 SongRow（屏宽减
 * 左右 24dp 边距）；每行仍是 0.1.17 形态：来源 icon 无背景（SourceIcon，
 * 品牌色 tint）放在标题文字之前（SongRow leading 槽）；尾部只留圆形播放钮。 */
@Composable
private fun NewSongsGrid(songs: List<Song>, vm: AppViewModel, onOpenArtist: (Long) -> Unit, onOpenQqArtist: (String) -> Unit = {}) {
    // 0.1.15 第 2 项：QQ 新歌的歌手名按 mid 进 QQ 歌手页（混源列表，
    // 用 resolveArtistTap 分流）。
    val openArtistForRows: (Long) -> Unit = { id -> resolveArtistTap(songs, id, onOpenArtist, onOpenQqArtist) }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    LazyHorizontalGrid(
        rows = GridCells.Fixed(6),
        modifier = Modifier.fillMaxWidth().height(448.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 混源列表：NetEase 真实 id 与 QQ 合成 id 理论上可能碰撞，key 用下标。
        items(count = songs.size, key = { it }) { index ->
            val song = songs[index]
            Box(Modifier.width(screenWidth - 48.dp)) {
                SongRow(
                    song,
                    onOpenArtist = openArtistForRows,
                    onLongPress = { vm.openSongActions(it) },
                    horizontalPadding = 0.dp,
                    leading = { SourceIcon(song.source, size = 20) },
                    trailingAction = { SmallRoundPlayButton { vm.play(songs, index, "推荐新歌") } },
                ) { vm.play(songs, index, "推荐新歌") }
            }
        }
    }
}

/**
 * Local music header (sixth round, batch 2, item 20): explains local mode,
 * asks for the audio permission, or offers a rescan when nothing was found.
 * The song rows themselves are LazyColumn items in [HomeScreen].
 */
@Composable
private fun LocalMusicHeader(hasSongs: Boolean, hasPermission: Boolean, onRequestPermission: () -> Unit, onRescan: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 0.1.13 第 7 项：本地模式标题上方加文件夹音乐图标（mdi
        // folder-music-outline，56dp 圆角 tile，surfaceContainer 底，
        // 28dp 图标）。
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_local_music), contentDescription = null, modifier = Modifier.size(28.dp))
        }
        Text("本地音乐", style = MaterialTheme.typography.titleLarge)
        Text(
            "两源都已关闭，只显示本地音乐",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasPermission) {
            FilledTonalButton(onClick = onRequestPermission, Modifier.height(56.dp)) {
                Text("允许访问本地音乐")
            }
        } else if (!hasSongs) {
            Text(
                "本地暂无歌曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onRescan, Modifier.height(56.dp)) { Text("重新扫描") }
        }
    }
}

/** Tall recommendation card: cover art full-bleed with a scrim, label + artists at the bottom.
 *
 * 第二轮第 7 项：圆角 28dp → 20dp；标题与介绍文字读封面取色自适应
 * （亮封面深字、暗封面白字）；文字背景用淡出的模糊层（底部渐进磨砂，
 * 与底栏同配方），黑罩只留少量兜底；图片仍全幅铺满。
 */
@Composable
private fun RecommendCard(
    title: String,
    songs: List<Song>,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onOpen: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
    backdropBlur: Boolean,
    showSourceBadge: Boolean = false,
    // 大排查 B2：徽标按卡片来源画——QQ 每日/雷达传 QQ，之前这里写死
    // 网易云，QQ 卡片顶着网易云徽标。
    source: MusicSource = MusicSource.NETEASE,
    modifier: Modifier = Modifier,
) {
    val hasCover = loggedIn && songs.isNotEmpty()
    val coverColors = if (hasCover) com.tokimusic.app.ui.theme.rememberCoverColors(songs.first().id, songs.first().picUrl)
        else null
    val textColor = coverColors?.onCoverText() ?: Color.White
    val dimColor = coverColors?.onCoverTextDim() ?: Color.White.copy(alpha = 0.8f)
    val blurOn = com.tokimusic.app.ui.theme.chromeBlurActive(backdropBlur)
    Card(
        onClick = if (loggedIn) onOpen else onLogin,
        modifier = modifier.fillMaxWidth().aspectRatio(200f / 260f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            if (hasCover) {
                AsyncImage(
                    songs.first().picUrl,
                    title,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                // 淡出的模糊层：只盖底部文字区，自下而上渐弱；玻璃关闭时不画。
                if (blurOn) {
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(132.dp)
                            .tokiGlassFade(hazeState, fadeFromTop = false),
                    )
                }
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            if (blurOn) listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.32f))
                            else listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.8f)),
                        ),
                    ),
                )
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = textColor)
                    Text(
                        artistLine(songs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = dimColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "登录后查看",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 第六轮第二批第 21 项：双源都启用时，卡片左上角叠 28dp 源徽标。
            // 0.1.16 第 1 项起徽标底色是品牌色（网易云 #ff5757、QQ #879101），
            // 不再是莫奈色系；来源由 source 参数定（B2）。
            if (showSourceBadge) {
                SourceBadge(source, size = 28, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
            }
        }
    }
}

private fun artistLine(songs: List<Song>): String {
    val artists = songs.flatMap { it.artists }.map { it.name }.distinct()
    return if (artists.size > 4) artists.take(4).joinToString("，") + "等" else artists.joinToString("，")
}

@Composable
private fun RecentCard(song: Song, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).clickable(onClick = onClick)) {
        AsyncImage(
            song.picUrl,
            song.name,
            Modifier.size(112.dp).clip(MaterialTheme.shapes.extraLarge),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(8.dp))
        Text(song.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            song.artistText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 0.1.18 第 14 项：两源推荐歌单 round-robin 交错——网易云、QQ 各取一张轮流排，某源取完后剩下的另一源接在后面；来源仍由卡片上的 SourceBadge 区分。纯函数，JVM 单测覆盖。 */
internal fun interleavePlaylists(first: List<Playlist>, second: List<Playlist>): List<Playlist> {
    if (first.isEmpty()) return second
    if (second.isEmpty()) return first
    val out = ArrayList<Playlist>(first.size + second.size)
    val n = maxOf(first.size, second.size)
    for (i in 0 until n) {
        if (i < first.size) out.add(first[i])
        if (i < second.size) out.add(second[i])
    }
    return out
}
