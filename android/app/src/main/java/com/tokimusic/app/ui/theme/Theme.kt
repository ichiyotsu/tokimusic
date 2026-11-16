package com.tokimusic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // Touch the capability object before any Haze node is created.
    TokiBlurCapability.canSampleInWindow
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialExpressiveTheme(
            colorScheme = resolveColorScheme(tokens, dark, useDynamicColor, seedIndex),
            shapes = Shapes(),
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            typography = Typography().withTokiType(InterFamily),
            content = content,
        )
    }
}
