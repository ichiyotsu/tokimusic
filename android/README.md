# TokiMusic Android

Android UI follows [TokiUI](https://github.com/ichiyotsu/TokiUI) (snapshot under `android/tokiui/`, from tag `v0.1.4`). iOS and Windows are not part of this module.

## TokiUI reference

The snapshot lives at `android/tokiui/` (see `tokiui/SYNC.md` for provenance).

`app/build.gradle.kts` points the app at the snapshot files:

```kotlin
kotlin.directories.add("../tokiui/src/main/kotlin")
assets.directories.add("../tokiui/tokens")
```

Runtime code calls `SemanticTokens.parse` and `ShowcaseInventory.parse` on those assets. Seeds for HCT / `SchemeTonalSpot` come from `tokens/tokiui.semantic.tokens.json`. The launcher background color is generated from `color.seed` during `syncTokiUiResources`. Inter and Noto Sans SC (Source Han Sans) are committed in `app/src/main/res/font/`. Nothing in this module checks in a second color table.

The snapshot is from TokiUI `v0.1.4` (`0edda3289be65e0ddd531515352d309a72f5089e`). To refresh it, overwrite the parser and JSON files from a newer TokiUI tag and update `tokiui/SYNC.md`. The old git submodule `TokiUI/` at the repository root is no longer used by the build or CI.

## Requirements

- Android 16 or newer (`minSdk` 36, `targetSdk` 36, `compileSdk` 37)
- JDK 17
- Android SDK platform `android-37.0` and build-tools `37.0.0`

`applicationId` is `com.tokimusic.app`. `versionName` is `0.1.3`, `versionCode` is `4`.

## Build

```bash
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

CI (`.github/workflows/android.yml`) just checks out this repo and builds: the TokiUI parser, tokens and fonts are all committed in the repo (see `tokiui/SYNC.md`), so no secret is needed.
