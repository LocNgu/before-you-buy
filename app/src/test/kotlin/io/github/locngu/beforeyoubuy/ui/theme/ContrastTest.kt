package io.github.locngu.beforeyoubuy.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.x AA contrast for the fixed palette in light and dark (#30): 4.5:1 for text, 3:1 for UI boundaries. */
class ContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun textPairs(c: ColorScheme) = listOf(
        "onPrimary/primary" to (c.onPrimary to c.primary),
        "onPrimaryContainer/primaryContainer" to (c.onPrimaryContainer to c.primaryContainer),
        "onSecondary/secondary" to (c.onSecondary to c.secondary),
        "onSecondaryContainer/secondaryContainer" to (c.onSecondaryContainer to c.secondaryContainer),
        "onTertiary/tertiary" to (c.onTertiary to c.tertiary),
        "onTertiaryContainer/tertiaryContainer" to (c.onTertiaryContainer to c.tertiaryContainer),
        "onError/error" to (c.onError to c.error),
        "onErrorContainer/errorContainer" to (c.onErrorContainer to c.errorContainer),
        "inverseOnSurface/inverseSurface" to (c.inverseOnSurface to c.inverseSurface),
        "inversePrimary/inverseSurface" to (c.inversePrimary to c.inverseSurface)
    ) +
        listOf(
            c.background,
            c.surface,
            c.surfaceDim,
            c.surfaceBright,
            c.surfaceContainerLowest,
            c.surfaceContainerLow,
            c.surfaceContainer,
            c.surfaceContainerHigh,
            c.surfaceContainerHighest,
            c.surfaceVariant
        ).flatMapIndexed { i, bg ->
            val fg = if (bg == c.surfaceVariant) c.onSurfaceVariant else c.onSurface
            listOf("onSurface text on surface role #$i" to (fg to bg)) +
                // Secondary text and the primary accent are also drawn on plain surfaces.
                listOf("onSurfaceVariant on surface role #$i" to (c.onSurfaceVariant to bg))
                    .filter { bg != c.surfaceVariant } +
                listOf("primary on surface role #$i" to (c.primary to bg))
        }

    private fun uiPairs(c: ColorScheme) = listOf(
        // Input borders and other boundaries a control needs to be found by (WCAG 1.4.11).
        "outline/surface" to (c.outline to c.surface),
        "outline/background" to (c.outline to c.background)
    )

    private fun check(name: String, scheme: ColorScheme) {
        val failures =
            textPairs(scheme).filter { (_, p) ->
                ratio(p.first.compositeOver(p.second), p.second) < 4.5
            }.map { it.first } +
                uiPairs(scheme).filter { (_, p) -> ratio(p.first, p.second) < 3.0 }.map { it.first }
        assertTrue("$name contrast failures: $failures", failures.isEmpty())
    }

    @Test fun lightPaletteMeetsAa() = check("light", LightColors)

    @Test fun darkPaletteMeetsAa() = check("dark", DarkColors)
}
