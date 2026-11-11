# TokiMusic

基于网易云音乐接口的第三方音乐 App，Toki 系列成员。设计规范与 Tokinote / Tokilive 完全一致
（Matcha 配色、TokiShapes、悬浮毛玻璃底栏）。

- 版本：**0.1.0-beta1**
- 平台：Android（Kotlin + Jetpack Compose）/ iOS（SwiftUI）/ Windows（原生 WinUI 3）
- 三页结构：主页（搜索 / 推荐）· 歌单（我创建的 / 我收藏的，分组）· 我的（收藏 / 听歌统计）
- 歌词页：**歌词居左**，支持网易云 YRC **逐字歌词**高亮，无逐字时回退 LRC 整行高亮

## 如何跑网易云 API 服务

本 App 不内置网易云官方服务，需要自备兼容
[NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi) 的服务：

```bash
npx NeteaseCloudMusicApi@latest   # 默认监听 http://127.0.0.1:3000
# 或 docker run -d -p 3000:3000 binaryify/netease_cloud_music_api
```

App 内「我的 → 设置 → API 地址」可修改基址（默认 `http://127.0.0.1:3000`）。
Android 真机调试时请填电脑局域网 IP，不要填 127.0.0.1。

无 API 服务时 App 使用内置 Mock 数据展示 UI（界面会明确标注「Demo 数据」），不会白屏。

## 目录

```
android/   Kotlin Compose 工程（包名 com.tokimusic.app）
ios/       SwiftUI 工程（源码 + Info.plist，MARKETING_VERSION 0.1.0）
windows/   WinUI 3 工程（TokiMusic.Core + TokiMusic + Tests）
docs/      DESIGN.md 设计令牌 / API.md 接口清单与登录流程
```

## 免责声明

第三方非官方应用，与网易、网易云音乐无关。音乐版权归版权方所有。
本项目仅供学习交流，需自备 API 服务；请勿用于商业用途或侵犯版权的行为。
