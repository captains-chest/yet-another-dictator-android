package com.captainduckay.dictator.android

import android.os.SystemClock

/**
 * The floating button and the accessibility service share one process. This is the only link
 * between them. Nothing read from another app is stored here.
 */
internal object FieldBridge {
    /** How long our own typing is ignored, so it is not mistaken for the user moving the cursor. */
    const val OWN_WRITE_GRACE_MS = 1_000L

    var onExternalEdit: (() -> Unit)? = null

    @Volatile private var ignoreUntilElapsed = 0L

    fun markOwnWrite() {
        ignoreUntilElapsed = SystemClock.elapsedRealtime() + OWN_WRITE_GRACE_MS
    }

    fun externalEdit() {
        if (SystemClock.elapsedRealtime() < ignoreUntilElapsed) return
        onExternalEdit?.invoke()
    }
}
