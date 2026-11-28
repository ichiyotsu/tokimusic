package com.tokimusic.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.tokimusic.app.data.AppStrings
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
import com.tokimusic.app.ui.theme.tokiPageColor
import com.tokimusic.app.ui.theme.tokiGlassFade

/**
 * Home, rebuilt from the reference: a large in-content title with the avatar at
 * the top right, a capsule search, tall recommendation cards （每日推荐 / 私人雷达）,
 * recommended playlists and new songs. Every
 * section is fed by the NetEase API service — no Demo data: login-gated
 * sections ask the user to sign in, and a dead service shows an error + retry.
 * The pinned overlay title is hidden on this page (see TokiMusicApp).
 * （本轮 0127 第 17 条：主页「最近播放」横排已删除，资料库的最近播放不动。）
 *
 * Sixth round, batch 2: dual-source display + Home rearrangement —
 * order 每日推荐 → 推荐歌单 3 行 → 推荐新歌（0.1.18
 * 第 13 项：排行榜分区已从主页删除）；推荐歌单 3 行横向网格（网易云 +
 * QQ 同一网格，卡片带源角标）；推荐新歌 0.1.19 第 1 项起分网易云 / QQ
 * 两个板块，每块 3 行横向网格，来源 icon 在歌名之后；下拉刷新
 * （PullToRefreshBox）与冷启动共用 loadHome；
 * 两源都关时只留本地音乐区（MediaStore 扫描 + 本地播放）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
    hazeState: dev.chrisbanes.haze.HazeState,
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
    val youtubeEnabled by vm.youtubeEnabled.collectAsState()
    // 0.1.21：算法推荐分区与开关已撤除（画像改为常开基础设施，在
    // VM 的 loadHome 里直接重排推荐流），主页不再有独立推荐区。
    val localTracks by vm.localTracks.collectAsState()
    // 本轮（0124）第 19 项：徽标显示条件从「双源都开」放宽为「≥2 个在线源开启」。
    val bothEnabled = listOf(neteaseEnabled, qqEnabled, youtubeEnabled).count { it } >= 2
    var entered by remember { mutableStateOf(false) }
    // 0.1.18 第 6 项：进场淡入走 effects spec（之前手写 tween(300)）。
    val enterAlpha by animateFloatAsState(if (entered) 1f else 0f, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "homeEnter")
    LaunchedEffect(Unit) { entered = true }
    // Round 3 item 20: track the section titles' top edges; the last one
    // at or above the top bar's bottom edge owns the floating title.
    val density = LocalDensity.current
    var playlistsTitleY by remember { mutableStateOf(Float.MAX_VALUE) }
    var newSongsTitleY by remember { mutableStateOf(Float.MAX_VALUE) }
    // 本轮（0124）第 23 项：主页推荐块顺序（LazyColumn 作用域不是
    // Composable，状态必须提到这里）。
    var blockOrder by remember { mutableStateOf(HomeBlockOrder.apply(vm.store.homeBlockOrder)) }
    val topBarBottomPx = with(density) { topPadding.toPx() }
    // 0127 第 9 条：标题文案先在组合作用域解析（derivedStateOf
    // 的 lambda 不是组合作用域，不能直接调 stringResource）。
    val sectionTitleNewSongs = stringResource(R.string.home_recommend_songs)
    val sectionTitlePlaylists = stringResource(R.string.home_recommend_playlists)
    val sectionTitleHome = stringResource(R.string.home_title)
    val activeSection by remember {
        derivedStateOf {
            when {
                newSongsTitleY <= topBarBottomPx -> sectionTitleNewSongs
                playlistsTitleY <= topBarBottomPx -> sectionTitlePlaylists
                else -> sectionTitleHome
            }
        }
    }
    LaunchedEffect(activeSection) { onSectionTitle(activeSection) }
    val hasContent = home.daily.isNotEmpty() || home.radar.isNotEmpty() ||
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
    // 本轮（0124）第 1 项：自研回弹已移除，列表回到平台默认 overscroll
    // （主页顶部仍有下拉刷新 PullToRefreshBox，互不影响）。
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
                    Text(stringResource(R.string.home_connect_failed_netease), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.common_check_network_retry),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { vm.refreshHome() }, Modifier.height(56.dp)) { Text(stringResource(R.string.common_retry)) }
                    FilledTonalButton(onClick = onOpenLogin, Modifier.height(56.dp)) { Text(stringResource(R.string.home_go_login)) }
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
            // 0.1.21 口径（覆盖 0.1.20 第 5 项）：本地模式主页只有本地
            // 三桶歌单——NeteaseMusic / QQMusic 装各自已导入全部（虚拟
            // 歌单 -7 / -8，0 首也显示卡片）；Device 歌单（-9）只在
            // Device 桶有歌时出现。歌单名按全局文本规范（标题英文、
            // 作者字段写中文，见 AppViewModel.localBucketPlaylist）。
            // 随机歌单 / 重温回顾从本地主页撤下（普通模式主页与回顾
            // 功能本身不动）；平铺的本地歌曲列表也不再上主页。
            item {
                val neteaseLocalCount = localTracks.count {
                    com.tokimusic.app.data.local.localBucketOf(it) == com.tokimusic.app.data.local.LocalBucket.NETEASE
                }
                val qqLocalCount = localTracks.count {
                    com.tokimusic.app.data.local.localBucketOf(it) == com.tokimusic.app.data.local.LocalBucket.QQ
                }
                val deviceLocalCount = localTracks.count {
                    com.tokimusic.app.data.local.localBucketOf(it) == com.tokimusic.app.data.local.LocalBucket.DOWNLOAD
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LocalGeneratedCard(
                        title = "NeteaseMusic",
                        caption = stringResource(R.string.home_device_song_count, neteaseLocalCount),
                        modifier = Modifier.weight(1f),
                    ) { onOpenPlaylist(com.tokimusic.app.ui.LOCAL_NETEASE_PLAYLIST_ID) }
                    LocalGeneratedCard(
                        title = "QQMusic",
                        caption = stringResource(R.string.home_device_song_count, qqLocalCount),
                        modifier = Modifier.weight(1f),
                    ) { onOpenPlaylist(com.tokimusic.app.ui.LOCAL_QQ_PLAYLIST_ID) }
                    if (deviceLocalCount > 0) {
                        LocalGeneratedCard(
                            title = "Device",
                            caption = stringResource(R.string.home_device_song_count, deviceLocalCount),
                            modifier = Modifier.weight(1f),
                        ) { onOpenPlaylist(com.tokimusic.app.ui.LOCAL_DOWNLOAD_PLAYLIST_ID) }
                    }
                }
            }
        }
        val showDaily = neteaseEnabled && (!home.loggedIn || home.daily.isNotEmpty())
        val showRadar = neteaseEnabled && (!home.loggedIn || home.radar.isNotEmpty())
        // 0.1.15 第 15 项：QQ 每日推荐 / 私人雷达（只在对应列表非空时
        // 出现。每日需 QQ 登录，未登录时 qqDaily 为空所以不显示；雷达
        // 匿名可用。0.1.18 第 8 项：QQ 两张卡改名 Daily 30 / For You；
        // 点开走虚拟歌单 -3（QQ 每日）/ -4（QQ 雷达）。）
        val showQqDaily = qqEnabled && home.qqDaily.isNotEmpty()
        val showQqRadar = qqEnabled && home.qqRadar.isNotEmpty()
        // 本轮（0124）第 23 项：四个推荐块（每日推荐 / 私人雷达 /
        // Daily 30 / For You）合并成一个可重排区——长按拖动换位，
        // 顺序存 SettingsStore（HomeBlockOrder）；隐藏的块不渲染，
        // 存储顺序对可见块生效。其他分区（最近播放/推荐歌单/新歌/
        // 回顾/音乐记忆）不参与，位置不变。
        val visibleBlocks = blockOrder.filter { key ->
            when (key) {
                HomeBlockOrder.DAILY -> showDaily
                HomeBlockOrder.RADAR -> showRadar
                HomeBlockOrder.QQ_DAILY -> showQqDaily
                HomeBlockOrder.QQ_RADAR -> showQqRadar
                else -> false
            }
        }
        if (visibleBlocks.isNotEmpty()) {
            item {
                ReorderableBlockGrid(
                    keys = visibleBlocks,
                    onOrderCommitted = { newVisibleOrder ->
                        val merged = HomeBlockOrder.mergeVisible(blockOrder, newVisibleOrder)
                        blockOrder = merged
                        vm.store.homeBlockOrder = HomeBlockOrder.serialize(merged)
                    },
                ) { key ->
                    when (key) {
                        HomeBlockOrder.DAILY -> RecommendCard(
                            title = stringResource(R.string.home_daily),
                            songs = home.daily,
                            loggedIn = home.loggedIn,
                            onLogin = onOpenLogin,
                            onOpen = { onOpenPlaylist(-1L) },
                            hazeState = hazeState,
                            showSourceBadge = bothEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HomeBlockOrder.RADAR -> RecommendCard(
                            title = stringResource(R.string.home_radar),
                            songs = home.radar,
                            loggedIn = home.loggedIn,
                            onLogin = onOpenLogin,
                            onOpen = { onOpenPlaylist(-2L) },
                            hazeState = hazeState,
                            showSourceBadge = bothEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HomeBlockOrder.QQ_DAILY -> RecommendCard(
                            title = "Daily 30",
                            songs = home.qqDaily,
                            loggedIn = true,
                            onLogin = onOpenLogin,
                            onOpen = { onOpenPlaylist(-3L) },
                            hazeState = hazeState,
                            showSourceBadge = true,
                            source = MusicSource.QQ,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        else -> RecommendCard(
                            title = "For You",
                            songs = home.qqRadar,
                            loggedIn = true,
                            onLogin = onOpenLogin,
                            onOpen = { onOpenPlaylist(-4L) },
                            hazeState = hazeState,
                            showSourceBadge = true,
                            source = MusicSource.QQ,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        // 本轮（0127）第 17 条：主页「最近播放」区已整段删除（原 0124
        // 第 22 项的本机 PlayEventLog 横排卡）。资料库「歌曲」页的
        // 最近播放是另一套（MineUiState.recent + 资料库自己的列表），
        // 不受影响；回顾 / 音乐记忆区（同为 PlayEventLog 数据）保留。
        // Sixth round, batch 2, item 33 (overrides item 8's 2 rows):
        // recommended playlists in a 3-row horizontal grid, each card
        // badged with its source when both are on. 0.1.18 第 14 项：
        // 两源歌单 round-robin 交错进同一网格，不再网易云一块、QQ 一块。
        val allPlaylists = interleavePlaylists(home.playlists, home.qqPlaylists)
        if (allPlaylists.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_recommend_playlists),
                    Modifier.padding(horizontal = 24.dp)
                        .onGloballyPositioned { playlistsTitleY = it.positionInRoot().y },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                LazyHorizontalGrid(
                    // 本轮（0124）第 12 项：推荐歌单改 2 行（行高与 3 行时
                    // 同口径：(644 − 2×16) / 3 = 204/行 → 2 行 = 424dp）。
                    rows = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(424.dp),
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
        // Sixth round, batch 2, items 8 + 21: recommended new songs.
        // 0.1.18 第 2 项：改回多列横向网格（Fixed(6) 混源一网格）。
        // 0.1.19 第 1 项：分两个板块——先网易云、后 QQ，各带源小标题，
        // 每块 3 行网格（NewSongsGrid Fixed(3)）；某源无歌（未启用时 VM
        // 返回空）整块隐藏；大节标题与顶栏标题追踪（newSongsTitleY）不变。
        if (home.newSongs.isNotEmpty() || home.qqNewSongs.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_recommend_songs),
                    Modifier.padding(horizontal = 24.dp)
                        .onGloballyPositioned { newSongsTitleY = it.positionInRoot().y },
                    style = MaterialTheme.typography.titleLarge,
                )
                if (home.newSongs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    // 0.1.20 第 3 项：源小标题改英文原名（Netease Music / QQ Music）。
                    NewSongsSourceLabel("NeteaseMusic")
                    Spacer(Modifier.height(8.dp))
                    NewSongsGrid(home.newSongs, vm, onOpenArtist, onOpenQqArtist)
                }
                if (home.qqNewSongs.isNotEmpty()) {
                    Spacer(Modifier.height(if (home.newSongs.isNotEmpty()) 20.dp else 8.dp))
                    NewSongsSourceLabel("QQMusic")
                    Spacer(Modifier.height(8.dp))
                    NewSongsGrid(home.qqNewSongs, vm, onOpenArtist, onOpenQqArtist)
                }
            }
        }
        // 0.1.20 第 6 项：回顾歌单——过去月份的月度回顾（数据来自
        // PlayEventLog，与算法推荐共用同一批事件；每月不同歌曲 ≥10
        // 才生成，VM 已过滤）。卡片与每日推荐同版式（200/260）：封面
        // 是音乐记忆生成封面（0125 第 6 项：同类色 45° 渐变 + 大字
        // OCT'26 + 副标「音乐记忆 · M月」），点开走虚拟歌单详情。
        if (home.retrospectives.isNotEmpty()) {
            item {
                Text(stringResource(R.string.home_retrospective), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(home.retrospectives, key = { it.first }) { (monthKey, songs) ->
                        RetrospectiveCard(monthKey, songs) {
                            onOpenPlaylist(com.tokimusic.app.player.Retrospective.playlistId(monthKey))
                        }
                    }
                }
            }
        }
        // 音乐记忆（0124 第 11 项起；0127 第 18 条改生命周期）：
        // 当前月回顾歌单（作者 TokiMusic，PlayEventLog 按月派生，
        // VM 的 refreshRetrospectives 算出，新播放事件落库即刷新——
        // 当月实时更新；跨月后本卡自动换到新月，旧月转入上方「回顾」
        // 并冻结）。当月无播放记录时显示空态卡（不编造歌曲、不可点）。
        item {
            Text(stringResource(R.string.home_memory), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (home.memorySongs.isNotEmpty() && home.memoryMonthKey != 0) {
                // 本轮（0125）第 6 项：音乐记忆改歌单卡形式——生成
                // 渐变封面宽卡（与回顾卡同款封面），点开走歌单详情。
                MemoryCoverCard(
                    monthKey = home.memoryMonthKey,
                    trackCount = home.memorySongs.size,
                    wide = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).aspectRatio(2.1f),
                ) {
                    onOpenPlaylist(com.tokimusic.app.player.Retrospective.memoryPlaylistId(home.memoryMonthKey))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(stringResource(R.string.home_memory_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.home_no_play_records),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(listOf(tokiPageColor(), Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, tokiPageColor())),
            ),
        )
    }
    }
}


/** 推荐新歌分源小标题（0.1.19 第 1 项；0.1.20 第 3 项改英文原名
 * "Netease Music" / "QQ Music"）。 */
