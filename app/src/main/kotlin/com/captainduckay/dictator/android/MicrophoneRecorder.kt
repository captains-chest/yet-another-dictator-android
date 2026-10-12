package com.captainduckay.dictator.android

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.captainduckay.dictator.core.AudioRecording
import com.captainduckay.dictator.core.CaptureBuffer
import com.captainduckay.dictator.core.DictationController

/**
 * Records 16 kHz mono float audio with [AudioRecord] only while a dictation runs. The microphone is
 * closed between dictations, so the system's microphone indicator is only on while dictating. Audio
 * stays in memory and is dropped after transcription.
 */
class MicrophoneRecorder(
    private val onFailure: (String) -> Unit,
    private val onLimit: () -> Unit,
) : AudioRecording {
    private val main = Handler(Looper.getMainLooper())
    private val buffer = CaptureBuffer(capacity = MAX_SECONDS * SAMPLE_RATE, preRoll = 0)
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile private var running = false

    init {
        buffer.onCapacityReached = { main.post(onLimit) }
    }

    // RECORD_AUDIO is checked by the floating button before a dictation can start.
    @SuppressLint("MissingPermission")
    override fun start(onLevel: (Float) -> Unit) {
        check(record == null) { "Already recording." }
        val minimum =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            )
        require(minimum > 0) { "This device cannot record 16 kHz audio." }
        val audio =
            AudioRecord
                .Builder()
                // Raw-ish input meant for speech recognition: no automatic gain or noise suppression.
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build(),
                ).setBufferSizeInBytes(maxOf(minimum, CHUNK * 4 * 4))
                .build()
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            throw IllegalStateException("The microphone is not available.")
        }
        audio.startRecording()
        if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            audio.release()
            throw IllegalStateException("The microphone is in use by another app.")
        }
        buffer.begin(onLevel)
        record = audio
        running = true
        thread =
            Thread({ readLoop(audio) }, "DictatorMicrophone").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
    }

    private fun readLoop(audio: AudioRecord) {
        val chunk = FloatArray(CHUNK)
        while (running) {
            val read = audio.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
            if (read < 0) {
                if (running) main.post { onFailure("The microphone stopped unexpectedly.") }
                return
            }
            if (read > 0) buffer.append(chunk, read)
        }
    }

    override fun stop(): FloatArray {
        running = false
        record?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
                // Already stopped by the system.
            }
        }
        thread?.join(STOP_TIMEOUT_MS)
        record?.release()
        record = null
        thread = null
        return buffer.end()
    }

    companion object {
        const val SAMPLE_RATE = DictationController.SAMPLE_RATE.toInt()
        const val MAX_SECONDS = 10 * 60
        private const val CHUNK = 1_600 // 100 ms
        private const val STOP_TIMEOUT_MS = 500L
    }
}
