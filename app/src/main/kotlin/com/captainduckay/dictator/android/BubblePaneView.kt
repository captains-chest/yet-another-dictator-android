package com.captainduckay.dictator.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.captainduckay.dictator.core.DictationState
import kotlin.math.PI
import kotlin.math.sin

/**
 * The wooden plank beside the microphone (ADR 0007), in its own window so the microphone's window
 * never changes. It slides out from behind the button toward the middle of the screen: level bars and
 * a timer while listening, a flowing wave and "Writing" while transcribing, then "Typed N words ·
 * Undo" for a few seconds. Only Undo takes a tap; otherwise the window lets touches through.
 *
 * While it slides, the window reaches under the button to its centre, and the plank is clipped
 * around the cookie, so it looks like it comes from behind it. Once it offers Undo, the window ends
 * at the plank, so it never covers the button.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class BubblePaneView(context: Context, private val listener: Listener) : View(context) {
    interface Listener {
        fun onUndo()

        /** The pane opened, closed, finished hiding, or changed size or touchability. */
        fun onPaneChanged()
    }

    var kind = BubblePane.Kind.None
        private set

    /** Set by the service from the button's place on screen. */
    var onLeft = true

    /** Only Undo takes a tap, so only then is the window touchable. */
    val touchable get() = kind == BubblePane.Kind.Typed

    /** The window is up while the plank is shown or still sliding away. */
    val wantsWindow get() = kind != BubblePane.Kind.None || shown > 0f

    /** "Typed N words", while the plank offers Undo. */
    val undoLabel get() = typedLabel.takeIf { kind == BubblePane.Kind.Typed }

    private var shown = 0f
    private var animator: ValueAnimator? = null
    private var recordingSince = 0L
    private var typedAt = 0L
    private var lastTyped: String? = null
    private var typedLabel = ""
    private var wasRecording = false
    private val levels = FloatArray(BARS)

    /** The plank width drawn now; it eases toward the width the content needs. */
    private var plankShown = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outline =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = INK
            strokeWidth = px(2.5f)
            strokeJoin = Paint.Join.ROUND
        }
    private val wave =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = ACCENT
            strokeWidth = px(3f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = px(TEXT_DP)
        }
    private val plank = RectF()
    private val wavy = Path()
    private val cookie = Path()
    private var shader: LinearGradient? = null
    private val closeTyped = Runnable { refresh() }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun render(
        state: DictationState,
        level: Float,
        problem: Boolean,
        mode: BubbleActions.Mode,
        typed: String?,
    ) {
        val now = SystemClock.uptimeMillis()
        val recording = state == DictationState.Recording
        if (recording && !wasRecording) {
            recordingSince = now
            levels.fill(0f)
        }
        wasRecording = recording
        if (recording) {
            System.arraycopy(levels, 1, levels, 0, BARS - 1)
            levels[BARS - 1] = BubblePane.loudness(level)
        }
        // A new dictation was typed: say so, with Undo, for a few seconds.
        if (typed != null && typed !== lastTyped && state == DictationState.Ready) {
            typedAt = now
            val words = BubblePane.words(typed)
            typedLabel = resources.getQuantityString(R.plurals.pane_typed, words, words)
            removeCallbacks(closeTyped)
            postDelayed(closeTyped, BubblePane.TYPED_MS)
        }
        lastTyped = typed
        this.state = state
        this.mode = mode
        this.problem = problem
        refresh()
        invalidate()
    }

    private var state: DictationState = DictationState.LoadingModel
    private var mode = BubbleActions.Mode.Hidden
    private var problem = false

    private fun refresh() {
        val typedRecently = typedAt != 0L && SystemClock.uptimeMillis() - typedAt < BubblePane.TYPED_MS
        val next = BubblePane.kind(state, mode, problem, typedRecently)
        if (next == kind) return
        val opening = kind == BubblePane.Kind.None
        kind = next
        if (opening) plankShown = plankWidth()
        if (next == BubblePane.Kind.None) {
            slide(to = 0f)
        } else if (opening) {
            slide(to = 1f)
        }
        requestLayout()
        invalidate()
        listener.onPaneChanged()
    }

    private fun slide(to: Float) {
        animator?.cancel()
        if (shown == to) return
        animator =
            ValueAnimator.ofFloat(shown, to).apply {
                duration = if (to > 0f) SHOW_MS else HIDE_MS
                interpolator = if (to > 0f) DecelerateInterpolator(2f) else AccelerateInterpolator(1.5f)
                addUpdateListener {
                    shown = it.animatedValue as Float
                    invalidate()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        private var canceled = false

                        override fun onAnimationCancel(animation: Animator) {
                            canceled = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (!canceled && to == 0f) listener.onPaneChanged()
                        }
                    },
                )
                start()
            }
    }

    /** Width of the window, given the side and whether it reaches under the button. */
    fun windowWidth(): Int = (px(SLACK_DP) + plankMax() + px(SHADOW_DP) + reach()).toInt() + 1

    /** Where the window starts, relative to the button's left edge. */
    fun windowOffset(): Int =
        if (onLeft) {
            (nearEdge() - windowWidth()).toInt()
        } else {
            (px(BubblePanel.SIZE_DP) - nearEdge()).toInt()
        }

    /** How far the window reaches past the plank's near end, toward the button. */
    private fun reach(): Float = if (touchable) 0f else px(BubblePanel.SIZE_DP / 2 - RING_DP + GAP_DP)

    /** The window's edge on the button's side, from the button's own edge on that side. */
    private fun nearEdge(): Float =
        if (touchable) {
            px(RING_DP - GAP_DP) + if (onLeft) px(SHADOW_DP) else 0f
        } else {
            px(BubblePanel.SIZE_DP / 2)
        }

    private fun plankMax(): Float = maxOf(listeningWidth(), typedWidth())

    private fun plankWidth(): Float =
        when (kind) {
            BubblePane.Kind.None -> plankShown
            BubblePane.Kind.Listening, BubblePane.Kind.Writing -> listeningWidth()
            BubblePane.Kind.Typed -> typedWidth()
        }

    /** Listening and Writing share a width, so the plank doesn't change when listening ends. */
    private fun listeningWidth(): Float =
        px(PANE_PAD_DP) * 2 +
            maxOf(
                barsWidth() + px(INNER_GAP_DP) + text.measureText(TIMER_WIDEST),
                px(WAVY_DP) + px(INNER_GAP_DP) + text.measureText(context.getString(R.string.pane_writing)),
            )

    private fun typedWidth(): Float =
        if (typedLabel.isEmpty()) {
            0f
        } else {
            px(PANE_PAD_DP) * 2 + text.measureText(typedLabel) + px(INNER_GAP_DP) * 2 + text.measureText(undo())
        }

    private fun barsWidth() = px(BAR_DP) * BARS + px(BAR_GAP_DP) * (BARS - 1)

    private fun undo() = context.getString(R.string.action_undo)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(windowWidth(), px(BubblePanel.SIZE_DP.toFloat()).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (shown <= 0f) return
        val now = SystemClock.uptimeMillis()
        val target = plankWidth()
        plankShown += (target - plankShown) * 0.3f
        if (kotlin.math.abs(target - plankShown) < 0.5f) plankShown = target
        val w = plankShown
        val h = px(PANE_HEIGHT_DP)
        val top = (height - h) / 2f
        // The plank's end by the button, in this window; it slides in from behind the button.
        val offset = windowOffset()
        val near = (if (onLeft) px(RING_DP - GAP_DP) else px(BubblePanel.SIZE_DP - RING_DP + GAP_DP)) - offset
        val travel = (1f - shown) * (w + px(BubblePanel.SIZE_DP / 2))
        val left = if (onLeft) near - w + travel else near - travel
        plank.set(left, top, left + w, top + h)

        // Keep the plank behind the cookie while it slides.
        val cx = px(BubblePanel.SIZE_DP / 2) - offset
        cookie.reset()
        cookie.addCircle(cx, height / 2f, px(BubblePanel.SIZE_DP / 2 - RING_DP + 4), Path.Direction.CW)

        canvas.save()
        canvas.clipOutPath(cookie)
        val alpha = (255 * minOf(1f, shown * 2f)).toInt()
        canvas.saveLayerAlpha(null, alpha)
        drawPlank(canvas, top, h)
        canvas.withClip(plank) { drawContent(this, now, top, h) }
        canvas.restore()
        canvas.restore()

        val animating = kind == BubblePane.Kind.Listening || kind == BubblePane.Kind.Writing || plankShown != target
        if (animating) postInvalidateOnAnimation()
    }

    private fun drawPlank(canvas: Canvas, top: Float, h: Float) {
        val corner = px(12f)
        val shadow = px(SHADOW_DP)
        fill.shader = null
        fill.color = INK
        canvas.withTranslation(shadow, shadow) { drawRoundRect(plank, corner, corner, fill) }
        fill.shader = plankShader(top, h)
        canvas.drawRoundRect(plank, corner, corner, fill)
        fill.shader = null
        canvas.drawRoundRect(plank, corner, corner, outline)
        fill.color = RIVET
        canvas.drawCircle(plank.left + px(8f), top + h / 2f, px(2.5f), fill)
        canvas.drawCircle(plank.right - px(8f), top + h / 2f, px(2.5f), fill)
    }

    private fun drawContent(canvas: Canvas, now: Long, top: Float, h: Float) {
        val cy = top + h / 2f
        val baseline = cy - (text.ascent() + text.descent()) / 2f
        var x = plank.left + px(PANE_PAD_DP)
        text.color = CREAM
        when (kind) {
            BubblePane.Kind.None -> Unit
            BubblePane.Kind.Listening -> {
                fill.color = ACCENT
                val barW = px(BAR_DP)
                for (i in 0 until BARS) {
                    val barH = px(4f) + (px(BAR_MAX_DP) - px(4f)) * levels[i]
                    canvas.drawRoundRect(x, cy - barH / 2f, x + barW, cy + barH / 2f, barW / 2f, barW / 2f, fill)
                    x += barW + px(BAR_GAP_DP)
                }
                x += px(INNER_GAP_DP) - px(BAR_GAP_DP)
                canvas.drawText(BubblePane.timer(now - recordingSince), x, baseline, text)
            }
            BubblePane.Kind.Writing -> {
                // A continuous sine wave, flowing toward the text.
                val length = px(WAVY_DP)
                val wavelength = px(WAVELENGTH_DP)
                val amplitude = px(4f)
                val phase = (now % WAVE_MS) / WAVE_MS.toFloat() * 2f * PI.toFloat()
                wavy.reset()
                var dx = 0f
                while (dx <= length) {
                    val y = cy + amplitude * sin(dx / wavelength * 2f * PI.toFloat() - phase)
                    if (dx == 0f) wavy.moveTo(x + dx, y) else wavy.lineTo(x + dx, y)
                    dx += px(1f)
                }
                canvas.drawPath(wavy, wave)
                x += length + px(INNER_GAP_DP)
                canvas.drawText(context.getString(R.string.pane_writing), x, baseline, text)
            }
            BubblePane.Kind.Typed -> {
                canvas.drawText(typedLabel, x, baseline, text)
                text.color = ACCENT
                canvas.drawText(undo(), plank.right - px(PANE_PAD_DP) - text.measureText(undo()), baseline, text)
            }
        }
    }

    private var shaderTop = Float.NaN

    private fun plankShader(top: Float, h: Float): LinearGradient {
        shader?.takeIf { shaderTop == top }?.let { return it }
        shaderTop = top
        return LinearGradient(
            0f,
            top,
            0f,
            top + h,
            intArrayOf(PLANK_TOP, PLANK_MID, PLANK_BOTTOM),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        ).also { shader = it }
    }

    private var downOnUndo = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downOnUndo = touchable && inUndo(event.x)
                return downOnUndo
            }
            MotionEvent.ACTION_UP -> {
                if (downOnUndo && inUndo(event.x)) listener.onUndo()
                downOnUndo = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> downOnUndo = false
        }
        return downOnUndo
    }

    /** The Undo end of the plank, generously. */
    private fun inUndo(x: Float): Boolean {
        val undoStart = plank.right - px(PANE_PAD_DP) - text.measureText(undo()) - px(INNER_GAP_DP) * 2
        return x >= undoStart && x <= plank.right + px(SHADOW_DP)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (kind != BubblePane.Kind.None && shown < 1f) slide(to = 1f)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        shown = 0f
        super.onDetachedFromWindow()
    }

    private fun px(value: Float) = value * resources.displayMetrics.density

    private fun px(value: Int) = value * resources.displayMetrics.density

    companion object {
        private const val RING_DP = 6
        private const val GAP_DP = 4
        private const val SHADOW_DP = 3
        private const val SLACK_DP = 2
        private const val PANE_HEIGHT_DP = 48
        private const val PANE_PAD_DP = 18
        private const val INNER_GAP_DP = 10
        private const val TEXT_DP = 14
        private const val BARS = 9
        private const val BAR_DP = 4
        private const val BAR_GAP_DP = 3
        private const val BAR_MAX_DP = 34
        private const val WAVY_DP = 40
        private const val WAVELENGTH_DP = 20
        private const val WAVE_MS = 900L
        private const val SHOW_MS = 320L
        private const val HIDE_MS = 200L
        private const val TIMER_WIDEST = "00:00"

        private const val ACCENT = 0xFFB6E08A.toInt()
        private const val INK = 0xFF050807.toInt()
        private const val CREAM = 0xFFFFF3D6.toInt()
        private const val RIVET = 0xFFECD093.toInt()
        private const val PLANK_TOP = 0xFF61432A.toInt()
        private const val PLANK_MID = 0xFF3F2915.toInt()
        private const val PLANK_BOTTOM = 0xFF2D1D0F.toInt()
    }
}
