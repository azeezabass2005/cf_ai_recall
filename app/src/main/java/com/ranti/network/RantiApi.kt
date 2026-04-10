package com.ranti.network

import android.content.Context
import com.ranti.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * HTTP client for the Ranti Worker. Covers chat (LLM path) and direct
 * REST CRUD for reminders (manual form path, no LLM involvement).
 */
class RantiApi(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        // Workers AI (Llama 3.3) can take 20-30 s on a cold start or when
        // running a multi-step tool-use loop. Set generous timeouts so the
        // user isn't greeted with a socket error instead of Ranti's reply.
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 90_000   // full round-trip incl. LLM
            socketTimeoutMillis  = 90_000
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    private val baseUrl: String get() = BuildConfig.RANTI_BASE_URL

    suspend fun health(): String =
        client.get("$baseUrl/health").bodyAsText()

    suspend fun chat(message: String, inputMode: String = "text"): ChatResponse {
        val deviceId = DeviceId.get(context)
        val tz = java.util.TimeZone.getDefault().id
        val clientNow = java.time.Instant.now().toString()
        val response = client.post("$baseUrl/chat") {
            headers.append("X-Ranti-Device", deviceId)
            setBody(
                ChatRequest(
                    message = message,
                    input_mode = inputMode,
                    tz = tz,
                    client_now = clientNow,
                ),
            )
        }
        return json.decodeFromString(ChatResponse.serializer(), response.bodyAsText())
    }

    // ─── Reminder CRUD (REST, no LLM) ──────────────────────────────────

    private suspend fun HttpRequestBuilder.deviceHeaders() {
        val deviceId = DeviceId.get(context)
        val tz = java.util.TimeZone.getDefault().id
        headers.append("X-Ranti-Device", deviceId)
        headers.append("X-Ranti-Tz", tz)
    }

    suspend fun listReminders(filter: String = "active"): List<ReminderDto> {
        val response = client.get("$baseUrl/reminders?filter=$filter") { deviceHeaders() }
        return json.decodeFromString(ReminderListResponse.serializer(), response.bodyAsText()).reminders
    }

    suspend fun createReminder(body: String, timeExpr: String? = null, fireAt: String? = null): ReminderDto {
        val response = client.post("$baseUrl/reminders") {
            deviceHeaders()
            setBody(CreateReminderRequest(body = body, time_expr = timeExpr, fire_at = fireAt))
        }
        return json.decodeFromString(ReminderCreateResponse.serializer(), response.bodyAsText()).reminder
    }

    suspend fun updateReminder(id: String, newBody: String? = null, newTimeExpr: String? = null): ReminderDto? {
        val response = client.patch("$baseUrl/reminders/$id") {
            deviceHeaders()
            setBody(UpdateReminderRequest(new_body = newBody, new_time_expr = newTimeExpr))
        }
        return json.decodeFromString(ReminderUpdateResponse.serializer(), response.bodyAsText()).updated
    }

    suspend fun deleteReminder(id: String): ReminderDto? {
        val response = client.delete("$baseUrl/reminders/$id") { deviceHeaders() }
        return json.decodeFromString(ReminderDeleteResponse.serializer(), response.bodyAsText()).deleted
    }

    suspend fun pauseReminder(id: String): ReminderDto? {
        val response = client.post("$baseUrl/reminders/$id/pause") { deviceHeaders() }
        return json.decodeFromString(ReminderPauseResponse.serializer(), response.bodyAsText()).paused
    }

    suspend fun resumeReminder(id: String): ReminderDto? {
        val response = client.post("$baseUrl/reminders/$id/resume") { deviceHeaders() }
        return json.decodeFromString(ReminderResumeResponse.serializer(), response.bodyAsText()).resumed
    }

    suspend fun snoozeReminder(id: String, minutes: Int = 10): ReminderDto? {
        val response = client.post("$baseUrl/reminders/$id/snooze") {
            deviceHeaders()
            setBody(SnoozeRequest(minutes = minutes))
        }
        return json.decodeFromString(ReminderSnoozeResponse.serializer(), response.bodyAsText()).snoozed
    }
}
