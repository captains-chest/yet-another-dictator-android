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

    private val manifest = ModelManifest.parse(File("../scripts/model-manifest.txt").readText())

    @Test
    fun rankingPutsParakeet06BFirstThen110M() {
        assertEquals(listOf(SpeechModelId.PARAKEET_V2, SpeechModelId.PARAKEET_110M), SpeechModelId.ranked)
        assertEquals("parakeet-tdt-ctc-110m-en-int8", SpeechModelId.PARAKEET_110M.id)
        assertTrue(SpeechModelId.entries.all { it.englishOnly })
    }

    @Test
    fun the110MIsSmallerThanThe06B() {
        assertTrue(
            manifest.totalBytes(SpeechModelId.PARAKEET_110M.id) * 4 < manifest.totalBytes(SpeechModelId.PARAKEET_V2.id),
        )
    }

    @Test
    fun everyModelIsPinnedInTheManifestWithExactlyTheFilesItLoads() {
        assertEquals(SpeechModelId.entries.map { it.id }, manifest.modelIds)
        for (model in SpeechModelId.entries) {
            assertEquals(
                model.id,
                RecognizerSetup.of(model).files.toSet(),
                manifest.files(model.id).map { it.localPath }.toSet(),
            )
        }
    }

    @Test
    fun the110MIsNemoCtcNotATransducer() {
        val setup = RecognizerSetup.of(SpeechModelId.PARAKEET_110M)
        assertEquals(RecognizerSetup.NemoCtc("model.int8.onnx", "tokens.txt"), setup)
        assertEquals("nemo_ctc", setup.modelType)
        val big = RecognizerSetup.of(SpeechModelId.PARAKEET_V2)
        assertTrue(big is RecognizerSetup.NemoTransducer)
        assertEquals("nemo_transducer", big.modelType)
        assertEquals(16_000, RecognizerSetup.SAMPLE_RATE)
        assertEquals(80, RecognizerSetup.FEATURE_DIM)
        assertEquals("greedy_search", RecognizerSetup.DECODING_METHOD)
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
        ModelChoice(store).choose(SpeechModelId.PARAKEET_110M)
        assertEquals(SpeechModelId.PARAKEET_110M, ModelChoice(store).selected)
        assertFalse(ModelChoice(store).needsChoice)
    }

    @Test
    fun unknownStoredModelCountsAsUnset() {
        val store = MemoryStore().apply { values[ModelChoice.KEY] = "removed-model" }
        assertTrue(ModelChoice(store).needsChoice)
    }

    @Test
    fun storedWhisperChoiceShowsThePickerAndDownloadsNothing() {
        // What 0.4.0 stored when Whisper Small was chosen.
        val store = MemoryStore().apply { values[ModelChoice.KEY] = "ggml-small-q5_1" }
        val choice = ModelChoice(store)
        assertNull(choice.selected)
        assertTrue(choice.needsChoice)
        assertEquals(ModelStartup.ShowPicker, ModelStartup.decide(choice.selected, emptySet()))
        // Picking a model afterwards replaces the stale value.
        choice.choose(SpeechModelId.PARAKEET_110M)
        assertEquals("parakeet-tdt-ctc-110m-en-int8", store.values[ModelChoice.KEY])
    }

    @Test
    fun storedWhisperChoiceAdoptsAnInstalledParakeetWithoutAsking() {
        val store = MemoryStore().apply { values[ModelChoice.KEY] = "ggml-small-q5_1" }
        assertEquals(
            ModelStartup.Use(SpeechModelId.PARAKEET_110M),
            ModelStartup.decide(ModelChoice(store).selected, setOf(SpeechModelId.PARAKEET_110M)),
        )
    }

    @Test
    fun whisperIsRetiredAndGoneFromTheManifest() {
        assertEquals(setOf("ggml-small-q5_1"), SpeechModelId.retiredIds)
        for (id in SpeechModelId.retiredIds) {
            assertNull(SpeechModelId.fromId(id))
            assertTrue(manifest.files(id).isEmpty())
        }
    }

    @Test
    fun pickerShownOnlyWhenNothingIsChosen() {
        assertEquals(ModelStartup.ShowPicker, ModelStartup.decide(null, emptySet()))
        assertEquals(
            ModelStartup.Use(SpeechModelId.PARAKEET_V2),
            ModelStartup.decide(SpeechModelId.PARAKEET_V2, emptySet()),
        )
        assertEquals(
            ModelStartup.Use(SpeechModelId.PARAKEET_110M),
            ModelStartup.decide(SpeechModelId.PARAKEET_110M, setOf(SpeechModelId.PARAKEET_V2)),
        )
    }

    @Test
    fun anInstalledModelIsKeptWithoutAskingBestFirst() {
        assertEquals(
            ModelStartup.Use(SpeechModelId.PARAKEET_V2),
            ModelStartup.decide(null, setOf(SpeechModelId.PARAKEET_110M, SpeechModelId.PARAKEET_V2)),
        )
    }
}
