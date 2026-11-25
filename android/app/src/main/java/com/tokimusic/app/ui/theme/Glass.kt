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

// 大排查 D2/D3：tokiChrome / tokiContentChrome / tokiChromeStrong /
// tokiGlassStrong 无任何调用者，已删（导航胶囊在用下面的
// tokiContentChromeStrong；固定尺寸面走 tokiGlass）。
/** 加强档 chrome（第二轮第 1 项）：32dp 模糊 + 45% veil，与底栏同配方。
 * 0.1.17 第 4 项：底栏色换成主题紫 TokiNavBar（#5C4861）——玻璃开时是
 * 45% 紫 veil 盖在强模糊上，玻璃关时是实色紫（MiniPlayer 与搜索圆钮
 * 共用同一份 barGlass，跟着一起变）。 */
@Composable
fun tokiContentChromeStrong(state: HazeState, backdropBlur: Boolean): Modifier {
    return if (chromeBlurActive(backdropBlur)) {
        Modifier.tokiContentGlassStrong(state).background(TokiNavBar.copy(alpha = TokiGlassParams.strongVeilAlpha))
    } else {
        Modifier.background(TokiNavBar)
    }
}

object TokiGlassParams {
    val blurRadius: Dp = 24.dp
    const val noise: Float = 0.02f

    /**
     * 加强档（第二轮第 1 项）：底栏在亮背景下显透，磨砂要更强。
     * 模糊半径 24dp → 32dp，surface veil 22% → 45%，深色 tint 白 20% → 30%。
     * 底栏（导航胶囊 + 迷你播放器）与悬浮标题共用这一组参数；普通玻璃不动。
     */
    val strongBlurRadius: Dp = 32.dp
    const val strongVeilAlpha: Float = 0.45f
    const val strongDarkTintAlpha: Float = 0.30f

    fun tints(dark: Boolean): List<HazeColorEffect> = if (dark) {
        listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.20f)))
    } else {
        listOf(
            HazeColorEffect.tint(Color(0xFF2A2622).copy(alpha = 0.16f)),
            HazeColorEffect.tint(Color.White.copy(alpha = 0.46f)),
        )
    }

    fun strongTints(dark: Boolean): List<HazeColorEffect> = if (dark) {
        listOf(HazeColorEffect.tint(Color.White.copy(alpha = strongDarkTintAlpha)))
    } else {
        listOf(
            HazeColorEffect.tint(Color(0xFF2A2622).copy(alpha = 0.16f)),
            HazeColorEffect.tint(Color.White.copy(alpha = 0.46f)),
        )
    }

    fun fallback(dark: Boolean): Color = Color.Black.copy(alpha = if (dark) 0.45f else 0.30f)
}

internal fun tokiBlurStyle(dark: Boolean, fade: HazeProgressive? = null, strong: Boolean = false): HazeBlurStyle = HazeBlurStyle {
    blurEnabled(true)
    blurRadius(if (strong) TokiGlassParams.strongBlurRadius else TokiGlassParams.blurRadius)
    noiseFactor(TokiGlassParams.noise)
    backgroundColor(Color.Transparent)
    colorEffects(if (strong) TokiGlassParams.strongTints(dark) else TokiGlassParams.tints(dark))
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

/** 加强档内容源玻璃（底栏/悬浮标题，见 [TokiGlassParams]）。 */
@Composable
fun Modifier.tokiContentGlassStrong(state: HazeState): Modifier {
    val dark = LocalDarkTheme.current
    return hazeBlur(
        input = HazeInput.Sources(state),
        style = tokiBlurStyle(dark, strong = true),
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
 *
 * [strong]（第二轮第 1/3 项）：悬浮标题与底栏同配方——32dp 模糊 + 45% veil。
 */
@Composable
fun TokiScrollChipBackdrop(
    shape: Shape,
    backdropBlur: Boolean,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    strong: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val frosted = backdropBlur && TokiBlurCapability.canSampleInWindow
    val veil = if (strong) TokiGlassParams.strongVeilAlpha else 0.24f
    Box(
        modifier
            .clip(shape)
            .then(
                if (frosted && hazeState != null) {
                    (if (strong) Modifier.tokiContentGlassStrong(hazeState) else Modifier.tokiContentGlass(hazeState))
                        .background(scheme.surfaceContainer.copy(alpha = veil))
                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), shape)
                } else if (!frosted) {
                    Modifier
                        .background(scheme.surfaceContainer)
                        .border(TokiChromeStroke.width, scheme.primary, shape)
                } else {
                    Modifier.background(scheme.surfaceContainer.copy(alpha = veil))
                },
            ),
    )
}
