package com.captainduckay.dictator.android

import android.content.Context
import com.captainduckay.dictator.core.DictationLanguage

/** The few stored preferences. No text and no audio are ever stored. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var language: DictationLanguage
        get() = DictationLanguage.fromStored(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.storedValue).apply()

    var lastSeenVersion: String?
        get() = prefs.getString(KEY_LAST_SEEN, null)
        set(value) = prefs.edit().putString(KEY_LAST_SEEN, value).apply()

    /** Where the floating button was last left, in pixels from the top left. Null until it is moved. */
    fun bubblePosition(): Pair<Int, Int>? {
        if (!prefs.contains(KEY_BUBBLE_X) || !prefs.contains(KEY_BUBBLE_Y)) return null
        return prefs.getInt(KEY_BUBBLE_X, 0) to prefs.getInt(KEY_BUBBLE_Y, 0)
    }

    fun saveBubblePosition(x: Int, y: Int) {
        prefs
            .edit()
            .putInt(KEY_BUBBLE_X, x)
            .putInt(KEY_BUBBLE_Y, y)
            .apply()
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_LAST_SEEN = "lastSeenVersion"
        const val KEY_BUBBLE_X = "bubbleX"
        const val KEY_BUBBLE_Y = "bubbleY"
    }
}
