package com.captainduckay.dictator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelManifestTest {
    private val sha = "a".repeat(64)

    @Test fun parsesEntriesPerModel() {
        val manifest =
            ModelManifest.parse(
                """
                # comment
                small  $sha  10  org/repo/resolve/abc/a.bin  a.bin

                small  $sha  5  org/repo/resolve/abc/dir/b.bin  dir/b.bin
                dansk  $sha  7  org/other/resolve/def/c.bin  c.bin
                """.trimIndent(),
            )
        assertEquals(listOf("small", "dansk"), manifest.modelIds)
        assertEquals(15L, manifest.totalBytes("small"))
        assertEquals("https://huggingface.co/org/other/resolve/def/c.bin", manifest.files("dansk").single().url)
    }

    @Test fun rejectsUnsafePathsAndBadHashes() {
        assertThrows(IllegalArgumentException::class.java) { ModelManifest.parse("m  $sha  1  r/x  ../x") }
        assertThrows(IllegalArgumentException::class.java) { ModelManifest.parse("m  $sha  1  r/x  /x") }
        assertThrows(IllegalArgumentException::class.java) { ModelManifest.parse("m  nothex  1  r/x  x") }
        assertThrows(IllegalArgumentException::class.java) { ModelManifest.parse("m  $sha  1  r/x") }
    }

    @Test fun theShippedManifestParses() {
        val text = java.io.File("../scripts/model-manifest.txt").readText()
        val manifest = ModelManifest.parse(text)
        assertEquals(listOf("ggml-small-q5_1", "parakeet-tdt-0.6b-v2-int8"), manifest.modelIds)
    }
}
