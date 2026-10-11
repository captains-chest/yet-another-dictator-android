package com.captainduckay.dictator.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Ported from DictationCore's DictationControllerTests.swift. Virtual time replaces the Swift
// suite's real sleeps, so these run instantly and deterministically.

class FakeRecorder : AudioRecording {
    var samplesToReturn = FloatArray(16_000) { 0.1f }
    var startError: Exception? = null
    var isRecording = false
        private set
    var startCount = 0
        private set

    override fun start(onLevel: (Float) -> Unit) {
        startError?.let { throw it }
        isRecording = true
        startCount += 1
    }

    override fun stop(): FloatArray {
        isRecording = false
        return samplesToReturn
    }
}

class FakeTranscriber : Transcribing {
    var result = "hello world"
    var prepareError: Exception? = null
    val received = mutableListOf<FloatArray>()
    val languages = mutableListOf<String?>()
    var delay: Duration? = null
    var cooperative = true
    var sawCancellation = false

    override suspend fun prepare() {
        prepareError?.let { throw it }
    }

    override suspend fun transcribe(samples: FloatArray, language: String?): String {
        received += samples
        languages += language
        delay?.let { pause ->
            if (cooperative) {
                try {
                    delay(pause)
                } catch (error: CancellationException) {
                    sawCancellation = true
                    throw error
                }
            } else {
                withContext(NonCancellable) { delay(pause) }
            }
        }
        return result
    }
}

class FakeInserter : TextInserting {
    val inserted = mutableListOf<String>()
    val posted = mutableListOf<String>()
    val deleted = mutableListOf<Int>()
    var preflightError: Exception? = null
    var insertError: Exception? = null
    var delayPerCharacter: Duration? = null

    override fun preflight() {
        preflightError?.let { throw it }
    }

    override suspend fun insert(text: String) {
        insertError?.let { throw it }
        val pause = delayPerCharacter
        if (pause != null) {
            for (character in text.graphemes()) {
                currentCoroutineContext().ensureActive()
                posted += character
                delay(pause)
            }
        } else {
            posted += text.graphemes()
        }
        inserted += text
    }

    override suspend fun deleteBackward(count: Int) {
        insertError?.let { throw it }
        deleted += count
    }
}

/** Returns [text] as the field's text before the cursor; null means it can't be read. */
class FakePrecedingTextReader : PrecedingTextReading {
    var text: String? = null
    val requestedLengths = mutableListOf<Int>()

    override fun textBeforeCursor(maxLength: Int): String? {
        requestedLengths += maxLength
        return text
    }
}

class Boom : Exception("Boom.")

@OptIn(ExperimentalCoroutinesApi::class)
class DictationControllerTest {
    private val recorder = FakeRecorder()
    private val transcriber = FakeTranscriber()
    private val inserter = FakeInserter()
    private val precedingText = FakePrecedingTextReader()
    private var clock = 100.0

    private suspend fun TestScope.makeController(
        timeout: Duration = 30.seconds,
        stopTail: Duration = Duration.ZERO,
    ): DictationController {
        val controller =
            DictationController(
                scope = this,
                recorder = recorder,
                transcriber = transcriber,
                inserter = inserter,
                precedingText = precedingText,
                now = { clock },
                stopTail = stopTail,
                transcriptionTimeout = { timeout },
            )
        controller.loadModel()
        return controller
    }

    private fun TestScope.dictate(controller: DictationController, text: String) {
        transcriber.result = text
        controller.pressed()
        controller.pressed()
        advanceUntilIdle()
    }

    private val ready = DictationState.Ready

    @Test fun loadsModelThenBecomesReady() =
        runTest {
            assertEquals(ready, makeController().state.value)
        }

    @Test fun failedModelLoadDisablesDictation() =
        runTest {
            transcriber.prepareError = Boom()
            val controller = makeController()
            assertTrue(controller.state.value is DictationState.Unavailable)
            controller.pressed()
            assertFalse(recorder.isRecording)
        }

    @Test fun tapTogglesRecordingAndInsertsText() =
        runTest {
            val controller = makeController()
            controller.pressed()
            clock += 0.1
            controller.released()
            assertEquals(DictationState.Recording, controller.state.value)
            controller.pressed()
            controller.released()
            assertEquals(DictationState.Transcribing, controller.state.value)
            advanceUntilIdle()
            assertEquals(listOf("hello world"), inserter.inserted)
            assertEquals(1, transcriber.received.size)
        }

    @Test fun holdIsPushToTalk() =
        runTest {
            val controller = makeController()
            controller.pressed()
            clock += 1
            controller.released()
            assertEquals(DictationState.Transcribing, controller.state.value)
            advanceUntilIdle()
            assertEquals(listOf("hello world"), inserter.inserted)
        }

