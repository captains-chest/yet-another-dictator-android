package com.captainduckay.dictator.android

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The scalloped "cookie" outline of the floating button and the decorative blob (ADR 0009). */
internal object Cookie {
    const val LOBES = 9
    const val DEPTH = 0.075f
    private const val STEPS = 180

    fun path(cx: Float, cy: Float, radius: Float, lobes: Int = LOBES, depth: Float = DEPTH, into: Path = Path()): Path {
        into.rewind()
        for (i in 0..STEPS) {
            val t = i.toDouble() / STEPS * 2 * PI
            val r = radius * (1 - depth + depth * cos(lobes * t)).toFloat()
            val x = cx + r * cos(t).toFloat()
            val y = cy + r * sin(t).toFloat()
            if (i == 0) into.moveTo(x, y) else into.lineTo(x, y)
        }
        into.close()
        return into
    }
}
