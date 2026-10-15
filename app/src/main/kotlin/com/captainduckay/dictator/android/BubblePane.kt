package com.captainduckay.dictator.android

import com.captainduckay.dictator.core.DictationState

/**
 * The plank beside the microphone (ADR 0007): what the dictation is doing, and Undo once it is typed.
 * It opens toward the middle of the screen, so the button itself never moves.
 */
internal object BubblePane {
    enum class Kind {
        None,
        Listening,
        Writing,
        Typed,
    }

    /** How long "Typed N words · Undo" stays out after a dictation; the badge keeps Undo after that. */
    const val TYPED_MS = 4000L

    fun kind(state: DictationState, mode: BubbleActions.Mode, problem: Boolean, typedRecently: Boolean): Kind =
        when {
            problem || mode == BubbleActions.Mode.Retry -> Kind.None
            state == DictationState.Recording -> Kind.Listening
            state == DictationState.Transcribing -> Kind.Writing
            mode == BubbleActions.Mode.Undo && typedRecently -> Kind.Typed
            else -> Kind.None
        }

    /** The pane opens toward the middle: on the left when the button is in the right half of the screen. */
    fun onLeft(buttonX: Int, buttonWidth: Int, screenWidth: Int): Boolean = buttonX + buttonWidth / 2 > screenWidth / 2

    /**
     * How tall a level bar is, 0 to 1. The microphone level is gentle for speech, so the quiet floor
     * is cut and the rest is lifted, which gives speech tall peaks.
     */
    fun loudness(level: Float): Float {
        val above = ((level - NOISE_FLOOR) / (1f - NOISE_FLOOR)).coerceIn(0f, 1f)
        return (kotlin.math.sqrt(above) * LIFT).coerceAtMost(1f)
    }

    private const val NOISE_FLOOR = 0.04f
    private const val LIFT = 1.3f

    fun words(text: String): Int = text.split(Regex("\\s+")).count { it.isNotEmpty() }

    fun timer(elapsedMs: Long): String {
        val seconds = (elapsedMs / 1000).coerceAtLeast(0)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
}
