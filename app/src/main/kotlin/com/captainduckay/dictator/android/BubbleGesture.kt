package com.captainduckay.dictator.android

/**
 * Decides what a finger did on the floating microphone. A small pause separates a drag from a
 * press, so moving the button does not open the microphone. The hold threshold matches
 * [com.captainduckay.dictator.core.DictationController]'s default: a press that starts a little
 * late still has to count as a hold when the finger was down long enough.
 */
internal object BubbleGesture {
    const val DRAG_DECISION_MS = 80L
    const val HOLD_MS = 350L

    sealed interface Release {
        data object Ignored : Release

        data object Tap : Release

        data class Hold(val fingerDownMs: Long) : Release
    }

    fun onRelease(holding: Boolean, dragging: Boolean, fingerDownMs: Long, canceled: Boolean): Release =
        when {
            holding -> Release.Hold(fingerDownMs)
            dragging || canceled -> Release.Ignored
            else -> Release.Tap
        }

    /**
     * The controller starts its own hold timer when recording starts. Because that start is delayed
     * until a drag has been ruled out, a finger that was down for a full hold can look like a tap
     * to the controller. This says to stop recording anyway.
     */
    fun holdNeedsForcedStop(fingerDownMs: Long, stillRecording: Boolean): Boolean =
        stillRecording && fingerDownMs >= HOLD_MS

    /**
     * The window manager takes the view before the view says it is attached. Adding it again in
     * that gap crashes the process, and the button is left on screen with nobody to hear a press.
     */
    fun needsAdding(requested: Boolean, attached: Boolean): Boolean = !requested && !attached
}
