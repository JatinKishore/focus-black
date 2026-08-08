package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_black_prefs")

data class WhitelistedApp(
    val name: String,
    val packageName: String,
    val isDefaultSelected: Boolean = false
)

object WhitelistDefaults {
    val defaultApps = listOf(
        WhitelistedApp("WhatsApp", "com.whatsapp", true),
        WhitelistedApp("Gmail", "com.google.android.gm", true),
        WhitelistedApp("Google Drive", "com.google.android.apps.docs", true),
        WhitelistedApp("Google Docs", "com.google.android.apps.docs.editors.docs", true),
        WhitelistedApp("Google Meet", "com.google.android.apps.meetings", true)
    )
}

class FocusPreferences(private val context: Context) {

    companion object {
        val KEY_SESSION_ACTIVE = booleanPreferencesKey("session_active")
        val KEY_SESSION_MODE = intPreferencesKey("session_mode") // 0: Strict Blackout, 1: Kiosk Focus
        val KEY_SESSION_END_TIME = longPreferencesKey("session_end_time")
        val KEY_DURATION_MINUTES = intPreferencesKey("duration_minutes")
        val KEY_WHITELISTED_PACKAGES = stringSetPreferencesKey("whitelisted_packages")
        val KEY_DAILY_USAGE_SECONDS = longPreferencesKey("daily_usage_seconds")
        val KEY_LAST_USAGE_DAY = stringPreferencesKey("last_usage_day")
    }

    val isSessionActive: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SESSION_ACTIVE] ?: false
    }

    val sessionMode: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_SESSION_MODE] ?: 0
    }

    val sessionEndTime: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_SESSION_END_TIME] ?: 0L
    }

    val durationMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_DURATION_MINUTES] ?: 25
    }

    val whitelistedPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_WHITELISTED_PACKAGES] ?: WhitelistDefaults.defaultApps.map { it.packageName }.toSet()
    }

    val dailyUsageSeconds: Flow<Long> = context.dataStore.data.map { prefs ->
        val todayStr = LocalDate.now().toString()
        val lastDay = prefs[KEY_LAST_USAGE_DAY] ?: ""
        if (lastDay != todayStr) {
            0L
        } else {
            prefs[KEY_DAILY_USAGE_SECONDS] ?: 0L
        }
    }

    suspend fun startSession(mode: Int, durationMinutes: Int, endTime: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SESSION_ACTIVE] = true
            prefs[KEY_SESSION_MODE] = mode
            prefs[KEY_DURATION_MINUTES] = durationMinutes
            prefs[KEY_SESSION_END_TIME] = endTime
        }
    }

    suspend fun stopSession() {
        context.dataStore.edit { prefs ->
            prefs[KEY_SESSION_ACTIVE] = false
            prefs[KEY_SESSION_END_TIME] = 0L
        }
    }

    suspend fun updateWhitelistedPackages(packages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WHITELISTED_PACKAGES] = packages
        }
    }

    suspend fun updateDailyUsage(seconds: Long) {
        val todayStr = LocalDate.now().toString()
        context.dataStore.edit { prefs ->
            val lastDay = prefs[KEY_LAST_USAGE_DAY] ?: ""
            if (lastDay != todayStr) {
                prefs[KEY_LAST_USAGE_DAY] = todayStr
                prefs[KEY_DAILY_USAGE_SECONDS] = seconds
            } else {
                prefs[KEY_DAILY_USAGE_SECONDS] = (prefs[KEY_DAILY_USAGE_SECONDS] ?: 0L) + seconds
            }
        }
    }
}

private fun stringPreferencesKey(name: String) = androidx.datastore.preferences.core.stringPreferencesKey(name)