@Composable
private fun NewSongsSourceLabel(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 24.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Recommended new songs, one source block (0.1.19 第 1 项分源；
 * 0.1.20 第 3 项每块 3 行 → 5 行（Fixed(5)、高 220dp → 372dp）。一列
 * 是一整行宽的 SongRow（屏宽减左右 24dp 边距）；来源 icon 无背景
 * （SourceIcon，品牌色 tint）放在歌名文字之后（SongRow titleTrailing
 * 槽），边长 = 歌名行高（titleMedium lineHeight，与歌名等高）；尾部
 * 只留圆形播放钮。 */
@Composable
private fun NewSongsGrid(songs: List<Song>, vm: AppViewModel, onOpenArtist: (Long) -> Unit, onOpenQqArtist: (String) -> Unit = {}) {
    // 0.1.15 第 2 项：QQ 新歌的歌手名按 mid 进 QQ 歌手页（按行所在
    // 列表分流——0.1.19 起每块是单源列表，逻辑不变）。
    val openArtistForRows: (Long) -> Unit = { id -> resolveArtistTap(songs, id, onOpenArtist, onOpenQqArtist) }
    // 0127 第 9 条：播放上下文文案在组合作用域先解析（点击 lambda 里不能调 stringResource）。
    val recommendPlayContext = stringResource(R.string.home_recommend_songs)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // 来源 icon 与歌名等高：边长取 titleMedium 的行高（默认排版 24sp →
    // 24dp）；行高未指定（value 为 NaN）时回落 24。
    val titleLineHeight = MaterialTheme.typography.titleMedium.lineHeight
    val titleIconSize = if (titleLineHeight.value.isFinite()) {
        with(LocalDensity.current) { titleLineHeight.toDp().value.toInt() }
    } else {
        24
    }
    LazyHorizontalGrid(
        // 本轮（0124）第 12 项：单源改 3 行（原 5 行；行高同口径：
        // (372 − 4×8) / 5 = 68/行 → 3 行 = 3×68 + 2×8 = 220dp）。
        rows = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 单源列表内 id 唯一（网易云真实 id / QQ 合成 id 各自不重复），key 用下标保险。
        items(count = songs.size, key = { it }) { index ->
            val song = songs[index]
            Box(Modifier.width(screenWidth - 48.dp)) {
                SongRow(
                    song,
                    onOpenArtist = openArtistForRows,
                    onLongPress = { vm.openSongActions(it) },
                    horizontalPadding = 0.dp,
                    titleTrailing = { SourceIcon(song.source, size = titleIconSize) },
                    trailingAction = { SmallRoundPlayButton { vm.play(songs, index, recommendPlayContext) } },
                ) { vm.play(songs, index, recommendPlayContext) }
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
        Text(stringResource(R.string.home_local_music), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.home_local_both_sources_off),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasPermission) {
            FilledTonalButton(onClick = onRequestPermission, Modifier.height(56.dp)) {
                Text(stringResource(R.string.home_local_grant))
            }
        } else if (!hasSongs) {
            Text(
                stringResource(R.string.home_local_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onRescan, Modifier.height(56.dp)) { Text(stringResource(R.string.home_local_rescan)) }
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
    // 0.1.21 第 4 项：玻璃固定开启，模糊只看设备采样能力。
    val blurOn = com.tokimusic.app.ui.theme.TokiBlurCapability.canSampleInWindow
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
                        // 本轮（0124）第 24 项：有封面主题色时，下半改走
                        // 主题色淡出（每张卡自己的封面色，下缘最浓、
                        // 中部渐隐），压住封面让底部文字读得清；无封面色
                        // 时回退原中性黑 scrim（行为不变）。
                        if (coverColors != null) {
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.5f to Color.Transparent,
                                    1f to com.tokimusic.app.ui.theme.coverBottomFadeColor(
                                        coverColors.top, coverColors.bottom,
                                        lightMode = !com.tokimusic.app.ui.theme.LocalDarkTheme.current,
                                    ),
                                ),
                            )
                        } else {
                            Brush.verticalGradient(
                                if (blurOn) listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.32f))
                                else listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.8f)),
                            )
                        },
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
                        stringResource(R.string.home_login_to_view),
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
    // 0127 第 9 条：分隔符与「等」后缀随 App 语言（非组合作用域，走 AppStrings）。
    val separator = AppStrings.getOrFallback(R.string.home_artist_separator, "，")
    return if (artists.size > 4) {
        artists.take(4).joinToString(separator) + AppStrings.getOrFallback(R.string.home_more_artists_suffix, "等")
    } else {
        artists.joinToString(separator)
    }
}

