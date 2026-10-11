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
 * End-to-end check of the JNI bridge and the bundled model on a device or emulator: transcribes
 * whisper.cpp's JFK sample (16 kHz mono PCM16, MIT, from the whisper.cpp repository). Skipped when
 * the build has no model (run scripts/fetch-model.sh first).
 */
@RunWith(AndroidJUnit4::class)
class WhisperTranscriberTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val tests = InstrumentationRegistry.getInstrumentation().context

    private fun jfk(): FloatArray {
        val bytes = tests.assets.open("jfk.wav").use { it.readBytes() }
        val pcm =
            ByteBuffer
                .wrap(
                    bytes,
                    WAV_HEADER,
                    bytes.size - WAV_HEADER,
                ).order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
        return FloatArray(pcm.remaining()) { pcm.get(it) / 32768f }
    }

    @Test fun transcribesEnglishOffline() =
        runBlocking {
            assumeTrue("no model bundled", SpeechModel.isBundled(app))
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
