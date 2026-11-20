package com.tokimusic.app.ui

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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tokimusic.app.ui.components.FloatingNavBar
import com.tokimusic.app.ui.components.OverlayTitleHeader
import com.tokimusic.app.ui.components.TokiLongPressLabel
import com.tokimusic.app.ui.components.navEntries
import com.tokimusic.app.ui.discover.DiscoverScreen
import com.tokimusic.app.ui.home.HomeScreen
import com.tokimusic.app.ui.library.LibraryScreen
import com.tokimusic.app.ui.player.MiniPlayer
import com.tokimusic.app.ui.player.PlayerScreen
import com.tokimusic.app.ui.playlists.PlaylistDetailScreen
import com.tokimusic.app.ui.search.SearchScreen
import com.tokimusic.app.ui.settings.SettingsScreen
import com.tokimusic.app.ui.theme.ThemeMode
import com.tokimusic.app.ui.theme.ThemePreferences
import com.tokimusic.app.ui.theme.TokiGlassFade
import com.tokimusic.app.ui.theme.TokiMusicTheme
import com.tokimusic.app.ui.theme.chromeBlurActive
import com.tokimusic.app.ui.theme.tokiChrome
import com.tokimusic.app.ui.theme.tokiContentChrome
import com.tokimusic.app.ui.theme.tokiGlassFade
import com.tokimusic.app.ui.theme.tokiPageBackground
import com.tokimusic.app.ui.theme.tokiSpace
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
        var showPlayer by remember { mutableStateOf(false) }
        // 设置页：独立页，左上 X 关闭 + 居中标题（Cirro）。
        var showSettings by remember { mutableStateOf(false) }
        val hazeState = rememberHazeState()
        val glass = tokiChrome(hazeState, theme.backdropBlur)
        // The nav capsule changes width as its label expands: content-source
        // sampling, so the blur never black-edges mid-animation (0.1.10).
        val barGlass = tokiContentChrome(hazeState, theme.backdropBlur)
        val density = LocalDensity.current

        val playerState by vm.player.state.collectAsState()
        val pageTitle = when {
            showPlayer -> "正在播放"
            showSettings -> "账户与设置"
            playlistId != null -> playlistTitle
            else -> stringResource(navEntries.first { it.dest == dest }.label)
        }
        // The player is a second-level page: no top bar, no bottom bar, no
        // rail — but it now carries its own floating back chip (0.1.10: every
        // second-level page gets one). Home / Discover / Library / Search draw
        // their own large in-content titles (Cirro), so the pinned overlay
        // header only shows for playlist detail.
        val chromeVisible = !showPlayer && !showSettings
        val headerVisible = chromeVisible && playlistId != null
        val onBack: (() -> Unit)? = when {
            showPlayer -> ({ showPlayer = false })
            showSettings -> ({ showSettings = false })
            playlistId != null -> ({ playlistId = null })
            else -> null
        }
        BackHandler(enabled = showPlayer) { showPlayer = false }
        BackHandler(enabled = showSettings) { showSettings = false }
        BackHandler(enabled = !showPlayer && !showSettings && playlistId != null) { playlistId = null }

        val contentGap = tokiSpace("lg")
        val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        var topBarHeight by remember { mutableStateOf(0.dp) }
        var measuredChrome by remember { mutableStateOf(0.dp) }
        val topPadding = if (topBarHeight > 0.dp) topBarHeight else statusBarHeight + 64.dp
        val bottomPadding = measuredChrome + contentGap
        val scheme = MaterialTheme.motionScheme

        // Page identity drives the transitions: the player slides up from the
        // bottom, a playlist detail slides in from the right, tabs crossfade.
        val pageKey: Any = when {
            showPlayer -> "player"
            showSettings -> "settings"
            playlistId != null -> "playlist:$playlistId"
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
                                        val toDetail = (targetState as? String)?.startsWith("playlist:") == true
                                        val fromDetail = (initialState as? String)?.startsWith("playlist:") == true
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
                                        key == "player" -> PlayerScreen(
                                            vm,
                                            onBack = { showPlayer = false },
                                            hazeState = hazeState,
                                            backdropBlur = theme.backdropBlur,
                                        )
                                        key == "settings" -> SettingsScreen(vm, theme, updateTheme, onClose = { showSettings = false })
                                        (key as? String)?.startsWith("playlist:") == true -> PlaylistDetailScreen(
                                            vm, (key as String).removePrefix("playlist:").toLong(), bottomPadding,
                                            onTitle = { playlistTitle = it },
                                        )
                                        key == Dest.Home -> HomeScreen(
                                            vm, statusBarHeight, bottomPadding,
                                            onOpenLogin = { showSettings = true },
                                            onOpenPlaylist = { playlistId = it },
                                        )
                                        key == Dest.Discover -> DiscoverScreen(vm, topPadding, bottomPadding) { playlistId = it }
                                        key == Dest.Library -> LibraryScreen(vm, topPadding, bottomPadding, onOpenSettings = { showSettings = true }) { playlistId = it }
                                        else -> SearchScreen(vm, topPadding, bottomPadding) { playlistId = it; playlistTitle = "" }
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
                                wide = wide,
                                showMiniPlayer = !showPlayer && playerState.current != null,
                                playerState = playerState,
                                vm = vm,
                                dest = dest,
                                glass = glass,
                                barGlass = barGlass,
                                solidStroke = !chromeBlurActive(theme.backdropBlur),
                                onOpenPlayer = { showPlayer = true },
                                onSelect = {
                                    dest = it
                                    playlistId = null
                                    showPlayer = false
                                },
                                onHeight = { if (chromeVisible) measuredChrome = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomChrome(
    modifier: Modifier = Modifier,
    visible: Boolean,
    wide: Boolean,
    showMiniPlayer: Boolean,
    playerState: com.tokimusic.app.player.PlayerState,
    vm: AppViewModel,
    dest: Dest,
    glass: Modifier,
    barGlass: Modifier,
    solidStroke: Boolean,
    onOpenPlayer: () -> Unit,
    onSelect: (Dest) -> Unit,
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
            if (showMiniPlayer) MiniPlayer(playerState, vm, onOpenPlayer)
            if (!wide) {
                // Cirro：只有胶囊导航，没有胶囊旁的独立队列按钮（队列入口在播放器底部）。
                FloatingNavBar(dest, onSelect, glass = barGlass, solidStroke = solidStroke)
            }
        }
    }
}
