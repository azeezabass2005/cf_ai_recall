package com.ranti.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ranti.network.DisambiguationDto
import com.ranti.network.PlaceOption
import com.ranti.ui.theme.LocalRantiColors
import com.ranti.ui.theme.Spacing

/**
 * SPEC §8 — Disambiguation bottom sheet.
 *
 * Shown when the agent's resolve_place call returns multiple matches.
 * The user picks one, and their selection is sent as a chat message so the
 * LLM can create the reminder with the chosen place's coordinates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisambiguationSheet(
    disambiguation: DisambiguationDto,
    onSelect: (PlaceOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val ranti = LocalRantiColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.base, vertical = Spacing.md),
        ) {
            Text(
                text = disambiguation.prompt,
                style = MaterialTheme.typography.titleMedium,
                color = ranti.textHi,
                modifier = Modifier.padding(bottom = Spacing.md),
            )

            disambiguation.options.forEachIndexed { index, option ->
                PlaceOptionRow(
                    index = index + 1,
                    option = option,
                    onClick = { onSelect(option) },
                )
                if (index < disambiguation.options.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Spacing.xs),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun PlaceOptionRow(
    index: Int,
    option: PlaceOption,
    onClick: () -> Unit,
) {
    val ranti = LocalRantiColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.md, horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$index. ${option.name}",
                style = MaterialTheme.typography.bodyLarge,
                color = ranti.textHi,
            )
            Text(
                text = option.formatted_address,
                style = MaterialTheme.typography.bodySmall,
                color = ranti.textMid,
            )
        }
    }
}
