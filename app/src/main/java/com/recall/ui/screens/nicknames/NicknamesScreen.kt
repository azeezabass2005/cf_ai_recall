package com.recall.ui.screens.nicknames

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recall.network.NicknameDto
import com.recall.ui.theme.LocalRecallColors
import com.recall.ui.theme.Spacing

/**
 * SPEC §10 — Saved Places / Nicknames list screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicknamesScreen(
    onNavigateBack: () -> Unit,
    onCreateNickname: () -> Unit,
    onEditNickname: (String) -> Unit,
    vm: NicknamesViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
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
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNickname) {
                Icon(Icons.Default.Add, contentDescription = "Add nickname")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.nicknames.isEmpty() -> {
                EmptyNicknamesState(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    error = state.error,
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = Spacing.base, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.nicknames, key = { it.id }) { nickname ->
                        NicknameRow(
                            nickname = nickname,
                            onDelete = { vm.deleteNickname(nickname) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NicknameRow(
    nickname: NicknameDto,
    onDelete: () -> Unit,
) {
    val recall = LocalRecallColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nickname.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    color = recall.textHi,
                )
                Text(
                    text = nickname.place_name,
                    style = MaterialTheme.typography.bodySmall,
                    color = recall.textMid,
                )
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete nickname?") },
            text = { Text("Remove \"${nickname.nickname}\" from your saved places?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyNicknamesState(modifier: Modifier = Modifier, error: String?) {
    val recall = LocalRecallColors.current
    Column(
        modifier = modifier,
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
            "No saved places yet",
            style = MaterialTheme.typography.headlineMedium,
            color = recall.textHi,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Tap + to name your favourite spots — home, work, school —\nand say \"remind me when I get to home.\"",
            style = MaterialTheme.typography.bodyMedium,
            color = recall.textMid,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
        if (error != null) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
