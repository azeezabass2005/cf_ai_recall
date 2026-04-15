package com.ranti.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ranti.location.GeofenceMonitorService
import com.ranti.location.GeofencePrefs
import com.ranti.network.RantiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * SPEC 12.2 — Handles notification action buttons: "Done" and "Snooze 10 min".
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return

        when (intent.action) {
            ACTION_DONE -> handleDone(context, id)
            ACTION_SNOOZE -> {
                val body = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_BODY) ?: "Reminder"
                handleSnooze(context, id, body)
            }
        }
    }

    private fun handleDone(context: Context, id: String) {
        Log.d(TAG, "Done action for reminder $id")

        // Dismiss the notification
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(id.hashCode())

        // Cancel any pending alarm for this reminder
        ReminderScheduler.cancel(context, id)

        // Clean up geofence data if this was a location reminder.
        GeofencePrefs.remove(context, id)
        if (GeofencePrefs.getActiveCount(context) == 0) {
            GeofenceMonitorService.stopMonitoring(context)
        }

        // Update the backend status to 'dismissed' so it moves to History.
        // goAsync() extends the receiver's lifecycle for the network call.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = RantiApi(context)
                api.markDone(id)
                Log.d(TAG, "Marked reminder $id as done on backend")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark reminder $id as done on backend", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSnooze(context: Context, id: String, body: String) {
        Log.d(TAG, "Snooze action for reminder $id — rescheduling +10 min")

        // Dismiss the current notification
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(id.hashCode())

        // Schedule a new alarm for now + 10 minutes
        val snoozeMillis = System.currentTimeMillis() + SNOOZE_DURATION_MS
        val alarmIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_FIRE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, id)
            putExtra(ReminderScheduler.EXTRA_REMINDER_BODY, body)
            putExtra(ReminderScheduler.EXTRA_REMINDER_RECURRING, false)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(AlarmManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeMillis, pi)
        }

        // Show a brief "Snoozed" notification
        val snoozeTime = Instant.ofEpochMilli(snoozeMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

        val snoozedNotification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Snoozed")
            .setContentText("Will remind you again at $snoozeTime")
            .setSmallIcon(com.ranti.R.mipmap.ic_launcher)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(5000) // auto-dismiss after 5 seconds
            .build()
        nm.notify("snoozed_$id".hashCode(), snoozedNotification)

        Log.d(TAG, "Snoozed reminder $id until $snoozeTime")
    }

    companion object {
        private const val TAG = "ReminderActionReceiver"
        private const val CHANNEL_ID = "ranti_reminders"
        const val ACTION_DONE = "com.ranti.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.ranti.action.REMINDER_SNOOZE"
        private const val SNOOZE_DURATION_MS = 10 * 60 * 1000L // 10 minutes
    }
}

