plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Code shared by the Android app and (from v0.8.7) the iOS app.
// iOS targets only compile on macOS; GitHub's macOS CI builds and tests them.
kotlin {
    androidLibrary {
        namespace = "com.wildmusicplayer.shared"
        compileSdk = 37
        minSdk = 26
        withHostTestBuilder {}
    }
    // The iOS app links the shared code as a static framework named "Shared"
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvmToolchain(17)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.serialization.json)

            // Shared screens (Compose Multiplatform)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(libs.compose.material.icons.extended)
            api(libs.coil.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
