package com.recall.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.recall.MainActivity
import com.recall.R
import com.recall.reminders.ReminderActionReceiver
import com.recall.reminders.ReminderScheduler

/**
 * Shared notification builder for geofence-triggered reminders.
 * Used by both [GeofenceBroadcastReceiver] (passive API) and
 * [GeofenceMonitorService] (active proximity check).
 */
object GeofenceNotificationHelper {

    private const val CHANNEL_ID = "recall_reminders_v3"

    fun showNotification(
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
            "Recall Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Recall reminder alerts"
            enableVibration(true)
            setShowBadge(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                audioAttributes,
            )
        }
        nm.createNotificationChannel(channel)
    }
}