    @Test fun tooShortRecordingIsDiscarded() =
        runTest {
            val controller = makeController()
            recorder.samplesToReturn = FloatArray(100)
            controller.pressed()
            controller.pressed()
            advanceUntilIdle()
            assertEquals(ready, controller.state.value)
            assertFalse(recorder.isRecording)
            assertTrue(transcriber.received.isEmpty())
        }

    @Test fun cancelDiscardsAudio() =
        runTest {
            val controller = makeController()
            controller.pressed()
            controller.cancel()
            advanceUntilIdle()
            assertEquals(ready, controller.state.value)
            assertTrue(transcriber.received.isEmpty())
            assertTrue(inserter.inserted.isEmpty())
        }

    @Test fun keepsRecordingForTheTailAfterStop() =
        runTest {
            val controller = makeController(stopTail = 50.milliseconds)
            controller.pressed()
            controller.pressed()
            runCurrent()
            assertEquals(DictationState.Transcribing, controller.state.value)
            assertTrue("the microphone must stay open during the tail", recorder.isRecording)
            advanceUntilIdle()
            assertFalse(recorder.isRecording)
            assertEquals(listOf("hello world"), inserter.inserted)
        }

    @Test fun cancelDuringTranscriptionTypesNothing() =
        runTest {
            val controller = makeController()
            transcriber.delay = 150.milliseconds
            transcriber.cooperative = false
            controller.pressed()
            controller.pressed()
            runCurrent()
            controller.cancel()
            assertEquals(ready, controller.state.value)
            advanceUntilIdle()
            assertTrue(inserter.inserted.isEmpty())
            assertNull(controller.lastError.value)
            assertEquals(ready, controller.state.value)
        }

    @Test fun cancelStopsACooperativeTranscriber() =
        runTest {
            val controller = makeController()
            transcriber.delay = 60.seconds
            controller.pressed()
            controller.pressed()
            runCurrent()
            assertEquals(1, transcriber.received.size)
            controller.cancel()
            runCurrent()
            assertTrue(transcriber.sawCancellation)
        }

    @Test fun stuckTranscriptionTimesOut() =
        runTest {
            val controller = makeController(timeout = 50.milliseconds)
            transcriber.delay = 60.seconds
            controller.pressed()
            controller.pressed()
            advanceTimeBy(100.milliseconds)
            runCurrent()
            assertEquals(ready, controller.state.value)
            assertTrue(controller.lastError.value!!.contains("too long"))
            assertTrue(inserter.inserted.isEmpty())
        }

    @Test fun dictationWorksAgainAfterCancel() =
        runTest {
            val controller = makeController()
            transcriber.delay = 100.milliseconds
            transcriber.cooperative = false
            controller.pressed()
            controller.pressed()
            runCurrent()
            controller.cancel()
            transcriber.delay = null
            controller.pressed()
            controller.pressed()
            advanceUntilIdle()
            assertEquals(listOf("hello world"), inserter.inserted)
        }

    @Test fun defaultTimeoutGrowsWithAudioLength() {
        assertEquals(30.seconds, DictationController.defaultTranscriptionTimeout(0))
        assertEquals(90.seconds, DictationController.defaultTranscriptionTimeout(16_000 * 60))
    }

    @Test fun silentRecordingIsNotTranscribed() =
        runTest {
            val controller = makeController()
            recorder.samplesToReturn = FloatArray(32_000)
            controller.pressed()
            controller.pressed()
            advanceUntilIdle()
            assertTrue(transcriber.received.isEmpty())
            assertTrue(inserter.inserted.isEmpty())
        }

    @Test fun microphoneFailureDuringRecordingReturnsToReadyAndReports() =
        runTest {
            val controller = makeController()
            controller.pressed()
            controller.recordingFailed("The microphone was disconnected.")
            assertEquals(ready, controller.state.value)
            assertFalse(recorder.isRecording)
            assertEquals("The microphone was disconnected.", controller.lastError.value)
            assertTrue(transcriber.received.isEmpty())
        }

    @Test fun microphoneFailureOutsideRecordingIsIgnored() =
        runTest {
            val controller = makeController()
            controller.recordingFailed("The microphone was disconnected.")
            assertNull(controller.lastError.value)
        }

    @Test fun passesTheChosenLanguageToTheTranscriber() =
        runTest {
            val controller = makeController()
            controller.language = DictationLanguage.DANISH
            dictate(controller, "hej")
            controller.language = DictationLanguage.AUTOMATIC
            dictate(controller, "hello")
            assertEquals(listOf("da", null), transcriber.languages)
        }

