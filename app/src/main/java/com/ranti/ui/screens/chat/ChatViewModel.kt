package com.ranti.ui.screens.chat

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.lifecycle.viewModelScope
import com.ranti.network.RantiApi
import com.ranti.reminders.ReminderScheduler
import com.ranti.service.WakeWordService
import com.ranti.ui.components.BubbleSender
import com.ranti.ui.components.OrbState
import com.ranti.voice.SpeechRecognizerManager
import com.ranti.voice.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * SPEC §5.2 / §6 — Chat ViewModel.
 *
 * In-memory conversation state plus the voice plumbing wired up in milestone
 * §6: tapping the mic spins up a [SpeechRecognizerManager] session, partial
 * transcripts feed the orb's "ghost text", the final transcript is sent
 * through the same path as a typed message, and Ranti's reply is spoken back
 * via [TextToSpeechManager] when the input was voice.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val api = RantiApi(application)
    private val speech = SpeechRecognizerManager(application)
    private val tts = TextToSpeechManager(application)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        // Mirror the recognizer's partial transcript into chat state so the
        // voice bar can show it under the orb.
        speech.partialText
            .onEach { partial -> _state.update { it.copy(partialTranscript = partial) } }
            .launchIn(viewModelScope)

        // Map recognizer + TTS state into the orb state for the voice bar.
        combine(speech.state, tts.isSpeaking) { recState, speaking ->
            when {
                speaking -> OrbState.Speaking
                recState == SpeechRecognizerManager.State.Listening -> OrbState.Listening
                recState == SpeechRecognizerManager.State.Processing -> OrbState.Processing
                else -> OrbState.Idle
            }
        }
            .onEach { orb -> _state.update { it.copy(voiceState = orb) } }
            .launchIn(viewModelScope)

        // Surface recognizer errors as a transient field the UI can render.
        speech.errorMessage
            .onEach { msg -> _state.update { it.copy(voiceError = msg) } }
            .launchIn(viewModelScope)
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    /** Tap the mic icon: flip into voice mode and start listening. */
    fun toggleInputMode() {
        val current = _state.value.inputMode
        if (current == InputMode.Text) {
            _state.update {
                it.copy(
                    inputMode = InputMode.Voice,
                    voiceState = OrbState.Listening,
                    partialTranscript = "",
                    voiceError = null,
                )
            }
            startListening()
        } else {
            cancelVoice()
        }
    }

    /** Tap the keyboard icon while in voice mode: bail out. */
    fun cancelVoice() {
        speech.cancel()
        tts.stop()
        // Hand the mic back to the wake-word service.
        WakeWordService.resumeEngine(getApplication())
        _state.update {
            it.copy(
                inputMode = InputMode.Text,
                voiceState = OrbState.Idle,
                partialTranscript = "",
            )
        }
    }

    /** Wake-word handler — bring chat into voice mode and start listening. */
    fun onWakeWordDetected() {
        if (_state.value.inputMode != InputMode.Voice) {
            _state.update { it.copy(inputMode = InputMode.Voice) }
        }
        startListening()
    }

    private fun startListening() {
        if (!speech.isAvailable() || !speech.hasMicPermission()) {
            _state.update {
                it.copy(
                    voiceError = if (!speech.hasMicPermission())
                        "Microphone permission is needed to listen."
                    else
                        "Speech recognition isn't available on this device.",
                    voiceState = OrbState.Idle,
                )
            }
            return
        }
        // Tell the wake-word service to release the mic so SpeechRecognizer
        // can actually capture audio. The service's AudioRecordingCallback
        // would catch this eventually anyway, but the explicit call avoids
        // a race where SpeechRecognizer fails to initialise.
        WakeWordService.pauseEngine(getApplication())

        speech.start { transcript ->
            if (transcript.isNullOrBlank()) {
                // Stay in voice mode so the user can tap to retry — error text
                // is already surfaced via [voiceError]. Hand the mic back so
                // the wake word still works.
                WakeWordService.resumeEngine(getApplication())
                return@start
            }
            sendInternal(transcript, InputMode.Voice)
        }
    }

    fun send() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isProcessing) return
        sendInternal(text, InputMode.Text)
    }

    private fun sendInternal(text: String, mode: InputMode) {
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = BubbleSender.User,
            text = text,
            inputMode = mode,
        )
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = if (mode == InputMode.Text) "" else it.inputText,
                isProcessing = true,
                partialTranscript = "",
            )
        }

        viewModelScope.launch {
            val apiModeLabel = if (mode == InputMode.Voice) "voice" else "text"
            val chatResult = runCatching { api.chat(text, apiModeLabel) }
            val replyText = chatResult
                .map { it.response_text }
                .getOrElse { e -> "Sorry — I couldn't reach the server.\n(${e.message})" }

            // SPEC §7.3 — handle the structured reminder payload from the
            // worker. The worker is the source of truth for parsing & D1
            // persistence; we mirror time-based reminders into a local
            // AlarmManager alarm so they fire even when the network is down.
            chatResult.getOrNull()?.let { response ->
                when (response.action) {
                    "reminder_created", "reminder_updated", "reminder_resumed" ->
                        response.reminder?.let {
                            if (it.trigger?.type == "time") {
                                ReminderScheduler.schedule(getApplication(), it)
                                showSchedulingDiagnostic(it.next_fire_at ?: it.trigger.fire_at)
                            }
                        }
                    "reminder_deleted", "reminder_paused" ->
                        response.reminder?.let { ReminderScheduler.cancel(getApplication(), it.id) }
                    else -> { /* noop — listing / disambiguation etc */ }
                }
            }

            val rantiMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = BubbleSender.Ranti,
                text = replyText,
                inputMode = mode,
            )
            _state.update {
                it.copy(
                    messages = it.messages + rantiMessage,
                    isProcessing = false,
                )
            }

            // SPEC §6.3 — only speak voice-mode replies.
            if (mode == InputMode.Voice) {
                tts.speak(replyText)
                // Hand the mic back to the wake-word service now that our
                // recognizer is done. (TTS uses the speaker, not the mic.)
                WakeWordService.resumeEngine(getApplication())
            }
        }
    }

    /**
     * Show a short toast confirming an alarm was scheduled, and call out the
     * inexact-alarm fallback — that's the #1 reason a reminder "doesn't fire"
     * on Android 12+ and it's otherwise invisible to the user.
     *
     * We format the fire time in the user's LOCAL wall-clock zone — the
     * server hands us UTC ISO ("...T16:03:00Z") and displaying it raw makes
     * every reminder look an hour off for any non-UTC user.
     */
    private fun showSchedulingDiagnostic(fireAt: String?) {
        val app = getApplication<Application>()
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am?.canScheduleExactAlarms() == true
        } else true
        val when_ = formatLocal(fireAt)
        val msg = if (canExact) {
            "Reminder set for $when_"
        } else {
            "Reminder set for $when_ — but exact alarms are OFF. " +
                "Enable 'Alarms & reminders' in app settings so it fires on time."
        }
        Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
    }

    private fun formatLocal(isoUtc: String?): String {
        if (isoUtc.isNullOrBlank()) return "(unknown time)"
        return try {
            val instant = Instant.parse(isoUtc)
            val local = instant.atZone(ZoneId.systemDefault())
            // "Fri 3:45 PM" — compact and unambiguous.
            val fmt = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.getDefault())
            local.format(fmt)
        } catch (_: Exception) {
            isoUtc
        }
    }

    override fun onCleared() {
        super.onCleared()
        speech.destroy()
        tts.shutdown()
        // Make sure we don't leave the wake-word service paused if the
        // ViewModel dies while voice mode was active.
        WakeWordService.resumeEngine(getApplication())
    }

    private fun initialState() = ChatState(
        messages = listOf(
            ChatMessage(
                id = "welcome",
                sender = BubbleSender.Ranti,
                text = "Hi! I'm Ranti. Tell me what to remember and when, and I'll make sure you don't forget.",
                inputMode = InputMode.Text,
            ),
        ),
        inputText = "",
        inputMode = InputMode.Text,
        voiceState = OrbState.Idle,
        isProcessing = false,
        partialTranscript = "",
        voiceError = null,
    )
}

// ─── State types ─────────────────────────────────────────────────────────────

enum class InputMode { Text, Voice }

data class ChatMessage(
    val id: String,
    val sender: BubbleSender,
    val text: String,
    val inputMode: InputMode,
)

data class ChatState(
    val messages: List<ChatMessage>,
    val inputText: String,
    val inputMode: InputMode,
    val voiceState: OrbState,
    val isProcessing: Boolean,
    val partialTranscript: String,
    val voiceError: String?,
)
