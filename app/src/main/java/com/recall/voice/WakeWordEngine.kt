package com.recall.voice

import android.content.Context

/**
 * SPEC §6.1 — abstraction over a wake-word detector so the rest of the app
 * doesn't depend on the engine implementation directly.
 *
 * The real implementation is [PocketSphinxWakeWordEngine] which uses CMU
 * PocketSphinx offline keyword-spotting constrained to "hi recall" / "hey
 * recall". It runs PocketSphinx's own internal audio thread — no network,
 * no API keys, no continuous AudioRecord in our code.
 *
 * [NoopWakeWordEngine] is kept as a fallback for testing scenarios or
 * environments where the PocketSphinx model fails to load.
 */
interface WakeWordEngine {
    fun start(onDetected: () -> Unit)
    fun stop()

    /**
     * Release the microphone but keep the recognizer loaded.
     * Used when the chat layer's SpeechRecognizer needs the mic, or when
     * another assistant (Google, Bixby) is actively capturing audio.
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
 * Factory for creating the default wake word engine.
 * Returns the PocketSphinx-based wake word engine for in-app "Hi Recall" detection.
 */
fun createDefaultEngine(context: Context): WakeWordEngine =
    PocketSphinxWakeWordEngine(context)
