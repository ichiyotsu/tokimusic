package com.tokimusic.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tokimusic.app.R
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.PlaylistGroup
import com.tokimusic.app.data.model.groupPlaylists
import com.tokimusic.app.ui.albums.AlbumDetailScreen
import com.tokimusic.app.ui.artist.ArtistScreen
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.FloatingNavBar
import com.tokimusic.app.ui.components.OverlayTitleHeader
import com.tokimusic.app.ui.components.SongActionsSheet
import com.tokimusic.app.ui.components.TokiGlassIconButton
import com.tokimusic.app.ui.components.TokiLongPressLabel
import com.tokimusic.app.ui.components.TokiSideActionButton
import com.tokimusic.app.ui.components.navEntries
import com.tokimusic.app.ui.home.HomeScreen
import com.tokimusic.app.ui.library.LibraryScreen
import com.tokimusic.app.ui.library.LibrarySection
import com.tokimusic.app.ui.stats.ListeningStatsScreen
import com.tokimusic.app.ui.stats.NeteaseReportScreen
import com.tokimusic.app.ui.player.MiniPlayer
import com.tokimusic.app.ui.player.PlayerScreen
import com.tokimusic.app.ui.playlists.PlaylistDetailScreen
import com.tokimusic.app.ui.search.SearchSheet
import com.tokimusic.app.ui.settings.SettingsSheet
import com.tokimusic.app.ui.theme.ThemePreferences
import com.tokimusic.app.ui.theme.TokiGlassFade
import com.tokimusic.app.ui.theme.TokiMusicTheme
import com.tokimusic.app.ui.theme.chromeBlurActive
import com.tokimusic.app.ui.theme.tokiGlass
import com.tokimusic.app.ui.theme.rememberCoverColors
import com.tokimusic.app.ui.theme.tokiContentChromeStrong
import com.tokimusic.app.ui.theme.tokiGlassFade
import com.tokimusic.app.ui.theme.TokiBlack
import com.tokimusic.app.ui.theme.tokiPageBackground
import com.tokimusic.app.ui.theme.tokiSpace
import com.tokimusic.app.BuildConfig
import com.tokimusic.app.update.AppOptimizer
import com.tokimusic.app.update.LatestRelease
import com.tokimusic.app.update.OptimizeResult
import com.tokimusic.app.update.OptimizeSheet
import com.tokimusic.app.update.OptimizeSnapshot
import com.tokimusic.app.update.UpdateCheckResult
import com.tokimusic.app.update.UpdateChecker
import com.tokimusic.app.update.UpdatePrompt
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TokiMusicApp(
    // 第六轮第 29 项：从通知栏媒体卡片点进时直达播放器页——冷启动用初始参数，
    // singleTop 复用时走 openPlayerRequests（MainActivity.onNewIntent 发出）。
    openPlayerInitially: Boolean = false,
    openPlayerRequests: SharedFlow<Boolean>? = null,
) {
    val vm: AppViewModel = viewModel()
    // One-off messages from the ViewModel (login expired, unplayable song,
    // like failed…): show once as a toast, then consume.
    val context = LocalContext.current
    val userMessage by vm.userMessage.collectAsState()
    LaunchedEffect(userMessage) {
        userMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.consumeMessage() }
    }
    // 第五轮第 15 项：每次冷启动都查一次更新（去掉每天一次节流；设置 →
    // 通用可关“启动时检查更新”）。LaunchedEffect(Unit) 只在冷启动跑一次，
    // 从后台切回前台不重复查。有新版时弹底部弹窗（UpdatePrompt），不自动下载。
    // 第六轮第 9 项：延后到 5 秒，不与首页请求抢带宽。
    var pendingUpdate by remember { mutableStateOf<LatestRelease?>(null) }
    LaunchedEffect(Unit) {
        delay(5000)
        if (!vm.store.checkUpdateOnLaunch) return@LaunchedEffect
        when (val r = UpdateChecker.checkForUpdate(vm.versionName)) {
            is UpdateCheckResult.UpdateAvailable -> pendingUpdate = r.release
            else -> Unit
        }
    }
    // 第五轮第 15 项：更新完首次启动（versionCode 变大）时，先算优化前数值
    // 再弹二次确认问是否优化；确认后清理并展示优化后数值，拒绝则本次跳过
    // （记下当前 versionCode，下次升版再问）。首次记录（0）时只记数不弹。
    var optimizeSnapshot by remember { mutableStateOf<OptimizeSnapshot?>(null) }
    var optimizeResult by remember { mutableStateOf<OptimizeResult?>(null) }
    var optimizeCleaning by remember { mutableStateOf(false) }
    val optimizeScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val lastCode = vm.store.lastLaunchedVersionCode
        val currentCode = BuildConfig.VERSION_CODE
        if (AppOptimizer.isUpgrade(lastCode, currentCode)) {
            optimizeSnapshot = AppOptimizer.measure(context)
        } else if (lastCode == 0) {
            vm.store.lastLaunchedVersionCode = currentCode
        }
    }
    // 拒绝/上滑关闭都算“本次跳过”：记下当前 versionCode，下次升版再问。
    val dismissOptimize: () -> Unit = {
        vm.store.lastLaunchedVersionCode = BuildConfig.VERSION_CODE
        optimizeSnapshot = null
        optimizeResult = null
    }
    var theme by remember {
        mutableStateOf(
            ThemePreferences(
                reduceMotion = vm.store.reduceMotion,
                backdropBlur = vm.store.backdropBlur,
            ),
        )
    }
    val updateTheme: (ThemePreferences) -> Unit = { next ->
        theme = next
        vm.store.reduceMotion = next.reduceMotion
        vm.store.backdropBlur = next.backdropBlur
    }

    TokiMusicTheme(
        reduceMotion = theme.reduceMotion,
    ) {
        var dest by remember { mutableStateOf(Dest.Home) }
        var playlistId by remember { mutableStateOf<Long?>(null) }
        var playlistTitle by remember { mutableStateOf("歌单") }
        // Round 3 item 12: artist + album detail pages (same level as
        // playlistId). Stacking: opening an artist from a playlist keeps
        // the playlist underneath (back returns to it); opening an album
        // from an artist keeps the artist underneath. The pageKey/onBack
        // priority is album > artist > playlist > search > stats > section.
        var artistId by remember { mutableStateOf<Long?>(null) }
        var artistTitle by remember { mutableStateOf("歌手") }
        var albumId by remember { mutableStateOf<Long?>(null) }
        var albumTitle by remember { mutableStateOf("专辑") }
        // 0.1.15 第 2 项：QQ 歌手 / 专辑页按 mid 路由（与网易云
        // 的 Long id 不是同一空间，另起两个导航状态；QQ 歌手与
        // 网易云歌手互斥——打开一个时清掉另一个，下同专辑）。
        var qqArtistMid by remember { mutableStateOf<String?>(null) }
        var qqAlbumMid by remember { mutableStateOf<String?>(null) }
        // Round 3 item 2: the open Library section lives here (with
        // playlistId) so second-level pages get the floating title + back.
        var librarySection by remember { mutableStateOf<LibrarySection?>(null) }
        // Round 3 item 13: listening-stats second-level page.
        var statsOpen by remember { mutableStateOf(false) }
        // 0.1.17 第 17 项：网易云收听报告二级页（资料库网易云听歌时长入口）。
        var neteaseReportOpen by remember { mutableStateOf(false) }
        // Sixth round, batch 2, item 10: the stats page's 〈!〉 annotation.
        var statsNoteVisible by remember { mutableStateOf(false) }
        LaunchedEffect(statsOpen) { if (!statsOpen) statsNoteVisible = false }
        // Round 3 item 20: Home's floating title follows the scrolled section.
        var homeSectionTitle by remember { mutableStateOf("主页") }
        // 第六轮第 29 项：播放器页开关可保存——进程重建 / 从通知点进后仍回到
        // 播放器页；歌词视图与沉浸态在 PlayerScreen 里同样可保存。
        var showPlayer by rememberSaveable { mutableStateOf(openPlayerInitially) }
        LaunchedEffect(openPlayerRequests) {
            openPlayerRequests?.collect { if (it) showPlayer = true }
        }
        // 第六轮第 36 项：歌手页是否从播放器页点开——是则返回时回到播放器页
        // （播放器与歌手页都是二级页，导航栈顺序：播放器在下、歌手页在上）。
        var artistFromPlayer by remember { mutableStateOf(false) }
        val closeArtist: () -> Unit = {
            artistId = null
            if (artistFromPlayer) { artistFromPlayer = false; showPlayer = true }
        }
        // 0.1.15 第 2 项：QQ 歌手页的返回（与 closeArtist 同款：
        // 从播放器点开时返回播放器页）。
        val closeQqArtist: () -> Unit = {
            qqArtistMid = null
            if (artistFromPlayer) { artistFromPlayer = false; showPlayer = true }
        }
        // 设置页：底部弹窗（第二轮第 4 项，TokiNote SheetContainer 配方），
        // 不再是独立全屏页；搜索自第五轮第 16 项起改回底部弹窗（推翻第三轮
        // 第 11 项的独立页），状态与 playlistId 同级，弹窗浮在页面与底栏之上。
        var showSettings by remember { mutableStateOf(false) }
        var searchSheetOpen by remember { mutableStateOf(false) }
        val density = LocalDensity.current
        // 第二轮第 8 项：歌单详情的悬浮标题只在标题行滑出可视区后出现
        // （淡入 + 上移动画）；进新歌单时先复位为隐藏。第三轮第 5 项起，
        // 该动画只作用在标题 chip 上，返回 chip 一直显示。歌手/专辑页
        // 同款规则（第 12 项），各有自己的可见标志；headerAlpha 取最
        // 上层详情页的那一个。
        var detailHeaderVisible by remember { mutableStateOf(false) }
        LaunchedEffect(playlistId) { detailHeaderVisible = false }
        var artistHeaderVisible by remember { mutableStateOf(false) }
        LaunchedEffect(artistId) { artistHeaderVisible = false }
        var albumHeaderVisible by remember { mutableStateOf(false) }
        LaunchedEffect(albumId) { albumHeaderVisible = false }
        // 0.1.15 第 2 项：QQ 歌手 / 专辑页的悬浮标题复位（与网易云同款）。
        LaunchedEffect(qqArtistMid) { artistHeaderVisible = false }
        LaunchedEffect(qqAlbumMid) { albumHeaderVisible = false }
        // 0.1.13 第 24 / 25 项：顶栏右侧的收藏 / 关注按钮态——歌单详情与
        // 歌手页通过 onFavoriteAction / onFollowAction 上报 (是否已选, 切换)；
        // 进新歌单 / 歌手时先复位（详情未加载完之前不显示旧按钮）。
        var playlistFavAction by remember { mutableStateOf<Pair<Boolean, () -> Unit>?>(null) }
        var artistFollowAction by remember { mutableStateOf<Pair<Boolean, () -> Unit>?>(null) }
        LaunchedEffect(playlistId) { playlistFavAction = null }
        LaunchedEffect(artistId) { artistFollowAction = null }
        LaunchedEffect(qqArtistMid) { artistFollowAction = null }
        val topDetailHeaderVisible = when {
            albumId != null -> albumHeaderVisible
            qqAlbumMid != null -> albumHeaderVisible
            artistId != null -> artistHeaderVisible
            qqArtistMid != null -> artistHeaderVisible
            playlistId != null -> detailHeaderVisible
            else -> true
        }
        // 0.1.18 第 6 项：标题显隐动画走 motionScheme（透明度 effects、
        // 位移 spatial；之前手写 tween(300)）。
        val scheme = MaterialTheme.motionScheme
        val headerAlpha by animateFloatAsState(
            targetValue = if (albumId == null && qqAlbumMid == null && artistId == null && qqArtistMid == null && playlistId == null || topDetailHeaderVisible) 1f else 0f,
            animationSpec = scheme.defaultEffectsSpec(),
            label = "detailHeaderAlpha",
        )
        val headerOffsetY by animateFloatAsState(
            targetValue = if (albumId == null && qqAlbumMid == null && artistId == null && qqArtistMid == null && playlistId == null || topDetailHeaderVisible) 0f else with(density) { -8.dp.toPx() },
            animationSpec = scheme.defaultSpatialSpec(),
            label = "detailHeaderOffset",
        )
        val profile by vm.profile.collectAsState()
        // 0.1.13 第 18 项：自定义头像（头像入口也显示自定义头像）。
        val customAvatarPath by vm.customAvatarPath.collectAsState()
        // 第六轮第 1 项：源启用开关（主页左上角下拉入口只在双源都启用时出现）。
        val neteaseEnabled by vm.neteaseEnabled.collectAsState()
        val qqEnabled by vm.qqEnabled.collectAsState()
        val hazeState = rememberHazeState()
        // 加强档磨砂（第二轮第 1 项）：导航胶囊 + 迷你播放器 + 悬浮标题共用。
        val barGlass = tokiContentChromeStrong(hazeState, theme.backdropBlur)

        val playerState by vm.player.state.collectAsState()
        // 第三轮第 21 项：播放器封面取色提升到这里——外层满屏 Box 用它画
        // 渐变（盖住 28dp 条带与底部圆角外的条带），状态栏图标深浅也跟随
        // 它；PlayerScreen 复用同一份取色，不再各自取色。
        val playerSong = playerState.current
        val playerCoverColors = rememberCoverColors(playerSong?.id ?: 0L, playerSong?.picUrl ?: "")
        val playerView = LocalView.current
        LaunchedEffect(showPlayer, playerCoverColors) {
            val window = (playerView.context as? Activity)?.window ?: return@LaunchedEffect
            // 播放器打开时按封面亮度切图标深浅（亮封面深色图标）；
            // 关闭后恢复——应用固定深色，状态栏图标为浅色。
            WindowInsetsControllerCompat(window, playerView).isAppearanceLightStatusBars =
                showPlayer && playerSong != null && playerCoverColors.isLight()
        }
        val pageTitle = when {
            showPlayer -> "正在播放"
            albumId != null -> albumTitle
            qqAlbumMid != null -> albumTitle
            artistId != null -> artistTitle
            qqArtistMid != null -> artistTitle
            playlistId != null -> playlistTitle
            statsOpen -> "听歌时长"
            neteaseReportOpen -> "收听报告"
            librarySection != null -> librarySection!!.title
            dest == Dest.Home -> homeSectionTitle
            else -> stringResource(navEntries.first { it.dest == dest }.label)
        }
        // The player is a second-level page: no top bar, no bottom bar, no
        // rail — but it now carries its own floating back chip (0.1.10: every
        // second-level page gets one). Tab pages (Home / Library)
        // draw the pinned overlay title too (round 2 item 3); playlist detail
        // keeps its overlay title for now — round 2 item 8 makes it
        // scroll-driven in phase B.
        val chromeVisible = !showPlayer
        val headerVisible = chromeVisible
        // 第五轮第 9 项（用户确认）：底栏隐藏所有二级页都一样做——导航胶囊
        // 只在三个 tab 页显示；MiniPlayer 仍留在底边（见 BottomChrome）。
        val isSecondLevel = albumId != null || qqAlbumMid != null || artistId != null || qqArtistMid != null || playlistId != null ||
            statsOpen || neteaseReportOpen || librarySection != null
        val bottomNavVisible = !showPlayer && !isSecondLevel
        val onBack: (() -> Unit)? = when {
            showPlayer -> ({ showPlayer = false })
            albumId != null -> ({ albumId = null })
            qqAlbumMid != null -> ({ qqAlbumMid = null })
            artistId != null -> closeArtist
            qqArtistMid != null -> closeQqArtist
            playlistId != null -> ({ playlistId = null })
            statsOpen -> ({ statsOpen = false })
            neteaseReportOpen -> ({ neteaseReportOpen = false })
            librarySection != null -> ({ librarySection = null })
            else -> null
        }
        BackHandler(enabled = showPlayer) { showPlayer = false }
        BackHandler(enabled = !showPlayer && albumId != null) { albumId = null }
        BackHandler(enabled = !showPlayer && albumId == null && qqAlbumMid != null) { qqAlbumMid = null }
        BackHandler(enabled = !showPlayer && albumId == null && qqAlbumMid == null && artistId != null) { closeArtist() }
        BackHandler(enabled = !showPlayer && albumId == null && qqAlbumMid == null && artistId == null && qqArtistMid != null) { closeQqArtist() }
        BackHandler(enabled = !showPlayer && albumId == null && qqAlbumMid == null && artistId == null && qqArtistMid == null && playlistId != null) { playlistId = null }
        BackHandler(enabled = !showPlayer && albumId == null && qqAlbumMid == null && artistId == null && qqArtistMid == null && playlistId == null && statsOpen) { statsOpen = false }
        BackHandler(enabled = !showPlayer && albumId == null && qqAlbumMid == null && artistId == null && qqArtistMid == null && playlistId == null && !statsOpen && neteaseReportOpen) { neteaseReportOpen = false }
        BackHandler(enabled = !showPlayer && albumId == null && qqAlbumMid == null && artistId == null && qqArtistMid == null && playlistId == null && !statsOpen && !neteaseReportOpen && librarySection != null) { librarySection = null }

        val contentGap = tokiSpace("lg")
        val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        // 第六轮第 12 项：tab 切换的方向滑入距离（24dp，按 tab 顺序方向）。
        val tabSlidePx = with(density) { 24.dp.roundToPx() }
        var topBarHeight by remember { mutableStateOf(0.dp) }
        var measuredChrome by remember { mutableStateOf(0.dp) }
        val topPadding = if (topBarHeight > 0.dp) topBarHeight else statusBarHeight + 64.dp
        val bottomPadding = measuredChrome + contentGap

        // Page identity drives the transitions: the player slides up from the
        // bottom, a playlist detail slides in from the right, tabs crossfade.
        // 第五轮第 1 项：资料库分段页也编进 pageKey（否则分段页进出没有
        // 动画触发点——pageKey 之前一直是 dest，分段切换不触发 AnimatedContent）。
        val pageKey: Any = when {
            showPlayer -> "player"
            albumId != null -> "album:$albumId"
            qqAlbumMid != null -> "qqalbum:$qqAlbumMid"
            artistId != null -> "artist:$artistId"
            qqArtistMid != null -> "qqartist:$qqArtistMid"
            playlistId != null -> "playlist:$playlistId"
            statsOpen -> "stats"
            neteaseReportOpen -> "netease-report"
            librarySection != null -> "section:$librarySection"
            else -> dest
        }

        BoxWithConstraints(Modifier.fillMaxSize().background(tokiPageBackground())) {
            val wide = usesNavigationRail(maxWidth.value)
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = chromeVisible && wide,
                    enter = slideInHorizontally(scheme.defaultSpatialSpec()) { -it } + fadeIn(scheme.defaultEffectsSpec()),
                    exit = slideOutHorizontally(scheme.defaultSpatialSpec()) { -it } + fadeOut(scheme.defaultEffectsSpec()),
                ) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        navEntries.forEach { entry ->
                            val label = stringResource(entry.label)
                            TokiLongPressLabel(label) {
                                NavigationRailItem(
                                    selected = dest == entry.dest,
                                    onClick = {
                                        dest = entry.dest
                                        playlistId = null
                                        artistId = null
                                        artistFromPlayer = false
                                        albumId = null
                                        // 大排查 B1：切 tab 时 QQ 歌手 / 专辑页同清——
                                        // 与底栏 onSelect（0.1.15 第 2 项）同款，之前
                                        // rail 这里漏了这两行，宽屏下在 QQ 歌手/专辑
                                        // 页点 tab 切不走，页还盖在上面。
                                        qqArtistMid = null
                                        qqAlbumMid = null
                                        librarySection = null
                                        statsOpen = false
                                        neteaseReportOpen = false
                                        searchSheetOpen = false
                                        showPlayer = false
                                    },
                                    icon = {
                                        Icon(
                                            if (dest == entry.dest) entry.selectedIcon else entry.icon,
                                            contentDescription = label,
                                        )
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                        // 宽屏：搜索也不在 tab 里，同样走独立按钮（打开底部弹窗）。
                        TokiLongPressLabel(stringResource(R.string.nav_search)) {
                            NavigationRailItem(
                                selected = false,
                                onClick = { searchSheetOpen = true },
                                icon = { Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.nav_search)) },
                                label = { Text(stringResource(R.string.nav_search)) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        topBar = {
                            if (headerVisible) {
                                OverlayTitleHeader(
                                    title = pageTitle,
                                    onBack = onBack,
                                    onHeightMeasured = { topBarHeight = it },
                                    backdropBlur = theme.backdropBlur,
                                    hazeState = hazeState,
                                    strong = true,
                                    // 第三轮第 5 项：淡入/上移只作用在标题 chip 上
                                    // （titleAlpha/titleOffsetY），返回 chip 一直显示。
                                    titleAlpha = headerAlpha,
                                    titleOffsetY = headerOffsetY,
                                    // 第五轮第 25 项：详情页（歌单/歌手/专辑）滚动后，悬浮标题
                                    // chip 加不透明底（页面底色 TokiBlack）——封面渐变
                                    // 的上端色会透过半透明磨砂让顶栏看起来还留着封面；
                                    // tab 页不传（标题常显，磨砂是设计的一部分）。
                                    // chip 本身随 titleAlpha 淡入，进页时看不到这层底。
                                    titleSolidColor = if (albumId != null || artistId != null || playlistId != null) TokiBlack else null,
                                    // 0.1.15 第 3 项：主页 / 资料库左上角的源切换
                                    // 入口已删——源开关只在设置里调。
                                    leading = null,
                                    // Tab 页：头像入口放标题行右侧（第 3 项）；资料库分段 /
                                    // 听歌时长不放，只留返回 chip；歌单详情页放收藏星星
                                    // （0.1.13 第 25 项），歌手页放关注按钮（第 24 项）。
                                    // 搜索是底部弹窗，不占标题位。
                                    // 第六轮第二批第 10 项：听歌时长页标题栏右侧放一个〈!〉，
                                    // 点开在 icon 下方显示悬浮注释（Netease x h）。
                                    trailing = when {
                                        statsOpen && playlistId == null && artistId == null && albumId == null && librarySection == null -> {
                                            {
                                                // 0.1.17 第 11 项：〈!〉改成返回 chip 同款
                                                // 磨砂圆钮（TokiGlassIconButton）。
                                                TokiGlassIconButton(
                                                    icon = Icons.Rounded.PriorityHigh,
                                                    label = "查看网易云统计估算",
                                                    backdropBlur = theme.backdropBlur,
                                                    hazeState = hazeState,
                                                    onClick = { statsNoteVisible = !statsNoteVisible },
                                                )
                                            }
                                        }
                                        // 0.1.13 第 25 项：歌单详情页顶栏右侧收藏星星
                                        // （单个 icon；详情未加载完时不显示）。
                                        // 0.1.14 第 5 项：改成返回 chip 同款磨砂圆钮。
                                        playlistId != null && artistId == null && albumId == null && playlistFavAction != null -> {
                                            {
                                                val (isFav, toggleFav) = playlistFavAction!!
                                                TokiGlassIconButton(
                                                    icon = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                    label = if (isFav) "取消收藏" else "收藏歌单",
                                                    backdropBlur = theme.backdropBlur,
                                                    hazeState = hazeState,
                                                    onClick = toggleFav,
                                                )
                                            }
                                        }
                                        // 0.1.13 第 24 项：歌手页顶栏右侧关注按钮
                                        // （单个 icon；未关注是加号人形，已关注是对勾）。
                                        // 0.1.14 第 5 项：改成返回 chip 同款磨砂圆钮。
                                        artistId != null && albumId == null && artistFollowAction != null -> {
                                            {
                                                val (isFollowed, toggleFollow) = artistFollowAction!!
                                                TokiGlassIconButton(
                                                    icon = if (isFollowed) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
                                                    label = if (isFollowed) "取消关注" else "关注",
                                                    backdropBlur = theme.backdropBlur,
                                                    hazeState = hazeState,
                                                    onClick = toggleFollow,
                                                )
                                            }
                                        }
                                        playlistId == null && artistId == null && albumId == null && librarySection == null && !statsOpen && !neteaseReportOpen -> {
                                            {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    TokiLongPressLabel(if (profile == null) "登录" else "账户与设置") {
                                                        IconButton(onClick = { showSettings = true }) {
                                                            Avatar(customAvatarPath.ifBlank { profile?.avatarUrl ?: "" }, 48)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        else -> null
                                    },
                                )
                            }
                        },
                    ) { _ ->
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .hazeSource(hazeState)
                                    .background(tokiPageBackground()),
                            ) {
                                AnimatedContent(
                                    targetState = pageKey,
                                    transitionSpec = {
                                        val toPlayer = targetState == "player"
                                        val fromPlayer = initialState == "player"
                                        // 第五轮第 1 项：二级页统一从右滑入（之前只覆盖
                                        // 歌单详情）；tab 间切换仍纯淡入淡出。二级页
                                        // 之间互切（如歌单 → 歌手）方向不明，走淡入淡出。
                                        fun isDetailKey(key: Any): Boolean {
                                            val s = key as? String ?: return false
                                            return s.startsWith("album:") || s.startsWith("qqalbum:") || s.startsWith("artist:") || s.startsWith("qqartist:") ||
                                                s.startsWith("playlist:") || s.startsWith("section:") ||
                                                s == "stats"
                                        }
                                        val toDetail = isDetailKey(targetState) && !isDetailKey(initialState) && initialState != "player"
                                        val fromDetail = isDetailKey(initialState) && !isDetailKey(targetState) && targetState != "player"
                                        // 第六轮第 12 项：tab 间切换按 tab 顺序方向滑入 24dp
                                        // + 淡入淡出（Home → Library 从右进、向左出，反向对
                                        // 称）；同 tab 重复点不触发（pageKey 不变）；从二级页
                                        // 返回仍走原有的从右滑入。
                                        val isTabSwitch = targetState is Dest && initialState is Dest && targetState != initialState
                                        val tabDir = if (isTabSwitch) {
                                            if ((targetState as Dest).ordinal > (initialState as Dest).ordinal) 1 else -1
                                        } else 0
                                        when {
                                            // 第六轮第 13 项：播放器面板从 MiniPlayer 处弹出——
                                            // 缩放 0.95→1 + 淡入 + 弹簧；关闭反向（缩放 1→0.95 +
                                            // 淡出，取代原来的底部滑出）。0.1.18 第 6 项：弹簧
                                            // 改 MD3E spatial spec（之前手写 spring(0.8, 500)）。
                                            toPlayer -> (scaleIn(scheme.defaultSpatialSpec(), initialScale = 0.95f) + fadeIn(scheme.defaultEffectsSpec())) togetherWith
                                                fadeOut(scheme.defaultEffectsSpec())
                                            fromPlayer -> fadeIn(scheme.defaultEffectsSpec()) togetherWith
                                                (scaleOut(scheme.defaultSpatialSpec(), targetScale = 0.95f) + fadeOut(scheme.defaultEffectsSpec()))
                                            toDetail -> (slideInHorizontally(scheme.defaultSpatialSpec()) { it } + fadeIn(scheme.defaultEffectsSpec())) togetherWith
                                                (fadeOut(scheme.defaultEffectsSpec()) + scaleOut(scheme.defaultSpatialSpec(), 0.98f))
                                            fromDetail -> (fadeIn(scheme.defaultEffectsSpec()) + scaleIn(scheme.defaultSpatialSpec(), 0.98f)) togetherWith
                                                (slideOutHorizontally(scheme.defaultSpatialSpec()) { it } + fadeOut(scheme.defaultEffectsSpec()))
                                            // 0.1.18 第 6 项：tab 滑入滑出走 spatial spec
                                            //（之前手写 tween(300)）。
                                            isTabSwitch -> (slideInHorizontally(scheme.defaultSpatialSpec()) { tabDir * tabSlidePx } + fadeIn(scheme.defaultEffectsSpec())) togetherWith
                                                (slideOutHorizontally(scheme.defaultSpatialSpec()) { -tabDir * tabSlidePx } + fadeOut(scheme.defaultEffectsSpec()))
                                            else -> fadeIn(scheme.defaultEffectsSpec()) togetherWith fadeOut(scheme.defaultEffectsSpec())
                                        }
                                    },
                                    label = "page",
                                ) { key ->
                                    when {
                                        // 第二轮第 2 项：播放器是圆角面板形态——顶部圆角加大、
                                        // 高度略低于全屏（顶部留 28dp），面板内重新排布封面与按钮。
                                        // 第三轮第 21 项：外层满屏 Box 先画一层同取色渐变，盖住
                                        // 28dp 条带与底部条带（面板圆角/间距不变），状态栏区域
                                        // 即被背景覆盖；面板内背景保留（双层同色，无色差）。
                                        key == "player" -> Box(
                                            Modifier.fillMaxSize()
                                                .then(if (playerSong != null) Modifier.background(playerCoverColors.toBrush()) else Modifier),
                                        ) {
                                            Box(
                                                Modifier.fillMaxSize()
                                                    .padding(top = 28.dp)
                                                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
                                            ) {
                                                PlayerScreen(
                                                    vm,
                                                    onBack = { showPlayer = false },
                                                    hazeState = hazeState,
                                                    backdropBlur = theme.backdropBlur,
                                                    coverColors = playerCoverColors,
                                                    // 第六轮第 36 项：播放器页歌手名可点进歌手页，
                                                    // 返回时回到播放器页。
                                                    onOpenArtist = { artistId = it; showPlayer = false; artistFromPlayer = true },
                                                    // 0.1.15 第 2 项：QQ 歌的歌手按 mid 进 QQ 歌手页。
                                                    onOpenQqArtist = { qqArtistMid = it; showPlayer = false; artistFromPlayer = true },
                                                )
                                            }
                                        }
                                        (key as? String)?.startsWith("album:") == true -> AlbumDetailScreen(
                                            vm, (key as String).removePrefix("album:").toLong(), bottomPadding,
                                            onTitle = { albumTitle = it },
                                            onHeaderVisible = { albumHeaderVisible = it },
                                            // 专辑页上点歌手名：专辑页让位给歌手页。
                                            onOpenArtist = { artistId = it; albumId = null },
                                        )
                                        // 0.1.15 第 2 项：QQ 专辑页（按 mid 路由）。
                                        (key as? String)?.startsWith("qqalbum:") == true -> AlbumDetailScreen(
                                            vm, 0L, bottomPadding,
                                            onTitle = { albumTitle = it },
                                            onHeaderVisible = { albumHeaderVisible = it },
                                            onOpenArtist = { artistId = it; albumId = null; qqAlbumMid = null },
                                            qqMid = (key as String).removePrefix("qqalbum:"),
                                            onOpenQqArtist = { qqArtistMid = it; qqAlbumMid = null },
                                        )
                                        (key as? String)?.startsWith("artist:") == true -> ArtistScreen(
                                            vm, (key as String).removePrefix("artist:").toLong(), bottomPadding,
                                            onTitle = { artistTitle = it },
                                            onHeaderVisible = { artistHeaderVisible = it },
                                            onOpenAlbum = { albumId = it },
                                            // 第六轮第 36 项：歌手页内再点歌手是歌手→歌手，不再
                                            // 经过播放器，返回时按原来的路走（回 tab，不回播放器）。
                                            onOpenArtist = { artistId = it; artistFromPlayer = false },
                                            // 0.1.13 第 24 项：关注态上报给顶栏右侧按钮。
                                            onFollowAction = { artistFollowAction = it },
                                        )
                                        // 0.1.15 第 2 项：QQ 歌手页（按 mid 路由）。
                                        (key as? String)?.startsWith("qqartist:") == true -> ArtistScreen(
                                            vm, 0L, bottomPadding,
                                            onTitle = { artistTitle = it },
                                            onHeaderVisible = { artistHeaderVisible = it },
                                            onOpenAlbum = { albumId = it; qqArtistMid = null },
                                            onOpenArtist = { artistId = it; artistFromPlayer = false; qqArtistMid = null },
                                            onFollowAction = { artistFollowAction = it },
                                            qqMid = (key as String).removePrefix("qqartist:"),
                                            onOpenQqAlbum = { qqAlbumMid = it },
                                            onOpenQqArtist = { qqArtistMid = it; artistFromPlayer = false },
                                        )
                                        (key as? String)?.startsWith("playlist:") == true -> PlaylistDetailScreen(
                                            vm, (key as String).removePrefix("playlist:").toLong(), bottomPadding,
                                            onTitle = { playlistTitle = it },
                                            onHeaderVisible = { detailHeaderVisible = it },
                                            onOpenArtist = { artistId = it },
                                            // 0.1.15 第 2 项：QQ 歌单里歌曲的歌手按 mid 进 QQ 歌手页。
                                            onOpenQqArtist = { qqArtistMid = it },
                                            // 0.1.13 第 25 项：收藏态上报给顶栏右侧按钮。
                                            onFavoriteAction = { playlistFavAction = it },
                                        )
                                        key == "stats" -> ListeningStatsScreen(
                                            vm, topPadding, bottomPadding,
                                            noteVisible = statsNoteVisible,
                                            onNoteDismiss = { statsNoteVisible = false },
                                        )
                                        // 0.1.17 第 17 项：网易云收听报告页。
                                        key == "netease-report" -> NeteaseReportScreen(
                                            vm, topPadding, bottomPadding,
                                            onOpenArtist = { artistId = it },
                                        )
                                        key == Dest.Home -> HomeScreen(
                                            vm, topPadding, bottomPadding,
                                            hazeState, theme.backdropBlur,
                                            onOpenLogin = { showSettings = true },
                                            onOpenPlaylist = { playlistId = it },
                                            onOpenArtist = { artistId = it },
                                            // 0.1.15 第 2 项：QQ 新歌的歌手按 mid 进 QQ 歌手页。
                                            onOpenQqArtist = { qqArtistMid = it },
                                            onSectionTitle = { homeSectionTitle = it },
                                        )
                                        else -> LibraryScreen(
                                            vm, topPadding, bottomPadding,
                                            onOpenSettings = { showSettings = true },
                                            onOpenPlaylist = { playlistId = it },
                                            section = librarySection,
                                            onOpenSection = { librarySection = it },
                                            onCloseSection = { librarySection = null },
                                            onOpenListeningStats = { statsOpen = true },
                                            onOpenNeteaseReport = { neteaseReportOpen = true },
                                            onOpenArtist = { artistId = it },
                                            onOpenAlbum = { albumId = it },
                                            // 0.1.15 资料库同步：QQ 关注歌手按 mid 进 QQ 歌手页。
                                            onOpenQqArtist = { qqArtistMid = it },
                                        )
                                    }
                                }
                            }
                            if (headerVisible && chromeBlurActive(theme.backdropBlur)) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .height(statusBarHeight + 64.dp + TokiGlassFade.topFadeExtra)
                                        .tokiGlassFade(hazeState, fadeFromTop = true),
                                )
                            }
                            BottomChrome(
                                modifier = Modifier.align(Alignment.BottomCenter),
                                visible = chromeVisible,
                                navVisible = bottomNavVisible,
                                wide = wide,
                                showMiniPlayer = !showPlayer && playerState.current != null,
                                playerState = playerState,
                                coverColors = playerCoverColors,
                                vm = vm,
                                dest = dest,
                                glass = barGlass,
                                solidStroke = !chromeBlurActive(theme.backdropBlur),
                                glassOn = chromeBlurActive(theme.backdropBlur),
                                onOpenPlayer = { showPlayer = true },
                                onSelect = {
                                    dest = it
                                    playlistId = null
                                    artistId = null
                                    artistFromPlayer = false
                                    albumId = null
                                    // 0.1.15 第 2 项：切 tab 时 QQ 歌手 / 专辑页同清。
                                    qqArtistMid = null
                                    qqAlbumMid = null
                                    librarySection = null
                                    statsOpen = false
                                    neteaseReportOpen = false
                                    searchSheetOpen = false
                                    showPlayer = false
                                },
                                onSearch = { searchSheetOpen = true },
                                onHeight = { if (chromeVisible) measuredChrome = it },
                            )
                        }
                    }
                    // 0.1.14 第 3 条：设置弹窗的遮罩不淡入淡出——
                    // 原生 ModalBottomSheet 的 scrim 带
                    // ScrimAlphaAnimation（M3 1.5.0-beta02 的 AAR
                    // 实测：sheet 本身不渐变，只有遮罩渐变），API
                    // 关不掉。改成透明 scrim（见 SettingsSheet）
                    // + 一层 32% 黑遮罩（与 M3 默认 scrim 同色同透明度）；
                    // 点遮罩关闭仍走原生 scrim 的手势（Dialog 窗口在上层
                    // 接手势）。渐进动画见 SettingsBackdrop（0.1.16 第 3 条）。
                    // 0.1.18 第 17 项：这一层从 Scaffold 的内容 Box 里移到
                    // Scaffold 之外（同一外层 Box 内、画在 Scaffold 之后）——
                    // 之前它在内容层里，Scaffold 的 topBar（主页 / 资源库的
                    // 标题 + 头像）画在它上面，进设置时不进模糊；移到
                    // Scaffold 之上后，tokiGlass 的 Backdrop 采样把整个
                    // Scaffold（页面内容 + 顶栏）都算进去，标题和头像
                    // 一起糊。配方（tokiGlass + 32% 黑）与渐进动画不变，
                    // 只扩大范围。
                    SettingsBackdrop(
                        visible = showSettings,
                        backdropBlur = theme.backdropBlur,
                        hazeState = hazeState,
                    )
                }
            }
        }
        // 底部弹窗（第二轮第 4 项）：设置是 ModalBottomSheet，浮在页面与
        // 底栏之上；chrome 在弹窗下保持可见。搜索自第五轮第 16 项起改回
        // 同款底部弹窗（TokiSheetContainer），点行播放后弹窗留着。
        if (showSettings) SettingsSheet(vm, theme, updateTheme, onClose = { showSettings = false })
        if (searchSheetOpen) SearchSheet(
            vm,
            backdropBlur = theme.backdropBlur,
            onClose = { searchSheetOpen = false },
            onOpenPlaylist = { playlistId = it; playlistTitle = ""; searchSheetOpen = false },
            onOpenArtist = { artistId = it; searchSheetOpen = false },
            // 0.1.15 第 2 项：QQ 歌手按 mid 进 QQ 歌手页。
            onOpenQqArtist = { qqArtistMid = it; searchSheetOpen = false },
        )
        // 第 6 项：长按歌曲的底部弹窗；“添加”只列我创建的歌单。
        val songActions by vm.songActions.collectAsState()
        val likedIds by vm.likedIds.collectAsState()
        val myPlaylists by vm.myPlaylists.collectAsState()
        val myProfile by vm.profile.collectAsState()
        if (songActions != null) {
            val ctx = songActions!!
            val createdPlaylists = groupPlaylists(myPlaylists, myProfile?.uid ?: 0L)[PlaylistGroup.Created] ?: emptyList()
            SongActionsSheet(
                context = ctx,
                liked = ctx.song.id in likedIds,
                myPlaylists = createdPlaylists,
                onDismiss = { vm.closeSongActions() },
                onToggleLike = { vm.toggleLike(ctx.song.id) },
                onAddToPlaylist = { pl -> vm.addSongToPlaylist(ctx.song, pl); vm.closeSongActions() },
                onRemoveFromPlaylist = { ctx.playlistId?.let { vm.removeSongFromPlaylist(ctx.song, it) }; vm.closeSongActions() },
            )
        }
        // 第三轮第 15 项：启动自动检查发现的新版提醒（与设置页手动检查共用 UpdatePrompt）。
        if (pendingUpdate != null) UpdatePrompt(pendingUpdate!!, currentVersion = vm.versionName) { pendingUpdate = null }
        // 第五轮第 15 项：更新完首次启动的优化二次确认（清理在 IO 协程里跑，
        // 按钮在清理期间禁用；清理完才记 versionCode，中途被杀下次再问）。
        if (optimizeSnapshot != null) {
            OptimizeSheet(
                snapshot = optimizeSnapshot!!,
                result = optimizeResult,
                cleaning = optimizeCleaning,
                onConfirm = {
                    if (optimizeCleaning) return@OptimizeSheet
                    optimizeCleaning = true
                    optimizeScope.launch {
                        val result = AppOptimizer.clean(context)
                        optimizeResult = result
                        optimizeCleaning = false
                        vm.store.lastLaunchedVersionCode = BuildConfig.VERSION_CODE
                    }
                },
                onDecline = dismissOptimize,
                onDismiss = dismissOptimize,
            )
        }
    }
}