    @Test fun consecutiveDictationsAreSpacedAndCapitalized() =
        runTest {
            val controller = makeController()
            dictate(controller, "Hello world.")
            dictate(controller, "and more")
            dictate(controller, "So that's it.")
            assertEquals(listOf("Hello world.", " And more", " so that's it."), inserter.inserted)
        }

    @Test fun otherInputMakesTheContextUnknown() =
        runTest {
            val controller = makeController()
            dictate(controller, "Hello world.")
            controller.noteOtherInput()
            dictate(controller, "and more.")
            assertEquals(listOf("Hello world.", "and more."), inserter.inserted)
        }

    @Test fun readableFieldTextWinsOverHistory() =
        runTest {
            val controller = makeController()
            dictate(controller, "Hello world.")
            precedingText.text = "Dear Sir,"
            dictate(controller, "Thanks for writing.")
            assertEquals(" thanks for writing.", inserter.inserted.last())
        }

    @Test fun emptyFieldReadingDoesNotOverrideHistory() =
        runTest {
            val controller = makeController()
            precedingText.text = ""
            dictate(controller, "hello world.")
            dictate(controller, "and more.")
            assertEquals(listOf("Hello world.", " And more."), inserter.inserted)
        }

    @Test fun unreadableFieldFallsBackToHistory() =
        runTest {
            val controller = makeController()
            precedingText.text = null
            dictate(controller, "Hello world.")
            dictate(controller, "and more")
            assertEquals(listOf("Hello world.", " And more"), inserter.inserted)
            assertEquals(listOf(64, 64), precedingText.requestedLengths)
        }

    @Test fun unreadableFieldWithoutHistoryTypesAsTranscribed() =
        runTest {
            val controller = makeController()
            precedingText.text = null
            dictate(controller, "hello world")
            assertEquals(listOf("hello world"), inserter.inserted)
        }

    @Test fun reportsTimingFromReleaseToTypedText() =
        runTest {
            val controller = makeController()
            val timings = mutableListOf<DictationTiming>()
            controller.onTiming = { timings += it }
            controller.pressed()
            clock += 2
            controller.released()
            clock += 0.5
            advanceUntilIdle()
            assertEquals(1, timings.size)
            assertEquals(1.0, timings[0].audioSeconds, 0.0)
            assertEquals(0.5, timings[0].releaseToTypedSeconds, 1e-9)
        }

    @Test fun noTimingWhenNothingIsTyped() =
        runTest {
            val controller = makeController()
            val timings = mutableListOf<DictationTiming>()
            controller.onTiming = { timings += it }
            dictate(controller, "[BLANK_AUDIO]")
            assertTrue(timings.isEmpty())
        }

    @Test fun undoDeletesExactlyWhatWasTyped() =
        runTest {
            val controller = makeController()
            dictate(controller, "Hello world.")
            dictate(controller, "and more 👋🏽.")
            assertTrue(controller.canUndo)
            controller.undoLastDictation()
            assertEquals(listOf(12), inserter.deleted)
            assertFalse(controller.canUndo)
            controller.undoLastDictation()
            assertEquals(1, inserter.deleted.size)
        }

    @Test fun undoIsUnavailableAfterOtherInput() =
        runTest {
            val controller = makeController()
            dictate(controller, "Hello world.")
            controller.noteOtherInput()
            assertFalse(controller.canUndo)
            controller.undoLastDictation()
            assertTrue(inserter.deleted.isEmpty())
        }

    @Test fun undoIsUnavailableBeforeAnyDictationAndWhileRecording() =
        runTest {
            val controller = makeController()
            assertFalse(controller.canUndo)
            dictate(controller, "Hello.")
            controller.pressed()
            assertFalse(controller.canUndo)
            controller.undoLastDictation()
            assertTrue(inserter.deleted.isEmpty())
        }

    @Test fun reachingTheLimitStillTranscribesAndTypes() =
        runTest {
            val controller = makeController()
            controller.pressed()
            controller.recordingReachedLimit()
            assertEquals(DictationState.Transcribing, controller.state.value)
            advanceUntilIdle()
            assertEquals(listOf("hello world"), inserter.inserted)
            assertTrue(controller.lastError.value!!.contains("10-minute"))
        }

    @Test fun reachingTheLimitIsANoOpWhenReady() =
        runTest {
            val controller = makeController()
            controller.recordingReachedLimit()
            assertEquals(ready, controller.state.value)
            assertNull(controller.lastError.value)
        }

