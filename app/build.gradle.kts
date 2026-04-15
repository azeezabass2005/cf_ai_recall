plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.ranti"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ranti"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Deployed Cloudflare Worker URL.
        // For local dev swap back to your LAN IP and run `wrangler dev`.
        buildConfigField("String", "RANTI_BASE_URL", "\"https://ranti-worker.azeezabass2005.workers.dev\"")

        // Keep APK size manageable — PocketSphinx ships native libs for every ABI.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            buildConfigField("String", "RANTI_BASE_URL", "\"https://ranti-worker.azeezabass2005.workers.dev\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Networking
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

    // Location — geofencing for location-based reminders
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Wake word — CMU PocketSphinx offline keyword spotting (bundled local AAR).
    // The AAR lives at app/libs/pocketsphinx-android-5prealpha-release.aar and
    // ships prebuilt JNI libs for arm64-v8a, armeabi-v7a, x86, and x86_64.
    // Acoustic model + dictionary are in assets/sync/models/; "ranti" has been
    // added to words.dic with phoneme string "R AE N T IY".
    implementation(files("libs/pocketsphinx-android-5prealpha-release.aar"))

    debugImplementation("androidx.compose.ui:ui-tooling")
}
