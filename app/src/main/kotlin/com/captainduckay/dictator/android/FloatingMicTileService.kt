package com.captainduckay.dictator.android

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that shows or hides the floating microphone — the same toggle as
 * "Floating mic" in [MainActivity] setup ([FloatingButtonService.show] / [FloatingButtonService.hide]).
 */
class FloatingMicTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listenJob?.cancel()
        listenJob =
            scope.launch {
                FloatingButtonService.running.collect { refresh(it) }
            }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        listenJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val turningOn = !FloatingButtonService.running.value
            if (turningOn) {
                // Notifications permission stays in setup; the foreground service can start without it.
                FloatingButtonService.show(this)
            } else {
                FloatingButtonService.hide(this)
            }
            // Flip immediately; [onStartListening] collect confirms when the service catches up.
            refresh(turningOn)
        }
    }

    private fun refresh(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_floating_mic)
        tile.subtitle = getString(if (running) R.string.tile_floating_mic_on else R.string.tile_floating_mic_off)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_logo)
        tile.contentDescription =
            getString(R.string.tile_floating_mic) + ", " +
                getString(if (running) R.string.tile_floating_mic_on else R.string.tile_floating_mic_off)
        tile.updateTile()
    }
}
