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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tokimusic.app.ui.components.FloatingNavBar
import com.tokimusic.app.ui.components.OverlayTitleHeader
import com.tokimusic.app.ui.components.navEntries
import com.tokimusic.app.ui.home.HomeScreen
import com.tokimusic.app.ui.mine.MineScreen
import com.tokimusic.app.ui.player.MiniPlayer
import com.tokimusic.app.ui.player.PlayerScreen
import com.tokimusic.app.ui.playlists.PlaylistDetailScreen
import com.tokimusic.app.ui.playlists.PlaylistsScreen
import com.tokimusic.app.ui.theme.ThemeMode
import com.tokimusic.app.ui.theme.ThemePreferences
import com.tokimusic.app.ui.theme.TokiGlassFade
import com.tokimusic.app.ui.theme.TokiMusicTheme
import com.tokimusic.app.ui.theme.chromeBlurActive
import com.tokimusic.app.ui.theme.tokiChrome
import com.tokimusic.app.ui.theme.tokiGlassFade
import com.tokimusic.app.ui.theme.tokiSpace
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TokiMusicApp() {
    val vm: AppViewModel = viewModel()
    var theme by remember {
        mutableStateOf(
            ThemePreferences(
                mode = runCatching { ThemeMode.valueOf(vm.store.themeMode) }.getOrDefault(ThemeMode.System),
                useDynamicColor = vm.store.useDynamicColor,
                seedIndex = vm.store.seedIndex,
                reduceMotion = vm.store.reduceMotion,
                backdropBlur = vm.store.backdropBlur,
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
    }

    TokiMusicTheme(
        mode = theme.mode,
        useDynamicColor = theme.useDynamicColor,
        seedIndex = theme.seedIndex,
        reduceMotion = theme.reduceMotion,
    ) {
        var dest by remember { mutableStateOf(Dest.Home) }
        var playlistId by remember { mutableStateOf<Long?>(null) }
        var playlistTitle by remember { mutableStateOf("歌单") }
        var showPlayer by remember { mutableStateOf(false) }
        val hazeState = rememberHazeState()
        val glass = tokiChrome(hazeState, theme.backdropBlur)
        val density = LocalDensity.current
        val headerOffset = remember { mutableFloatStateOf(0f) }
        val headerNestedScroll = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    headerOffset.floatValue = headerScrollOffset(headerOffset.floatValue, consumed.y, available.y)
                    return Offset.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (available.y > 0f) headerOffset.floatValue = 0f
                    return Velocity.Zero
                }
            }
        }
        val headerOverlapped = headerOverlapsContent(
            headerOffset.floatValue,
            with(density) { 8.dp.toPx() },
        )
        LaunchedEffect(dest, playlistId, showPlayer) { headerOffset.floatValue = 0f }

        val playerState by vm.player.state.collectAsState()
        val homeTitle = stringResource(navEntries[0].label)
        val playlistsTitle = stringResource(navEntries[1].label)
        val mineTitle = stringResource(navEntries[2].label)
        val pageTitle = when {
            showPlayer -> "正在播放"
            playlistId != null -> playlistTitle
            dest == Dest.Home -> homeTitle
            dest == Dest.Playlists -> playlistsTitle
            else -> mineTitle
        }
        // The player is a second-level page: no top bar, no bottom bar, no rail.
        // Home draws its own large title + avatar in content, so the pinned
        // overlay header is hidden there too.
        val chromeVisible = !showPlayer
        val headerVisible = chromeVisible && (playlistId != null || dest != Dest.Home)
        val onBack: (() -> Unit)? = when {
            showPlayer -> ({ showPlayer = false })
            playlistId != null -> ({ playlistId = null })
            else -> null
        }
        BackHandler(enabled = showPlayer) { showPlayer = false }
        BackHandler(enabled = !showPlayer && playlistId != null) { playlistId = null }

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
            playlistId != null -> "playlist:$playlistId"
            else -> dest
        }

        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
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
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        topBar = {
                            if (headerVisible) {
                                OverlayTitleHeader(
                                    title = pageTitle,
                                    overlapped = headerOverlapped,
                                    onBack = onBack,
                                    onHeightMeasured = { topBarHeight = it },
                                    backdropBlur = theme.backdropBlur,
                                    hazeState = hazeState,
                                )
                            }
                        },
                    ) { _ ->
                        Box(Modifier.fillMaxSize().nestedScroll(headerNestedScroll)) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .hazeSource(hazeState)
                                    .background(MaterialTheme.colorScheme.surface),
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
                                        key == "player" -> PlayerScreen(vm)
                                        (key as? String)?.startsWith("playlist:") == true -> PlaylistDetailScreen(
                                            vm, (key as String).removePrefix("playlist:").toLong(), topPadding, bottomPadding,
                                            onTitle = { playlistTitle = it },
                                        )
                                        key == Dest.Home -> HomeScreen(
                                            vm, statusBarHeight, bottomPadding,
                                            onOpenLogin = { dest = Dest.Mine },
                                            onOpenPlaylist = { playlistId = it },
                                        )
                                        key == Dest.Playlists -> PlaylistsScreen(vm, topPadding, bottomPadding) { playlistId = it }
                                        else -> MineScreen(vm, topPadding, bottomPadding, theme, updateTheme)
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
            if (!wide) FloatingNavBar(dest, onSelect, glass = glass, solidStroke = solidStroke)
        }
    }
}
