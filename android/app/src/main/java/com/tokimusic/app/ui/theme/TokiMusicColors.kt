package com.tokimusic.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * TokiMusic 取色方案：固定深色 + 固定色板（颜色按用户提供的 5 张截图取定）。
 *
 * 应用唯一外观：锁定深色。
 */
val TokiBlack: Color = Color(0xFF000000)
val TokiCard: Color = Color(0xFF1C1C1E)
val TokiMiniPlayer: Color = Color(0xFF2C2C2E)
/** 选中 tab 的 pill：截图里是比底栏浅的灰紫，图标落在其上为深色。 */
val TokiPill: Color = Color(0xFF54545F)
val TokiPillIcon: Color = Color(0xFF131318)
/** 主页底栏色（0.1.17 第 4 项，用户指定 #5C4861）：淡紫色，软件主题色（底栏用）。 */
val TokiNavBar: Color = Color(0xFF5C4861)
val TokiSecondary: Color = Color(0xFF8E8E93)
val TokiPlayKey: Color = Color(0xFFF5E6D3)
val TokiPlayKeyIcon: Color = Color(0xFF2E1A0C)
val TokiLogoutRed: Color = Color(0xFFFF3B30)
/** 播放器暖棕渐变的回退色（取色失败或封面无彩色时用）。 */
val TokiWarmTop: Color = Color(0xFF5A2A12)
val TokiWarmBottom: Color = Color(0xFF2E1A0C)

/**
 * TokiMusic 固定深色 ColorScheme：背景/表面纯黑，卡片走 surfaceContainer 阶梯
 * （Lowest 黑 → Low #0E0E10 → Container #1C1C1E → High #2C2C2E →
 * Highest #3D3D4E），主色为播放键浅米色，错误色为退出红。
 */
fun tokiMusicColorScheme() = darkColorScheme(
    primary = TokiPlayKey, onPrimary = TokiPlayKeyIcon,
    primaryContainer = Color(0xFF3D3D4E), onPrimaryContainer = Color.White,
    inversePrimary = TokiPlayKey,
    secondary = TokiSecondary, onSecondary = Color.Black,
    secondaryContainer = TokiMiniPlayer, onSecondaryContainer = Color.White,
    tertiary = TokiPlayKey, onTertiary = TokiPlayKeyIcon,
    tertiaryContainer = TokiMiniPlayer, onTertiaryContainer = Color.White,
    background = TokiBlack, onBackground = Color.White,
    surface = TokiBlack, onSurface = Color.White,
    surfaceVariant = TokiCard, onSurfaceVariant = TokiSecondary,
    surfaceDim = TokiBlack, surfaceBright = TokiMiniPlayer,
    surfaceContainerLowest = TokiBlack, surfaceContainerLow = Color(0xFF0E0E10),
    surfaceContainer = TokiCard, surfaceContainerHigh = TokiMiniPlayer,
    surfaceContainerHighest = Color(0xFF3D3D4E),
    inverseSurface = Color.White, inverseOnSurface = Color.Black,
    error = TokiLogoutRed, onError = Color.White,
    errorContainer = Color(0xFF5A1A18), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF55555E), outlineVariant = TokiMiniPlayer, scrim = Color.Black,
    surfaceTint = TokiPlayKey,
    primaryFixed = TokiPlayKey, primaryFixedDim = Color(0xFFE8D5B5),
    onPrimaryFixed = TokiPlayKeyIcon, onPrimaryFixedVariant = TokiPlayKeyIcon,
    secondaryFixed = TokiMiniPlayer, secondaryFixedDim = Color(0xFF1C1C1E),
    onSecondaryFixed = Color.White, onSecondaryFixedVariant = TokiSecondary,
    tertiaryFixed = TokiPlayKey, tertiaryFixedDim = Color(0xFFE8D5B5),
    onTertiaryFixed = TokiPlayKeyIcon, onTertiaryFixedVariant = TokiPlayKeyIcon,
)
