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
 * End-to-end check of sherpa-onnx and the downloaded Parakeet model on a device or emulator, with the
 * same JFK sample as [WhisperTranscriberTest]. Skipped until the model is on the device (choose and
 * download it in the app, or `scripts/fetch-model.sh parakeet-tdt-0.6b-v2-int8` then
 * `scripts/push-model.sh parakeet-tdt-0.6b-v2-int8`).
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

    @Test fun transcribesEnglishOffline() =
        runBlocking {
            assumeTrue("model not downloaded", SpeechModel.isInstalled(app, SpeechModelId.PARAKEET_V2))
            val transcriber = ParakeetTranscriber(app)
            transcriber.prepare()
            val text = transcriber.transcribe(jfk(), null)
            transcriber.close()
            assertTrue(text, text.lowercase().contains("ask not what your country can do for you"))
        }

    private companion object {
        const val RIFF_HEADER = 12
        const val CHUNK_HEADER = 8
    }
}
