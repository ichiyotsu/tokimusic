package com.tokimusic.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Cirro 外观色板（按用户提供的 5 张截图取色，见 PLAN.md §1）。
 *
 * Cirro 是固定深色：浏览页纯黑底，卡片深灰，选中 pill 浅灰紫，播放键浅米色。
 * 与 TokiUI 的动态取色 / 种子色不兼容：Cirro 外观开启时，主题模式、动态取色
 * 与种子色全部失效（见 [ThemePreferences.cirroAppearance]）。
 */
val CirroBlack: Color = Color(0xFF000000)
val CirroCard: Color = Color(0xFF1C1C1E)
val CirroMiniPlayer: Color = Color(0xFF2C2C2E)
/** 选中 tab 的 pill：截图里是比底栏浅的灰紫，图标落在其上为深色。 */
val CirroPill: Color = Color(0xFF54545F)
val CirroPillIcon: Color = Color(0xFF131318)
val CirroSecondary: Color = Color(0xFF8E8E93)
val CirroPlayKey: Color = Color(0xFFF5E6D3)
val CirroPlayKeyIcon: Color = Color(0xFF2E1A0C)
val CirroLogoutRed: Color = Color(0xFFFF3B30)
/** 播放器暖棕渐变的回退色（取色失败或封面无彩色时用）。 */
val CirroWarmTop: Color = Color(0xFF5A2A12)
val CirroWarmBottom: Color = Color(0xFF2E1A0C)

/** Cirro 是否开启：为真时锁定深色 + 下面的固定色板。 */
val LocalCirroAppearance = staticCompositionLocalOf { false }

/**
 * Cirro 固定深色 ColorScheme：背景/表面纯黑，卡片走 surfaceContainer 阶梯
 * （Lowest 黑 → Low #0E0E10 → Container #1C1C1E → High #2C2C2E →
 * Highest #3D3D4E），主色为播放键浅米色，错误色为退出红。
 */
fun cirroColorScheme() = darkColorScheme(
    primary = CirroPlayKey, onPrimary = CirroPlayKeyIcon,
    primaryContainer = Color(0xFF3D3D4E), onPrimaryContainer = Color.White,
    inversePrimary = CirroPlayKey,
    secondary = CirroSecondary, onSecondary = Color.Black,
    secondaryContainer = CirroMiniPlayer, onSecondaryContainer = Color.White,
    tertiary = CirroPlayKey, onTertiary = CirroPlayKeyIcon,
    tertiaryContainer = CirroMiniPlayer, onTertiaryContainer = Color.White,
    background = CirroBlack, onBackground = Color.White,
    surface = CirroBlack, onSurface = Color.White,
    surfaceVariant = CirroCard, onSurfaceVariant = CirroSecondary,
    surfaceDim = CirroBlack, surfaceBright = CirroMiniPlayer,
    surfaceContainerLowest = CirroBlack, surfaceContainerLow = Color(0xFF0E0E10),
    surfaceContainer = CirroCard, surfaceContainerHigh = CirroMiniPlayer,
    surfaceContainerHighest = Color(0xFF3D3D4E),
    inverseSurface = Color.White, inverseOnSurface = Color.Black,
    error = CirroLogoutRed, onError = Color.White,
    errorContainer = Color(0xFF5A1A18), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF55555E), outlineVariant = CirroMiniPlayer, scrim = Color.Black,
    surfaceTint = CirroPlayKey,
    primaryFixed = CirroPlayKey, primaryFixedDim = Color(0xFFE8D5B5),
    onPrimaryFixed = CirroPlayKeyIcon, onPrimaryFixedVariant = CirroPlayKeyIcon,
    secondaryFixed = CirroMiniPlayer, secondaryFixedDim = Color(0xFF1C1C1E),
    onSecondaryFixed = Color.White, onSecondaryFixedVariant = CirroSecondary,
    tertiaryFixed = CirroPlayKey, tertiaryFixedDim = Color(0xFFE8D5B5),
    onTertiaryFixed = CirroPlayKeyIcon, onTertiaryFixedVariant = CirroPlayKeyIcon,
)