    @Test fun cancelWhileTypingStopsPostingAndEndsReady() =
        runTest {
            val controller = makeController()
            transcriber.result = "This is a long dictation that takes a while to type."
            inserter.delayPerCharacter = 5.milliseconds
            controller.pressed()
            controller.pressed()
            advanceTimeBy(20.milliseconds)
            assertTrue(inserter.posted.isNotEmpty())
            assertEquals(DictationState.Transcribing, controller.state.value)
            controller.cancel()
            assertEquals(ready, controller.state.value)
            val postedAtCancel = inserter.posted.size
            advanceUntilIdle()
            assertTrue(inserter.posted.size <= postedAtCancel + 1)
            assertTrue(inserter.inserted.isEmpty())
            assertNull(controller.lastError.value)
            assertNull(controller.undeliveredTranscript.value)
            assertFalse(controller.canUndo)
        }

    @Test fun cancelWhileTypingDoesntJoinTheNextDictation() =
        runTest {
            val controller = makeController()
            val timings = mutableListOf<DictationTiming>()
            controller.onTiming = { timings += it }
            transcriber.result = "First dictation that is cancelled."
            inserter.delayPerCharacter = 5.milliseconds
            controller.pressed()
            controller.pressed()
            advanceTimeBy(20.milliseconds)
            controller.cancel()
            advanceUntilIdle()
            inserter.delayPerCharacter = null
            dictate(controller, "next one.")
            assertEquals(1, timings.size)
            assertEquals(listOf("next one."), inserter.inserted)
        }

    @Test fun aNewDictationStartedRightAfterCancellingTypingIsNotStopped() =
        runTest {
            val controller = makeController()
            transcriber.result = "A long dictation being typed slowly."
            inserter.delayPerCharacter = 5.milliseconds
            controller.pressed()
            controller.pressed()
            advanceTimeBy(20.milliseconds)
            controller.cancel()
            controller.pressed()
            advanceTimeBy(100.milliseconds)
            assertEquals(DictationState.Recording, controller.state.value)
            assertTrue(recorder.isRecording)
        }

    @Test fun microphoneStartFailureSurfacesErrorAndStaysReady() =
        runTest {
            val controller = makeController()
            recorder.startError = Boom()
            controller.pressed()
            assertEquals(ready, controller.state.value)
            assertNotNull(controller.lastError.value)
        }

    @Test fun refusesToRecordWhenTextCannotBeInserted() =
        runTest {
            val controller = makeController()
            inserter.preflightError = Boom()
            controller.pressed()
            controller.released()
            assertEquals(ready, controller.state.value)
            assertFalse(recorder.isRecording)
            assertNotNull(controller.lastError.value)
        }

    @Test fun failedInsertKeepsTranscriptForRetry() =
        runTest {
            val controller = makeController()
            inserter.insertError = Boom()
            dictate(controller, "hello world")
            assertEquals("hello world", controller.undeliveredTranscript.value)
            assertNotNull(controller.lastError.value)
            controller.retryUndelivered()
            assertEquals("hello world", controller.undeliveredTranscript.value)
            inserter.insertError = null
            controller.retryUndelivered()
            assertEquals(listOf("hello world"), inserter.inserted)
            assertNull(controller.undeliveredTranscript.value)
            assertNull(controller.lastError.value)
        }

    @Test fun nextSuccessfulDictationReplacesUndeliveredTranscript() =
        runTest {
            val controller = makeController()
            inserter.insertError = Boom()
            dictate(controller, "hello world")
            inserter.insertError = null
            dictate(controller, "hello world")
            assertNull(controller.undeliveredTranscript.value)
            assertEquals(listOf("hello world"), inserter.inserted)
        }

    @Test fun discardForgetsUndeliveredTranscript() =
        runTest {
            val controller = makeController()
            inserter.insertError = Boom()
            dictate(controller, "hello world")
            controller.discardUndelivered()
            assertNull(controller.undeliveredTranscript.value)
        }

    @Test fun pressIgnoredWhileTranscribing() =
        runTest {
            val controller = makeController()
            transcriber.delay = 300.milliseconds
            controller.pressed()
            controller.pressed()
            runCurrent()
            assertFalse(recorder.isRecording)
            controller.pressed()
            assertEquals(1, recorder.startCount)
            assertEquals(DictationState.Transcribing, controller.state.value)
            advanceUntilIdle()
            assertEquals(1, transcriber.received.size)
        }

    @Test fun cancelDuringStopTailClosesTheMicrophoneAtOnce() =
        runTest {
            val controller = makeController(stopTail = 200.milliseconds)
            controller.pressed()
            controller.pressed()
            assertTrue(recorder.isRecording)
            controller.cancel()
            assertFalse(recorder.isRecording)
            assertEquals(ready, controller.state.value)
            controller.pressed()
            advanceTimeBy(300.milliseconds)
            assertTrue(recorder.isRecording)
            assertEquals(DictationState.Recording, controller.state.value)
            assertTrue(transcriber.received.isEmpty())
        }
}
