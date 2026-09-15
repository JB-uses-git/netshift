package com.jb.netshift

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var statusCard: MaterialCardView
    private lateinit var networkTypeText: TextView
    private lateinit var networkStatusText: TextView
    private lateinit var statusText: TextView

    private lateinit var dataSessionStatus: TextView
    private lateinit var dataUsageValue: TextView
    private lateinit var dataUsageSubtitle: TextView

    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NetworkWatchService.ACTION_NETWORK_STATE) {
                val isFiveG = intent.getBooleanExtra(NetworkWatchService.EXTRA_IS_FIVE_G, false)
                val bytesUsed4G = intent.getLongExtra(NetworkWatchService.EXTRA_BYTES_USED_4G, 0L)
                updateCard(isFiveG)
                updateDataUsage(isFiveG, bytesUsed4G)
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
        networkTypeText = view.findViewById(R.id.networkTypeText)
        networkStatusText = view.findViewById(R.id.networkStatusText)
        statusText = view.findViewById(R.id.statusText)

        dataSessionStatus = view.findViewById(R.id.dataSessionStatus)
        dataUsageValue = view.findViewById(R.id.dataUsageValue)
        dataUsageSubtitle = view.findViewById(R.id.dataUsageSubtitle)

        view.findViewById<View>(R.id.startButton).setOnClickListener {
            (activity as? MainActivity)?.requestPermissionsAndStart()
        }

        view.findViewById<View>(R.id.stopButton).setOnClickListener {
            activity?.let {
                it.stopService(Intent(it, NetworkWatchService::class.java))
                statusText.text = "Stopped."
                resetCard()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(NetworkWatchService.ACTION_NETWORK_STATE)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(networkReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(networkReceiver)
    }

    fun updateStatusText(text: String) {
        if (::statusText.isInitialized) {
            statusText.text = text
        }
    }

    private fun updateCard(isFiveG: Boolean) {
        if (isFiveG) {
            statusCard.setCardBackgroundColor(Color.parseColor("#2E7D32"))
            networkTypeText.text = "5G"
            networkStatusText.text = "You're on 5G. Unlimited data active."
        } else {
            statusCard.setCardBackgroundColor(Color.parseColor("#C62828"))
            networkTypeText.text = "4G"
            networkStatusText.text = "Dropped to 4G. Watch your daily quota."
        }
    }

    private fun updateDataUsage(isFiveG: Boolean, bytes: Long) {
        val mb = bytes / (1024.0 * 1024.0)
        if (isFiveG) {
            dataSessionStatus.text = "Unlimited 5G"
            dataSessionStatus.setTextColor(Color.parseColor("#2E7D32"))
            dataUsageValue.text = "0.0 MB"
            dataUsageSubtitle.text = "Connected to 5G (No quota consumed)"
        } else {
            dataSessionStatus.text = "Quota Active (4G)"
            dataSessionStatus.setTextColor(Color.parseColor("#C62828"))
            dataUsageValue.text = String.format(Locale.US, "%.2f MB", mb)
            dataUsageSubtitle.text = "Mobile data used since last 4G fallback"
        }
    }

    private fun resetCard() {
        statusCard.setCardBackgroundColor(Color.parseColor("#616161"))
        networkTypeText.text = "—"
        networkStatusText.text = "Waiting for status…"
        dataSessionStatus.text = "Inactive"
        dataSessionStatus.setTextColor(Color.parseColor("#616161"))
        dataUsageValue.text = "0.0 MB"
        dataUsageSubtitle.text = "Service stopped"
    }
}
