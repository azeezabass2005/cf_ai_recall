package com.ranti.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * SPEC §6.3 — Text-to-speech wrapper.
 *
 * Lazily initializes [TextToSpeech] on first [speak]. Locale prefers en-NG and
 * falls back to en-US if the Nigerian voice isn't installed. Speed 0.95x,
 * pitch 1.0 per spec.
 *
 * Caller must invoke [shutdown] when done (typically `ViewModel.onCleared`).
 */
class TextToSpeechManager(private val context: Context) {

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pendingUtterances = mutableListOf<String>()

    private val initListener = TextToSpeech.OnInitListener { status ->
        if (status != TextToSpeech.SUCCESS) {
            tts = null
            return@OnInitListener
        }
        val engine = tts ?: return@OnInitListener
        // Prefer en-NG, fall back to en-US.
        val ng = Locale("en", "NG")
        val result = engine.setLanguage(ng)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.US
        }
        engine.setSpeechRate(0.95f)
        engine.setPitch(1.0f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
            override fun onDone(utteranceId: String?) { _isSpeaking.value = false }
            @Deprecated("Deprecated in API 21", ReplaceWith(""))
            override fun onError(utteranceId: String?) { _isSpeaking.value = false }
            override fun onError(utteranceId: String?, errorCode: Int) { _isSpeaking.value = false }
        })
        ready = true
        // Drain anything queued before init finished.
        val drain = pendingUtterances.toList()
        pendingUtterances.clear()
        drain.forEach { speakNow(it) }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null) {
            pendingUtterances += text
            tts = TextToSpeech(context.applicationContext, initListener)
            return
        }
        if (!ready) {
            pendingUtterances += text
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val engine = tts ?: return
        val id = UUID.randomUUID().toString()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        pendingUtterances.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        _isSpeaking.value = false
    }
}
