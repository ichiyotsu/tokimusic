package com.tokimusic.app.ui.player
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.lyric.YrcParser
import com.tokimusic.app.ui.theme.CoverColors
import kotlin.math.abs

/**
 * Immersive lyrics — LEFT-ALIGNED on purpose (TokiMusic spec, unlike centred
 * karaoke apps). Drawn over the player's cover-color gradient, so every line
 * is white with distance-based alpha: the active line sits anchored about
 * 28% down the viewport, lines further away fade out. Word-by-word: sung
 * characters are solid white, unsung are dimmed; with [karaoke] off the
 * active line is uniformly bright. Without YRC the whole line highlights at
 * once. Cirro: active line 28sp Bold, translation 16sp below it, top/bottom
 * 32dp edge fades into the cover gradient.
 */
@Composable fun LyricsView(lines: List<LyricLine>, positionMs: Long, modifier: Modifier = Modifier, karaoke: Boolean = true, showTranslation: Boolean = true, coverColors: CoverColors? = null) {
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
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().onSizeChanged { viewportH = it.height },
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
                    style = if (isActive) MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp) else MaterialTheme.typography.titleLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
                if (showTranslation && !line.translation.isNullOrBlank()) Text(
                    line.translation!!,
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = if (isActive) 0.7f else (dim * 0.5f).coerceAtMost(0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }
        }
        if (coverColors != null) {
            // 上下 32dp 淡出到封面渐变的上下端色；淡出层不处理触摸，不挡滚动。
            Box(
                Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                    Brush.verticalGradient(listOf(coverColors.top, Color.Transparent)),
                ),
            )
            Box(
                Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, coverColors.bottom)),
                ),
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
