package com.recall.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.recall.BuildConfig
import com.recall.ui.theme.LocalRecallColors
import com.recall.ui.theme.Spacing

/**
 * SPEC §13.6 — About Recall.
 *
 * Version info, backend note, open-source licenses, and privacy statement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val recall = LocalRecallColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("About Recall", style = MaterialTheme.typography.headlineSmall) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                InfoRow(
                    icon = Icons.Default.Info,
                    title = "Version",
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
                HorizontalDivider(color = recall.borderSubtle)
            }
            item {
                InfoRow(
                    icon = Icons.Default.Cloud,
                    title = "Backend",
                    value = "Cloudflare Workers",
                )
                HorizontalDivider(color = recall.borderSubtle)
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Article,
                    title = "Open-source licenses",
                    subtitle = "Third-party software used in Recall",
                    onClick = { /* TODO: launch licenses screen */ },
                )
                HorizontalDivider(color = recall.borderSubtle)
            }
            item {
                Spacer(Modifier.height(Spacing.xl))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.base),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(Spacing.base)) {
                        Row {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = recall.accent)
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Privacy", style = MaterialTheme.typography.titleSmall, color = recall.textHi)
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "Recall processes your voice on-device using PocketSphinx. Your reminders sync with your personal Cloudflare Worker — no third-party servers see your data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = recall.textMid,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}
