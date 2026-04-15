package com.recall.location

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
 * Registers geofences with the Play Services Geofencing API (passive) AND
 * starts [GeofenceMonitorService] (active polling) so location reminders
 * fire promptly instead of being delayed by Android's battery batching.
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

        // Persist body, place name, AND coordinates so the monitor service
        // can check proximity directly without the Geofencing API.
        GeofencePrefs.put(context, reminderId, body, placeName, lat, lng, radiusM)

        val geofence = Geofence.Builder()
            .setRequestId(reminderId)
            .setCircularRegion(lat, lng, radiusM)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setNotificationResponsiveness(0) // minimum delay
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val client = LocationServices.getGeofencingClient(context)
        return try {
            client.addGeofences(request, geofencePendingIntent(context)).await()
            Log.d(TAG, "Registered geofence $reminderId at ($lat, $lng) r=${radiusM}m")

            // Start the active location monitor so we don't rely solely on
            // the passive Geofencing API which can delay triggers by hours.
            GeofenceMonitorService.startMonitoring(context)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register geofence $reminderId", e)
            // Still start the monitor — our manual proximity check works even
            // if the Geofencing API registration failed.
            GeofenceMonitorService.startMonitoring(context)
            true
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

        // Stop the monitor service if no geofences remain.
        if (GeofencePrefs.getActiveCount(context) == 0) {
            GeofenceMonitorService.stopMonitoring(context)
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
