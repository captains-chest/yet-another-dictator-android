package com.captainduckay.dictator.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Ports, implemented by the app. All calls happen on the controller's scope (the main thread).

/** Captures microphone audio as 16 kHz mono float samples (Whisper's native format). */
interface AudioRecording {
    /** [onLevel] receives a 0..1 loudness value, possibly from the audio thread. */
    fun start(onLevel: (Float) -> Unit)

    fun stop(): FloatArray
}

/** Turns 16 kHz mono samples into text. Must work fully offline. */
interface Transcribing {
    suspend fun prepare()

    /** [language] is a Whisper code ("da", "en"), or null to detect it from the audio. */
    suspend fun transcribe(samples: FloatArray, language: String?): String
}

/** Delivers text into the focused field. */
interface TextInserting {
    /** Throws a user-facing error if [insert] is known to fail (e.g. no text field). */
    fun preflight()

    /** Types [text]. Must stop between chunks when the coroutine is cancelled. */
    suspend fun insert(text: String)

    /** Deletes [count] user-visible characters before the cursor. */
    suspend fun deleteBackward(count: Int)
}

/** How long one dictation took, for latency logging. Contains no text or audio. */
data class DictationTiming(
    val audioSeconds: Double,
    val releaseToTypedSeconds: Double,
    val transcriptionSeconds: Double,
)

sealed interface DictationState {
    data object LoadingModel : DictationState

    data object Ready : DictationState

    data object Recording : DictationState

    data object Transcribing : DictationState

    /** The model could not be loaded; dictation is disabled. */
    data class Unavailable(val message: String) : DictationState
}

/**
 * Orchestrates press → record → transcribe → insert. A quick tap toggles recording; holding records
 * until release (push-to-talk). Kotlin port of DictationCore's `DictationController`; the behaviour
 * and its tests mirror the Swift original (ADR 0005).
 */
