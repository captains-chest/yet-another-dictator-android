package com.captainduckay.dictator.android

import android.content.Context
import com.captainduckay.dictator.core.ModelChoice
import com.captainduckay.dictator.core.ModelManifest
import com.captainduckay.dictator.core.SpeechModelId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

/**
 * The speech models on disk (ADR 0006, ADR 0008). Only the model the user chose is downloaded, after
 * they opt in. The file list and SHA-256 hashes come from `model-manifest.txt` inside the signed APK,
 * never from the network. Files live in app-private storage (`filesDir/models/<model-id>/`), excluded
 * from backup.
 */
object SpeechModel {
    private const val MANIFEST_ASSET = "model/model-manifest.txt"
    private const val COMPLETE_MARKER = ".complete"

    fun manifest(context: Context): ModelManifest =
        ModelManifest.parse(
            context.assets
                .open(MANIFEST_ASSET)
                .bufferedReader()
                .use { it.readText() },
        )

    fun choice(context: Context) = ModelChoice(Settings(context))

    /** The chosen model, or null until the user picks one. */
    fun chosen(context: Context): SpeechModelId? = choice(context).selected

    fun displayName(model: SpeechModelId): String =
        when (model) {
            SpeechModelId.PARAKEET_V2 -> "NVIDIA Parakeet TDT 0.6B v2"
            SpeechModelId.PARAKEET_110M -> "NVIDIA Parakeet TDT-CTC 110M"
        }

    fun downloadBytes(context: Context, model: SpeechModelId): Long = manifest(context).totalBytes(model.id)

    fun folder(context: Context, model: SpeechModelId): File = File(context.filesDir, "models/${model.id}")

    /** True once every file was downloaded and verified; the worker writes the marker last. */
    fun isInstalled(context: Context, model: SpeechModelId): Boolean {
        val folder = folder(context, model)
        return File(folder, COMPLETE_MARKER).isFile &&
            manifest(context).files(model.id).all { File(folder, it.localPath).isFile }
    }

    fun installed(context: Context): Set<SpeechModelId> =
        SpeechModelId.entries
            .filter {
                isInstalled(
                    context,
                    it,
                )
            }.toSet()

    internal fun markComplete(context: Context, model: SpeechModelId) =
        File(folder(context, model), COMPLETE_MARKER).writeText(model.id)

    /** Frees the storage of a model that isn't in use. */
    fun delete(context: Context, model: SpeechModelId) {
        if (model == chosen(context)) return
        folder(context, model).deleteRecursively()
        mutableChanges.tryEmit(Unit)
    }

    /** Deletes models that are no longer in the manifest, e.g. after a model bump. */
    fun removeStale(context: Context) {
        val keep = manifest(context).modelIds.toSet()
        File(context.filesDir, "models").listFiles()?.filter { it.name !in keep }?.forEach { it.deleteRecursively() }
    }

    /**
     * Deletes the folders of retired models only ([SpeechModelId.retiredIds], i.e. the third model
     * offered up to 0.4.0), which no engine in this version can read (ADR 0010). Nothing else is
     * touched. Blocking file I/O: call it off the main thread.
     */
    fun removeRetired(context: Context) {
        val models = File(context.filesDir, "models")
        SpeechModelId.retiredIds
            .map { File(models, it) }
            .filter { it.exists() }
            .forEach { it.deleteRecursively() }
    }

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** Emits when the choice changes or a model finishes downloading, so dictation reloads it. */
    val changes: SharedFlow<Unit> = mutableChanges

    fun choose(context: Context, model: SpeechModelId) {
        choice(context).choose(model)
        mutableChanges.tryEmit(Unit)
    }

    internal fun notifyInstalled() {
        mutableChanges.tryEmit(Unit)
    }
}
