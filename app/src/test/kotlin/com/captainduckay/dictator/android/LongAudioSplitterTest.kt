package com.captainduckay.dictator.android

import com.captainduckay.dictator.core.SpeechActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random
import kotlin.math.sin

class LongAudioSplitterTest {
    private companion object {
        const val RATE = 16_000
    }

    /** A steady tone, loud enough to count as speech everywhere. */
    private fun tone(seconds: Double): FloatArray =
        FloatArray((seconds * RATE).toInt()) {
            0.3f *
                sin(it * 0.2).toFloat()
        }

    private fun assertCovers(
        pieces: List<IntRange>,
        count: Int,
    ) {
        assertEquals(0, pieces.first().first)
        assertEquals(count, pieces.last().last + 1)
        for ((previous, next) in pieces.zipWithNext()) assertEquals(previous.last + 1, next.first)
        for (piece in pieces) assertTrue("piece of ${piece.size()}", piece.size() <= LongAudioSplitter.MAX_LENGTH)
        if (pieces.size > 1) {
            for (piece in pieces) assertTrue("piece of ${piece.size()}", piece.size() >= LongAudioSplitter.MIN_LENGTH)
        }
    }

    private fun IntRange.size() = last + 1 - first

    @Test fun upToThirtySecondsIsOnePiece() {
        for (count in listOf(0, 1, 16_000, 479_999, 480_000)) {
            val samples = FloatArray(count) { 0.1f }
            assertEquals(listOf(0 until count), LongAudioSplitter.ranges(samples))
        }
    }

    @Test fun cutsInThePause() {
        // 40 s of "speech" with a 0.3 s pause at 25 s.
        val samples = tone(40.0)
        val pause = 25 * RATE until 25 * RATE + 4_800
        for (index in pause) samples[index] = 0f

        val pieces = LongAudioSplitter.ranges(samples)

        assertEquals(2, pieces.size)
        val cut = pieces[0].last + 1
        assertTrue("cut at $cut", cut in pause)
        assertCovers(pieces, samples.size)
    }

    /** Just over 30 s: the tail must be long enough to transcribe on its own. */
    @Test fun justOverThirtySecondsKeepsALongEnoughTail() {
        val samples = tone(30.5)

        val pieces = LongAudioSplitter.ranges(samples)

        assertEquals(2, pieces.size)
        assertTrue(pieces[1].size() >= LongAudioSplitter.MIN_LENGTH)
        assertCovers(pieces, samples.size)
    }

    @Test fun withoutAPauseCutsAsLateAsAllowed() {
        // Equally loud everywhere (±0.5 squares exactly, so every stretch has the same energy).
        val samples = FloatArray(70 * RATE) { if (it % 2 == 0) 0.5f else -0.5f }

        val pieces = LongAudioSplitter.ranges(samples)

        assertEquals(listOf(480_000, 480_000, 160_000), pieces.map { it.size() })
        assertCovers(pieces, samples.size)
    }

    @Test fun piecesCoverTheRecording() {
        for (seconds in listOf(30.01, 31.0, 45.5, 60.0, 61.9, 90.0, 600.0)) {
            val samples = tone(seconds)
            // Some pauses at irregular places.
            var start = 7 * RATE
            while (start < samples.size - 3_200) {
                for (index in start until start + 3_200) samples[index] = 0.001f
                start += 11 * RATE + 1_234
            }

            assertCovers(LongAudioSplitter.ranges(samples), samples.size)
        }
    }

    @Test fun aShortDictationGoesToTheEngineUnchanged() =
        runBlocking {
            val samples = tone(12.0)
            val heard = mutableListOf<FloatArray>()

            val text =
                LongAudioSplitter.transcribe(samples, containsSpeech = {
                    fail("not checked")
                    true
                }) {
                    heard += it
                    " As is. "
                }

            assertEquals(" As is. ", text)
            assertEquals(1, heard.size)
            assertSame(samples, heard[0])
        }

