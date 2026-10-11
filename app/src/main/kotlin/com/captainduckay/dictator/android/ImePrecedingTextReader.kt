package com.captainduckay.dictator.android

import android.inputmethodservice.InputMethodService
import com.captainduckay.dictator.core.PrecedingTextReading

/**
 * The controller's [PrecedingTextReading] port for the keyboard: reads the focused field through
 * the input connection. Null when there is no connection or the editor won't say.
 */
class ImePrecedingTextReader(private val service: InputMethodService) : PrecedingTextReading {
    override fun textBeforeCursor(maxLength: Int): String? =
        service.currentInputConnection?.getTextBeforeCursor(maxLength, 0)?.toString()
}
