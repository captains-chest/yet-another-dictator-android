package com.captainduckay.dictator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

// Ported from DictationCore's PureLogicTests.swift. Mac-only suites (Shortcut, KeyLabels, Chord)
// are not ported because Android has no global shortcut.

class TranscriptCleanerTest {
    @Test fun cleans() {
        val cases =
            listOf(
                "  Hello world. " to "Hello world.",
                "[BLANK_AUDIO]" to "",
                "Hi [Music] there" to "Hi there",
                "<|en|><|transcribe|> Hej med dig" to "Hej med dig",
                "line one\nline two\tend" to "line one line two end",
                "bell\u0007escape\u001B" to "bellescape",
                "👩‍💻 works" to "👩‍💻 works",
            )
        for ((raw, expected) in cases) assertEquals(raw, expected, TranscriptCleaner.clean(raw))
    }
}

class TextChunkerTest {
    @Test fun splitsAtLimit() {
        assertEquals(listOf(20, 20, 5), TextChunker.chunks("a".repeat(45)).map { it.length })
    }

    @Test fun neverSplitsGraphemeClusters() {
        val family = "👩‍👩‍👧‍👦" // 11 UTF-16 units
        val text = family.repeat(5)
        val chunks = TextChunker.chunks(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= TextChunker.MAX_UTF16_PER_CHUNK })
        assertTrue(chunks.all { it.graphemes().all { g -> g == family } })
    }

    @Test fun emptyTextHasNoChunks() {
        assertTrue(TextChunker.chunks("").isEmpty())
    }
}

class SpeechActivityTest {
    private fun tone(amplitude: Float, seconds: Double) =
        FloatArray((seconds * 16_000).toInt()) { amplitude * sin(it * 2 * PI.toFloat() * 220 / 16_000) }

    private fun silence(seconds: Double, noise: Float = 0f): FloatArray {
        var seed = 1u
        return FloatArray((seconds * 16_000).toInt()) {
            seed = seed * 1_664_525u + 1_013_904_223u
            noise * ((seed shr 8).toFloat() / (1 shl 24).toFloat() * 2 - 1)
        }
    }

    @Test fun digitalSilenceHasNoSpeech() = assertFalse(SpeechActivity.containsSpeech(silence(2.0)))

    @Test fun quietRoomNoiseHasNoSpeech() = assertFalse(SpeechActivity.containsSpeech(silence(3.0, 0.003f)))

    @Test fun aTapIsNotSpeech() {
        val click = silence(1.0, 0.002f) + tone(0.5f, 0.05) + silence(0.25, 0.002f)
        assertFalse(SpeechActivity.containsSpeech(click))
    }

    @Test fun normalSpeechLevelCounts() = assertTrue(SpeechActivity.containsSpeech(silence(0.5) + tone(0.05f, 0.4)))

    @Test fun quietSpeechLevelCounts() = assertTrue(SpeechActivity.containsSpeech(tone(0.012f, 0.15)))

    @Test fun tooShortForAFrameHasNoSpeech() = assertFalse(SpeechActivity.containsSpeech(tone(0.5f, 0.01)))
}

class DictationPromptTest {
    @Test fun promptIsPunctuatedInBothLanguages() {
        val prompt = DictationPrompt.TEXT
        assertTrue(prompt.contains("?") && prompt.contains("."))
        assertTrue(prompt.contains("tak") && prompt.contains("thanks"))
        assertTrue(prompt.endsWith("."))
    }

    @Test fun detectsPromptEcho() {
        listOf(
            DictationPrompt.TEXT,
            "Hej, hvordan går det? Det går godt, tak. Hello, how are you?",
            "hello how are you i'm fine thanks",
            " Det går godt, tak. Hello, how are you? I'm fine, thanks. ",
        ).forEach { assertTrue(it, DictationPrompt.isEcho(it)) }
    }

    @Test fun keepsRealDictation() {
        listOf(
            "",
            "Tak.",
            "Hello, how are you?",
            "Det går godt, tak.",
            "Hello, how are you? I'm fine, thanks. And you?",
            "Please send the report by Friday.",
            "Hej, hvordan går det med projektet i dag? Det går godt nu.",
        ).forEach { assertFalse(it, DictationPrompt.isEcho(it)) }
    }
}

class DictationLanguageTest {
    @Test fun mapsToWhisperCodes() {
        assertNull(DictationLanguage.AUTOMATIC.whisperCode)
        assertEquals("en", DictationLanguage.ENGLISH.whisperCode)
    }

