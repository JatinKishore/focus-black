package com.example.service

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.data.FocusPreferences
import com.example.ui.LockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppBlockerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private lateinit var focusPreferences: FocusPreferences

    override fun onCreate() {
        super.onCreate()
        focusPreferences = FocusPreferences(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            monitorForegroundApp()
        }
        return START_STICKY
    }

    private suspend fun monitorForegroundApp() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return

        var lastTrackTime = System.currentTimeMillis()

        while (serviceJob.isActive) {
            val now = System.currentTimeMillis()
            val elapsedSeconds = (now - lastTrackTime) / 1000
            if (elapsedSeconds >= 5) {
                // Track 5 seconds of active screen usage
                focusPreferences.updateDailyUsage(elapsedSeconds)
                lastTrackTime = now

                // Check 60-minute (3600 seconds) quota limit
                val dailySeconds = focusPreferences.dailyUsageSeconds.first()
                if (dailySeconds >= 3600L) {
                    val isActive = focusPreferences.isSessionActive.first()
                    if (!isActive) {
                        // Automatically trigger pitch-black lock for remainder of the 24h cycle
                        val endTime = now + (24 * 3600 * 1000L) // remainder
                        focusPreferences.startSession(mode = 0, durationMinutes = 1440, endTime = endTime)

                        val lockIntent = Intent(applicationContext, LockActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(lockIntent)
                    }
                }
            }

            val isSessionActive = focusPreferences.isSessionActive.first()
            val sessionMode = focusPreferences.sessionMode.first()
            val whitelistedPackages = focusPreferences.whitelistedPackages.first()

            if (isSessionActive) {
                val currentForegroundPkg = getForegroundPackageName(usageStatsManager, now)
                
                if (currentForegroundPkg != null && currentForegroundPkg != packageName) {
                    if (sessionMode == 0) { // Hard Blackout Mode - Block everything
                        pullOverlayToFront()
                    } else if (sessionMode == 1) { // Kiosk Focus - Only allow whitelisted packages
                        if (!whitelistedPackages.contains(currentForegroundPkg) &&
                            currentForegroundPkg != "com.android.systemui"
                        ) {
                            pullOverlayToFront()
                        }
                    }
                }
            }

            delay(1000)
        }
    }

    private fun getForegroundPackageName(usm: UsageStatsManager, now: Long): String? {
        var foregroundApp: String? = null
        val events = usm.queryEvents(now - 3000, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                foregroundApp = event.packageName
            }
        }
        return foregroundApp
    }

    private fun pullOverlayToFront() {
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
