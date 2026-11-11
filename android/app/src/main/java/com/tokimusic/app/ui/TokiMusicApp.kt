package com.tokimusic.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.tokimusic.app.ui.components.FloatingNavBar
import com.tokimusic.app.ui.home.HomeScreen
import com.tokimusic.app.ui.mine.MineScreen
import com.tokimusic.app.ui.player.MiniPlayer
import com.tokimusic.app.ui.player.PlayerScreen
import com.tokimusic.app.ui.playlists.PlaylistDetailScreen
import com.tokimusic.app.ui.playlists.PlaylistsScreen
import com.tokimusic.app.ui.theme.ThemeMode
import com.tokimusic.app.ui.theme.TokiMusicTheme
import com.tokimusic.app.ui.theme.tokiNavGlass

@Composable fun TokiMusicApp() {
    val context = LocalContext.current
    val vm: AppViewModel = viewModel()
    var themeMode by remember { mutableStateOf(runCatching { ThemeMode.valueOf(vm.store.themeMode) }.getOrDefault(ThemeMode.System)) }
    TokiMusicTheme(themeMode) {
        var dest by remember { mutableStateOf(Dest.Home) }
        var playlistId by remember { mutableStateOf<Long?>(null) }
        var showPlayer by remember { mutableStateOf(false) }
        var showLoginFromHome by remember { mutableStateOf(false) }
        val hazeState = remember { HazeState() }
        val density = LocalDensity.current
        var barHeight by remember { mutableStateOf(0.dp) }
        val playerState by vm.player.state.collectAsState()
        val bottomPadding = barHeight + if (playerState.current != null) 76.dp else 16.dp
        Scaffold { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                    when {
                        showPlayer -> PlayerScreen(vm) { showPlayer = false }
                        playlistId != null -> PlaylistDetailScreen(vm, playlistId!!) { playlistId = null }
                        dest == Dest.Home -> HomeScreen(vm, bottomPadding, onOpenLogin = { dest = Dest.Mine }, onOpenPlaylist = { playlistId = it })
                        dest == Dest.Playlists -> PlaylistsScreen(vm, bottomPadding) { playlistId = it }
                        dest == Dest.Mine -> MineScreen(vm, bottomPadding, themeMode) { themeMode = it; vm.store.themeMode = it.name }
                    }
                }
                // Floating stack: MiniPlayer above the floating glass bottom bar (8dp gap).
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!showPlayer && playerState.current != null) MiniPlayer(playerState, vm) { showPlayer = true }
                    FloatingNavBar(dest, { dest = it; playlistId = null; showPlayer = false },
                        modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } }, glass = Modifier.tokiNavGlass(hazeState))
                }
            }
        }
    }
}