    @Test fun unknownStoredValueFallsBackToAutomatic() {
        assertEquals(DictationLanguage.AUTOMATIC, DictationLanguage.fromStored(null))
        assertEquals(DictationLanguage.AUTOMATIC, DictationLanguage.fromStored("klingon"))
        assertEquals(DictationLanguage.AUTOMATIC, DictationLanguage.fromStored("danish"))
        assertEquals(DictationLanguage.ENGLISH, DictationLanguage.fromStored("english"))
    }
}

class SmartJoinTest {
    private fun j(text: String, after: String?) = SmartJoin.adjust(text, after)

    @Test fun unknownContextLeavesTextAlone() = assertEquals("Hello there.", j("Hello there.", null))

    @Test fun emptyFieldCapitalizesWithoutSpace() = assertEquals("Hello there.", j("hello there.", ""))

    @Test fun newSentenceAfterFullStopGetsSpaceAndCapital() {
        assertEquals(" And then.", j("and then.", "It works."))
        assertEquals(" Really?", j("Really?", "Done!"))
        assertEquals(" Yes.", j("Yes.", "He said \"no.\""))
    }

    @Test fun midSentenceLowercasesSentenceCapital() {
        assertEquals(" how are you?", j("How are you?", "Hello,"))
        assertEquals(" og så videre.", j("Og så videre.", "Vi tog toget"))
    }

    @Test fun midSentenceKeepsIntentionalCapitals() {
        assertEquals(" I think so.", j("I think so.", "Well,"))
        assertEquals(" I'm here.", j("I'm here.", "Well,"))
        assertEquals(" NASA said so.", j("NASA said so.", "and"))
        assertEquals(" iPhone works.", j("iPhone works.", "my"))
    }

    @Test fun noSpaceAfterExistingWhitespaceOrOpener() {
        assertEquals("Next one.", j("next one.", "First one. "))
        assertEquals("like this", j("Like this", "("))
    }

    @Test fun newLineStartsASentence() {
        assertEquals("Second line.", j("second line.", "First line.\n"))
        assertEquals("Second line.", j("second line.", "First line\n"))
    }

    @Test fun punctuationAttachesToPreviousWord() {
        assertEquals(", and more.", j(", and more.", "This"))
        assertEquals("?", j("?", "Really"))
    }

    @Test fun spacesBeforeCursorStillSeeTheSentenceEnd() {
        assertEquals("Next.", j("next.", "Done.  "))
        assertEquals("next.", j("Next.", "and  "))
    }

    @Test fun straightQuoteOpensAfterSpace() = assertEquals("hello", j("Hello", "She said \""))
}

class DictationHistoryTest {
    @Test fun undoCountsVisibleCharacters() {
        val history = DictationHistory()
        history.didInsert(" Hej 👋🏽 café")
        assertEquals(11, history.takeUndo())
        assertNull(history.takeUndo())
    }

    @Test fun otherInputPreventsUndo() {
        val history = DictationHistory()
        history.didInsert("Hello.")
        history.otherInput()
        assertNull(history.lastInserted)
        assertNull(history.takeUndo())
    }
}

class AudioRingBufferTest {
    @Test fun keepsEverythingUntilFull() {
        val ring = AudioRingBuffer(4)
        ring.append(floatArrayOf(1f, 2f, 3f))
        assertEquals(listOf(1f, 2f, 3f), ring.samples.toList())
    }

    @Test fun keepsOnlyTheNewestSamples() {
        val ring = AudioRingBuffer(4)
        ring.append(floatArrayOf(1f, 2f, 3f))
        ring.append(floatArrayOf(4f, 5f, 6f))
        assertEquals(listOf(3f, 4f, 5f, 6f), ring.samples.toList())
        ring.append(floatArrayOf(7f, 8f, 9f, 10f, 11f))
        assertEquals(listOf(8f, 9f, 10f, 11f), ring.samples.toList())
    }

    @Test fun removeAllEmptiesIt() {
        val ring = AudioRingBuffer(2)
        ring.append(floatArrayOf(1f, 2f, 3f))
        ring.removeAll()
        assertTrue(ring.samples.isEmpty())
        ring.append(floatArrayOf(4f))
        assertEquals(listOf(4f), ring.samples.toList())
    }

    @Test fun zeroCapacityHoldsNothing() {
        val ring = AudioRingBuffer(0)
        ring.append(floatArrayOf(1f, 2f))
        assertTrue(ring.samples.isEmpty())
    }
}
