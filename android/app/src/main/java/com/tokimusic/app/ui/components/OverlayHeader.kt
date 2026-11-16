package com.tokimusic.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.pressScale
import com.tokimusic.app.ui.theme.TokiScrollChipBackdrop
import com.tokimusic.app.ui.theme.tokiSpace
import dev.chrisbanes.haze.HazeState

/**
 * Large title and optional back button.
 * Overlap pads them with the same frost as the bottom bar. Text stays onSurface.
 * No chip when nothing has scrolled underneath.
 */
@Composable
fun OverlayTitleHeader(
    title: String,
    overlapped: Boolean,
    onBack: (() -> Unit)?,
    onHeightMeasured: (Dp) -> Unit,
    backdropBlur: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val content = MaterialTheme.colorScheme.onSurface
    val cover by animateFloatAsState(if (overlapped) 1f else 0f, label = "scrollChip")
    val titleShape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightMeasured(with(density) { it.height.toDp() }) }
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(horizontal = tokiSpace("lg")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TokiScrollBackChip(overlapped, backdropBlur, hazeState, onBack)
            Spacer(Modifier.width(tokiSpace("md")))
        }
        Box(
            Modifier
                .weight(1f, fill = false)
                .heightIn(min = 56.dp)
                .clip(titleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            TokiScrollChipBackdrop(
                shape = titleShape,
                cover = cover,
                backdropBlur = backdropBlur,
                hazeState = hazeState,
                applyBlur = true,
                modifier = Modifier.matchParentSize(),
            )
            Text(
                title,
                modifier = Modifier.padding(horizontal = tokiSpace("lg")),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TokiScrollBackChip(
    overlapped: Boolean,
    backdropBlur: Boolean,
    hazeState: HazeState,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val cover by animateFloatAsState(if (overlapped) 1f else 0f, label = "backChip")
    val content = MaterialTheme.colorScheme.onSurface
    TokiLongPressLabel("返回") {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = ripple()) { onClick() }
                .pressScale(interaction),
            contentAlignment = Alignment.Center,
        ) {
            TokiScrollChipBackdrop(
                shape = CircleShape,
                cover = cover,
                backdropBlur = backdropBlur,
                hazeState = hazeState,
                applyBlur = true,
                modifier = Modifier.matchParentSize(),
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = content,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
