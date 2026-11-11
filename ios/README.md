# TokiMusic iOS

SwiftUI 工程。用 Xcode 打开 `TokiMusic.xcodeproj`，scheme 为 `TokiMusic`，MARKETING_VERSION 0.1.0 / CURRENT_PROJECT_VERSION 1（对应发布语义版本 0.1.0-beta1），Bundle ID `com.tokimusic.app`，部署目标 iOS 16.0。`TokiMusicCore` 是本地 Swift Package（含 YRC 逐字歌词解析器与 XCTest），App 通过本地包依赖引用它。Info.plist 在 `Config/TokiMusic-Info.plist`，未签名 IPA 脚本在 `scripts/unsigned-ipa.sh`。

Xcode 只能运行在 macOS 上：本机（Linux）装不了 Xcode，应用本体未经 Xcode 编译。编译走 GitHub Actions 的 macOS runner，workflow 已备好在仓库根目录 `.github/workflows/ios.yml`，需通过 GitHub 网页端添加进仓库后生效（GitHub App 推送权限不含 workflow 文件）。共享包已在本机用 Linux 版 Swift 6.0.3 验证：`swift build` 成功、`swift test --package-path TokiMusicCore` 2 个测试全部通过。

API 基址默认 http://127.0.0.1:3000，在「我的 → 设置」修改；真机请填电脑局域网 IP。
