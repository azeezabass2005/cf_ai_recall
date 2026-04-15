package com.recall.network

import kotlinx.serialization.Serializable

/**
 * Wire types for the Recall Worker API. These mirror worker/src/lib/types.ts —
 * keep the two in sync until we generate one from the other.
 */

@Serializable
data class ChatRequest(
    val message: String,
    val input_mode: String = "text",
    val tz: String,
    val client_now: String,
    val user_lat: Double? = null,
    val user_lng: Double? = null,
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

// ─── Nickname models ────────────────────────────────────────────────────────

@Serializable
data class NicknameDto(
    val id: String,
    val device_id: String,
    val nickname: String,
    val place_name: String,
    val place_id: String? = null,
    val lat: Double,
    val lng: Double,
    val created_at: String,
    val updated_at: String,
)

@Serializable
data class SaveNicknameRequest(
    val nickname: String,
    val place_name: String,
    val place_id: String? = null,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class NicknameListResponse(
    val nicknames: List<NicknameDto>,
)

@Serializable
data class NicknameSaveResponse(
    val nickname: NicknameDto,
)

@Serializable
data class NicknameDeleteResponse(
    val deleted: NicknameDto? = null,
)

// ─── Place resolution models ────────────────────────────────────────────────

@Serializable
data class ResolvePlaceRequest(
    val query: String,
    val bias_lat: Double? = null,
    val bias_lng: Double? = null,
)

@Serializable
data class ResolvePlaceResponse(
    val places: List<PlaceOption>,
)
