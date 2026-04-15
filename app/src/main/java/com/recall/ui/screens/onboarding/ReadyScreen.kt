package com.recall.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recall.data.OnboardingPrefs
import com.recall.ui.theme.LocalRecallColors
import com.recall.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * SPEC §4.4 — Ready.
 * Tagline + 3 example chat bubbles + "Start Using Recall" CTA → chat.
 * Marks onboarding complete in DataStore so subsequent launches skip the flow.
 */
@Composable
fun ReadyScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val recall = LocalRecallColors.current
    val scope = rememberCoroutineScope()

    val examples = listOf(
        "Remind me to call mum at 7pm",
        "Remind me to buy eggs when I get to Shoprite",
        "Remind me to check the pot in 15 minutes",
    )

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
            Spacer(Modifier.height(Spacing.huge))

            Text(
                text = "You're all set",
                style = MaterialTheme.typography.displayLarge,
                color = recall.textHi,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.base))

            Text(
                text = "Just say \"Hi Recall\" anytime, or open the app and type. I'll remember so you don't have to.",
                style = MaterialTheme.typography.bodyLarge,
                color = recall.textMid,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.huge))

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                examples.forEach { example ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = example,
                            style = MaterialTheme.typography.bodyMedium,
                            color = recall.textHi,
                            modifier = Modifier.padding(
                                horizontal = Spacing.base,
                                vertical = Spacing.md,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    scope.launch {
                        OnboardingPrefs.markComplete(context)
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text("Start Using Recall", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
