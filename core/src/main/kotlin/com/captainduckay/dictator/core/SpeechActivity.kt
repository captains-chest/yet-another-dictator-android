package com.captainduckay.dictator.core

import kotlin.math.ceil

/**
 * A cheap loudness check that tells clearly silent recordings apart from ones that may hold speech.
 *
 * Whisper invents text for silence and faint noise ("Thank you.", "Tekster af …"), so a clip with no
 * speech must never reach it. Only audio that stays below a quiet-room level for almost all of its
 * length is rejected. Port of DictationCore's `SpeechActivity`.
 */
object SpeechActivity {
    /** Analysis frame: 30 ms at 16 kHz. */
    const val FRAME_LENGTH = 480

    /** Frame RMS that counts as audible: about −48 dBFS. */
    const val LEVEL_THRESHOLD = 0.004f

    /** Audible audio needed in total. Shorter bursts (a tap on the screen) don't count. */
    const val MINIMUM_AUDIBLE_SECONDS = 0.1

    fun containsSpeech(samples: FloatArray, sampleRate: Double = 16_000.0): Boolean {
        val needed = ceil(MINIMUM_AUDIBLE_SECONDS * sampleRate / FRAME_LENGTH).toInt()
        val threshold = LEVEL_THRESHOLD * LEVEL_THRESHOLD * FRAME_LENGTH
        var audible = 0
        var start = 0
        while (start + FRAME_LENGTH <= samples.size) {
            var sumOfSquares = 0f
            for (index in start until start + FRAME_LENGTH) sumOfSquares += samples[index] * samples[index]
            if (sumOfSquares >= threshold) {
                audible += 1
                if (audible >= needed) return true
            }
            start += FRAME_LENGTH
        }
        return false
    }
}
