# TokiMusic Android

Android UI follows [TokiUI](https://github.com/ichiyotsu/TokiUI) (pinned submodule, tag `v0.1.4`). iOS and Windows are not part of this module.

## TokiUI reference

The submodule lives at the repository root: `TokiUI/`.

`app/build.gradle.kts` points the app at TokiUI files (same idea as `docs/复用.md`):

```kotlin
kotlin.directories.add("../../TokiUI/platforms/android/src/main/kotlin")
assets.directories.add("../../TokiUI/tokens")
```

Runtime code calls `SemanticTokens.parse` and `ShowcaseInventory.parse` on those assets. Seeds for HCT / `SchemeTonalSpot` come from `tokens/tokiui.semantic.tokens.json`. The launcher background color is generated from `color.seed` during `syncTokiUiResources`. Inter and Noto Sans SC (Source Han Sans) are copied from `TokiUI/tokistudio/android/app/src/main/res/font` in that same task. Nothing in this module checks in a second color table.

Checkout:

```bash
git submodule update --init --recursive
```

The recorded commit is TokiUI `v0.1.4` (`0edda3289be65e0ddd531515352d309a72f5089e`).

## Requirements

- Android 16 or newer (`minSdk` 36, `targetSdk` 36, `compileSdk` 37)
- JDK 17
- Android SDK platform `android-37.0` and build-tools `37.0.0`

`applicationId` is `com.tokimusic.app`. `versionName` is `0.1.2`, `versionCode` is `3`.

## Build

```bash
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

CI (`.github/workflows/android.yml`) checks out this repo, then fetches the private TokiUI submodule with the `TOKIUI_READ_TOKEN` secret (a PAT that can read `ichiyotsu/TokiUI`). `GITHUB_TOKEN` cannot read that repository.
