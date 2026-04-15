package com.recall.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Border radius — see DESIGN_SYSTEM.md §"Border Radius".
val RecallShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // xs
    small = RoundedCornerShape(8.dp),         // sm — badges, pills
    medium = RoundedCornerShape(16.dp),       // md — input fields, small cards
    large = RoundedCornerShape(24.dp),        // lg — cards, sheets
    extraLarge = RoundedCornerShape(32.dp),   // xl — chat bubbles (dynamic squircle)
)

val PillShape = RoundedCornerShape(999.dp)
