package com.captainduckay.dictator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpeechModelsTest {
    private class MemoryStore : StringStore {
        val values = mutableMapOf<String, String>()

        override fun getString(key: String) = values[key]

        override fun putString(key: String, value: String) {
            values[key] = value
        }
    }

    @Test
    fun rankingPutsParakeetFirstThenWhisper() {
        assertEquals(listOf(SpeechModelId.PARAKEET_V2, SpeechModelId.WHISPER_SMALL), SpeechModelId.ranked)
    }

    @Test
    fun everyModelIsPinnedInTheManifest() {
        val manifest = ModelManifest.parse(File("../scripts/model-manifest.txt").readText())
        for (model in SpeechModelId.entries) {
            assertTrue("${model.id} missing from manifest", manifest.files(model.id).isNotEmpty())
        }
        assertEquals(
            setOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            manifest.files(SpeechModelId.PARAKEET_V2.id).map { it.localPath }.toSet(),
        )
    }

    @Test
    fun choiceIsUnsetAtFirstLaunch() {
        val choice = ModelChoice(MemoryStore())
        assertNull(choice.selected)
        assertTrue(choice.needsChoice)
    }

    @Test
    fun choicePersists() {
        val store = MemoryStore()
        ModelChoice(store).choose(SpeechModelId.PARAKEET_V2)
        assertEquals(SpeechModelId.PARAKEET_V2, ModelChoice(store).selected)
        ModelChoice(store).choose(SpeechModelId.WHISPER_SMALL)
        assertEquals(SpeechModelId.WHISPER_SMALL, ModelChoice(store).selected)
        assertFalse(ModelChoice(store).needsChoice)
    }

    @Test
    fun unknownStoredModelCountsAsUnset() {
        val store = MemoryStore().apply { values[ModelChoice.KEY] = "removed-model" }
        assertTrue(ModelChoice(store).needsChoice)
    }

    @Test
    fun pickerShownOnlyWhenNothingIsChosen() {
        assertEquals(ModelStartup.ShowPicker, ModelStartup.decide(null, emptySet()))
        assertEquals(
            ModelStartup.Use(SpeechModelId.PARAKEET_V2),
            ModelStartup.decide(SpeechModelId.PARAKEET_V2, emptySet()),
        )
        assertEquals(
            ModelStartup.Use(SpeechModelId.WHISPER_SMALL),
            ModelStartup.decide(SpeechModelId.WHISPER_SMALL, setOf(SpeechModelId.PARAKEET_V2)),
        )
    }

    @Test
    fun existingWhisperDownloadIsKeptWithoutAsking() {
        assertEquals(
            ModelStartup.Use(SpeechModelId.WHISPER_SMALL),
            ModelStartup.decide(null, setOf(SpeechModelId.WHISPER_SMALL)),
        )
    }
}
