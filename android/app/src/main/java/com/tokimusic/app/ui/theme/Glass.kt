package com.tokimusic.app.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeFeatureFlags
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Capsule and title chrome follow TokiStudio at TokiUI 0.1.4.
 * [HazeInput.Backdrop] samples pixels already drawn in the window.
 * Light frost is a 16% warm dark wash then 46% white. Dark frost is 20% white.
 * Glass off or sampling unavailable: opaque surfaceContainer, and callers add
 * the shared 3dp primary stroke. No second opaque fill on a frosted title.
 */
@OptIn(ExperimentalHazeApi::class)
object TokiBlurCapability {
    init {
        HazeFeatureFlags.isPlatformBackdropEnabled = true
    }

    val canSampleInWindow: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

fun chromeBlurActive(backdropBlur: Boolean): Boolean =
    backdropBlur && TokiBlurCapability.canSampleInWindow

@Composable
fun tokiChrome(state: HazeState, backdropBlur: Boolean): Modifier {
    return if (chromeBlurActive(backdropBlur)) {
        Modifier.tokiGlass(state)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    }
}

object TokiGlassParams {
    val blurRadius: Dp = 24.dp
    const val noise: Float = 0.02f

    fun tints(dark: Boolean): List<HazeColorEffect> = if (dark) {
        listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.20f)))
    } else {
        listOf(
            HazeColorEffect.tint(Color(0xFF2A2622).copy(alpha = 0.16f)),
            HazeColorEffect.tint(Color.White.copy(alpha = 0.46f)),
        )
    }

    fun fallback(dark: Boolean): Color = Color.Black.copy(alpha = if (dark) 0.45f else 0.30f)
}

internal fun tokiBlurStyle(dark: Boolean, fade: HazeProgressive? = null): HazeBlurStyle = HazeBlurStyle {
    blurEnabled(true)
    blurRadius(TokiGlassParams.blurRadius)
    noiseFactor(TokiGlassParams.noise)
    backgroundColor(Color.Transparent)
    colorEffects(TokiGlassParams.tints(dark))
    fallbackColorEffect(HazeColorEffect.tint(TokiGlassParams.fallback(dark)))
    if (fade != null) progressive(fade)
}

@Composable
fun Modifier.tokiGlass(state: HazeState): Modifier {
    val dark = LocalDarkTheme.current
    return hazeBlur(
        input = HazeInput.Backdrop(state),
        style = tokiBlurStyle(dark),
        performanceMode = HazePerformanceMode.Quality,
    )
}

object TokiGlassFade {
    val topFadeExtra: Dp = 56.dp
}

@Composable
fun Modifier.tokiGlassFade(state: HazeState, fadeFromTop: Boolean): Modifier {
    val dark = LocalDarkTheme.current
    val fade = if (fadeFromTop) {
        HazeProgressive.LinearGradient(
            start = Offset(0f, 0f), startIntensity = 1f,
            end = Offset(0f, 1f), endIntensity = 0f,
        )
    } else {
        HazeProgressive.LinearGradient(
            start = Offset(0f, 0f), startIntensity = 0f,
            end = Offset(0f, 1f), endIntensity = 1f,
        )
    }
    return hazeBlur(
        input = HazeInput.Backdrop(state),
        style = tokiBlurStyle(dark, fade),
        performanceMode = HazePerformanceMode.Quality,
    )
}

/** Solid bar and solid title chips share this stroke. Glass sampling adds none. */
object TokiChromeStroke {
    val width: Dp = 3.dp
    val barGap: Dp = 6.dp
}

/**
 * Scroll title / back chip. Frosted sampling draws only [tokiGlass].
 * Otherwise opaque surfaceContainer plus a 3dp primary stroke.
 * [cover] is 0 when content is not underneath, so nothing is painted.
 */
@Composable
fun TokiScrollChipBackdrop(
    shape: Shape,
    cover: Float,
    backdropBlur: Boolean,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    applyBlur: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val frosted = backdropBlur && TokiBlurCapability.canSampleInWindow
    val canBlur = applyBlur && hazeState != null && cover > 0.01f && frosted
    Box(
        modifier
            .clip(shape)
            .then(if (canBlur) Modifier.tokiGlass(hazeState) else Modifier)
            .then(
                if (!frosted && cover > 0f) {
                    Modifier
                        .background(scheme.surfaceContainer.copy(alpha = cover))
                        .border(TokiChromeStroke.width, scheme.primary.copy(alpha = cover), shape)
                } else {
                    Modifier
                },
            ),
    )
}
