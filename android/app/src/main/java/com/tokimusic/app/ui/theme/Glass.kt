package com.tokimusic.app.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
 * Capsule chrome follows TokiStudio 0.1.2 (the Android implementation record):
 * Haze [HazeInput.Backdrop] samples pixels already drawn in the window, and
 * falls back to the hazeSource content root. Sampling is not a blur of the
 * control's own pixels. With the glass toggle off, or when sampling is unavailable,
 * the capsule uses opaque surfaceContainer.
 *
 * The numeric frost is the TokiStudio Glass.kt recipe at the pinned submodule.
 * Semantic tokens do not define a second blur table, and this is not the retired
 * TokiNote 30dp tint stack.
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

    fun frost(dark: Boolean): Color = Color.White.copy(alpha = if (dark) 0.20f else 0.40f)

    fun fallback(dark: Boolean): Color = Color.Black.copy(alpha = if (dark) 0.45f else 0.30f)
}

internal fun tokiBlurStyle(dark: Boolean, fade: HazeProgressive? = null): HazeBlurStyle = HazeBlurStyle {
    blurEnabled(true)
    blurRadius(TokiGlassParams.blurRadius)
    noiseFactor(TokiGlassParams.noise)
    backgroundColor(Color.Transparent)
    colorEffects(listOf(HazeColorEffect.tint(TokiGlassParams.frost(dark))))
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
