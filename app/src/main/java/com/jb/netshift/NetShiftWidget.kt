package com.jb.netshift

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class NetShiftWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
        val isRunning = NetworkWatchService.isRunning
        val isFiveG = if (prefs.contains(KEY_IS_FIVE_G)) prefs.getBoolean(KEY_IS_FIVE_G, false) else null

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, isRunning, isFiveG)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET_STATE) {
            val isRunning = intent.getBooleanExtra(EXTRA_IS_RUNNING, false)
            val hasState = intent.hasExtra(EXTRA_IS_FIVE_G)
            val isFiveG = if (hasState) intent.getBooleanExtra(EXTRA_IS_FIVE_G, false) else null

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, NetShiftWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, isRunning, isFiveG)
            }
        }
    }

    companion object {
        const val PREFS_WIDGET = "netshift_widget_prefs"
        const val KEY_IS_FIVE_G = "last_is_five_g"

        const val ACTION_UPDATE_WIDGET_STATE = "com.jb.netshift.ACTION_UPDATE_WIDGET_STATE"
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_IS_FIVE_G = "extra_is_five_g"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            isRunning: Boolean,
            isFiveG: Boolean?
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Click opens MainActivity
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetContainer, pendingIntent)

            if (!isRunning) {
                views.setTextViewText(R.id.widgetNetworkType, "OFF")
                views.setTextViewText(R.id.widgetTitle, "NetShift")
                views.setTextViewText(R.id.widgetStatus, "Tap to start monitoring")
                views.setInt(R.id.widgetContainer, "setBackgroundColor", Color.parseColor("#424242"))
            } else {
                when (isFiveG) {
                    true -> {
                        views.setTextViewText(R.id.widgetNetworkType, "5G")
                        views.setTextViewText(R.id.widgetTitle, "Connected: 5G")
                        views.setTextViewText(R.id.widgetStatus, "Unlimited data active")
                        views.setInt(R.id.widgetContainer, "setBackgroundColor", Color.parseColor("#2E7D32"))
                    }
                    false -> {
                        views.setTextViewText(R.id.widgetNetworkType, "4G")
                        views.setTextViewText(R.id.widgetTitle, "Fallback: 4G")
                        views.setTextViewText(R.id.widgetStatus, "Watch daily quota!")
                        views.setInt(R.id.widgetContainer, "setBackgroundColor", Color.parseColor("#C62828"))
                    }
                    null -> {
                        views.setTextViewText(R.id.widgetNetworkType, "—")
                        views.setTextViewText(R.id.widgetTitle, "NetShift Watching")
                        views.setTextViewText(R.id.widgetStatus, "Waiting for signal…")
                        views.setInt(R.id.widgetContainer, "setBackgroundColor", Color.parseColor("#616161"))
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
