plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.adesh.devjarvis"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.adesh.devjarvis"
        minSdk = 26
        targetSdk = 36
        // CI run number -> every build has a higher versionCode, so in-app updates install over the old one.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = 100 + (run ?: 0)
        versionName = "1.0." + (run ?: 0)
        buildConfigField("String", "UPDATE_REPO", "\"singhadesh0534-dev/dev-jarvis\"")
    }

    // One fixed key for every build, so a new APK installs over the old one (live updates keep data).
    // The key is never in the repo: CI gets it from the JARVIS_KEYSTORE_B64 secret, a laptop from
    // JARVIS_KEYSTORE_PATH. Without it builds fall back to the (per-machine) debug key. See app/signing/README.md.
    fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
    val keystore = env("JARVIS_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("personal") {
                storeFile = keystore
                storePassword = env("JARVIS_KEYSTORE_PASSWORD") ?: "jarvis-personal"
                keyAlias = env("JARVIS_KEY_ALIAS") ?: "jarvis"
                keyPassword = env("JARVIS_KEY_PASSWORD") ?: "jarvis-personal"
            }
        }
    }
    val signing = if (keystore != null) signingConfigs.getByName("personal") else signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = signing
        }
        release {
            isMinifyEnabled = false
            signingConfig = signing
        }
    }

    flavorDimensions += "edition"
    productFlavors {
        // Everything, incl. Accessibility (screen agent) and Notification access (alerts/replies).
        create("full") {
            dimension = "edition"
        }
        // No Accessibility / Notification-listener services, so Play Protect's sideloading fraud check doesn't
        // block the install. Same package: upgrading Lite -> Full keeps all data.
        create("lite") {
            dimension = "edition"
            versionNameSuffix = "-lite"
        }
    }

    lint {
        // Personal sideload app: don't fail release builds on lint warnings.
        checkReleaseBuilds = false
        abortOnError = false
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.vosk.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
