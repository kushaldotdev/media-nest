package com.example.medianest.ui.theme

import androidx.compose.ui.graphics.Color

object MediaNestColors {
    val Background = Color(0xFF120B0E)
    val NavigationBar = Color(0xFF241417)
    val Card = Color(0xFF2A1A1F)
    val Raised = Color(0xFF382027)
    val Border = Color(0xFF4B2B33)
    val TextPrimary = Color(0xFFFFF7F8)
    val TextSecondary = Color(0xFFC8AEB4)
    val Accent = Color(0xFFFFB1B6)
    val AccentDeep = Color(0xFF8F1D2C)
    val NavigationActive = Color(0xFF682B38)
    val Success = Color(0xFF67D98A)
    val Destructive = Color(0xFFEA4A59)
    val YouTubeRed = Color(0xFFD21F31)
    val ProgressTrack = Color(0xFF54333C)
    val PlayerSurface = Color(0xFF090506)
    val OnAccent = Color(0xFF3A0B12)
    val AudioDownload = Color(0xFFFF9800)
}

object MediaNestSemanticColors {
    val Surface = MediaNestColors.Card
    val ElevatedSurface = MediaNestColors.Raised
    val PrimaryAction = MediaNestColors.Accent
    val Selected = MediaNestColors.AccentDeep
    val ActiveNavigation = MediaNestColors.NavigationActive
    val Error = MediaNestColors.Destructive
    val Completed = MediaNestColors.Success
}
