package com.captainduckay.dictator.core

/**
 * One edit of a focused text field, expressed as the whole text plus a caret. Callers that only
 * have a selection range (or no selection at all) normalize into this first. Pure, so insertion
 * through an accessibility node can be tested without a device.
 */
data class FieldSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
) {
    val cursor: Int get() = selectionEnd
}

object FieldEdit {
    /**
     * [selectionStart] or [selectionEnd] below zero means the field did not report a caret.
     * The caret is then the end of the text, which is where speech should land.
     */
    fun snapshot(text: String, selectionStart: Int, selectionEnd: Int): FieldSnapshot {
        if (selectionStart < 0 || selectionEnd < 0) return FieldSnapshot(text, text.length, text.length)
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(0, text.length)
        return if (start <= end) FieldSnapshot(text, start, end) else FieldSnapshot(text, end, start)
    }

    /** Replaces the selection, the same way typing does. The caret ends up after the new text. */
    fun insert(field: FieldSnapshot, insertion: String): FieldSnapshot {
        val text = field.text.substring(0, field.selectionStart) + insertion + field.text.substring(field.selectionEnd)
        val cursor = field.selectionStart + insertion.length
        return FieldSnapshot(text, cursor, cursor)
    }

    /** Deletes up to [graphemeCount] user-visible characters before the caret. */
    fun deleteBeforeCursor(field: FieldSnapshot, graphemeCount: Int): FieldSnapshot {
        val before = field.text.substring(0, field.selectionStart)
        val drop = before.graphemes().takeLast(graphemeCount.coerceAtLeast(0)).sumOf { it.length }
        val start = field.selectionStart - drop
        val text = field.text.removeRange(start, field.selectionStart)
        return FieldSnapshot(text, start, start)
    }

    /** Text before the caret, capped at [maxLength] UTF-16 units, for spacing and capitalization. */
    fun preceding(field: FieldSnapshot, maxLength: Int): String {
        val before = field.text.substring(0, field.selectionStart)
        if (maxLength <= 0) return ""
        return if (before.length <= maxLength) before else before.takeLast(maxLength)
    }
}
