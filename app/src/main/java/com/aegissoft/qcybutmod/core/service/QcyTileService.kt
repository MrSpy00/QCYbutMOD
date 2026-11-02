package com.aegissoft.qcybutmod.core.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.core.models.AncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QcyTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var bleManager: BleConnectionManager

    override fun onCreate() {
        super.onCreate()
        bleManager = BleConnectionManager.getInstance(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
        serviceScope.launch {
            bleManager.deviceState.collect {
                updateTileState()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val currentState = bleManager.deviceState.value
        if (!currentState.isConnected) {
            // If disconnected, try to scan and connect or open app
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // PendingIntent required on API 34+
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        this, 0, intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
            return
        }

        // Cycle through: ANC -> TRANSPARENCY -> OFF -> ANC
        val nextMode = when (currentState.ancMode) {
            AncMode.OFF -> AncMode.ANC
            AncMode.ANC -> AncMode.TRANSPARENCY
            AncMode.TRANSPARENCY -> AncMode.OFF
        }

        bleManager.setAnc(nextMode)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val state = bleManager.deviceState.value

        if (!state.isConnected) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "QCY ANC"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Bağlı Değil"
            }
        } else {
            tile.state = when (state.ancMode) {
                AncMode.OFF -> Tile.STATE_INACTIVE
                AncMode.ANC, AncMode.TRANSPARENCY -> Tile.STATE_ACTIVE
            }

            tile.label = "QCY ANC"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = when (state.ancMode) {
                    AncMode.OFF -> "Kapalı"
                    AncMode.ANC -> "ANC Açık (${state.ancSubScene.title})"
                    AncMode.TRANSPARENCY -> "Şeffaf (${state.transparencyLevel}. Seviye)"
                }
            }
        }
        tile.updateTile()
    }
}
