package com.captainduckay.dictator.core

/**
 * A short, well-punctuated "previous text" given to Whisper as its initial prompt, so short
 * dictations keep their final full stop or question mark. Bilingual; it only sets the style. Port
 * of DictationCore's `DictationPrompt`.
 */
object DictationPrompt {
    const val TEXT = "Hej, hvordan går det? Det går godt, tak. Hello, how are you? I'm fine, thanks."

    /**
     * Whisper sometimes repeats its prompt when the audio holds no speech. True when the transcript
     * is a run of at least half the prompt's words, word for word.
     */
    fun isEcho(transcript: String): Boolean {
        val spoken = words(transcript)
        val prompt = words(TEXT)
        if (spoken.size * 2 < prompt.size || spoken.size > prompt.size) return false
        return (0..(prompt.size - spoken.size)).any { start -> prompt.subList(start, start + spoken.size) == spoken }
    }

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
}
