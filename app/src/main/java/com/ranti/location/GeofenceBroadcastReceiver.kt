package com.ranti.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.Geofence
import com.ranti.MainActivity
import com.ranti.R
import com.ranti.reminders.ReminderActionReceiver
import com.ranti.reminders.ReminderScheduler

/**
 * SPEC §9 — Fired when the user enters a geofenced area.
 *
 * Posts a heads-up notification with "Done" action (reuses
 * [ReminderActionReceiver.ACTION_DONE]). Reads reminder body from
 * [GeofencePrefs] since onReceive is synchronous.
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
            val body = GeofencePrefs.getBody(context, reminderId) ?: "Location reminder"
            val placeName = GeofencePrefs.getPlaceName(context, reminderId) ?: "this place"

            Log.d(TAG, "Geofence enter: $reminderId at $placeName")
            showNotification(context, reminderId, body, placeName)
        }
    }

    private fun showNotification(
        context: Context,
        reminderId: String,
        body: String,
        placeName: String,
    ) {
        ensureChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPi = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // "Done" action — dismisses and cleans up geofence prefs
        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DONE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        val donePi = PendingIntent.getBroadcast(
            context,
            "done_$reminderId".hashCode(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("You're near $placeName")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tapPi)
            .setSound(alarmSound)
            .addAction(R.mipmap.ic_launcher, "Done", donePi)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(reminderId.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ranti Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Ranti reminder alerts"
            enableVibration(true)
            setShowBadge(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                audioAttributes,
            )
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
        // Same channel as ReminderReceiver so all reminders are grouped
        private const val CHANNEL_ID = "ranti_reminders_v3"
    }
}
