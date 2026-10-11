package dev.placeholder.beforeyoubuy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

// The platform font (no bundled font files). Material's scale with calmer, lighter headlines:
// the app reads like a notebook, not a shop banner.
private val base = Typography()

internal val AppTypography =
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Normal),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Normal),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Normal),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium)
    )