    @Test fun aLongDictationIsTranscribedPieceByPieceInOrder() =
        runBlocking {
            val samples = tone(70.0)
            val pieces = LongAudioSplitter.ranges(samples)
            val heard = mutableListOf<Int>()

            val text =
                LongAudioSplitter.transcribe(samples) {
                    heard += it.size
                    " Piece ${heard.size}. "
                }

            assertEquals(pieces.map { it.size() }, heard)
            assertEquals(heard.sum(), samples.size)
            assertEquals("Piece 1. Piece 2. Piece 3.", text)
        }

    @Test fun silentPiecesAreSkipped() =
        runBlocking {
            // Speech, then 30 s of silence, then speech: the silent middle never reaches the engine.
            val samples = tone(20.0) + FloatArray(30 * RATE) + tone(20.0)
            assertEquals(3, LongAudioSplitter.ranges(samples).size)
            val heard = mutableListOf<FloatArray>()

            val text =
                LongAudioSplitter.transcribe(samples) {
                    heard += it
                    if (heard.size == 1) "First." else "Last."
                }

            assertEquals(2, heard.size)
            assertTrue(
                heard.all {
                    com.captainduckay.dictator.core.SpeechActivity
                        .containsSpeech(it)
                },
            )
            assertEquals("First. Last.", text)
        }

    @Test fun emptyResultsAreDropped() =
        runBlocking {
            val samples = tone(70.0)
            var calls = 0

            val text =
                LongAudioSplitter.transcribe(samples) {
                    calls += 1
                    if (calls == 2) "  " else "Piece $calls."
                }

            assertEquals(3, calls)
            assertEquals("Piece 1. Piece 3.", text)
        }

    /** The cut points match the Swift original, which sums the whole recording up front. */
    @Test fun cutsMatchTheSwiftAlgorithm() {
        val random = Random(19)
        for (seconds in listOf(30.2, 47.0, 95.5, 181.0)) {
            val samples = FloatArray((seconds * RATE).toInt()) { (random.nextGaussian() * 0.1).toFloat() }
            // Quieter stretches of varying depth, so there is a real quietest point to find.
            var start = 3 * RATE
            while (start < samples.size - 8_000) {
                val depth = 0.01f + random.nextFloat() * 0.2f
                for (index in start until start + 8_000) samples[index] *= depth
                start += 5 * RATE + random.nextInt(4 * RATE)
            }

            assertEquals("$seconds s", reference(samples), LongAudioSplitter.ranges(samples))
        }
    }

    /** Straight port of the Swift `LongAudioSplitter.ranges`, prefix sums over the whole recording. */
    private fun reference(samples: FloatArray): List<IntRange> {
        val max = LongAudioSplitter.MAX_LENGTH
        val min = LongAudioSplitter.MIN_LENGTH
        val half = LongAudioSplitter.QUIET_WINDOW / 2
        if (samples.size <= max) return listOf(0 until samples.size)
        val sums = DoubleArray(samples.size + 1)
        for (i in samples.indices) sums[i + 1] = sums[i] + samples[i].toDouble() * samples[i].toDouble()

        fun sum(
            lower: Int,
            upper: Int,
        ): Double {
            val low = lower.coerceIn(0, sums.size - 1)
            val high = upper.coerceIn(low, sums.size - 1)
            return sums[high] - sums[low]
        }
        val pieces = mutableListOf<IntRange>()
        var start = 0
        while (samples.size - start > max) {
            val earliest = start + max / 2
            val latest = minOf(start + max, samples.size - min)
            var best = earliest
            var bestEnergy = Double.POSITIVE_INFINITY
            var point = earliest
            while (true) {
                val value = sum(point - half, point + half)
                if (value <= bestEnergy) {
                    best = point
                    bestEnergy = value
                }
                if (point == latest) break
                point = minOf(point + LongAudioSplitter.STEP, latest)
            }
            pieces += start until best
            start = best
        }
        pieces += start until samples.size
        return pieces
    }

    @Test fun cancellingStopsBeforeTheNextPiece() =
        runBlocking {
            val samples = tone(70.0)
            var calls = 0

            try {
                coroutineScope {
                    LongAudioSplitter.transcribe(samples) {
                        calls += 1
                        currentCoroutineContext()[Job]!!.cancel()
                        "Text."
                    }
                }
                fail("expected cancellation")
            } catch (_: CancellationException) {
            }

            assertEquals(1, calls)
        }
}
