package com.captainduckay.dictator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Ported from DictationCore's CaptureBufferTests.swift.
class CaptureBufferTest {
    private fun CaptureBuffer.add(vararg samples: Float) = append(samples)

    private fun CaptureBuffer.endList() = end().toList()

    @Test fun preRollKeepsTheNewestSamplesAndIsPrependedOnBegin() {
        val buffer = CaptureBuffer(100, 3)
        buffer.add(1f, 2f, 3f, 4f, 5f)
        buffer.begin {}
        buffer.add(6f, 7f)
        assertEquals(listOf(3f, 4f, 5f, 6f, 7f), buffer.endList())
    }

    @Test fun clearedPreRollIsNotPrepended() {
        val buffer = CaptureBuffer(100, 3)
        buffer.add(1f, 2f)
        buffer.clearPreRoll()
        buffer.begin {}
        buffer.add(3f)
        assertEquals(listOf(3f), buffer.endList())
    }

    @Test fun endReturnsAndClears() {
        val buffer = CaptureBuffer(100, 0)
        buffer.begin {}
        assertTrue(buffer.isCapturing)
        buffer.add(1f, 2f)
        assertEquals(listOf(1f, 2f), buffer.endList())
        assertFalse(buffer.isCapturing)
        assertTrue(buffer.end().isEmpty())
    }

    @Test fun truncatesExactlyAtCapacity() {
        val buffer = CaptureBuffer(4, 0)
        var reached = 0
        buffer.onCapacityReached = { reached++ }
        buffer.begin {}
        buffer.add(1f, 2f, 3f, 4f)
        assertEquals(0, reached)
        buffer.add(5f)
        assertEquals(listOf(1f, 2f, 3f, 4f), buffer.endList())
        assertEquals(1, reached)
    }

    @Test fun truncatesAChunkThatOverflows() {
        val buffer = CaptureBuffer(3, 0)
        buffer.begin {}
        buffer.add(1f, 2f)
        buffer.add(3f, 4f, 5f)
        assertEquals(listOf(1f, 2f, 3f), buffer.endList())
    }

    @Test fun capacityCallbackFiresOncePerCaptureAndAgainAfterBegin() {
        val buffer = CaptureBuffer(2, 0)
        var reached = 0
        buffer.onCapacityReached = { reached++ }
        buffer.begin {}
        buffer.add(1f, 2f, 3f)
        buffer.add(4f)
        buffer.add(5f, 6f)
        assertEquals(1, reached)
        buffer.end()
        buffer.begin {}
        buffer.add(1f)
        assertEquals(1, reached)
        buffer.add(2f, 3f)
        assertEquals(2, reached)
    }

    @Test fun noCapacityCallbackOutsideACapture() {
        val buffer = CaptureBuffer(1, 2)
        var reached = 0
        buffer.onCapacityReached = { reached++ }
        buffer.add(1f, 2f, 3f, 4f)
        assertEquals(0, reached)
    }

    @Test fun onLevelOnlyDuringACapture() {
        val buffer = CaptureBuffer(100, 4)
        var levels = 0
        buffer.add(0.5f)
        buffer.begin { levels++ }
        buffer.add(0.5f, 0.5f)
        assertEquals(1, levels)
        buffer.end()
        buffer.add(0.5f)
        assertEquals(1, levels)
    }
}
