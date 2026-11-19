# TokiUI 快照

这个目录是 [TokiUI](https://github.com/ichiyotsu/TokiUI) 的只读快照，给公开仓库的 CI 用。TokiMusic 不能在 CI 里用 `../TokiUI` 这种兄弟路径，因为 TokiUI 是私有仓库。

- 来源标签：`v0.1.4`
- 来源提交：`0edda3289be65e0ddd531515352d309a72f5089e`（正式版 Android TokiStudio 0.1.4；语义 token 版本仍是 `0.2.0-b1`）
- 包含：`src/main/kotlin/com/tokiui/semantic/`（官方解析器，不改逻辑）和 `tokens/*.json`（官方语义 token 与场景清单）
- 不包含：TokiStudio 的界面代码。TokiMusic 自己的界面只调用 `SemanticTokens.parse`，不把色值抄进 Kotlin。
- 字体（Inter、Noto Sans SC）不在这个目录里，直接放在 `android/app/src/main/res/font/`，同样是提交进仓库的。

刷新：从 TokiUI 标签 `v0.1.4`（或更新的正式标签）原样覆盖上面两类文件，并改本说明里的标签和提交号。不要手改解析器或 JSON，也不要在应用源码里另写一套色板。
