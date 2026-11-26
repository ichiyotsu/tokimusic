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
 * Sampling unavailable: opaque surfaceContainer. No second opaque fill on
 * a frosted title. (0.1.21 第 4 项：玻璃关的实色预设——原 TokiUI 的
 * 莫奈悬浮底栏那套，含 3dp primary 描边——已整套删除，全应用只剩
 * 玻璃一条外观路径；这里的 opaque 分支只是采样不可用时的兜底。)
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

// 大排查 D2/D3：tokiChrome / tokiContentChrome / tokiChromeStrong /
// tokiGlassStrong 无任何调用者，已删（导航胶囊在用下面的
// tokiContentChromeStrong；固定尺寸面走 tokiGlass）。
// 0.1.21 第 4 项：chromeBlurActive 已删——玻璃模糊开关删除后不再有
// 「设置 && 能力」的组合判断，各处直接看 TokiBlurCapability。
/** 加强档 chrome（第二轮第 1 项）：32dp 模糊 + 45% veil，与底栏同配方。
 * 0.1.18 第 22 项：回 0.1.15 的样子——0.1.17 第 4 项换的主题紫
 * TokiNavBar（#5C4861）撤掉。0.1.21 第 4 项：玻璃关的实色分支删除，
 * 本函数只剩玻璃配方；采样不可用时 Haze 样式自带 fallback 上色兜底。 */
@Composable
fun tokiContentChromeStrong(state: HazeState): Modifier {
    return Modifier.tokiContentGlassStrong(state).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = TokiGlassParams.strongVeilAlpha))
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

    /**
     * MiniPlayer 专用档（0.1.18 第 25 项）：模糊半径约 85dp（远强于
     * strong 的 32dp）。0.1.19 第 5 项起不再是零底色：纯模糊在纯黑
     * 背景上不可见，另加一层动态底（见 miniPlayerGlassVeil），底色
     * 不进 Haze 的 colorEffects、由 tokiContentGlassMiniPlayer 以
     * background 形式叠在模糊之上。
     */
    val miniPlayerBlurRadius: Dp = 85.dp

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

    // 0.1.21 第 7 项：浅色回退改白色 72%（原来黑 30% 是深色时代的值，
    // 浅色模式下会把标题片/底栏糊成一块灰黑）。深色黑 45% 不变。
    fun fallback(dark: Boolean): Color =
        if (dark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.72f)
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

/**
 * MiniPlayer 专用玻璃（0.1.18 第 25 项）：85dp 强模糊；Haze 样式
 * 本身仍不上色（colorEffects 为空、fallback 全透明）。0.1.19 第 5 项：
 * 纯模糊在完全黑色的背景上糊出来也是黑、MiniPlayer 看不见——在
 * 模糊之上叠一层动态底 [veil]（background 形式，与
 * tokiContentChromeStrong 的 veil 同款叠法）：基准 25% 深灰，按
 * 当前歌曲封面亮度动态提亮/加深（见 miniPlayerGlassVeil）。噪点与
 * 全应用玻璃同值 0.02（磨砂质感，不是上色）。0.1.21 第 4 项：玻璃
 * 关的实色回退已删，MiniPlayer 恒套这个 modifier。
 */
@Composable
fun Modifier.tokiContentGlassMiniPlayer(state: HazeState, veil: Color): Modifier {
    return hazeBlur(
        input = HazeInput.Sources(state),
        style = HazeBlurStyle {
            blurEnabled(true)
            blurRadius(TokiGlassParams.miniPlayerBlurRadius)
            noiseFactor(TokiGlassParams.noise)
            backgroundColor(Color.Transparent)
            colorEffects(emptyList())
            fallbackColorEffect(HazeColorEffect.tint(Color.Transparent))
        },
        performanceMode = HazePerformanceMode.Quality,
    ).background(veil)
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

/**
 * Scroll title / back chip. TokiStudio 0.1.10: the frost is ALWAYS on — no
 * scroll reporting, no cover animation. Frosted: content-source glass + a
 * 24% surfaceContainer veil + a 1dp outlineVariant stroke so the chip reads
 * on any background. Sampling unavailable (or no haze state): opaque
 * surfaceContainer at the veil alpha. 0.1.21 第 4 项：原「未磨砂 =
 * 实色 + 3dp primary 描边」的实色预设分支已删（描边常量随之删除）。
 *
 * [strong]（第二轮第 1/3 项）：悬浮标题与底栏同配方——32dp 模糊 + 45% veil。
 */
@Composable
fun TokiScrollChipBackdrop(
    shape: Shape,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    strong: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val frosted = TokiBlurCapability.canSampleInWindow
    val veil = if (strong) TokiGlassParams.strongVeilAlpha else 0.24f
    Box(
        modifier
            .clip(shape)
            .then(
                if (frosted && hazeState != null) {
                    (if (strong) Modifier.tokiContentGlassStrong(hazeState) else Modifier.tokiContentGlass(hazeState))
                        .background(scheme.surfaceContainer.copy(alpha = veil))
                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), shape)
                } else {
                    Modifier.background(scheme.surfaceContainer.copy(alpha = veil))
                },
            ),
    )
}
