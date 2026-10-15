package com.captainduckay.dictator.core

/**
 * The language Whisper should transcribe. Automatic detection is unreliable for short clips, so a
 * fixed language can be chosen. Dictator is English-only: Danish is not offered.
 */
enum class DictationLanguage(val storedValue: String, val whisperCode: String?, val displayName: String) {
    AUTOMATIC("automatic", null, "Automatic"),
    ENGLISH("english", "en", "English"),
    ;

    companion object {
        /** Reads a stored value, falling back to automatic for anything unknown (including removed `danish`). */
        fun fromStored(value: String?): DictationLanguage = entries.firstOrNull { it.storedValue == value } ?: AUTOMATIC
    }
}
