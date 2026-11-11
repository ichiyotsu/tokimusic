package com.tokimusic.app.ui.player
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.player.PlayerState
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.formatDuration
import kotlinx.coroutines.delay

@Composable fun MiniPlayer(state: PlayerState, vm: AppViewModel, onOpen: () -> Unit) {
    val song = state.current ?: return
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.index, state.isPlaying) { while (true) { pos = vm.player.currentPosition(); delay(500) } }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onOpen), shape = RoundedCornerShape(26.dp)) {
        Column {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(song.picUrl, song.name, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text(song.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold); Text(song.artistText, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { vm.player.toggle() }) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "播放/暂停") }
                IconButton(onClick = { vm.player.next() }) { Icon(Icons.Default.SkipNext, "下一首") }
            }
            LinearProgressIndicator(progress = { if (song.durationMs > 0) (pos.toFloat() / song.durationMs).coerceIn(0f, 1f) else 0f }, Modifier.fillMaxWidth().height(2.dp))
        }
    }
}
@Composable fun PlayerScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.player.state.collectAsState(); val lyrics by vm.lyrics.collectAsState()
    val song = state.current; var pos by remember { mutableLongStateOf(0L) }; var showLyrics by remember { mutableStateOf(true) }
    LaunchedEffect(state.index) { song?.let { vm.loadLyrics(it.id) } }
    LaunchedEffect(state.isPlaying, state.index) { while (true) { pos = vm.player.currentPosition(); delay(250) } }
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("‹ 返回") }
        if (song == null) { Text("暂无播放中的歌曲", Modifier.padding(16.dp)); return@Column }
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(song.picUrl, song.name, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(16.dp))
            Column { Text(song.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(song.artistText, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(showLyrics, { showLyrics = true }, label = { Text("歌词（居左 · 逐字）") }); FilterChip(!showLyrics, { showLyrics = false }, label = { Text("封面") })
        }
        if (showLyrics) LyricsView(lyrics, pos, Modifier.weight(1f))
        else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { AsyncImage(song.picUrl, song.name, Modifier.size(280.dp).clip(RoundedCornerShape(26.dp)), contentScale = ContentScale.Crop) }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Slider(pos.toFloat(), { vm.player.seekTo(it.toLong()); pos = it.toLong() }, valueRange = 0f..(song.durationMs.coerceAtLeast(1).toFloat()))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatDuration(pos)); Text(formatDuration(song.durationMs)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.player.previous() }) { Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(36.dp)) }
                FilledIconButton(onClick = { vm.player.toggle() }, Modifier.size(64.dp)) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "播放/暂停", Modifier.size(32.dp)) }
                IconButton(onClick = { vm.player.next() }) { Icon(Icons.Default.SkipNext, "下一首", Modifier.size(36.dp)) }
            }
        }
    }
}
