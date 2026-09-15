package com.jb.netshift

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jb.netshift.data.NetworkEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkEventAdapter(
    private var events: List<NetworkEvent> = emptyList()
) : RecyclerView.Adapter<NetworkEventAdapter.EventViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy  HH:mm:ss", Locale.getDefault())

    fun submitList(newEvents: List<NetworkEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_network_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    override fun getItemCount(): Int = events.size

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val indicatorDot: View = itemView.findViewById(R.id.indicatorDot)
        private val eventTitle: TextView = itemView.findViewById(R.id.eventTitle)
        private val eventTime: TextView = itemView.findViewById(R.id.eventTime)
        private val eventBadge: TextView = itemView.findViewById(R.id.eventBadge)

        fun bind(event: NetworkEvent) {
            val colorGreen = Color.parseColor("#2E7D32")
            val colorRed = Color.parseColor("#C62828")

            if (event.isFiveG) {
                eventTitle.text = if (event.previousIsFiveG == false) "4G → 5G (Restored)" else "Connected to 5G"
                eventBadge.text = "5G"
                setIndicatorAndBadgeColor(colorGreen)
            } else {
                eventTitle.text = if (event.previousIsFiveG == true) "5G → 4G (Fallback)" else "Connected to 4G"
                eventBadge.text = "4G"
                setIndicatorAndBadgeColor(colorRed)
            }

            eventTime.text = dateFormat.format(Date(event.timestamp))
        }

        private fun setIndicatorAndBadgeColor(color: Int) {
            val dotDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            indicatorDot.background = dotDrawable

            val badgeDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(color)
            }
            eventBadge.background = badgeDrawable
        }
    }
}
