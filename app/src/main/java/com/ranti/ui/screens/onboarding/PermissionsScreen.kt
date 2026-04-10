package com.ranti.ui.screens.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ranti.ui.theme.LocalRantiColors
import com.ranti.ui.theme.Spacing

/**
 * SPEC §4.2 — Permissions.
 *
 * Required (CTA stays disabled until granted):
 *   - Microphone (RECORD_AUDIO)
 *   - Notifications (POST_NOTIFICATIONS) — auto-granted on API < 33
 *
 * Recommended (CTA enabled even if denied, location reminders just won't work):
 *   - Fine Location
 *   - Background Location (only askable after Fine is granted; on API 30+
 *     this routes to system settings)
 *   - Battery optimization exemption
 */
@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val ranti = LocalRantiColors.current

    // Re-check permissions every time the screen resumes (e.g. user came back
    // from system Settings after denying with "Don't ask again").
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state = remember(refreshTick) { computePermissionState(context) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }

    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }

    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
        ) {
            Text(
                text = "Ranti needs a few things\nto work properly",
                style = MaterialTheme.typography.headlineLarge,
                color = ranti.textHi,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "${state.grantedRequiredCount}/${state.totalRequired} required permissions granted",
                style = MaterialTheme.typography.bodySmall,
                color = ranti.textMid,
            )

            Spacer(Modifier.height(Spacing.xl))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.cards) { card ->
                    PermissionCard(
                        card = card,
                        onGrant = {
                            when (card.id) {
                                PermId.Microphone -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                PermId.Notifications -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                PermId.FineLocation -> fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                PermId.BackgroundLocation -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    }
                                }
                                PermId.ExactAlarm -> requestExactAlarmAccess(context)
                                PermId.Battery -> requestIgnoreBatteryOptimizations(context)
                            }
                        },
                        onOpenSettings = { openAppSettings(context) },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.base))

            Button(
                onClick = onContinue,
                enabled = state.requiredAllGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text("Continue", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    card: PermissionCardData,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ranti = LocalRantiColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(Spacing.base))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = ranti.textHi,
                    )
                    if (card.required) {
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = "Required",
                            style = MaterialTheme.typography.labelSmall,
                            color = ranti.warning,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = card.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = ranti.textMid,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            when (card.status) {
                PermStatus.Granted -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = ranti.success,
                    )
                }
                PermStatus.Denied -> {
                    TextButton(onClick = onGrant) { Text("Grant") }
                }
                PermStatus.PermanentlyDenied -> {
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                }
                PermStatus.NotApplicable -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Not needed on this device",
                        tint = ranti.textLo,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permission state plumbing
// ─────────────────────────────────────────────────────────────────────────────

private enum class PermId { Microphone, Notifications, ExactAlarm, FineLocation, BackgroundLocation, Battery }
private enum class PermStatus { Granted, Denied, PermanentlyDenied, NotApplicable }

private data class PermissionCardData(
    val id: PermId,
    val title: String,
    val explanation: String,
    val icon: ImageVector,
    val required: Boolean,
    val status: PermStatus,
)

private data class PermissionState(
    val cards: List<PermissionCardData>,
    val totalRequired: Int,
    val grantedRequiredCount: Int,
) {
    val requiredAllGranted: Boolean get() = grantedRequiredCount == totalRequired
}

private fun computePermissionState(context: Context): PermissionState {
    val mic = checkRuntime(context, Manifest.permission.RECORD_AUDIO)
    val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        checkRuntime(context, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        PermStatus.NotApplicable
    }
    val fine = checkRuntime(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (fine != PermStatus.Granted) PermStatus.Denied
        else checkRuntime(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        PermStatus.NotApplicable
    }
    // Exact alarms — required on API 31/32 via user opt-in; auto-granted
    // on API 33+ through USE_EXACT_ALARM; no-op on pre-12.
    val exactAlarm = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> PermStatus.NotApplicable
        canScheduleExactAlarms(context) -> PermStatus.Granted
        else -> PermStatus.Denied
    }
    val battery = if (isIgnoringBatteryOptimizations(context)) PermStatus.Granted else PermStatus.Denied

    val cards = listOf(
        PermissionCardData(
            id = PermId.Microphone,
            title = "Microphone",
            explanation = "So I can hear you when you speak.",
            icon = Icons.Default.Mic,
            required = true,
            status = mic,
        ),
        PermissionCardData(
            id = PermId.Notifications,
            title = "Notifications",
            explanation = "So I can remind you on time.",
            icon = Icons.Default.Notifications,
            required = true,
            status = notif,
        ),
        PermissionCardData(
            id = PermId.ExactAlarm,
            title = "Exact alarms",
            explanation = "So reminders fire at the exact minute you asked for — not whenever Doze wakes up.",
            icon = Icons.Default.Alarm,
            required = true,
            status = exactAlarm,
        ),
        PermissionCardData(
            id = PermId.FineLocation,
            title = "Location",
            explanation = "So I can remind you when you arrive somewhere.",
            icon = Icons.Default.LocationOn,
            required = false,
            status = fine,
        ),
        PermissionCardData(
            id = PermId.BackgroundLocation,
            title = "Background location",
            explanation = "So location reminders work even when the app is closed.",
            icon = Icons.Default.LocationOn,
            required = false,
            status = bg,
        ),
        PermissionCardData(
            id = PermId.Battery,
            title = "Skip battery optimisation",
            explanation = "So your reminders are never delayed by Doze.",
            icon = Icons.Default.BatteryFull,
            required = false,
            status = battery,
        ),
    )

    val totalRequired = cards.count { it.required && it.status != PermStatus.NotApplicable }
    val grantedRequired = cards.count { it.required && it.status == PermStatus.Granted }

    // POST_NOTIFICATIONS on pre-API-33 is "NotApplicable" but morally granted.
    val effectiveGranted = grantedRequired + cards.count { it.required && it.status == PermStatus.NotApplicable }

    return PermissionState(
        cards = cards,
        totalRequired = totalRequired + cards.count { it.required && it.status == PermStatus.NotApplicable },
        grantedRequiredCount = effectiveGranted,
    )
}

private fun checkRuntime(context: Context, permission: String): PermStatus {
    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return if (granted) PermStatus.Granted else PermStatus.Denied
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return am.canScheduleExactAlarms()
}

private fun requestExactAlarmAccess(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    // SCHEDULE_EXACT_ALARM is a special app-op permission — you can't
    // request it via the normal runtime permission dialog. You deep-link
    // the user into this specific Settings page instead.
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Some OEM builds don't honour the deep link — fall back to the
        // generic app details page.
        openAppSettings(context)
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    // Note: ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is restricted by Play
    // policy for most apps. We use the settings page instead, which is allowed.
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
