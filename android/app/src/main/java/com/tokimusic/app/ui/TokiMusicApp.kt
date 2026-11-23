package com.tokimusic.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.tokimusic.app.ui.components.SourceDropdownButton
import com.tokimusic.app.ui.components.TokiLongPressLabel
import com.tokimusic.app.ui.components.TokiSideActionButton
import com.tokimusic.app.ui.components.navEntries
import com.tokimusic.app.ui.home.HomeScreen
import com.tokimusic.app.ui.library.LibraryScreen
import com.tokimusic.app.ui.library.LibrarySection
import com.tokimusic.app.ui.stats.ListeningStatsScreen
import com.tokimusic.app.ui.stats.ListeningTreemapScreen
import com.tokimusic.app.ui.player.MiniPlayer
import com.tokimusic.app.ui.player.PlayerScreen
import com.tokimusic.app.ui.playlists.PlaylistDetailScreen
import com.tokimusic.app.ui.search.SearchSheet
import com.tokimusic.app.ui.settings.SettingsSheet
import com.tokimusic.app.ui.theme.ThemeMode
import com.tokimusic.app.ui.theme.ThemePreferences
import com.tokimusic.app.ui.theme.TokiGlassFade
import com.tokimusic.app.ui.theme.TokiMusicTheme
import com.tokimusic.app.ui.theme.chromeBlurActive
import com.tokimusic.app.ui.theme.rememberCoverColors
import com.tokimusic.app.ui.theme.tokiContentChromeStrong
import com.tokimusic.app.ui.theme.tokiGlassFade
import com.tokimusic.app.ui.theme.CirroBlack
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TokiMusicApp() {
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
                mode = runCatching { ThemeMode.valueOf(vm.store.themeMode) }.getOrDefault(ThemeMode.System),
                useDynamicColor = vm.store.useDynamicColor,
                seedIndex = vm.store.seedIndex,
                reduceMotion = vm.store.reduceMotion,
                backdropBlur = vm.store.backdropBlur,
                cirroAppearance = vm.store.cirroAppearance,
            ),
        )
    }
    val updateTheme: (ThemePreferences) -> Unit = { next ->
        theme = next
        vm.store.themeMode = next.mode.name
        vm.store.useDynamicColor = next.useDynamicColor
        vm.store.seedIndex = next.seedIndex
        vm.store.reduceMotion = next.reduceMotion
        vm.store.backdropBlur = next.backdropBlur
        vm.store.cirroAppearance = next.cirroAppearance
    }

    TokiMusicTheme(
        mode = theme.mode,
        useDynamicColor = theme.useDynamicColor,
        seedIndex = theme.seedIndex,
        reduceMotion = theme.reduceMotion,
        cirroAppearance = theme.cirroAppearance,
    ) {
        var dest by remember { mutableStateOf(Dest.Home) }
        var playlistId by remember { mutableStateOf<Long?>(null) }
        var playlistTitle by remember { mutableStateOf("歌单") }
        // Round 3 item 12: artist + album detail pages (same level as
        // playlistId). Stacking: opening an artist from a playlist keeps
        // the playlist underneath (back returns to it); opening an album
        // from an artist keeps the artist underneath. The pageKey/onBack
        // priority is album > artist > playlist > search > treemap > stats > section.
        var artistId by remember { mutableStateOf<Long?>(null) }
        var artistTitle by remember { mutableStateOf("歌手") }
        var albumId by remember { mutableStateOf<Long?>(null) }
        var albumTitle by remember { mutableStateOf("专辑") }
        // Round 3 item 2: the open Library section lives here (with
        // playlistId) so second-level pages get the floating title + back.
        var librarySection by remember { mutableStateOf<LibrarySection?>(null) }
        // Round 3 item 13: listening-stats second-level page.
        var statsOpen by remember { mutableStateOf(false) }
        // 第 5 项：听歌时长树状图，stats 页里的二级页（返回先回时长页）。
        var treemapOpen by remember { mutableStateOf(false) }
        // Round 3 item 20: Home's floating title follows the scrolled section.
        var homeSectionTitle by remember { mutableStateOf("主页") }
        var showPlayer by remember { mutableStateOf(false) }
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
        val topDetailHeaderVisible = when {
            albumId != null -> albumHeaderVisible
            artistId != null -> artistHeaderVisible
            playlistId != null -> detailHeaderVisible
            else -> true
        }
        val headerAlpha by animateFloatAsState(
            targetValue = if (albumId == null && artistId == null && playlistId == null || topDetailHeaderVisible) 1f else 0f,
            animationSpec = tween(300),
            label = "detailHeaderAlpha",
        )
        val headerOffsetY by animateFloatAsState(
            targetValue = if (albumId == null && artistId == null && playlistId == null || topDetailHeaderVisible) 0f else with(density) { -8.dp.toPx() },
            animationSpec = tween(300),
            label = "detailHeaderOffset",
        )
        val profile by vm.profile.collectAsState()
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
            artistId != null -> artistTitle
            playlistId != null -> playlistTitle
            treemapOpen -> "时长分布"
            statsOpen -> "听歌时长"
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
        val isSecondLevel = albumId != null || artistId != null || playlistId != null ||
            statsOpen || treemapOpen || librarySection != null
        val bottomNavVisible = !showPlayer && !isSecondLevel
        val onBack: (() -> Unit)? = when {
            showPlayer -> ({ showPlayer = false })
            albumId != null -> ({ albumId = null })
            artistId != null -> ({ artistId = null })
            playlistId != null -> ({ playlistId = null })
            treemapOpen -> ({ treemapOpen = false })
            statsOpen -> ({ statsOpen = false })
            librarySection != null -> ({ librarySection = null })
            else -> null
        }
        BackHandler(enabled = showPlayer) { showPlayer = false }
        BackHandler(enabled = !showPlayer && albumId != null) { albumId = null }
        BackHandler(enabled = !showPlayer && albumId == null && artistId != null) { artistId = null }
        BackHandler(enabled = !showPlayer && albumId == null && artistId == null && playlistId != null) { playlistId = null }
        BackHandler(enabled = !showPlayer && albumId == null && artistId == null && playlistId == null && treemapOpen) { treemapOpen = false }
        BackHandler(enabled = !showPlayer && albumId == null && artistId == null && playlistId == null && !treemapOpen && statsOpen) { statsOpen = false; treemapOpen = false }
        BackHandler(enabled = !showPlayer && albumId == null && artistId == null && playlistId == null && !statsOpen && librarySection != null) { librarySection = null }

        val contentGap = tokiSpace("lg")
        val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        var topBarHeight by remember { mutableStateOf(0.dp) }
        var measuredChrome by remember { mutableStateOf(0.dp) }
        val topPadding = if (topBarHeight > 0.dp) topBarHeight else statusBarHeight + 64.dp
        val bottomPadding = measuredChrome + contentGap
        val scheme = MaterialTheme.motionScheme

        // Page identity drives the transitions: the player slides up from the
        // bottom, a playlist detail slides in from the right, tabs crossfade.
        // 第五轮第 1 项：资料库分段页也编进 pageKey（否则分段页进出没有
        // 动画触发点——pageKey 之前一直是 dest，分段切换不触发 AnimatedContent）。
        val pageKey: Any = when {
            showPlayer -> "player"
            albumId != null -> "album:$albumId"
            artistId != null -> "artist:$artistId"
            playlistId != null -> "playlist:$playlistId"
            treemapOpen -> "treemap"
            statsOpen -> "stats"
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
                                        albumId = null
                                        librarySection = null
                                        statsOpen = false
                                        treemapOpen = false
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
                                icon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.nav_search)) },
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
                                    // chip 加不透明底（页面底色 CirroBlack）——封面渐变
                                    // 的上端色会透过半透明磨砂让顶栏看起来还留着封面；
                                    // tab 页不传（标题常显，磨砂是设计的一部分）。
                                    // chip 本身随 titleAlpha 淡入，进页时看不到这层底。
                                    titleSolidColor = if (albumId != null || artistId != null || playlistId != null) CirroBlack else null,
                                    // 第六轮第 1 项：主页左上角源切换入口——
                                    // 只在主页 tab 且双源都启用时出现；点开
                                    // 下拉，网易云在上、QQ 在下，点行切换。
                                    leading = if (dest == Dest.Home && playlistId == null && artistId == null && albumId == null && librarySection == null && !statsOpen && neteaseEnabled && qqEnabled) {
                                        {
                                            SourceDropdownButton(
                                                neteaseEnabled = neteaseEnabled,
                                                qqEnabled = qqEnabled,
                                                onToggleNetease = { vm.setSourceEnabled(MusicSource.NETEASE, !neteaseEnabled) },
                                                onToggleQq = { vm.setSourceEnabled(MusicSource.QQ, !qqEnabled) },
                                            )
                                        }
                                    } else null,
                                    // Tab 页：头像入口放标题行右侧（第 3 项）；第二级页面
                                    // （歌单/歌手/专辑详情 / 资料库分段 / 听歌时长）不放，只留返回 chip。
                                    // 搜索是底部弹窗，不占标题位。
                                    trailing = if (playlistId == null && artistId == null && albumId == null && librarySection == null && !statsOpen) {
                                        {
                                            TokiLongPressLabel(if (profile == null) "登录" else "账户与设置") {
                                                IconButton(onClick = { showSettings = true }) {
                                                    Avatar(profile?.avatarUrl ?: "", 48)
                                                }
                                            }
                                        }
                                    } else null,
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
                                            return s.startsWith("album:") || s.startsWith("artist:") ||
                                                s.startsWith("playlist:") || s.startsWith("section:") ||
                                                s == "stats" || s == "treemap"
                                        }
                                        val toDetail = isDetailKey(targetState) && !isDetailKey(initialState) && initialState != "player"
                                        val fromDetail = isDetailKey(initialState) && !isDetailKey(targetState) && targetState != "player"
                                        when {
                                            toPlayer -> (slideInVertically(scheme.defaultSpatialSpec()) { it } + fadeIn(scheme.defaultEffectsSpec())) togetherWith
                                                fadeOut(scheme.defaultEffectsSpec())
                                            fromPlayer -> fadeIn(scheme.defaultEffectsSpec()) togetherWith
                                                (slideOutVertically(scheme.defaultSpatialSpec()) { it } + fadeOut(scheme.defaultEffectsSpec()))
                                            toDetail -> (slideInHorizontally(scheme.defaultSpatialSpec()) { it } + fadeIn(scheme.defaultEffectsSpec())) togetherWith
                                                (fadeOut(scheme.defaultEffectsSpec()) + scaleOut(scheme.defaultSpatialSpec(), 0.98f))
                                            fromDetail -> (fadeIn(scheme.defaultEffectsSpec()) + scaleIn(scheme.defaultSpatialSpec(), 0.98f)) togetherWith
                                                (slideOutHorizontally(scheme.defaultSpatialSpec()) { it } + fadeOut(scheme.defaultEffectsSpec()))
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
                                        (key as? String)?.startsWith("artist:") == true -> ArtistScreen(
                                            vm, (key as String).removePrefix("artist:").toLong(), bottomPadding,
                                            onTitle = { artistTitle = it },
                                            onHeaderVisible = { artistHeaderVisible = it },
                                            onOpenAlbum = { albumId = it },
                                            onOpenArtist = { artistId = it },
                                        )
                                        (key as? String)?.startsWith("playlist:") == true -> PlaylistDetailScreen(
                                            vm, (key as String).removePrefix("playlist:").toLong(), bottomPadding,
                                            onTitle = { playlistTitle = it },
                                            onHeaderVisible = { detailHeaderVisible = it },
                                            onOpenArtist = { artistId = it },
                                        )
                                        key == "treemap" -> ListeningTreemapScreen(vm, topPadding, bottomPadding)
                                        key == "stats" -> ListeningStatsScreen(vm, topPadding, bottomPadding, onOpenTreemap = { treemapOpen = true })
                                        key == Dest.Home -> HomeScreen(
                                            vm, topPadding, bottomPadding,
                                            hazeState, theme.backdropBlur,
                                            onOpenLogin = { showSettings = true },
                                            onOpenPlaylist = { playlistId = it },
                                            onOpenArtist = { artistId = it },
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
                                            onOpenArtist = { artistId = it },
                                            onOpenAlbum = { albumId = it },
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
                                    albumId = null
                                    librarySection = null
                                    statsOpen = false
                                    treemapOpen = false
                                    searchSheetOpen = false
                                    showPlayer = false
                                },
                                onSearch = { searchSheetOpen = true },
                                onHeight = { if (chromeVisible) measuredChrome = it },
                            )
                        }
                    }
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
            // 第五轮第 9 项：MiniPlayer 从导航胶囊里拆出来——进二级页时导航
            // 弹走，MiniPlayer 留在底边（贴底，只留手势导航条间距）。
            if (showMiniPlayer) MiniPlayer(playerState, vm, onOpenPlayer, glass = glass, glassOn = glassOn, coverColors = coverColors)
            AnimatedVisibility(
                visible = navVisible,
                enter = slideInVertically(scheme.defaultSpatialSpec()) { it } + fadeIn(scheme.defaultEffectsSpec()),
                exit = slideOutVertically(scheme.defaultSpatialSpec()) { it } + fadeOut(scheme.defaultEffectsSpec()),
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
                            Icons.Filled.Search,
                            stringResource(R.string.nav_search),
                            onClick = onSearch,
                            glass = glass,
                            solidFill = solidStroke,
                        )
                    }
                }
            }
        }
    }
}
