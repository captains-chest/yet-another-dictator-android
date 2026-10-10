package com.captainduckay.dictator.core

/**
 * The language Whisper should transcribe. Automatic detection is unreliable for short clips (Danish
 * is easily mistaken for Norwegian or English), so a fixed language can be chosen.
 */
enum class DictationLanguage(val storedValue: String, val whisperCode: String?, val displayName: String) {
    AUTOMATIC("automatic", null, "Automatic"),
    DANISH("danish", "da", "Dansk"),
    ENGLISH("english", "en", "English"),
    ;

    companion object {
        /** Reads a stored value, falling back to automatic for anything unknown. */
        fun fromStored(value: String?): DictationLanguage = entries.firstOrNull { it.storedValue == value } ?: AUTOMATIC
    }
}
