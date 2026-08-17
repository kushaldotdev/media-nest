package com.example.medianest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

@Composable
fun MediaNestTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalMediaNestColors provides MediaNestColors
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = MediaNestTypography,
            shapes = MediaNestShapes.shapes,
            content = content
        )
    }
}
