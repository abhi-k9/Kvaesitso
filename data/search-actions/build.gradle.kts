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
    namespace = "de.mm20.launcher2.searchactions"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)

    implementation(libs.koin.android)
    implementation(libs.jsoup)
    implementation(libs.bundles.ktor)
    implementation(libs.coil.core)

    implementation(project(":core:base"))
    implementation(project(":core:profiles"))
    implementation(project(":data:database"))
    implementation(project(":core:ktx"))
    implementation(project(":core:preferences"))
    implementation(project(":core:crashreporter"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

// Resolve Robolectric's Android runtime with Gradle, including proxy and cache support.
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
