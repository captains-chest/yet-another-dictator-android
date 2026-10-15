package com.captainduckay.dictator.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.captainduckay.dictator.core.DictationState
import kotlin.math.sin

/**
 * The floating microphone: one fixed-size, icon-only control (ADR 0007). Every state is drawn inside
 * the same bounds, so the window never grows, shrinks or moves when the state changes. A small
 * badge slot in the top-right corner is reserved for Undo, or Discard when a dictation could not be
 * typed; a tap on the badge runs that action, a tap anywhere else is the microphone (or Try again).
 * The window itself is not focusable.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class BubblePanel(context: Context, private val listener: Listener) : View(context) {
    interface Listener {
        fun onTap()

        fun onHoldStart()

        fun onHoldEnd(fingerDownMs: Long)

        fun onDragBy(dx: Int, dy: Int)

        fun onRetry()

        fun onDiscard()

        fun onUndo()
    }

    private var state: DictationState = DictationState.LoadingModel
    private var level = 0f
    private var error: String? = null
    private var mode = BubbleActions.Mode.Hidden
    private var shakeStartedAt = 0L

    private val accent = accentColor(context)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val icon =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val arc = RectF()

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        // An accessibility overlay does not show a hardware-accelerated circle. Software does.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        render(DictationState.LoadingModel, 0f, null, showRetry = false, canUndo = false)
    }

    /** Fixed size in every state: the window never changes size, so the button never moves. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = px(SIZE_DP)
        setMeasuredDimension(size, size)
    }

    fun render(state: DictationState, level: Float, error: String?, showRetry: Boolean, canUndo: Boolean) {
        // A model that failed to load reports through the state, not through the error.
        val newError =
            error?.takeIf { it.isNotBlank() }
                ?: (state as? DictationState.Unavailable)?.message?.takeIf { it.isNotBlank() }
        if (newError != null && newError != this.error) {
            shakeStartedAt = SystemClock.uptimeMillis()
            announceForAccessibility(newError)
        }
        this.state = state
        this.level = level.coerceIn(0f, 1f)
        this.error = newError
        mode = BubbleActions.mode(showRetry, canUndo, state)
        contentDescription = describe()
        invalidate()
    }

    private fun describe(): String {
        val main =
            when {
                mode == BubbleActions.Mode.Retry -> context.getString(R.string.action_try_again)
                state == DictationState.Recording -> context.getString(R.string.mic_label_recording)
                state == DictationState.Transcribing -> context.getString(R.string.mic_label_transcribing)
                state == DictationState.LoadingModel -> context.getString(R.string.mic_label_loading)
                else -> context.getString(R.string.mic_label)
            }
        return listOfNotNull(error, main).joinToString(". ")
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
        val label =
            when (mode) {
                BubbleActions.Mode.Retry -> context.getString(R.string.action_discard)
                BubbleActions.Mode.Undo -> context.getString(R.string.action_undo)
                BubbleActions.Mode.Hidden -> null
            }
        if (label != null) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.bubble_secondary, label))
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == R.id.bubble_secondary && mode != BubbleActions.Mode.Hidden) {
            secondary()
            return true
        }
        if (action == AccessibilityNodeInfo.ACTION_CLICK) {
            primaryTap()
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private fun primaryTap() {
        if (mode == BubbleActions.Mode.Retry) listener.onRetry() else listener.onTap()
    }

    private fun secondary() {
        when (mode) {
            BubbleActions.Mode.Retry -> listener.onDiscard()
            BubbleActions.Mode.Undo -> listener.onUndo()
            BubbleActions.Mode.Hidden -> Unit
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val size = width.toFloat()
        val inset = pxf(RING_DP)
        // The shake stays inside the reserved ring margin, so it never leaves the window.
        val shaking = now - shakeStartedAt < SHAKE_MS
        val shift = if (shaking) sin((now - shakeStartedAt) / 25.0).toFloat() * pxf(3) else 0f
        val cx = size / 2f + shift
        val cy = size / 2f
        val radius = size / 2f - inset

        val problem = error != null || state is DictationState.Unavailable || mode == BubbleActions.Mode.Retry
        fill.color =
            when {
                problem -> WARNING
                state == DictationState.Recording -> RECORDING
                state == DictationState.Transcribing -> TRANSCRIBING
                state == DictationState.LoadingModel -> IDLE_DIM
                else -> accent
            }
        canvas.drawCircle(cx, cy, radius, fill)

        var animating = shaking
        when {
            state == DictationState.Recording -> {
                // Level ring plus a slow pulse, both inside the fixed margin.
                val pulse = (sin(now / 180.0).toFloat() + 1f) / 2f
                ring.color = RECORDING
                ring.alpha = (90 + 120 * pulse).toInt()
                ring.strokeWidth = pxf(2) + pxf(3) * maxOf(level, pulse * 0.4f)
                canvas.drawCircle(cx, cy, radius + inset / 2f, ring)
                ring.alpha = 255
                animating = true
            }
            state == DictationState.Transcribing || state == DictationState.LoadingModel -> {
                val sweepStart = (now % 1000L) * 0.36f
                ring.color = if (state == DictationState.Transcribing) TRANSCRIBING else accent
                ring.strokeWidth = pxf(3)
                val r = radius + inset / 2f
                arc.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawArc(arc, sweepStart, 100f, false, ring)
                animating = true
            }
        }

        when {
            mode == BubbleActions.Mode.Retry -> drawRetry(canvas, cx, cy)
            problem -> drawWarning(canvas, cx, cy)
            else -> drawMic(canvas, cx, cy)
        }
        drawBadge(canvas, size)
        if (animating) postInvalidateOnAnimation()
    }

    private fun drawMic(canvas: Canvas, cx: Float, cy: Float) {
        icon.style = Paint.Style.FILL
        val head = pxf(7)
        canvas.drawRoundRect(cx - head / 2f, cy - pxf(12), cx + head / 2f, cy - pxf(1), head, head, icon)
        icon.style = Paint.Style.STROKE
        icon.strokeWidth = pxf(2)
        canvas.drawArc(cx - pxf(8), cy - pxf(10), cx + pxf(8), cy + pxf(4), 0f, 180f, false, icon)
        canvas.drawLine(cx, cy + pxf(4), cx, cy + pxf(9), icon)
        canvas.drawLine(cx - pxf(5), cy + pxf(9), cx + pxf(5), cy + pxf(9), icon)
    }

    private fun drawWarning(canvas: Canvas, cx: Float, cy: Float) {
        icon.style = Paint.Style.STROKE
        icon.strokeWidth = pxf(3)
        canvas.drawLine(cx, cy - pxf(10), cx, cy + pxf(3), icon)
        icon.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy + pxf(9), pxf(2), icon)
    }

    private fun drawRetry(canvas: Canvas, cx: Float, cy: Float) {
        icon.style = Paint.Style.STROKE
        icon.strokeWidth = pxf(2.5f)
        val r = pxf(9)
        arc.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(arc, -60f, 300f, false, icon)
        // Arrow head at the arc's start (top right).
        val ax = cx + r * 0.5f
        val ay = cy - r * 0.866f
        canvas.drawLine(ax, ay, ax - pxf(6), ay - pxf(2), icon)
        canvas.drawLine(ax, ay, ax - pxf(1), ay + pxf(6), icon)
    }

    /** The reserved top-right slot. It is drawn only when there is an action, but never moves anything. */
    private fun drawBadge(canvas: Canvas, size: Float) {
        if (mode == BubbleActions.Mode.Hidden) return
        val r = pxf(BADGE_DP) / 2f
        val bx = size - r
        val by = r
        fill.color = BADGE
        canvas.drawCircle(bx, by, r, fill)
        icon.style = Paint.Style.STROKE
        icon.strokeWidth = pxf(1.8f)
        val k = r * 0.45f
        if (mode == BubbleActions.Mode.Retry) {
            canvas.drawLine(bx - k, by - k, bx + k, by + k, icon)
            canvas.drawLine(bx - k, by + k, bx + k, by - k, icon)
        } else {
            // Undo: a hooked arrow pointing left.
            arc.set(bx - k, by - k, bx + k, by + k)
            canvas.drawArc(arc, 180f, 270f, false, icon)
            canvas.drawLine(bx - k, by, bx - k - pxf(2), by - pxf(3), icon)
            canvas.drawLine(bx - k, by, bx - k + pxf(3), by - pxf(2), icon)
        }
    }

    private fun inBadge(x: Float, y: Float): Boolean {
        if (mode == BubbleActions.Mode.Hidden) return false
        val slot = pxf(BADGE_DP) + pxf(4)
        return x >= width - slot && y <= slot
    }

    private fun px(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun pxf(value: Int) = value * resources.displayMetrics.density

    private fun pxf(value: Float) = value * resources.displayMetrics.density

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var downAt = 0L
    private var dragging = false
    private var holding = false
    private var downOnBadge = false
    private val decideHold =
        Runnable {
            if (dragging) return@Runnable
            holding = true
            listener.onHoldStart()
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downOnBadge = inBadge(event.x, event.y)
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                downAt = SystemClock.elapsedRealtime()
                dragging = false
                holding = false
                // Only the microphone itself records on hold. A press on the badge, or on Try again,
                // is a tap or a drag, so it can never start a dictation by accident.
                if (!downOnBadge && mode != BubbleActions.Mode.Retry) {
                    handler.postDelayed(decideHold, BubbleGesture.DRAG_DECISION_MS)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!holding && !dragging && dx * dx + dy * dy > slop * slop) {
                    dragging = true
                    handler.removeCallbacks(decideHold)
                }
                if (dragging && !holding) {
                    listener.onDragBy((event.rawX - lastRawX).toInt(), (event.rawY - lastRawY).toInt())
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(decideHold)
                val fingerDown = SystemClock.elapsedRealtime() - downAt
                when (
                    BubbleGesture.onRelease(
                        holding = holding,
                        dragging = dragging,
                        fingerDownMs = fingerDown,
                        canceled = event.actionMasked == MotionEvent.ACTION_CANCEL,
                    )
                ) {
                    BubbleGesture.Release.Ignored -> Unit
                    BubbleGesture.Release.Tap -> if (downOnBadge) secondary() else primaryTap()
                    is BubbleGesture.Release.Hold -> listener.onHoldEnd(fingerDown)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(decideHold)
        super.onDetachedFromWindow()
    }

    companion object {
        /** The whole window, ring margin included. It is the same in every state. */
        const val SIZE_DP = 64
        private const val RING_DP = 6
        private const val BADGE_DP = 20
        private const val SHAKE_MS = 400L
        private const val RECORDING = 0xFFD32F2F.toInt()
        private const val TRANSCRIBING = 0xFFF57C00.toInt()
        private const val WARNING = 0xFFB26A00.toInt()
        private const val IDLE_DIM = 0xFF757575.toInt()
        private const val BADGE = 0xE6212121.toInt()

        private fun accentColor(context: Context): Int {
            val night =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            val scheme = if (night) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            return scheme.primary.toArgb()
        }
    }
}
