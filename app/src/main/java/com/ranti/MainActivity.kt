package com.ranti

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.ranti.data.OnboardingPrefs
import com.ranti.navigation.NavGraph
import com.ranti.navigation.Routes
import com.ranti.service.WakeWordService
import com.ranti.ui.theme.RantiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Wake-word activation events from [WakeWordService] flow through here so
    // [ChatScreen] / [ChatViewModel] can react. Compose collects this in a
    // LaunchedEffect.
    private val wakeEvents = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SPEC §6.1 step 6 — when launched from a lock-screen wake-word
        // notification, ask the OS to show us over the keyguard and turn the
        // screen on so the user goes straight into voice mode.
        if (intent?.getBooleanExtra(WakeWordService.EXTRA_WAKE, false) == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                )
            }
        }

        // SPEC §6.1 — start the wake-word foreground service if the user has
        // it enabled. Done off the main thread because the pref read is async.
        lifecycleScope.launch {
            if (OnboardingPrefs.isWakeWordEnabled(this@MainActivity)) {
                WakeWordService.start(this@MainActivity)
            }
        }

        handleWakeIntent(intent)

        setContent {
            RantiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val context = LocalContext.current
                    val nav = rememberNavController()

                    var startDestination by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        startDestination = if (OnboardingPrefs.isComplete(context)) {
                            Routes.Chat
                        } else {
                            Routes.OnboardingWelcome
                        }
                    }

                    startDestination?.let {
                        NavGraph(
                            navController = nav,
                            startDestination = it,
                            wakeEvents = wakeEvents.asStateFlow(),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isAppForeground = true
    }

    override fun onPause() {
        super.onPause()
        isAppForeground = false
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(WakeWordService.EXTRA_WAKE, false) != true) return

        wakeEvents.value = wakeEvents.value + 1

        // Dismiss the alert notification so it doesn't linger after launch
        val nm = getSystemService(NotificationManager::class.java)
        nm?.cancel(1002) // WakeWordService.ALERT_NOTIFICATION_ID

        // If on the lock screen, ask the system to dismiss the keyguard so
        // the user goes straight into voice mode without swiping/entering PIN.
        // On a secure lock screen the system still shows the unlock prompt.
        val keyguard = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardLocked == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(this, null)
        }
    }

    companion object {
        /**
         * True between [onResume] and [onPause]. [WakeWordService] checks this
         * to decide whether it can `startActivity` directly (Android 10+ only
         * permits that from a foreground app) or must fall back to a
         * full-screen-intent notification.
         */
        @Volatile
        var isAppForeground: Boolean = false
    }
}
