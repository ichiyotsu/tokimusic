package com.tokimusic.app.ui.player
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.lyric.YrcParser
import kotlin.math.abs

/**
 * Immersive lyrics — LEFT-ALIGNED on purpose (TokiMusic spec, unlike centred
 * karaoke apps). Drawn over the player's dark scrim, so every line is white
 * with distance-based alpha: the active line sits anchored about 28% down the
 * viewport, lines further away fade out. Word-by-word: sung characters are
 * solid white, unsung are dimmed; with [karaoke] off the active line is
 * uniformly bright. Without YRC the whole line highlights at once.
 */
@Composable fun LyricsView(lines: List<LyricLine>, positionMs: Long, modifier: Modifier = Modifier, karaoke: Boolean = true) {
    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.6f))
        }
        return
    }
    val active = remember(lines, positionMs) { YrcParser.activeLineIndex(lines, positionMs) }
    val listState = rememberLazyListState()
    var viewportH by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val anchorPx = (viewportH * 0.28f).toInt()
    LaunchedEffect(active, viewportH) {
        if (active >= 0 && viewportH > 0) listState.animateScrollToItem(active, scrollOffset = -anchorPx)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().onSizeChanged { viewportH = it.height },
        contentPadding = PaddingValues(
            top = if (viewportH > 0) with(density) { anchorPx.toDp() } else 120.dp,
            bottom = if (viewportH > 0) with(density) { (viewportH * 0.6f).toInt().toDp() } else 240.dp,
        ),
    ) {
        itemsIndexed(lines) { i, line ->
            val isActive = i == active
            val distance = if (active < 0) i + 1 else abs(i - active)
            val dim = lineAlpha(distance)
            val annotated = buildAnnotatedString {
                for (word in line.words) {
                    when {
                        isActive && karaoke -> {
                            val progress = YrcParser.wordProgress(word, positionMs)
                            val sungChars = (word.text.length * progress).toInt().coerceIn(0, word.text.length)
                            if (sungChars > 0) withStyle(SpanStyle(color = Color.White)) { append(word.text.substring(0, sungChars)) }
                            if (sungChars < word.text.length) withStyle(SpanStyle(color = Color.White.copy(alpha = 0.45f))) { append(word.text.substring(sungChars)) }
                        }
                        isActive -> withStyle(SpanStyle(color = Color.White)) { append(word.text) }
                        else -> withStyle(SpanStyle(color = Color.White.copy(alpha = dim))) { append(word.text) }
                    }
                }
            }
            Text(
                annotated,
                textAlign = TextAlign.Start,
                style = if (isActive) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
            if (!line.translation.isNullOrBlank()) Text(
                line.translation!!,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = (dim * 0.7f).coerceAtMost(0.45f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
        }
    }
}

private fun lineAlpha(distance: Int): Float = when (distance) {
    0 -> 1f
    1 -> 0.6f
    2 -> 0.4f
    3 -> 0.28f
    else -> 0.18f
}
