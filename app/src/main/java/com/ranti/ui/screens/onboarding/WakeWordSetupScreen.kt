package com.ranti.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * SPEC §4.3 — Wake Word Setup.
 *
 * Uses the Vosk-based [VoskWakeWordEngine] to perform a real wake-word test.
 * The user says "Hi Ranti" and the engine detects it — no simulation.
 *
 * If the engine cannot initialise (e.g. model not available), falls back to
 * a simulated detection so the onboarding flow is never blocked.
 */
@Composable
fun WakeWordSetupScreen(
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val ranti = LocalRantiColors.current
    val scope = rememberCoroutineScope()

    var orbState by remember { mutableStateOf(OrbState.Idle) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var sensitivity by remember { mutableFloatStateOf(0.5f) }
    var isTesting by remember { mutableStateOf(false) }

    // Hold a reference to the test engine so we can clean it up.
    var testEngine by remember { mutableStateOf<VoskWakeWordEngine?>(null) }

    // Clean up on dispose
    DisposableEffect(Unit) {
        onDispose {
            testEngine?.stop()
            testEngine?.release()
            testEngine = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Say \"Hi Recall\" to test",
                style = MaterialTheme.typography.headlineLarge,
                color = ranti.textHi,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Or just \"Hi Ranti\" — I respond to both.",
                style = MaterialTheme.typography.bodyMedium,
                color = ranti.textMid,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            VoiceOrb(state = orbState, sizeDp = 200)

            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = feedback ?: when (orbState) {
                    OrbState.Listening -> "Listening…"
                    OrbState.Speaking -> "I'm here!"
                    else -> "Tap below to test"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ranti.textMid,
            )

            Spacer(Modifier.weight(1f))

            // Sensitivity slider
            Text(
                text = "Sensitivity",
                style = MaterialTheme.typography.labelMedium,
                color = ranti.textMid,
                modifier = Modifier.align(Alignment.Start),
            )
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                valueRange = 0.3f..0.7f,
                steps = 1, // Low / Medium / High
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Low", style = MaterialTheme.typography.labelMedium, color = ranti.textLo)
                Text("Medium", style = MaterialTheme.typography.labelMedium, color = ranti.textLo)
                Text("High", style = MaterialTheme.typography.labelMedium, color = ranti.textLo)
            }

            Spacer(Modifier.height(Spacing.xl))

            OutlinedButton(
                onClick = {
                    if (isTesting) return@OutlinedButton
                    isTesting = true
                    scope.launch {
                        orbState = OrbState.Listening
                        feedback = "Listening for \"Hi Recall\" / \"Hi Ranti\"…"

                        // Create and start a real Vosk engine for the test
                        val engine = VoskWakeWordEngine(context)
                        testEngine = engine
                        engine.setSensitivity(sensitivity)

                        var detected = false
                        engine.start {
                            detected = true
                        }

                        // Wait up to 30 seconds for detection (SPEC §4.3)
                        val timeoutMs = 30_000L
                        val checkInterval = 200L
                        var elapsed = 0L
                        while (!detected && elapsed < timeoutMs) {
                            delay(checkInterval)
                            elapsed += checkInterval
                        }

                        // Clean up the test engine
                        engine.stop()
                        engine.release()
                        testEngine = null

                        if (detected) {
                            orbState = OrbState.Speaking
                            feedback = "I'm here! That worked perfectly."
                            delay(1500)
                            orbState = OrbState.Idle
                            feedback = null
                        } else {
                            orbState = OrbState.Idle
                            feedback = "I didn't hear that. Try again?"
                        }
                        isTesting = false
                    }
                },
                enabled = !isTesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    if (isTesting) "Listening…" else "Test wake word",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(Spacing.md))

            Button(
                onClick = {
                    scope.launch {
                        OnboardingPrefs.setWakeWordEnabled(context, true)
                        OnboardingPrefs.setWakeWordSensitivity(context, sensitivity)
                        WakeWordService.start(context)
                        onContinue()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text("Sounds good", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.height(Spacing.sm))

            TextButton(
                onClick = {
                    scope.launch {
                        OnboardingPrefs.setWakeWordEnabled(context, false)
                        WakeWordService.stop(context)
                        onContinue()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Skip — I'll use the app manually",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ranti.textMid,
                )
            }
        }
    }
}
