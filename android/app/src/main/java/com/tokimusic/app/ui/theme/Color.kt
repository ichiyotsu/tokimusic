package com.tokimusic.app.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.tokiui.semantic.SemanticTokens
import com.tokiui.semantic.SeedSpec

/**
 * Runtime color comes from Material dynamic color, or from a semantic-token seed
 * run through HCT / SchemeTonalSpot. The reference palette inside the token file
 * is not copied into ColorScheme.
 */
fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seed.toArgb()), dark, 0.0)
    val m = MaterialDynamicColors()
    fun c(get: MaterialDynamicColors.() -> com.materialkolor.dynamiccolor.DynamicColor): Color =
        Color(m.get().getArgb(scheme))
    return if (dark) {
        darkColorScheme(
            primary = c { primary() }, onPrimary = c { onPrimary() },
            primaryContainer = c { primaryContainer() }, onPrimaryContainer = c { onPrimaryContainer() },
            inversePrimary = c { inversePrimary() },
            secondary = c { secondary() }, onSecondary = c { onSecondary() },
            secondaryContainer = c { secondaryContainer() }, onSecondaryContainer = c { onSecondaryContainer() },
            tertiary = c { tertiary() }, onTertiary = c { onTertiary() },
            tertiaryContainer = c { tertiaryContainer() }, onTertiaryContainer = c { onTertiaryContainer() },
            background = c { background() }, onBackground = c { onBackground() },
            surface = c { surface() }, onSurface = c { onSurface() },
            surfaceVariant = c { surfaceVariant() }, onSurfaceVariant = c { onSurfaceVariant() },
            surfaceDim = c { surfaceDim() }, surfaceBright = c { surfaceBright() },
            surfaceContainerLowest = c { surfaceContainerLowest() }, surfaceContainerLow = c { surfaceContainerLow() },
            surfaceContainer = c { surfaceContainer() }, surfaceContainerHigh = c { surfaceContainerHigh() },
            surfaceContainerHighest = c { surfaceContainerHighest() },
            inverseSurface = c { inverseSurface() }, inverseOnSurface = c { inverseOnSurface() },
            error = c { error() }, onError = c { onError() },
            errorContainer = c { errorContainer() }, onErrorContainer = c { onErrorContainer() },
            outline = c { outline() }, outlineVariant = c { outlineVariant() }, scrim = c { scrim() },
            surfaceTint = c { surfaceTint() },
            primaryFixed = c { primaryFixed() }, primaryFixedDim = c { primaryFixedDim() },
            onPrimaryFixed = c { onPrimaryFixed() }, onPrimaryFixedVariant = c { onPrimaryFixedVariant() },
            secondaryFixed = c { secondaryFixed() }, secondaryFixedDim = c { secondaryFixedDim() },
            onSecondaryFixed = c { onSecondaryFixed() }, onSecondaryFixedVariant = c { onSecondaryFixedVariant() },
            tertiaryFixed = c { tertiaryFixed() }, tertiaryFixedDim = c { tertiaryFixedDim() },
            onTertiaryFixed = c { onTertiaryFixed() }, onTertiaryFixedVariant = c { onTertiaryFixedVariant() },
        )
    } else {
        lightColorScheme(
            primary = c { primary() }, onPrimary = c { onPrimary() },
            primaryContainer = c { primaryContainer() }, onPrimaryContainer = c { onPrimaryContainer() },
            inversePrimary = c { inversePrimary() },
            secondary = c { secondary() }, onSecondary = c { onSecondary() },
            secondaryContainer = c { secondaryContainer() }, onSecondaryContainer = c { onSecondaryContainer() },
            tertiary = c { tertiary() }, onTertiary = c { onTertiary() },
            tertiaryContainer = c { tertiaryContainer() }, onTertiaryContainer = c { onTertiaryContainer() },
            background = c { background() }, onBackground = c { onBackground() },
            surface = c { surface() }, onSurface = c { onSurface() },
            surfaceVariant = c { surfaceVariant() }, onSurfaceVariant = c { onSurfaceVariant() },
            surfaceDim = c { surfaceDim() }, surfaceBright = c { surfaceBright() },
            surfaceContainerLowest = c { surfaceContainerLowest() }, surfaceContainerLow = c { surfaceContainerLow() },
            surfaceContainer = c { surfaceContainer() }, surfaceContainerHigh = c { surfaceContainerHigh() },
            surfaceContainerHighest = c { surfaceContainerHighest() },
            inverseSurface = c { inverseSurface() }, inverseOnSurface = c { inverseOnSurface() },
            error = c { error() }, onError = c { onError() },
            errorContainer = c { errorContainer() }, onErrorContainer = c { onErrorContainer() },
            outline = c { outline() }, outlineVariant = c { outlineVariant() }, scrim = c { scrim() },
            surfaceTint = c { surfaceTint() },
            primaryFixed = c { primaryFixed() }, primaryFixedDim = c { primaryFixedDim() },
            onPrimaryFixed = c { onPrimaryFixed() }, onPrimaryFixedVariant = c { onPrimaryFixedVariant() },
            secondaryFixed = c { secondaryFixed() }, secondaryFixedDim = c { secondaryFixedDim() },
            onSecondaryFixed = c { onSecondaryFixed() }, onSecondaryFixedVariant = c { onSecondaryFixedVariant() },
            tertiaryFixed = c { tertiaryFixed() }, tertiaryFixedDim = c { tertiaryFixedDim() },
            onTertiaryFixed = c { onTertiaryFixed() }, onTertiaryFixedVariant = c { onTertiaryFixedVariant() },
        )
    }
}

