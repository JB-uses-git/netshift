package com.jb.netshift

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.jb.netshift.data.AppDatabase
import java.util.Locale
import java.util.concurrent.Executors

class HomeFragment : Fragment() {

    private lateinit var statusCard: MaterialCardView
    private lateinit var badgeContainer: View
    private lateinit var badgeIcon: ImageView
    private lateinit var statusBadgeText: TextView
    private lateinit var networkTypeText: TextView
    private lateinit var networkStatusText: TextView
    private lateinit var btnEmergencyUnblock: MaterialButton
    private lateinit var btnResumeBlock: MaterialButton
    private lateinit var serviceStatusChip: TextView

    private lateinit var dataUsageCard: MaterialCardView
    private lateinit var dataSessionStatus: TextView
    private lateinit var dataUsageValue: TextView
    private lateinit var dataQuotaTotal: TextView
    private lateinit var dataQuotaRemaining: TextView
    private lateinit var quotaProgressBar: LinearProgressIndicator
    private lateinit var dataUsageSubtitle: TextView

    private lateinit var textStatsFallbacks: TextView
    private lateinit var textStatsProtectedTime: TextView
    private lateinit var textStatsQuotaSaved: TextView

    private lateinit var statusText: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton

    private var lastIsFiveG: Boolean? = null
    private var lastIsWifi: Boolean = false
    private var lastBytes4G: Long = 0L
    private val dbExecutor = Executors.newSingleThreadExecutor()

    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NetworkWatchService.ACTION_NETWORK_STATE) {
                val isFiveG = intent.getBooleanExtra(NetworkWatchService.EXTRA_IS_FIVE_G, false)
                val bytesUsed4G = intent.getLongExtra(NetworkWatchService.EXTRA_BYTES_USED_4G, 0L)
                val isWifi = intent.getBooleanExtra(NetworkWatchService.EXTRA_IS_WIFI, false)
                lastIsFiveG = isFiveG
                lastIsWifi = isWifi
                lastBytes4G = bytesUsed4G

                updateUIState()
                loadStats()
            }
        }
    }

    private val killSwitchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DataKillSwitchService.ACTION_KILLSWITCH_STATE) {
                updateUIState()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusCard = view.findViewById(R.id.statusCard)
        badgeContainer = view.findViewById(R.id.badgeContainer)
        badgeIcon = view.findViewById(R.id.badgeIcon)
        statusBadgeText = view.findViewById(R.id.statusBadgeText)
        networkTypeText = view.findViewById(R.id.networkTypeText)
        networkStatusText = view.findViewById(R.id.networkStatusText)
        btnEmergencyUnblock = view.findViewById(R.id.btnEmergencyUnblock)
        btnResumeBlock = view.findViewById(R.id.btnResumeBlock)
        serviceStatusChip = view.findViewById(R.id.serviceStatusChip)

        dataUsageCard = view.findViewById(R.id.dataUsageCard)
        dataSessionStatus = view.findViewById(R.id.dataSessionStatus)
        dataUsageValue = view.findViewById(R.id.dataUsageValue)
        dataQuotaTotal = view.findViewById(R.id.dataQuotaTotal)
        dataQuotaRemaining = view.findViewById(R.id.dataQuotaRemaining)
        quotaProgressBar = view.findViewById(R.id.quotaProgressBar)
        dataUsageSubtitle = view.findViewById(R.id.dataUsageSubtitle)

        textStatsFallbacks = view.findViewById(R.id.textStatsFallbacks)
        textStatsProtectedTime = view.findViewById(R.id.textStatsProtectedTime)
        textStatsQuotaSaved = view.findViewById(R.id.textStatsQuotaSaved)

        statusText = view.findViewById(R.id.statusText)
        startButton = view.findViewById(R.id.startButton)
        stopButton = view.findViewById(R.id.stopButton)

        btnEmergencyUnblock.setOnClickListener {
            context?.let { ctx ->
                DataKillSwitchService.stop(ctx)
                Toast.makeText(ctx, "⚡ 4G unblocked for this session", Toast.LENGTH_SHORT).show()
                updateUIState()
            }
        }

        btnResumeBlock.setOnClickListener {
            context?.let { ctx ->
                DataKillSwitchService.start(ctx)
                Toast.makeText(ctx, "🛡️ Data protection re-engaged", Toast.LENGTH_SHORT).show()
                updateUIState()
            }
        }

        startButton.setOnClickListener {
            (activity as? MainActivity)?.requestPermissionsAndStart()
        }

        stopButton.setOnClickListener {
            activity?.let {
                it.stopService(Intent(it, NetworkWatchService::class.java))
                if (DataKillSwitchService.isActive) {
                    DataKillSwitchService.stop(it)
                }
                statusText.text = "Monitoring paused"
                updateServiceChip(isRunning = false)
                resetCard()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val lbm = LocalBroadcastManager.getInstance(requireContext())
        lbm.registerReceiver(networkReceiver, IntentFilter(NetworkWatchService.ACTION_NETWORK_STATE))
        lbm.registerReceiver(killSwitchReceiver, IntentFilter(DataKillSwitchService.ACTION_KILLSWITCH_STATE))

        updateServiceChip(NetworkWatchService.isRunning)
        updateUIState()
        loadStats()
    }

    override fun onPause() {
        super.onPause()
        val lbm = LocalBroadcastManager.getInstance(requireContext())
        lbm.unregisterReceiver(networkReceiver)
        lbm.unregisterReceiver(killSwitchReceiver)
    }

    fun updateStatusText(text: String) {
        if (::statusText.isInitialized) {
            statusText.text = text
        }
    }

    private fun updateServiceChip(isRunning: Boolean) {
        if (!::serviceStatusChip.isInitialized) return
        if (isRunning) {
            serviceStatusChip.text = "MONITORING"
            serviceStatusChip.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
            statusText.text = "Actively monitoring network type"
        } else {
            serviceStatusChip.text = "STOPPED"
            serviceStatusChip.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#757575"))
            statusText.text = "Monitoring is stopped"
        }
    }

    private fun updateUIState() {
        val isBlocked = DataKillSwitchService.isActive
        val isFiveG = lastIsFiveG
        val isWifi = lastIsWifi

        if (isWifi) {
            // Wi-Fi connected state
            statusCard.setCardBackgroundColor(Color.parseColor("#1565C0"))
            statusBadgeText.text = "WI-FI ACTIVE"
            badgeIcon.setImageResource(R.drawable.ic_wifi)
            networkTypeText.text = "Wi-Fi"
            networkStatusText.text = "Connected to Wi-Fi. 4G mobile quota is untouched."
            btnEmergencyUnblock.visibility = View.GONE
            btnResumeBlock.visibility = View.GONE

            dataSessionStatus.text = "Wi-Fi Active"
            dataSessionStatus.setTextColor(Color.parseColor("#1565C0"))
            dataUsageSubtitle.text = "Mobile network is idle"
        } else if (isFiveG == true) {
            // 5G Active state
            statusCard.setCardBackgroundColor(Color.parseColor("#2E7D32"))
            statusBadgeText.text = "5G UNLIMITED"
            badgeIcon.setImageResource(R.drawable.ic_circle_indicator)
            networkTypeText.text = "5G"
            networkStatusText.text = "You're on 5G. Unlimited high-speed data active."
            btnEmergencyUnblock.visibility = View.GONE
            btnResumeBlock.visibility = View.GONE

            dataSessionStatus.text = "Unlimited 5G"
            dataSessionStatus.setTextColor(Color.parseColor("#2E7D32"))
            dataUsageSubtitle.text = "Connected to 5G (No quota consumed)"
        } else if (isFiveG == false) {
            // 4G Fallback state
            if (isBlocked) {
                // 4G Blocked
                statusCard.setCardBackgroundColor(Color.parseColor("#E65100"))
                statusBadgeText.text = "4G DATA BLOCKED"
                badgeIcon.setImageResource(R.drawable.ic_shield)
                networkTypeText.text = "4G"
                networkStatusText.text = "Mobile data paused to protect your daily quota."
                btnEmergencyUnblock.visibility = View.VISIBLE
                btnResumeBlock.visibility = View.GONE

                dataSessionStatus.text = "Data Blocked"
                dataSessionStatus.setTextColor(Color.parseColor("#E65100"))
                dataUsageSubtitle.text = "Kill-switch black-hole active — 0 bytes wasted"
            } else {
                // 4G Unblocked / Active
                statusCard.setCardBackgroundColor(Color.parseColor("#C62828"))
                statusBadgeText.text = "4G CONSUMING QUOTA"
                badgeIcon.setImageResource(R.drawable.ic_bolt)
                networkTypeText.text = "4G"
                networkStatusText.text = "Dropped to 4G. Watch your daily quota closely."
                btnEmergencyUnblock.visibility = View.GONE
                btnResumeBlock.visibility = View.VISIBLE

                dataSessionStatus.text = "Quota Active (4G)"
                dataSessionStatus.setTextColor(Color.parseColor("#C62828"))
                dataUsageSubtitle.text = "Mobile data active on 4G carrier network"
            }
        }

        updateQuotaSection(lastBytes4G)
    }

    private fun updateQuotaSection(bytesUsed: Long) {
        val prefs = requireContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val dailyQuotaMb = prefs.getLong(NetworkWatchService.KEY_DAILY_QUOTA_MB, NetworkWatchService.DEFAULT_DAILY_QUOTA_MB)

        val mbUsed = bytesUsed / (1024.0 * 1024.0)
        val quotaGb = dailyQuotaMb / 1024.0
        val remainingMb = (dailyQuotaMb - mbUsed).coerceAtLeast(0.0)

        dataUsageValue.text = String.format(Locale.US, "%.1f MB", mbUsed)
        dataQuotaTotal.text = String.format(Locale.US, "/ %.1f GB", quotaGb)

        if (remainingMb >= 1024) {
            dataQuotaRemaining.text = String.format(Locale.US, "%.2f GB left", remainingMb / 1024.0)
        } else {
            dataQuotaRemaining.text = String.format(Locale.US, "%.0f MB left", remainingMb)
        }

        val progress = ((mbUsed / dailyQuotaMb) * 100).toInt().coerceIn(0, 100)
        quotaProgressBar.progress = progress

        val indicatorColor = when {
            progress < 70 -> Color.parseColor("#2E7D32")
            progress < 90 -> Color.parseColor("#F57C00")
            else -> Color.parseColor("#D32F2F")
        }
        quotaProgressBar.setIndicatorColor(indicatorColor)
    }

    private fun loadStats() {
        dbExecutor.execute {
            try {
                val db = AppDatabase.getDatabase(requireContext().applicationContext)
                val fallbackEvents = db.networkEventDao().get4GEvents()
                val count = fallbackEvents.size

                activity?.runOnUiThread {
                    if (::textStatsFallbacks.isInitialized) {
                        textStatsFallbacks.text = count.toString()
                        textStatsProtectedTime.text = "${count * 4}m"
                        textStatsQuotaSaved.text = "${(count * 15).coerceAtLeast(0)} MB"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resetCard() {
        statusCard.setCardBackgroundColor(Color.parseColor("#616161"))
        statusBadgeText.text = "OFFLINE / IDLE"
        networkTypeText.text = "—"
        networkStatusText.text = "Waiting for monitoring to start…"
        btnEmergencyUnblock.visibility = View.GONE
        btnResumeBlock.visibility = View.GONE

        dataSessionStatus.text = "Inactive"
        dataSessionStatus.setTextColor(Color.parseColor("#616161"))
        dataUsageValue.text = "0.0 MB"
        dataUsageSubtitle.text = "Service stopped"
        quotaProgressBar.progress = 0
        lastIsFiveG = null
    }
}
