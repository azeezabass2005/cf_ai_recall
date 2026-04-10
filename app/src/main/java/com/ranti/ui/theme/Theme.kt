package com.ranti.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Ranti color tokens that don't fit Material3's slot model
 * (accent, time/location triggers, the muted text tier, etc.).
 *
 * Material3's `colorScheme` covers `primary`, `surface`, `background`, etc.
 * Anything Ranti-specific lives here and is read via `LocalRantiColors.current`.
 */
@Immutable
data class RantiColors(
    val accent: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val textHi: Color,
    val textMid: Color,
    val textLo: Color,
    val borderSubtle: Color,
    val timeTrigger: Color,
    val timeTriggerSoft: Color,
    val locationTrigger: Color,
    val locationTriggerSoft: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

val LocalRantiColors = staticCompositionLocalOf {
    // Sensible default — overridden by RantiTheme.
    RantiColors(
        accent = Accent,
        accentDeep = AccentDeep,
        accentSoft = AccentSoftLight,
        textHi = TextHiLight,
        textMid = TextMidLight,
        textLo = TextLoLight,
        borderSubtle = BorderSubtleLight,
        timeTrigger = TimeTrigger,
        timeTriggerSoft = TimeTriggerSoftLight,
        locationTrigger = LocationTrigger,
        locationTriggerSoft = LocationTriggerSoftLight,
        success = Success,
        warning = Warning,
        error = ErrorRed,
    )
}

private val LightScheme = lightColorScheme(
    primary = Primary,
    onPrimary = SurfaceLight,
    primaryContainer = PrimarySoftLight,
    onPrimaryContainer = PrimaryDeep,
    background = BaseLight,
    onBackground = TextHiLight,
    surface = SurfaceLight,
    onSurface = TextHiLight,
    surfaceVariant = InputBgLight,
    onSurfaceVariant = TextMidLight,
    outline = BorderLight,
    error = ErrorRed,
)

private val DarkScheme = darkColorScheme(
    primary = Primary,
    onPrimary = SurfaceDark,
    primaryContainer = PrimarySoftDark,
    onPrimaryContainer = PrimarySoftLight,
    background = BaseDark,
    onBackground = TextHiDark,
    surface = SurfaceDark,
    onSurface = TextHiDark,
    surfaceVariant = InputBgDark,
    onSurfaceVariant = TextMidDark,
    outline = BorderDark,
    error = ErrorRed,
)

@Composable
fun RantiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val rantiColors = if (darkTheme) {
        RantiColors(
            accent = Accent,
            accentDeep = AccentDeep,
            accentSoft = AccentSoftDark,
            textHi = TextHiDark,
            textMid = TextMidDark,
            textLo = TextLoDark,
            borderSubtle = BorderSubtleDark,
            timeTrigger = TimeTrigger,
            timeTriggerSoft = TimeTriggerSoftDark,
            locationTrigger = LocationTrigger,
            locationTriggerSoft = LocationTriggerSoftDark,
            success = Success,
            warning = Warning,
            error = ErrorRed,
        )
    } else {
        RantiColors(
            accent = Accent,
            accentDeep = AccentDeep,
            accentSoft = AccentSoftLight,
            textHi = TextHiLight,
            textMid = TextMidLight,
            textLo = TextLoLight,
            borderSubtle = BorderSubtleLight,
            timeTrigger = TimeTrigger,
            timeTriggerSoft = TimeTriggerSoftLight,
            locationTrigger = LocationTrigger,
            locationTriggerSoft = LocationTriggerSoftLight,
            success = Success,
            warning = Warning,
            error = ErrorRed,
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalRantiColors provides rantiColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RantiTypography,
            shapes = RantiShapes,
            content = content,
        )
    }
}
