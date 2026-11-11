package com.tokimusic.app.ui.player
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.lyric.YrcParser

/**
 * Lyrics view — LEFT-ALIGNED on purpose (TokiMusic spec, unlike centred karaoke apps):
 * the column scrolls so the active line sits in the middle of the viewport, but every
 * line's text itself starts at the left edge. Word-by-word: sung characters are filled
 * with primary, unsung with onSurfaceVariant; without YRC the whole line highlights.
 */
@Composable fun LyricsView(lines: List<LyricLine>, positionMs: Long, modifier: Modifier = Modifier) {
    val active = remember(lines, positionMs) { YrcParser.activeLineIndex(lines, positionMs) }
    val listState = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) listState.animateScrollToItem(active) }
    LazyColumn(listState, modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 180.dp, horizontal = 24.dp)) {
        itemsIndexed(lines) { i, line ->
            val isActive = i == active
            val annotated = buildAnnotatedString {
                for (word in line.words) {
                    val progress = if (isActive) YrcParser.wordProgress(word, positionMs) else if (i < active) 1f else 0f
                    // Per-word fill: fully sung -> primary; in-progress word splits at the progress fraction.
                    val sungChars = (word.text.length * progress).toInt().coerceIn(0, word.text.length)
                    if (sungChars > 0) withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(word.text.substring(0, sungChars)) }
                    if (sungChars < word.text.length) withStyle(SpanStyle(color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)) { append(word.text.substring(sungChars)) }
                }
            }
            Text(annotated, textAlign = TextAlign.Start, fontSize = if (isActive) 22.sp else 17.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, lineHeight = 30.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            if (!line.translation.isNullOrBlank()) Text(line.translation!!, textAlign = TextAlign.Start, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
    }
}
