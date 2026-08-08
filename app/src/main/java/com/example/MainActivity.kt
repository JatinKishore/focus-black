package com.example

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FocusPreferences
import com.example.data.WhitelistDefaults
import com.example.receiver.FocusDeviceAdminReceiver
import com.example.service.AppBlockerService
import com.example.service.FocusLockService
import com.example.ui.LockActivity
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DimGray
import com.example.ui.theme.FocusBlackTheme
import com.example.ui.theme.MutedGray
import com.example.ui.theme.OledBlack
import com.example.ui.theme.OffWhite
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var focusPreferences: FocusPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        focusPreferences = FocusPreferences(applicationContext)

        // Start App Blocker Background Monitoring Service
        val blockerIntent = Intent(this, AppBlockerService::class.java)
        startService(blockerIntent)

        setContent {
            FocusBlackTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = OledBlack
                ) { innerPadding ->
                    SetupScreen(
                        modifier = Modifier.padding(innerPadding),
                        focusPreferences = focusPreferences,
                        onStartSession = { mode, durationMinutes ->
                            val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
                            val scope = (this as? ComponentActivity)
                            
                            val serviceIntent = Intent(this, FocusLockService::class.java).apply {
                                action = FocusLockService.ACTION_START
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent)
                            } else {
                                startService(serviceIntent)
                            }

                            val lockIntent = Intent(this, LockActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            startActivity(lockIntent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    focusPreferences: FocusPreferences,
    onStartSession: (mode: Int, durationMinutes: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var durationMinutes by remember { mutableFloatStateOf(25f) }
    var isKioskMode by remember { mutableStateOf(false) } // false = Strict Blackout, true = Kiosk Whitelist
    var showAppsDialog by remember { mutableStateOf(false) }

    val savedWhitelistedPackages by focusPreferences.whitelistedPackages.collectAsState(initial = emptySet())
    val dailyUsageSeconds by focusPreferences.dailyUsageSeconds.collectAsState(initial = 0L)
    val isSessionActive by focusPreferences.isSessionActive.collectAsState(initial = false)

    var hasUsageStatsPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var isDeviceAdminActive by remember { mutableStateOf(false) }

    fun checkPermissions() {
        // Usage Stats Check
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        hasUsageStatsPermission = (mode == AppOpsManager.MODE_ALLOWED)

        // Overlay Check
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        // Device Admin Check
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, FocusDeviceAdminReceiver::class.java)
        isDeviceAdminActive = dpm.isAdminActive(adminComponent)
    }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = OledBlack
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Title
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(if (isSessionActive) AccentGreen else DimGray, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSessionActive) "SESSION RUNNING" else "DISCIPLINE ENGINE",
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            color = MutedGray,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "FocusBlack",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        letterSpacing = (-1).sp
                    )

                    Text(
                        text = "Minimal OLED Focus Lock & Digital Detox",
                        fontSize = 13.sp,
                        color = MutedGray
                    )
                }
            }

            // Daily Usage Quota Card
            item {
                val dailyMinutes = dailyUsageSeconds / 60
                val progress = (dailyUsageSeconds.toFloat() / 3600f).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, DimGray, shape = RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MutedGray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Daily Active Screen Quota",
                                fontSize = 14.sp,
                                color = OffWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "$dailyMinutes / 60 min",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (dailyMinutes >= 60) AccentRed else OffWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = if (dailyMinutes >= 60) AccentRed else PureWhite,
                        trackColor = DarkSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (dailyMinutes >= 60)
                            "Quota Reached! Pitch-Black Lock active for remaining day cycle."
                        else
                            "${60 - dailyMinutes} minutes remaining before auto-lockout.",
                        fontSize = 11.sp,
                        color = MutedGray
                    )
                }
            }

            // Duration Slider Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, DimGray, shape = RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = "FOCUS DURATION",
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        color = MutedGray,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "${durationMinutes.toInt()} Minutes",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )

                        Text(
                            text = "1 to 120 min",
                            fontSize = 12.sp,
                            color = MutedGray
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = durationMinutes,
                        onValueChange = { durationMinutes = it },
                        valueRange = 1f..120f,
                        steps = 118,
                        colors = SliderDefaults.colors(
                            thumbColor = PureWhite,
                            activeTrackColor = PureWhite,
                            inactiveTrackColor = DimGray
                        ),
                        modifier = Modifier.testTag("duration_slider")
                    )
                }
            }

            // Mode Selector Toggle Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, DimGray, shape = RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (!isKioskMode) Icons.Default.Lock else Icons.Default.LockClock,
                                    contentDescription = null,
                                    tint = PureWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (!isKioskMode) "Strict Blackout Lock" else "Kiosk Focus (Whitelisted)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (!isKioskMode)
                                    "Complete OLED pitch-black screen. All apps & gestures blocked."
                                else
                                    "Minimal OLED home screen with allowed productivity apps only.",
                                fontSize = 12.sp,
                                color = MutedGray
                            )
                        }

                        Switch(
                            checked = isKioskMode,
                            onCheckedChange = { isKioskMode = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OledBlack,
                                checkedTrackColor = PureWhite,
                                uncheckedThumbColor = MutedGray,
                                uncheckedTrackColor = DarkSurfaceVariant
                            ),
                            modifier = Modifier.testTag("kiosk_mode_toggle")
                        )
                    }

                    if (isKioskMode) {
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { showAppsDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("configure_whitelisted_apps_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = OffWhite
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Configure Allowed Apps (${savedWhitelistedPackages.size} Selected)",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // System Permissions Requirements List
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, DimGray, shape = RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MutedGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SYSTEM ACCESS PERMISSIONS",
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            color = MutedGray,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    PermissionItemRow(
                        title = "Usage Stats Access",
                        subtitle = "Required to detect non-whitelisted apps & active screen quota",
                        isGranted = hasUsageStatsPermission,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionItemRow(
                        title = "Display Over Other Apps (Overlay)",
                        subtitle = "Required to enforce pitch-black lock overlay",
                        isGranted = hasOverlayPermission,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionItemRow(
                        title = "Device Administrator",
                        subtitle = "Prevents force-stop or bypass during active sessions",
                        isGranted = isDeviceAdminActive,
                        onClick = {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(
                                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                    ComponentName(context, FocusDeviceAdminReceiver::class.java)
                                )
                                putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "Device Administrator privileges prevent bypassing active focus sessions."
                                )
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Start Focus Session Action Button
            item {
                Button(
                    onClick = {
                        onStartSession(if (isKioskMode) 1 else 0, durationMinutes.toInt())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_focus_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureWhite,
                        contentColor = OledBlack
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "START FOCUS SESSION (${durationMinutes.toInt()}M)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Whitelisted Apps Dialog
    if (showAppsDialog) {
        var tempSelectedApps by remember { mutableStateOf(savedWhitelistedPackages) }

        AlertDialog(
            onDismissRequest = { showAppsDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Allowed Applications",
                    color = PureWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Select applications permitted during Kiosk Focus mode:",
                        fontSize = 12.sp,
                        color = MutedGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(WhitelistDefaults.defaultApps) { app ->
                            val isChecked = tempSelectedApps.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tempSelectedApps = if (isChecked) {
                                            tempSelectedApps - app.packageName
                                        } else {
                                            tempSelectedApps + app.packageName
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        tempSelectedApps = if (checked) {
                                            tempSelectedApps + app.packageName
                                        } else {
                                            tempSelectedApps - app.packageName
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = PureWhite,
                                        uncheckedColor = MutedGray,
                                        checkmarkColor = OledBlack
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = app.name,
                                        color = PureWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = DimGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            focusPreferences.updateWhitelistedPackages(tempSelectedApps)
                        }
                        showAppsDialog = false
                    }
                ) {
                    Text("Save", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppsDialog = false }) {
                    Text("Cancel", color = MutedGray)
                }
            }
        )
    }
}

@Composable
fun PermissionItemRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MutedGray
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = AccentGreen,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = "Grant",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentRed
            )
        }
    }
}
