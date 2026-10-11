package com.captainduckay.dictator.android

import android.content.Context
import com.captainduckay.dictator.core.ModelManifest
import java.io.File

/**
 * The Whisper model, downloaded once after install with the user's consent (ADR 0006). The file list
 * and SHA-256 hashes come from `model-manifest.txt` inside the signed APK, never from the network.
 * Downloaded files live in app-private storage (`filesDir/models/<model-id>/`), excluded from backup.
 */
object SpeechModel {
    const val MODEL_ID = "ggml-small-q5_1"
    const val FILE_NAME = "ggml-small-q5_1.bin"
    const val DISPLAY_NAME = "Whisper Small (q5_1)"
    private const val MANIFEST_ASSET = "model/model-manifest.txt"
    private const val COMPLETE_MARKER = ".complete"

    fun manifest(context: Context): ModelManifest =
        ModelManifest.parse(
            context.assets
                .open(MANIFEST_ASSET)
                .bufferedReader()
                .use { it.readText() },
        )

    fun downloadBytes(context: Context): Long = manifest(context).totalBytes(MODEL_ID)

    fun folder(context: Context): File = File(context.filesDir, "models/$MODEL_ID")

    fun file(context: Context): File = File(folder(context), FILE_NAME)

    /** True once every file was downloaded and verified; the worker writes the marker last. */
    fun isInstalled(context: Context): Boolean =
        File(folder(context), COMPLETE_MARKER).isFile && file(context).isFile

    internal fun markComplete(context: Context) = File(folder(context), COMPLETE_MARKER).writeText(MODEL_ID)

    /** Deletes models that are no longer in the manifest, e.g. after a model bump. */
    fun removeStale(context: Context) {
        val keep = manifest(context).modelIds.toSet()
        File(context.filesDir, "models").listFiles()?.filter { it.name !in keep }?.forEach { it.deleteRecursively() }
    }
}
