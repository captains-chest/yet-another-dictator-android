package com.captainduckay.dictator.android

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that shows or hides the floating microphone — the same toggle as
 * "Floating mic" in [MainActivity] setup ([FloatingButtonService.show] / [FloatingButtonService.hide]).
 */
class FloatingMicTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh(FloatingButtonService.running.value)
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            if (FloatingButtonService.running.value) {
                FloatingButtonService.hide(this)
            } else {
                // Notifications permission stays in setup; the foreground service can start without it.
                FloatingButtonService.show(this)
            }
        }
    }

    private fun refresh(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_floating_mic)
        tile.contentDescription =
            getString(if (running) R.string.setup_button_on else R.string.setup_button_off)
        tile.updateTile()
    }

    companion object {
        /** Ask the system to bind briefly so [onStartListening] can mirror [FloatingButtonService.running]. */
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, FloatingMicTileService::class.java))
            } catch (_: IllegalArgumentException) {
                // Tile not added to Quick Settings yet.
            } catch (_: SecurityException) {
                // Not allowed to update from this context.
            }
        }
    }
}
