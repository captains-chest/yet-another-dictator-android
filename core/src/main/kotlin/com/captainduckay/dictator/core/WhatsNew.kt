package com.captainduckay.dictator.core

/** One version's section of CHANGELOG.md. */
data class ChangelogEntry(val version: AppVersion, val note: String, val body: String)

object Changelog {
    /** Parses `## [0.1.0] - 2026-10-09` / `## 0.1.0` sections; non-version headings are skipped. */
    fun parse(markdown: String): List<ChangelogEntry> {
        val entries = mutableListOf<ChangelogEntry>()
        var current: Pair<AppVersion, String>? = null
        val lines = mutableListOf<String>()

        fun flush() {
            current?.let { (version, note) ->
                entries += ChangelogEntry(version, note, lines.joinToString("\n").trim())
            }
            lines.clear()
        }

        for (line in markdown.split("\n")) {
            if (line.startsWith("## ")) {
                flush()
                current = heading(line.drop(3))
            } else if (current != null) {
                lines += line
            }
        }
        flush()
        return entries
    }

    private fun heading(text: String): Pair<AppVersion, String>? {
        val trimmed = text.trim(' ', '\t')
        val token = trimmed.takeWhile { !it.isWhitespace() }
        val version = AppVersion.parse(token.trim('[', ']')) ?: return null
        val note = trimmed.drop(token.length).trim(' ', '-', '–', '—')
        return version to note
    }
}

/** Decides whether to show What's New at launch. */
object WhatsNew {
    sealed interface Decision {
        data object None : Decision

        data class Welcome(val entries: List<ChangelogEntry>) : Decision

        data class Updated(val entries: List<ChangelogEntry>) : Decision
    }

    fun decide(current: AppVersion, lastSeen: String?, changelog: List<ChangelogEntry>): Decision {
        if (lastSeen == null) return Decision.Welcome(changelog.filter { it.version == current })
        val seen = AppVersion.parse(lastSeen) ?: return Decision.Updated(changelog.filter { it.version == current })
        if (seen >= current) return Decision.None
        return Decision.Updated(changelog.filter { it.version > seen && it.version <= current }.sortedDescending())
    }

    private fun List<ChangelogEntry>.sortedDescending() = sortedByDescending { it.version }
}
