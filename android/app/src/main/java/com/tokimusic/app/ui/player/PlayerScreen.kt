package com.tokimusic.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.player.PlayerState
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.TokiLongPressLabel
import com.tokimusic.app.ui.components.formatDuration
import kotlinx.coroutines.delay

@Composable
fun MiniPlayer(state: PlayerState, vm: AppViewModel, onOpen: () -> Unit) {
    val song = state.current ?: return
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.index, state.isPlaying) { while (true) { pos = vm.player.currentPosition(); delay(500) } }
    val playLabel = if (state.isPlaying) "暂停" else "播放"
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(song.picUrl, song.name, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(song.artistText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TokiLongPressLabel(playLabel) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(percent = 50)).clickable { vm.player.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = playLabel)
                    }
                }
                TokiLongPressLabel("下一首") {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(percent = 50)).clickable { vm.player.next() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一首")
                    }
                }
            }
            LinearProgressIndicator(
                progress = { if (song.durationMs > 0) (pos.toFloat() / song.durationMs).coerceIn(0f, 1f) else 0f },
                Modifier.fillMaxWidth().height(2.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, topPadding: Dp, bottomPadding: Dp) {
    val state by vm.player.state.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val song = state.current
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.index) { song?.let { vm.loadLyrics(it.id) } }
    LaunchedEffect(state.isPlaying, state.index) { while (true) { pos = vm.player.currentPosition(); delay(250) } }
    if (song == null) {
        Text("暂无播放中的歌曲", Modifier.padding(top = topPadding + 16.dp, start = 16.dp), style = MaterialTheme.typography.bodyLarge)
        return
    }
    val playLabel = if (state.isPlaying) "暂停" else "播放"
    Column(Modifier.fillMaxSize().padding(top = topPadding, bottom = bottomPadding)) {
        Column(
            Modifier
                .weight(1.1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                song.picUrl,
                song.name,
                Modifier.size(280.dp).clip(RoundedCornerShape(36.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(24.dp))
            Text(song.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(song.artistText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (song.albumName.isNotBlank()) {
                Text(song.albumName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Slider(
                pos.toFloat(),
                { vm.player.seekTo(it.toLong()); pos = it.toLong() },
                valueRange = 0f..(song.durationMs.coerceAtLeast(1).toFloat()),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(pos), style = MaterialTheme.typography.labelLarge)
                Text(formatDuration(song.durationMs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TokiLongPressLabel("上一首") {
                    TransportButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一首") { vm.player.previous() }
                }
                TokiLongPressLabel(playLabel) {
                    LargeFloatingActionButton(onClick = { vm.player.toggle() }) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = playLabel,
                        )
                    }
                }
                TokiLongPressLabel("下一首") {
                    TransportButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一首") { vm.player.next() }
                }
            }
        }
        Text("歌词", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
        LyricsView(lyrics, pos, Modifier.weight(1f))
    }
}

@Composable
private fun TransportButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
    }
}
