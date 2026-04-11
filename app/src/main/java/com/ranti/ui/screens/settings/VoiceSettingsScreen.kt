package com.ranti.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
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
 * SPEC §13.3 — Voice & Speech settings.
 *
 * TTS toggle, speed slider, and speech language picker.
 * Preferences are persisted immediately on change via DataStore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val ranti = LocalRantiColors.current
    val scope = rememberCoroutineScope()

    var ttsEnabled by remember { mutableStateOf(true) }
    var ttsSpeed by remember { mutableFloatStateOf(1.0f) }
    var language by remember { mutableStateOf("en-NG") }

    LaunchedEffect(Unit) {
        ttsEnabled = OnboardingPrefs.isVoiceTtsEnabled(context)
        ttsSpeed = OnboardingPrefs.getVoiceTtsSpeed(context)
        language = OnboardingPrefs.getVoiceLanguage(context)
    }

    val languages = listOf("en-NG" to "English (Nigeria)", "en-US" to "English (US)", "en-GB" to "English (UK)")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Voice & Speech", style = MaterialTheme.typography.headlineSmall) },
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
            // TTS enabled toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = ranti.accent)
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Speak responses", style = MaterialTheme.typography.bodyLarge, color = ranti.textHi)
                    Text("Ranti reads her replies aloud", style = MaterialTheme.typography.bodySmall, color = ranti.textMid)
                }
                Switch(
                    checked = ttsEnabled,
                    onCheckedChange = { on ->
                        ttsEnabled = on
                        scope.launch { OnboardingPrefs.setVoiceTtsEnabled(context, on) }
                    },
                )
            }

            HorizontalDivider(color = ranti.borderSubtle)

            // TTS speed slider
            Spacer(Modifier.height(Spacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = ranti.accent)
                Spacer(Modifier.width(Spacing.sm))
                Text("Speech speed", style = MaterialTheme.typography.bodyLarge, color = ranti.textHi)
            }
            Spacer(Modifier.height(Spacing.xs))
            Slider(
                value = ttsSpeed,
                onValueChange = { ttsSpeed = it },
                onValueChangeFinished = {
                    scope.launch { OnboardingPrefs.setVoiceTtsSpeed(context, ttsSpeed) }
                },
                valueRange = 0.75f..1.25f,
                steps = 1, // Slow / Normal / Fast — 3 positions
                enabled = ttsEnabled,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Slow", style = MaterialTheme.typography.labelSmall, color = ranti.textLo)
                Text("Normal", style = MaterialTheme.typography.labelSmall, color = ranti.textLo)
                Text("Fast", style = MaterialTheme.typography.labelSmall, color = ranti.textLo)
            }

            HorizontalDivider(modifier = Modifier.padding(top = Spacing.lg), color = ranti.borderSubtle)

            // Speech language picker
            Spacer(Modifier.height(Spacing.lg))
            Text("Speech language", style = MaterialTheme.typography.bodyLarge, color = ranti.textHi)
            Spacer(Modifier.height(Spacing.sm))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                languages.forEachIndexed { idx, (code, label) ->
                    SegmentedButton(
                        selected = language == code,
                        onClick = {
                            language = code
                            scope.launch { OnboardingPrefs.setVoiceLanguage(context, code) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = languages.size),
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Language affects speech recognition and TTS accent.",
                style = MaterialTheme.typography.bodySmall,
                color = ranti.textLo,
            )
        }
    }
}
