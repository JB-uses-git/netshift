package com.jb.netshift

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.TrafficStats
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.jb.netshift.data.AppDatabase
import com.jb.netshift.data.NetworkEvent
import java.util.concurrent.Executors

class NetworkWatchService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private var telephonyCallback: TelephonyCallback? = null
    private var currentIsFiveG: Boolean? = null
    private val dbExecutor = Executors.newSingleThreadExecutor()
    private val usageHandler = Handler(Looper.getMainLooper())

    private val usageRunnable = object : Runnable {
        override fun run() {
            if (currentIsFiveG == false) {
                broadcastState(false)
                usageHandler.postDelayed(this, 3000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID_STATUS, buildStatusNotification(null))
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        registerCallback()
        updateWidgetState(isRunning = true, isFiveG = null)
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
            val previous = currentIsFiveG
            currentIsFiveG = isFiveG

            if (!isFiveG) {
                // Switched or started on 4G -> snapshot traffic
                snapshotTrafficStats()
                usageHandler.removeCallbacks(usageRunnable)
                usageHandler.post(usageRunnable)
            } else {
                usageHandler.removeCallbacks(usageRunnable)
            }

            updateStatusNotification(isFiveG)
            broadcastState(isFiveG)
            updateWidgetState(isRunning = true, isFiveG = isFiveG)
            logEventToDatabase(isFiveG, previous)
        }
    }

    private fun snapshotTrafficStats() {
        val totalBytes = getTotalMobileBytes()
        getSharedPreferences(PREFS_STATS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_4G_START_BYTES, totalBytes)
            .putLong(KEY_4G_START_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun getTotalMobileBytes(): Long {
        val rx = TrafficStats.getMobileRxBytes()
        val tx = TrafficStats.getMobileTxBytes()
        return if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            0L
        } else {
            rx + tx
        }
    }

    private fun logEventToDatabase(isFiveG: Boolean, previousIsFiveG: Boolean?) {
        dbExecutor.execute {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.networkEventDao().insert(
                    NetworkEvent(
                        isFiveG = isFiveG,
                        previousIsFiveG = previousIsFiveG
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun broadcastState(isFiveG: Boolean) {
        val prefs = getSharedPreferences(PREFS_STATS, Context.MODE_PRIVATE)
        val startBytes = prefs.getLong(KEY_4G_START_BYTES, 0L)
        val currentBytes = getTotalMobileBytes()
        val bytesUsedOn4G = if (!isFiveG && startBytes > 0 && currentBytes >= startBytes) {
            currentBytes - startBytes
        } else {
            0L
        }

        val intent = Intent(ACTION_NETWORK_STATE).apply {
            putExtra(EXTRA_IS_FIVE_G, isFiveG)
            putExtra(EXTRA_BYTES_USED_4G, bytesUsedOn4G)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun updateWidgetState(isRunning: Boolean, isFiveG: Boolean?) {
        getSharedPreferences(NetShiftWidget.PREFS_WIDGET, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (isFiveG != null) putBoolean(NetShiftWidget.KEY_IS_FIVE_G, isFiveG)
            }
            .apply()

        val intent = Intent(this, NetShiftWidget::class.java).apply {
            action = NetShiftWidget.ACTION_UPDATE_WIDGET_STATE
            putExtra(NetShiftWidget.EXTRA_IS_RUNNING, isRunning)
            if (isFiveG != null) putExtra(NetShiftWidget.EXTRA_IS_FIVE_G, isFiveG)
        }
        sendBroadcast(intent)
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
        isRunning = false
        updateWidgetState(isRunning = false, isFiveG = null)
        usageHandler.removeCallbacks(usageRunnable)
        telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val STATUS_CHANNEL_ID = "netshift_status"
        const val NOTIF_ID_STATUS = 1
        const val ACTION_NETWORK_STATE = "com.jb.netshift.NETWORK_STATE"
        const val EXTRA_IS_FIVE_G = "isFiveG"
        const val EXTRA_BYTES_USED_4G = "bytesUsed4G"

        const val PREFS_STATS = "netshift_data_stats"
        const val KEY_4G_START_BYTES = "snap_bytes_4g"
        const val KEY_4G_START_TIME = "snap_time_4g"

        @Volatile
        var isRunning: Boolean = false
    }
}