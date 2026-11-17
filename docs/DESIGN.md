# TokiMusic 设计

Android 以 [TokiUI](https://github.com/ichiyotsu/TokiUI) 为唯一界面标准。iOS 与 Windows **这一次没有迁移**，仍使用文末「iOS / Windows（尚未迁移）」里的上一套说明，直到后续 PR。

## Android（TokiUI）

不要再把本文旧的 Matcha 色表、TokiShapes 或 Haze 30dp 配方当作 Android 标准。那些数值不进 Android 主题。

源头是仓库根的 git submodule `TokiUI/`，钉在正式标签 **v0.1.4**（`0edda3289be65e0ddd531515352d309a72f5089e`）。阅读顺序与 TokiUI 仓库一致：`docs/README.md`、`docs/复用.md`、`docs/语义.md`、`docs/组件契约.md`、`docs/platforms/android.md`、`docs/platforms/材质.md`，再是 `tokens/tokiui.semantic.tokens.json` 与 `tokens/showcase.inventory.json`。`TokiUI/tokistudio/docs/DESIGN.md` 只是 Android / MD3E 的实现记录；和语义层或 `docs/platforms/android.md` 冲突时，以后者为准。不要读 `docs/history/tokinote-0.1.0/`。音乐界面的版式对照 TokiStudio 0.1.4 的音乐工具样例（`MusicScreen.kt`），播放、歌词和登录仍是 TokiMusic 自己的功能。

Android 工程不复制色板。`android/app/build.gradle.kts` 按 `docs/复用.md` 引用：

```kotlin
kotlin.directories.add("../../TokiUI/platforms/android/src/main/kotlin")
assets.directories.add("../../TokiUI/tokens")
```

运行时 `SemanticTokens.parse` / `ShowcaseInventory.parse` 读 assets 里的 JSON。种子列表来自 `color.seeds`。启动图标底色在构建时从 `color.seed` 生成，不另写一份色表。拉丁字体 Inter 与思源黑体的官方发行 Noto Sans SC（`res/font/inter.ttf`、`res/font/noto_sans_sc.ttf`）从 submodule 拷进生成资源，供编译使用。

### 主题

- Jetpack Compose + `MaterialExpressiveTheme`。
- 颜色：动态取色，或种子色 HCT（`SchemeTonalSpot`）。种子是语义 token 里的 `seeds`，不是 Matcha 锁死 hex。
- 动效：默认 `MotionScheme.expressive()`，减少动效时 `MotionScheme.standard()`。不写死旧的弹簧常数。
- 形状和字阶：Material 默认值（`Shapes()`、`Typography()`）。文字大小用 sp，跟随系统字体缩放。不把 `referenceSizeSp` 写成 `Text` 的 `fontSize`。
- 字体：拉丁文 Inter，汉字用思源黑体（包内文件是官方 Noto Sans SC）。标题、标签和强调句是粗体（wght 700），正文和辅助说明是常规字重（wght 400）。不再使用 Google Sans，也不再用 PingFang / 微软雅黑作为 Android 指引。

`minSdk` 是 **36**（Android 16+），与 TokiStudio 的 TokiUI Android 目标一致。`targetSdk` 36，`compileSdk` 37。应用 ID 仍是 `com.tokimusic.app`，`versionName` 是 `0.1.2`。

### 导航与材质

- 窗口宽度小于 840dp：悬浮胶囊。达到 840dp：`NavigationRail`。这是 Android 自适应，不要抄到 iOS / Windows。
- 玻璃模糊打开且可以采样窗口（RenderEffect）时，胶囊用 Haze `HazeInput.Backdrop` 采样已经画好的窗口像素，采不到时按 TokiStudio 回退到挂了 `hazeSource` 的内容根。浅色磨砂是 16% 暖色暗罩再加 46% 白霜；深色磨砂是 20% 白霜，不加黑罩。顶栏、底栏和滚动标题共用这一套。
- 开关关闭或采样不可用时，胶囊使用不透明 `surfaceContainer`，并加 3dp `primary` 描边，描边和选中胶囊之间留 6dp。玻璃打开时不加这道粗描边。
- 不使用 `Modifier.blur`，也不用 `HazeInput.Content` 冒充背后内容的模糊。磨砂只发生在胶囊形状里，底栏下面不铺全宽淡化带。例外：播放页用 `Modifier.blur` 模糊封面图做沉浸式背景——那是 artwork，不是导航材质。
- 内容滑到大标题或返回键后面时，色块只做与底栏相同的磨砂，字用 `onSurface`，不再叠一层不透明填色。玻璃不可用时改为不透明 `surfaceContainer` 加同样的 3dp `primary` 描边。没有重叠时不垫色。歌单详情和我的的顶栏走这一套；主页的大标题画在内容里，播放页是沉浸式二级页面，没有顶栏与底栏。
- 只显示图标的控件长按用 Material 3 `PlainTooltip` 显示名称，同一段文字写进 `contentDescription`。

设置里保留原来的深浅色，并加上 TokiUI 需要的动态取色、种子色、减少动效和玻璃模糊开关。

### 音乐产品行为（保持不变）

信息架构仍是主页、歌单、我的；迷你播放条和全屏播放页；歌词左对齐；YRC 逐字、没有逐字时回退 LRC，翻译在行下；二维码登录；搜索、每日推荐 / 私人雷达 / 最近播放、推荐歌单 / 新歌；喜欢 / 创建 / 收藏分组；资料统计；API 基址；主题设置；播放页与歌词页是沉浸式二级页面（没有顶栏与底栏），封面与歌词在同一页内切换。API 不可用时主页显示错误与重试，登录后分区改为登录提示，不再用 Demo 数据。

歌词的左对齐、当前行强调、翻译跟在行下，是产品行为。Android 歌词画在深色罩上：当前行用 `headlineSmall` 加粗纯白、逐字填充（已唱纯白、未唱半透白），其余行用 `titleLarge` 常规、按与当前行的距离渐隐，翻译用 `bodyMedium` 半透白；当前行锚定在视口约 28% 处。

## iOS / Windows（尚未迁移）

下面是 0.1.0 从 TokiNote 对齐过来的说明，**只适用于 iOS 与 Windows**。Android 不使用这些 hex、圆角和模糊数字。后续迁到 TokiUI 时再改这一节，不要在 Android 上继续实现它们。

来源：Tokinote `app/src/main/java/com/tokinote/app/ui/theme/{Color,Shape,Glass}.kt`、`ui/components/FloatingNavBar.kt`、`ios/TokiNote/Theme.swift`。

### Matcha 配色（完整色板）

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

### TokiShapes

extraSmall 8dp / small 12dp / medium 18dp / large 26dp / extraLarge 36dp /
largeIncreased 32dp / extraLargeIncreased 44dp / extraExtraLarge 56dp。

- ExpressiveCardShape：34 / 22 / 34 / 22（topStart/topEnd/bottomEnd/bottomStart）
- ExpressiveChipShape：20 / 20 / 20 / 7
- ExpressiveIslandShape：34dp；ExpressiveSheetShape：顶部 34dp

### 悬浮底栏（iOS / Windows）

36dp 圆角胶囊，一层毛玻璃（Haze）：blur 30dp、noise 0.02、
tint 浅色 黑3%+白10% / 深色 黑16%+白8%、fallback 浅 黑30% / 深 黑55%，**无实色层**。
选中项：实色 primary pill + 文字弹簧展开（open: damping 0.6 / stiffness 440；
close: damping 0.8 / stiffness 440），LongPress 触觉反馈，pressScale 0.92，
悬浮间隙 8dp，内容底部 padding 按底栏实测高度计算。
iOS 用 `.ultraThinMaterial` 胶囊对应；Windows 用 AcrylicBrush 对应。

### 字体（iOS / Windows）

系统无衬线 / Inter 思路，中文回退 PingFang SC（iOS/macOS）/ Microsoft YaHei（Windows）。
不提供字体选择器。

### Windows

原生 WinUI 3（UseWinUI true，net8.0-windows10.0.19041.0，WindowsAppSDK 1.6）。
标题栏透明：内容延伸进标题栏、caption 按钮透明、保留拖拽区。
NavigationView PaneDisplayMode=Auto 窄窗口自动收缩；最小窗口 640×480（DIP）。
窗口图标仍是 Matcha 主色圆角底加白色音符，资源说明见 `windows/src/TokiMusic/Assets/README.md`。

### 歌词页（三端产品行为）

歌词整列**左对齐**（Start），当前行自动滚动到视口约 28% 处只是滚动位置、文字本身仍左对齐。
逐字：已唱字用强调色、未唱字用次要文字色；当前行放大加粗；
翻译小字在主行下方、同样左对齐。
Android 的歌词画在深色罩上，强调色是纯白、未唱与远处行是半透白。iOS / Windows 在迁到 TokiUI 之前，强调色仍按上面的 Matcha `primary` / `onSurfaceVariant`。
