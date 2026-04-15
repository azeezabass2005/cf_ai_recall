package com.recall.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.recall.location.GeofenceMonitorService
import com.recall.location.GeofencePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Restarts [GeofenceMonitorService] after device reboot if there are pending
 * location-based reminders.
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
                // Restart the active location monitor if there are pending
                // location-based reminders — without this, only the passive
                // Geofencing API is active after reboot (30 min – 2h delay).
                if (GeofencePrefs.getActiveCount(context) > 0) {
                    GeofenceMonitorService.startMonitoring(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
