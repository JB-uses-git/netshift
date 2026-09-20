package com.jb.netshift

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class NetworkWatchService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private var telephonyCallback: TelephonyCallback? = null
    private lateinit var connectivityManager: ConnectivityManager
    private var isWifiConnected = false
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

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (isWifiConnected != hasWifi) {
                isWifiConnected = hasWifi
                if (isWifiConnected) {
                    // Wi-Fi connected: pause/stop killswitch so Wi-Fi traffic is never blocked
                    if (DataKillSwitchService.isActive) {
                        DataKillSwitchService.stop(this@NetworkWatchService)
                    }
                } else {
                    // Wi-Fi lost: If on 4G and auto-block enabled, engage kill-switch
                    if (currentIsFiveG == false) {
                        triggerKillSwitch(activate = true)
                    }
                }
                broadcastState(currentIsFiveG ?: false)
            }
        }

        override fun onLost(network: Network) {
            val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (isWifiConnected && !hasWifi) {
                isWifiConnected = false
                if (currentIsFiveG == false) {
                    triggerKillSwitch(activate = true)
                }
                broadcastState(currentIsFiveG ?: false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID_STATUS, buildStatusNotification(null))
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Check initial Wi-Fi capability & register callback defensively
        try {
            val currentNet = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(currentNet)
            isWifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
                // Trigger kill-switch if enabled, not on Wi-Fi, and on cellular
                triggerKillSwitch(activate = true)
                // Alert via vibration if user enabled it
                alertFallbackTo4G()
            } else {
                usageHandler.removeCallbacks(usageRunnable)
                // Stop kill-switch immediately on 5G return
                triggerKillSwitch(activate = false)
            }

            updateStatusNotification(isFiveG)
            broadcastState(isFiveG)
            updateWidgetState(isRunning = true, isFiveG = isFiveG)
            logEventToDatabase(isFiveG, previous)
        }
    }

    private fun alertFallbackTo4G() {
        val prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_VIBRATE_ALERT, true)) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 100, 250), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 250, 100, 250), -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerKillSwitch(activate: Boolean) {
        val prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val autoBlockEnabled = prefs.getBoolean(KEY_AUTO_BLOCK_4G, true)

        if (activate) {
            // Only engage killswitch if not on Wi-Fi and mobile data is on
            if (autoBlockEnabled && !isWifiConnected && isOnCellular()) {
                DataKillSwitchService.start(this)
            }
        } else {
            // Stop kill-switch on 5G return or Wi-Fi connected
            if (DataKillSwitchService.isActive) {
                DataKillSwitchService.stop(this)
            }
        }
    }

    private fun isOnCellular(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            e.printStackTrace()
            true
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
            putExtra(EXTRA_IS_WIFI, isWifiConnected)
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
        val killSwitchActive = DataKillSwitchService.isActive

        val (title, text, color, icon) = when (isFiveG) {
            true -> listOf(
                "Connected: 5G",
                "You're on 5G. Unlimited data active.",
                Color.parseColor("#2E7D32"),
                android.R.drawable.presence_online
            )
            false -> if (killSwitchActive) {
                listOf(
                    "Fallback: 4G — Data Blocked",
                    "Mobile data paused to protect your daily quota.",
                    Color.parseColor("#E65100"),
                    android.R.drawable.ic_lock_idle_lock
                )
            } else {
                listOf(
                    "Fallback: 4G",
                    "Dropped to 4G. Watch your daily quota.",
                    Color.parseColor("#C62828"),
                    android.R.drawable.presence_busy
                )
            }
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
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Safety: Ensure killswitch is stopped so internet isn't left blocked
        if (DataKillSwitchService.isActive) {
            DataKillSwitchService.stop(this)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val STATUS_CHANNEL_ID = "netshift_status"
        const val NOTIF_ID_STATUS = 1
        const val ACTION_NETWORK_STATE = "com.jb.netshift.NETWORK_STATE"
        const val EXTRA_IS_FIVE_G = "isFiveG"
        const val EXTRA_BYTES_USED_4G = "bytesUsed4G"
        const val EXTRA_IS_WIFI = "isWifi"

        const val PREFS_STATS = "netshift_data_stats"
        const val KEY_4G_START_BYTES = "snap_bytes_4g"
        const val KEY_4G_START_TIME = "snap_time_4g"
        const val KEY_AUTO_BLOCK_4G = "auto_block_4g"
        const val KEY_VIBRATE_ALERT = "vibrate_alert"
        const val KEY_DAILY_QUOTA_MB = "daily_quota_mb"
        const val DEFAULT_DAILY_QUOTA_MB = 1500L

        @Volatile
        var isRunning: Boolean = false
    }
}