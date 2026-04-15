package com.recall.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recall.ui.components.ChatBubble
import com.recall.ui.components.DisambiguationSheet
import com.recall.ui.components.OrbState
import com.recall.ui.components.VoiceOrb
import com.recall.ui.theme.LocalRecallColors
import com.recall.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * SPEC §5.1 — Chat (the home screen).
 *
 * Header: "Recall" title + reminders / + / settings icons.
 * Body: scrollable chat bubbles, newest at bottom.
 * Input bar: text mode (mic icon + text field + send) ↔ voice mode (orb).
 *
 * Voice input is wired up in milestone §6: the mic icon flips into voice
 * mode and starts a real [com.recall.voice.SpeechRecognizerManager] session,
 * partial transcripts surface as ghost text under the orb, and Recall speaks
 * her reply via [com.recall.voice.TextToSpeechManager].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenReminders: () -> Unit = {},
    onCreateReminder: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val recall = LocalRecallColors.current

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    // SPEC §8 — show disambiguation sheet when the agent returns multiple places.
    state.disambiguation?.let { disamb ->
        DisambiguationSheet(
            disambiguation = disamb,
            onSelect = vm::onDisambiguationSelect,
            onDismiss = vm::onDismissDisambiguation,
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Recall",
                        style = MaterialTheme.typography.headlineSmall,
                        color = recall.textHi,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenReminders) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "Reminders",
                            tint = recall.textHi,
                        )
                    }
                    IconButton(onClick = onCreateReminder) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New reminder",
                            tint = recall.textHi,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = recall.textHi,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // Chat area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                contentPadding = PaddingValues(vertical = Spacing.base),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(sender = message.sender, text = message.text)
                }
                if (state.isProcessing) {
                    item(key = "thinking") {
                        TypingIndicator()
                    }
                }
            }

            // Input bar
            when (state.inputMode) {
                InputMode.Text -> TextInputBar(
                    value = state.inputText,
                    onValueChange = vm::onInputChange,
                    onSend = vm::send,
                    onMic = vm::toggleInputMode,
                    enabled = !state.isProcessing,
                )
                InputMode.Voice -> VoiceInputBar(
                    state = state.voiceState,
                    partialTranscript = state.partialTranscript,
                    errorMessage = state.voiceError,
                    onSwitchToKeyboard = vm::cancelVoice,
                )
            }
        }
    }
}

// ─── Input bar variants ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    enabled: Boolean,
) {
    val recall = LocalRecallColors.current
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.base, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mic toggle
            IconButton(
                onClick = onMic,
                enabled = enabled,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice input",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Type a reminder…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = recall.textLo,
                    )
                },
                enabled = enabled,
                maxLines = 4,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { onSend() },
                ),
            )

            Spacer(Modifier.width(Spacing.sm))

            // Send button
            IconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled && value.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.primaryContainer,
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled && value.isNotBlank())
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun VoiceInputBar(
    state: OrbState,
    partialTranscript: String,
    errorMessage: String?,
    onSwitchToKeyboard: () -> Unit,
) {
    val recall = LocalRecallColors.current
    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VoiceOrb(state = state, sizeDp = 96)
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = when {
                    errorMessage != null -> errorMessage
                    state == OrbState.Listening -> "Listening…"
                    state == OrbState.Processing -> "Thinking…"
                    state == OrbState.Speaking -> "Speaking…"
                    else -> "Tap the keyboard to type"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = recall.textMid,
            )
            if (partialTranscript.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = partialTranscript,
                    style = MaterialTheme.typography.bodyLarge,
                    color = recall.textHi,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            IconButton(
                onClick = onSwitchToKeyboard,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Switch to keyboard",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ─── Typing indicator ────────────────────────────────────────────────────────

@Composable
private fun TypingIndicator() {
    val recall = LocalRecallColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(
                text = "…",
                style = MaterialTheme.typography.bodyLarge,
                color = recall.textMid,
                modifier = Modifier.padding(
                    horizontal = Spacing.base,
                    vertical = Spacing.md,
                ),
            )
        }
    }
}

// Suppress unused-param warning for the snapshot type — kept for future use
// when we wire up multi-turn disambiguation state lifting in milestone §9.
@Suppress("unused")
private fun touch(@Suppress("UNUSED_PARAMETER") x: SnapshotStateList<Any>) = Unit
