package com.recall.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Single DataStore for all app-level flags — onboarding, wake word, and settings.
private val Context.onboardingDataStore by preferencesDataStore(name = "recall_onboarding")

// ── Onboarding ────────────────────────────────────────────────────────────────
private val ONBOARDING_COMPLETE       = booleanPreferencesKey("onboarding_complete")

// ── Wake Word ─────────────────────────────────────────────────────────────────
private val WAKE_WORD_ENABLED         = booleanPreferencesKey("wake_word_enabled")
private val WAKE_WORD_SENSITIVITY     = floatPreferencesKey("wake_word_sensitivity")

// ── Voice Settings ────────────────────────────────────────────────────────────
private val VOICE_TTS_ENABLED         = booleanPreferencesKey("voice_tts_enabled")
private val VOICE_TTS_SPEED           = floatPreferencesKey("voice_tts_speed")
private val VOICE_LANGUAGE            = stringPreferencesKey("voice_language")

// ── Notification Settings ─────────────────────────────────────────────────────
private val NOTIF_SOUND               = booleanPreferencesKey("notif_sound")
private val NOTIF_VIBRATION           = booleanPreferencesKey("notif_vibration")
private val NOTIF_HEADS_UP            = booleanPreferencesKey("notif_heads_up")
private val NOTIF_SNOOZE_MINUTES      = intPreferencesKey("notif_snooze_minutes")
private val NOTIF_RENOTIFY            = booleanPreferencesKey("notif_renotify")
private val NOTIF_RENOTIFY_INTERVAL   = intPreferencesKey("notif_renotify_interval")

// ── Location Settings ─────────────────────────────────────────────────────────
private val LOCATION_GEOFENCE_RADIUS  = intPreferencesKey("location_geofence_radius")

// ── Theme ─────────────────────────────────────────────────────────────────────
// Values: "light" | "dark" | "system"
private val THEME_MODE                = stringPreferencesKey("theme_mode")

object OnboardingPrefs {

    // ── Onboarding ─────────────────────────────────────────────────────────────

    suspend fun isComplete(context: Context): Boolean =
        context.onboardingDataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }.first()

    suspend fun markComplete(context: Context) {
        context.onboardingDataStore.edit { it[ONBOARDING_COMPLETE] = true }
    }

    // ── Wake Word ──────────────────────────────────────────────────────────────

    suspend fun setWakeWordEnabled(context: Context, enabled: Boolean) {
        context.onboardingDataStore.edit { it[WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun isWakeWordEnabled(context: Context): Boolean =
        context.onboardingDataStore.data.map { it[WAKE_WORD_ENABLED] ?: false }.first()

    suspend fun setWakeWordSensitivity(context: Context, sensitivity: Float) {
        context.onboardingDataStore.edit { it[WAKE_WORD_SENSITIVITY] = sensitivity }
    }

    suspend fun getWakeWordSensitivity(context: Context): Float =
        context.onboardingDataStore.data.map { it[WAKE_WORD_SENSITIVITY] ?: 0.5f }.first()

    // ── Voice Settings ─────────────────────────────────────────────────────────

    suspend fun setVoiceTtsEnabled(context: Context, enabled: Boolean) {
        context.onboardingDataStore.edit { it[VOICE_TTS_ENABLED] = enabled }
    }

    suspend fun isVoiceTtsEnabled(context: Context): Boolean =
        context.onboardingDataStore.data.map { it[VOICE_TTS_ENABLED] ?: true }.first()

    suspend fun setVoiceTtsSpeed(context: Context, speed: Float) {
        context.onboardingDataStore.edit { it[VOICE_TTS_SPEED] = speed }
    }

    suspend fun getVoiceTtsSpeed(context: Context): Float =
        context.onboardingDataStore.data.map { it[VOICE_TTS_SPEED] ?: 1.0f }.first()

    suspend fun setVoiceLanguage(context: Context, language: String) {
        context.onboardingDataStore.edit { it[VOICE_LANGUAGE] = language }
    }

    suspend fun getVoiceLanguage(context: Context): String =
        context.onboardingDataStore.data.map { it[VOICE_LANGUAGE] ?: "en-NG" }.first()

    // ── Notification Settings ──────────────────────────────────────────────────

    suspend fun setNotifSound(context: Context, enabled: Boolean) {
        context.onboardingDataStore.edit { it[NOTIF_SOUND] = enabled }
    }

    suspend fun isNotifSound(context: Context): Boolean =
        context.onboardingDataStore.data.map { it[NOTIF_SOUND] ?: true }.first()

    suspend fun setNotifVibration(context: Context, enabled: Boolean) {
        context.onboardingDataStore.edit { it[NOTIF_VIBRATION] = enabled }
    }

    suspend fun isNotifVibration(context: Context): Boolean =
        context.onboardingDataStore.data.map { it[NOTIF_VIBRATION] ?: true }.first()

    suspend fun setNotifHeadsUp(context: Context, enabled: Boolean) {
        context.onboardingDataStore.edit { it[NOTIF_HEADS_UP] = enabled }
    }

    suspend fun isNotifHeadsUp(context: Context): Boolean =
        context.onboardingDataStore.data.map { it[NOTIF_HEADS_UP] ?: true }.first()

    suspend fun setNotifSnoozeMinutes(context: Context, minutes: Int) {
        context.onboardingDataStore.edit { it[NOTIF_SNOOZE_MINUTES] = minutes }
    }

    suspend fun getNotifSnoozeMinutes(context: Context): Int =
        context.onboardingDataStore.data.map { it[NOTIF_SNOOZE_MINUTES] ?: 10 }.first()

    suspend fun setNotifRenotify(context: Context, enabled: Boolean) {
        context.onboardingDataStore.edit { it[NOTIF_RENOTIFY] = enabled }
    }

    suspend fun isNotifRenotify(context: Context): Boolean =
        context.onboardingDataStore.data.map { it[NOTIF_RENOTIFY] ?: false }.first()

    suspend fun setNotifRenotifyInterval(context: Context, minutes: Int) {
        context.onboardingDataStore.edit { it[NOTIF_RENOTIFY_INTERVAL] = minutes }
    }

    suspend fun getNotifRenotifyInterval(context: Context): Int =
        context.onboardingDataStore.data.map { it[NOTIF_RENOTIFY_INTERVAL] ?: 10 }.first()

    // ── Location Settings ──────────────────────────────────────────────────────

    suspend fun setGeofenceRadius(context: Context, radiusMeters: Int) {
        context.onboardingDataStore.edit { it[LOCATION_GEOFENCE_RADIUS] = radiusMeters }
    }

    suspend fun getGeofenceRadius(context: Context): Int =
        context.onboardingDataStore.data.map { it[LOCATION_GEOFENCE_RADIUS] ?: 100 }.first()

    // ── Theme ──────────────────────────────────────────────────────────────────

    /** Reactive Flow — collect in MainActivity to avoid restart on theme change. */
    fun themeModeFlow(context: Context): Flow<String> =
        context.onboardingDataStore.data.map { it[THEME_MODE] ?: "system" }

    suspend fun setThemeMode(context: Context, mode: String) {
        context.onboardingDataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun getThemeMode(context: Context): String =
        context.onboardingDataStore.data.map { it[THEME_MODE] ?: "system" }.first()
}
