package com.ranti.location

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

    private const val PREFS_NAME = "ranti_geofence_prefs"
    private const val KEY_PREFIX_BODY = "body_"
    private const val KEY_PREFIX_PLACE = "place_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Save the reminder body and place name so the receiver can show them. */
    fun put(context: Context, reminderId: String, body: String, placeName: String) {
        prefs(context).edit()
            .putString("$KEY_PREFIX_BODY$reminderId", body)
            .putString("$KEY_PREFIX_PLACE$reminderId", placeName)
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
            .apply()
    }
}