/**
 * 本地模式生成卡（0.1.20 第 5 项）：与每日推荐卡同版式（200/260），
 * 无封面图——用主题色渐变兜底，左下标题 + 说明。
 */
@Composable
private fun LocalGeneratedCard(title: String, caption: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(200f / 260f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 回顾歌单卡（0.1.20 第 6 项）：与每日推荐卡同版式（200/260），
 * 内容走音乐记忆生成封面（本轮 0125 第 6 项，见 [MemoryCoverCard]）。
 */
@Composable
private fun RetrospectiveCard(monthKey: Int, songs: List<Song>, onClick: () -> Unit) {
    MemoryCoverCard(
        monthKey = monthKey,
        trackCount = songs.size,
        wide = false,
        modifier = Modifier.height(195.dp).aspectRatio(200f / 260f),
        onClick = onClick,
    )
}

/**
 * 音乐记忆生成封面歌单卡（本轮 0125 第 6 项）：封面不用图片——
 * Retrospective.coverColors 按年月种子选的同类色 3 色做 45° 渐变
 * （深/浅两套按主题选，同月恒定），封面文字按用户参考图：大字
 * "OCT'26"（coverTitle）+ 副标「音乐记忆 · 10月」（coverSubtitle），
 * 左下小字「N 首 · TokiMusic」（作者口径，用户 14:02 补充）。
 * wide = 主页音乐记忆入口的宽卡；窄版是回顾横排卡。
 */
@Composable
private fun MemoryCoverCard(
    monthKey: Int,
    trackCount: Int,
    wide: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val darkTheme = com.tokimusic.app.ui.theme.LocalDarkTheme.current
    val colors = com.tokimusic.app.player.Retrospective.coverColors(monthKey, darkTheme)
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        colors,
                        start = androidx.compose.ui.geometry.Offset.Zero,
                        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    ),
                ),
            )
            Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text(
                    com.tokimusic.app.player.Retrospective.coverTitle(monthKey),
                    style = (if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium).copy(
                        fontFamily = com.tokimusic.app.ui.theme.InterFamily,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.3f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                            blurRadius = 8f,
                        ),
                    ),
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    com.tokimusic.app.player.Retrospective.coverSubtitle(monthKey, stringResource(R.string.memory_cover_subtitle)),
                    style = if (wide) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                )
            }
            Text(
                stringResource(R.string.home_memory_creator_count, trackCount),
                Modifier.align(Alignment.BottomStart).padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
    }
}

