plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.captainduckay.dictator.android"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.captainduckay.dictator.android"
        minSdk = 31 // ADR 0003
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // arm64 for phones, x86_64 for the emulator (ADR 0002).
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned on purpose: no signing keys live in this repository or in CI.
            signingConfig = null
        }
    }

    androidResources {
        // The model is read straight out of the APK, which requires it to be stored uncompressed.
        noCompress += "bin"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // New library versions arrive through Dependabot PRs, not lint.
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

// CHANGELOG.md (What's New) and the license texts ship inside the app, generated from the repo root
// so there is one source of truth.
val bundleDocs by tasks.registering(Sync::class) {
    from(rootProject.file("CHANGELOG.md"), rootProject.file("LICENSE"), rootProject.file("THIRD_PARTY_NOTICES.md"))
    from(rootProject.file("docs/licenses")) { into("licenses") }
    into(layout.buildDirectory.dir("generated/docs/assets/docs"))
}

android.sourceSets
    .getByName("main")
    .assets.directories += "build/generated/docs/assets"
tasks.named("preBuild") { dependsOn(bundleDocs) }

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.savedstate)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
