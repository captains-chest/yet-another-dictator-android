package com.captainduckay.dictator.core

/**
 * Keeps only the most recent [capacity] samples: the microphone pre-roll. Older audio is
 * overwritten and never leaves memory. Not thread-safe; [CaptureBuffer] guards it.
 */
class AudioRingBuffer(val capacity: Int) {
    init {
        require(capacity >= 0)
    }

    private val storage = FloatArray(capacity)
    private var start = 0
    var count = 0
        private set

    fun append(samples: FloatArray, length: Int = samples.size) {
        if (capacity == 0) return
        // Only the newest `capacity` samples can survive.
        for (index in maxOf(0, length - capacity) until length) {
            storage[(start + count) % capacity] = samples[index]
            if (count < capacity) count += 1 else start = (start + 1) % capacity
        }
    }

    /** The held samples, oldest first. */
    val samples: FloatArray get() = FloatArray(count) { storage[(start + it) % capacity] }

    fun removeAll() {
        start = 0
        count = 0
    }
}
