package com.tokimusic.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Material 3 Expressive theme.
 *
 * Colors: TokiMusic 固定深色取色方案（见 [tokiMusicColorScheme]）——
 * 应用唯一外观，锁定深色。
 * Motion: expressive by default, standard when reduce-motion is on.
 * Shapes and the type scale sizes are Material defaults.
 * Latin is Inter, Chinese is Source Han Sans. All text is Bold
 * (user 2026-11-21: global Bold, intentionally deviates from the
 * TokiUI 0.2.0-b1 rule of titles Bold / body Regular).
 */

val LocalDarkTheme = staticCompositionLocalOf { false }

data class ThemePreferences(
    val reduceMotion: Boolean = false,
    // 0.1.21 第 4 项：backdropBlur 字段已删——玻璃模糊开关与实色
    // 预设一并删除，外观固定玻璃，TokiMusicApp 统一传固定值 true。
)

private fun Typography.withTokiType(family: FontFamily): Typography {
    fun TextStyle.bold() = copy(fontFamily = family, fontWeight = FontWeight.Bold)
    return Typography(
        displayLarge = displayLarge.bold(), displayMedium = displayMedium.bold(), displaySmall = displaySmall.bold(),
        headlineLarge = headlineLarge.bold(), headlineMedium = headlineMedium.bold(), headlineSmall = headlineSmall.bold(),
        titleLarge = titleLarge.bold(), titleMedium = titleMedium.bold(), titleSmall = titleSmall.bold(),
        bodyLarge = bodyLarge.bold(), bodyMedium = bodyMedium.bold(), bodySmall = bodySmall.bold(),
        labelLarge = labelLarge.bold(), labelMedium = labelMedium.bold(), labelSmall = labelSmall.bold(),
        // TokiStudio 0.1.10: the 15 emphasized styles, all Bold like the base scale.
        displayLargeEmphasized = displayLargeEmphasized.bold(), displayMediumEmphasized = displayMediumEmphasized.bold(), displaySmallEmphasized = displaySmallEmphasized.bold(),
        headlineLargeEmphasized = headlineLargeEmphasized.bold(), headlineMediumEmphasized = headlineMediumEmphasized.bold(), headlineSmallEmphasized = headlineSmallEmphasized.bold(),
        titleLargeEmphasized = titleLargeEmphasized.bold(), titleMediumEmphasized = titleMediumEmphasized.bold(), titleSmallEmphasized = titleSmallEmphasized.bold(),
        bodyLargeEmphasized = bodyLargeEmphasized.bold(), bodyMediumEmphasized = bodyMediumEmphasized.bold(), bodySmallEmphasized = bodySmallEmphasized.bold(),
        labelLargeEmphasized = labelLargeEmphasized.bold(), labelMediumEmphasized = labelMediumEmphasized.bold(), labelSmallEmphasized = labelSmallEmphasized.bold(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TokiMusicTheme(
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 固定深色（LocalDarkTheme 供 Glass.kt 的玻璃 helper 用）。
    val dark = true
    // Touch the capability object before any Haze node is created.
    TokiBlurCapability.canSampleInWindow
    val colorScheme = tokiMusicColorScheme()
    // MaterialExpressiveTheme does not provide LocalContentColor: bare Text()
    // would default to black and vanish on dark backgrounds. Provide the
    // theme's onBackground here, as TokiStudio 0.1.10-aligned apps do.
    CompositionLocalProvider(
        LocalDarkTheme provides dark,
        LocalContentColor provides colorScheme.onBackground,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            shapes = Shapes(),
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            typography = Typography().withTokiType(InterFamily),
            content = content,
        )
    }
}
