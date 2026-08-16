package com.example.medianest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MediaNestColors.Accent,
    onPrimary = MediaNestColors.OnAccent,
    primaryContainer = MediaNestColors.AccentDeep,
    onPrimaryContainer = MediaNestColors.TextPrimary,
    inversePrimary = MediaNestColors.AccentDeep,
    secondary = MediaNestColors.TextSecondary,
    onSecondary = MediaNestColors.Background,
    secondaryContainer = MediaNestColors.NavigationActive,
    onSecondaryContainer = MediaNestColors.TextPrimary,
    tertiary = MediaNestColors.YouTubeRed,
    onTertiary = MediaNestColors.TextPrimary,
    tertiaryContainer = MediaNestColors.Raised,
    onTertiaryContainer = MediaNestColors.TextPrimary,
    background = MediaNestColors.Background,
    onBackground = MediaNestColors.TextPrimary,
    surface = MediaNestColors.Card,
    onSurface = MediaNestColors.TextPrimary,
    surfaceVariant = MediaNestColors.Raised,
    onSurfaceVariant = MediaNestColors.TextSecondary,
    surfaceTint = MediaNestColors.Accent,
    inverseSurface = MediaNestColors.TextPrimary,
    inverseOnSurface = MediaNestColors.Background,
    error = MediaNestColors.Destructive,
    onError = MediaNestColors.TextPrimary,
    errorContainer = Color(0xFF5C141C),
    onErrorContainer = MediaNestColors.TextPrimary,
    outline = MediaNestColors.Border,
    outlineVariant = MediaNestColors.Border,
    scrim = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    primary = MediaNestColors.AccentDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9DD),
    onPrimaryContainer = Color(0xFF3A0B12),
    inversePrimary = MediaNestColors.Accent,
    secondary = Color(0xFF682B38),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9DD),
    onSecondaryContainer = Color(0xFF241417),
    tertiary = MediaNestColors.YouTubeRed,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD8),
    onTertiaryContainer = Color(0xFF410006),
    background = MediaNestColors.TextPrimary,
    onBackground = Color(0xFF241417),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF241417),
    surfaceVariant = Color(0xFFF3E5E7),
    onSurfaceVariant = Color(0xFF5C454B),
    surfaceTint = MediaNestColors.AccentDeep,
    inverseSurface = MediaNestColors.Raised,
    inverseOnSurface = MediaNestColors.TextPrimary,
    error = MediaNestColors.Destructive,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF857377),
    outlineVariant = Color(0xFFD7C1C5),
    scrim = Color(0xFF000000)
)

@Composable
fun MediaNestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MediaNestTypography,
        shapes = MediaNestShapes.shapes,
        content = content
    )
}
