plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

// Code shared by the Android app and (from v0.8.7) the iOS app.
// iOS targets only compile on macOS; GitHub's macOS CI builds and tests them.
kotlin {
    androidLibrary {
        namespace = "com.wildmusicplayer.shared"
        compileSdk = 35
        minSdk = 26
        withHostTestBuilder {}
    }
    iosArm64()
    iosSimulatorArm64()

    jvmToolchain(17)

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
