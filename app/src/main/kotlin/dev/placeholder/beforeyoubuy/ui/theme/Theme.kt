package dev.placeholder.beforeyoubuy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The app's Material 3 theme: the fixed calm palette in light and dark, no dynamic colour (ADR-0026).
 * Wrap every screen, preview and screenshot test in it.
 */
@Composable
fun BeforeYouBuyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
