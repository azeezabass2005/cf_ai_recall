package com.ranti.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Ranti type scale — see DESIGN_SYSTEM.md §"Typography".
// TODO: Plus Jakarta Sans is the spec font; bundling deferred — falling back
// to FontFamily.Default keeps the metrics close enough for milestone §4 and
// avoids dragging in font assets before they're needed.

private val FF = FontFamily.Default

val RantiTypography = Typography(
    // display: 36/44 ExtraBold — onboarding hero
    displayLarge = TextStyle(
        fontFamily = FF, fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp, lineHeight = 44.sp,
    ),
    // h1: 28/36 Bold
    headlineLarge = TextStyle(
        fontFamily = FF, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 36.sp,
    ),
    // h2: 24/32 Bold
    headlineMedium = TextStyle(
        fontFamily = FF, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 32.sp,
    ),
    // h3: 20/28 SemiBold — card titles, dialog headers
    headlineSmall = TextStyle(
        fontFamily = FF, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    // body-lg: 18/28 Regular — Ranti's chat replies
    bodyLarge = TextStyle(
        fontFamily = FF, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, lineHeight = 28.sp,
    ),
    // body-md: 16/24 Regular — default body
    bodyMedium = TextStyle(
        fontFamily = FF, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    // body-sm: 14/22 Regular
    bodySmall = TextStyle(
        fontFamily = FF, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp,
    ),
    // caption: 12/18 Medium
    labelMedium = TextStyle(
        fontFamily = FF, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 18.sp,
    ),
    // label: 11/16 SemiBold — badges, tiny labels
    labelSmall = TextStyle(
        fontFamily = FF, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 16.sp,
    ),
)
