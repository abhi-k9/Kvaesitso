import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Weather plugin for Kvaesitso that gets its forecasts from Open-Meteo (https://open-meteo.com).
// It's a separate app, built and released together with the fork's launcher.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.abhik9.kvaesitso.plugin.openmeteo"

    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        applicationId = "io.github.abhik9.kvaesitso.plugin.openmeteo"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = System.getenv("VERSION_CODE_OVERRIDE")?.toIntOrNull() ?: 1
        versionName = "1.0"
        // Re-signed with the fork's key by the release workflow, like the launcher
        signingConfig = signingConfigs.getByName("debug")
    }

    buildTypes {
        release {
            // Not minified: the app is small anyway, and it can't break the plugin SDK's
            // serialization at runtime
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(project(":plugins:sdk"))
    implementation(libs.bundles.kotlin)

    testImplementation(libs.bundles.tests)
}
