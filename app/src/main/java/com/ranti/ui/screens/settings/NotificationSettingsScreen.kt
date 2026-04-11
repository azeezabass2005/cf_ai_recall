package com.ranti.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ranti.data.OnboardingPrefs
import com.ranti.ui.theme.LocalRantiColors
import com.ranti.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * SPEC §13.4 — Notification settings.
 *
 * Sound, vibration, heads-up toggles, snooze duration picker, and a
 * re-notify option with an animated interval picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val ranti = LocalRantiColors.current
    val scope = rememberCoroutineScope()

    var sound by remember { mutableStateOf(true) }
    var vibration by remember { mutableStateOf(true) }
    var headsUp by remember { mutableStateOf(true) }
    var snoozeMinutes by remember { mutableIntStateOf(10) }
    var renotify by remember { mutableStateOf(false) }
    var renotifyInterval by remember { mutableIntStateOf(10) }

    LaunchedEffect(Unit) {
        sound = OnboardingPrefs.isNotifSound(context)
        vibration = OnboardingPrefs.isNotifVibration(context)
        headsUp = OnboardingPrefs.isNotifHeadsUp(context)
        snoozeMinutes = OnboardingPrefs.getNotifSnoozeMinutes(context)
        renotify = OnboardingPrefs.isNotifRenotify(context)
        renotifyInterval = OnboardingPrefs.getNotifRenotifyInterval(context)
    }

    val snoozeDurations = listOf(5, 10, 15, 30)
    val renotifyIntervals = listOf(5, 10, 15)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Notifications", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.base),
        ) {
            // Sound toggle
            ToggleRow(
                icon = Icons.Default.VolumeUp,
                title = "Sound",
                subtitle = "Play a sound when a reminder fires",
                checked = sound,
                onCheckedChange = { on ->
                    sound = on
                    scope.launch { OnboardingPrefs.setNotifSound(context, on) }
                },
            )
            HorizontalDivider(color = ranti.borderSubtle)

            // Vibration toggle
            ToggleRow(
                icon = Icons.Default.Vibration,
                title = "Vibration",
                subtitle = "Vibrate when a reminder fires",
                checked = vibration,
                onCheckedChange = { on ->
                    vibration = on
                    scope.launch { OnboardingPrefs.setNotifVibration(context, on) }
                },
            )
            HorizontalDivider(color = ranti.borderSubtle)

            // Heads-up toggle
            ToggleRow(
                icon = Icons.Default.NotificationsActive,
                title = "Heads-up notifications",
                subtitle = "Show notification over other apps",
                checked = headsUp,
                onCheckedChange = { on ->
                    headsUp = on
                    scope.launch { OnboardingPrefs.setNotifHeadsUp(context, on) }
                },
            )
            HorizontalDivider(color = ranti.borderSubtle)

            // Snooze duration
            Spacer(Modifier.height(Spacing.lg))
            Text("Snooze duration", style = MaterialTheme.typography.bodyLarge, color = ranti.textHi)
            Spacer(Modifier.height(Spacing.sm))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                snoozeDurations.forEachIndexed { idx, minutes ->
                    SegmentedButton(
                        selected = snoozeMinutes == minutes,
                        onClick = {
                            snoozeMinutes = minutes
                            scope.launch { OnboardingPrefs.setNotifSnoozeMinutes(context, minutes) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = snoozeDurations.size),
                        label = { Text("${minutes}m") },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = Spacing.lg), color = ranti.borderSubtle)

            // Re-notify toggle
            ToggleRow(
                icon = Icons.Default.Refresh,
                title = "Re-notify",
                subtitle = "Re-fire notification if not dismissed",
                checked = renotify,
                onCheckedChange = { on ->
                    renotify = on
                    scope.launch { OnboardingPrefs.setNotifRenotify(context, on) }
                },
            )

            // Re-notify interval — only shown when re-notify is enabled
            AnimatedVisibility(visible = renotify) {
                Column {
                    Spacer(Modifier.height(Spacing.sm))
                    Text("Re-notify after", style = MaterialTheme.typography.bodyMedium, color = ranti.textMid)
                    Spacer(Modifier.height(Spacing.xs))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        renotifyIntervals.forEachIndexed { idx, minutes ->
                            SegmentedButton(
                                selected = renotifyInterval == minutes,
                                onClick = {
                                    renotifyInterval = minutes
                                    scope.launch { OnboardingPrefs.setNotifRenotifyInterval(context, minutes) }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = renotifyIntervals.size),
                                label = { Text("${minutes}m") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val ranti = LocalRantiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ranti.accent)
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ranti.textHi)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ranti.textMid)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
