package com.captainduckay.dictator.android

import com.captainduckay.dictator.android.BubblePane.Kind
import com.captainduckay.dictator.core.DictationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubblePaneTest {
    private val hidden = BubbleActions.Mode.Hidden

    @Test fun followsTheDictation() {
        assertEquals(Kind.Listening, BubblePane.kind(DictationState.Recording, hidden, false, false))
        assertEquals(Kind.Writing, BubblePane.kind(DictationState.Transcribing, hidden, false, false))
        assertEquals(Kind.Typed, BubblePane.kind(DictationState.Ready, BubbleActions.Mode.Undo, false, true))
    }

    @Test fun closedWhenIdleOrLateOrInTrouble() {
        assertEquals(Kind.None, BubblePane.kind(DictationState.Ready, hidden, false, true))
        assertEquals(Kind.None, BubblePane.kind(DictationState.Ready, BubbleActions.Mode.Undo, false, false))
        assertEquals(Kind.None, BubblePane.kind(DictationState.Recording, hidden, true, false))
        assertEquals(Kind.None, BubblePane.kind(DictationState.Ready, BubbleActions.Mode.Retry, false, true))
    }

    @Test fun opensTowardTheMiddle() {
        assertTrue(BubblePane.onLeft(buttonX = 900, buttonWidth = 160, screenWidth = 1080))
        assertFalse(BubblePane.onLeft(buttonX = 20, buttonWidth = 160, screenWidth = 1080))
    }

    @Test fun barsPeakForSpeechAndRestInSilence() {
        assertEquals(0f, BubblePane.loudness(0.02f), 0f)
        assertTrue(BubblePane.loudness(0.3f) > 0.6f)
        assertEquals(1f, BubblePane.loudness(0.7f), 0f)
    }

    @Test fun countsWordsAndTime() {
        assertEquals(3, BubblePane.words(" Hello there,  world. "))
        assertEquals(0, BubblePane.words(""))
        assertEquals("0:07", BubblePane.timer(7_900))
        assertEquals("1:05", BubblePane.timer(65_000))
    }
}
