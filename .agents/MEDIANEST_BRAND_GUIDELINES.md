# MediaNest Brand Guidelines

Version: 1.0  
Theme: Dark red  
Platform target: Android / Kotlin / Jetpack Compose

## Design direction

MediaNest is an offline-first media library. Visual language should feel compact, warm, focused, and media-rich. Keep screens dense without becoming crowded. Prefer clear metadata, rounded media cards, restrained chrome, and fast access to playback and downloads.

Use dark red and warm white as the primary visual identity. Keep thumbnail imagery colorful, but keep application chrome restrained.

## Single source of truth for colors

Define the complete palette once in the Android theme layer. Every screen, component, player control, download state, tab, and navigation item must reference these tokens. Do not add raw hex values inside composables, view models, XML layouts, or feature modules.

Recommended structure:

```text
ui/
  theme/
    Color.kt
    Type.kt
    Shape.kt
    Theme.kt
```

Put every replaceable color in `ui/theme/Color.kt`. Change the palette there to recolor the entire app.

```kotlin
// ui/theme/Color.kt
package com.medianest.ui.theme

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

    // Supporting colors. Use only for explicit states or media imagery.
    val Destructive = Color(0xFFEA4A59)
    val YouTubeRed = Color(0xFFD21F31)
    val ProgressTrack = Color(0xFF54333C)
    val PlayerSurface = Color(0xFF090506)
    val OnAccent = Color(0xFF3A0B12)
}
```

Use semantic aliases when a feature needs meaning-specific naming:

```kotlin
object MediaNestSemanticColors {
    val Surface = MediaNestColors.Card
    val ElevatedSurface = MediaNestColors.Raised
    val PrimaryAction = MediaNestColors.Accent
    val Selected = MediaNestColors.AccentDeep
    val ActiveNavigation = MediaNestColors.NavigationActive
    val Error = MediaNestColors.Destructive
    val Completed = MediaNestColors.Success
}
```

When a future color refresh is needed, modify only `MediaNestColors` and, if necessary, the semantic aliases. Do not search-and-replace hex values across the codebase.

## Core palette

| Token | Hex | Usage |
|---|---:|---|
| `Background` | `#120B0E` | Main app background |
| `NavigationBar` | `#241417` | Bottom navigation and persistent chrome |
| `Card` | `#2A1A1F` | Media cards, rows, stream cards, settings rows |
| `Raised` | `#382027` | Descriptions and emphasized panels |
| `Border` | `#4B2B33` | Outlines and dividers |
| `TextPrimary` | `#FFF7F8` | Headings, titles, primary values |
| `TextSecondary` | `#C8AEB4` | Metadata, timestamps, supporting copy |
| `Accent` | `#FFB1B6` | Primary actions, progress, active controls |
| `AccentDeep` | `#8F1D2C` | Selected tabs and strong action surfaces |
| `NavigationActive` | `#682B38` | Selected bottom-navigation item |
| `Success` | `#67D98A` | Completed downloads and successful states |

## Supporting palette

These colors are implementation details, not primary brand colors:

| Token | Hex | Usage |
|---|---:|---|
| `Destructive` | `#EA4A59` | Delete, cancel, remove |
| `YouTubeRed` | `#D21F31` | YouTube-specific action |
| `ProgressTrack` | `#54333C` | Player and download progress track |
| `PlayerSurface` | `#090506` | Video player surface |
| `OnAccent` | `#3A0B12` | Text/icons on soft pink accent |

Thumbnail gradients may use additional colors because thumbnails represent media, not the MediaNest interface. Do not use those thumbnail colors for buttons or navigation.

## Color rules

- Use `Background` for the screen canvas.
- Use `Card` for repeated content surfaces.
- Use `Raised` only for elevated descriptions or priority panels.
- Use `Accent` for one primary action per screen.
- Use `AccentDeep` for selected tabs and high-priority red actions.
- Use `NavigationActive` for active bottom navigation.
- Use `TextPrimary` for essential information.
- Use `TextSecondary` for non-essential metadata.
- Use `Success` only for completed or successful states.
- Use `Destructive` only for destructive actions.
- Keep controls free of decorative gradients.
- Pair color with text or icon shape; never rely on color alone.
- Keep contrast readable against every actual surface.

