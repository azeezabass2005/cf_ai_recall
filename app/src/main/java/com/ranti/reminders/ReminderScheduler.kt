package com.ranti.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ranti.network.ReminderDto
import java.time.Instant

/**
 * SPEC §7.3 — Time-reminder scheduling.
 *
 * Takes a [ReminderDto] returned by the worker and asks the OS to fire a
 * BroadcastReceiver at the reminder's `next_fire_at`. Uses
 * `setExactAndAllowWhileIdle` so alarms survive Doze mode.
 *
 * The scheduler is the single source of truth for pending alarms on the
 * Android side. The worker stores the canonical reminder row in D1, but
 * *that* storage only matters for history / recurrence math — once we've
 * called [schedule], the alarm fires even if the user has no network.
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    const val ACTION_FIRE = "com.ranti.action.FIRE_REMINDER"
    const val EXTRA_REMINDER_ID = "ranti.reminder.id"
    const val EXTRA_REMINDER_BODY = "ranti.reminder.body"
    const val EXTRA_REMINDER_FIRE_AT = "ranti.reminder.fire_at"
    /** Serialized next-occurrence metadata so the receiver can reschedule recurring reminders. */
    const val EXTRA_REMINDER_RECURRING = "ranti.reminder.recurring"
    const val EXTRA_REMINDER_RECURRENCE_JSON = "ranti.reminder.recurrence_json"

    /**
     * Schedule (or reschedule) a reminder. If `next_fire_at` is in the past,
     * fires immediately (SPEC §7.3 "missed reminder" rule).
     */
    fun schedule(context: Context, reminder: ReminderDto) {
        val fireAtStr = reminder.next_fire_at ?: reminder.trigger?.fire_at ?: run {
            Log.w(TAG, "Reminder ${reminder.id} has no fire time, skipping")
            return
        }

        val fireAtMs = try {
            Instant.parse(fireAtStr).toEpochMilli()
        } catch (e: Exception) {
            Log.e(TAG, "Can't parse fire_at '$fireAtStr'", e)
            return
        }

        val now = System.currentTimeMillis()
        val triggerAt = if (fireAtMs < now) now + 1000L else fireAtMs

        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntentFor(context, reminder, fireAtStr)

        // Android 12+ requires SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM. We
        // fall back to setAndAllowWhileIdle when the user hasn't granted it.
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true

        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.d(TAG, "Scheduled reminder ${reminder.id} for $fireAtStr (exact=$canExact)")
        } catch (e: SecurityException) {
            Log.e(TAG, "AlarmManager denied the set call", e)
        }
    }

    fun cancel(context: Context, reminderId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
            Log.d(TAG, "Cancelled reminder $reminderId")
        }
    }

    private fun pendingIntentFor(
        context: Context,
        reminder: ReminderDto,
        fireAt: String,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_REMINDER_BODY, reminder.body)
            putExtra(EXTRA_REMINDER_FIRE_AT, fireAt)
            putExtra(EXTRA_REMINDER_RECURRING, reminder.recurrence != null)
            if (reminder.recurrence != null) {
                // Store as JSON so the receiver can hand it straight back to
                // ReminderScheduler when rescheduling the next occurrence.
                putExtra(
                    EXTRA_REMINDER_RECURRENCE_JSON,
                    kotlinx.serialization.json.Json.encodeToString(
                        com.ranti.network.RecurrenceDto.serializer(),
                        reminder.recurrence,
                    ),
                )
            }
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
