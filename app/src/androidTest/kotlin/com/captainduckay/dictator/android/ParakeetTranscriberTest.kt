package com.captainduckay.dictator.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.captainduckay.dictator.core.SpeechModelId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * End-to-end check of sherpa-onnx and each downloaded Parakeet model on a device or emulator, with the
 * JFK sample. Each test is skipped until its model is on the device (choose and download it in the
 * app, or `scripts/fetch-model.sh <model-id>` then `scripts/push-model.sh <model-id>`).
 */
@RunWith(AndroidJUnit4::class)
class ParakeetTranscriberTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val tests = InstrumentationRegistry.getInstrumentation().context

    private fun jfk(): FloatArray {
        val bytes = tests.assets.open("jfk.wav").use { it.readBytes() }
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = RIFF_HEADER
        while (String(bytes, offset, 4, Charsets.US_ASCII) != "data") offset += CHUNK_HEADER + header.getInt(offset + 4)
        val size = header.getInt(offset + 4)
        val pcm = ByteBuffer.wrap(bytes, offset + CHUNK_HEADER, size).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(pcm.remaining()) { pcm.get(it) / 32768f }
    }

    private fun transcribesJfk(model: SpeechModelId) =
        runBlocking {
            assumeTrue("${model.id} not downloaded", SpeechModel.isInstalled(app, model))
            val transcriber = ParakeetTranscriber(app, model)
            transcriber.prepare()
            val text = transcriber.transcribe(jfk(), null)
            transcriber.close()
            assertTrue(text, text.lowercase().contains("ask not what your country can do for you"))
            text
        }

    @Test fun parakeet06BTranscribesEnglishOffline() {
        transcribesJfk(SpeechModelId.PARAKEET_V2)
    }

    @Test fun parakeet110MTranscribesEnglishWithPunctuation() {
        val text = transcribesJfk(SpeechModelId.PARAKEET_110M)
        // The 110M writes cased, punctuated text ("And so, my fellow Americans, ask not ...").
        assertTrue(text, text.first().isUpperCase() && text.contains(","))
    }

    private companion object {
        const val RIFF_HEADER = 12
        const val CHUNK_HEADER = 8
    }
}
