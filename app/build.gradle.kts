plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.captainduckay.dictator.android"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    // `-PtestBuildType=release` builds the device test against the signed release build, so the
    // JFK test can run on a phone that has the release APK installed.
    testBuildType = providers.gradleProperty("testBuildType").getOrElse("debug")

    defaultConfig {
        applicationId = "com.captainduckay.dictator.android"
        minSdk = 31 // ADR 0003
        targetSdk = 36
        // CI passes the tag's version and a run-based code; local builds use these defaults.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // arm64 for phones, x86_64 for the emulator (ADR 0002).
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // The model ships as an install-time asset pack (ADR 0004), keeping the base under Play's limit.
    assetPacks += listOf(":model_pack")

    // Release signing comes from the environment (CI secrets); without it the release is unsigned.
    val keystore = System.getenv("ANDROID_UPLOAD_KEYSTORE")
    signingConfigs {
        if (keystore != null) {
            create("upload") {
                storeFile = file(keystore)
                storePassword = System.getenv("ANDROID_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_UPLOAD_KEY_PASSWORD")
            }
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
            testProguardFiles("proguard-test-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
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
