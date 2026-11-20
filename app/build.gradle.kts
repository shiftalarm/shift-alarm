plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shiftalarm.app"
    compileSdk = 35

    defaultConfig {
        // Keep the application ID identical to the installed release (run #42)
        // so Android upgrades the existing app instead of creating a different package.
        applicationId = "com.shiftalarm.app2"
        minSdk = 30
        targetSdk = 35
        versionCode = 9
        versionName = "1.2.2"
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