## Typography

Primary font: `Inter`.  
Android fallback: `Roboto`.  
System fallback: `system-ui, sans-serif`.

Recommended Compose setup:

```kotlin
val MediaNestTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)
```

Typography scale:

- Screen title: `26–31sp`
- Section title: `18sp`
- Card title: `13–15sp`
- Metadata: `10–12sp`
- Minimum readable text: `10sp`
- Weights: `400`, `500`, `600`, `700`

## Compact layout rules

- Screen horizontal padding: `14–16dp`
- Card padding: `8–10dp`
- Section spacing: `14–16dp`
- Small control gap: `6–8dp`
- Card radius: `12–14dp`
- Hero panel radius: `14–16dp`
- Bottom navigation height: approximately `72dp`
- Use `16:9` video thumbnails.
- Keep list rows short and metadata close to titles.
- Avoid large empty areas and oversized hero treatments.
- Use horizontal scrolling only for compact tabs or filters.

## Component rules

### Buttons

Primary button:

- Background: `Accent`
- Content: `OnAccent`
- Radius: `18–22dp`
- One high-emphasis primary action per group

Secondary button:

- Transparent background
- Text: `Accent`
- Border: `Border`
- Radius: `18–22dp`

Destructive button:

- Text or icon: `Destructive`
- Use only for delete, cancel, remove, or clear actions

### Tabs

- Inactive text: `TextSecondary`
- Active background: `AccentDeep`
- Active text: `TextPrimary`
- Compact horizontal padding: `8–10dp`
- Fully rounded selected shape

### Media cards

- Background: `Card`
- Border: `Border`
- Radius: `12–14dp`
- Padding: `8–10dp`
- Display title, channel, duration, resolution, and download/watch state.
- Display video thumbnail for videos.
- Display the original video thumbnail for audio-only items.
- Show `VIDEO`, `AUDIO`, resolution, duration, or state as compact overlays.

### Player

- Video surface: `PlayerSurface`
- Progress track: `ProgressTrack`
- Progress fill: `Accent`
- Play/pause control: `Accent`
- Keep rewind, forward, previous, and next controls visible.
- Show autoplay whenever a playlist, folder, Favorites list, or queue is active.
- Previous/next must follow the active list context.

### Downloads

Each download card should support:

- Active progress
- Paused state
- Resume
- Restart
- Cancel
- Delete
- Play when complete
- Extract audio when a video file is complete

Show thumbnail, media type, resolution/container, downloaded size, total size, speed, elapsed time, remaining time, and progress.

### Home / extraction

Home must keep the main YouTube URL entry point prominent. It supports:

- Video URLs
- Audio extraction
- Playlist URLs
- Channel URLs
- Extraction status
- Link history management

Hide URL input and link history inside opened playlists and channels. Keep them on Home only.

## Navigation model

Primary bottom navigation:

1. Home
2. Collections
3. Downloads
4. Settings

Secondary screens:

- Collections sections: All, Folders, Playlists, Channels, Favorites, History, Watched
- Collections also contains continue-watching and recently-added media
- Playlist/folder detail
- Channel detail
- Video details
- Stream selection
- Player
- Download detail state

## Branding voice

Use short, direct labels:

- `Extract`
- `Play all`
- `Autoplay next`
- `Download`
- `Set watched`
- `Resume`
- `Restart`
- `Extract audio`
- `View all`

Avoid long explanatory labels inside dense list rows. Put explanations in secondary text or supporting panels.

## Implementation checklist

- Keep all palette values in `ui/theme/Color.kt`.
- Keep all typography values in `ui/theme/Type.kt`.
- Keep shared shapes in `ui/theme/Shape.kt`.
- Reference semantic color aliases from feature composables.
- Do not add raw color literals in feature UI files.
- Run a project-wide search for `Color(0x` before merging a new screen.
- Verify dark red colors against both card and background surfaces.
- Verify active, inactive, success, paused, error, and destructive states.
- Verify compact layout at narrow Android widths.
