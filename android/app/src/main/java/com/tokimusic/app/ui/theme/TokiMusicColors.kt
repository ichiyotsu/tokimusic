package com.tokimusic.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * TokiMusic 取色方案：深色为默认（颜色按用户提供的 5 张截图取定）。
 *
 * 0.1.21 第 7 项：新增浅色模式——与深色成对的浅色色板（见
 * [tokiMusicLightColorScheme]），设置 → 个性化开关切换，深色仍是默认，
 * 不翻开关的人外观一字不变。
 */
val TokiBlack: Color = Color(0xFF000000)
val TokiCard: Color = Color(0xFF1C1C1E)
val TokiMiniPlayer: Color = Color(0xFF2C2C2E)
/** 选中 tab 的 pill：截图里是比底栏浅的灰紫（0.1.15 与现在同值；
 * 0.1.18 第 22 项用户确认保持 #54545F，不改深紫 #5C4861）。 */
val TokiPill: Color = Color(0xFF54545F)
/** 底栏 icon 颜色（0.1.18 第 1 项定浅紫 #D0A9E8；0.1.19 第 1 项用户改定
 * 白色）：选中与未选中同色，选中态靠 pill + 实心 icon 形状区分。 */
val TokiNavIcon: Color = Color.White
val TokiSecondary: Color = Color(0xFF8E8E93)
val TokiPlayKey: Color = Color(0xFFF5E6D3)
val TokiPlayKeyIcon: Color = Color(0xFF2E1A0C)
val TokiLogoutRed: Color = Color(0xFFFF3B30)
/** 播放器暖棕渐变的回退色（取色失败或封面无彩色时用）。 */
val TokiWarmTop: Color = Color(0xFF5A2A12)
val TokiWarmBottom: Color = Color(0xFF2E1A0C)

/** 浅色模式正文墨色（与深色卡片 #1C1C1E 同值，深底浅字/浅底深字成对）。 */
val TokiInk: Color = Color(0xFF1C1C1E)
/** 浅色模式卡片色（深色 TokiCard #1C1C1E 的浅色对应）。 */
val TokiCardLight: Color = Color(0xFFF2F2F4)
/** 浅色模式高一阶表面（深色 TokiMiniPlayer #2C2C2E 的浅色对应）。 */
val TokiSurfaceHighLight: Color = Color(0xFFE9E9ED)
/** 浅色模式选中 pill：浅灰紫，压在白色玻璃底栏上能分清选中态。 */
val TokiPillLight: Color = Color(0xFFD9D6E2)
/** 浅色模式次级文本：深色 #8E8E93 在白底上太浅，压深一档。 */
val TokiSecondaryLight: Color = Color(0xFF6E6E73)

/** 底栏选中 pill 色（按当前明暗取）：深色 #54545F / 浅色 #D9D6E2。 */
@Composable
fun tokiPillColor(): Color = if (LocalDarkTheme.current) TokiPill else TokiPillLight

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

/**
 * TokiMusic 浅色 ColorScheme（0.1.21 第 7 项）：与深色方案逐角色成对——
 * 背景/表面纯白（对应纯黑），卡片走 surfaceContainer 阶梯
 * （Lowest 白 → Low #F7F7F9 → Container #F2F2F4 → High #E9E9ED →
 * Highest #DEDEE5），正文墨色 #1C1C1E，次级文本压深到 #6E6E73；
 * 播放键仍是浅米色 + 深棕图标（两模式同值，米色键在白底上成立）；
 * 错误色仍为退出红。深色方案 [tokiMusicColorScheme] 一字未动。
 */
fun tokiMusicLightColorScheme() = lightColorScheme(
    primary = TokiPlayKey, onPrimary = TokiPlayKeyIcon,
    primaryContainer = Color(0xFFE7E3EF), onPrimaryContainer = TokiInk,
    inversePrimary = TokiPlayKey,
    secondary = TokiSecondaryLight, onSecondary = Color.White,
    secondaryContainer = TokiSurfaceHighLight, onSecondaryContainer = TokiInk,
    tertiary = TokiPlayKey, onTertiary = TokiPlayKeyIcon,
    tertiaryContainer = TokiSurfaceHighLight, onTertiaryContainer = TokiInk,
    background = Color.White, onBackground = TokiInk,
    surface = Color.White, onSurface = TokiInk,
    surfaceVariant = TokiCardLight, onSurfaceVariant = TokiSecondaryLight,
    surfaceDim = Color(0xFFDDDDE2), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F7F9),
    surfaceContainer = TokiCardLight, surfaceContainerHigh = TokiSurfaceHighLight,
    surfaceContainerHighest = Color(0xFFDEDEE5),
    inverseSurface = TokiInk, inverseOnSurface = Color.White,
    error = TokiLogoutRed, onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF5A1A18),
    outline = Color(0xFFC6C6C8), outlineVariant = Color(0xFFE3E3E8), scrim = Color.Black,
    surfaceTint = TokiPlayKey,
    primaryFixed = TokiPlayKey, primaryFixedDim = Color(0xFFE8D5B5),
    onPrimaryFixed = TokiPlayKeyIcon, onPrimaryFixedVariant = TokiPlayKeyIcon,
    secondaryFixed = TokiSurfaceHighLight, secondaryFixedDim = TokiCardLight,
    onSecondaryFixed = TokiInk, onSecondaryFixedVariant = TokiSecondaryLight,
    tertiaryFixed = TokiPlayKey, tertiaryFixedDim = Color(0xFFE8D5B5),
    onTertiaryFixed = TokiPlayKeyIcon, onTertiaryFixedVariant = TokiPlayKeyIcon,
)