class DictationController(
    private val scope: CoroutineScope,
    private val recorder: AudioRecording,
    private val transcriber: Transcribing,
    private val inserter: TextInserting,
    private val now: () -> Double = { System.nanoTime() / 1e9 },
    private val holdThreshold: Double = 0.35,
    minimumDuration: Double = 0.3,
    private val stopTail: Duration = 250.milliseconds,
    private val transcriptionTimeout: (sampleCount: Int) -> Duration = ::defaultTranscriptionTimeout,
    private val containsSpeech: (FloatArray) -> Boolean = { SpeechActivity.containsSpeech(it) },
) {
    private val mutableState = MutableStateFlow<DictationState>(DictationState.LoadingModel)
    val state: StateFlow<DictationState> = mutableState.asStateFlow()

    private val mutableLevel = MutableStateFlow(0f)

    /** Live input loudness while recording, 0..1. */
    val level: StateFlow<Float> = mutableLevel.asStateFlow()

    private val mutableError = MutableStateFlow<String?>(null)

    /** Last non-fatal error, cleared on the next successful start. */
    val lastError: StateFlow<String?> = mutableError.asStateFlow()

    private val mutableUndelivered = MutableStateFlow<String?>(null)

    /** A transcript that could not be typed, kept in memory only so it isn't lost. */
    val undeliveredTranscript: StateFlow<String?> = mutableUndelivered.asStateFlow()

    /** Language for the next transcriptions. */
    var language: DictationLanguage = DictationLanguage.AUTOMATIC

    /** Reads the text just before the cursor, or returns null. Never stored or logged. */
    var textBeforeCursor: (() -> String?)? = null
    var onTiming: ((DictationTiming) -> Unit)? = null

    private val minimumSamples = (minimumDuration * SAMPLE_RATE).toInt()
    private var isInStopTail = false
    private var pressedAt: Double? = null
    private var transcription: Job? = null
    private var watchdog: Job? = null

    /** Identifies the current transcription; bumped on cancel or timeout so a late result is ignored. */
    private var generation = 0
    private val history = DictationHistory()

    /** Whether the last dictation can still be undone. */
    val canUndo: Boolean get() = state.value == DictationState.Ready && history.lastInserted != null

    private fun setState(value: DictationState) {
        mutableState.value = value
    }

    suspend fun loadModel() {
        setState(DictationState.LoadingModel)
        setState(
            try {
                transcriber.prepare()
                DictationState.Ready
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                DictationState.Unavailable(error.message ?: error.toString())
            },
        )
    }

    fun pressed() {
        when (state.value) {
            DictationState.Ready -> {
                try {
                    inserter.preflight()
                } catch (error: Exception) {
                    report(error)
                    return
                }
                pressedAt = now()
                startRecording()
            }
            DictationState.Recording -> {
                pressedAt = null
                finishRecording()
            }
            else -> Unit
        }
    }

    fun released() {
        val pressed = pressedAt ?: return
        pressedAt = null
        if (state.value == DictationState.Recording && now() - pressed >= holdThreshold) finishRecording()
    }

    /** Stops recording, or abandons the running transcription, without typing anything. */
    fun cancel() {
        when (state.value) {
            DictationState.Recording -> {
                pressedAt = null
                recorder.stop()
                mutableLevel.value = 0f
                setState(DictationState.Ready)
            }
            DictationState.Transcribing -> {
                closeStopTail()
                abandonTranscription()
                setState(DictationState.Ready)
            }
            else -> Unit
        }
    }

    /** The user typed or moved the cursor, so the last dictation can no longer be undone safely. */
    fun noteOtherInput() = history.otherInput()

    /** The microphone stopped on its own during recording. What was captured is dropped. */
    fun recordingFailed(message: String) {
        if (state.value != DictationState.Recording) return
        pressedAt = null
        recorder.stop()
        mutableLevel.value = 0f
        setState(DictationState.Ready)
        report(message)
    }

    /** The recording reached the capture buffer's capacity; it is transcribed and typed as usual. */
    fun recordingReachedLimit() {
        if (state.value != DictationState.Recording) return
        pressedAt = null
        finishRecording()
        report("Recording reached the 10-minute limit and was stopped.")
    }

    suspend fun undoLastDictation() {
        if (state.value != DictationState.Ready) return
        val count = history.takeUndo() ?: return
        try {
            inserter.deleteBackward(count)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            report(error)
        }
    }

    /** Types the undelivered transcript again, e.g. once a text field is focused. */
    suspend fun retryUndelivered() {
        if (state.value != DictationState.Ready) return
        val text = mutableUndelivered.value ?: return
        try {
            inserter.insert(text)
            history.didInsert(text)
            mutableUndelivered.value = null
            mutableError.value = null
        } catch (error: CancellationException) {
            history.otherInput()
            throw error
        } catch (error: Exception) {
            history.otherInput()
            report(error)
        }
    }

    fun discardUndelivered() {
        mutableUndelivered.value = null
    }

    private fun report(error: Exception) = report(error.message ?: error.toString())

    private fun report(message: String) {
        // Clearing first makes a repeated identical failure observable again.
        mutableError.value = null
        mutableError.value = message
    }

    private fun startRecording() {
        try {
            recorder.start { level -> mutableLevel.value = level }
            mutableError.value = null
            setState(DictationState.Recording)
        } catch (error: Exception) {
            pressedAt = null
            report("Could not start microphone: ${error.message ?: error}")
        }
    }

    private fun finishRecording() {
        val stoppedAt = now()
        setState(DictationState.Transcribing)
        generation += 1
        val id = generation
        isInStopTail = true
        transcription = scope.launch { finishAndTranscribe(id, stoppedAt) }
    }

    private fun closeStopTail(): FloatArray {
        if (!isInStopTail) return FloatArray(0)
        isInStopTail = false
        mutableLevel.value = 0f
        return recorder.stop()
    }

    /** Keeps the microphone open for [stopTail] so the last syllable isn't clipped. */
    private suspend fun finishAndTranscribe(id: Int, stoppedAt: Double) {
        if (stopTail > Duration.ZERO) delay(stopTail)
        if (id != generation) return
        val samples = closeStopTail()
        // Too short or silent: nothing was said, and Whisper would only invent text.
        if (samples.size < minimumSamples || !containsSpeech(samples)) {
            transcription = null
            setState(DictationState.Ready)
            return
        }
        val timeout = transcriptionTimeout(samples.size)
        watchdog =
            scope.launch {
                delay(timeout)
                if (generation != id || state.value != DictationState.Transcribing) return@launch
                abandonTranscription()
                report("Transcription took too long and was stopped. Please try again.")
                setState(DictationState.Ready)
            }
        transcribeAndInsert(samples, id, stoppedAt)
    }

    private fun abandonTranscription() {
        generation += 1
        transcription?.cancel()
        transcription = null
        watchdog?.cancel()
        watchdog = null
    }

    private suspend fun transcribeAndInsert(samples: FloatArray, id: Int, stoppedAt: Double) {
        val transcriptionStart = now()
        val outcome =
            try {
                Result.success(transcriber.transcribe(samples, language.whisperCode))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        val transcriptionEnd = now()
        if (id != generation) return
        watchdog?.cancel()
        watchdog = null
        val raw =
            outcome.getOrElse {
                finishTranscription()
                report(it as Exception)
                return
            }
        val text = TranscriptCleaner.clean(raw)
        if (text.isEmpty()) {
            finishTranscription()
            return
        }
        // The field's own text when it can be read; otherwise our previous dictation; otherwise
        // unknown. An empty reading right after our own dictation is distrusted.
        val fieldText = textBeforeCursor?.invoke()
        val preceding = if (!fieldText.isNullOrEmpty()) fieldText else (history.lastInserted ?: fieldText)
        val typed = SmartJoin.adjust(text, preceding)
        // Typing stays in Transcribing, so cancel() can still stop it between chunks.
        val failure: Exception? =
            try {
                inserter.insert(typed)
                null
            } catch (error: CancellationException) {
                history.otherInput()
                throw error
            } catch (error: Exception) {
                error
            }
        if (id != generation) {
            history.otherInput()
            return
        }
        finishTranscription()
        if (failure == null) {
            history.didInsert(typed)
            mutableUndelivered.value = null
            onTiming?.invoke(
                DictationTiming(
                    audioSeconds = samples.size / SAMPLE_RATE,
                    releaseToTypedSeconds = now() - stoppedAt,
                    transcriptionSeconds = transcriptionEnd - transcriptionStart,
                ),
            )
        } else {
            history.otherInput()
            mutableUndelivered.value = text
            report("${failure.message ?: failure} Your dictation is kept in the Dictator app.")
        }
    }

    private fun finishTranscription() {
        transcription = null
        setState(DictationState.Ready)
    }

    companion object {
        const val SAMPLE_RATE = 16_000.0

        /** 30 s plus the audio's length: far beyond normal, but bounded. */
        fun defaultTranscriptionTimeout(sampleCount: Int): Duration = 30.seconds + (sampleCount / SAMPLE_RATE).seconds
    }
}
