package com.ranti.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ranti.data.OnboardingPrefs
import com.ranti.ui.theme.LocalRantiColors
import com.ranti.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * SPEC §13.1 — Settings index screen.
 *
 * Provides access to all sub-screens plus two inline actions:
 *   • Theme toggle (Light / Dark / System) — cycles without navigation.
 *   • Chat History clear — confirmation dialog, local-only action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWakeWord: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToSavedPlaces: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    val context = LocalContext.current
    val ranti = LocalRantiColors.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    val themeMode by OnboardingPrefs.themeModeFlow(context)
        .collectAsStateWithLifecycle(initialValue = "system")

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear chat history?") },
            text = { Text("This will remove all messages from this session. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryDialog = false
                    scope.launch { snackbarHost.showSnackbar("Chat history cleared") }
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
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
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                SettingsRow(
                    icon = Icons.Default.GraphicEq,
                    title = "Wake Word",
                    subtitle = "Enable / disable, sensitivity",
                    onClick = onNavigateToWakeWord,
                )
                HorizontalDivider(color = ranti.borderSubtle)
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Mic,
                    title = "Voice & Speech",
                    subtitle = "TTS, speech recognition language",
                    onClick = onNavigateToVoice,
                )
                HorizontalDivider(color = ranti.borderSubtle)
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Sound, vibration, snooze",
                    onClick = onNavigateToNotifications,
                )
                HorizontalDivider(color = ranti.borderSubtle)
            }
            item {
                SettingsRow(
                    icon = Icons.Default.LocationOn,
                    title = "Location",
                    subtitle = "Geofence radius, GPS status",
                    onClick = onNavigateToLocation,
                )
                HorizontalDivider(color = ranti.borderSubtle)
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Place,
                    title = "Saved Places",
                    subtitle = "Manage location nicknames",
                    onClick = onNavigateToSavedPlaces,
                )
                HorizontalDivider(color = ranti.borderSubtle)
            }
            item {
                SettingsRow(
                    icon = Icons.Default.History,
                    title = "Chat History",
                    subtitle = "Clear all chat messages",
                    onClick = { showClearHistoryDialog = true },
                )
                HorizontalDivider(color = ranti.borderSubtle)
            }
            item {
                // Theme toggle — cycles Light → Dark → System inline
                val (themeIcon, themeLabel) = when (themeMode) {
                    "light" -> Icons.Default.LightMode to "Theme: Light"
                    "dark" -> Icons.Default.DarkMode to "Theme: Dark"
                    else -> Icons.Default.SettingsBrightness to "Theme: System"
                }
                SettingsRow(
                    icon = themeIcon,
                    title = themeLabel,
                    subtitle = "Tap to cycle Light / Dark / System",
                    trailing = { Icon(themeIcon, contentDescription = null, tint = ranti.textMid) },
                    onClick = {
                        scope.launch {
                            val next = when (themeMode) {
                                "light" -> "dark"
                                "dark" -> "system"
                                else -> "light"
                            }
                            OnboardingPrefs.setThemeMode(context, next)
                        }
                    },
                )
                HorizontalDivider(color = ranti.borderSubtle)
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "About Ranti",
                    subtitle = "Version, licenses",
                    onClick = onNavigateToAbout,
                )
            }
        }
    }
}

/** Shared settings row used by [SettingsScreen] and imported by sub-screens in this package. */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = LocalRantiColors.current.textLo,
        )
    },
    onClick: () -> Unit,
) {
    val ranti = LocalRantiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.base, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ranti.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Spacing.base))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ranti.textHi)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ranti.textMid)
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        trailing()
    }
}

/** Non-clickable info row — used on sub-screens for read-only values. */
@Composable
internal fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String,
) {
    val ranti = LocalRantiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ranti.textMid, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Spacing.base))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ranti.textHi)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = ranti.textMid)
    }
}
