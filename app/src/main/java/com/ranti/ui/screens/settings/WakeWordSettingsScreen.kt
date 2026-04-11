package com.ranti.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ranti.data.OnboardingPrefs
import com.ranti.service.WakeWordService
import com.ranti.ui.components.OrbState
import com.ranti.ui.components.VoiceOrb
import com.ranti.ui.theme.LocalRantiColors
import com.ranti.ui.theme.Spacing
import com.ranti.voice.VoskWakeWordEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SPEC §13.2 — Wake Word settings.
 *
 * Enable/disable the wake word service, adjust sensitivity, and test detection
 * live with the real Vosk engine — mirrors the onboarding wake-word screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeWordSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val ranti = LocalRantiColors.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(0.5f) }
    var orbState by remember { mutableStateOf(OrbState.Idle) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var testEngine by remember { mutableStateOf<VoskWakeWordEngine?>(null) }

    LaunchedEffect(Unit) {
        enabled = OnboardingPrefs.isWakeWordEnabled(context)
        sensitivity = OnboardingPrefs.getWakeWordSensitivity(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            testEngine?.stop()
            testEngine?.release()
            testEngine = null
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Wake Word", style = MaterialTheme.typography.headlineSmall) },
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
            // Enable toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ranti.accent)
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable wake word", style = MaterialTheme.typography.bodyLarge, color = ranti.textHi)
                    Text("Say \"Hi Ranti\" to open the app", style = MaterialTheme.typography.bodySmall, color = ranti.textMid)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        scope.launch {
                            OnboardingPrefs.setWakeWordEnabled(context, on)
                            if (on) WakeWordService.start(context) else WakeWordService.stop(context)
                        }
                    },
                )
            }

            HorizontalDivider(color = ranti.borderSubtle)

            // Sensitivity slider
            Spacer(Modifier.height(Spacing.lg))
            Text("Sensitivity", style = MaterialTheme.typography.labelMedium, color = ranti.textMid)
            Spacer(Modifier.height(Spacing.xs))
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                onValueChangeFinished = {
                    scope.launch { OnboardingPrefs.setWakeWordSensitivity(context, sensitivity) }
                },
                valueRange = 0.25f..0.75f,
                steps = 1, // Low / Medium / High — 3 snap points
                enabled = enabled,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Low", style = MaterialTheme.typography.labelSmall, color = ranti.textLo)
                Text("Medium", style = MaterialTheme.typography.labelSmall, color = ranti.textLo)
                Text("High", style = MaterialTheme.typography.labelSmall, color = ranti.textLo)
            }

            // Test section — only shown when enabled
            if (enabled) {
                Spacer(Modifier.height(Spacing.xl))

                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    VoiceOrb(state = orbState, sizeDp = 140)
                }

                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = feedback ?: when (orbState) {
                        OrbState.Listening -> "Listening…"
                        OrbState.Speaking -> "Detected!"
                        else -> "Tap below to test"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ranti.textMid,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                Spacer(Modifier.height(Spacing.lg))

                OutlinedButton(
                    onClick = {
                        if (isTesting) return@OutlinedButton
                        isTesting = true
                        scope.launch {
                            orbState = OrbState.Listening
                            feedback = "Listening for \"Hi Ranti\"…"
                            val engine = VoskWakeWordEngine(context)
                            testEngine = engine
                            engine.setSensitivity(sensitivity)
                            var detected = false
                            engine.start { detected = true }
                            val timeoutMs = 30_000L
                            val interval = 200L
                            var elapsed = 0L
                            while (!detected && elapsed < timeoutMs) {
                                delay(interval)
                                elapsed += interval
                            }
                            engine.stop()
                            engine.release()
                            testEngine = null
                            if (detected) {
                                orbState = OrbState.Speaking
                                feedback = "I'm here! That worked perfectly."
                                delay(1500)
                            } else {
                                feedback = "Didn't hear that. Try again?"
                            }
                            orbState = OrbState.Idle
                            feedback = null
                            isTesting = false
                        }
                    },
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(if (isTesting) "Listening…" else "Test wake word")
                }

                Spacer(Modifier.height(Spacing.md))
                Text(
                    "Wake word uses less than 5% battery per day",
                    style = MaterialTheme.typography.bodySmall,
                    color = ranti.textLo,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
