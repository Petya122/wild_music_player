import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// The Google Play upload key (only the play flavor uses it; Google re-signs for the store)
val uploadPropertiesFile = rootProject.file("upload.properties")
val uploadProperties = Properties()
if (uploadPropertiesFile.exists()) {
    uploadProperties.load(FileInputStream(uploadPropertiesFile))
}

android {
    namespace = "com.wildmusicplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wildmusicplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 54
        versionName = "0.9.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                val storePath = keystoreProperties.getProperty("storeFile").removePrefix("../")
                storeFile = rootProject.file(storePath)
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
        create("upload") {
            if (uploadPropertiesFile.exists()) {
                keyAlias = uploadProperties.getProperty("keyAlias")
                keyPassword = uploadProperties.getProperty("keyPassword")
                storeFile = rootProject.file(uploadProperties.getProperty("storeFile").removePrefix("../"))
                storePassword = uploadProperties.getProperty("storePassword")
            }
        }
    }

    // Two versions of the app:
    // - full: GitHub releases. YouTube search/streaming/downloads, self-update, online lyrics (LRCLIB).
    // - play: Google Play. Only the music on the phone and the user's own lyrics files; Play updates it.
    // The flags are constants, so R8 drops the code they switch off from the play build.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "YOUTUBE", "true")
            buildConfigField("boolean", "SELF_UPDATE", "true")
            buildConfigField("boolean", "ONLINE_LYRICS", "true")
            buildConfigField("boolean", "DONATIONS", "true")
            // Phones update only from APKs signed with this same key
            signingConfig = signingConfigs.getByName(if (keystorePropertiesFile.exists()) "release" else "debug")
        }
        create("play") {
            dimension = "distribution"
            // Its own app on the phone: installs next to the GitHub version
            applicationIdSuffix = ".play"
            buildConfigField("boolean", "YOUTUBE", "false")
            buildConfigField("boolean", "SELF_UPDATE", "false")
            buildConfigField("boolean", "ONLINE_LYRICS", "false")
            // Play's payments policy: no links to outside tips or donations in the app
            buildConfigField("boolean", "DONATIONS", "false")
            signingConfig = signingConfigs.getByName(if (uploadPropertiesFile.exists()) "upload" else "debug")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Signed per flavor (see productFlavors)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

base {
    archivesName.set("wild_music_player-v${android.defaultConfig.versionName}")
}


kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)

    // AndroidX Media3 (ExoPlayer + Bluetooth MediaSession)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.work.runtime)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Networking; YouTube extraction only in the full (GitHub) version
    implementation(libs.okhttp)
    "fullImplementation"(libs.newpipe.extractor)


    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
