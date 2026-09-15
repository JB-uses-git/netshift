package com.jb.netshift

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class NetworkWatchService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private var telephonyCallback: TelephonyCallback? = null
    private var lastWasFiveG = false
    private var lastAlertAt = 0L
    private val cooldownMs = 15_000L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIF_ID_SERVICE, buildServiceNotification())
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

        if (lastWasFiveG && !isFiveG) fireAlert()
        lastWasFiveG = isFiveG
    }

    private fun fireAlert() {
        val now = System.currentTimeMillis()
        if (now - lastAlertAt < cooldownMs) return
        lastAlertAt = now

        val vibrator = if (Build.VERSION.SDK_INT >= 31)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else getSystemService(VIBRATOR_SERVICE) as Vibrator

        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Dropped to 4G")
            .setContentText("Switch back to 5G now, or your daily 4G quota takes the hit.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID_ALERT, notification)
    }

    private fun buildServiceNotification() =
        NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("NetShift is watching your network type")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(SERVICE_CHANNEL_ID, "Background watcher", NotificationManager.IMPORTANCE_LOW)
        )

        val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "5G drop alerts", NotificationManager.IMPORTANCE_HIGH)
        alertChannel.setSound(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        nm.createNotificationChannel(alertChannel)
    }

    override fun onDestroy() {
        telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val SERVICE_CHANNEL_ID = "netshift_service"
        const val ALERT_CHANNEL_ID = "netshift_alert"
        const val NOTIF_ID_SERVICE = 1
        const val NOTIF_ID_ALERT = 2
    }
}