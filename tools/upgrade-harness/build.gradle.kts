// Self-contained emulator instrumentation: no dependency on the target app's unminified classes.
// PsyZine, GPL-3.0-or-later. Versions come from the unchanged main project catalog.
import java.security.KeyStore

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val protectedPath = System.getenv("PSYZINE_COMPAT_KEYSTORE_FILE")
fun signingValue(name: String, localDefault: String): String = System.getenv(name)?.takeIf { it.isNotBlank() }
    ?: if (protectedPath == null) localDefault else error("Missing protected signing input: $name")
val retainedFile = file(protectedPath ?: checkNotNull(System.getenv("PSYZINE_DEVELOPMENT_KEYSTORE")) { "Retained key path is required" })
check(retainedFile.isFile && retainedFile.canRead()) { "Retained key is missing; never generate a replacement" }

android {
    namespace = "app.psyzine.upgradetest"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "app.psyzine.debug.test"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("retained") {
            storeFile = retainedFile
            storePassword = signingValue("PSYZINE_COMPAT_STORE_PASSWORD", "android")
            keyAlias = signingValue("PSYZINE_COMPAT_KEY_ALIAS", "androiddebugkey")
            keyPassword = signingValue("PSYZINE_COMPAT_KEY_PASSWORD", "android")
            storeType = KeyStore.getInstance(retainedFile, checkNotNull(storePassword).toCharArray()).type
        }
    }
    buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("retained") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Implementation dependencies keep Kotlin/test/DataStore classes inside this APK.
    implementation(libs.junit)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.datastore.preferences)
    // Instrumentation can share unminified target classes on API26. Keep these at the
    // same catalog versions as both reviewed baselines, rather than runner transitives.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation("androidx.test:runner:1.7.0") // Already resolved by the app's Espresso 3.7.0 suite.
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
