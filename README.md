# TokiMusic

TokiMusic 是一款基于网易云音乐接口的第三方音乐应用，Toki 系列成员。Android 这一版是 **0.1.3**，Windows、iOS 与 iPadOS 仍是 **0.1.0**——各端版本号独立更新、分别发布（见 TokiUI 规范）。其中 Android 已正式发布（见仓库的 GitHub Release），iOS / Windows 尚未正式发布。

应用本身不内置网易云的服务，需要自己准备一个兼容 NeteaseCloudMusicApi 的接口服务（见下面的「API 服务」）。Android 界面以 [TokiUI](https://github.com/ichiyotsu/TokiUI) 为标准（`android/tokiui/` 快照，语义规范 0.2.0-b1；Android 设计对齐 TokiStudio 0.1.10）。iOS 与 Windows 这一版还没有迁过去，仍按 `docs/DESIGN.md` 里标明的上一套说明。

## 功能

- 三页结构：主页、歌单、我的。Android 窄窗口是悬浮胶囊底栏，宽窗口（≥ 840dp）是 NavigationRail；选中项是实色药丸并展开文字。iOS / Windows 仍是上一套底栏说明
- 主页：顶部胶囊搜索框，右上角是头像，未登录显示默认头像、已登录显示真实头像，点击进入二维码登录；下面是 Banner、每日推荐、新歌速递和推荐歌单；搜索支持单曲、歌手、歌单
- 歌单：按「我喜欢的音乐（置顶）、我创建的、我收藏的」分组，分组可折叠并带数量，点进歌单看曲目列表
- 我的：头像、昵称、等级和关注 / 粉丝；统计有收藏歌曲数、听歌时长、歌单数，另有我的收藏、最近播放、听歌排行入口；设置里可以改 API 地址和主题
- 二维码登录：走 `/login/qr/key`、`/login/qr/create`、`/login/qr/check` 轮询；`/login/qr/create` 返回的 base64 二维码图三端都已实现解码展示（Android 用 BitmapFactory、iOS 用 Data(base64Encoded:) 转 UIImage、Windows 用 BitmapImage + 内存流），登录成功后在本机保存 cookie
- 播放：底部上方有 MiniPlayer（封面、歌名、播放 / 暂停、进度），点开是全屏播放页
- 歌词页：**歌词居左**，整列左对齐；有逐字歌词时按网易云 YRC 逐字高亮，唱到的字逐个变主色，当前行加粗放大并自动滚动，翻译以小字跟在主行下方、同样居左；没有逐字歌词时回退到 LRC 整行高亮
- 没有可用的 API 服务时，Android 显示错误与重试，不再用 Demo 数据；iOS / Windows 仍是上一套（待对齐）

## Android

应用 ID 是 `com.tokimusic.app`。`versionName` 是 `0.1.3`，`versionCode` 是 4。

### 要求

Android 16 及以上（`minSdk` 36，与 TokiUI / TokiStudio 的 Android 目标一致），`targetSdk` 36，`compileSdk` 37。从 26 提到 36 是这次对齐 TokiUI 的结果，Android 8–15 不再是目标。

### 工程与构建

工程在 `android/`，用 Kotlin、Jetpack Compose 和 Material 3 Expressive，图片用 Coil，网络用 OkHttp，播放用 Media3（ExoPlayer）。设计 token 与 Android 语义解析来自 `android/tokiui/` 快照（见 `android/README.md` 和 `android/tokiui/SYNC.md`），不在本仓库再抄一份色板。底栏在玻璃模糊打开时用 Haze 采样窗口里已经画好的像素。需要 JDK 17 和 Android SDK（platform android-37、build-tools 37.0.0）。克隆下来直接就能构建，不用拉子模块。

```bash
cd android
bash ./gradlew :app:testDebugUnitTest
bash ./gradlew :app:assembleDebug
```

调试 APK 输出在 `android/app/build/outputs/apk/debug/app-debug.apk`。正式发布包（签名 APK `TokiMusic-0.1.3.apk` 与 AAB）在仓库的 GitHub Release 页提供下载。

### 应用内更新

尚未实现。规划与 Toki 系列其他应用一致：读取 GitHub Releases，只认正式版标签（例如 `v0.1.3`，带 `v` 前缀、不带 `-aN`/`-bN` 后缀），并且该发布里要有 APK 才提示；测试版标识在正式版通道里不提示。

## Windows

版本 `0.1.0-beta1`（程序集 `0.1.0.0`，`InformationalVersion` 是 `0.1.0-beta1`）。包身份是 `Toki.TokiMusic`。

### 要求

- Windows 10 版本 1809（内部版本 17763）或更高，包括 Windows 11
- 64 位（工程只配置了 x64）

### 工程与构建

工程在 `windows/`，用 C# 和原生 WinUI 3、.NET 8、Windows App SDK 1.6.250602001，和 Android 工程分开。分三部分：共享逻辑 `windows/src/TokiMusic.Core`（纯 `net8.0` 类库：数据模型、网易云接口客户端、YRC 逐字歌词解析器）、应用本体 `windows/src/TokiMusic`（WinUI 3，标题栏透明、导航栏随窗口宽度自动收缩、最小窗口 640×480）、测试 `windows/tests/TokiMusic.Tests`（xUnit）。

共享层可以在任何装了 .NET 8 SDK 的系统上编译和测试：

```bash
dotnet build windows/src/TokiMusic.Core/TokiMusic.Core.csproj -c Release
dotnet test windows/tests/TokiMusic.Tests/TokiMusic.Tests.csproj -c Release
```

应用本体只能在 Windows 上用 Visual Studio 2022 打开 `windows/TokiMusic.sln` 构建，XAML 编译需要 Windows 和 Windows App SDK，Linux 和 macOS 上编不了。图标已生成真实文件放在 `windows/src/TokiMusic/Assets/`（Matcha 主色圆角底 + 白色音符：StoreLogo 50×50、Square44x44、Square150x150、Square310x310、Wide310x150 和多尺寸 `TokiMusic.ico`），包清单引用同名文件，正式发布前可直接替换同名文件换品牌图。

### 应用内更新

尚未实现，规划同 Android：只认纯版本号标签的 GitHub Release。

## iOS 与 iPadOS

iOS 版是一个 SwiftUI 通用 App，安装包同时覆盖 iPhone 和 iPad。Bundle ID 是 `com.tokimusic.app`。`CFBundleShortVersionString` 是 `0.1.0`，构建号（`CFBundleVersion`）是 1。

### 要求

iOS / iPadOS 16.0 或更高（工程的部署目标是 16.0）。安装包暂未签名，正式分发方式确定后再补安装说明。

### 工程与构建

工程在 `ios/`：应用本体 `ios/TokiMusic`（SwiftUI），共享包 `ios/TokiMusicCore`（Swift Package，只依赖 Foundation，包含 YRC 逐字歌词解析器和它的 XCTest）。Xcode 工程是 `ios/TokiMusic.xcodeproj`，scheme 是 `TokiMusic`，工程通过本地包依赖引用 `TokiMusicCore`，Info.plist 在 `ios/Config/TokiMusic-Info.plist`。

Xcode 只能运行在 macOS 上，本仓库的 iOS 编译走 GitHub Actions 的 macOS runner（真 Xcode），workflow 在本地已备好 `.github/workflows/ios.yml`：在 macOS runner 上先跑 `TokiMusicCore` 的测试，再用 `ios/scripts/unsigned-ipa.sh` 打未签名 IPA 作为构建产物。注意：这个 workflow 文件需要通过 GitHub 网页端添加进仓库（GitHub App 的推送权限不包含 workflow 文件，直接推会被拒绝），添加后才会生效。

本地有 Xcode 时：

```bash
xcodebuild -project ios/TokiMusic.xcodeproj -scheme TokiMusic -destination 'generic/platform=iOS' build CODE_SIGNING_ALLOWED=NO
xcrun swift test --package-path ios/TokiMusicCore
```

在装有 Linux 版 Swift 工具链的机器上，也可以直接 `swift build` / `swift test --package-path ios/TokiMusicCore` 只验证共享包；SwiftUI 应用本体在 Linux 上编不了。

### 应用内更新

尚未实现，规划同 Android：只认纯版本号标签的 GitHub Release，并下载 IPA。

## API 服务

本 App 不内置网易云官方服务，需要自备兼容 [NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi) 的服务：

```bash
npx NeteaseCloudMusicApi@4.32.0   # 默认监听 http://127.0.0.1:3000（钉住经测试的版本，不要用 @latest 裸奔）
# 或 docker run -d -p 3000:3000 binaryify/netease_cloud_music_api
```

App 内「我的 → 设置 → API 地址」可修改基址（默认 `http://127.0.0.1:3000`，模拟器默认 `http://10.0.2.2:3000`）。真机上 127.0.0.1 指的是手机自身，连不上电脑的服务，请填电脑的局域网 IP（例如 `http://192.168.1.10:3000`）；地址旁有「测试连接」按钮，会明确告诉你是已连通、未登录还是连不上。用到的接口清单和二维码登录流程在 `docs/API.md`。听歌时长没有现成的总时长接口字段，是用 `/user/record` 的记录估算的，界面上会标注来源。

无 API 服务或服务不可用时，Android 显示错误页与重试，不再用 Mock 数据展示假内容；iOS / Windows 仍是上一套（待对齐）。登录过期时 Android 会明确提示「登录已过期，请重新扫码登录」，而不是悄悄变回未登录。

## 版本号规则

- 版本号从 `0.1.0` 开始迭代；各端版本号独立更新，Android 当前正式版本是 `0.1.3`，iOS / Windows 当前是 `0.1.0`（待发布）。
- 各端版本号独立更新、分别发布，只有大版本才一起发（见 TokiUI 规范）。
- 每次发布前先与作者确认版本号，不擅自升版；没有 GitHub Release 之前，本文不写下载链接。

## 验证状态

- Windows 共享层：已在本机（Linux、.NET 8 SDK 8.0.428）真实编译通过，产物 `TokiMusic.Core.dll`；xUnit 测试 4 个全部通过（YRC 解析 3 个 + 二维码 base64 解码 1 个；本机虚拟机的回环连接会污染 `dotnet test` 的 vstest 协议，实际是用 xunit console runner 进程内执行验证的；在普通机器上直接 `dotnet test` 即可）。
- Android：TokiUI 这次改动已在本机跑过 `./gradlew :app:testDebugUnitTest` 和 `./gradlew :app:assembleDebug`（Gradle 9.6.0，compileSdk 37，minSdk 36）。CI 见 `.github/workflows/android.yml`，不用配任何 secret。
- iOS：共享包 `TokiMusicCore` 已在本机用 Linux 版 Swift 6.0.3 真实编译通过，XCTest 2 个全部通过（期间修掉了 `YrcParser.swift` 里 3 处真实的编译错误：元组下标访问、字符串索引类型、复杂表达式拆分）；应用本体的 SwiftUI 源码只做了语法解析检查，未经 Xcode 编译，首次真实编译以 GitHub Actions 的 iOS workflow 结果为准（workflow 文件按上面说明经网页端添加后生效）。Xcode 工程已补齐：完整的 project.pbxproj（源码引用、本地包依赖、共享 scheme TokiMusic）、Assets.xcassets（1024 AppIcon 与 Matcha AccentColor）、未签名 IPA 脚本。Windows 本体的二维码展示与图标同理：代码与资源已备好，但 WinUI 本体未经 Windows 编译验证。

## 目录

```
android/   Kotlin Compose 工程（应用 ID com.tokimusic.app），设计引用 TokiUI
android/tokiui/  TokiUI 快照（语义规范 0.2.0-b1），Android 的 token 与语义解析
ios/       SwiftUI 工程 + TokiMusicCore Swift Package（界面尚未迁到 TokiUI）
windows/   WinUI 3 工程（界面尚未迁到 TokiUI）
docs/      DESIGN.md（Android = TokiUI；iOS/Windows 仍是上一套说明）/ API.md
```

## 免责声明

第三方非官方应用，与网易、网易云音乐无关。音乐版权归版权方所有。本项目仅供学习交流，需自备 API 服务；请勿用于商业用途或侵犯版权的行为。
