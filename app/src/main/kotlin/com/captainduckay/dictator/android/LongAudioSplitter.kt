package com.captainduckay.dictator.android

import com.captainduckay.dictator.core.SpeechActivity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Splits a recording longer than 30 s into pieces that are each transcribed whole. Port of the
 * `LongAudioSplitter` in the Mac and iOS apps.
 *
 * Parakeet through sherpa-onnx reads the whole clip at once: its memory grows with the length, and
 * the ONNX export is reported to fail beyond about 400 s (its position table holds 5,000 encoder
 * frames), well under the 10-minute recording limit.
 *
 * Cutting at the quietest moment in the second half of each window keeps words whole, and keeping
 * every piece at least [MIN_LENGTH] long means none is too short to transcribe. Audio of up to 30 s
 * is one piece, exactly as before.
 */
object LongAudioSplitter {
    /** Longest piece read at once: 30 s at 16 kHz, as on Mac and iOS. */
    const val MAX_LENGTH = 480_000

    /** Shortest piece a split may leave: 2 s. */
    const val MIN_LENGTH = 32_000

    /** The stretch whose loudness is compared when looking for a pause: 0.2 s. */
    internal const val QUIET_WINDOW = 3_200

    /** Distance between candidate cut points: 10 ms. */
    internal const val STEP = 160

    /**
     * Consecutive ranges that cover [samples] exactly, each at most [maxLength] long. When the audio
     * is split, every piece is at least [minLength] long.
     */
    fun ranges(
        samples: FloatArray,
        maxLength: Int = MAX_LENGTH,
        minLength: Int = MIN_LENGTH,
    ): List<IntRange> {
        require(minLength > 0 && minLength * 2 <= maxLength) { "minLength must be at most half of maxLength" }
        if (samples.size <= maxLength) return listOf(0 until samples.size)

        val pieces = mutableListOf<IntRange>()
        var start = 0
        while (samples.size - start > maxLength) {
            // Cut in the second half of the window, and leave at least minLength for what follows.
            val earliest = start + maxLength / 2
            val latest = minOf(start + maxLength, samples.size - minLength)
            val cut = quietestPoint(samples, earliest, latest)
            pieces += start until cut
            start = cut
        }
        pieces += start until samples.size
        return pieces
    }

    /**
     * Transcribes [samples] one piece at a time with [transcribePiece] and joins the texts. A
     * recording of up to 30 s goes to [transcribePiece] unchanged. In a longer one, silent pieces
     * are skipped (the recording as a whole was checked for speech; a silent piece would only give
     * the model room to invent text), and empty results are dropped.
     */
    suspend fun transcribe(
        samples: FloatArray,
        containsSpeech: (FloatArray) -> Boolean = { SpeechActivity.containsSpeech(it) },
        transcribePiece: suspend (FloatArray) -> String,
    ): String {
        val pieces = ranges(samples)
        if (pieces.size == 1) return transcribePiece(samples)
        val texts = mutableListOf<String>()
        for (piece in pieces) {
            currentCoroutineContext().ensureActive()
            val audio = samples.copyOfRange(piece.first, piece.last + 1)
            if (!containsSpeech(audio)) continue
            val text = transcribePiece(audio).trim()
            if (text.isNotEmpty()) texts += text
        }
        return texts.joinToString(" ")
    }

    /**
     * The candidate point in [earliest]..[latest] whose surrounding [QUIET_WINDOW] holds the least
     * energy. On a tie the later point wins, so pieces stay close to the full window.
     */
    private fun quietestPoint(samples: FloatArray, earliest: Int, latest: Int): Int {
        // Running sum of squares over just the stretch the candidates look at (at most about 15 s),
        // so a 10-minute recording doesn't need a second, larger array.
        val from = maxOf(0, earliest - QUIET_WINDOW / 2)
        val to = minOf(samples.size, latest + QUIET_WINDOW / 2)
        val sums = DoubleArray(to - from + 1)
        for (index in from until to) {
            val sample = samples[index].toDouble()
            sums[index - from + 1] = sums[index - from] + sample * sample
        }

        fun energy(
            lower: Int,
            upper: Int,
        ): Double {
            // Clamped to the recording, like the Swift original.
            val low = lower.coerceIn(from, to)
            val high = upper.coerceIn(low, to)
            return sums[high - from] - sums[low - from]
        }

        var best = earliest
        var bestEnergy = Double.POSITIVE_INFINITY
        var point = earliest
        while (true) {
            val value = energy(point - QUIET_WINDOW / 2, point + QUIET_WINDOW / 2)
            if (value <= bestEnergy) {
                best = point
                bestEnergy = value
            }
            if (point == latest) break
            point = minOf(point + STEP, latest)
        }
        return best
    }
}
