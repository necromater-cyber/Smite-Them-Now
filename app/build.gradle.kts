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
        versionCode = 5
        versionName = "0.4.0-poc"
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// No dependencies on purpose: the proof of concept uses only the Android SDK.
