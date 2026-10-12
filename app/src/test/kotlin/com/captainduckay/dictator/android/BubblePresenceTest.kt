package com.captainduckay.dictator.android

import com.captainduckay.dictator.core.DictationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubblePresenceTest {
    @Test fun theMicrophoneAppearsForAFocusedField() {
        assertTrue(visible(inputFocused = true, state = DictationState.Ready, undelivered = false))
    }

    @Test fun theMicrophoneStaysHiddenWithNoField() {
        assertFalse(visible(inputFocused = false, state = DictationState.Ready, undelivered = false))
        assertFalse(visible(inputFocused = false, state = DictationState.LoadingModel, undelivered = false))
    }

    @Test fun anUnfinishedDictationKeepsTheMicrophone() {
        assertTrue(visible(inputFocused = false, state = DictationState.Recording, undelivered = false))
        assertTrue(visible(inputFocused = false, state = DictationState.Transcribing, undelivered = false))
        assertTrue(visible(inputFocused = false, state = DictationState.Ready, undelivered = true))
    }

    private fun visible(inputFocused: Boolean, state: DictationState, undelivered: Boolean) =
        BubblePresence.visible(inputFocused, state, undelivered)
}
