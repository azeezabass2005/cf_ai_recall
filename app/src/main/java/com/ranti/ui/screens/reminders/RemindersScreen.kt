package com.ranti.ui.screens.reminders

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ranti.network.ReminderDto
import com.ranti.ui.components.ReminderCard
import com.ranti.ui.theme.LocalRantiColors
import com.ranti.ui.theme.Spacing

/**
 * SPEC 11.1 — Reminder list with Active / Recurring / History tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onNavigateBack: () -> Unit,
    onCreateReminder: () -> Unit,
    onReminderClick: (String) -> Unit,
    vm: RemindersViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ranti = LocalRantiColors.current
    val snackbar = remember { SnackbarHostState() }

    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var showActionMenu by remember { mutableStateOf<ReminderDto?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Your Reminders", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateReminder,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New reminder")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tabs
            val tabs = ReminderTab.entries
            TabRow(selectedTabIndex = tabs.indexOf(state.selectedTab)) {
                tabs.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { vm.selectTab(tab) },
                        text = { Text(tab.name) },
                    )
                }
            }

            val currentList = when (state.selectedTab) {
                ReminderTab.Active -> state.activeList
                ReminderTab.Recurring -> state.recurringList
                ReminderTab.History -> state.historyList
            }

            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { vm.loadReminders() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (currentList.isEmpty() && !state.isLoading) {
                    EmptyState(state.selectedTab)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(Spacing.base),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(currentList, key = { it.id }) { reminder ->
                            SwipeToDismissBox(
                                state = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            pendingDeleteId = reminder.id
                                            vm.deleteReminder(reminder.id)
                                            true
                                        } else false
                                    },
                                ),
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(ranti.error)
                                            .padding(horizontal = Spacing.xl),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onError,
                                        )
                                    }
                                },
                                enableDismissFromStartToEnd = false,
                            ) {
                                ReminderCard(
                                    reminder = reminder,
                                    onClick = { onReminderClick(reminder.id) },
                                    onLongClick = { showActionMenu = reminder },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Long-press action menu
    showActionMenu?.let { reminder ->
        AlertDialog(
            onDismissRequest = { showActionMenu = null },
            title = { Text(reminder.body, maxLines = 1) },
            text = {
                Column {
                    if (reminder.recurrence != null) {
                        val isPaused = reminder.status == "paused"
                        TextButton(
                            onClick = {
                                if (isPaused) vm.resumeReminder(reminder.id)
                                else vm.pauseReminder(reminder.id)
                                showActionMenu = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (isPaused) "Resume" else "Pause")
                        }
                    }
                    TextButton(
                        onClick = {
                            vm.deleteReminder(reminder.id)
                            showActionMenu = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Delete", color = ranti.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionMenu = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyState(tab: ReminderTab) {
    val ranti = LocalRantiColors.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = when (tab) {
                    ReminderTab.Active -> Icons.Default.NotificationsNone
                    ReminderTab.Recurring -> Icons.Default.Repeat
                    ReminderTab.History -> Icons.Default.History
                },
                contentDescription = null,
                tint = ranti.textLo,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(Spacing.base))
            Text(
                text = when (tab) {
                    ReminderTab.Active -> "No reminders right now.\nTap + or say \"Hi Recall\" to add one."
                    ReminderTab.Recurring -> "No recurring reminders.\nSet one up for things that happen on a schedule."
                    ReminderTab.History -> "No completed reminders yet."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ranti.textMid,
                textAlign = TextAlign.Center,
            )
        }
    }
}
