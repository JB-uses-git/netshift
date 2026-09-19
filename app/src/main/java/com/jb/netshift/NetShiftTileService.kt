package com.jb.netshift

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class NetShiftTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (NetworkWatchService.isRunning) {
            stopService(Intent(this, NetworkWatchService::class.java))
            if (DataKillSwitchService.isActive) {
                DataKillSwitchService.stop(this)
            }
        } else {
            startForegroundService(Intent(this, NetworkWatchService::class.java))
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = NetworkWatchService.isRunning

        if (isRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "NetShift"
            tile.subtitle = "Monitoring"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "NetShift"
            tile.subtitle = "Off"
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.updateTile()
    }
}
