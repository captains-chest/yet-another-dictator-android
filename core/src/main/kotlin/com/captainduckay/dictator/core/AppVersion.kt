package com.captainduckay.dictator.core

/**
 * A semantic version like `0.1.0` or `v0.2.0-beta.1`, compared by semver rules (a pre-release sorts
 * before its release; build metadata is ignored). Port of DictationCore's `AppVersion`.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int, val prerelease: List<String> = emptyList()) :
    Comparable<AppVersion> {
    val isPrerelease: Boolean get() = prerelease.isNotEmpty()

    override fun toString(): String {
        val core = "$major.$minor.$patch"
        return if (prerelease.isEmpty()) core else core + "-" + prerelease.joinToString(".")
    }

    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }).let { if (it != 0) return it }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return when {
                prerelease.isEmpty() && other.prerelease.isEmpty() -> 0
                prerelease.isEmpty() -> 1
                else -> -1
            }
        }
        for ((a, b) in prerelease.zip(other.prerelease)) {
            if (a == b) continue
            val x = a.toNumericOrNull()
            val y = b.toNumericOrNull()
            return when {
                x != null && y != null -> x.compareTo(y)
                x != null -> -1 // numeric identifiers sort first
                y != null -> 1
                else -> a.compareTo(b)
            }
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        fun parse(string: String): AppVersion? {
            var text = string.trim()
            if (text.startsWith("v") || text.startsWith("V")) text = text.drop(1)
            text.indexOf('+').takeIf { it >= 0 }?.let { text = text.substring(0, it) }
            var pre = emptyList<String>()
            val dash = text.indexOf('-')
            if (dash >= 0) {
                pre = text.substring(dash + 1).split(".")
                if (pre.isEmpty() || pre.any { it.isEmpty() }) return null
                text = text.substring(0, dash)
            }
            val numbers = text.split(".")
            if (numbers.size !in 1..3) return null
            val parts = numbers.map { number -> number.toNumericOrNull() ?: return null }.toMutableList()
            while (parts.size < 3) parts += 0
            return AppVersion(parts[0], parts[1], parts[2], pre)
        }

        private fun String.toNumericOrNull(): Int? = if (isNotEmpty() && all { it in '0'..'9' }) toIntOrNull() else null
    }
}
