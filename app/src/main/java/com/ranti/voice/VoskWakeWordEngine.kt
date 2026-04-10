package com.ranti.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService

/**
 * SPEC §6.1 — Vosk-based wake word engine.
 *
 * Vosk's grammar-mode decoder requires every word in the grammar to exist in
 * the model's lexicon. The shipping Vosk small en-US model does **not** know
 * the Yoruba name "ranti" — feeding it an OOV word produces a decoder that
 * never matches anything, which is why the original `"hi ranti"` grammar
 * silently failed.
 *
 * The fix is to spell the wake phrase with words the model already knows. We
 * accept several variants:
 *   - "hi recall" / "hey recall"  — the canonical English wake phrase.
 *   - "hi ronnie" / "hey ronnie"  — phonetic approximation of "Hi Ranti";
 *   - "hi randy"  / "hey randy"   — another phonetic approximation;
 *   so the user can naturally say "Hi Ranti" and the decoder will pick
 *   whichever in-vocab phrase scores highest.
 *
 * Audio is captured via [AudioRecord] at 16 kHz / mono / 16-bit PCM on a
 * dedicated [HandlerThread]. When the recognizer returns a result matching
 * one of the wake phrases, the [onDetected] callback fires on the main thread.
 *
 * **Mic sharing.** The engine captures audio continuously (no duty cycling)
 * so wake words are never missed. Mic sharing with other assistants (Google,
 * Bixby) is handled by [WakeWordService]'s [AudioRecordingCallback] — when
 * another app starts recording, the service calls [pause] to release our
 * AudioRecord, and [resume] after a cooldown once the mic is free.
 */
class VoskWakeWordEngine(private val context: Context) : WakeWordEngine {

    companion object {
        private const val TAG = "VoskWakeWord"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "model-en-us"

        // Every phrase here must be composed of words the model's lexicon
        // contains, otherwise the entire decoder degenerates and matches
        // nothing. Keep it to common English words.
        private val WAKE_PHRASES = setOf(
            "hi recall", "hey recall",
            "hi ronnie", "hey ronnie",
            "hi randy", "hey randy",
        )
        
        // We include Bixby and Google so the model doesn't hallucinate them into a match
        // against our wake phrases! If someone says "hi bixby", it will transcribe it as 
        // "hi big beat" etc and ignore it, rather than forcing "hi ronnie".
        private val NEGATIVE_PHRASES = setOf(
            "hi bixby", "hey bixby", 
            "hey google", "ok google",
            "alexa", "siri"
        )
        
        private val GRAMMAR: String = buildString {
            append('[')
            val allPhrases = WAKE_PHRASES + NEGATIVE_PHRASES
            allPhrases.forEachIndexed { i, phrase ->
                if (i > 0) append(", ")
                append('"').append(phrase).append('"')
            }
            append(", \"[unk]\"]")
        }
    }

    private var model: Model? = null
    private var recognizer:     Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var audioThread: HandlerThread? = null
    private var audioHandler: Handler? = null
    private var onDetected: (() -> Unit)? = null
    private var sensitivity: Float = 0.5f

    @Volatile private var isRunning = false
    @Volatile private var isPaused = false

    /** Audio session id of the live AudioRecord, or -1 if mic isn't held. */
    fun audioSessionId(): Int = audioRecord?.audioSessionId ?: -1

    override fun start(onDetected: () -> Unit) {
        if (isRunning) stop()
        this.onDetected = onDetected
        isPaused = false

        // Unpack + init model on background thread, then start listening.
        Thread {
            try {
                if (model == null) {
                    val modelPath = StorageService.sync(context, MODEL_DIR, MODEL_DIR)
                    model = Model(modelPath)
                    Log.d(TAG, "Vosk model loaded from $modelPath")
                }
                if (recognizer == null) {
                    recognizer = Recognizer(model, SAMPLE_RATE.toFloat(), GRAMMAR)
                }
                beginCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialise Vosk model", e)
            }
        }.start()
    }

    override fun stop() {
        Log.d(TAG, "stop()")
        isRunning = false
        isPaused = false

        endCapture()

        recognizer?.close()
        recognizer = null

        onDetected = null
    }

    override fun pause() {
        if (isPaused) return
        if (!isRunning && audioRecord == null) return
        Log.d(TAG, "pause() — releasing mic")
        isPaused = true
        endCapture()
        // Reset the recognizer so we don't replay buffered audio on resume.
        try { recognizer?.reset() } catch (_: Throwable) {}
    }

