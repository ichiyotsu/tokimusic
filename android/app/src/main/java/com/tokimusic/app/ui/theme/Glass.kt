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
 * Capsule and title chrome follow TokiStudio 0.1.10.
 * [HazeInput.Backdrop] samples pixels already drawn in the window.
 * Light frost is a 16% warm dark wash then 46% white. Dark frost is 20% white.
 * Glass off or sampling unavailable: opaque surfaceContainer, and callers add
 * the shared 3dp primary stroke. No second opaque fill on a frosted title.
 *
 * 0.1.10 rewrote the scroll-title chip: the frost is ALWAYS on (the old
 * scroll-overlap reporting, which kept losing the blur on 0.1.7/0.1.8/0.1.9,
 * is deleted). Size-changing surfaces (nav capsule, title chips) sample from
 * the content source ([tokiContentGlass]) so the blur never black-edges
 * during a size animation; fixed-size surfaces keep [tokiGlass].
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

/**
 * Fixed-size glass surface (side button, floating toolbar): Haze background
 * blur when on and sampleable, else opaque surfaceContainer. Frosted glass
 * also carries a 22% surfaceContainer veil, as in TokiStudio 0.1.10.
 */
@Composable
fun tokiChrome(state: HazeState, backdropBlur: Boolean): Modifier {
    return if (chromeBlurActive(backdropBlur)) {
        Modifier.tokiGlass(state).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.22f))
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    }
}

/**
 * Chrome for size-changing surfaces (floating nav capsule): same treatment
 * as [tokiChrome] (frost + 22% surface veil, opaque fallback), only the
 * sampling switches to the content source ([tokiContentGlass]) — the capsule
 * width animates as its label expands, and window-backdrop sampling black-edges
 * during that size animation while content-source sampling does not.
 */
@Composable
fun tokiContentChrome(state: HazeState, backdropBlur: Boolean): Modifier {
    return if (chromeBlurActive(backdropBlur)) {
        Modifier.tokiContentGlass(state).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.22f))
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

/**
 * Content-source glass: same parameters as [tokiGlass] (radius/tint/fallback/
 * noise unchanged), only the input changes — [HazeInput.Sources] consumes the
 * `hazeSource` content root directly. For surfaces whose size animates (nav
 * capsule, title chips): platform window-background sampling black-edges or
 * drops out during the size animation; content-source sampling redraws through
 * Haze and stays complete. (The TokiUI blur ban covers the content-input
 * mode for navigation; `Sources` is the sanctioned 0.1.10 replacement.)
 */
@Composable
fun Modifier.tokiContentGlass(state: HazeState): Modifier {
    val dark = LocalDarkTheme.current
    return hazeBlur(
        input = HazeInput.Sources(state),
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
 * Scroll title / back chip. TokiStudio 0.1.10: the frost is ALWAYS on — no
 * scroll reporting, no cover animation. Frosted: content-source glass + a
 * 24% surfaceContainer veil + a 1dp outlineVariant stroke so the chip reads
 * on any background. Otherwise opaque surfaceContainer plus a 3dp primary
 * stroke (same weight as the solid bottom bar).
 */
@Composable
fun TokiScrollChipBackdrop(
    shape: Shape,
    backdropBlur: Boolean,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val frosted = backdropBlur && TokiBlurCapability.canSampleInWindow
    Box(
        modifier
            .clip(shape)
            .then(
                if (frosted && hazeState != null) {
                    Modifier.tokiContentGlass(hazeState)
                        .background(scheme.surfaceContainer.copy(alpha = 0.24f))
                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), shape)
                } else if (!frosted) {
                    Modifier
                        .background(scheme.surfaceContainer)
                        .border(TokiChromeStroke.width, scheme.primary, shape)
                } else {
                    Modifier.background(scheme.surfaceContainer.copy(alpha = 0.24f))
                },
            ),
    )
}
