package com.recall.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recall.ui.theme.LocalRecallColors
import com.recall.ui.theme.Spacing

/**
 * Placeholder for the Saved Places / Nicknames screen.
 * The real [com.recall.ui.screens.nicknames.NicknamesScreen] lands in Milestone §10.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicknamesPlaceholderScreen(onNavigateBack: () -> Unit) {
    val recall = LocalRecallColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Saved Places", style = MaterialTheme.typography.headlineSmall) },
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = recall.textLo,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                "Saved Places",
                style = MaterialTheme.typography.headlineMedium,
                color = recall.textHi,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Name your favourite spots — home, work, school — and say\n\"remind me when I get to home.\"",
                style = MaterialTheme.typography.bodyMedium,
                color = recall.textMid,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.xl),
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                "Coming soon",
                style = MaterialTheme.typography.labelLarge,
                color = recall.accent,
            )
        }
    }
}
