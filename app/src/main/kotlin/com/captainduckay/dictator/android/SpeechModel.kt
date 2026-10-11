package com.captainduckay.dictator.android

import android.content.Context
import java.io.FileNotFoundException

/**
 * The Whisper model bundled in the APK by `scripts/fetch-model.sh` (ADR 0004). It is read in place
 * from the APK; nothing is downloaded or copied.
 */
object SpeechModel {
    const val ASSET_PATH = "model/ggml-small-q5_1.bin"
    const val DISPLAY_NAME = "Whisper Small (q5_1)"

    fun isBundled(context: Context): Boolean =
        try {
            context.assets.openFd(ASSET_PATH).use { it.length > 0 }
        } catch (_: FileNotFoundException) {
            false
        }
}
