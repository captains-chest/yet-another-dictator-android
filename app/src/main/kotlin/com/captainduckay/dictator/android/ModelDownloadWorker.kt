package com.captainduckay.dictator.android

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.captainduckay.dictator.core.ModelManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Downloads the model listed in the bundled manifest (ADR 0006). The only network code in the app.
 *
 * - Runs only after the user taps Download. Wi-Fi (unmetered) only unless they allowed mobile data.
 * - Resumes with an HTTP Range request from the `.part` file, so retries and restarts continue.
 * - Every file is checked against its SHA-256 before it is moved into place; a mismatch deletes it.
 * - Retries with exponential backoff; WorkManager restarts it when the network returns.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val context = applicationContext
            try {
                SpeechModel.removeStale(context)
                if (SpeechModel.isInstalled(context)) return@withContext Result.success()
                val files = SpeechModel.manifest(context).files(SpeechModel.MODEL_ID)
                val total = files.sumOf { it.bytes }
                var done = 0L
                val folder = SpeechModel.folder(context).apply { mkdirs() }
                for (entry in files) {
                    val target = File(folder, entry.localPath)
                    if (!(target.isFile && target.length() == entry.bytes)) {
                        download(entry, target) { written -> setProgress(progress(done + written, total)) }
                    }
                    done += entry.bytes
                    setProgress(progress(done, total))
                }
                SpeechModel.markComplete(context)
                Result.success()
            } catch (e: ChecksumMismatch) {
                if (runAttemptCount <
                    MAX_ATTEMPTS
                ) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf(KEY_ERROR to e.message))
                }
            } catch (e: IOException) {
                if (runAttemptCount <
                    MAX_ATTEMPTS
                ) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf(KEY_ERROR to e.message))
                }
            }
        }

    private suspend fun download(entry: ModelManifest.Entry, target: File, onProgress: suspend (Long) -> Unit) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        if (part.length() > entry.bytes) part.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        // Re-hash what we already have, so a resumed file is verified end to end.
        if (part.exists()) part.inputStream().use { input -> hash(input, digest) }
        var offset = part.length()
        if (offset < entry.bytes) {
            val connection = URL(entry.url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_PARTIAL -> Unit
                    HttpURLConnection.HTTP_OK -> {
                        // The server ignored the range: start over.
                        offset = 0
                        digest.reset()
                    }
                    else -> throw IOException("HTTP ${connection.responseCode} for ${entry.localPath}")
                }
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(offset)
                    out.seek(offset)
                    connection.inputStream.use { input ->
                        var written = offset
                        var lastReport = 0L
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            if (written > entry.bytes) throw ChecksumMismatch(entry.localPath)
                            if (written - lastReport >= REPORT_EVERY) {
                                lastReport = written
                                onProgress(written)
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (part.length() != entry.bytes || actual != entry.sha256) {
            part.delete()
            throw ChecksumMismatch(entry.localPath)
        }
        if (!part.renameTo(target)) throw IOException("Could not move ${entry.localPath} into place")
    }

    private fun hash(input: InputStream, digest: MessageDigest) {
        val buffer = ByteArray(BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            digest.update(buffer, 0, read)
        }
    }

    private class ChecksumMismatch(path: String) : IOException("SHA-256 mismatch for $path")

    companion object {
        private const val WORK_NAME = "model-download"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val MAX_ATTEMPTS = 8
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER = 64 * 1024
        private const val REPORT_EVERY = 1L shl 20

        private fun progress(done: Long, total: Long) = workDataOf(KEY_DONE to done, KEY_TOTAL to total)

        /** Starts (or keeps) the download. Wi-Fi only unless [allowMobileData]. */
        fun start(context: Context, allowMobileData: Boolean) {
            val request =
                OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(
                                if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED,
                            ).setRequiresStorageNotLow(true)
                            .build(),
                    ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

        fun observe(context: Context): Flow<List<WorkInfo>> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME)
    }
}
