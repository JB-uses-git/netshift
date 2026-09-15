package com.jb.netshift

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class NetworkWatchService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private var telephonyCallback: TelephonyCallback? = null
    private var currentIsFiveG: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID_STATUS, buildStatusNotification(null))
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        registerCallback()
    }

    private fun registerCallback() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf(); return
        }

        telephonyCallback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) = handleDisplayInfo(info)
        }
        telephonyManager.registerTelephonyCallback(Executors.newSingleThreadExecutor(), telephonyCallback!!)
    }

    private fun handleDisplayInfo(info: TelephonyDisplayInfo) {
        val isFiveG = when (info.overrideNetworkType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> true
            else -> info.networkType == TelephonyManager.NETWORK_TYPE_NR
        }

        if (currentIsFiveG != isFiveG) {
            currentIsFiveG = isFiveG
            updateStatusNotification(isFiveG)
        }
    }

    private fun updateStatusNotification(isFiveG: Boolean) {
        val notification = buildStatusNotification(isFiveG)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID_STATUS, notification)
    }

    private fun buildStatusNotification(isFiveG: Boolean?): android.app.Notification {
        val (title, text, color, icon) = when (isFiveG) {
            true -> listOf(
                "Connected: 5G",
                "You're on 5G. Unlimited data active.",
                Color.parseColor("#2E7D32"),
                android.R.drawable.presence_online
            )
            false -> listOf(
                "Fallback: 4G",
                "Dropped to 4G. Watch your daily quota.",
                Color.parseColor("#C62828"),
                android.R.drawable.presence_busy
            )
            null -> listOf(
                "NetShift is watching your network type",
                "Waiting for first reading…",
                Color.parseColor("#616161"),
                android.R.drawable.stat_notify_sync
            )
        }

        return NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(icon as Int)
            .setContentTitle(title as String)
            .setContentText(text as String)
            .setColor(color as Int)
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            STATUS_CHANNEL_ID,
            "Live network status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val STATUS_CHANNEL_ID = "netshift_status"
        const val NOTIF_ID_STATUS = 1
    }
}