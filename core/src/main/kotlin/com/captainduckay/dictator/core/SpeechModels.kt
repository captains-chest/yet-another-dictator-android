package com.captainduckay.dictator.core

/**
 * The speech models Dictator can download, best first (ADR 0008). Nothing is downloaded until the
 * user picks one. [id] is the model-id in `model-manifest.txt`.
 */
enum class SpeechModelId(
    val id: String,
    val englishOnly: Boolean,
) {
    // Declaration order is the ranking shown in the picker: best first.
    PARAKEET_V2("parakeet-tdt-0.6b-v2-int8", englishOnly = true),
    WHISPER_SMALL("ggml-small-q5_1", englishOnly = false),
    ;

    companion object {
        /** Best first. */
        val ranked: List<SpeechModelId> get() = entries

        fun fromId(id: String?): SpeechModelId? = entries.firstOrNull { it.id == id }
    }
}

/** The few strings the model choice is stored as. The app backs it with SharedPreferences. */
interface StringStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String)
}

/** The user's model choice. Unset until they pick one. */
class ModelChoice(
    private val store: StringStore,
) {
    val selected: SpeechModelId? get() = SpeechModelId.fromId(store.getString(KEY))

    /** The first-launch picker is shown only while this is true. */
    val needsChoice: Boolean get() = selected == null

    fun choose(model: SpeechModelId) = store.putString(KEY, model.id)

    companion object {
        const val KEY = "speechModel"
    }
}

/** What to do about the model at launch. Pure, so "never download on its own" is tested. */
sealed interface ModelStartup {
    /** Nothing is chosen: show the picker and download nothing. */
    data object ShowPicker : ModelStartup

    /** Use the model chosen earlier. Downloading it still needs the user's opt-in. */
    data class Use(
        val model: SpeechModelId,
    ) : ModelStartup

    companion object {
        /**
         * [installed] lists models whose verified files are on disk. A copy that downloaded Whisper
         * before the picker existed keeps using it without being asked.
         */
        fun decide(choice: SpeechModelId?, installed: Set<SpeechModelId>): ModelStartup {
            if (choice != null) return Use(choice)
            val existing = SpeechModelId.ranked.firstOrNull { it in installed } ?: return ShowPicker
            return Use(existing)
        }
    }
}
