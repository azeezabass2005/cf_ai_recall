package com.ranti.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

/**
 * SPEC §9 — Geofence registration.
 *
 * Uses a single shared PendingIntent (request code 0) for all geofences.
 * The Geofencing API injects extras at delivery time — the receiver uses
 * [com.google.android.gms.location.GeofencingEvent.getTriggeringGeofences]
 * to iterate through the triggered fences.
 *
 * The PendingIntent uses FLAG_MUTABLE because the Geofencing API needs to
 * inject extras at delivery time.
 */
object GeofenceManager {

    private const val TAG = "GeofenceManager"

    /**
     * Register a geofence for a location-based reminder.
     *
     * @param reminderId used as the geofence request ID so we can remove it later
     * @param body reminder text — stored in [GeofencePrefs] for the receiver
     * @param placeName human-readable name for the notification
     * @param lat latitude
     * @param lng longitude
     * @param radiusM geofence radius in metres
     */
    suspend fun register(
        context: Context,
        reminderId: String,
        body: String,
        placeName: String,
        lat: Double,
        lng: Double,
        radiusM: Float = 100f,
    ): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Missing ACCESS_FINE_LOCATION — cannot register geofence")
            return false
        }

        // Persist body + place name so the BroadcastReceiver can read them synchronously.
        GeofencePrefs.put(context, reminderId, body, placeName)

        val geofence = Geofence.Builder()
            .setRequestId(reminderId)
            .setCircularRegion(lat, lng, radiusM)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val client = LocationServices.getGeofencingClient(context)
        return try {
            client.addGeofences(request, geofencePendingIntent(context)).await()
            Log.d(TAG, "Registered geofence $reminderId at ($lat, $lng) r=${radiusM}m")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register geofence $reminderId", e)
            false
        }
    }

    /** Remove a geofence by reminder ID. */
    suspend fun remove(context: Context, reminderId: String) {
        GeofencePrefs.remove(context, reminderId)
        val client = LocationServices.getGeofencingClient(context)
        try {
            client.removeGeofences(listOf(reminderId)).await()
            Log.d(TAG, "Removed geofence $reminderId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove geofence $reminderId", e)
        }
    }

    /**
     * Single shared PendingIntent for all geofences. Uses FLAG_MUTABLE so the
     * Geofencing API can inject trigger information at delivery time.
     */
    private fun geofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
