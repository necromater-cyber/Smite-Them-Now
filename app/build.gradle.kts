plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.necromater.smitethemnow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.necromater.smitethemnow"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-poc"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// No dependencies on purpose: the proof of concept uses only the Android SDK.
