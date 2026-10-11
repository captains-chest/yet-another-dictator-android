package com.captainduckay.dictator.core

/**
 * The pinned list of model files the app may download (ADR 0006). It ships inside the signed app and
 * is the only trust anchor: it is never fetched from the network.
 *
 * Line format: `<model-id>  <sha256>  <bytes>  <huggingface-path>  <local-path>`; `#` starts a comment.
 */
class ModelManifest private constructor(val entries: List<Entry>) {
    data class Entry(
        val modelId: String,
        val sha256: String,
        val bytes: Long,
        val remotePath: String,
        val localPath: String,
    ) {
        val url: String get() = "https://huggingface.co/$remotePath"
    }

    fun files(modelId: String): List<Entry> = entries.filter { it.modelId == modelId }

    fun totalBytes(modelId: String): Long = files(modelId).sumOf { it.bytes }

    val modelIds: List<String> get() = entries.map { it.modelId }.distinct()

    companion object {
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val SAFE_PATH = Regex("[A-Za-z0-9._/-]+")

        fun parse(text: String): ModelManifest {
            val entries =
                text
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { line ->
                        val parts = line.split(Regex("\\s+"))
                        require(parts.size == 5) { "Malformed manifest line: $line" }
                        val (id, sha, bytes, remote, local) = parts
                        require(SHA256.matches(sha)) { "Bad SHA-256 in manifest: $sha" }
                        require(isSafe(local) && isSafe(remote)) { "Unsafe path in manifest: $line" }
                        Entry(id, sha, bytes.toLong().also { require(it > 0) }, remote, local)
                    }.toList()
            require(
                entries.distinctBy { it.modelId to it.localPath }.size == entries.size,
            ) { "Duplicate manifest entry" }
            return ModelManifest(entries)
        }

        private fun isSafe(path: String) =
            SAFE_PATH.matches(path) && !path.startsWith("/") && path.split("/").none { it == ".." || it.isEmpty() }
    }
}
