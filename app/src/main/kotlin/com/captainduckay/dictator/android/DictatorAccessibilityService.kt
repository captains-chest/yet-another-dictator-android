package com.captainduckay.dictator.android

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.captainduckay.dictator.core.FieldEdit

/**
 * Types into the focused field of whatever app is open. The only text it reads is that field, and
 * only while placing a dictation. Event text is never read, stored or logged (ADR 0007).
 *
 * The microphone is shown from here, and only while a text field is focused. This service does not
 * use Android's accessibility shortcut button. That button was a second control.
 */
class DictatorAccessibilityService : AccessibilityService(), FocusedField {
    override fun onServiceConnected() {
        instance = this
        val waiting = onConnected
        onConnected = null
        waiting?.invoke()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance == this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeBubble()
        if (instance == this) instance = null
        // The window belonged to this service. Ask the button to put it back when we return.
        FloatingButtonService.onAccessibilityStopped()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        ) {
            val source = event.source
            if (source != null) {
                try {
                    if (source.isEditable) FieldBridge.externalEdit()
                } finally {
                    source.recycle()
                }
            }
        }
        if (isFocusSignal(type)) FloatingButtonService.onInputField(hasFocusedField())
    }

    private fun isFocusSignal(type: Int): Boolean =
        type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED

    override fun read(): FieldRead =
        withWindows { open ->
            val found = findEditable(open) ?: return@withWindows FieldRead.None
            try {
                if (found.isPassword) {
                    FieldRead.Unsupported
                } else {
                    val text = found.text?.toString() ?: ""
                    FieldRead.Ready(FieldEdit.snapshot(text, found.textSelectionStart, found.textSelectionEnd))
                }
            } finally {
                found.recycle()
            }
        } ?: FieldRead.None

    override fun write(text: String, cursor: Int): Boolean =
        withWindows { open ->
            val found = findEditable(open) ?: return@withWindows false
            try {
                if (found.isPassword) return@withWindows false
                val arguments = Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                if (!found.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return@withWindows false
                val selection = Bundle()
                selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
                found.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
                FieldBridge.markOwnWrite()
                true
            } finally {
                found.recycle()
            }
        } ?: false

    private fun focusedFieldPresent(): Boolean =
        withWindows { open ->
            val found = findEditable(open) ?: return@withWindows false
            found.recycle()
            true
        } ?: false

    private fun findEditable(open: List<AccessibilityWindowInfo>): AccessibilityNodeInfo? {
        for (index in open.indices) {
            val root = open[index].root ?: continue
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused == null) {
                root.recycle()
                continue
            }
            if (focused !== root) root.recycle()
            if (focused.canAcceptText()) return focused
            focused.recycle()
        }
        return null
    }

    private fun AccessibilityNodeInfo.canAcceptText(): Boolean {
        if (isPassword) return true
        if (isEditable) return true
        return actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
    }

    private inline fun <T> withWindows(block: (List<AccessibilityWindowInfo>) -> T): T? {
        val open = windows ?: return null
        return try {
            block(open)
        } catch (_: RuntimeException) {
            null
        } finally {
            for (index in open.indices) open[index].recycle()
        }
    }

    companion object {
        @Volatile private var instance: DictatorAccessibilityService? = null
        private var onConnected: (() -> Unit)? = null
        private var bubbleWindow: WindowManager? = null
        private var bubble: View? = null

        fun isEnabled(): Boolean = instance != null

        fun field(): FocusedField? = instance

        /** Whether some window has a focused field that can take text. The field's text is not read. */
        fun hasFocusedField(): Boolean = instance?.focusedFieldPresent() ?: false

        fun runWhenConnected(block: () -> Unit) {
            if (instance != null) block() else onConnected = block
        }

        fun clearWhenConnected() {
            onConnected = null
        }

        /**
         * An accessibility overlay, not a draw-over-other-apps window. The latter is hidden when
         * Dictator leaves the screen, and a press on it never reaches the microphone.
         */
        fun showBubble(panel: View, params: WindowManager.LayoutParams): Boolean {
            val service = instance ?: return false
            if (bubble === panel && panel.isAttachedToWindow) return true
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            params.format = PixelFormat.TRANSLUCENT
            val windows = service.getSystemService(WindowManager::class.java)
            return try {
                windows.addView(panel, params)
                bubbleWindow = windows
                bubble = panel
                true
            } catch (_: WindowManager.BadTokenException) {
                false
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalStateException) {
                bubbleWindow = windows
                bubble = panel
                true
            }
        }

        fun updateBubble(panel: View, params: WindowManager.LayoutParams) {
            try {
                bubbleWindow?.updateViewLayout(panel, params)
            } catch (_: IllegalArgumentException) {
                // The window was already removed.
            }
        }

        fun removeBubble() {
            val panel = bubble ?: return
            try {
                bubbleWindow?.removeView(panel)
            } catch (_: IllegalArgumentException) {
                // Already gone.
            }
            bubble = null
            bubbleWindow = null
        }
    }
}