/**
 * 设置背景的模糊 + 压暗层（0.1.16 第 3 条）——单独抽成顶层 composable，
 * 否则在页面 Row 里调 AnimatedVisibility 会撞 RowScope 的 DSL 作用域
 * 限制（编译失败；同款前例见下面的 NavBarSlot）。
 */
@Composable
private fun SettingsBackdrop(
    visible: Boolean,
    backdropBlur: Boolean,
    hazeState: HazeState,
) {
    // 0.1.16 第 3 条：模糊 + 压暗一起渐进出现 / 渐隐消失（fade 走 effects
    // spec，与 sheet 滑入匹配）——之前是 if 开关瞬间出现 / 消失。手势不变：
    // 黑遮罩层仍在 Dialog 窗口之下、不接手势，关闭仍走原生 scrim /
    // 返回键；玻璃关闭时只渐隐黑遮罩。
    val scheme = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = scheme.defaultEffectsSpec()),
        exit = fadeOut(animationSpec = scheme.defaultEffectsSpec()),
        label = "settings-backdrop",
    ) {
        // 0.1.15 第 13 项：设置打开时背景加磨砂模糊（与底栏 / 标题同款
        // tokiGlass 配方；玻璃关闭时 chromeBlurActive 关掉不画），让设置
        // 页更突出。模糊层在遮罩之下（先模糊、再压暗）。
        if (chromeBlurActive(backdropBlur)) {
            Box(Modifier.fillMaxSize().tokiGlass(hazeState))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomChrome(
    modifier: Modifier = Modifier,
    visible: Boolean,
    navVisible: Boolean,
    wide: Boolean,
    showMiniPlayer: Boolean,
    playerState: com.tokimusic.app.player.PlayerState,
    coverColors: com.tokimusic.app.ui.theme.CoverColors? = null,
    vm: AppViewModel,
    dest: Dest,
    glass: Modifier,
    solidStroke: Boolean,
    glassOn: Boolean,
    onOpenPlayer: () -> Unit,
    onSelect: (Dest) -> Unit,
    onSearch: () -> Unit,
    onHeight: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    val scheme = MaterialTheme.motionScheme
    // 0.1.14 第 12 项：0.1.13 第 26 项的固定槽走偏了——导航栏放在固定
    // 高度的占位槽里，MiniPlayer 被冻住，进二级页底边留空槽。本轮拆掉
    // 固定槽：NavBarSlot 回到 Column 的直接子项（MiniPlayer 之下），
    // 导航栏退出时滑出 + 淡出 + 纵向收缩（布局高度跟着动画走），Column
    // 底锚定，MiniPlayer 平滑滑下去贴底，回来时滑回来；内容区底边距
    // （bottomPadding）由 onHeight 在收缩动画里逐帧跟随，不跳。
    // 0.1.14 第 2 项：底栏偏左的根因也在这个槽——Box 默认 TopStart
    // 对齐，wrap-content 的导航 Row 被顶到左边缘；直接子项走 Column
    // 的 CenterHorizontally，恢复居中。
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(scheme.defaultSpatialSpec()) { it } + fadeIn(scheme.defaultEffectsSpec()),
        exit = slideOutVertically(scheme.defaultSpatialSpec()) { it } + fadeOut(scheme.defaultEffectsSpec()),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { onHeight(with(density) { it.height.toDp() }) }
                .navigationBarsPadding()
                .padding(bottom = tokiSpace("sm")),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokiSpace("sm")),
        ) {
            // 第五轮第 9 项：MiniPlayer 从导航胶囊里拆出来。
            // 0.1.18 第 6 项：显隐补纵向滑入 + 淡入（之前 if 瞬切）；滑入
            // 滑出走 spatial spec、淡入淡出走 effects spec。代价：隐藏后
            // AnimatedVisibility 仍占 0 高度布局位，Column 的 spacedBy(sm)
            // 会多留 8dp，onHeight 随之多报 8dp（内容底边距多 8dp，可忽略）。
            AnimatedVisibility(
                visible = showMiniPlayer,
                enter = slideInVertically(scheme.defaultSpatialSpec()) { it } + fadeIn(scheme.defaultEffectsSpec()),
                exit = slideOutVertically(scheme.defaultSpatialSpec()) { it } + fadeOut(scheme.defaultEffectsSpec()),
                label = "miniPlayer",
            ) {
                MiniPlayer(playerState, vm, onOpenPlayer, glass = glass, glassOn = glassOn, coverColors = coverColors)
            }
            NavBarSlot(
                navVisible = navVisible,
                wide = wide,
                dest = dest,
                glass = glass,
                solidStroke = solidStroke,
                onSelect = onSelect,
                onSearch = onSearch,
            )
        }
    }
}

