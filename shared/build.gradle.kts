plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// The app's shared code: data, playlists, settings and the Compose screens. It is a Kotlin
// Multiplatform module with only the Android target for now (the iOS app was removed in v0.9.4).
kotlin {
    android {
        namespace = "com.wildmusicplayer.shared"
        compileSdk = 37
        minSdk = 26
        withHostTestBuilder {}
    }
    jvmToolchain(17)
    // expect/actual classes (FileStore etc.) are still "Beta" in Kotlin; they work fine, so no warning
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
            api(libs.compose.material.icons.extended)
            api(libs.coil.compose)
            implementation(libs.reorderable)
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
