plugins {
    id("com.android.application")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "dev.besan.browserbrake"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val stableTestKeyPath = System.getenv("APPLOCKOUT_TEST_KEY_PATH")
    val stableTestStorePassword = System.getenv("APPLOCKOUT_TEST_STORE_PASSWORD")
    val stableTestKeyAlias = System.getenv("APPLOCKOUT_TEST_KEY_ALIAS")
    val stableTestKeyPassword = System.getenv("APPLOCKOUT_TEST_KEY_PASSWORD")
    val stableTestKey = stableTestKeyPath?.let(::file)
    val canUseStableTestKey = stableTestKey?.exists() == true &&
        !stableTestStorePassword.isNullOrBlank() &&
        !stableTestKeyAlias.isNullOrBlank() &&
        !stableTestKeyPassword.isNullOrBlank()

    if (canUseStableTestKey) {
        signingConfigs {
            create("stableTest") {
                storeFile = stableTestKey
                storePassword = stableTestStorePassword
                keyAlias = stableTestKeyAlias
                keyPassword = stableTestKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "dev.besan.browserbrake"
        minSdk = 29
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        debug {
            if (canUseStableTestKey) {
                signingConfig = signingConfigs.getByName("stableTest")
            }
        }
        release {
            signingConfig = if (canUseStableTestKey) {
                signingConfigs.getByName("stableTest")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
