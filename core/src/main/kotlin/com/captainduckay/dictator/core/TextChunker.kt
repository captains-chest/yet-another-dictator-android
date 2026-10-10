package com.captainduckay.dictator.core

/**
 * Splits text into chunks for one `commitText` call each, never splitting a grapheme cluster, so a
 * cancelled dictation stops typing promptly. Port of DictationCore's `TextChunker`.
 */
object TextChunker {
    const val MAX_UTF16_PER_CHUNK = 20

    fun chunks(text: String, maxUtf16: Int = MAX_UTF16_PER_CHUNK): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (character in text.graphemes()) {
            if (current.length + character.length > maxUtf16 && current.isNotEmpty()) {
                result += current.toString()
                current.clear()
            }
            current.append(character)
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }
}
