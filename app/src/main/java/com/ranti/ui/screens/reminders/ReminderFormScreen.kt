package com.ranti.ui.screens.reminders

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ranti.network.RantiApi
import com.ranti.network.ReminderDto
import com.ranti.reminders.ReminderScheduler
import com.ranti.ui.components.ReminderCard
import com.ranti.ui.theme.LocalRantiColors
import com.ranti.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * SPEC 11.3 — Manual reminder builder. Handles both create and edit modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderFormScreen(
    editReminderId: String? = null,
    onNavigateBack: () -> Unit,
    remindersVm: RemindersViewModel = viewModel(),
    formVm: ReminderFormViewModel = viewModel(),
) {
    val state by formVm.state.collectAsStateWithLifecycle()
    val ranti = LocalRantiColors.current
    val context = LocalContext.current
    val isEdit = editReminderId != null

    // Pre-populate for edit mode
    LaunchedEffect(editReminderId) {
        if (editReminderId != null) {
            val existing = remindersVm.getReminderById(editReminderId)
            if (existing != null) formVm.populateFromExisting(existing)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isEdit) "Edit Reminder" else "New Reminder",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Body input
            OutlinedTextField(
                value = state.body,
                onValueChange = { formVm.updateBody(it) },
                label = { Text("What should I remind you about?") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                isError = state.bodyError,
                supportingText = if (state.bodyError) {{ Text("Required") }} else null,
            )

            // Date picker
            Text("Date", style = MaterialTheme.typography.labelMedium, color = ranti.textMid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                DateButton(
                    date = state.date,
                    onDateChange = { formVm.updateDate(it) },
                    modifier = Modifier.weight(1f),
                )
                TimeButton(
                    time = state.time,
                    onTimeChange = { formVm.updateTime(it) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Repeat toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Repeat this reminder", style = MaterialTheme.typography.bodyMedium, color = ranti.textHi)
                Spacer(Modifier.weight(1f))
                Switch(checked = state.isRepeating, onCheckedChange = { formVm.toggleRepeat(it) })
            }

            // Recurrence config
            if (state.isRepeating) {
                RecurrenceSection(state, formVm)
            }

            // Live preview
            if (state.body.isNotBlank()) {
                Text("Preview", style = MaterialTheme.typography.labelMedium, color = ranti.textMid)
                ReminderCard(reminder = formVm.buildPreviewDto())
            }

            Spacer(Modifier.weight(1f))

            // Submit
            Button(
                onClick = {
                    formVm.submit(
                        editId = editReminderId,
                        onSuccess = { dto ->
                            if (dto.trigger?.type == "time") {
                                ReminderScheduler.schedule(context, dto)
                            }
                            remindersVm.loadReminders()
                            Toast.makeText(context, "Reminder saved", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                        onError = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                enabled = !state.isSaving,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save Reminder", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(date: LocalDate, onDateChange: (LocalDate) -> Unit, modifier: Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("EEE, MMM d")

    OutlinedButton(onClick = { showPicker = true }, modifier = modifier, shape = MaterialTheme.shapes.large) {
        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(date.format(fmt))
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val picked = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        onDateChange(picked)
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(time: LocalTime, onTimeChange: (LocalTime) -> Unit, modifier: Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("h:mm a")

    OutlinedButton(onClick = { showPicker = true }, modifier = modifier, shape = MaterialTheme.shapes.large) {
        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(time.format(fmt))
    }

    if (showPicker) {
        val pickerState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(pickerState.hour, pickerState.minute))
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}

@Composable
private fun RecurrenceSection(state: FormState, vm: ReminderFormViewModel) {
    val ranti = LocalRantiColors.current
    val frequencies = listOf("daily", "weekly", "monthly")

    // Frequency picker
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        frequencies.forEach { freq ->
            FilterChip(
                selected = state.frequency == freq,
                onClick = { vm.updateFrequency(freq) },
                label = { Text(freq.replaceFirstChar { it.uppercase() }) },
            )
        }
    }

    // Weekday picker (weekly)
    if (state.frequency == "weekly") {
        val days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            days.forEach { day ->
                FilterChip(
                    selected = day in state.byWeekday,
                    onClick = { vm.toggleWeekday(day) },
                    label = { Text(day.replaceFirstChar { it.uppercase() }) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // Interval stepper
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Every", style = MaterialTheme.typography.bodyMedium, color = ranti.textHi)
        Spacer(Modifier.width(Spacing.sm))
        IconButton(onClick = { vm.updateInterval(maxOf(1, state.interval - 1)) }) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease")
        }
        Text("${state.interval}", style = MaterialTheme.typography.headlineSmall, color = ranti.textHi)
        IconButton(onClick = { vm.updateInterval(state.interval + 1) }) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
        Text(
            when (state.frequency) {
                "daily" -> if (state.interval > 1) "days" else "day"
                "weekly" -> if (state.interval > 1) "weeks" else "week"
                "monthly" -> if (state.interval > 1) "months" else "month"
                else -> state.frequency
            },
            style = MaterialTheme.typography.bodyMedium,
            color = ranti.textHi,
        )
    }
}

// ─── ViewModel ──────────────────────────────────────────────────────────────

class ReminderFormViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RantiApi(application)
    private val _state = MutableStateFlow(FormState())
    val state: StateFlow<FormState> = _state.asStateFlow()

    fun updateBody(v: String) = _state.update { it.copy(body = v.take(200), bodyError = false) }
    fun updateDate(v: LocalDate) = _state.update { it.copy(date = v) }
    fun updateTime(v: LocalTime) = _state.update { it.copy(time = v) }
    fun toggleRepeat(on: Boolean) = _state.update { it.copy(isRepeating = on) }
    fun updateFrequency(f: String) = _state.update { it.copy(frequency = f) }
    fun updateInterval(n: Int) = _state.update { it.copy(interval = n) }
    fun toggleWeekday(day: String) = _state.update {
        val new = it.byWeekday.toMutableSet()
        if (day in new) new.remove(day) else new.add(day)
        it.copy(byWeekday = new)
    }

    fun populateFromExisting(r: ReminderDto) {
        val fireAt = r.next_fire_at ?: r.trigger?.fire_at
        val zoned = if (fireAt != null) {
            try { java.time.Instant.parse(fireAt).atZone(ZoneId.systemDefault()) } catch (_: Exception) { null }
        } else null

        _state.update {
            it.copy(
                body = r.body,
                date = zoned?.toLocalDate() ?: LocalDate.now(),
                time = zoned?.toLocalTime() ?: LocalTime.now().plusMinutes(30),
                isRepeating = r.recurrence != null,
                frequency = r.recurrence?.frequency ?: "daily",
                interval = r.recurrence?.interval ?: 1,
                byWeekday = r.recurrence?.by_weekday?.toSet() ?: emptySet(),
            )
        }
    }

    fun buildPreviewDto(): ReminderDto {
        val s = _state.value
        val fireIso = ZonedDateTime.of(s.date, s.time, ZoneId.systemDefault()).toInstant().toString()
        return ReminderDto(
            id = "preview",
            body = s.body,
            status = "pending",
            source = "manual_form",
            created_at = java.time.Instant.now().toString(),
            next_fire_at = fireIso,
            trigger = com.ranti.network.TriggerDto(type = "time", fire_at = fireIso),
            recurrence = if (s.isRepeating) com.ranti.network.RecurrenceDto(
                frequency = s.frequency,
                interval = s.interval,
                by_weekday = if (s.frequency == "weekly") s.byWeekday.toList() else null,
                time_of_day = "%02d:%02d".format(s.time.hour, s.time.minute),
                starts_on = s.date.toString(),
                original_expr = "manual",
            ) else null,
        )
    }

    fun submit(editId: String?, onSuccess: (ReminderDto) -> Unit, onError: (String) -> Unit) {
        val s = _state.value
        if (s.body.isBlank()) {
            _state.update { it.copy(bodyError = true) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val fireIso = ZonedDateTime.of(s.date, s.time, ZoneId.systemDefault()).toInstant().toString()
                val result = if (editId != null) {
                    val timeExpr = if (s.isRepeating) buildRecurrenceExpr(s) else null
                    api.updateReminder(editId, newBody = s.body, newTimeExpr = timeExpr)
                        ?: throw Exception("Update failed")
                } else {
                    if (s.isRepeating) {
                        api.createReminder(body = s.body, timeExpr = buildRecurrenceExpr(s))
                    } else {
                        api.createReminder(body = s.body, fireAt = fireIso)
                    }
                }
                _state.update { it.copy(isSaving = false) }
                onSuccess(result)
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false) }
                onError(e.message ?: "Failed to save reminder")
            }
        }
    }

    private fun buildRecurrenceExpr(s: FormState): String {
        val timeStr = "%d:%02d".format(
            if (s.time.hour == 0) 12 else if (s.time.hour > 12) s.time.hour - 12 else s.time.hour,
            s.time.minute,
        ) + if (s.time.hour >= 12) "pm" else "am"
        return when (s.frequency) {
            "daily" -> "every day at $timeStr"
            "weekly" -> {
                val days = s.byWeekday.ifEmpty { setOf("mon") }
                "every ${days.joinToString(" and ")} at $timeStr"
            }
            "monthly" -> "every month on the ${s.date.dayOfMonth} at $timeStr"
            else -> "every day at $timeStr"
        }
    }
}

data class FormState(
    val body: String = "",
    val bodyError: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = run {
        val now = LocalTime.now()
        val nextHalf = if (now.minute < 30) now.withMinute(30) else now.plusHours(1).withMinute(0)
        nextHalf.withSecond(0).withNano(0)
    },
    val isRepeating: Boolean = false,
    val frequency: String = "daily",
    val interval: Int = 1,
    val byWeekday: Set<String> = emptySet(),
    val isSaving: Boolean = false,
)
