package com.captainduckay.dictator.android

import com.captainduckay.dictator.core.DictationState

/**
 * The microphone is the only on-screen control, and it is there for the field the user is in.
 * A dictation that is still recording, transcribing, or waiting to be typed keeps the button up
 * so the finger can finish and a failed insert can be retried.
 */
internal object BubblePresence {
    const val FIELD_GONE_MS = 200L

    fun visible(inputFocused: Boolean, state: DictationState, undelivered: Boolean): Boolean =
        inputFocused ||
            undelivered ||
            state == DictationState.Recording ||
            state == DictationState.Transcribing
}
