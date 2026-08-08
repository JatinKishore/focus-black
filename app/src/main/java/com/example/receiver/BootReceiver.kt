package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.FocusPreferences
import com.example.service.AppBlockerService
import com.example.service.FocusLockService
import com.example.ui.LockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val focusPreferences = FocusPreferences(context.applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                val isSessionActive = focusPreferences.isSessionActive.first()
                val endTime = focusPreferences.sessionEndTime.first()

                // Always start background monitoring service
                val blockerIntent = Intent(context, AppBlockerService::class.java)
                context.startService(blockerIntent)

                if (isSessionActive && System.currentTimeMillis() < endTime) {
                    // Re-trigger LockActivity and FocusLockService
                    val lockServiceIntent = Intent(context, FocusLockService::class.java).apply {
                        action = FocusLockService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(lockServiceIntent)
                    } else {
                        context.startService(lockServiceIntent)
                    }

                    val lockActivityIntent = Intent(context, LockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(lockActivityIntent)
                }
            }
        }
    }
}
