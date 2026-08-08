package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.R
import com.example.data.FocusPreferences
import com.example.service.FocusLockService
import com.example.ui.LockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_QUICK_LOCK = "com.example.widget.ACTION_QUICK_LOCK"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_QUICK_LOCK) {
            val focusPreferences = FocusPreferences(context.applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                val durationMinutes = 25
                val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
                focusPreferences.startSession(mode = 0, durationMinutes = durationMinutes, endTime = endTime)

                // Trigger Lock Service and Activity
                val serviceIntent = Intent(context, FocusLockService::class.java).apply {
                    action = FocusLockService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                val lockActivityIntent = Intent(context, LockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(lockActivityIntent)
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        val intent = Intent(context, WidgetProvider::class.java).apply {
            action = ACTION_QUICK_LOCK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
