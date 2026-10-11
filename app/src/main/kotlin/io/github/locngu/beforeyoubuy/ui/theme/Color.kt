package io.github.locngu.beforeyoubuy.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Calm, fixed palette (ADR-0028): Material 3 "tonal spot" scheme (2021 spec) generated from the muted
// teal seed #4F7A74 with material-color-utilities. Regenerate rather than hand-tune single roles.
// `error` is Material's standard red, for input errors only: never for countdowns or prices.

internal val LightColors =
    lightColorScheme(
        primary = Color(0xFF006A62),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF9DF2E6),
        onPrimaryContainer = Color(0xFF005049),
        inversePrimary = Color(0xFF81D5CA),
        secondary = Color(0xFF4A635F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8E3),
        onSecondaryContainer = Color(0xFF324B48),
        tertiary = Color(0xFF46617A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCDE5FF),
        onTertiaryContainer = Color(0xFF2E4961),
        background = Color(0xFFF4FBF8),
        onBackground = Color(0xFF161D1C),
        surface = Color(0xFFF4FBF8),
        onSurface = Color(0xFF161D1C),
        surfaceVariant = Color(0xFFDAE5E2),
        onSurfaceVariant = Color(0xFF3F4947),
        surfaceTint = Color(0xFF006A62),
        inverseSurface = Color(0xFF2B3230),
        inverseOnSurface = Color(0xFFECF2F0),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        outline = Color(0xFF6F7977),
        outlineVariant = Color(0xFFBEC9C6),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF4FBF8),
        surfaceDim = Color(0xFFD5DBD9),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEFF5F3),
        surfaceContainer = Color(0xFFE9EFED),
        surfaceContainerHigh = Color(0xFFE3EAE7),
        surfaceContainerHighest = Color(0xFFDDE4E2)
    )

internal val DarkColors =
    darkColorScheme(
        primary = Color(0xFF81D5CA),
        onPrimary = Color(0xFF003732),
        primaryContainer = Color(0xFF005049),
        onPrimaryContainer = Color(0xFF9DF2E6),
        inversePrimary = Color(0xFF006A62),
        secondary = Color(0xFFB1CCC7),
        onSecondary = Color(0xFF1C3531),
        secondaryContainer = Color(0xFF324B48),
        onSecondaryContainer = Color(0xFFCCE8E3),
        tertiary = Color(0xFFAEC9E6),
        onTertiary = Color(0xFF163349),
        tertiaryContainer = Color(0xFF2E4961),
        onTertiaryContainer = Color(0xFFCDE5FF),
        background = Color(0xFF0E1514),
        onBackground = Color(0xFFDDE4E2),
        surface = Color(0xFF0E1514),
        onSurface = Color(0xFFDDE4E2),
        surfaceVariant = Color(0xFF3F4947),
        onSurfaceVariant = Color(0xFFBEC9C6),
        surfaceTint = Color(0xFF81D5CA),
        inverseSurface = Color(0xFFDDE4E2),
        inverseOnSurface = Color(0xFF2B3230),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF899390),
        outlineVariant = Color(0xFF3F4947),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF343A39),
        surfaceDim = Color(0xFF0E1514),
        surfaceContainerLowest = Color(0xFF090F0E),
        surfaceContainerLow = Color(0xFF161D1C),
        surfaceContainer = Color(0xFF1A2120),
        surfaceContainerHigh = Color(0xFF252B2A),
        surfaceContainerHighest = Color(0xFF303635)
    )
