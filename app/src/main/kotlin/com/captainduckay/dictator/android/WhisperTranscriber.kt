package com.captainduckay.dictator.android

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PerformanceHintManager
import android.os.Process
import com.captainduckay.dictator.core.DictationPrompt
import com.captainduckay.dictator.core.SpeechModelId
import com.captainduckay.dictator.core.Transcribing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/** On-device transcription with whisper.cpp (ADR 0002). Audio never leaves the device. */
class WhisperTranscriber(private val context: Context) : Transcribing {
    class ModelMissing : Exception(
        "The speech model isn't downloaded yet. Open Dictator to download it.",
    )

    class LoadFailed : Exception("The speech model could not be loaded.")

    private companion object {
        /** A short target asks the scheduler for the fast cores for the whole transcription. */
        const val HINT_TARGET_NANOS = 2_000_000_000L
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val mutex = Mutex()
    private val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
    private val hints = context.getSystemService(PerformanceHintManager::class.java)

    @Volatile private var handle = 0L

    override suspend fun prepare() =
        mutex.withLock {
            if (handle != 0L) return@withLock
            withContext(dispatcher) {
                if (!SpeechModel.isInstalled(context, SpeechModelId.WHISPER_SMALL)) throw ModelMissing()
                val file = File(SpeechModel.folder(context, SpeechModelId.WHISPER_SMALL), SpeechModel.WHISPER_FILE_NAME)
                val descriptor =
                    try {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    } catch (_: FileNotFoundException) {
                        throw ModelMissing()
                    }
                descriptor.use {
                    handle = WhisperNative.load(it.fd, 0L, file.length())
                }
                if (handle == 0L) throw LoadFailed()
            }
        }

    override suspend fun transcribe(samples: FloatArray, language: String?): String =
        mutex.withLock {
            val engine = handle
            check(engine != 0L) { "The speech model is not loaded." }
            val text =
                coroutineScope {
                    var finished = false
                    // Cancelling the dictation asks whisper.cpp to stop at its next step.
                    val watcher =
                        launch {
                            try {
                                awaitCancellation()
                            } finally {
                                if (!finished) WhisperNative.abort(engine)
                            }
                        }
                    val result =
                        withContext(dispatcher) {
                            boosted {
                                WhisperNative.transcribe(engine, samples, language, DictationPrompt.TEXT, threads)
                            }
                        }
                    finished = true
                    watcher.cancel()
                    result
                }
            currentCoroutineContext().ensureActive()
            checkNotNull(text) { "Transcription failed. Please try again." }
            // Whisper sometimes repeats its prompt for audio without speech.
            if (DictationPrompt.isEcho(text)) "" else text
        }

    private fun <T> boosted(block: () -> T): T {
        val calling = Process.myTid()
        val previous =
            try {
                Process.getThreadPriority(calling)
            } catch (_: IllegalArgumentException) {
                Process.THREAD_PRIORITY_DEFAULT
            }
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        } catch (_: IllegalArgumentException) {
            // Some devices refuse a priority change. Transcription still runs.
        } catch (_: SecurityException) {
        }
        val session =
            try {
                hints?.createHintSession(intArrayOf(calling), HINT_TARGET_NANOS)
            } catch (_: RuntimeException) {
                null
            }
        // setPreferPowerEfficiency arrived in API 35. The hint session itself is older.
        if (Build.VERSION.SDK_INT >= 35) session?.setPreferPowerEfficiency(false)
        val started = System.nanoTime()
        return try {
            block()
        } finally {
            try {
                session?.reportActualWorkDuration(System.nanoTime() - started)
                session?.close()
            } catch (_: RuntimeException) {
            }
            try {
                Process.setThreadPriority(previous)
            } catch (_: IllegalArgumentException) {
            } catch (_: SecurityException) {
            }
        }
    }

    fun close() {
        val engine = handle
        handle = 0L
        if (engine != 0L) WhisperNative.free(engine)
    }
}
