package com.recall.location

import android.content.Context

/**
 * SPEC §9 — SharedPreferences (not DataStore) for geofence metadata.
 *
 * We use SharedPreferences here because [GeofenceBroadcastReceiver.onReceive]
 * is synchronous — it cannot `suspend` into a DataStore coroutine. The prefs
 * store a mapping from reminderId → body text so the receiver can build the
 * notification without a network call.
 */
object GeofencePrefs {

    private const val PREFS_NAME = "recall_geofence_prefs"
    private const val KEY_PREFIX_BODY = "body_"
    private const val KEY_PREFIX_PLACE = "place_"
    private const val KEY_PREFIX_LAT = "lat_"
    private const val KEY_PREFIX_LNG = "lng_"
    private const val KEY_PREFIX_RADIUS = "radius_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save the reminder body, place name, and coordinates so the monitor
     * service can check proximity and the receiver can show notifications.
     */
    fun put(
        context: Context,
        reminderId: String,
        body: String,
        placeName: String,
        lat: Double = 0.0,
        lng: Double = 0.0,
        radiusM: Float = 100f,
    ) {
        prefs(context).edit()
            .putString("$KEY_PREFIX_BODY$reminderId", body)
            .putString("$KEY_PREFIX_PLACE$reminderId", placeName)
            .putString("$KEY_PREFIX_LAT$reminderId", lat.toString())
            .putString("$KEY_PREFIX_LNG$reminderId", lng.toString())
            .putFloat("$KEY_PREFIX_RADIUS$reminderId", radiusM)
            .apply()
    }

    fun getBody(context: Context, reminderId: String): String? =
        prefs(context).getString("$KEY_PREFIX_BODY$reminderId", null)

    fun getPlaceName(context: Context, reminderId: String): String? =
        prefs(context).getString("$KEY_PREFIX_PLACE$reminderId", null)

    fun remove(context: Context, reminderId: String) {
        prefs(context).edit()
            .remove("$KEY_PREFIX_BODY$reminderId")
            .remove("$KEY_PREFIX_PLACE$reminderId")
            .remove("$KEY_PREFIX_LAT$reminderId")
            .remove("$KEY_PREFIX_LNG$reminderId")
            .remove("$KEY_PREFIX_RADIUS$reminderId")
            .apply()
    }

    /** Count how many active geofences are registered in preferences. */
    fun getActiveCount(context: Context): Int {
        val keys = prefs(context).all.keys
        return keys.count { it.startsWith(KEY_PREFIX_BODY) }
    }
}
