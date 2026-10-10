package com.captainduckay.dictator.core

/**
 * Fits a dictation to the text it is typed after: adds the separating space, and capitalizes or
 * lowercases the first word depending on whether a new sentence starts there. Port of
 * DictationCore's `SmartJoin`.
 */
object SmartJoin {
    /**
     * @param text The cleaned transcript.
     * @param preceding The text just before the cursor (only its end matters), or null when unknown.
     *   Unknown leaves the transcript as Whisper wrote it.
     */
    fun adjust(text: String, preceding: String?): String {
        if (preceding == null) return text
        val first = text.graphemes().firstOrNull() ?: return text
        if (first.isBlankGrapheme()) return text

        var result = text
        if (startsSentence(preceding)) {
            result = capitalizingFirstLetter(result)
        } else {
            loweringFirstWord(result)?.let { result = it }
        }
        if (needsSpace(preceding, first)) result = " $result"
        return result
    }

    /**
     * True when the cursor is at the start of a sentence: the field is empty, the line is new, or the
     * previous sentence ended (possibly followed by a closing quote or bracket).
     */
    fun startsSentence(preceding: String): Boolean {
        val tail = preceding.graphemes().toMutableList()
        while (tail.isNotEmpty() &&
            tail.last().isBlankGrapheme() &&
            !tail.last().isNewline()
        ) {
            tail.removeAt(tail.lastIndex)
        }
        while (tail.isNotEmpty() && tail.last() in closers) tail.removeAt(tail.lastIndex)
        val last = tail.lastOrNull() ?: return true
        return last.isNewline() || last in terminators
    }

    private fun needsSpace(preceding: String, next: String): Boolean {
        val characters = preceding.graphemes()
        val previous = characters.lastOrNull() ?: return false
        if (previous.isBlankGrapheme() || previous in openers) return false
        // A straight quote opens when it follows a space or starts the text, and closes otherwise.
        if (previous == "\"" || previous == "'") {
            val before = characters.getOrNull(characters.size - 2)
            if (before == null || before.isBlankGrapheme() || before in openers) return false
        }
        return next !in attachesToPrevious
    }

    private fun capitalizingFirstLetter(text: String): String {
        val first = text.graphemes().first()
        if (!first.isLowercaseLetter()) return text
        return first.uppercase() + text.substring(first.length)
    }

    /**
     * Lowercases a sentence-style capital in the middle of a sentence ("Hello, How" → "Hello, how").
     * Leaves "I", acronyms ("NASA") and words with inner capitals ("iPhone"). Proper nouns can't be
     * told apart and get lowercased.
     */
    private fun loweringFirstWord(text: String): String? {
        val word = text.graphemes().takeWhile { it.isLetterGrapheme() }
        if (word.size <= 1) return null
        val first = word.first()
        if (!first.isUppercaseLetter() || !word.drop(1).all { it.isLowercaseLetter() }) return null
        return first.lowercase() + text.substring(first.length)
    }

    private val terminators = setOf(".", "!", "?", "…")
    private val closers = setOf("\"", "'", ")", "]", "}", "»", "”", "’")
    private val openers = setOf("(", "[", "{", "«", "“", "‘", "/")
    private val attachesToPrevious = setOf(".", ",", ";", ":", "!", "?", "…", ")", "]", "}", "%", "»", "”", "’")

    private fun String.isNewline() =
        this == "\n" ||
            this == "\r\n" ||
            this == "\r" ||
            this == "\u2028" ||
            this == "\u2029" ||
            this == "\u0085"

    private fun String.isBlankGrapheme() = isNotEmpty() && all { it.isWhitespace() }

    private fun String.isLetterGrapheme() = isNotEmpty() && Character.isLetter(codePointAt(0))

    private fun String.isUppercaseLetter() = isNotEmpty() && Character.isUpperCase(codePointAt(0))

    private fun String.isLowercaseLetter() = isNotEmpty() && Character.isLowerCase(codePointAt(0))
}

/**
 * What the last dictation typed, as long as the user hasn't typed or moved the cursor since. Used as
 * the text before the cursor when the field can't be read, and for undo.
 */
class DictationHistory {
    var lastInserted: String? = null
        private set

    fun didInsert(text: String) {
        lastInserted = text
    }

    /** The user typed or moved the cursor: it may be anywhere now. */
    fun otherInput() {
        lastInserted = null
    }

    /**
     * The number of characters (as the user sees them) to delete to undo the last dictation, or null
     * when it can't be undone safely. Clears the history.
     */
    fun takeUndo(): Int? {
        val text = lastInserted
        lastInserted = null
        if (text.isNullOrEmpty()) return null
        return text.graphemeCount()
    }
}
