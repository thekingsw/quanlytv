import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.tvlock"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.tvlock"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // Fixed debug keystore so every CI build signs with the same key:
    // updates install over the previous version instead of failing.
    // The keystore is stored base64-encoded (debug.keystore.b64) and
    // decoded at configuration time. Debug keys are not sensitive.
    // It is the same key that signed the first manual APK, so a CI APK
    // can update over it if that APK ever got installed.
    signingConfigs {
        // Configure the built-in debug signing config (AGP already creates
        // one named "debug", so use getByName instead of create).
        getByName("debug") {
            val b64File = file("debug.keystore.b64")
            val ksFile = file("debug.keystore")
            if (!ksFile.exists() && b64File.exists()) {
                ksFile.writeBytes(Base64.getDecoder().decode(b64File.readText().trim()))
            }
            storeFile = ksFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // No AndroidX / Compose: plain Views + Kotlin stdlib only.
}