/**
 * 本轮（0127）第 19 条：推荐歌单的刷新窗口（纯函数，JVM 单测覆盖）。
 * 根因：网易云 personalized 推荐池由服务端按账号周期性生成，同一
 * 请求每次返回同一有序池；客户端原先只取池的前 12 个（limit=12），
 * 所以下拉刷新虽然每次都真发请求，看到的永远是同一组。改为拉满
 * 池（limit=30）后按手动刷新次数滚动窗口：第 rotation 次刷新取
 * 第 rotation 个窗口（步长 = window，对池长取模环绕，窗口可跨
 * 池尾接回池头）；池不大于窗口时原样返回（无更多可刷，如实呈现）。
 */
internal fun <T> rotateRecommendWindow(pool: List<T>, rotation: Int, window: Int): List<T> {
    if (window <= 0 || pool.size <= window) return pool
    val offset = ((rotation.toLong() * window) % pool.size).toInt()
    if (offset == 0) return pool.take(window)
    return (pool.drop(offset) + pool.take(offset)).take(window)
}

/**
 * 本轮（0127）第 19 条：整表滚动（QQ 推荐歌单用，纯函数，JVM 单测
 * 覆盖）——不截断，只按手动刷新次数把列表左旋 rotation × step
 * 位（对表长取模），每次刷新起点不同、内容全保留。
 */
