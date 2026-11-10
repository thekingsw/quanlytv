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
        // (2026-11-09) versionCode/versionName CHỈ nằm ở AndroidManifest.xml
        // (27/4.0) — trước đây Gradle ghi 4/1.3 đè lên manifest, 2 nguồn
        // lệch nhau. Không khai ở đây thì AGP lấy từ manifest.
    }

    // Fixed debug keystore so every CI build signs with the same key:
    // updates install over the previous version instead of failing.
    // The keystore is stored base64-encoded (debug.keystore.b64) and
    // decoded at configuration time. Debug keys are not sensitive.
    // It is the same key that signed the first manual APK, so a CI APK
    // can update over it if that APK ever got installed.
    // (2026-11-09) Chỉ ghi đè storeFile khi keystore thật sự có — clone
    // sạch không có file thì để AGP tự sinh debug keystore mặc định,
    // build không fail (trước đây trỏ vào file không tồn tại nên CI fail).
    signingConfigs {
        // Configure the built-in debug signing config (AGP already creates
        // one named "debug", so use getByName instead of create).
        getByName("debug") {
            val b64File = file("debug.keystore.b64")
            val ksFile = file("debug.keystore")
            if (!ksFile.exists() && b64File.exists()) {
                ksFile.writeBytes(Base64.getDecoder().decode(b64File.readText().trim()))
            }
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
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
    // Unit test chạy trên JVM (LockPolicy, SchedulePolicy, ForegroundTracker, PinGuard,
    // LockDecisionTest, ArchitectureTest). Thiếu dòng này thì `gradle :app:testDebugUnitTest`
    // (bước test trong CI) không biên dịch được test.
    testImplementation("junit:junit:4.13.2")
}
