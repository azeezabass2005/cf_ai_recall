package com.ranti.ui.theme

import androidx.compose.ui.graphics.Color

// Ranti color tokens — see DESIGN_SYSTEM.md §"Color System".
// Light values first, dark second; the picker happens in Theme.kt.

// Backgrounds
val BaseLight = Color(0xFFF9FAFB) // Sleeker off-white
val BaseDark = Color(0xFF090A0F)  // Deep OLED-friendly dark
val SurfaceLight = Color(0xFFFFFFFF) // Pure white
val SurfaceDark = Color(0xFF14151C)  // Slightly elevated sleek dark
val ElevatedLight = Color(0xFFFFFFFF)
val ElevatedDark = Color(0xFF1C1D26)
val InputBgLight = Color(0xFFF3F4F6)
val InputBgDark = Color(0xFF1E1F29)
val BorderLight = Color(0xFFE5E7EB)
val BorderDark = Color(0xFF2E303E)
val BorderSubtleLight = Color(0xFFF3F4F6)
val BorderSubtleDark = Color(0xFF1C1D26)

// Text
val TextHiLight = Color(0xFF111827)
val TextHiDark = Color(0xFFF9FAFB)
val TextMidLight = Color(0xFF6B7280)
val TextMidDark = Color(0xFF9CA3AF)
val TextLoLight = Color(0xFF9CA3AF)
val TextLoDark = Color(0xFF4B5563)

// Brand: Indigo (primary)
val Primary = Color(0xFF4F46E5) // More vibrant, premium Indigo
val PrimaryDeep = Color(0xFF4338CA)
val PrimarySoftLight = Color(0xFFEEF2FF)
val PrimarySoftDark = Color(0xFF1E1B4B)

// Brand: Violet (accent)
val Accent = Color(0xFF8B5CF6)
val AccentDeep = Color(0xFF7C3AED)
val AccentSoftLight = Color(0xFFF5F3FF)
val AccentSoftDark = Color(0xFF2E1065)

// Reminder triggers
val TimeTrigger = Color(0xFFF59E0B) // Amber
val TimeTriggerSoftLight = Color(0xFFFEF3C7)
val TimeTriggerSoftDark = Color(0xFF451A03)
val LocationTrigger = Color(0xFF10B981) // Emerald
val LocationTriggerSoftLight = Color(0xFFD1FAE5)
val LocationTriggerSoftDark = Color(0xFF064E3B)

// Status
val Success = Color(0xFF10B981)
val Warning = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)