    override fun resume() {
        if (!isPaused) return
        if (onDetected == null) {
            // start() was never called or stop() happened — nothing to resume.
            isPaused = false
            return
        }
        Log.d(TAG, "resume() — re-acquiring mic")
        isPaused = false
        try {
            if (recognizer == null && model != null) {
                recognizer = Recognizer(model, SAMPLE_RATE.toFloat(), GRAMMAR)
            }
            beginCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume", e)
        }
    }

    override fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.3f, 0.7f)
    }

    /**
     * Release the model completely — call when the service is destroyed.
     */
    fun release() {
        stop()
        model?.close()
        model = null
    }

    // ─── internals ─────────────────────────────────────────────────────

    private fun beginCapture() {
        if (recognizer == null) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted, cannot start wake word")
            return
        }

        // Make sure we have a worker thread to schedule the read loop on. We
        // create it once per begin/end cycle so [pause]/[resume] tear it down
        // cleanly.
        if (audioThread == null) {
            audioThread = HandlerThread("VoskWakeWordThread").also { it.start() }
            audioHandler = Handler(audioThread!!.looper)
        }

        isRunning = true

        if (createAndStartAudioRecord()) {
            scheduleReadLoop()
        } else {
            Log.w(TAG, "AudioRecord unavailable, cannot start wake word")
        }
    }

    /**
     * Allocate the AudioRecord and call startRecording. Sets [audioRecord] on
     * success. Returns false on any failure.
     */
    private fun createAndStartAudioRecord(): Boolean {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
            4096,
        )

        val record = try {
            AudioRecord(
                // Use MIC instead of VOICE_RECOGNITION. VOICE_RECOGNITION completely blocks
                // the Android system's background hotword detectors (Google/Bixby) from listening.
                // Using MIC enables concurrent capture where they can keep listening.
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord constructor failed", e)
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to init")
            record.release()
            return false
        }

        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "startRecording() failed", e)
            record.release()
            return false
        }

        audioRecord = record
        return true
    }

    private fun scheduleReadLoop() {
        val handler = audioHandler ?: return
        val initialBufferSize = maxOf(
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
            4096,
        )
        val buffer = ShortArray(initialBufferSize / 2)

        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return

                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val rec = recognizer ?: return
                    val isFinal = rec.acceptWaveForm(buffer, read)
                    if (isFinal) {
                        handleResult(rec.result)
                    } else {
                        handlePartialResult(rec.partialResult)
                    }
                }

                audioHandler?.post(this)
            }
        })

        Log.d(TAG, "Wake word audio loop started (sensitivity=$sensitivity)")
    }

    private fun endCapture() {
        isRunning = false

        audioHandler?.removeCallbacksAndMessages(null)
        audioThread?.quitSafely()
        audioThread = null
        audioHandler = null

        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) { /* already stopped */ }
        audioRecord?.release()
        audioRecord = null
    }

    private fun handleResult(json: String) {
        try {
            val obj = JSONObject(json)
            val text = obj.optString("text", "").trim().lowercase()
            if (text.isNotEmpty() && matchesWakePhrase(text)) {
                Log.i(TAG, "Wake word detected (final): $text")
                fireDetection()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Vosk result", e)
        }
    }

    private fun handlePartialResult(json: String) {
        try {
            val obj = JSONObject(json)
            val partial = obj.optString("partial", "").trim().lowercase()
            if (partial.isNotEmpty() && matchesWakePhrase(partial)) {
                Log.i(TAG, "Wake word detected (partial): $partial")
                fireDetection()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Vosk partial result", e)
        }
    }

    /**
     * Check if decoded text matches a wake phrase.
     *
     * Sensitivity controls matching strictness:
     *   - Low  (<=0.4): exact full-phrase match only — fewest false positives.
     *   - Med+ (>0.4):  also accept the wake phrase as a substring of the
     *     decoded text (Vosk partials drift, e.g. "hi" → "hi recall" →
     *     "hi recall please"). Grammar mode keeps vocab so small that false
     *     positives are unlikely even with substring matching.
     *
     * Negative phrases ("hi bixby", "hey google") are always rejected.
     */
    private fun matchesWakePhrase(text: String): Boolean {
        // Reject negative phrases first — never trigger on another assistant's wake word
        if (text in NEGATIVE_PHRASES || NEGATIVE_PHRASES.any { text.contains(it) }) return false

        // Exact match always accepted regardless of sensitivity
        if (text in WAKE_PHRASES) return true

        // Substring match only at medium/high sensitivity
        if (sensitivity > 0.4f) {
            return WAKE_PHRASES.any { phrase -> text.contains(phrase) }
        }
        return false
    }

    private fun fireDetection() {
        val cb = onDetected ?: return
        // Reset the recognizer so it doesn't re-trigger on the same audio
        recognizer?.reset()
        // Fire callback on main thread
        Handler(context.mainLooper).post { cb() }
    }
}
