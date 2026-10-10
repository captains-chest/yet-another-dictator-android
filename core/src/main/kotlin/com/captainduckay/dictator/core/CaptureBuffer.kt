package com.captainduckay.dictator.core

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Thread-safe sample store shared between the audio thread and the main thread.
 *
 * While a dictation is captured, samples accumulate up to [capacity]. Otherwise only the newest
 * pre-roll samples are kept, and they become the start of the next capture. Port of DictationCore's
 * `CaptureBuffer`; it has no audio dependencies, so it is unit tested here.
 */
class CaptureBuffer(private val capacity: Int, preRoll: Int) {
    private val lock = Any()
    private var capturing = false
    private var samples = FloatArray(0)
    private var sampleCount = 0
    private val preRoll = AudioRingBuffer(preRoll)
    private var onLevel: ((Float) -> Unit)? = null
    private var reachedCapacity = false

    /** Called once per capture, on the first chunk that doesn't fit, outside the lock. Must return quickly. */
    @Volatile var onCapacityReached: (() -> Unit)? = null

    val isCapturing: Boolean get() = synchronized(lock) { capturing }

    fun begin(onLevel: (Float) -> Unit) =
        synchronized(lock) {
            samples = preRoll.samples
            sampleCount = samples.size
            preRoll.removeAll()
            this.onLevel = onLevel
            capturing = true
            reachedCapacity = false
        }

    fun end(): FloatArray =
        synchronized(lock) {
            val result = samples.copyOf(sampleCount)
            samples = FloatArray(0)
            sampleCount = 0
            capturing = false
            onLevel = null
            result
        }

    fun clearPreRoll() = synchronized(lock) { preRoll.removeAll() }

    fun append(chunk: FloatArray, length: Int = chunk.size) {
        var levelCallback: ((Float) -> Unit)? = null
        var capacityCallback: (() -> Unit)? = null
        synchronized(lock) {
            if (!capturing) {
                preRoll.append(chunk, length)
                return
            }
            val room = capacity - sampleCount
            if (room > 0) {
                val taken = min(room, length)
                ensureRoom(sampleCount + taken)
                chunk.copyInto(samples, sampleCount, 0, taken)
                sampleCount += taken
            }
            if (length > maxOf(room, 0) && !reachedCapacity) {
                reachedCapacity = true
                capacityCallback = onCapacityReached
            }
            levelCallback = onLevel
        }
        capacityCallback?.invoke()
        val callback = levelCallback ?: return
        var sumOfSquares = 0f
        for (index in 0 until length) sumOfSquares += chunk[index] * chunk[index]
        val rms = if (length == 0) 0f else sqrt(sumOfSquares / length)
        callback(min(1f, rms * 8))
    }

    private fun ensureRoom(needed: Int) {
        if (needed <= samples.size) return
        samples = samples.copyOf(min(capacity, maxOf(needed, samples.size * 2, 16_000)))
    }
}
