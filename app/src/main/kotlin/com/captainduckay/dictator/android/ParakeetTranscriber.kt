package com.captainduckay.dictator.android

import android.content.Context
import com.captainduckay.dictator.core.SpeechModelId
import com.captainduckay.dictator.core.Transcribing
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * NVIDIA Parakeet TDT 0.6B v2 (English, int8) through sherpa-onnx (ADR 0008), on the CPU. Loads only
 * from the verified folder the app downloaded; sherpa-onnx never downloads. English only, so
 * [transcribe] ignores the language. Audio never leaves the device.
 */
class ParakeetTranscriber(
    private val context: Context,
) : Transcribing {
    class LoadFailed : Exception("The speech model could not be loaded.")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val mutex = Mutex()
    private val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    @Volatile private var recognizer: OfflineRecognizer? = null

    override suspend fun prepare() =
        mutex.withLock {
            if (recognizer != null) return@withLock
            withContext(dispatcher) {
                val model = SpeechModelId.PARAKEET_V2
                if (!SpeechModel.isInstalled(context, model)) throw WhisperTranscriber.ModelMissing()
                val folder = SpeechModel.folder(context, model)
                val config =
                    OfflineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
                        modelConfig =
                            OfflineModelConfig(
                                transducer =
                                    OfflineTransducerModelConfig(
                                        encoder = File(folder, "encoder.int8.onnx").path,
                                        decoder = File(folder, "decoder.int8.onnx").path,
                                        joiner = File(folder, "joiner.int8.onnx").path,
                                    ),
                                tokens = File(folder, "tokens.txt").path,
                                modelType = "nemo_transducer",
                                numThreads = threads,
                            ),
                        decodingMethod = "greedy_search",
                    )
                recognizer =
                    try {
                        OfflineRecognizer(config = config)
                    } catch (_: IllegalArgumentException) {
                        throw LoadFailed()
                    }
            }
        }

    override suspend fun transcribe(samples: FloatArray, language: String?): String =
        mutex.withLock {
            val engine = checkNotNull(recognizer) { "The speech model is not loaded." }
            val text =
                withContext(dispatcher) {
                    val stream = engine.createStream()
                    try {
                        stream.acceptWaveform(samples, SAMPLE_RATE)
                        engine.decode(stream)
                        engine.getResult(stream).text
                    } finally {
                        stream.release()
                    }
                }
            currentCoroutineContext().ensureActive()
            text.trim()
        }

    fun close() {
        recognizer?.release()
        recognizer = null
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FEATURE_DIM = 80
    }
}

/**
 * The transcriber dictation talks to. Before each load it reads the model the user chose and swaps
 * the engine if the choice changed, so switching models doesn't restart the floating button.
 */
class ChosenModelTranscriber(
    private val context: Context,
) : Transcribing {
    private val mutex = Mutex()
    private var loaded: SpeechModelId? = null
    private var engine: Transcribing? = null

    class NoModel : Exception("No speech model is chosen yet. Open Dictator to choose one.")

    override suspend fun prepare() {
        val current =
            mutex.withLock {
                val chosen = SpeechModel.chosen(context) ?: throw NoModel()
                if (chosen != loaded) {
                    close(engine)
                    engine =
                        when (chosen) {
                            SpeechModelId.PARAKEET_V2 -> ParakeetTranscriber(context)
                            SpeechModelId.WHISPER_SMALL -> WhisperTranscriber(context)
                        }
                    loaded = chosen
                }
                engine!!
            }
        current.prepare()
    }

    override suspend fun transcribe(samples: FloatArray, language: String?): String {
        val current = mutex.withLock { engine } ?: throw NoModel()
        return current.transcribe(samples, language)
    }

    fun close() {
        close(engine)
        engine = null
        loaded = null
    }

    private fun close(engine: Transcribing?) {
        when (engine) {
            is ParakeetTranscriber -> engine.close()
            is WhisperTranscriber -> engine.close()
        }
    }
}
