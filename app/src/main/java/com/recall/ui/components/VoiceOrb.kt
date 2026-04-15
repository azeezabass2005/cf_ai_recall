package com.recall.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.recall.ui.theme.Accent
import com.recall.ui.theme.Primary

/**
 * VoiceOrb — Recall's signature visual. A soft glowing circle that breathes
 * when idle, pulses faster when listening, and brightens to violet when
 * speaking.
 *
 * Milestone §4 only needs Idle + Listening. Processing/Speaking states are
 * stubbed and will animate properly when wired up in milestone §6.
 */
enum class OrbState { Idle, Listening, Processing, Speaking }

@Composable
fun VoiceOrb(
    state: OrbState = OrbState.Idle,
    sizeDp: Int = 160,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val scale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = when (state) {
            OrbState.Listening -> 1.08f
            OrbState.Speaking -> 1.05f
            else -> 1.0f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    OrbState.Listening -> 900
                    OrbState.Speaking -> 600
                    else -> 1800
                },
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    val glow by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (state == OrbState.Listening) 0.6f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val coreColor: Color = when (state) {
        OrbState.Listening, OrbState.Speaking -> Accent
        else -> Primary
    }

    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(coreColor.copy(alpha = glow), coreColor.copy(alpha = glow * 0.3f), Color.Transparent),
                center = center,
                radius = r * 1.3f * scale,
            ),
            radius = r * 1.3f * scale,
            center = center,
        )
        // Core orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(coreColor.copy(alpha = 0.85f), coreColor),
                center = center,
                radius = r * 0.7f * scale,
            ),
            radius = r * 0.7f * scale,
            center = center,
        )
    }
}
