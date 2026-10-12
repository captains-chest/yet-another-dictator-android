package com.captainduckay.dictator.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleGestureTest {
    @Test fun aShortPressIsATap() {
        assertEquals(
            BubbleGesture.Release.Tap,
            BubbleGesture.onRelease(holding = false, dragging = false, fingerDownMs = 120, canceled = false),
        )
    }

    @Test fun aDragIsIgnored() {
        assertEquals(
            BubbleGesture.Release.Ignored,
            BubbleGesture.onRelease(holding = false, dragging = true, fingerDownMs = 400, canceled = false),
        )
    }

    @Test fun aCancelIsIgnored() {
        assertEquals(
            BubbleGesture.Release.Ignored,
            BubbleGesture.onRelease(holding = false, dragging = false, fingerDownMs = 100, canceled = true),
        )
    }

    @Test fun aHoldReportsHowLongTheFingerWasDown() {
        assertEquals(
            BubbleGesture.Release.Hold(900),
            BubbleGesture.onRelease(holding = true, dragging = false, fingerDownMs = 900, canceled = false),
        )
    }

    @Test fun aWindowIsNotAddedAgainBeforeItAttaches() {
        assertTrue(BubbleGesture.needsAdding(requested = false, attached = false))
        assertFalse(BubbleGesture.needsAdding(requested = true, attached = false))
        assertFalse(BubbleGesture.needsAdding(requested = false, attached = true))
    }

    @Test fun aDelayedHoldStillStops() {
        assertTrue(BubbleGesture.holdNeedsForcedStop(fingerDownMs = 360, stillRecording = true))
        assertFalse(BubbleGesture.holdNeedsForcedStop(fingerDownMs = 360, stillRecording = false))
        assertFalse(BubbleGesture.holdNeedsForcedStop(fingerDownMs = 200, stillRecording = true))
    }
}
