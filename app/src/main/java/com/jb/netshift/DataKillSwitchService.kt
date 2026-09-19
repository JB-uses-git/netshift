package com.jb.netshift

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class DataKillSwitchService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopKillSwitch()
            return START_NOT_STICKY
        }

        if (vpnInterface != null) {
            // Already active, don't re-establish
            return START_STICKY
        }

        return try {
            vpnInterface = Builder()
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .setSession("NetShift Kill Switch")
                .setBlocking(false)
                .establish()

            if (vpnInterface != null) {
                isActive = true
                startForeground(NOTIF_ID_KILLSWITCH, buildKillSwitchNotification())
                broadcastKillSwitchState(true)
            } else {
                // VPN consent not granted — can't establish
                stopSelf()
            }

            START_STICKY
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun stopKillSwitch() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vpnInterface = null
        isActive = false
        broadcastKillSwitchState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun broadcastKillSwitchState(active: Boolean) {
        val intent = Intent(ACTION_KILLSWITCH_STATE).apply {
            putExtra(EXTRA_KILLSWITCH_ACTIVE, active)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun buildKillSwitchNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val unblockIntent = Intent(this, DataKillSwitchService::class.java).apply {
            action = ACTION_STOP
        }
        val unblockPendingIntent = android.app.PendingIntent.getService(
            this,
            1,
            unblockIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NetworkWatchService.STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("4G Data Blocked")
            .setContentText("Mobile data paused — waiting for 5G to return")
            .setColor(Color.parseColor("#E65100"))
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "⚡ Unblock 4G",
                unblockPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    override fun onDestroy() {
        stopKillSwitch()
        super.onDestroy()
    }

    override fun onRevoke() {
        // Called when user revokes VPN permission from system settings
        stopKillSwitch()
    }

    companion object {
        const val NOTIF_ID_KILLSWITCH = 2
        const val ACTION_KILLSWITCH_STATE = "com.jb.netshift.KILLSWITCH_STATE"
        const val EXTRA_KILLSWITCH_ACTIVE = "killswitch_active"
        const val ACTION_STOP = "com.jb.netshift.STOP_KILLSWITCH"

        @Volatile
        var isActive: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, DataKillSwitchService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DataKillSwitchService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
