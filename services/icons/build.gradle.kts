import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
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
    namespace = "de.mm20.launcher2.icons"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.palette)

    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.koin.android)

    implementation(project(":data:database"))
    implementation(project(":core:preferences"))
    implementation(project(":core:ktx"))
    implementation(project(":core:base"))
    implementation(project(":data:applications"))
    implementation(project(":core:crashreporter"))
    api(project(":data:customattrs"))

    testImplementation(libs.bundles.tests)
    testImplementation(libs.robolectric)
}

// Robolectric downloads the Android framework while the tests run, so a failed download fails the
// tests. Let Gradle download (and cache) it instead, and run Robolectric offline.
val robolectricAndroidAll by configurations.creating
dependencies {
    robolectricAndroidAll(libs.robolectric.android.all)
}
val robolectricDependencyDir = layout.buildDirectory.dir("robolectric")
val copyRobolectricAndroidAll by tasks.registering(Sync::class) {
    from(robolectricAndroidAll)
    into(robolectricDependencyDir)
}
tasks.withType<Test>().configureEach {
    dependsOn(copyRobolectricAndroidAll)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDependencyDir.get().asFile.absolutePath)
}
