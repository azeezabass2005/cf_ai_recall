package com.ranti.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Border radius — see DESIGN_SYSTEM.md §"Border Radius".
val RantiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // xs
    small = RoundedCornerShape(8.dp),         // sm — badges, pills
    medium = RoundedCornerShape(12.dp),       // md — input fields, small cards
    large = RoundedCornerShape(16.dp),        // lg — cards, sheets
    extraLarge = RoundedCornerShape(20.dp),   // xl — chat bubbles
)

val PillShape = RoundedCornerShape(999.dp)
