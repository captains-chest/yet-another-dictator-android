package com.captainduckay.dictator.android

import android.inputmethodservice.InputMethodService
import com.captainduckay.dictator.core.TextChunker
import com.captainduckay.dictator.core.TextInserting
import com.captainduckay.dictator.core.graphemes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/**
 * Types into the focused field through the keyboard's [android.view.inputmethod.InputConnection].
 * Never uses the clipboard. Text is committed in small chunks so a cancelled dictation stops typing
 * at once, like the macOS and iOS inserters.
 */
class ImeTextInserter(private val service: InputMethodService) : TextInserting {
    /** Where the cursor will be after our own typing, so the keyboard can tell it apart from the user's. */
    var expectedCursor: Int = -1
        private set

    /** The cursor as last reported by the editor. */
    var cursor: Int = -1

    override fun preflight() {
        if (service.currentInputConnection == null || service.currentInputEditorInfo?.inputType == 0) {
            throw IllegalStateException(service.getString(R.string.error_no_field))
        }
    }

    override suspend fun insert(text: String) {
        for (chunk in TextChunker.chunks(text)) {
            currentCoroutineContext().ensureActive()
            val connection =
                service.currentInputConnection
                    ?: throw IllegalStateException(service.getString(R.string.error_field_gone))
            if (cursor >= 0) expectedCursor = cursor.coerceAtLeast(expectedCursor) + chunk.length
            if (!connection.commitText(
                    chunk,
                    1,
                )
            ) {
                throw IllegalStateException(service.getString(R.string.error_field_gone))
            }
            yield()
        }
    }

    override suspend fun deleteBackward(count: Int) {
        val connection =
            service.currentInputConnection ?: throw IllegalStateException(service.getString(R.string.error_field_gone))
        val before = connection.getTextBeforeCursor(count * MAX_UTF16_PER_GRAPHEME, 0)?.toString() ?: return
        val length = before.graphemes().takeLast(count).sumOf { it.length }
        connection.deleteSurroundingText(length, 0)
    }

    fun textBeforeCursor(): String? = service.currentInputConnection?.getTextBeforeCursor(CONTEXT_LENGTH, 0)?.toString()

    fun resetExpectation() {
        expectedCursor = -1
    }

    private companion object {
        const val CONTEXT_LENGTH = 200
        const val MAX_UTF16_PER_GRAPHEME = 16
    }
}
