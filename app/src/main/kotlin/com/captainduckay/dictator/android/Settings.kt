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

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_LAST_SEEN = "lastSeenVersion"
    }
}
