package com.captainduckay.dictator.android

import com.captainduckay.dictator.core.DictationState

/** Which secondary labels sit above the microphone (ADR 0007). */
internal object BubbleActions {
    enum class Mode {
        Hidden,
        Retry,
        Undo,
    }

    /** Undelivered text wins over Undo so Try again / Discard stay alone. */
    fun mode(showRetry: Boolean, canUndo: Boolean, state: DictationState): Mode =
        when {
            showRetry -> Mode.Retry
            canUndo && state == DictationState.Ready -> Mode.Undo
            else -> Mode.Hidden
        }
}
