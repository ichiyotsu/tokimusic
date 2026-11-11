# TokiMusic 设计规范（复刻 Tokinote，禁止改色）

来源：Tokinote `app/src/main/java/com/tokinote/app/ui/theme/{Color,Shape,Glass}.kt`、
`ui/components/FloatingNavBar.kt`、`ios/TokiNote/Theme.swift`。三端必须使用同一套 hex。

## Matcha 配色（完整色板）

| Token | Light | Dark |
|---|---|---|
| primary | #4C662B | #B1D18A |
| onPrimary | #FFFFFF | #1F3701 |
| primaryContainer | #CDEDA3 | #354E16 |
| onPrimaryContainer | #354E16 | #CDEDA3 |
| secondary | #586249 | #BFCBAD |
| onSecondary | #FFFFFF | #2A331E |
| secondaryContainer | #DCE7C8 | #404A33 |
| onSecondaryContainer | #404A33 | #DCE7C8 |
| tertiary | #386663 | #A0D0CB |
| onTertiary | #FFFFFF | #003735 |
| tertiaryContainer | #BCECE7 | #1F4E4B |
| onTertiaryContainer | #1F4E4B | #BCECE7 |
| error | #BA1A1A | #FFB4AB |
| onError | #FFFFFF | #690005 |
| errorContainer | #FFDAD6 | #93000A |
| onErrorContainer | #93000A | #FFDAD6 |
| background | #F9FAEF | #12140E |
| onBackground | #1A1C16 | #E2E3D8 |
| surface | #F9FAEF | #12140E |
| onSurface | #1A1C16 | #E2E3D8 |
| surfaceVariant | #E1E4D5 | #44483D |
| onSurfaceVariant | #44483D | #C5C8BA |
| outline | #75796C | #8F9285 |
| outlineVariant | #C5C8BA | #44483D |
| scrim | #000000 | #000000 |
| inverseSurface | #2F312A | #E2E3D8 |
| inverseOnSurface | #F1F2E6 | #2F312A |
| inversePrimary | #B1D18A | #4C662B |
| surfaceDim | #DADBD0 | #12140E |
| surfaceBright | #F9FAEF | #383A32 |
| surfaceContainerLowest | #FFFFFF | #0C0F09 |
| surfaceContainerLow | #F3F4E9 | #1A1C16 |
| surfaceContainer | #EEEFE3 | #1E201A |
| surfaceContainerHigh | #E8E9DE | #282B24 |
| surfaceContainerHighest | #E2E3D8 | #33362E |

## TokiShapes

extraSmall 8dp / small 12dp / medium 18dp / large 26dp / extraLarge 36dp /
largeIncreased 32dp / extraLargeIncreased 44dp / extraExtraLarge 56dp。
- ExpressiveCardShape：34 / 22 / 34 / 22（topStart/topEnd/bottomEnd/bottomStart）
- ExpressiveChipShape：20 / 20 / 20 / 7
- ExpressiveIslandShape：34dp；ExpressiveSheetShape：顶部 34dp

## 悬浮底栏 FloatingNavBar

36dp 圆角胶囊，一层毛玻璃（Haze）：blur 30dp、noise 0.02、
tint 浅色 黑3%+白10% / 深色 黑16%+白8%、fallback 浅 黑30% / 深 黑55%，**无实色层**。
选中项：实色 primary pill + 文字弹簧展开（open: damping 0.6 / stiffness 440；
close: damping 0.8 / stiffness 440），LongPress 触觉反馈，pressScale 0.92，
悬浮间隙 8dp，内容底部 padding 按底栏实测高度计算。
iOS 用 `.ultraThinMaterial` 胶囊对应；Windows 用 AcrylicBrush 对应。

## 字体

系统无衬线 / Inter 思路，中文回退 PingFang SC（iOS/macOS）/ Microsoft YaHei（Windows）。
不提供字体选择器。

## Windows

原生 WinUI 3（UseWinUI true，net8.0-windows10.0.19041.0，WindowsAppSDK 1.6）。
标题栏透明：内容延伸进标题栏、caption 按钮透明、保留拖拽区。
NavigationView PaneDisplayMode=Auto 窄窗口自动收缩；最小窗口 640×480（DIP）。

## 歌词页

歌词整列**左对齐**（Start），当前行自动滚动到视口中央只是滚动位置、文字本身仍左对齐。
逐字：已唱字 primary 色填充、未唱字 onSurfaceVariant；当前行放大加粗；
翻译小字在主行下方、同样左对齐。
