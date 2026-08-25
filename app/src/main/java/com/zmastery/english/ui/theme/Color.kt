package com.zmastery.english.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ==========================================================================
// «Dusk Indigo» — the Z-Mastery design language (v2, adopted 2026/08/25).
//
// A calm, modern learning palette: soft periwinkle indigo, teal support and a
// warm amber accent, on cool near-white / deep-navy canvases.
//
// Screens read the ZXxx properties directly; they are computed getters that
// depend on ZThemeState.isDark (a Compose state), so toggling dark mode
// recomposes the entire app instantly — no per-screen changes needed.
// ==========================================================================

object ZThemeState {
    var isDark by mutableStateOf(false)
}

// ---- Canvas & surfaces ----
val ZBackground: Color get() = if (ZThemeState.isDark) Color(0xFF12131C) else Color(0xFFF4F5F9)   // deep navy / cool near-white
val ZSurface: Color get() = if (ZThemeState.isDark) Color(0xFF1B1D2A) else Color(0xFFFFFFFF)      // bars / nav
val ZSurfaceVariant: Color get() = if (ZThemeState.isDark) Color(0xFF262938) else Color(0xFFE9EBF2) // chips / fills
val ZCard: Color get() = if (ZThemeState.isDark) Color(0xFF202231) else Color(0xFFFFFFFF)         // cards

// ---- Brand accents — lightened in dark mode for comfort ----
val ZIndigo: Color get() = if (ZThemeState.isDark) Color(0xFF8E94F0) else Color(0xFF5B62D6)       // primary periwinkle indigo
val ZPurple: Color get() = if (ZThemeState.isDark) Color(0xFF6A70E0) else Color(0xFF4148B8)       // deep indigo (gradient partner)
val ZCyan: Color get() = if (ZThemeState.isDark) Color(0xFF5BC2B0) else Color(0xFF2E9E8F)         // teal
val ZCyanDeep: Color get() = if (ZThemeState.isDark) Color(0xFF4FA996) else Color(0xFF1F7A6E)     // deep teal
val ZEmerald: Color get() = if (ZThemeState.isDark) Color(0xFF6FCB92) else Color(0xFF2FA36B)      // success green
val ZAmber: Color get() = if (ZThemeState.isDark) Color(0xFFE8B26A) else Color(0xFFE8A23D)        // warm gold
val ZRose: Color get() = if (ZThemeState.isDark) Color(0xFFE08282) else Color(0xFFD66060)         // soft coral (danger)

// ---- Text ----
val ZTextPrimary: Color get() = if (ZThemeState.isDark) Color(0xFFEDEEF4) else Color(0xFF2A2C38)
val ZTextSecondary: Color get() = if (ZThemeState.isDark) Color(0xFFA8ABC2) else Color(0xFF5F6272)
val ZTextMuted: Color get() = if (ZThemeState.isDark) Color(0xFF6E7188) else Color(0xFF9A9DAD)
val ZBorder: Color get() = if (ZThemeState.isDark) Color(0xFF323648) else Color(0xFFE2E4EC)

// ---- Semantic deep variants (readable text on tinted chips in LIGHT mode) ----
val ZEmeraldDeep: Color get() = if (ZThemeState.isDark) Color(0xFF9EE7BC) else Color(0xFF1F7A4D)
val ZRoseDeep: Color get() = if (ZThemeState.isDark) Color(0xFFF3B8B8) else Color(0xFFB4443E)
val ZAmberDeep: Color get() = if (ZThemeState.isDark) Color(0xFFF3D5A0) else Color(0xFF8F6614)

// Legacy names kept for any older references
val Purple80 = Color(0xFFC7CBF7)
val PurpleGrey80 = Color(0xFF323648)
val Pink80 = Color(0xFFE8B26A)
val Purple40 = Color(0xFF5B62D6)
val PurpleGrey40 = Color(0xFF2E9E8F)
val Pink40 = Color(0xFFE8A23D)
