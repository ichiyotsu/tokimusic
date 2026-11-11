package com.tokimusic.app.ui.components
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.data.model.Playlist
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.ui.theme.ExpressiveCardShape

@Composable fun SongRow(song: Song, index: Int? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (index != null) { Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp)) }
        AsyncImage(song.picUrl, song.name, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(song.artistText, song.albumName).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
@Composable fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        AsyncImage(playlist.coverUrl, playlist.name, Modifier.size(140.dp).clip(ExpressiveCardShape), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${playlist.trackCount} 首", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun Avatar(url: String, size: Int = 40) {
    AsyncImage(url, "头像", Modifier.size(size.dp).clip(RoundedCornerShape(50)), contentScale = ContentScale.Crop)
}
fun formatDuration(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
fun formatPlayCount(n: Long): String = when { n >= 100_000_000 -> "%.1f亿".format(n / 100_000_000.0); n >= 10_000 -> "%.1f万".format(n / 10_000.0); else -> n.toString() }
