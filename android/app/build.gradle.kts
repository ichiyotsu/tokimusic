import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose) }
// AGP 9 has built-in Kotlin support: the org.jetbrains.kotlin.android plugin must NOT be applied.
// Release signing is driven by environment (TOKIMUSIC_KEYSTORE / TOKIMUSIC_STORE_PASSWORD /
// TOKIMUSIC_KEY_ALIAS / TOKIMUSIC_KEY_PASSWORD), same pattern as TokiDiscover. Without the
// env vars the release build falls back to the debug key so it always compiles.
val releaseKeystore: String? = System.getenv("TOKIMUSIC_KEYSTORE")

// TokiUI is the design source of truth (git submodule at the repository root, pinned to v0.1.4).
// Paths match docs/复用.md: Kotlin adapters and token JSON are referenced, not copied.
val tokiUiDir = rootProject.file("../TokiUI")
val tokiTokens = tokiUiDir.resolve("tokens/tokiui.semantic.tokens.json")
val tokiFonts = tokiUiDir.resolve("tokistudio/android/app/src/main/res/font")
check(tokiTokens.isFile && tokiFonts.resolve("inter.ttf").isFile && tokiFonts.resolve("noto_sans_sc.ttf").isFile) {
    "TokiUI submodule is missing at ${tokiUiDir.absolutePath}. From the repository root run: git submodule update --init --recursive"
}

val generatedTokiRes = layout.buildDirectory.dir("generated/tokiuiRes")
generatedTokiRes.get().asFile.mkdirs()

val syncTokiUiResources = tasks.register<Copy>("syncTokiUiResources") {
    group = "tokiui"
    description = "Reference TokiUI fonts and write the launcher color from semantic color.seed."
    from(tokiFonts) {
        include("inter.ttf", "noto_sans_sc.ttf")
        into("font")
    }
    doFirst {
        generatedTokiRes.get().asFile.resolve("font/google_sans_flex.ttf").delete()
    }
    into(generatedTokiRes)
    inputs.file(tokiTokens)
    doLast {
        val seed = Regex(""""seed"\s*:\s*"(#[0-9A-Fa-f]{6})"""")
            .find(tokiTokens.readText())
            ?.groupValues
            ?.get(1)
            ?: error("TokiUI color.seed is missing from $tokiTokens")
        val values = generatedTokiRes.get().asFile.resolve("values")
        values.mkdirs()
        values.resolve("toki_seed_colors.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <!-- Generated from TokiUI tokens/tokiui.semantic.tokens.json color.seed. Do not edit. -->
                <color name="toki_seed">#FF${seed.removePrefix("#").uppercase()}</color>
            </resources>
            """.trimIndent() + "\n",
        )
    }
}

android {
    namespace = "com.tokimusic.app"
    compileSdk = 37 // Compose 1.12 / core-ktx 1.19 dependencies require compiling against API 37+
    defaultConfig {
        applicationId = "com.tokimusic.app"
        // TokiUI Android target is Android 16+ (minSdk 36), same as TokiStudio.
        minSdk = 36
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("TOKIMUSIC_STORE_PASSWORD")
                keyAlias = System.getenv("TOKIMUSIC_KEY_ALIAS") ?: "tokimusic"
                keyPassword = System.getenv("TOKIMUSIC_KEY_PASSWORD")
            }
        }
    }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"); signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.named("main") {
        kotlin.directories.add("../../TokiUI/platforms/android/src/main/kotlin")
        assets.directories.add("../../TokiUI/tokens")
        res.directories.add(generatedTokiRes.get().asFile.absolutePath)
    }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
tasks.named("preBuild").configure { dependsOn(syncTokiUiResources) }
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui); implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation); implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material.color.utilities)
    implementation(libs.haze); implementation(libs.haze.blur)
    implementation(libs.coil.compose); implementation(libs.okhttp)
    implementation(libs.media3.exoplayer); implementation(libs.media3.session)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
