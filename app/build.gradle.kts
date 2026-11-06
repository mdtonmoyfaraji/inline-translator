plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.inlinetranslator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.inlinetranslator"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "0.7.0"
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

// Intentionally no third-party dependencies: plain Android Views + HttpURLConnection.