/**
 * 底部导航栏（3 tab 胶囊 + 搜索圆钮）——单独抽成顶层 composable，
 * 否则在 BottomChrome 的 Box 里调 AnimatedVisibility 会撞 ColumnScope
 * 的 DSL 作用域限制（编译失败）。0.1.14 第 12 项：显隐动画加纵向
 * 展开 / 收缩（布局高度跟着动画走），退出时导航栏滑出 + 淡出 +
 * 收缩，底锚定的 Column 收短，MiniPlayer 平滑滑下去贴底；进入时
 * 反向滑回来。0.1.13 第 26 项的固定槽已拆，不再需要 onNavHeightPx。
 */
@Composable
private fun NavBarSlot(
    navVisible: Boolean,
    wide: Boolean,
    dest: Dest,
    glass: Modifier,
    solidStroke: Boolean,
    onSelect: (Dest) -> Unit,
    onSearch: () -> Unit,
) {
    val scheme = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = navVisible,
        enter = slideInVertically(scheme.defaultSpatialSpec()) { it } + fadeIn(scheme.defaultEffectsSpec()) + expandVertically(scheme.defaultSpatialSpec()),
        exit = slideOutVertically(scheme.defaultSpatialSpec()) { it } + fadeOut(scheme.defaultEffectsSpec()) + shrinkVertically(scheme.defaultSpatialSpec()),
    ) {
        if (!wide) {
            // 第二轮第 23 项：3 tab 胶囊 + 胶囊旁的独立搜索圆钮（TokiUI
            // 0.1.10 的独立按钮形态，8dp 间隔，与胶囊同配方）。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FloatingNavBar(dest, onSelect, glass = glass, solidStroke = solidStroke)
                TokiSideActionButton(
                    Icons.Rounded.Search,
                    stringResource(R.string.nav_search),
                    onClick = onSearch,
                    glass = glass,
                    solidFill = solidStroke,
                )
            }
        }
    }
}
