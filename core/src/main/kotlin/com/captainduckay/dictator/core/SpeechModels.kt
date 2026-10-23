package com.captainduckay.dictator.core

/**
 * The speech models Dictator can download, best first (ADR 0008). Nothing is downloaded until the
 * user picks one. [id] is the model-id in `model-manifest.txt`.
 */
enum class SpeechModelId(
    val id: String,
    val englishOnly: Boolean,
) {
    // Declaration order is the ranking shown in the picker: most accurate first (ADR 0010).
    PARAKEET_V2("parakeet-tdt-0.6b-v2-int8", englishOnly = true),
    PARAKEET_110M("parakeet-tdt-ctc-110m-en-int8", englishOnly = true),
    ;

    companion object {
        /** Best first. */
        val ranked: List<SpeechModelId> get() = entries

        /**
         * Model-ids earlier versions could store or download that this version no longer offers
         * (Whisper Small, removed in ADR 0010). A stored retired id reads as "nothing chosen", and its
         * leftover folder is deleted.
         */
        val retiredIds: Set<String> = setOf("ggml-small-q5_1")

        fun fromId(id: String?): SpeechModelId? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Which sherpa-onnx recogniser a model needs and the files it loads, relative to the model's folder.
 * Pure, so the 110M's CTC setup is tested without a device (ADR 0010).
 */
sealed interface RecognizerSetup {
    /** sherpa-onnx `modelType`. */
    val modelType: String
    val tokens: String

    /** All files the recogniser opens; each must be pinned in the manifest. */
    val files: List<String>

    /** NeMo TDT transducer: encoder, decoder and joiner (Parakeet TDT 0.6B v2). */
    data class NemoTransducer(
        val encoder: String,
        val decoder: String,
        val joiner: String,
        override val tokens: String,
    ) : RecognizerSetup {
        override val modelType = "nemo_transducer"
        override val files get() = listOf(encoder, decoder, joiner, tokens)
    }

    /** NeMo CTC: one model file (the CTC branch of the hybrid TDT-CTC 110M). */
    data class NemoCtc(
        val model: String,
        override val tokens: String,
    ) : RecognizerSetup {
        override val modelType = "nemo_ctc"
        override val files get() = listOf(model, tokens)
    }

    companion object {
        /** Both models are 16 kHz with 80 mel bins and use greedy search. */
        const val SAMPLE_RATE = 16_000
        const val FEATURE_DIM = 80
        const val DECODING_METHOD = "greedy_search"

        fun of(model: SpeechModelId): RecognizerSetup =
            when (model) {
                SpeechModelId.PARAKEET_V2 ->
                    NemoTransducer("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")
                SpeechModelId.PARAKEET_110M -> NemoCtc("model.int8.onnx", "tokens.txt")
            }
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
         * [installed] lists models whose verified files are on disk. With nothing chosen (first
         * launch, or a retired model such as Whisper Small was chosen), an installed model is adopted
         * without asking; otherwise the picker is shown.
         */
        fun decide(choice: SpeechModelId?, installed: Set<SpeechModelId>): ModelStartup {
            if (choice != null) return Use(choice)
            val existing = SpeechModelId.ranked.firstOrNull { it in installed } ?: return ShowPicker
            return Use(existing)
        }
    }
}
