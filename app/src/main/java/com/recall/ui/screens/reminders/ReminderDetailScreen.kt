package com.recall.ui.screens.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recall.network.ReminderDto
import com.recall.ui.components.RecurrencePill
import com.recall.ui.theme.LocalRecallColors
import com.recall.ui.theme.Spacing
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * SPEC 11.2 — Full detail view for a single reminder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailScreen(
    reminderId: String,
    onNavigateBack: () -> Unit,
    onEdit: (String) -> Unit,
    vm: RemindersViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val reminder = vm.getReminderById(reminderId)
    val recall = LocalRecallColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // If the VM is shared via nested nav graph, data is already loaded.
    // Fallback: if opened from a deep link or the cache was cleared, reload.
    LaunchedEffect(reminderId) {
        if (reminder == null && !state.isLoading) vm.loadReminders()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Reminder", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (reminder == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) CircularProgressIndicator()
                else Text("Reminder not found", color = recall.textMid)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Body
            Text(
                text = reminder.body,
                style = MaterialTheme.typography.headlineLarge,
                color = recall.textHi,
            )

            // Trigger info
            if (reminder.trigger?.type == "time") {
                val fireAt = reminder.next_fire_at ?: reminder.trigger.fire_at
                if (fireAt != null) {
                    DetailRow(
                        icon = Icons.Default.Schedule,
                        label = "Fires at",
                        value = formatFull(fireAt),
                        color = recall.timeTrigger,
                    )
                }
            } else if (reminder.trigger?.type == "location") {
                DetailRow(
                    icon = Icons.Default.LocationOn,
                    label = "Location",
                    value = reminder.trigger.place_name ?: "Unknown",
                    color = recall.locationTrigger,
                )
            }

            // Recurrence
            if (reminder.recurrence != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Repeat, contentDescription = null, tint = recall.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    RecurrencePill(reminder.recurrence)
                }
            }

            // Countdown
            if (reminder.status == "pending" && reminder.trigger?.type == "time") {
                val fireAt = reminder.next_fire_at ?: reminder.trigger.fire_at
                if (fireAt != null) {
                    val countdown = countdownFull(fireAt)
                    if (countdown != null) {
                        DetailRow(Icons.Default.Timer, "Countdown", countdown, recall.textMid)
                    }
                }
            }

            HorizontalDivider(color = recall.borderSubtle)

            // Metadata
            DetailRow(Icons.Default.CalendarToday, "Created", formatFull(reminder.created_at), recall.textLo)
            if (reminder.fire_count > 0) {
                DetailRow(Icons.Default.Notifications, "Fired", "${reminder.fire_count} time${if (reminder.fire_count > 1) "s" else ""}", recall.textLo)
            }
            DetailRow(Icons.Default.Source, "Source", reminder.source.replace("_", " ").replaceFirstChar { it.uppercase() }, recall.textLo)

            Spacer(Modifier.weight(1f))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedButton(
                    onClick = { onEdit(reminder.id) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Edit")
                }
                if (reminder.recurrence != null) {
                    val isPaused = reminder.status == "paused"
                    OutlinedButton(
                        onClick = {
                            if (isPaused) vm.resumeReminder(reminder.id)
                            else vm.pauseReminder(reminder.id)
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(if (isPaused) "Resume" else "Pause")
                    }
                }
            }
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = recall.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Delete")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete reminder?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteReminder(reminderId)
                    showDeleteConfirm = false
                    onNavigateBack()
                }) { Text("Delete", color = recall.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
) {
    val recall = LocalRecallColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text = "$label: ", style = MaterialTheme.typography.labelMedium, color = recall.textMid)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = recall.textHi)
    }
}

private val fullFormatter = DateTimeFormatter.ofPattern("EEE, MMM d yyyy \u00b7 h:mm a", Locale.getDefault())

private fun formatFull(iso: String): String {
    return try {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(fullFormatter)
    } catch (_: Exception) {
        iso
    }
}

private fun countdownFull(iso: String): String? {
    return try {
        val dur = Duration.between(Instant.now(), Instant.parse(iso))
        if (dur.isNegative) return null
        val d = dur.toDays()
        val h = dur.toHours() % 24
        val m = dur.toMinutes() % 60
        buildString {
            if (d > 0) append("${d}d ")
            if (h > 0) append("${h}h ")
            append("${m}m")
        }.trim()
    } catch (_: Exception) {
        null
    }
}