internal fun <T> rotateRecommendList(list: List<T>, rotation: Int, step: Int): List<T> {
    if (list.size <= 1 || step <= 0) return list
    val offset = ((rotation.toLong() * step) % list.size).toInt()
    if (offset == 0) return list
    return list.drop(offset) + list.take(offset)
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

/**
 * 主页推荐块可重排网格（本轮 0124 第 23 项）：两列排布，长按任意块
 * 拾起（放大 + 阴影 + 跟手位移），拖过相邻块中心即换位，松手落位
 * 并回调提交顺序（父级持久化）。普通点按/块内滚动不受影响（长按
 * 才进拖动态；拖动事件被消费，外层 LazyColumn 不跟着滚）。
 * 本轮（0125）第 5 项：被换位的块不再瞬移——四块同处一个
 * LazyColumn item 内，Lazy 的 animateItemPlacement 用不上，改走
 * FLIP 式动画：换位瞬间按各块新旧槽位差给它补一个起始偏移
 * （视觉位置与换位前完全一致），再用弹簧把偏移收回 0，块便平滑
 * 滑到新槽位。槽位坐标按统一格尺寸推算（与拖动换位的步进同
 * 口径）。拖动块本身不走此动画，继续 1:1 跟手。
 */
@Composable
private fun ReorderableBlockGrid(
    keys: List<String>,
    onOrderCommitted: (List<String>) -> Unit,
    cellContent: @Composable (String) -> Unit,
) {
    var order by remember { mutableStateOf(keys) }
    LaunchedEffect(keys) {
        if (order.toSet() != keys.toSet()) order = keys
    }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var cellSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
    val scope = rememberCoroutineScope()
    // 每块一个 FLIP 偏移动画（仅非拖动块用；拖动块走 dragOffset 跟手）。
    val shifts = remember { mutableStateMapOf<String, Animatable<androidx.compose.ui.geometry.Offset, AnimationVector2D>>() }
    fun shiftFor(key: String) = shifts.getOrPut(key) {
        Animatable(androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset.VectorConverter)
    }
    // 槽位左上角坐标（统一格尺寸推算，与拖动步进同口径）。
    fun slotOffset(index: Int, stepX: Float, stepY: Float) =
        androidx.compose.ui.geometry.Offset((index % 2) * stepX, (index / 2) * stepY)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for (rowKeys in order.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (key in rowKeys) {
                    val isDragging = draggingKey == key
                    val shift = shiftFor(key)
                    Box(
                        Modifier.weight(1f)
                            .onSizeChanged { if (it.width > 0) cellSize = it }
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 16.dp.toPx()
                                } else {
                                    translationX = shift.value.x
                                    translationY = shift.value.y
                                }
                            }
                            .pointerInput(key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingKey = key
                                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                    },
                                    onDragEnd = {
                                        onOrderCommitted(order)
                                        draggingKey = null
                                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                    },
                                    onDragCancel = {
                                        onOrderCommitted(order)
                                        draggingKey = null
                                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        if (cellSize != androidx.compose.ui.unit.IntSize.Zero) {
                                            val idx = order.indexOf(key)
                                            if (idx >= 0) {
                                                val col = idx % 2
                                                val row = idx / 2
                                                val stepX = cellSize.width + spacingPx
                                                val stepY = cellSize.height + spacingPx
                                                val targetCol = (col + (dragOffset.x / stepX).roundToInt()).coerceIn(0, 1)
                                                val targetRow = (row + (dragOffset.y / stepY).roundToInt())
                                                    .coerceIn(0, (order.size - 1) / 2)
                                                val target = (targetRow * 2 + targetCol).coerceAtMost(order.size - 1)
                                                if (target != idx) {
                                                    val oldOrder = order
                                                    val newOrder = HomeBlockOrder.move(order, idx, target)
                                                    // FLIP 让位动画：被挤开的块先补上
                                                    // 新旧槽位差（视觉位置不变），再滑回 0。
                                                    for (k in newOrder) {
                                                        if (k == key) continue
                                                        val oldIdx = oldOrder.indexOf(k)
                                                        val newIdx = newOrder.indexOf(k)
                                                        if (oldIdx >= 0 && newIdx >= 0 && oldIdx != newIdx) {
                                                            val anim = shiftFor(k)
                                                            val delta = slotOffset(oldIdx, stepX, stepY) - slotOffset(newIdx, stepX, stepY)
                                                            scope.launch {
                                                                anim.snapTo(anim.value + delta)
                                                                anim.animateTo(
                                                                    androidx.compose.ui.geometry.Offset.Zero,
                                                                    spring(
                                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                                        stiffness = Spring.StiffnessMediumLow,
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                    }
                                                    order = newOrder
                                                    dragOffset -= androidx.compose.ui.geometry.Offset(
                                                        (targetCol - col) * stepX,
                                                        (targetRow - row) * stepY,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                            },
                    ) {
                        cellContent(key)
                    }
                }
                if (rowKeys.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
