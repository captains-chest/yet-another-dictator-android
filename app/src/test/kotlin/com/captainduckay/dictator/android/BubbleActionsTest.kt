package com.captainduckay.dictator.android

import com.captainduckay.dictator.core.DictationState
import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleActionsTest {
    @Test fun undeliveredShowsRetryNotUndo() {
        assertEquals(
            BubbleActions.Mode.Retry,
            BubbleActions.mode(showRetry = true, canUndo = true, state = DictationState.Ready),
        )
    }

    @Test fun readyWithUndoShowsUndo() {
        assertEquals(
            BubbleActions.Mode.Undo,
            BubbleActions.mode(showRetry = false, canUndo = true, state = DictationState.Ready),
        )
    }

    @Test fun hiddenWhenBusyOrNoUndo() {
        assertEquals(
            BubbleActions.Mode.Hidden,
            BubbleActions.mode(showRetry = false, canUndo = true, state = DictationState.Recording),
        )
        assertEquals(
            BubbleActions.Mode.Hidden,
            BubbleActions.mode(showRetry = false, canUndo = false, state = DictationState.Ready),
        )
    }
}
