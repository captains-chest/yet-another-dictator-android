package com.captainduckay.dictator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(listOf("parakeet-tdt-0.6b-v2-int8", "parakeet-tdt-ctc-110m-en-int8"), manifest.modelIds)
    }

    @Test fun the110MIsPinnedToTheVerifiedFiles() {
        val manifest = ModelManifest.parse(java.io.File("../scripts/model-manifest.txt").readText())
        val files = manifest.files("parakeet-tdt-ctc-110m-en-int8").associateBy { it.localPath }
        val commit =
            "VocaHQ/sherpa-onnx-nemo-parakeet-tdt-ctc-110m-en-int8/resolve/" +
                "43869f73831659dd8b0ccee617ee42474b7aa974"
        val model = files.getValue("model.int8.onnx")
        assertEquals("9177a9146cf32ee0cc8152276ef95116f312018d316be37ccf57f7efea81fc1a", model.sha256)
        assertEquals(131_652_171L, model.bytes)
        assertEquals("https://huggingface.co/$commit/model.int8.onnx", model.url)
        val tokens = files.getValue("tokens.txt")
        assertEquals("450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10", tokens.sha256)
        assertEquals(9_953L, tokens.bytes)
        assertEquals("https://huggingface.co/$commit/tokens.txt", tokens.url)
        assertEquals(2, files.size)
        assertEquals(131_662_124L, manifest.totalBytes("parakeet-tdt-ctc-110m-en-int8"))
    }

    @Test fun noRetiredModelIsListed() {
        val text = java.io.File("../scripts/model-manifest.txt").readText()
        assertFalse(text.contains("ggml", ignoreCase = true))
        assertFalse(text.contains("whisper", ignoreCase = true))
    }
}
