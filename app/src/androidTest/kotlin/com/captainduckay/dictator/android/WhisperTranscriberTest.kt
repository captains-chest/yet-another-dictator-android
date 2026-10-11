package com.captainduckay.dictator.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * End-to-end check of the JNI bridge and the downloaded model on a device or emulator: transcribes
 * whisper.cpp's JFK sample (16 kHz mono PCM16, MIT, from the whisper.cpp repository). Skipped when
 * the model isn't downloaded yet (download it in the app, or push it with scripts/push-model.sh).
 */
@RunWith(AndroidJUnit4::class)
class WhisperTranscriberTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val tests = InstrumentationRegistry.getInstrumentation().context

    private fun jfk(): FloatArray {
        val bytes = tests.assets.open("jfk.wav").use { it.readBytes() }
        // Walk the RIFF chunks to "data"; the sample also carries a LIST chunk.
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = RIFF_HEADER
        while (String(bytes, offset, 4, Charsets.US_ASCII) != "data") offset += CHUNK_HEADER + header.getInt(offset + 4)
        val size = header.getInt(offset + 4)
        val pcm = ByteBuffer.wrap(bytes, offset + CHUNK_HEADER, size).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(pcm.remaining()) { pcm.get(it) / 32768f }
    }

    @Test fun transcribesEnglishOffline() =
        runBlocking {
            assumeTrue("model not downloaded", SpeechModel.isInstalled(app))
            val transcriber = WhisperTranscriber(app)
            transcriber.prepare()
            val text = transcriber.transcribe(jfk(), "en")
            transcriber.close()
            assertTrue(text, text.lowercase().contains("ask not what your country can do for you"))
        }

    private companion object {
        const val RIFF_HEADER = 12
        const val CHUNK_HEADER = 8
    }
}
