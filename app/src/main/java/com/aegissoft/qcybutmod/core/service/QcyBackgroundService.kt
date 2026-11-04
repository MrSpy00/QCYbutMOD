package com.aegissoft.qcybutmod.core.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aegissoft.qcybutmod.R
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.core.models.AncMode
import com.aegissoft.qcybutmod.ui.MainActivity
import kotlinx.coroutines.*

class QcyBackgroundService : Service() {

    companion object {
        const val CHANNEL_ID = "qcy_background_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "QcyBackgroundService"

        const val ACTION_CYCLE_ANC = "com.aegissoft.qcybutmod.ACTION_CYCLE_ANC"
        const val ACTION_TOGGLE_GAME = "com.aegissoft.qcybutmod.ACTION_TOGGLE_GAME"
        const val ACTION_DISCONNECT = "com.aegissoft.qcybutmod.ACTION_DISCONNECT"
        const val ACTION_RECONNECT = "com.aegissoft.qcybutmod.ACTION_RECONNECT"

        fun startService(context: Context) {
            val intent = Intent(context, QcyBackgroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Android 14+ connectedDevice FGS için uygun izin yoksa fırlatır;
                // bildirimsiz devam et, asla çökme.
                Log.w(TAG, "FGS başlatılamadı, servis atlanıyor", e)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, QcyBackgroundService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var bleManager: BleConnectionManager

    override fun onCreate() {
        super.onCreate()
        bleManager = BleConnectionManager.getInstance(applicationContext)
        createNotificationChannel()

        val initialNotif = buildNotification(
            content = "L: --  •  R: --  •  Kutu: --",
            title = "QCYbutMOD • Bağlı Değil",
            subText = "Beklemede",
            isConnected = false
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } catch (e: Exception) {
                // Android 14+ izin yoksa fırlatır; servis arka planda bildirimsiz devam etsin, çökme yok.
                Log.w(TAG, "startForeground başarısız", e)
            }
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        serviceScope.launch {
            bleManager.deviceState.collect { state ->
                if (state.isConnected) {
                    val leftLvl = state.battery.leftLevel
                    val rightLvl = state.battery.rightLevel
                    val caseLvl = state.battery.caseLevel

                    val leftStr = if (leftLvl in 0..100) "%$leftLvl" else "--"
                    val rightStr = if (rightLvl in 0..100) "%$rightLvl" else "--"
                    val caseStr = if (caseLvl in 0..100) "%$caseLvl" else "--"

                    val ancStr = when (state.ancMode) {
                        AncMode.OFF -> "Normal Mod"
                        AncMode.ANC -> if (state.isAiAdaptiveAncOn) "AI Adaptif ANC" else "ANC (${state.ancSubScene.title})"
                        AncMode.TRANSPARENCY -> "Şeffaf Mod (Seviye ${state.transparencyLevel})"
                    }

                    val title = "${state.modelName} • $ancStr"
                    val content = "Sol: $leftStr  •  Sağ: $rightStr  •  Kutu: $caseStr"
                    val subText = if (state.isGameModeOn) "Oyun Modu Açık" else "QCYbutMOD"

                    val updatedNotification = buildNotification(
                        content = content,
                        title = title,
                        subText = subText,
                        currentAnc = state.ancMode,
                        isGameOn = state.isGameModeOn,
                        isConnected = true
                    )
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, updatedNotification)
                } else {
                    val title = "${state.modelName.ifBlank { "QCYbutMOD" }} • Bağlı Değil"
                    val content = "Sol: --  •  Sağ: --  •  Kutu: --"
                    val subText = "Bağlantı Kesildi"

                    val updatedNotification = buildNotification(
                        content = content,
                        title = title,
                        subText = subText,
                        currentAnc = AncMode.OFF,
                        isGameOn = false,
                        isConnected = false
                    )
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, updatedNotification)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CYCLE_ANC -> {
                val current = bleManager.deviceState.value.ancMode
                val next = when (current) {
                    AncMode.OFF -> AncMode.ANC
                    AncMode.ANC -> AncMode.TRANSPARENCY
                    AncMode.TRANSPARENCY -> AncMode.OFF
                }
                bleManager.setAnc(next)
            }
            ACTION_TOGGLE_GAME -> {
                val current = bleManager.deviceState.value.isGameModeOn
                bleManager.setGameMode(!current)
            }
            ACTION_DISCONNECT -> {
                bleManager.disconnect()
            }
            ACTION_RECONNECT -> {
                bleManager.reconnectSelectedDevice()
            }
        }
        return START_STICKY
    }

    private fun buildNotification(
        content: String,
        title: String,
        subText: String? = null,
        currentAnc: AncMode = AncMode.OFF,
        isGameOn: Boolean = false,
        isConnected: Boolean = false
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val ancIntent = Intent(this, QcyBackgroundService::class.java).apply {
            action = ACTION_CYCLE_ANC
        }
        val pAncIntent = PendingIntent.getService(
            this, 1, ancIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val gameIntent = Intent(this, QcyBackgroundService::class.java).apply {
            action = ACTION_TOGGLE_GAME
        }
        val pGameIntent = PendingIntent.getService(
            this, 2, gameIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val discIntent = Intent(this, QcyBackgroundService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pDiscIntent = PendingIntent.getService(
            this, 3, discIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val reconnIntent = Intent(this, QcyBackgroundService::class.java).apply {
            action = ACTION_RECONNECT
        }
        val pReconnIntent = PendingIntent.getService(
            this, 4, reconnIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextAncText = when (currentAnc) {
            AncMode.OFF -> "Mod: ANC"
            AncMode.ANC -> "Mod: Şeffaf"
            AncMode.TRANSPARENCY -> "Mod: Normal"
        }

        val gameText = if (isGameOn) "Oyun: Açık" else "Oyun: Kapalı"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content).setBigContentTitle(title))
            .setSmallIcon(R.drawable.ic_anc_tile)
            .setContentIntent(pIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(0xFFFF1744.toInt())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (subText != null) {
            builder.setSubText(subText)
        }

        if (isConnected) {
            builder.addAction(R.drawable.ic_anc_tile, nextAncText, pAncIntent)
            builder.addAction(R.drawable.ic_anc_tile, gameText, pGameIntent)
            builder.addAction(R.drawable.ic_anc_tile, "Bağlantıyı Kes", pDiscIntent)
        } else {
            builder.addAction(R.drawable.ic_anc_tile, "Yeniden Bağlan", pReconnIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "QCYbutMOD Cihaz Durumu",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Bağlı QCY kulaklık durumu, anlık pil seviyeleri ve hızlı donanım kontrolleri"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
