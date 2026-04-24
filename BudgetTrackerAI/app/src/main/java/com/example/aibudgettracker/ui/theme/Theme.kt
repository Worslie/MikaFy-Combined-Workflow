package com.example.aibudgettracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Simplified Theme Implementation:
 * All backgrounds match the mode background.
 * All text matches the mode text color.
 * Exceptions: Green/Red accents and custom Box background.
 */

private val DarkColorScheme = darkColorScheme(
    // Unified Backgrounds
    background = DarkModeBackground,
    surface = DarkModeBackground,
    surfaceVariant = DarkModeBackground,
    surfaceContainer = DarkModeBackground,
    surfaceContainerHigh = DarkModeBackground,
    primaryContainer = DarkModeBoxBackground, // Using for the header box
    secondaryContainer = DarkModeBackground,
    
    // Unified Text/Icons
    primary = DarkModeText,
    onPrimary = DarkModeBackground,
    onBackground = DarkModeText,
    onSurface = DarkModeText,
    onSurfaceVariant = DarkModeText,
    onSecondaryContainer = DarkModeText,
    onPrimaryContainer = DarkModeText,
    
    // Mode Exceptions
    secondary = DarkModeGreen,
    onSecondary = DarkModeText,
    error = DarkModeRed,
    onError = DarkModeText,
    
    outline = DarkModeText,
    surfaceTint = Color.Transparent
)

private val LightColorScheme = lightColorScheme(
    // Unified Backgrounds
    background = LightModeBackground,
    surface = LightModeBackground,
    surfaceVariant = LightModeBackground,
    surfaceContainer = LightModeBackground,
    surfaceContainerHigh = LightModeBackground,
    primaryContainer = LightModeBoxBackground, // Using for the header box
    secondaryContainer = LightModeBackground,
    
    // Unified Text/Icons
    primary = LightModeText,
    onPrimary = LightModeBackground,
    onBackground = LightModeText,
    onSurface = LightModeText,
    onSurfaceVariant = LightModeText,
    onSecondaryContainer = LightModeText,
    onPrimaryContainer = LightModeText,
    
    // Mode Exceptions
    secondary = LightModeGreen,
    onSecondary = LightModeText,
    error = LightModeRed,
    onError = LightModeBackground,
    
    outline = LightModeText,
    surfaceTint = Color.Transparent
)

private val BusinessLightColorScheme = lightColorScheme(
    // Use LightModeBackground generally, but specifically update primary/text
    background = LightModeBackground,
    surface = LightModeBackground,
    surfaceVariant = LightModeBackground,
    surfaceContainer = LightModeBackground,
    surfaceContainerHigh = LightModeBackground,
    primaryContainer = LightModeBoxBackground,
    secondaryContainer = LightModeBackground,

    // Light theme business mode text color
    primary = BusinessModeLightText,
    onPrimary = LightModeBackground,
    onBackground = BusinessModeLightText,
    onSurface = BusinessModeLightText,
    onSurfaceVariant = BusinessModeLightText,
    onSecondaryContainer = BusinessModeLightText,
    onPrimaryContainer = BusinessModeLightText,

    secondary = LightModeGreen,
    onSecondary = LightModeText,
    error = LightModeRed,
    onError = LightModeBackground,

    outline = BusinessModeLightText,
    surfaceTint = Color.Transparent
)

private val BusinessDarkColorScheme = darkColorScheme(
    // Use specialized business dark colors
    background = BusinessModeDarkBackground,
    surface = BusinessModeDarkBackground,
    surfaceVariant = BusinessModeDarkBackground,
    surfaceContainer = BusinessModeDarkBackground,
    surfaceContainerHigh = BusinessModeDarkBackground,
    primaryContainer = BusinessModeDarkBoxBackground,
    secondaryContainer = BusinessModeDarkBackground,

    // Dark mode business view text color (stays white or light as per existing DarkModeText or specific value)
    primary = DarkModeText,
    onPrimary = BusinessModeDarkBackground,
    onBackground = DarkModeText,
    onSurface = DarkModeText,
    onSurfaceVariant = DarkModeText,
    onSecondaryContainer = DarkModeText,
    onPrimaryContainer = DarkModeText,

    secondary = DarkModeGreen,
    onSecondary = DarkModeText,
    error = DarkModeRed,
    onError = DarkModeText,

    outline = DarkModeText,
    surfaceTint = Color.Transparent
)

@Composable
fun AIBudgetTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isBusiness: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isBusiness -> if (darkTheme) BusinessDarkColorScheme else BusinessLightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
