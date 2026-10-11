package com.captainduckay.dictator.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.captainduckay.dictator.core.DictationController
import com.captainduckay.dictator.core.DictationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The Dictator keyboard. Records, transcribes and types in this one service (ADR 0001): the active
 * keyboard's window is visible, so it may use the microphone, and the model is loaded once while
 * the keyboard is in use.
 */
class DictatorIme :
    InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: Settings
    private lateinit var transcriber: WhisperTranscriber
    private lateinit var inserter: ImeTextInserter
    lateinit var controller: DictationController
        private set

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        settings = Settings(this)
        transcriber = WhisperTranscriber(this)
        inserter = ImeTextInserter(this)
        lateinit var recorder: MicrophoneRecorder
        recorder =
            MicrophoneRecorder(
                onFailure = { controller.recordingFailed(it) },
                onLimit = { controller.recordingReachedLimit() },
            )
        controller = DictationController(scope, recorder, transcriber, inserter, ImePrecedingTextReader(this))
        scope.launch { controller.loadModel() }
    }

    override fun onCreateInputView(): View {
        val decor = window.window!!.decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DictatorIme)
            setViewTreeSavedStateRegistryOwner(this@DictatorIme)
            setContent {
                KeyboardScreen(
                    controller = controller,
                    hasMicrophone = ::hasMicrophonePermission,
                    onSwitchKeyboard = ::switchKeyboard,
                    onOpenApp = ::openApp,
                    onRetry = { scope.launch { controller.retryUndelivered() } },
                    onBeforePress = { controller.language = settings.language },
                )
            }
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        if (!restarting) controller.noteOtherInput()
        inserter.cursor = editorInfo?.initialSelEnd ?: -1
        inserter.resetExpectation()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Leaving the field ends any dictation without typing into whatever comes next.
        controller.cancel()
        controller.noteOtherInput()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        inserter.cursor = newSelEnd
        val ours = newSelStart == newSelEnd && newSelEnd == inserter.expectedCursor
        if (!ours && controller.state.value != DictationState.Transcribing) {
            controller.noteOtherInput()
            inserter.resetExpectation()
        }
    }

    override fun onDestroy() {
        controller.cancel()
        scope.cancel()
        transcriber.close()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun hasMicrophonePermission() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun switchKeyboard() {
        if (!switchToNextInputMethod(false)) {
            getSystemService(android.view.inputmethod.InputMethodManager::class.java).showInputMethodPicker()
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
