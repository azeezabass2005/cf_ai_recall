package com.ranti.voice

import android.content.Context

/**
 * SPEC §6.1 — abstraction over a wake-word detector so the rest of the app
 * doesn't depend on the engine implementation directly.
 *
 * The real implementation is [VoskWakeWordEngine] which uses Vosk's offline
 * grammar-mode recognizer constrained to "hi ranti" / "hey ranti". It runs
 * an AudioRecord loop on a background thread — no network, no API keys.
 *
 * [NoopWakeWordEngine] is kept as a fallback for environments where the Vosk
 * model isn't available or for testing scenarios.
 */
interface WakeWordEngine {
    fun start(onDetected: () -> Unit)
    fun stop()

    /**
     * Release the microphone but keep the model loaded. Used when another
     * app wants the mic (other assistants, our own SpeechRecognizer in voice
     * mode, etc.) so we don't block them.
     */
    fun pause()

    /**
     * Re-acquire the microphone after a [pause]. No-op if not paused.
     */
    fun resume()

    fun setSensitivity(value: Float)
}

class NoopWakeWordEngine : WakeWordEngine {
    override fun start(onDetected: () -> Unit) = Unit
    override fun stop() = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun setSensitivity(value: Float) = Unit
}

/**
 * Factory used by [com.ranti.service.WakeWordService]. Returns the Vosk-based
 * wake word engine for real "Hi Ranti" detection.
 */
fun createDefaultEngine(context: Context): WakeWordEngine =
    VoskWakeWordEngine(context)
