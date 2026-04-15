package com.recall.ui.screens.nicknames

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recall.network.PlaceOption
import com.recall.ui.theme.LocalRecallColors
import com.recall.ui.theme.Spacing

/**
 * SPEC §10 — Create or edit a place nickname.
 *
 * The screen has two sections:
 *  1. Nickname text field
 *  2. Place search — type a query, hit search, pick from results
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicknameEditScreen(
    onNavigateBack: () -> Unit,
    vm: NicknamesViewModel = viewModel(),
) {
    val form by vm.formState.collectAsStateWithLifecycle()
    val recall = LocalRecallColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (form.isEdit) "Edit Nickname" else "New Nickname",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
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
            Spacer(Modifier.height(Spacing.lg))

            // Nickname input
            Text(
                "Nickname",
                style = MaterialTheme.typography.labelLarge,
                color = recall.textHi,
            )
            Spacer(Modifier.height(Spacing.xs))
            OutlinedTextField(
                value = form.nickname,
                onValueChange = vm::onNicknameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. my hostel, home, the shop") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(Modifier.height(Spacing.lg))

            // Place search
            Text(
                "Place",
                style = MaterialTheme.typography.labelLarge,
                color = recall.textHi,
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = form.searchQuery,
                    onValueChange = vm::onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search for a place...") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.width(Spacing.sm))
                IconButton(
                    onClick = vm::searchPlaces,
                    enabled = form.searchQuery.isNotBlank() && !form.isSearching,
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }

            // Loading indicator
            if (form.isSearching) {
                Spacer(Modifier.height(Spacing.md))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Search results
            if (form.searchResults.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "Select a place:",
                    style = MaterialTheme.typography.labelMedium,
                    color = recall.textMid,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(form.searchResults) { place ->
                        PlaceSearchRow(
                            place = place,
                            onClick = { vm.selectPlace(place) },
                        )
                    }
                }
            }

            // Selected place preview
            if (form.placeName.isNotBlank() && form.lat != 0.0) {
                Spacer(Modifier.height(Spacing.lg))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.base),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column {
                            Text(
                                form.placeName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "%.5f, %.5f".format(form.lat, form.lng),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Save button
            Button(
                onClick = { vm.saveNickname(onSuccess = onNavigateBack) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.lg),
                enabled = form.nickname.isNotBlank()
                    && form.placeName.isNotBlank()
                    && form.lat != 0.0
                    && !form.isSaving,
            ) {
                if (form.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(if (form.isEdit) "Update" else "Save")
            }

            if (form.error != null) {
                Text(
                    form.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun PlaceSearchRow(
    place: PlaceOption,
    onClick: () -> Unit,
) {
    val recall = LocalRecallColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(
                    place.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = recall.textHi,
                )
                Text(
                    place.formatted_address,
                    style = MaterialTheme.typography.bodySmall,
                    color = recall.textMid,
                )
            }
        }
    }
}
