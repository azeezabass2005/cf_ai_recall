package com.recall.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "recall_prefs")
private val DEVICE_ID_KEY = stringPreferencesKey("device_id")

/**
 * Anonymous per-install device id. Generated lazily on first call, persisted
 * in DataStore, sent on every request as `X-Recall-Device`. See SPEC §1
 * "Network Bridge".
 */
object DeviceId {
    suspend fun get(context: Context): String {
        val prefs = context.dataStore.data.first()
        prefs[DEVICE_ID_KEY]?.let { return it }
        val fresh = UUID.randomUUID().toString()
        context.dataStore.edit { it[DEVICE_ID_KEY] = fresh }
        return fresh
    }
}
