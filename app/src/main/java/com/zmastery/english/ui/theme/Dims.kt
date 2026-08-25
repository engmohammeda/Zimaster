package com.zmastery.english.ui.theme

import androidx.compose.ui.unit.dp

// ==========================================================================
// Z-Design System v2 — dimension tokens.
//
// The whole UI normalizes onto these scales (see docs/DESIGN_AUDIT.md):
//   • Radii:  30 ad-hoc values  → 6 tokens (XS…PILL)
//   • Space:  16 off-grid values → 4dp grid (XS…XXL)
//   • Sizes:  icon/tile/plate standards for badge-like elements
// New code should reference these tokens instead of raw dp literals.
// ==========================================================================

/** Corner radius scale. */
object ZRadius {
    val XS = 8.dp    // chips, small tags, segmented controls
    val S = 12.dp    // buttons, inputs, small cards
    val M = 16.dp    // standard cards
    val L = 20.dp    // large cards, sheets
    val XL = 24.dp   // hero cards, sheets' header
    val PILL = 50    // fully-rounded pills (use with RoundedCornerShape(50))
}

/** Spacing scale — strict 4dp grid. */
object ZSpace {
    val XS = 4.dp    // hairline gaps inside rows
    val S = 8.dp     // inner element spacing
    val M = 12.dp    // default inner card spacing
    val L = 16.dp    // standard card padding / section spacing
    val XL = 20.dp   // generous card padding
    val XXL = 24.dp  // hero padding / screen margins (landscape)
}

/** Standard element sizes (badges, icon tiles, minimum touch targets). */
object ZSize {
    val iconS = 18.dp   // icons inside 28-30dp tiles
    val iconM = 24.dp   // standalone icons
    val tileS = 28.dp   // numbered/icoBadges in headers
    val tileM = 32.dp   // action buttons (play-all)
    val tileL = 44.dp   // icon plates in lists
    val hit = 48.dp     // minimum touch target
}
