package com.recall.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.recall.MainActivity
import com.recall.R

/**
 * Foreground service that actively monitors the user's location and checks
 * proximity to registered geofences. This supplements the passive Geofencing
 * API which batches location checks aggressively on modern Android, often
 * delaying triggers by 30 min to 2+ hours.
 *
 * Lifecycle:
 *   - Started by [GeofenceManager.register] when the first geofence is added.
 *   - Stopped by [GeofenceManager.remove] when the last geofence is removed,
 *     or by calling [stopMonitoring].
 *   - Requests location updates every ~30 seconds (balanced accuracy).
 *   - On each fix, checks distance to every registered geofence in [GeofencePrefs].
 *   - If within radius → fires the reminder notification directly via
 *     [GeofenceBroadcastReceiver.showNotificationDirect], removes the geofence,
 *     and stops if no fences remain.
 */
class GeofenceMonitorService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        startLocationUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    private fun startLocationUpdates() {
        if (locationCallback != null) return // already running

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Missing FINE_LOCATION — cannot monitor geofences")
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(INTERVAL_MS)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                checkProximity(location)
            }
        }
        locationCallback = callback

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        Log.d(TAG, "Started location updates (interval=${INTERVAL_MS}ms)")
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedClient.removeLocationUpdates(it)
            locationCallback = null
            Log.d(TAG, "Stopped location updates")
        }
    }

    /**
     * Check distance to every registered geofence. Fire notification + cleanup
     * for any that are within radius.
     */
    private fun checkProximity(location: Location) {
        val prefs = getSharedPreferences("recall_geofence_prefs", MODE_PRIVATE)
        val allKeys = prefs.all.keys
        val bodyKeys = allKeys.filter { it.startsWith("body_") }

        if (bodyKeys.isEmpty()) {
            Log.d(TAG, "No active geofences — stopping service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        for (key in bodyKeys) {
            val reminderId = key.removePrefix("body_")
            val lat = prefs.getString("lat_$reminderId", null)?.toDoubleOrNull() ?: continue
            val lng = prefs.getString("lng_$reminderId", null)?.toDoubleOrNull() ?: continue
            val radius = prefs.getFloat("radius_$reminderId", DEFAULT_RADIUS_M)

            val distance = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                lat, lng,
                distance,
            )

            if (distance[0] <= radius) {
                val body = prefs.getString("body_$reminderId", "Location reminder") ?: "Location reminder"
                val placeName = prefs.getString("place_$reminderId", "this place") ?: "this place"

                Log.d(TAG, "Within ${distance[0]}m of $placeName (radius=${radius}m) — firing reminder $reminderId")

                // Fire the notification directly
                GeofenceNotificationHelper.showNotification(this, reminderId, body, placeName)

                // Clean up this geofence — it's done
                GeofencePrefs.remove(this, reminderId)

                // Also remove from the Geofencing API
                try {
                    LocationServices.getGeofencingClient(this)
                        .removeGeofences(listOf(reminderId))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove geofence $reminderId from API", e)
                }
            }
        }

        // Re-check if any geofences remain
        val remaining = prefs.all.keys.count { it.startsWith("body_") }
        if (remaining == 0) {
            Log.d(TAG, "All geofences triggered — stopping service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPi = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location reminders active")
            .setContentText("Monitoring ${GeofencePrefs.getActiveCount(this)} location(s)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(tapPi)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Monitoring",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows while Recall monitors location reminders"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "GeofenceMonitor"
        private const val CHANNEL_ID = "recall_geofence_monitor"
        private const val NOTIF_ID = 9001
        const val ACTION_STOP = "com.recall.action.STOP_GEOFENCE_MONITOR"

        /** Location update interval — 15 seconds. */
        private const val INTERVAL_MS = 15_000L
        /** Fastest interval — 5 seconds if another app is requesting faster updates. */
        private const val MIN_INTERVAL_MS = 5_000L
        /** Default geofence radius if not stored. */
        private const val DEFAULT_RADIUS_M = 100f

        fun startMonitoring(context: Context) {
            val intent = Intent(context, GeofenceMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, GeofenceMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
