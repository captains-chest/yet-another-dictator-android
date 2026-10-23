package com.captainduckay.dictator.android

import android.content.Context
import com.captainduckay.dictator.core.RecognizerSetup
import com.captainduckay.dictator.core.SpeechModelId
import com.captainduckay.dictator.core.Transcribing
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
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
 * An NVIDIA Parakeet model (English, int8) through sherpa-onnx, on the CPU: TDT 0.6B v2 as a
 * transducer (ADR 0008) or TDT-CTC 110M through its CTC branch (ADR 0010). Loads only from the
 * verified folder the app downloaded; sherpa-onnx never downloads. English only, so [transcribe]
 * ignores the language. Audio never leaves the device.
 */
class ParakeetTranscriber(
    private val context: Context,
    private val model: SpeechModelId,
) : Transcribing {
    class ModelMissing : Exception(
        "The speech model isn't downloaded yet. Open Dictator to download it.",
    )

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
                if (!SpeechModel.isInstalled(context, model)) throw ModelMissing()
                val config = recognizerConfig(RecognizerSetup.of(model), SpeechModel.folder(context, model), threads)
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
        const val SAMPLE_RATE = RecognizerSetup.SAMPLE_RATE
    }
}

/**
 * The sherpa-onnx configuration for [setup], with files from [folder]. Pure, so the unit tests check
 * that the 110M is loaded as NeMo CTC and the 0.6B as a NeMo transducer.
 */
internal fun recognizerConfig(setup: RecognizerSetup, folder: File, threads: Int): OfflineRecognizerConfig {
    fun path(name: String) = File(folder, name).path
    val tokens = path(setup.tokens)
    val modelConfig =
        when (setup) {
            is RecognizerSetup.NemoTransducer ->
                OfflineModelConfig(
                    transducer =
                        OfflineTransducerModelConfig(
                            encoder = path(setup.encoder),
                            decoder = path(setup.decoder),
                            joiner = path(setup.joiner),
                        ),
                    tokens = tokens,
                    modelType = setup.modelType,
                    numThreads = threads,
                )
            is RecognizerSetup.NemoCtc ->
                OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = path(setup.model)),
                    tokens = tokens,
                    modelType = setup.modelType,
                    numThreads = threads,
                )
        }
    return OfflineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = RecognizerSetup.SAMPLE_RATE, featureDim = RecognizerSetup.FEATURE_DIM),
        modelConfig = modelConfig,
        decodingMethod = RecognizerSetup.DECODING_METHOD,
    )
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
                        ParakeetTranscriber(context, chosen)
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
        (engine as? ParakeetTranscriber)?.close()
    }
}
