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
import com.tokiui.semantic.SemanticTokens

/**
 * Material 3 Expressive theme.
 *
 * Colors: dynamic color, or a semantic-token seed through SchemeTonalSpot.
 * Motion: expressive by default, standard when reduce-motion is on.
 * Shapes and the type scale are Material defaults. Only the font family changes.
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

private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
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
            typography = Typography().withFamily(GoogleSansFamily),
            content = content,
        )
    }
}
