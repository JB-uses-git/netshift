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

class HomeFragment : Fragment() {

    private lateinit var statusCard: MaterialCardView
    private lateinit var networkTypeText: TextView
    private lateinit var networkStatusText: TextView
    private lateinit var statusText: TextView

    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NetworkWatchService.ACTION_NETWORK_STATE) {
                val isFiveG = intent.getBooleanExtra(NetworkWatchService.EXTRA_IS_FIVE_G, false)
                updateCard(isFiveG)
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

    private fun resetCard() {
        statusCard.setCardBackgroundColor(Color.parseColor("#616161"))
        networkTypeText.text = "—"
        networkStatusText.text = "Waiting for status…"
    }
}
