package com.captainduckay.dictator.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Whisper Small and whisper.cpp were removed (ADR 0010). Nothing the app ships, builds or downloads
 * may refer to them again: no native code, no CMake, no model line.
 */
class NoWhisperTest {
    private val root = File("..")
    private val retired = Regex("whisper|ggml|cmake|externalNativeBuild", RegexOption.IGNORE_CASE)

    private fun offenders(vararg paths: String): List<String> =
        paths
            .map { File(root, it) }
            .flatMap { if (it.isDirectory) it.walkTopDown().filter(File::isFile).toList() else listOf(it) }
            .filter { file -> retired.containsMatchIn(file.readText()) || retired.containsMatchIn(file.name) }
            .map { it.relativeTo(root).path }

    @Test fun noWhisperInTheAppItsBuildOrItsModels() {
        assertEquals(
            emptyList<String>(),
            offenders(
                "app/src/main",
                "app/build.gradle.kts",
                "app/proguard-rules.pro",
                "scripts",
                "gradle/libs.versions.toml",
                ".github",
            ),
        )
    }

    @Test fun noNativeBuildIsLeft() {
        assertFalse(File(root, "app/src/main/cpp").exists())
    }
}
