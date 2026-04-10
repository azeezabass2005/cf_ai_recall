package com.ranti.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ranti.data.OnboardingPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * SPEC §6.1 — restarts [WakeWordService] after device reboot if the user has
 * the wake word enabled.
 */
class BootReceiver : BroadcastReceiver() {
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pending = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (OnboardingPrefs.isWakeWordEnabled(context)) {
                    WakeWordService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
