package com.ranti.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ranti.MainActivity
import com.ranti.R
import com.ranti.network.RecurrenceDto
import com.ranti.network.ReminderDto
import com.ranti.network.TriggerDto
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * SPEC §7.3 — Broadcast receiver that fires when an AlarmManager alarm goes
 * off. Posts the reminder as a heads-up notification and, if the reminder is
 * recurring, schedules the next occurrence locally so we don't need the
 * network to keep recurring reminders alive.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return

        val id = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val body = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_BODY) ?: "Reminder"
        val recurring = intent.getBooleanExtra(ReminderScheduler.EXTRA_REMINDER_RECURRING, false)

        Log.d(TAG, "Firing reminder $id: $body")
        showNotification(context, id, body)

        if (recurring) {
            val recurrenceJson = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_RECURRENCE_JSON)
            if (recurrenceJson != null) {
                runCatching { rescheduleNext(context, id, body, recurrenceJson) }
                    .onFailure { Log.e(TAG, "Failed to reschedule recurring reminder $id", it) }
            }
        }
    }

    private fun showNotification(context: Context, id: String, body: String) {
        ensureChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            id.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // "Done" action — dismisses the reminder
        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DONE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, id)
        }
        val donePi = PendingIntent.getBroadcast(
            context,
            "done_$id".hashCode(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // "Snooze 10 min" action
        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, id)
            putExtra(ReminderScheduler.EXTRA_REMINDER_BODY, body)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context,
            "snooze_$id".hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Ranti")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setSound(alarmSound)
            .addAction(R.mipmap.ic_launcher, "Done", donePi)
            .addAction(R.mipmap.ic_launcher, "Snooze 10 min", snoozePi)
            .build()

        notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(id.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ranti Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Ranti reminder alerts"
            enableVibration(true)
            setShowBadge(true)
            setSound(alarmSound, audioAttributes)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Compute the next occurrence of a recurring reminder purely on-device and
     * reschedule. Mirrors worker/src/lib/time.ts::nextOccurrence. Having this
     * locally means a paused phone will still fire tomorrow's 8am reminder
     * without needing to hit the network first.
     */
    private fun rescheduleNext(context: Context, id: String, body: String, recurrenceJson: String) {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val rule = json.decodeFromString(RecurrenceDto.serializer(), recurrenceJson)

        val zone = ZoneId.systemDefault()
        val (hour, minute) = rule.time_of_day.split(":").let {
            Pair(it[0].toInt(), it[1].toInt())
        }
        val now = ZonedDateTime.now(zone)

        val next: ZonedDateTime = when (rule.frequency) {
            "daily" -> {
                val todayAt = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                if (todayAt.isAfter(now)) todayAt
                else todayAt.plusDays(rule.interval.toLong())
            }
            "weekly" -> {
                val wanted = (rule.by_weekday ?: listOf("mon")).map(::weekdayFromString).toSet()
                var candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                repeat(14 * rule.interval) {
                    if (candidate.isAfter(now) && wanted.contains(candidate.dayOfWeek)) {
                        return@repeat
                    }
                    candidate = candidate.plusDays(1)
                }
                // Advance until we hit one in the wanted set that's strictly after now.
                while (!(candidate.isAfter(now) && wanted.contains(candidate.dayOfWeek))) {
                    candidate = candidate.plusDays(1)
                }
                candidate
            }
            "monthly" -> {
                val days = rule.by_month_day ?: listOf(1)
                val sortedDays = days.sorted()
                var monthStart = now.withDayOfMonth(1).withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                var found: ZonedDateTime? = null
                repeat(12) {
                    for (d in sortedDays) {
                        val lengthOfMonth = monthStart.toLocalDate().lengthOfMonth()
                        if (d > lengthOfMonth) continue
                        val candidate = monthStart.withDayOfMonth(d)
                        if (candidate.isAfter(now)) { found = candidate; return@repeat }
                    }
                    monthStart = monthStart.plusMonths(rule.interval.toLong())
                }
                found ?: now.plusDays(1)
            }
            else -> now.plusDays(1)
        }

        // End-date check.
        if (rule.ends_on != null) {
            val endsAt = java.time.Instant.parse(rule.ends_on).atZone(zone)
            if (next.isAfter(endsAt)) {
                Log.d(TAG, "Recurring reminder $id has expired, not rescheduling")
                return
            }
        }

        val nextIso = next.toInstant().toString()
        val next_reminder = ReminderDto(
            id = id,
            body = body,
            status = "pending",
            source = "voice",
            created_at = java.time.Instant.now().toString(),
            next_fire_at = nextIso,
            trigger = TriggerDto(type = "time", fire_at = nextIso, original_expr = rule.original_expr),
            recurrence = rule,
        )
        ReminderScheduler.schedule(context, next_reminder)
        Log.d(TAG, "Rescheduled recurring reminder $id for $nextIso")
    }

    private fun weekdayFromString(s: String): DayOfWeek = when (s.lowercase()) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        "sun" -> DayOfWeek.SUNDAY
        else -> DayOfWeek.MONDAY
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "ranti_reminders_v3"
    }
}
