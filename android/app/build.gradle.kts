import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose) }
// AGP 9 has built-in Kotlin support: the org.jetbrains.kotlin.android plugin must NOT be applied.
android {
    namespace = "com.tokimusic.app"
    compileSdk = 37 // Compose 1.12 / core-ktx 1.19 dependencies require compiling against API 37+
    defaultConfig {
        applicationId = "com.tokimusic.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-beta1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui); implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation); implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.haze); implementation(libs.haze.blur)
    implementation(libs.coil.compose); implementation(libs.okhttp)
    implementation(libs.media3.exoplayer); implementation(libs.media3.session)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
