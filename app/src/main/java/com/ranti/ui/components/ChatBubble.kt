package com.ranti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ranti.ui.theme.LocalRantiColors
import com.ranti.ui.theme.Spacing

enum class BubbleSender { User, Ranti }

/**
 * ChatBubble — see SPEC §5.1.
 *
 * User bubbles right-aligned, primary container background.
 * Ranti bubbles left-aligned, surface background, Plus Jakarta Sans body-lg.
 */

val UserBubbleShape = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 24.dp,
    bottomEnd = 6.dp,
    bottomStart = 24.dp
)

val RantiBubbleShape = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 24.dp,
    bottomEnd = 24.dp,
    bottomStart = 6.dp
)

@Composable
fun ChatBubble(
    sender: BubbleSender,
    text: String,
    modifier: Modifier = Modifier,
) {
    val ranti = LocalRantiColors.current
    val isUser = sender == BubbleSender.User

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = 6.dp), // slightly airier spacing
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(if (isUser) UserBubbleShape else RantiBubbleShape)
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                )
                .padding(horizontal = 20.dp, vertical = 14.dp), // Sleeker inner padding
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = if (isUser) MaterialTheme.typography.bodyMedium
                else MaterialTheme.typography.bodyLarge,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else ranti.textHi,
            )
        }
    }
}
