package com.example.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FocusPreferences
import com.example.data.WhitelistDefaults
import com.example.service.FocusLockService
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DimGray
import com.example.ui.theme.FocusBlackTheme
import com.example.ui.theme.MutedGray
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockActivity : ComponentActivity() {

    private lateinit var focusPreferences: FocusPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        focusPreferences = FocusPreferences(applicationContext)

        // Prevent taking screenshots or showing in recents preview
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set screen brightness override to very low
        val lp = window.attributes
        lp.screenBrightness = 0.05f // 5% brightness
        window.attributes = lp

        // Show over keyguard / lock screen if required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Intercept back button gestures
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Strictly block back press during session
            }
        })

        setContent {
            FocusBlackTheme {
                val isSessionActive by focusPreferences.isSessionActive.collectAsState(initial = true)
                val sessionMode by focusPreferences.sessionMode.collectAsState(initial = 0)
                val sessionEndTime by focusPreferences.sessionEndTime.collectAsState(initial = 0L)
                val whitelistedPackages by focusPreferences.whitelistedPackages.collectAsState(initial = emptySet())

                if (!isSessionActive) {
                    LaunchedEffect(Unit) {
                        finish()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = OledBlack
                ) {
                    LockScreenContent(
                        mode = sessionMode,
                        endTime = sessionEndTime,
                        whitelistedPackages = whitelistedPackages,
                        onStopEmergency = {
                            val intent = Intent(this@LockActivity, FocusLockService::class.java).apply {
                                action = FocusLockService.ACTION_STOP
                            }
                            startService(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }
    }
}

@Composable
fun LockScreenContent(
    mode: Int,
    endTime: Long,
    whitelistedPackages: Set<String>,
    onStopEmergency: () -> Unit
) {
    val context = LocalContext.current
    var currentTimeString by remember { mutableStateOf("") }
    var remainingSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(endTime) {
        while (true) {
            val now = System.currentTimeMillis()
            val remaining = (endTime - now) / 1000
            remainingSeconds = if (remaining > 0) remaining else 0L

            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            currentTimeString = sdf.format(Date(now))

            delay(1000)
        }
    }

    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    val timerString = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (mode == 0) Icons.Default.Lock else Icons.Default.LockClock,
                contentDescription = "Lock Status",
                tint = MutedGray,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (mode == 0) "BLACKOUT FOCUS ACTIVE" else "KIOSK FOCUS MODE",
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                color = MutedGray
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = timerString,
                fontSize = 54.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                color = PureWhite
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Current Time: $currentTimeString",
                fontSize = 14.sp,
                color = DimGray,
                fontFamily = FontFamily.Monospace
            )

            if (mode == 1) { // Kiosk Focus mode - Show Whitelisted App Drawer
                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = "WHITELISTED APPLICATIONS",
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    color = MutedGray,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                val availableApps = WhitelistDefaults.defaultApps.filter {
                    whitelistedPackages.contains(it.packageName)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(availableApps) { app ->
                        WhitelistedAppButton(app = app) {
                            try {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                } else {
                                    // Fallback if app not installed on physical device
                                    val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://google.com"))
                                    context.startActivity(webIntent)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }

        // Bottom disclaimer
        Text(
            text = "FocusBlack • Hardware & Navigation Locked",
            fontSize = 10.sp,
            color = DimGray,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
fun WhitelistedAppButton(
    app: com.example.data.WhitelistedApp,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, shape = RoundedCornerShape(12.dp))
            .border(1.dp, DimGray, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MutedGray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = app.name,
                color = PureWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = "Launch",
            tint = MutedGray,
            modifier = Modifier.size(16.dp)
        )
    }
}
