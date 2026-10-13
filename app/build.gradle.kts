import java.util.Properties
import java.security.KeyStore
import java.security.MessageDigest
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    id("androidx.room")
    kotlin("plugin.serialization") version "2.0.20"
    id("kotlin-kapt") // this needs to be on bottom
}

// One version source for Gradle, packaging, tags and publication checks.
val psyzineVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val psyzineVersionCode = providers.gradleProperty("psyzineVersionCode").orNull?.toInt()
    ?: psyzineVersion.getProperty("versionCode").toInt()
val psyzineVersionName = psyzineVersion.getProperty("versionName")
require(psyzineVersionCode in 1..2100000000)
require(psyzineVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
val signingMode = providers.environmentVariable("PSYZINE_SIGNING_MODE").orElse("compatibility").get()
require(signingMode in listOf("compatibility", "unpublished", "production"))
fun env(name: String) = providers.environmentVariable(name).orNull?.takeIf(String::isNotBlank)
val compatNames = listOf("PSYZINE_COMPAT_KEYSTORE_FILE", "PSYZINE_COMPAT_KEY_ALIAS",
    "PSYZINE_COMPAT_STORE_PASSWORD", "PSYZINE_COMPAT_KEY_PASSWORD")
val productionNames = listOf("PSYZINE_RELEASE_KEYSTORE_FILE", "PSYZINE_KEY_ALIAS",
    "PSYZINE_STORE_PASSWORD", "PSYZINE_KEY_PASSWORD")
for (names in listOf(compatNames, productionNames)) {
    require(names.all { env(it) == null } || names.all { env(it) != null }) {
        "Incomplete PsyZine signing credentials; configure all four variables."
    }
}
require(signingMode == "production" || productionNames.all { env(it) == null }) {
    "Production credentials require explicit PSYZINE_SIGNING_MODE=production."
}
val signingValues = when {
    signingMode == "production" -> productionNames.map { requireNotNull(env(it)) { "Missing production signing input: $it" } }
    signingMode == "compatibility" && env(compatNames.first()) != null -> compatNames.map { checkNotNull(env(it)) }
    else -> listOf(env("PSYZINE_DEVELOPMENT_KEYSTORE")
        ?: rootProject.file(".local/signing/psyzine-development.jks").path, "androiddebugkey", "android", "android")
}
require(signingMode != "unpublished" || env("PSYZINE_DEVELOPMENT_KEYSTORE") != null) {
    "Unpublished checks require an explicit disposable keystore path."
}
val retainedStore = rootProject.file(signingValues[0])
require(retainedStore.isFile) { "Signing keystore is unavailable; automatic replacement is forbidden." }
val signingStore = KeyStore.getInstance(retainedStore, signingValues[2].toCharArray())
require(signingStore.isKeyEntry(signingValues[1]) &&
    signingStore.getKey(signingValues[1], signingValues[3].toCharArray()) != null) {
    "Signing private key is unavailable."
}
val signingDigest = MessageDigest.getInstance("SHA-256")
    .digest(signingStore.getCertificate(signingValues[1]).encoded).joinToString("") { "%02x".format(it) }
if (signingMode != "unpublished") {
    val policy = JsonSlurper().parse(rootProject.file("config/signing-policy.json")) as Map<*, *>
    val pins = policy["applicationIds"] as Map<*, *>
    require(pins.keys == setOf("app.psyzine", "app.psyzine.debug", "app.psyzine.play", "app.psyzine.play.debug"))
    require(pins.values.all { it == signingDigest }) { "Signing certificate does not match the retained package identities." }
}

android {
    namespace = "com.isaakhanimann.journal"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.psyzine"
        minSdk = 26
        targetSdk = 35
        versionCode = psyzineVersionCode
        versionName = psyzineVersionName
        val sourceUrl = providers.gradleProperty("psyzineSourceUrl").orElse("https://github.com/gurling/PsyZine").get()
        require(sourceUrl.isEmpty() || sourceUrl.matches(Regex("https://github.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))
        buildConfigField("String", "SOURCE_URL", "\"$sourceUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    room {
        schemaDirectory("$projectDir/schemas")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("direct") { dimension = "distribution" }
        create("play") {
            dimension = "distribution"
            applicationIdSuffix = ".play"
        }
    }

    signingConfigs {
        // Never fall back to Android's automatically generated debug certificate.
        getByName("debug") {
            storeFile = retainedStore
            storeType = signingStore.type
            keyAlias = signingValues[1]
            storePassword = signingValues[2]
            keyPassword = signingValues[3]
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            signingConfig = signingConfigs.getByName("debug")
            if (signingMode != "production") versionNameSuffix = "-dev"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk.debugSymbolLevel = "FULL"
        }
        create("compatibleRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-compat.$psyzineVersionCode"
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Launch transitions can reject injected taps before the window is opaque.
        animationsDisabled = true
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("compatibleRelease")) { variant ->
        if (variant.productFlavors.any { it.second == "play" }) variant.enable = false
    }
}

dependencies {
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    androidTestImplementation(libs.androidx.glance.appwidget.testing)
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive.navigation.suite.android)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose)

    implementation(libs.androidx.core.splashscreen)

}

kapt {
    correctErrorTypes = true
}
