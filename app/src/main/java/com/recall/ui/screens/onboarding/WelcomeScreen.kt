package com.recall.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recall.ui.components.OrbState
import com.recall.ui.components.VoiceOrb
import com.recall.ui.theme.LocalRecallColors
import com.recall.ui.theme.Spacing

/**
 * SPEC §4.1 — Welcome.
 * Hero (voice orb), tagline, "Get Started" CTA → permissions.
 */
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val recall = LocalRecallColors.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            VoiceOrb(state = OrbState.Idle, sizeDp = 180)

            Spacer(Modifier.height(Spacing.huge))

            Text(
                text = "Recall",
                style = MaterialTheme.typography.displayLarge,
                color = recall.textHi,
            )

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "Remember everything.\nSay it once.",
                style = MaterialTheme.typography.headlineSmall,
                color = recall.textMid,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.giant))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
