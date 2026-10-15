package com.captainduckay.dictator.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.captainduckay.dictator.core.DictationController
import com.captainduckay.dictator.core.DictationLanguage
import com.captainduckay.dictator.core.DictationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The on-screen microphone. It is not a keyboard: the user's own keyboard stays, and dictated text
 * is typed into the focused field by [DictatorAccessibilityService] (ADR 0007). The model stays
 * loaded while dictation is on, so the next dictation does not pay to load it again. The microphone
 * itself is shown only while a text field is focused.
 */
class FloatingButtonService : Service(), BubblePanel.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private lateinit var settings: Settings
    private lateinit var transcriber: ChosenModelTranscriber
    private lateinit var controller: DictationController
    private lateinit var panel: BubblePanel
    private lateinit var params: WindowManager.LayoutParams
    private var localMessage: String? = null
    private var microphoneType = false
    private var foregroundStarted = false
    private var callbacksRegistered = false
    private var bubbleRequested = false
    private var inputFocused = false
    private var laidOutWidth = -1
    private var laidOutHeight = -1
    private val handler = Handler(Looper.getMainLooper())
    private val hideWhenFieldGone =
        Runnable {
            inputFocused = false
            syncBubble()
        }

    private val configuration =
        object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                if (!::panel.isInitialized || !panel.isAttachedToWindow) return
                clamp()
                DictatorAccessibilityService.updateBubble(panel, params)
            }

            override fun onLowMemory() = Unit
        }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        createChannel()
        // Take the microphone type while the user is still in the app. Adding it later, from the
        // background, is what Android 14 refuses, and that would make the first dictation fail.
        microphoneType = true
        if (!enterForeground()) return
        mutableRunning.value = true
        transcriber = ChosenModelTranscriber(this)
        val fields =
            object : FocusedField {
                override fun read() = DictatorAccessibilityService.field()?.read() ?: FieldRead.None

                override fun write(text: String, cursor: Int) =
                    DictatorAccessibilityService.field()?.write(text, cursor) ?: false
            }
        val editor = AccessibilityFieldEditor(this, fields, DictatorAccessibilityService::isEnabled)
        lateinit var recorder: MicrophoneRecorder
        recorder =
            MicrophoneRecorder(
                onFailure = { controller.recordingFailed(it) },
                onLimit = { controller.recordingReachedLimit() },
            )
        controller = DictationController(scope, recorder, transcriber, editor, editor)
        controller.onTiming = { timing ->
            Log.i(
                TAG,
                String.format(
                    Locale.ROOT,
                    "dictation audio %.0f ms, transcription %.0f ms, until typed %.0f ms",
                    timing.audioSeconds * 1_000,
                    timing.transcriptionSeconds * 1_000,
                    timing.releaseToTypedSeconds * 1_000,
                ),
            )
        }
        FieldBridge.onExternalEdit = {
            controller.noteOtherInput()
            render()
        }
        instance = this
        panel = BubblePanel(this, this)
        panel.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width == laidOutWidth && height == laidOutHeight) return@addOnLayoutChangeListener
            laidOutWidth = width
            laidOutHeight = height
            if (view.isAttachedToWindow) DictatorAccessibilityService.updateBubble(panel, params)
        }
        params = overlayParams()
        registerComponentCallbacks(configuration)
        callbacksRegistered = true
        // The window waits for a text field. The accessibility service may still be binding.
        DictatorAccessibilityService.runWhenConnected {
            if (instance == this) {
                inputFocused = DictatorAccessibilityService.hasFocusedField()
                syncBubble()
            }
        }
        scope.launch { controller.loadModel() }
        // A new choice or a finished download: load that model (ADR 0008).
        scope.launch {
            SpeechModel.changes.collect {
                val state = controller.state.value
                // Never interrupt a dictation; the next change or launch loads it.
                if (state != DictationState.Recording && state != DictationState.Transcribing) controller.loadModel()
            }
        }
        scope.launch {
            controller.level.collect { render() }
        }
        scope.launch {
            combine(controller.state, controller.lastError, controller.undeliveredTranscript) { state, _, _ -> state }
                .collect { state ->
                    if (state == DictationState.Recording) localMessage = null
                    render()
                    enterForeground()
                    syncBubble()
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!foregroundStarted) enterForeground()
        if (::panel.isInitialized) syncBubble()
        return START_STICKY
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.cancel()
        FieldBridge.onExternalEdit = null
        handler.removeCallbacks(hideWhenFieldGone)
        if (callbacksRegistered) unregisterComponentCallbacks(configuration)
        DictatorAccessibilityService.clearWhenConnected()
        if (bubbleRequested) {
            DictatorAccessibilityService.removeBubble()
            bubbleRequested = false
        }
        scope.cancel()
        if (::transcriber.isInitialized) transcriber.close()
        mutableRunning.value = false
        if (instance == this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    override fun onTap() {
        beginPress()
        if (::controller.isInitialized) controller.released()
    }

    override fun onHoldStart() = beginPress()

    override fun onHoldEnd(fingerDownMs: Long) {
        controller.released()
        val stillRecording = controller.state.value == DictationState.Recording
        if (BubbleGesture.holdNeedsForcedStop(fingerDownMs, stillRecording)) controller.pressed()
    }

    override fun onDragBy(dx: Int, dy: Int) {
        params.x += dx
        params.y += dy
        clamp()
        DictatorAccessibilityService.updateBubble(panel, params)
        settings.saveBubblePosition(params.x, params.y)
    }

    override fun onRetry() {
        scope.launch { controller.retryUndelivered() }
    }

    override fun onDiscard() = controller.discardUndelivered()

    override fun onUndo() {
        scope.launch {
            controller.undoLastDictation()
            render()
        }
    }

    private fun beginPress() {
        when (val state = controller.state.value) {
            is DictationState.Unavailable -> openApp()
            DictationState.LoadingModel, DictationState.Transcribing -> Unit
            DictationState.Ready, DictationState.Recording -> {
                if (!hasMicrophone()) {
                    localMessage = getString(R.string.needs_microphone)
                    render()
                    openApp()
                    return
                }
                // A process restart may have had to drop the microphone type. The finger is the
                // user gesture that lets us ask for it again, before AudioRecord starts.
                setMicrophoneType(true)
                // Dictator is English-only: Whisper is told so, Parakeet only knows English.
                controller.language = DictationLanguage.ENGLISH
                controller.pressed()
            }
        }
    }

    private fun reattachAfterAccessibility() {
        bubbleRequested = false
        laidOutWidth = -1
        laidOutHeight = -1
        DictatorAccessibilityService.runWhenConnected {
            if (instance == this) {
                inputFocused = DictatorAccessibilityService.hasFocusedField()
                syncBubble()
            }
        }
    }

    private fun noteInputField(focused: Boolean) {
        handler.removeCallbacks(hideWhenFieldGone)
        if (focused) {
            inputFocused = true
            syncBubble()
        } else {
            // Focus can blip while the keyboard or our own window is added. Wait before hiding.
            handler.postDelayed(hideWhenFieldGone, BubblePresence.FIELD_GONE_MS)
        }
    }

    private fun wantsBubble(): Boolean {
        val state = if (::controller.isInitialized) controller.state.value else DictationState.LoadingModel
        val undelivered = ::controller.isInitialized && controller.undeliveredTranscript.value != null
        return BubblePresence.visible(inputFocused, state, undelivered)
    }

    private fun syncBubble() {
        if (!::panel.isInitialized) return
        if (wantsBubble()) showBubble() else hideBubble()
    }

    private fun hideBubble() {
        if (!bubbleRequested && !panel.isAttachedToWindow) return
        DictatorAccessibilityService.removeBubble()
        bubbleRequested = false
        laidOutWidth = -1
        laidOutHeight = -1
    }

    private fun showBubble(): Boolean {
        if (!::panel.isInitialized) return false
        if (!BubbleGesture.needsAdding(bubbleRequested, panel.isAttachedToWindow)) return true
        // Mark the request before addView. A second addView, before the view is attached, kills
        // the process.
        bubbleRequested = true
        if (!DictatorAccessibilityService.showBubble(panel, params)) {
            bubbleRequested = false
            return false
        }
        return true
    }

    private fun overlayParams(): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        val flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT,
            )
        params.gravity = Gravity.TOP or Gravity.START
        params.fitInsetsTypes = 0
        val bounds = windowManager.currentWindowMetrics.bounds
        val saved = settings.bubblePosition()
        if (saved != null) {
            params.x = saved.first
            params.y = saved.second
        } else {
            params.x = bounds.width() - dp(72)
            params.y = (bounds.height() * 0.45f).toInt()
        }
        return params
    }

    private fun clamp() {
        val bounds = windowManager.currentWindowMetrics.bounds
        val width = panel.width.takeIf { it > 0 } ?: dp(BubblePanel.SIZE_DP)
        val height = panel.height.takeIf { it > 0 } ?: dp(BubblePanel.SIZE_DP)
        params.x = params.x.coerceIn(0, (bounds.width() - width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (bounds.height() - height).coerceAtLeast(0))
    }

    private fun render() {
        if (!::panel.isInitialized) return
        val error = localMessage ?: controller.lastError.value
        panel.render(
            controller.state.value,
            controller.level.value,
            error,
            controller.undeliveredTranscript.value != null,
            controller.canUndo,
        )
    }

    private fun hasMicrophone() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun setMicrophoneType(enabled: Boolean) {
        if (microphoneType == enabled) return
        microphoneType = enabled
        enterForeground()
    }

    private fun enterForeground(): Boolean {
        val notification = notification()
        val type = foregroundType()
        return try {
            if (type == 0) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification, type)
            }
            foregroundStarted = true
            true
        } catch (_: RuntimeException) {
            if (microphoneType) {
                microphoneType = false
                enterForeground()
            } else {
                stopSelf()
                false
            }
        }
    }

    private fun foregroundType(): Int {
        val microphone = if (microphoneType) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        if (Build.VERSION.SDK_INT < 34) return microphone
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or microphone
    }

    private fun notification(): Notification {
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, FloatingButtonService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val text =
            when (if (::controller.isInitialized) controller.state.value else DictationState.LoadingModel) {
                DictationState.Recording -> getString(R.string.notification_listening)
                DictationState.Transcribing -> getString(R.string.notification_transcribing)
                DictationState.LoadingModel -> getString(R.string.status_loading)
                else -> getString(R.string.notification_ready)
            }
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.action_hide_button), stop).build(),
            ).build()
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        channel.setSound(null, null)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_STOP = "com.captainduckay.dictator.android.HIDE_BUTTON"
        private const val CHANNEL_ID = "dictation"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "Dictator"

        private val mutableRunning = MutableStateFlow(false)
        val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

        @Volatile private var instance: FloatingButtonService? = null

        /** The accessibility service was turned off, taking the window with it. */
        fun onAccessibilityStopped() {
            instance?.reattachAfterAccessibility()
        }

        /** A text field gained or lost input focus. Event text is not included. */
        fun onInputField(focused: Boolean) {
            instance?.handler?.post { instance?.noteInputField(focused) }
        }

        fun show(context: android.content.Context) {
            ContextCompat.startForegroundService(context, Intent(context, FloatingButtonService::class.java))
        }

        fun hide(context: android.content.Context) {
            context.startService(Intent(context, FloatingButtonService::class.java).setAction(ACTION_STOP))
        }
    }
}
