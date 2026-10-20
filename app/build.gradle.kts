import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version comes from the release workflow (-PversionName=1.2.3 -PversionCode=10203).
// Local builds fall back to a dev version.
val appVersionName = (findProperty("versionName") as String?) ?: "0.0.0-dev"
val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1

// Release signing is configured only when the keystore is provided via environment
// variables (CI secrets). Without them, assembleRelease produces an unsigned APK.
val releaseKeystorePath: String? = System.getenv("DOOMSTOP_KEYSTORE_PATH")

android {
    namespace = "io.github.ggrzesiek.doomstop"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.ggrzesiek.doomstop"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        releaseKeystorePath?.let { keystorePath ->
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("DOOMSTOP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DOOMSTOP_KEY_ALIAS")
                keyPassword = System.getenv("DOOMSTOP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        htmlReport = true
        xmlReport = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// No runtime dependencies on purpose: the app has no INTERNET permission and needs no libraries.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
