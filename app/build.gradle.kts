plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    id("androidx.room")
    kotlin("plugin.serialization") version "2.0.20"
    id("kotlin-kapt") // this needs to be on bottom
}

android {
    namespace = "com.isaakhanimann.journal"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.psyzine"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
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

    // Production credentials come from the environment and never enter source control.
    // Ordinary builds use the debug certificate and explicitly label release versions -dev.
    val signingInputs = listOf("PSYZINE_RELEASE_KEYSTORE_FILE", "PSYZINE_KEY_ALIAS",
        "PSYZINE_STORE_PASSWORD", "PSYZINE_KEY_PASSWORD").associateWith {
        providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank)
    }
    require(signingInputs.values.all { it == null } || signingInputs.values.all { it != null }) {
        "Set all four PsyZine production-signing variables, or leave all unset for development signing."
    }
    val productionSigning = signingInputs.values.all { it != null }
    signingConfigs {
        providers.environmentVariable("PSYZINE_DEVELOPMENT_KEYSTORE").orNull?.let { path ->
            getByName("debug") {
                storeFile = file(path)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (productionSigning) {
            create("psyzineProduction") {
                storeFile = file(checkNotNull(signingInputs["PSYZINE_RELEASE_KEYSTORE_FILE"]))
                require(checkNotNull(storeFile).isFile) { "PsyZine production keystore is unavailable." }
                storePassword = signingInputs["PSYZINE_STORE_PASSWORD"]
                keyAlias = signingInputs["PSYZINE_KEY_ALIAS"]
                keyPassword = signingInputs["PSYZINE_KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            signingConfig = signingConfigs.getByName(if (productionSigning) "psyzineProduction" else "debug")
            if (!productionSigning) versionNameSuffix = "-dev"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk.debugSymbolLevel = "FULL"
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

dependencies {
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    androidTestImplementation(libs.androidx.glance.appwidget.testing)
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
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
