package com.ranti.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SPEC §6.2 — wraps Android's [SpeechRecognizer] in a small state-flow API
 * that the chat layer can consume without touching the recognizer's callback
 * machinery directly.
 *
 * Locale: en-NG with implicit fallback to whatever the device offers if the
 * Nigerian model isn't available — the platform handles that downgrade for us.
 *
 * Threading: all SpeechRecognizer methods must be called on the main thread,
 * so the manager itself is main-thread-only. Callers should construct it from
 * a Composable or ViewModel and call [start]/[cancel] from there.
 */
class SpeechRecognizerManager(private val context: Context) {

    enum class State { Idle, Listening, Processing, Error }

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Set by [start]; called once with the final transcript or null on error. */
    private var onResult: ((String?) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(onResult: (String?) -> Unit) {
        if (!hasMicPermission()) {
            _errorMessage.value = "Microphone permission is needed to listen."
            _state.value = State.Error
            onResult(null)
            return
        }
        if (!isAvailable()) {
            _errorMessage.value = "Speech recognition isn't available on this device."
            _state.value = State.Error
            onResult(null)
            return
        }

        // Tear down any previous session.
        cancel()

        this.onResult = onResult
        _partialText.value = ""
        _errorMessage.value = null

        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(listener)
        recognizer = r

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-NG")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-NG")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // SPEC §6.2: silence detection auto-stops after 2 seconds.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        _state.value = State.Listening
        r.startListening(intent)
    }

    fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        onResult = null
        _state.value = State.Idle
        _partialText.value = ""
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        cancel()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { _state.value = State.Listening }
        override fun onBeginningOfSpeech() { _state.value = State.Listening }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { _state.value = State.Processing }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _partialText.value = text
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _state.value = State.Idle
            val cb = onResult
            onResult = null
            cb?.invoke(text.takeIf { it.isNotBlank() })
        }

        override fun onError(error: Int) {
            _errorMessage.value = describeError(error)
            _state.value = State.Error
            val cb = onResult
            onResult = null
            cb?.invoke(null)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "I couldn't read from the microphone."
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
        SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Recognition timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Try again?"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy. Try again."
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything."
        else -> "I didn't catch that. Try again?"
    }
}
