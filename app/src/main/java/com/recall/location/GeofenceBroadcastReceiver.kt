package com.recall.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.Geofence

/**
 * SPEC §9 — Fired when the passive Geofencing API detects an ENTER transition.
 *
 * This is the Play Services callback. On modern Android it can be delayed by
 * 30 min – 2 hours due to battery optimisation. [GeofenceMonitorService]
 * supplements it with active location polling so reminders fire promptly.
 *
 * If the monitor service already fired the notification for this geofence,
 * [GeofencePrefs] will have been cleared — we just skip silently.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Log.w(TAG, "GeofencingEvent.fromIntent returned null")
            return
        }
        if (event.hasError()) {
            Log.e(TAG, "Geofence error code: ${event.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val triggeringGeofences = event.triggeringGeofences ?: return
        for (geofence in triggeringGeofences) {
            val reminderId = geofence.requestId
            // If the monitor service already handled this, prefs will be empty.
            val body = GeofencePrefs.getBody(context, reminderId) ?: continue
            val placeName = GeofencePrefs.getPlaceName(context, reminderId) ?: "this place"

            Log.d(TAG, "Geofence enter (passive API): $reminderId at $placeName")
            GeofenceNotificationHelper.showNotification(context, reminderId, body, placeName)

            // Clean up so the monitor service doesn't double-fire.
            GeofencePrefs.remove(context, reminderId)
        }

        // If no geofences remain, stop the monitor service.
        if (GeofencePrefs.getActiveCount(context) == 0) {
            GeofenceMonitorService.stopMonitoring(context)
        }
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
    }
}
