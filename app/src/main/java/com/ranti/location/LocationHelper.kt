package com.ranti.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

/**
 * SPEC §8 — thin wrapper around FusedLocationProviderClient.
 *
 * Used to grab the user's current location for proximity bias when resolving
 * places via the Worker's `/resolve-place` endpoint.
 */
object LocationHelper {

    /**
     * Returns the last known location or a fresh fix if unavailable.
     * Returns null if the permission is missing or location is unavailable.
     */
    suspend fun getCurrentLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val client = LocationServices.getFusedLocationProviderClient(context)

        // Try the last known location first — it's instant and free.
        val last = try {
            client.lastLocation.await()
        } catch (_: Exception) {
            null
        }
        if (last != null) return last

        // Fall back to a fresh fix.
        return try {
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
        } catch (_: Exception) {
            null
        }
    }
}
