package com.ranti.network

import kotlinx.serialization.Serializable

/**
 * Wire types for the Ranti Worker API. These mirror worker/src/lib/types.ts —
 * keep the two in sync until we generate one from the other.
 */

@Serializable
data class ChatRequest(
    val message: String,
    val input_mode: String = "text",
    val tz: String,
    val client_now: String,
)

@Serializable
data class ChatResponse(
    val response_text: String,
    val action: String,
    val reminder: ReminderDto? = null,
    val reminders: List<ReminderDto>? = null,
    val disambiguation: DisambiguationDto? = null,
)

/**
 * Mirrors worker/src/lib/types.ts::Reminder. We only deserialize the fields
 * the Android scheduler needs — trigger/recurrence come through as raw JSON
 * strings so we don't duplicate the whole type hierarchy twice.
 */
@Serializable
data class ReminderDto(
    val id: String,
    val body: String,
    val status: String,
    val source: String,
    val created_at: String,
    val next_fire_at: String? = null,
    val trigger: TriggerDto? = null,
    val recurrence: RecurrenceDto? = null,
    val fire_count: Int = 0,
)

@Serializable
data class TriggerDto(
    val type: String,
    val fire_at: String? = null,
    val original_expr: String? = null,
    val place_name: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val radius_m: Double? = null,
)

@Serializable
data class RecurrenceDto(
    val frequency: String,
    val interval: Int,
    val by_weekday: List<String>? = null,
    val by_month_day: List<Int>? = null,
    val time_of_day: String,
    val starts_on: String,
    val ends_on: String? = null,
    val original_expr: String,
)

@Serializable
data class DisambiguationDto(
    val prompt: String,
    val options: List<PlaceOption>,
)

@Serializable
data class PlaceOption(
    val place_id: String,
    val name: String,
    val formatted_address: String,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class HealthResponse(
    val ok: Boolean,
    val service: String,
    val time: String,
)

// ─── REST API request/response models ──────────────────────────────────────

@Serializable
data class CreateReminderRequest(
    val body: String,
    val time_expr: String? = null,
    val fire_at: String? = null,
)

@Serializable
data class UpdateReminderRequest(
    val new_body: String? = null,
    val new_time_expr: String? = null,
)

@Serializable
data class SnoozeRequest(
    val minutes: Int = 10,
)

@Serializable
data class ReminderListResponse(
    val reminders: List<ReminderDto>,
)

@Serializable
data class ReminderCreateResponse(
    val reminder: ReminderDto,
    val note: String? = null,
)

@Serializable
data class ReminderUpdateResponse(
    val updated: ReminderDto? = null,
)

@Serializable
data class ReminderDeleteResponse(
    val deleted: ReminderDto? = null,
)

@Serializable
data class ReminderPauseResponse(
    val paused: ReminderDto? = null,
)

@Serializable
data class ReminderResumeResponse(
    val resumed: ReminderDto? = null,
)

@Serializable
data class ReminderSnoozeResponse(
    val snoozed: ReminderDto? = null,
)
