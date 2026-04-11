package com.ranti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ranti.network.ReminderDto
import com.ranti.ui.theme.LocalRantiColors
import com.ranti.ui.theme.Spacing
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * SPEC 11.1 — Reusable reminder card for the list, detail preview, and form live preview.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReminderCard(
    reminder: ReminderDto,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ranti = LocalRantiColors.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp), // Sleek subtle shadow
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            // Body text
            Text(
                text = reminder.body,
                style = MaterialTheme.typography.headlineSmall,
                color = ranti.textHi,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(Spacing.sm))

            // Trigger pill + recurrence pill row
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Time pill
                if (reminder.trigger?.type == "time") {
                    val fireAt = reminder.next_fire_at ?: reminder.trigger.fire_at
                    if (fireAt != null) {
                        TriggerPill(
                            text = formatFireAt(fireAt),
                            icon = Icons.Default.Schedule,
                            color = ranti.timeTrigger,
                            bgColor = ranti.timeTriggerSoft,
                        )
                    }
                } else if (reminder.trigger?.type == "location") {
                    TriggerPill(
                        text = reminder.trigger.place_name ?: "Location",
                        icon = Icons.Default.LocationOn,
                        color = ranti.locationTrigger,
                        bgColor = ranti.locationTriggerSoft,
                    )
                }

                // Recurrence pill
                if (reminder.recurrence != null) {
                    RecurrencePill(reminder.recurrence)
                }

                Spacer(Modifier.weight(1f))

                // Status badge
                StatusBadge(reminder.status)
            }

            // Countdown for pending time-based reminders
            if (reminder.status == "pending" && reminder.trigger?.type == "time") {
                val fireAt = reminder.next_fire_at ?: reminder.trigger.fire_at
                if (fireAt != null) {
                    val countdown = countdownText(fireAt)
                    if (countdown != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = countdown,
                            style = MaterialTheme.typography.labelMedium,
                            color = ranti.textLo,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TriggerPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun RecurrencePill(recurrence: com.ranti.network.RecurrenceDto) {
    val ranti = LocalRantiColors.current
    val label = buildString {
        append("Every ")
        if (recurrence.interval > 1) append("${recurrence.interval} ")
        when (recurrence.frequency) {
            "daily" -> append(if (recurrence.interval > 1) "days" else "day")
            "weekly" -> {
                val days = recurrence.by_weekday?.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
                append(days ?: "week")
            }
            "monthly" -> append(if (recurrence.interval > 1) "months" else "month")
            else -> append(recurrence.frequency)
        }
        append(" \u00b7 ${recurrence.time_of_day}")
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = ranti.accent,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ranti.accentSoft)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

@Composable
private fun StatusBadge(status: String) {
    val ranti = LocalRantiColors.current
    val (text, color) = when (status) {
        "pending" -> "Active" to ranti.success
        "paused" -> "Paused" to ranti.warning
        "snoozed" -> "Snoozed" to ranti.warning
        "fired" -> "Fired" to ranti.textLo
        "dismissed" -> "Done" to ranti.textLo
        "expired" -> "Expired" to ranti.textLo
        else -> status.replaceFirstChar { it.uppercase() } to ranti.textMid
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

private val timeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d \u00b7 h:mm a", Locale.getDefault())

private fun formatFireAt(iso: String): String {
    return try {
        val instant = Instant.parse(iso)
        val local = instant.atZone(ZoneId.systemDefault())
        local.format(timeFormatter)
    } catch (_: Exception) {
        iso
    }
}

private fun countdownText(iso: String): String? {
    return try {
        val fireAt = Instant.parse(iso)
        val now = Instant.now()
        if (fireAt.isBefore(now)) return null
        val dur = Duration.between(now, fireAt)
        val hours = dur.toHours()
        val minutes = dur.toMinutes() % 60
        when {
            hours > 24 -> "in ${hours / 24}d ${hours % 24}h"
            hours > 0 -> "in ${hours}h ${minutes}m"
            minutes > 0 -> "in ${minutes}m"
            else -> "any moment"
        }
    } catch (_: Exception) {
        null
    }
}
