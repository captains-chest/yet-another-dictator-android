package com.captainduckay.dictator.android

import android.content.Context
import com.captainduckay.dictator.core.FieldEdit
import com.captainduckay.dictator.core.FieldSnapshot
import com.captainduckay.dictator.core.PrecedingTextReading
import com.captainduckay.dictator.core.TextInserting
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** What the focused field looks like right now, or why it can't be typed into. */
sealed interface FieldRead {
    data class Ready(val snapshot: FieldSnapshot) : FieldRead

    data object None : FieldRead

    data object Unsupported : FieldRead
}

/** Reads and replaces the text of whatever field currently has the cursor. Never the clipboard. */
interface FocusedField {
    fun read(): FieldRead

    fun write(text: String, cursor: Int): Boolean
}

/**
 * The controller's typing port for the floating button. A dictation is one read and one write of
 * the field. The write replaces the whole text, so splitting it into chunks would only add
 * rewrites, and each later chunk would start from a read that may not show the earlier ones yet
 * (the node can still be the cached copy from before the write), which drops text.
 */
internal class AccessibilityFieldEditor(
    private val context: Context,
    private val field: FocusedField,
    private val typingEnabled: () -> Boolean,
) : TextInserting, PrecedingTextReading {
    override fun textBeforeCursor(maxLength: Int): String? {
        val ready = field.read() as? FieldRead.Ready ?: return null
        return FieldEdit.preceding(ready.snapshot, maxLength)
    }

    override fun preflight() {
        if (!typingEnabled()) throw IllegalStateException(context.getString(R.string.error_needs_accessibility))
        when (field.read()) {
            is FieldRead.Ready -> Unit
            FieldRead.Unsupported -> throw IllegalStateException(context.getString(R.string.error_field_unsupported))
            FieldRead.None -> throw IllegalStateException(context.getString(R.string.error_no_field))
        }
    }

    override suspend fun insert(text: String) {
        currentCoroutineContext().ensureActive()
        edit { FieldEdit.insert(it, text) }
    }

    override suspend fun deleteBackward(count: Int) {
        edit { FieldEdit.deleteBeforeCursor(it, count) }
    }

    private fun edit(transform: (FieldSnapshot) -> FieldSnapshot) {
        val ready =
            field.read() as? FieldRead.Ready
                ?: throw IllegalStateException(context.getString(R.string.error_field_gone))
        val next = transform(ready.snapshot)
        if (!field.write(next.text, next.cursor)) {
            throw IllegalStateException(context.getString(R.string.error_field_gone))
        }
    }
}
