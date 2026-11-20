package com.tokimusic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
import com.tokiui.semantic.SemanticTokens

/**
 * Material 3 Expressive theme.
 *
 * Colors: dynamic color, or a semantic-token seed through SchemeTonalSpot.
 * Motion: expressive by default, standard when reduce-motion is on.
 * Shapes and the type scale sizes are Material defaults.
 * Latin is Inter, Chinese is Source Han Sans. Titles and labels are Bold.
 */
enum class ThemeMode { System, Light, Dark }

val LocalDarkTheme = staticCompositionLocalOf { false }

data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.System,
    val useDynamicColor: Boolean = true,
    val seedIndex: Int = 0,
    val reduceMotion: Boolean = false,
    val backdropBlur: Boolean = true,
    /** Cirro 外观（用户 2026-11-21 确认）：锁定深色 + 固定色板，动态取色与种子色在其下失效。 */
    val cirroAppearance: Boolean = true,
)

private fun Typography.withTokiType(family: FontFamily): Typography {
    fun TextStyle.bold() = copy(fontFamily = family, fontWeight = FontWeight.Bold)
    fun TextStyle.regular() = copy(fontFamily = family, fontWeight = FontWeight.Normal)
    return Typography(
        displayLarge = displayLarge.bold(), displayMedium = displayMedium.bold(), displaySmall = displaySmall.bold(),
        headlineLarge = headlineLarge.bold(), headlineMedium = headlineMedium.bold(), headlineSmall = headlineSmall.bold(),
        titleLarge = titleLarge.bold(), titleMedium = titleMedium.bold(), titleSmall = titleSmall.bold(),
        bodyLarge = bodyLarge.regular(), bodyMedium = bodyMedium.regular(), bodySmall = bodySmall.regular(),
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
    mode: ThemeMode = ThemeMode.System,
    tokens: SemanticTokens = TokiUiHolder.semantic,
    useDynamicColor: Boolean = true,
    seedIndex: Int = 0,
    reduceMotion: Boolean = false,
    cirroAppearance: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = if (cirroAppearance) true else when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // Touch the capability object before any Haze node is created.
    TokiBlurCapability.canSampleInWindow
    val colorScheme = if (cirroAppearance) cirroColorScheme()
        else resolveColorScheme(tokens, dark, useDynamicColor, seedIndex)
    // MaterialExpressiveTheme does not provide LocalContentColor: bare Text()
    // would default to black and vanish on dark backgrounds. Provide the
    // theme's onBackground here, as TokiStudio 0.1.10-aligned apps do.
    CompositionLocalProvider(
        LocalDarkTheme provides dark,
        LocalCirroAppearance provides cirroAppearance,
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
