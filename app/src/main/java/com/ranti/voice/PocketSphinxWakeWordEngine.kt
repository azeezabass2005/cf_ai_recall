package com.ranti.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import edu.cmu.pocketsphinx.Assets
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.RecognitionListener
import edu.cmu.pocketsphinx.SpeechRecognizer
import edu.cmu.pocketsphinx.SpeechRecognizerSetup
import java.io.File
import java.io.IOException

/**
 * PocketSphinx-based wake word engine (in-app only).
 *
 * Uses the CMU PocketSphinx offline keyword-spotting recognizer.
 * Detection phrases are "hi recall" and "hey recall" (both words are in
 * the bundled CMUdict dictionary at assets/sync/models/lm/words.dic).
 *
 * Runs PocketSphinx's own internal audio thread — no manual AudioRecord loop
 * required. Keyword thresholds are written to a cache-dir file at init time
 * based on [sensitivity] so callers can tune false-positive rate.
 *
 * Lifecycle:
 *   [start]   → syncs assets, builds SpeechRecognizer, starts listening.
 *   [pause]   → cancels current search (releases mic), keeps recognizer alive.
 *   [resume]  → resumes the same search without rebuilding the model.
 *   [stop]    → shuts down recognizer and frees all resources.
 */
class PocketSphinxWakeWordEngine(private val context: Context) : WakeWordEngine {

    companion object {
        private const val TAG = "PocketSphinxWake"
        private const val KEYWORD_SEARCH = "WAKE_WORD"
        private const val RETRY_DELAY_MS = 3000L
        private const val DETECTION_COOLDOWN_MS = 3000L
    }

    private var recognizer: SpeechRecognizer? = null
    private var onDetected: (() -> Unit)? = null
    private var sensitivity: Float = 0.5f
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var isRunning = false
    @Volatile private var isPaused = false

    /** Simple debounce: ignore detections fired within cooldown of the last one. */
    @Volatile private var isDetectionCooldown = false

    /** Cached path where PocketSphinx model files were synced on first run. */
    private var assetsDir: File? = null

    // ─── RecognitionListener ──────────────────────────────────────────────────

    private val listener = object : RecognitionListener {

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech — restarting keyword search")
            // PocketSphinx stops the search on end-of-speech; restart to keep
            // listening continuously.
            if (isRunning && !isPaused) {
                recognizer?.startListening(KEYWORD_SEARCH)
            }
        }

        override fun onPartialResult(hypothesis: Hypothesis?) {
            if (hypothesis == null) return
            val text = hypothesis.hypstr?.trim()?.lowercase() ?: return
            if (text.isEmpty() || isDetectionCooldown) return

            Log.i(TAG, "Wake word detected (partial): '$text'")
            isDetectionCooldown = true
            // Fire callback on main thread.
            mainHandler.post { onDetected?.invoke() }
            // Clear cooldown after a few seconds so the next utterance can fire.
            mainHandler.postDelayed({ isDetectionCooldown = false }, DETECTION_COOLDOWN_MS)
        }

        override fun onResult(hypothesis: Hypothesis?) {
            Log.d(TAG, "onResult: ${hypothesis?.hypstr}")
            // Ensure we keep listening after a result is delivered.
            if (isRunning && !isPaused) {
                recognizer?.startListening(KEYWORD_SEARCH)
            }
        }

        override fun onError(e: Exception) {
            Log.e(TAG, "Recognizer error — will retry in ${RETRY_DELAY_MS}ms", e)
            mainHandler.postDelayed({
                if (isRunning && !isPaused) initAndStart()
            }, RETRY_DELAY_MS)
        }

        override fun onTimeout() {
            Log.d(TAG, "onTimeout — restarting keyword search")
            if (isRunning && !isPaused) {
                recognizer?.startListening(KEYWORD_SEARCH)
            }
        }
    }

    // ─── WakeWordEngine interface ─────────────────────────────────────────────

    override fun start(onDetected: () -> Unit) {
        if (isRunning) stop()
        this.onDetected = onDetected
        isRunning = true
        isPaused = false
        isDetectionCooldown = false
        Thread(::initAndStart, "PocketSphinxInit").start()
    }

    override fun stop() {
        Log.d(TAG, "stop()")
        isRunning = false
        isPaused = false
        isDetectionCooldown = false
        mainHandler.removeCallbacksAndMessages(null)
        shutdownRecognizer()
        onDetected = null
    }

    override fun pause() {
        if (isPaused) return
        Log.d(TAG, "pause() — cancelling keyword search")
        isPaused = true
        recognizer?.cancel()
    }

    override fun resume() {
        if (!isPaused) return
        if (!isRunning) { isPaused = false; return }
        Log.d(TAG, "resume() — restarting keyword search")
        isPaused = false
        if (recognizer != null) {
            recognizer?.startListening(KEYWORD_SEARCH)
        } else {
            Thread(::initAndStart, "PocketSphinxResume").start()
        }
    }

    override fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0f, 1f)
    }

    // ─── internals ────────────────────────────────────────────────────────────

    /**
     * On first call: sync the bundled PocketSphinx model assets from the APK to
     * device storage (this is slow; always call from a background thread).
     * Then build the recognizer and start listening.
     */
    private fun initAndStart() {
        if (!isRunning || isPaused) return

        if (assetsDir == null) {
            try {
                val sphinxAssets = Assets(context)
                assetsDir = sphinxAssets.syncAssets()
                Log.d(TAG, "PocketSphinx assets synced → $assetsDir")
            } catch (e: IOException) {
                Log.e(TAG, "Asset sync failed — cannot start wake word", e)
                return
            }
        }
        startRecognizer()
    }

    private fun startRecognizer() {
        if (!isRunning || isPaused) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted — wake word disabled")
            return
        }

        val dir = assetsDir ?: run {
            Log.e(TAG, "assetsDir is null in startRecognizer"); return
        }

        try {
            // Always shut down any existing instance before creating a new one.
            shutdownRecognizer()

            // Sensitivity → keyword threshold.
            //   sensitivity 0.0 → 1e-40  (strict — fewest false positives)
            //   sensitivity 0.5 → 1e-22  (balanced default)
            //   sensitivity 1.0 → 1e-5   (lenient — most detections)
            val exponent = (-40 + (35f * sensitivity).toInt()).coerceIn(-50, -5)
            val thresholdStr = "1e$exponent"

            // Write keyword list to cache dir so we can tune per-sensitivity.
            val kwFile = File(context.cacheDir, "ranti_keywords.list").also {
                it.writeText("hi recall /$thresholdStr/\nhey recall /$thresholdStr/\n")
            }

            val rec = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(File(dir, "models/en-us-ptm"))
                .setDictionary(File(dir, "models/lm/words.dic"))
                .recognizer

            rec.addKeywordSearch(KEYWORD_SEARCH, kwFile)
            rec.addListener(listener)
            rec.startListening(KEYWORD_SEARCH)
            recognizer = rec

            Log.d(TAG, "PocketSphinx listening — sensitivity=$sensitivity threshold=$thresholdStr")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PocketSphinx recognizer", e)
        }
    }

    private fun shutdownRecognizer() {
        val rec = recognizer ?: return
        recognizer = null
        try { rec.cancel() } catch (_: Exception) {}
        try { rec.shutdown() } catch (_: Exception) {}
        Log.d(TAG, "Recognizer shut down")
    }
}
