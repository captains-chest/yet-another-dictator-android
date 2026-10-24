import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.captainduckay.dictator.android"
    compileSdk = 37
    // `-PtestBuildType=release` builds the device test against the signed release build, so the
    // JFK test can run on a phone that has the release APK installed.
    testBuildType = providers.gradleProperty("testBuildType").getOrElse("debug")

    defaultConfig {
        applicationId = "com.captainduckay.dictator.android"
        minSdk = 31 // ADR 0003
        targetSdk = 36
        // CI passes the tag's version and a run-based code; local builds use these defaults.
        versionCode = (System.getenv("VERSION_CODE") ?: "3").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // Package sherpa-onnx's native libraries for arm64 phones and the x86_64 emulator only (ADR 0003).
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

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

    buildTypes {
        // Test build only (branch test/parakeet-110m-plus-splitting, never merged): a separate app
        // id so it installs next to the Play build without touching its settings or models.
        debug {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            testProguardFiles("proguard-test-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
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

// The pinned model manifest ships inside the signed APK: the trust anchor for the download (ADR 0006).
val bundleModelManifest by tasks.registering(Sync::class) {
    from(rootProject.file("scripts/model-manifest.txt"))
    into(layout.buildDirectory.dir("generated/manifest/assets/model"))
}

android.sourceSets
    .getByName("main")
    .assets.directories += listOf("build/generated/docs/assets", "build/generated/manifest/assets")
tasks.named("preBuild") { dependsOn(bundleDocs, bundleModelManifest) }

// sherpa-onnx runs the Parakeet model (ADR 0008). It isn't published to Maven Central, so the
// official release AAR is downloaded at build time and must match this pinned SHA-256, the same way
// the model files are pinned. Bump both together after reviewing the release.
val sherpaOnnxVersion = "1.13.8"
val sherpaOnnxSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
val sherpaOnnxAar = layout.buildDirectory.file("sherpa-onnx/sherpa-onnx-$sherpaOnnxVersion.aar")

val fetchSherpaOnnx =
    tasks.register("fetchSherpaOnnx") {
        val target = sherpaOnnxAar
        val expected = sherpaOnnxSha256
        val url =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar"
        outputs.file(target)
        doLast {
            fun sha256(file: File): String {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }

            val file = target.get().asFile
            if (file.isFile && sha256(file) == expected) return@doLast
            file.parentFile.mkdirs()
            val part = File(file.path + ".part")
            URI(url).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
            val actual = sha256(part)
            if (actual != expected) {
                part.delete()
                throw GradleException("sherpa-onnx AAR SHA-256 mismatch: expected $expected, got $actual")
            }
            part.renameTo(file)
        }
    }

tasks.named("preBuild") { dependsOn(fetchSherpaOnnx) }

dependencies {
    implementation(project(":core"))
    implementation(files(sherpaOnnxAar).builtBy(fetchSherpaOnnx))
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
