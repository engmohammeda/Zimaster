package com.zmastery.english.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ==========================================================================
// Dynamic warm palette with full light + dark support.
// Screens read the ZXxx properties directly; they are computed getters that
// depend on ZThemeState.isDark (a Compose state), so toggling dark mode
// recomposes the entire app instantly — no per-screen changes needed.
// ==========================================================================

object ZThemeState {
    var isDark by mutableStateOf(false)
}

// ---- Canvas & surfaces ----
val ZBackground: Color get() = if (ZThemeState.isDark) Color(0xFF1A1613) else Color(0xFFF3ECE1) // warm charcoal / warm cream
val ZSurface: Color get() = if (ZThemeState.isDark) Color(0xFF241F1A) else Color(0xFFFFFFFF)     // bars / nav
val ZSurfaceVariant: Color get() = if (ZThemeState.isDark) Color(0xFF2E2822) else Color(0xFFEDE3D5) // chips / fills
val ZCard: Color get() = if (ZThemeState.isDark) Color(0xFF29231D) else Color(0xFFFFFFFF)        // cards

// ---- Brand accents (warm) — softened slightly in dark for comfort ----
val ZIndigo: Color get() = if (ZThemeState.isDark) Color(0xFFE88968) else Color(0xFFE07856)      // primary terracotta
val ZPurple: Color get() = if (ZThemeState.isDark) Color(0xFFD46F4F) else Color(0xFFCB5F41)      // deep sienna (gradient)
val ZCyan: Color get() = if (ZThemeState.isDark) Color(0xFF87AA9A) else Color(0xFF6B9080)        // sage green
val ZCyanDeep: Color get() = if (ZThemeState.isDark) Color(0xFF6B9488) else Color(0xFF52796F)    // pine teal
val ZEmerald: Color get() = if (ZThemeState.isDark) Color(0xFF74B48C) else Color(0xFF5E9C76)     // success green
val ZAmber: Color get() = if (ZThemeState.isDark) Color(0xFFE9B36A) else Color(0xFFE0A34E)       // warm gold
val ZRose: Color get() = if (ZThemeState.isDark) Color(0xFFE38C80) else Color(0xFFD9776A)        // soft coral

// ---- Text ----
val ZTextPrimary: Color get() = if (ZThemeState.isDark) Color(0xFFF3EDE4) else Color(0xFF33302C)
val ZTextSecondary: Color get() = if (ZThemeState.isDark) Color(0xFFB6ABA0) else Color(0xFF6F6A62)
val ZTextMuted: Color get() = if (ZThemeState.isDark) Color(0xFF7E7468) else Color(0xFFA79F94)
val ZBorder: Color get() = if (ZThemeState.isDark) Color(0xFF3A322A) else Color(0xFFEDE4D6)

// Legacy names kept for any Theme references
val Purple80 = Color(0xFFF5C6B0)
val PurpleGrey80 = Color(0xFFDED3C6)
val Pink80 = Color(0xFFF3C4A6)
val Purple40 = Color(0xFFE07856)
val PurpleGrey40 = Color(0xFF6B9080)
val Pink40 = Color(0xFFD9776A)
