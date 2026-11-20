plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shiftalarm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shiftalarm.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("SHIFTALARM_KEYSTORE_PATH") ?: "release-key.jks")
            storePassword = System.getenv("SHIFTALARM_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SHIFTALARM_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("SHIFTALARM_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
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
