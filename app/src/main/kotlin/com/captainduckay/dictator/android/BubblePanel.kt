package com.captainduckay.dictator.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.captainduckay.dictator.core.DictationState

/** The floating microphone and the short status line above it. The window itself is not focusable. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class BubblePanel(context: Context, private val listener: Listener) : LinearLayout(context) {
    interface Listener {
        fun onTap()

        fun onHoldStart()

        fun onHoldEnd(fingerDownMs: Long)

        fun onDragBy(dx: Int, dy: Int)

        fun onRetry()

        fun onDiscard()

        fun onUndo()
    }

    private val bubble = BubbleView(context)
    private val caption: TextView
    private val message: TextView
    private val actions: LinearLayout
    private val retry: TextView
    private val discard: TextView
    private val undo: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val pad = dp(8)
        setPadding(pad, pad, pad, pad)
        message = label()
        caption = label()
        retry = action(context.getString(R.string.action_try_again), listener::onRetry)
        discard = action(context.getString(R.string.action_discard), listener::onDiscard)
        undo = action(context.getString(R.string.action_undo), listener::onUndo)
        actions =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                visibility = GONE
                addView(retry)
                addView(discard)
                addView(undo)
            }
        addView(message)
        addView(actions)
        addView(caption)
        addView(bubble, LayoutParams(dp(56), dp(56)).apply { topMargin = dp(4) })
        bubble.contentDescription = context.getString(R.string.mic_label)
        isClickable = true
        // An accessibility overlay does not show a hardware-accelerated circle. Software does.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        render(DictationState.LoadingModel, 0f, null, showRetry = false, canUndo = false)
    }

    fun render(state: DictationState, level: Float, error: String?, showRetry: Boolean, canUndo: Boolean) {
        bubble.state = state
        bubble.level = level
        caption.text =
            when (state) {
                DictationState.Recording -> context.getString(R.string.caption_recording)
                DictationState.Transcribing -> context.getString(R.string.caption_transcribing)
                DictationState.LoadingModel -> context.getString(R.string.status_loading)
                else -> context.getString(R.string.caption_ready)
            }
        message.text = error
        message.visibility = if (error.isNullOrBlank()) GONE else VISIBLE
        val mode = BubbleActions.mode(showRetry, canUndo, state)
        when (mode) {
            BubbleActions.Mode.Retry -> {
                retry.visibility = VISIBLE
                discard.visibility = VISIBLE
                undo.visibility = GONE
                actions.visibility = VISIBLE
            }
            BubbleActions.Mode.Undo -> {
                retry.visibility = GONE
                discard.visibility = GONE
                undo.visibility = VISIBLE
                actions.visibility = VISIBLE
            }
            BubbleActions.Mode.Hidden -> actions.visibility = GONE
        }
        // Retry keeps the ready caption; Undo is quiet like idle Ready.
        val quiet = state == DictationState.Ready && error.isNullOrBlank() && mode != BubbleActions.Mode.Retry
        caption.visibility = if (quiet) GONE else VISIBLE
    }

    private fun label() =
        TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            maxWidth = dp(240)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(0xCC000000.toInt())
        }

    private fun action(title: String, onClick: () -> Unit) =
        TextView(context).apply {
            text = title
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private inner class BubbleView(context: Context) : View(context) {
        var state: DictationState = DictationState.Ready
            set(value) {
                field = value
                invalidate()
            }
        var level: Float = 0f
            set(value) {
                field = value
                invalidate()
            }

        private val accent = accentColor(context)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val icon =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                strokeCap = Paint.Cap.ROUND
            }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val scale = if (state == DictationState.Recording) 1f + level.coerceIn(0f, 1f) * 0.12f else 1f
            canvas.save()
            canvas.scale(scale, scale, cx, cy)
            fill.color =
                when (state) {
                    DictationState.Recording -> RECORDING
                    DictationState.Transcribing -> TRANSCRIBING
                    else -> accent
                }
            canvas.drawCircle(cx, cy, cx - dp(2), fill)
            drawMic(canvas, cx, cy)
            canvas.restore()
        }

        private fun drawMic(canvas: Canvas, cx: Float, cy: Float) {
            icon.style = Paint.Style.FILL
            val head = dp(7).toFloat()
            canvas.drawRoundRect(cx - head / 2f, cy - dp(12), cx + head / 2f, cy - dp(1), head, head, icon)
            icon.style = Paint.Style.STROKE
            icon.strokeWidth = dp(2).toFloat()
            canvas.drawArc(cx - dp(8), cy - dp(10), cx + dp(8), cy + dp(4), 0f, 180f, false, icon)
            canvas.drawLine(cx, cy + dp(4), cx, cy + dp(9), icon)
            canvas.drawLine(cx - dp(5), cy + dp(9), cx + dp(5), cy + dp(9), icon)
        }

        private fun dp(value: Int) = value * resources.displayMetrics.density
    }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var downAt = 0L
    private var dragging = false
    private var holding = false
    private val decideHold =
        Runnable {
            if (dragging) return@Runnable
            holding = true
            listener.onHoldStart()
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                downAt = SystemClock.elapsedRealtime()
                dragging = false
                holding = false
                handler.postDelayed(decideHold, BubbleGesture.DRAG_DECISION_MS)
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
                    BubbleGesture.Release.Tap -> listener.onTap()
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

    private companion object {
        const val RECORDING = 0xFFD32F2F.toInt()
        const val TRANSCRIBING = 0xFFF57C00.toInt()

        fun accentColor(context: Context): Int {
            val night =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            val scheme = if (night) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            return scheme.primary.toArgb()
        }
    }
}
