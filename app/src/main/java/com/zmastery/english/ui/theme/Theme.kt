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
fun AgonAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Drive the dynamic palette used by every screen.
    SideEffect { ZThemeState.isDark = darkTheme }
    ZThemeState.isDark = darkTheme

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = ZIndigo,
            onPrimary = White,
            primaryContainer = ZPurple,
            onPrimaryContainer = White,
            secondary = ZCyan,
            onSecondary = Color(0xFF1A1613),
            tertiary = ZAmber,
            onTertiary = Color(0xFF1A1613),
            background = ZBackground,
            onBackground = ZTextPrimary,
            surface = ZSurface,
            onSurface = ZTextPrimary,
            surfaceVariant = ZSurfaceVariant,
            onSurfaceVariant = ZTextSecondary,
            outline = ZBorder,
            error = ZRose,
            onError = Color(0xFF1A1613),
        )
    } else {
        lightColorScheme(
            primary = ZIndigo,
            onPrimary = White,
            primaryContainer = ZPurple,
            onPrimaryContainer = White,
            secondary = ZCyan,
            onSecondary = White,
            tertiary = ZAmber,
            onTertiary = White,
            background = ZBackground,
            onBackground = ZTextPrimary,
            surface = ZSurface,
            onSurface = ZTextPrimary,
            surfaceVariant = ZSurfaceVariant,
            onSurfaceVariant = ZTextSecondary,
            outline = ZBorder,
            error = ZRose,
            onError = White,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZTypography,
        content = content,
    )
}
