package com.ranti.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Reuses the same DataStore as DeviceId — single "ranti_prefs" store for all
// app-level flags. The key for the device id lives in DeviceId.kt.
private val Context.onboardingDataStore by preferencesDataStore(name = "ranti_onboarding")

private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
private val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
private val WAKE_WORD_SENSITIVITY = androidx.datastore.preferences.core.floatPreferencesKey("wake_word_sensitivity")

object OnboardingPrefs {

    suspend fun isComplete(context: Context): Boolean {
        return context.onboardingDataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }.first()
    }

    suspend fun markComplete(context: Context) {
        context.onboardingDataStore.edit { it[ONBOARDING_COMPLETE] = true }
    }

    suspend fun setWakeWordEnabled(context: Context, enabled: Boolean) {
        context.onboardingDataStore.edit { it[WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun isWakeWordEnabled(context: Context): Boolean {
        return context.onboardingDataStore.data.map { it[WAKE_WORD_ENABLED] ?: false }.first()
    }

    suspend fun setWakeWordSensitivity(context: Context, sensitivity: Float) {
        context.onboardingDataStore.edit { it[WAKE_WORD_SENSITIVITY] = sensitivity }
    }

    suspend fun getWakeWordSensitivity(context: Context): Float {
        return context.onboardingDataStore.data
            .map { it[WAKE_WORD_SENSITIVITY] ?: 0.5f }
            .first()
    }
}
