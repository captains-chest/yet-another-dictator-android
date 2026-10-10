package com.captainduckay.dictator.core

/** Normalises raw Whisper output into text that is safe to type into another app. */
object TranscriptCleaner {
    private val annotations = Regex("""(?i)\[(?:blank_audio|music|noise|silence|inaudible)]|<\|[^|]*\|>""")
    private val repeatedSpaces = Regex(" {2,}")

    /**
     * Output is a single line: newlines and tabs become spaces so dictation can never submit a form
     * or move focus, and control characters are dropped.
     */
    fun clean(raw: String): String {
        val withoutAnnotations = raw.replace(annotations, "")
        val builder = StringBuilder(withoutAnnotations.length)
        var index = 0
        while (index < withoutAnnotations.length) {
            val codePoint = withoutAnnotations.codePointAt(index)
            when {
                isWhitespaceOrNewline(codePoint) -> builder.append(' ')
                Character.getType(codePoint) != Character.CONTROL.toInt() -> builder.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        return builder.toString().replace(repeatedSpaces, " ").trim(' ')
    }

    // Swift's CharacterSet.whitespacesAndNewlines: Unicode Zs, tab, and line separators.
    private fun isWhitespaceOrNewline(codePoint: Int): Boolean =
        codePoint in 0x09..0x0D || codePoint == 0x85 || Character.isSpaceChar(codePoint)
}
