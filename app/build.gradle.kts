plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStoreFile = System.getenv("SHIFTALARM_KEYSTORE_PATH") ?: "release-key.jks"
val releaseStorePassword = System.getenv("SHIFTALARM_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("SHIFTALARM_KEY_ALIAS")
val releaseKeyPassword = System.getenv("SHIFTALARM_KEY_PASSWORD")

android {
    namespace = "com.shiftalarm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shiftalarm.app2"
        minSdk = 30
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
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
