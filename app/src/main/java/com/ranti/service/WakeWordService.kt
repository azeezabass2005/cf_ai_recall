package com.ranti.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ranti.MainActivity
import com.ranti.R
import com.ranti.data.OnboardingPrefs
import com.ranti.voice.VoskWakeWordEngine
import com.ranti.voice.WakeWordEngine
import com.ranti.voice.createDefaultEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * SPEC §6.1 — Wake-word foreground service.
 *
 * Runs as a `microphone` foreground service with a persistent low-priority
 * notification. The actual wake-word detection is delegated to a
 * [WakeWordEngine] — the default factory returns a [VoskWakeWordEngine] that
 * uses Vosk's offline grammar-mode recognizer for "Hi Ranti" detection.
 *
 * Lifecycle:
 *   - Started by [start] when the user enables wake word.
 *   - Restarted on boot by [BootReceiver] if the setting is on.
 *   - Stopped by [stop] when the user disables wake word.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engine: WakeWordEngine? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    /**
     * True while another app (or our own SpeechRecognizer) is using the mic
     * and we've yielded. Tracked separately from the engine's own paused
     * state so explicit pause requests from the chat layer survive callback
     * churn.
     */
    @Volatile private var yieldedToOther: Boolean = false
    @Volatile private var explicitlyPaused: Boolean = false

    /**
     * Pending "resume after cooldown" job. We don't re-acquire the mic the
     * instant another app stops recording — assistants typically pause for a
     * fraction of a second between hot-word detection, command capture, TTS
     * playback and any follow-up turn, and we don't want to race them on every
     * gap. Cleared whenever a fresh conflict appears.
     */
    private var resumeRunnable: Runnable? = null
    private val yieldCooldownMs: Long = 3000L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannels(this)
        startInForeground()
        registerRecordingCallback()
        engine = createDefaultEngine(applicationContext).also { e ->
            scope.launch {
                // SPEC §6.1 — sensitivity comes from the onboarding pref the
                // user picked on the WakeWordSetupScreen.
                val sensitivity = OnboardingPrefs.getWakeWordSensitivity(this@WakeWordService)
                e.setSensitivity(sensitivity)
                e.start { onWakeWordDetected() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                explicitlyPaused = true
                engine?.pause()
            }
            ACTION_RESUME -> {
                explicitlyPaused = false
                if (!yieldedToOther) engine?.resume()
            }
        }
        // Sticky so the OS restarts us if we're killed.
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterRecordingCallback()
        engine?.stop()
        // Release the Vosk model if we have a VoskWakeWordEngine
        (engine as? VoskWakeWordEngine)?.release()
        engine = null
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    // ─── Audio sharing ───────────────────────────────────────────────────

    private fun registerRecordingCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = am
        val cb = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                handleRecordingConfigChanged(configs)
            }
        }
        recordingCallback = cb
        am.registerAudioRecordingCallback(cb, mainHandler)
        // Seed initial state from the current snapshot.
        handleRecordingConfigChanged(am.activeRecordingConfigurations)
    }

    private fun unregisterRecordingCallback() {
        resumeRunnable?.let { mainHandler.removeCallbacks(it) }
        resumeRunnable = null
        val am = audioManager ?: return
        val cb = recordingCallback ?: return
        try {
            am.unregisterAudioRecordingCallback(cb)
        } catch (_: Throwable) { /* best-effort */ }
        recordingCallback = null
        audioManager = null
    }

    private fun handleRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
        val ourSession = (engine as? VoskWakeWordEngine)?.audioSessionId() ?: -1
        // We only filter out HOTWORD: that's a privileged source that runs
        // concurrently with normal capture and never actually conflicts with
        // us. Everything else — MIC, VOICE_RECOGNITION, CAMCORDER, etc — is a
        // real audio-policy conflict, and includes the source Google Assistant
        // and Bixby use *after* their hot-word fires (they escalate from
        // HOTWORD to VOICE_RECOGNITION to capture the user's command). If we
        // don't yield on VOICE_RECOGNITION, our continuous MIC capture will
        // silence the assistant exactly when the user is trying to talk to it.
        val othersActive = configs.any {
            val source = it.clientAudioSource
            it.clientAudioSessionId != ourSession &&
                source != 1999 // MediaRecorder.AudioSource.HOTWORD (system-only)
        }

        if (othersActive) {
            // Cancel any pending resume — fresh conflict resets the cooldown.
            resumeRunnable?.let { mainHandler.removeCallbacks(it) }
            resumeRunnable = null
            if (!yieldedToOther) {
                Log.d(TAG, "Another recorder is active — pausing wake word")
                yieldedToOther = true
                engine?.pause()
            }
        } else if (yieldedToOther) {
            // Don't pounce on the mic the instant the other app's
            // configuration disappears: schedule a delayed resume so we
            // survive the natural pauses inside an assistant turn.
            if (resumeRunnable == null) {
                Log.d(TAG, "Mic appears free — scheduling resume in ${yieldCooldownMs}ms")
                val r = Runnable {
                    resumeRunnable = null
                    yieldedToOther = false
                    if (!explicitlyPaused) {
                        Log.d(TAG, "Cooldown elapsed — resuming wake word")
                        engine?.resume()
                    }
                }
                resumeRunnable = r
                mainHandler.postDelayed(r, yieldCooldownMs)
            }
        }
    }

    private fun startInForeground() {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ranti")
            .setContentText("Say \"Hi Recall\" anytime")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun onWakeWordDetected() {
        // SPEC §6.1 step 1 — short haptic.
        playHaptic()
        // SPEC §6.1 step 2 — activation chime.
        playChime()

        // Release the mic *immediately* so SpeechRecognizer (the next thing to
        // run, in voice mode) has a clean window to grab AudioRecord. We mark
        // this as an explicit pause so the recording-callback path doesn't
        // resume us behind the chat layer's back.
        explicitlyPaused = true
        engine?.pause()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_WAKE, true)
        }

        // Always attempt direct activity launch first. Foreground services
        // with FOREGROUND_SERVICE_TYPE_MICROPHONE have Background Activity
        // Launch (BAL) exemptions on Android 10-13. On Android 14+,
        // USE_FULL_SCREEN_INTENT (declared in the manifest) grants the
        // exemption. If the system blocks us, fall back to a high-priority
        // notification — like Bixby/Google, we want to go straight into voice
        // mode without requiring a tap whenever possible.
        var launched = false
        try {
            startActivity(intent)
            launched = true
        } catch (e: Exception) {
            Log.w(TAG, "Direct activity launch blocked, falling back to notification", e)
        }

        if (!launched) {
            postFullScreenWakeNotification()
        }

        // Auto-dismiss the alert notification after 10 seconds if the user
        // didn't interact — avoids stale "Ranti heard you" lingering.
        mainHandler.postDelayed({
            val nm = getSystemService(NotificationManager::class.java) ?: return@postDelayed
            nm.cancel(ALERT_NOTIFICATION_ID)
        }, 10_000L)
    }

    private fun playHaptic() {
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }
            vib?.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Throwable) {
            // Vibrator unavailable on this device — non-fatal.
        }
    }

    private fun playChime() {
        // We look the chime up by name so the project still compiles before
        // anyone drops the file in. Drop a short clip at
        //   app/src/main/res/raw/activation.ogg
        // and it'll start playing automatically.
        try {
            val id = resources.getIdentifier("activation", "raw", packageName)
            if (id == 0) return
            MediaPlayer.create(this, id)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (_: Throwable) {
            // Resource missing or playback failed — non-fatal.
        }
    }

    private fun postFullScreenWakeNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_WAKE, true)
        }
        val pi = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Ranti heard you")
            .setContentText("Tap to open voice mode")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()

        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(ALERT_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "ranti_wake_word"
        private const val ALERT_CHANNEL_ID = "ranti_wake_alert_v2"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        const val EXTRA_WAKE = "ranti.wake"
        const val ACTION_PAUSE = "com.ranti.action.PAUSE_WAKE"
        const val ACTION_RESUME = "com.ranti.action.RESUME_WAKE"

        // Single in-process instance — set in onCreate, cleared in onDestroy.
        // Used by [pauseEngine] / [resumeEngine] for the fast path that
        // doesn't depend on the AudioRecordingCallback racing.
        @Volatile
        private var instance: WakeWordService? = null

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }

        /**
         * Tell the running wake-word service to release the mic immediately.
         * Safe to call from any thread; no-ops if the service isn't running.
         * Use this before our own [android.speech.SpeechRecognizer] starts so
         * voice mode can actually capture audio without racing the global
         * recording callback.
         */
        fun pauseEngine(context: Context) {
            instance?.let {
                it.explicitlyPaused = true
                it.engine?.pause()
                return
            }
            // Service not yet bound in this process — fall through to an
            // intent so a freshly-started service still picks it up.
            val intent = Intent(context, WakeWordService::class.java)
                .setAction(ACTION_PAUSE)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) { /* nothing to pause */ }
        }

        /**
         * Tell the running wake-word service it can re-acquire the mic.
         */
        fun resumeEngine(context: Context) {
            instance?.let {
                it.explicitlyPaused = false
                if (!it.yieldedToOther) it.engine?.resume()
                return
            }
            val intent = Intent(context, WakeWordService::class.java)
                .setAction(ACTION_RESUME)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) { /* nothing to resume */ }
        }

        private fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return

            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Wake word",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Persistent notification while Ranti listens for the wake word."
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }

            if (nm.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                val alert = NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Wake word alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Heads-up alert when Ranti detects the wake word on the lock screen."
                    setShowBadge(false)
                    enableVibration(true)
                }
                nm.createNotificationChannel(alert)
            }
        }
    }
}
