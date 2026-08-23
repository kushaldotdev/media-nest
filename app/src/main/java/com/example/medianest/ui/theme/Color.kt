package com.example.medianest.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object MediaNestColors {
    // -------------------------------------------------------------------------
    // Core Brand Palette (Strictly Dark)
    // -------------------------------------------------------------------------
    val Background = Color(0xFF120B0E)       // #120B0E — Main canvas / screen background
    val NavigationBar = Color(0xFF241417)    // #241417 — Bottom nav & persistent chrome
    val Card = Color(0xFF2A1A1F)             // #2A1A1F — Media cards, list rows, stream cards
    val Raised = Color(0xFF382027)           // #382027 — Hero panels, dialogs, elevated chips
    val ThumbnailPlaceholder = Color(0xFF4A3A40) // #4A3A40 — Muted neutral placeholder for loading/unloaded thumbnails
    val Border = Color(0xFF4B2B33)           // #4B2B33 — Dividers and card borders
    val TextPrimary = Color(0xFFFFF7F8)      // #FFF7F8 — Titles and primary text
    val TextSecondary = Color(0xFFC8AEB4)    // #C8AEB4 — Metadata, timestamps, supporting copy
    val Accent = Color(0xFFFFB1B6)           // #FFB1B6 — Primary actions & active controls
    val AccentDeep = Color(0xFF8F1D2C)       // #8F1D2C — Selected tab pills & high-emphasis red
    val NavigationActive = Color(0xFF682B38) // #682B38 — Active bottom navigation pill indicator
    val Success = Color(0xFF67D98A)          // #67D98A — Completed states & checkmarks

    // -------------------------------------------------------------------------
    // Supporting & Functional Palette
    // -------------------------------------------------------------------------
    val Destructive = Color(0xFFEA4A59)      // #EA4A59 — Delete, cancel, remove
    val YouTubeRed = Color(0xFFD21F31)       // #D21F31 — YouTube badges & player watched progress
    val ProgressTrack = Color(0xFF54333C)    // #54333C — Progress bar & seekbar track background
    val ProgressVideo = Color(0xFFFFB1B6)    // Video download progress fill
    val ProgressAudio = Color(0xFFFF9800)    // Audio extraction progress fill
    val ProgressMerge = Color(0xFF67D98A)    // Muxing/merging progress fill
    val PlayerSurface = Color(0xFF090506)    // #090506 — Video player background
    val OnAccent = Color(0xFF3A0B12)         // Text/icons rendered on soft pink Accent buttons
    val AudioDownload = ProgressAudio        // Backward compatibility alias

    // -------------------------------------------------------------------------
    // Glassmorphism & Overlays
    // -------------------------------------------------------------------------
    val Glass = Color(0x9E2A1A1F)          // rgba(42, 26, 31, 0.62)
    val GlassStrong = Color(0xB8382027)    // rgba(56, 32, 39, 0.72)
    val GlassBorder = Color(0x24FFB7BF)    // rgba(255, 183, 191, 0.14)
    val Scrim = Color(0x9E090506)          // rgba(9, 5, 6, 0.62)
    val Overlay = Color(0x80090506)        // rgba(9, 5, 6, 0.50)

    // -------------------------------------------------------------------------
    // Note-Box Callout Variants (§5.5 MnNoteBox)
    // -------------------------------------------------------------------------
    val NoteBoxBgWarning = Color(0x14EAB308)     // 8% amber background
    val NoteBoxBgSuccess = Color(0x1422C55E)      // 8% green background
    val NoteBoxBgStandard = Color(0x08FFFFFF)     // 3% white overlay background
    val NoteBoxBorderWarning = Color(0x40EAB308)  // 25% amber border
    val NoteBoxBorderSuccess = Color(0x4022C55E)  // 25% green border
    val NoteBoxTextWarning = Color(0xFFEAB308)    // solid amber header
    val NoteBoxTextSuccess = Color(0xFF22C55E)    // solid green header
}

object MediaNestSemanticColors {
    val Surface = MediaNestColors.Card
    val ElevatedSurface = MediaNestColors.Raised
    val PrimaryAction = MediaNestColors.Accent
    val Selected = MediaNestColors.AccentDeep
    val ActiveNavigation = MediaNestColors.NavigationActive
    val Error = MediaNestColors.Destructive
    val Completed = MediaNestColors.Success
    val Track = MediaNestColors.ProgressTrack
}

val LocalMediaNestColors = staticCompositionLocalOf { MediaNestColors }
