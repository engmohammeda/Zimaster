package com.zmastery.english.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

private val White = Color(0xFFFFFFFF)

@Composable
fun ZMasteryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Drive the dynamic palette used by every screen.
    SideEffect { ZThemeState.isDark = darkTheme }
    ZThemeState.isDark = darkTheme

    // On-colors tuned for the Dusk Indigo palette: dark text on the lighter
    // accent colors in light mode (teal/amber), dark-navy text on the
    // lightened accents in dark mode — both guarantee readable contrast.
    val onAccentDark = Color(0xFF12131C)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = ZIndigo,
            onPrimary = Color.White,
            primaryContainer = ZPurple,
            onPrimaryContainer = Color.White,
            secondary = ZCyan,
            onSecondary = onAccentDark,
            tertiary = ZAmber,
            onTertiary = onAccentDark,
            background = ZBackground,
            onBackground = ZTextPrimary,
            surface = ZSurface,
            onSurface = ZTextPrimary,
            surfaceVariant = ZSurfaceVariant,
            onSurfaceVariant = ZTextSecondary,
            outline = ZBorder,
            error = ZRose,
            onError = onAccentDark,
        )
    } else {
        lightColorScheme(
            primary = ZIndigo,
            onPrimary = Color.White,
            primaryContainer = ZPurple,
            onPrimaryContainer = Color.White,
            secondary = ZCyan,
            onSecondary = Color.White,
            tertiary = ZAmber,
            onTertiary = Color.White,
            background = ZBackground,
            onBackground = ZTextPrimary,
            surface = ZSurface,
            onSurface = ZTextPrimary,
            surfaceVariant = ZSurfaceVariant,
            onSurfaceVariant = ZTextSecondary,
            outline = ZBorder,
            error = ZRose,
            onError = Color.White,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZTypography,
        content = content,
    )
}

/** Legacy template name — the app theme is now [ZMasteryTheme]. */
@Deprecated("Renamed to ZMasteryTheme", ReplaceWith("ZMasteryTheme(darkTheme, dynamicColor, content)"))
@Composable
fun AgonAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) = ZMasteryTheme(darkTheme, dynamicColor, content)