fun SeedSpec.toComposeColor(): Color = Color(android.graphics.Color.parseColor(value))

/** Android 12+ dynamic color. minSdk 36 always satisfies this. */
val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun resolveColorScheme(tokens: SemanticTokens, dark: Boolean, useDynamic: Boolean, seedIndex: Int): ColorScheme {
    val base = if (useDynamic && supportsDynamicColor) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val seed = tokens.seeds[seedIndex.coerceIn(0, tokens.seeds.lastIndex)]
        schemeFromSeed(seed.toComposeColor(), dark)
    }
    // TokiStudio 0.1.10: dark-mode contrast post-processing (see withDarkContrast).
    return if (dark) base.withDarkContrast() else base
}

/**
 * Dark-mode contrast lift, as in TokiStudio 0.1.10: background and surface
 * darken 8%, the surfaceContainer ladder lightens from 3% (lowest) to 9%
 * (highest). Overlaying black/white only changes lightness; hue and
 * saturation stay as the official scheme made them.
 */
private fun ColorScheme.withDarkContrast(): ColorScheme {
    fun Color.darken(alpha: Float): Color = Color.Black.copy(alpha = alpha).compositeOver(this)
    fun Color.lighten(alpha: Float): Color = Color.White.copy(alpha = alpha).compositeOver(this)
    return copy(
        background = background.darken(0.08f),
        surface = surface.darken(0.08f),
        surfaceDim = surfaceDim.darken(0.08f),
        surfaceContainerLowest = surfaceContainerLowest.lighten(0.03f),
        surfaceContainerLow = surfaceContainerLow.lighten(0.04f),
        surfaceContainer = surfaceContainer.lighten(0.05f),
        surfaceContainerHigh = surfaceContainerHigh.lighten(0.07f),
        surfaceContainerHighest = surfaceContainerHighest.lighten(0.09f),
    )
}

/**
 * Whole-app page background, as in TokiStudio 0.1.10: a vertical gradient
 * inside the surface family (surfaceContainer → surface → background), not a
 * large theme-colour gradient. The same roles give a dark in-family gradient
 * in dark mode; its contrast comes from [ColorScheme.withDarkContrast].
 */
@Composable
fun tokiPageBackground(): Brush {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    return Brush.verticalGradient(listOf(scheme.surfaceContainer, scheme.surface, scheme.background))
}
