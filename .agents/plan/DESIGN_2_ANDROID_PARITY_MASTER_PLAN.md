# MediaNest — Design-2 to Native Android Master Parity Plan

> **Objective:** Achieve 100% visual, tactile, architectural, and functional parity between the `design-2` prototype (`design-2/`) and the native Android Jetpack Compose application (`app/src/main/`).
> **Brand Identity:** Strictly dark-theme media manager (Canvas: `#120B0E`, Cards: `#2A1A1F`, Raised Panels: `#382027`, Deep Red: `#8F1D2C`, Pink Accent: `#FFB1B6`, YouTube Red: `#D21F31`).

---

## Table of Contents

1. [Root Cause Analysis & Fidelity Gaps](#1-root-cause-analysis--fidelity-gaps)
2. [Data & State Synchronization Bug Fixes](#2-data--state-synchronization-bug-fixes)
3. [Single-Source-of-Truth Theme & Palette Architecture](#3-single-source-of-truth-theme--palette-architecture)
4. [Vector Assets Pipeline (67 XML Drawables)](#4-vector-assets-pipeline-67-xml-drawables)
5. [Reusable Compose Component Library](#5-reusable-compose-component-library)
6. [In-App Notifications Hub Subsystem](#6-in-app-notifications-hub-subsystem)
7. [Screen-by-Screen Implementation Specifications](#7-screen-by-screen-implementation-specifications)
   - [7.1 App Shell, Navigation & Mini-Player (`MainScreen.kt`)](#71-app-shell-navigation--mini-player-mainscreenkt)
   - [7.2 Home Screen (`HomeScreen.kt`)](#72-home-screen-homescreenkt)
   - [7.3 Collections Hub (`LibraryScreen.kt` & `SubscriptionsScreen.kt`)](#73-collections-hub-libraryscreenkt--subscriptionsscreenkt)
   - [7.4 Downloads Screen (`DownloadsScreen.kt`)](#74-downloads-screen-downloadsscreenkt)
   - [7.5 Player Screen (`PlayerScreen.kt`)](#75-player-screen-playerscreenkt)
   - [7.6 Statistics Screen (`StatisticsScreen.kt`)](#76-statistics-screen-statisticsscreenkt)
   - [7.7 Settings Screen (`SettingsScreen.kt`)](#77-settings-screen-settingsscreenkt)
   - [7.8 Video Detail Screen (`VideoDetailScreen.kt`)](#78-video-detail-screen-videodetailscreenkt)
   - [7.9 Notifications Hub Screen (`NotificationsScreen.kt`)](#79-notifications-hub-screen-notificationsscreenkt)
8. [Phased Execution Roadmap & Verification Gates](#8-phased-execution-roadmap--verification-gates)

---

## 1. Root Cause Analysis & Fidelity Gaps

### 1.1 Why the Android App Appears Incomplete and Shows a White Background

1. **System Light Theme Fallback Bug in `Theme.kt`:**
   - `MediaNestTheme` currently uses `darkTheme: Boolean = isSystemInDarkTheme()`.
   - When an Android device or emulator has the system theme set to Light Mode, `LightColorScheme` is activated, forcing `surface = #FFFFFF` and `background = #FFF7F8`.
   - `app/src/main/res/values/themes.xml` specifies `parent="android:Theme.Material.Light.NoActionBar"`, creating bright white cold starts.
   - **Resolution:** Enforce the dark brand theme universally across all system modes, remove the light color scheme fallback, and update `themes.xml` window background to `#120B0E`.

2. **Missing Vector Assets (67 Prototype SVGs Missing in Android):**
   - Only 3 drawables exist in `app/src/main/res/drawable/`.
   - All 67 custom SVG icons from `design-2/index.html` were omitted during early development, resulting in generic filled Material icons or missing visual affordances.

3. **Inconsistent Color Token References:**
   - Composables mix `GlassCard` (`MediaNestColors.Card`), `MaterialTheme.colorScheme.surfaceVariant`, and hardcoded hex values, breaking contrast and uniform recoloring.

4. **Missing UI Structures & Cluttered Action Rows:**
   - Android video cards placed 4 separate inline buttons (`Play`, `Add to Folder`, `Delete`, `Share`) directly on each card instead of the clean single 3-dot action menu (`i-more`) bottom sheet used in `design-2`.
   - Hero URL extraction panel, compact 72dp navbar with active top-bar indicator, 6 sub-tab icon pills, continue-watching carousel styling, unified 3-dot bottom sheets, structured `.mn-note-box` callouts, and settings preference toggles (`autoMarkWatched`, `backgroundPlayback`) need full native implementation.

### 1.2 Already-Implemented Prototype Features (No Android Work Required)

These `design-2` README / HANDOVER schema items are **already present** in the Android data layer and must **not** be re-migrated:

- `VideoEntity.mediaType: String = "VIDEO"` — already migrated into Room.
- `LinkHistoryEntity.linkType: String = "VIDEO"` — already present.
- `SubscriptionEntity.audioOnly: Boolean = false` — already present.

Any plan step referencing these as missing is out of date; only the **notifications** subsystem (section 6) is a genuinely new schema addition.

### 1.3 Cross-Cutting Universal Requirements (apply to EVERY list & grid)

These two requirements from `design-2/HANDOVER.md` §10 are **global**, not per-screen — they apply to every list and grid in the app. They are easy to miss because they are not visual, so they are called out here explicitly:

1. **Universal 10-item lazy loading:** EVERY list and grid — Home results, Link History, Collections tabs (History, Watched, Folders, Favorites, Playlists, Channels), Downloads queue, Continue Watching horizontal row, Notifications, and Video Detail stream ladder — MUST use dynamic lazy loading (`LazyColumn` / `LazyVerticalGrid` / `LazyRow` / Paging 3) with an **initial batch of 10 items** and incremental batches on scroll (never rendering more than ~10–20 items at once).
2. **End-of-list indicator:** EVERY dynamically loaded list/grid MUST append a clear end-of-list marker (`• You have reached the end of the list •`) once all items are fetched. Reuse the existing `EndOfListIndicator` composable everywhere; the current per-screen implementations only cover a subset of lists.

### 1.4 Native-Only Capabilities (DO NOT regress — preserve as-is)

These Android capabilities exist **only** in the native app and must be **preserved** during UI migration (design-2 simulates them but has no real equivalent):

- **Player gestures & surface (verify before migrating):** design-2's README claims the native app has vertical-swipe brightness/volume, double-tap seek, and pinch-to-zoom. **Actual current state:** `PlayerScreen.kt` implements **double-tap seek** (left/right halves → ±10s) and tap-to-toggle-controls, but there is **no vertical-swipe brightness/volume** and **no pinch-to-zoom** in the current source. Preserve the existing double-tap seek; the brightness/volume swipe and pinch-to-zoom must be added if parity requires them — they are **not** currently present to preserve.
- **Storage Access Framework (SAF):** document picker, URI permission persistence, SD-card access (`DownloadPathResolver`).
- **WorkManager background jobs:** `BulkDownloadPreparationWorker`, `SubscriptionWorker`, `SyncWorker`, `AutoBackupWorker`, `UpdateCheckWorker`.
- **Extraction pipeline:** the app uses the **NewPipeExtractor** library (`org.schabi.newpipe.extractor`, dependency `com.github.teamnewpipe.newpipeextractor` in `app/build.gradle.kts`) via `extraction/YouTubeExtractor.kt` + `DownloaderProvider.kt` — **not** a `yt-dlp` Python binary. Preserve the NewPipe extractor and `extraction/DownloaderProvider`.
- **Storage migration/relocation** between internal storage and SAF paths.
- **System notifications** (`DownloadService` foreground progress, update alerts) — these are separate from the new in-app `NotificationsScreen` (section 6).
- **Backup/Export/Restore** (`BackupRepository`, `RestoreRepository`, library repair) and **VPS REST Sync** (`SyncManager`, `SyncRepository`).

---

## 2. Data & State Synchronization Bug Fixes

### 2.1 View Mode Desynchronization Bug

- **Defect:** `LibraryViewModel.kt` reads default view mode from `DevicePreferences.defaultViewMode`, while `SettingsScreen.kt` writes default view mode to `CollectionsPreferences.viewMode`.
- **Impact:** Changing the Collections View Mode (Grid vs List) in Settings has zero effect on the actual Collections/Library screen.
- **Resolution:**
  - Standardize on `CollectionsPreferences` as the single source of truth for Collections view mode.
  - Update `LibraryViewModel.kt` to inject and observe `CollectionsPreferences.viewModeFlow`.
  - Ensure that toggling view mode directly in the Collections header updates `CollectionsPreferences` and is reflected in Settings.

### 2.2 Move to Folder Subfolder Query Bug

- **Defect:** In `SubscriptionsScreen.kt` / `LibraryScreen.kt`, the Move to Folder dialog queries root folders only (`WHERE parentId IS NULL`), and Home/link-history move dialogs use the flat `getAllFolders()` list. There is no `getFoldersSync()` anywhere in the codebase.
- **Impact:** Subfolders nested inside parent folders cannot be selected (Library dialog) or are shown flat without hierarchy/indentation (Home dialog), preventing proper organization into nested folders.
- **Resolution:**
  - Create `getAllFoldersTreeFlow()` in `FolderDao` and `FolderRepository` that returns the complete hierarchical tree of folders (recursive parent→child expansion).
  - Render nested folders with visual indent prefixes (e.g., `📁 Music / 📁 Rock / 📁 90s`) in `MoveToFolderSheet`, and use the same tree source in every move-to-folder dialog (Library, Home link history, inline subscription cards).

### 2.3 Collections Root Folders Grid View Support

- **Defect:** Root folders in `SubscriptionsScreen.kt` / `LibraryScreen.kt` are currently hardcoded to render only as full-width list items regardless of whether the Grid or List view mode is selected.
- **Impact:** Grid mode in Collections displays channels, playlists, and videos as grid cards, but folders remain stretched list rows.
- **Resolution:**
  - Implement `FolderGridCard` composable matching `folderGridCardHtml` from `design-2/js/screens-collections.js`.
  - Render 2-column grid of folder cards with folder icon, title, item count badge, and 3-dot action menu when `viewMode == ViewMode.GRID`.

---

## 3. Single-Source-of-Truth Theme & Palette Architecture

### 3.1 Complete Token Palette in `Color.kt`

Define all 23 tokens once in `app/src/main/java/com/example/medianest/ui/theme/Color.kt`. Changing any token here will immediately recolor every screen, dialog, sheet, and control:

```kotlin
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

    // -------------------------------------------------------------------------
    // Glassmorphism & Overlays
    // -------------------------------------------------------------------------
    val Glass = Color(0x9E2A1A1F)          // rgba(42, 26, 31, 0.62)
    val GlassStrong = Color(0xB8382027)    // rgba(56, 32, 39, 0.72)
    val GlassBorder = Color(0x24FFB7BF)    // rgba(255, 183, 191, 0.14)
    val Scrim = Color(0x9E090506)          // rgba(9, 5, 6, 0.62)
    val Overlay = Color(0x80090506)        // rgba(9, 5, 6, 0.50)
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
```

### 3.2 Universal Dark Theme in `Theme.kt` and `themes.xml`

```kotlin
// app/src/main/java/com/example/medianest/ui/theme/Theme.kt
package com.example.medianest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = MediaNestColors.Accent,
    onPrimary = MediaNestColors.OnAccent,
    primaryContainer = MediaNestColors.AccentDeep,
    onPrimaryContainer = MediaNestColors.TextPrimary,
    surface = MediaNestColors.Card,
    onSurface = MediaNestColors.TextPrimary,
    surfaceVariant = MediaNestColors.Raised,
    onSurfaceVariant = MediaNestColors.TextSecondary,
    background = MediaNestColors.Background,
    onBackground = MediaNestColors.TextPrimary,
    outline = MediaNestColors.Border,
    error = MediaNestColors.Destructive,
    onError = MediaNestColors.TextPrimary
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
            typography = Typography,
            content = content
        )
    }
}
```

```xml
<!-- app/src/main/res/values/themes.xml -->
<resources>
    <style name="Theme.MediaNest" parent="Theme.Material3.Dark.NoActionBar">
        <item name="android:windowBackground">#120B0E</item>
        <item name="android:statusBarColor">#120B0E</item>
        <item name="android:navigationBarColor">#241417</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowLightNavigationBar">false</item>
    </style>
</resources>
```

---

## 4. Vector Assets Pipeline (67 XML Drawables)

> **Accuracy note (audit-corrected):** The 67 prototype symbols below are the **verbatim** `id="i-*"` values extracted from `design-2/index.html` (verified by grep). The prototype exposes **no** `chevron-left`, `more-vertical`, `folder-plus`, `heart-filled`, `sort-asc/desc`, `cloud-off`, `bell-off`, `external`, `paste`, `alert-circle`, `volume`, `volume-x`, `minimize`, `quality`, `timer`, `equalizer`, `tag`, or `radio` symbols. Where those UI affordances exist in `design-2`, they are built from the real symbols listed below (e.g. the 3-dot menu uses `i-more`; sort direction uses `i-arrow-up`/`i-arrow-down`; the active-favorite state is tinted `i-heart`). Do **not** author fabricated drawables.

All 67 SVG icons from `design-2/index.html` are mapped to Android Vector Drawables in `app/src/main/res/drawable/`:

| # | Prototype Symbol | Android Vector Resource | Purpose / Screen Usage |
| --- | --- | --- | --- |
| 1 | `i-home` | `ic_mn_home.xml` | Bottom Navigation: Home tab |
| 2 | `i-library` | `ic_mn_library.xml` | Bottom Navigation: Collections tab |
| 3 | `i-download` | `ic_mn_download.xml` | Bottom Navigation: Downloads tab / Action buttons |
| 4 | `i-settings` | `ic_mn_settings.xml` | Bottom Navigation: Settings tab |
| 5 | `i-search` | `ic_mn_search.xml` | Search input fields across all screens |
| 6 | `i-close` | `ic_mn_close.xml` | Clear search, dismiss sheets, close modals |
| 7 | `i-chevron-right` | `ic_mn_chevron_right.xml` | Settings navigation rows & list disclosure |
| 8 | `i-chevron-down` | `ic_mn_chevron_down.xml` | Accordions, dropdown triggers, collapsed logs |
| 9 | `i-chevron-up` | `ic_mn_chevron_up.xml` | Expanded logs and sheet dismissals |
| 10 | `i-more` | `ic_mn_more.xml` | 3-dot card action menus & header options (prototype has no `more-vertical`) |
| 11 | `i-play` | `ic_mn_play.xml` | Play video, resume download, hero play |
| 12 | `i-pause` | `ic_mn_pause.xml` | Pause playback, pause active download |
| 13 | `i-next` | `ic_mn_next.xml` | Skip to next track in player / mini player |
| 14 | `i-prev` | `ic_mn_prev.xml` | Skip to previous track in player |
| 15 | `i-check` | `ic_mn_check.xml` | Success indicators, selection checkmarks |
| 16 | `i-check-circle` | `ic_mn_check_circle.xml` | Completed downloads, verified updates |
| 17 | `i-trash` | `ic_mn_trash.xml` | Delete file, remove video, clear history |
| 18 | `i-edit` | `ic_mn_edit.xml` | Rename folder, edit metadata |
| 19 | `i-folder` | `ic_mn_folder.xml` | Folder tab, folder rows, move to folder |
| 20 | `i-folder-add` | `ic_mn_folder_add.xml` | New folder button and creation dialog (prototype has no `folder-plus`) |
| 21 | `i-history` | `ic_mn_history.xml` | History sub-tab, auto-sync intervals |
| 22 | `i-watched` | `ic_mn_watched.xml` | Watched sub-tab, auto-mark watched toggle |
| 23 | `i-heart` | `ic_mn_heart.xml` | Favorites sub-tab, favorite toggle (active state = accent-tinted fill) |
| 24 | `i-playlist` | `ic_mn_playlist.xml` | Playlists sub-tab, playlist cards |
| 25 | `i-channel` | `ic_mn_channel.xml` | Channels sub-tab, channel cards |
| 26 | `i-grid` | `ic_mn_grid.xml` | View mode toggle: 2-column Grid |
| 27 | `i-list` | `ic_mn_list.xml` | View mode toggle: Detailed List |
| 28 | `i-sort` | `ic_mn_sort.xml` | Sort pill trigger; sort order selector |
| 29 | `i-arrow-up` | `ic_mn_arrow_up.xml` | Sort order: Ascending |
| 30 | `i-arrow-down` | `ic_mn_arrow_down.xml` | Sort order: Descending |
| 31 | `i-cloud` | `ic_mn_cloud.xml` | VPS Cloud Sync status |
| 32 | `i-cloud-up` | `ic_mn_cloud_up.xml` | Push to VPS, Sync Now button |
| 33 | `i-cloud-down` | `ic_mn_cloud_down.xml` | Pull from VPS, sync pull records |
| 34 | `i-bell` | `ic_mn_bell.xml` | Notifications bell header icon |
| 35 | `i-refresh` | `ic_mn_refresh.xml` | Check for updates, refresh feed |
| 36 | `i-share` | `ic_mn_share.xml` | Share video URL / media file |
| 37 | `i-copy` | `ic_mn_copy.xml` | Copy URL, copy device ID |
| 38 | `i-extract` | `ic_mn_extract.xml` | Extract media link, export backup |
| 39 | `i-warning` | `ic_mn_warning.xml` | Warning badges, missing file alerts |
| 40 | `i-info` | `ic_mn_info.xml` | Info callouts, about screen |
| 41 | `i-sliders` | `ic_mn_sliders.xml` | Preferences header, max concurrent downloads |
| 42 | `i-video` | `ic_mn_video.xml` | Video media type badge, default quality |
| 43 | `i-music` | `ic_mn_music.xml` | Audio media type badge, background audio |
| 44 | `i-fullscreen` | `ic_mn_fullscreen.xml` | Enter fullscreen video player |
| 45 | `i-speed` | `ic_mn_speed.xml` | Playback speed selector |
| 46 | `i-youtube` | `ic_mn_youtube.xml` | YouTube source badge, Shorts toggle |
| 47 | `i-back` | `ic_mn_back.xml` | Back navigation button |
| 48 | `i-checkbox` | `ic_mn_checkbox.xml` | Multi-select box |
| 49 | `i-chart` | `ic_mn_chart.xml` | App statistics navigation |
| 50 | `i-device` | `ic_mn_device.xml` | Hardware ID & device registration |
| 51 | `i-repair` | `ic_mn_repair.xml` | Database repair tool |
| 52 | `i-grip` | `ic_mn_grip.xml` | Queue drag handle |
| 53 | `i-file` | `ic_mn_file.xml` | Backup file, import archive |
| 54 | `i-download-done` | `ic_mn_download_done.xml` | Completed download / ready-to-play state |
| 55 | `i-eye` | `ic_mn_eye.xml` | Watch counts, view metrics, visibility |
| 56 | `i-star` | `ic_mn_star.xml` | Rating / featured badge (defined in sprite) |
| 57 | `i-pin` | `ic_mn_pin.xml` | Pin / sticky item affordance |
| 58 | `i-cast` | `ic_mn_cast.xml` | Cast to device affordance |
| 59 | `i-autoplay` | `ic_mn_autoplay.xml` | Autoplay toggle in player |
| 60 | `i-shuffle` | `ic_mn_shuffle.xml` | Shuffle playback in player |
| 61 | `i-move` | `ic_mn_move.xml` | Move to folder action |
| 62 | `i-rewind5` | `ic_mn_rewind5.xml` | Rewind 5 seconds |
| 63 | `i-rewind10` | `ic_mn_rewind10.xml` | Rewind 10 seconds (defined in sprite) |
| 64 | `i-rewind30` | `ic_mn_rewind30.xml` | Rewind 30 seconds |
| 65 | `i-forward5` | `ic_mn_forward5.xml` | Forward 5 seconds |
| 66 | `i-forward10` | `ic_mn_forward10.xml` | Forward 10 seconds (defined in sprite) |
| 67 | `i-forward30` | `ic_mn_forward30.xml` | Forward 30 seconds |

---

## 5. Reusable Compose Component Library

### 5.1 `GlassCard`

- Translucent container (`MediaNestColors.Glass` = `0x9E2A1A1F`).
- 1dp subtle border (`MediaNestColors.GlassBorder` = `0x24FFB7BF`).
- 14dp rounded corners (`RoundedCornerShape(14.dp)`).
- Supports interactive active state (`scale(0.985f)`).
- **Current-state change:** the existing `app/src/main/java/com/example/medianest/ui/components/GlassCard.kt` uses solid `MediaNestColors.Card` + `MediaNestColors.Border` at 12dp radius. This must be migrated to the translucent `Glass`/`GlassBorder` tokens and 14dp radius above.

### 5.2 `MediaNestBottomNav`

- 72dp fixed height with `#241417` background and 1dp top border `#4B2B33`.
- Active item indicator: 40dp × 3dp bar pinned to top of tab item in `#FFB1B6` with `RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)`.
- 4 primary tabs: **Home** (`ic_mn_home`), **Collections** (`ic_mn_library`), **Downloads** (`ic_mn_download`), **Settings** (`ic_mn_settings`).

### 5.3 `MediaNestTopAppBar`

- 60dp sticky header with `#120B0E` background.
- Left: Back circular pill button (`ic_mn_back`) when back navigation is active.
- Center: Title in `headlineLarge` typography (`#FFF7F8`).
- Right Action Host:
  - View mode toggle (`ic_mn_grid` / `ic_mn_list`).
  - App Statistics button (`ic_mn_chart`).
  - Notification Bell (`ic_mn_bell`) with unread count pill badge (`9+`).

### 5.4 `MiniPlayer`

- Positioned above bottom navigation (`bottom: 80.dp`, horizontal padding: `12.dp`).
- 52×52dp thumbnail image with 8dp radius.
- Title and channel metadata in vertical column.
- Controls: Play/Pause button + **Next Track button** (`ic_mn_next`).
- Bottom 3dp accent progress strip (`#FFB1B6`).
- Tap navigates to full player; swipe down/drag dismisses mini player.

### 5.5 `MnNoteBox` (`.mn-note-box`)

- Structured callout card with icon, title header, and bullet points.
- Variants:
  - **Standard:** Background `rgba(255,255,255,0.03)`, Border `#4B2B33`, Icon/Header `#FFB1B6`.
  - **Warning:** Background `rgba(234,179,8,0.08)`, Border `#EAB308`, Icon/Header `#EAB308`.
  - **Success:** Background `rgba(34,197,94,0.08)`, Border `#22C55E`, Icon/Header `#22C55E`.
- **Width Alignment Contract:** Uses `fillMaxWidth()` with 0 horizontal margin inside 16dp padded cards so its edges perfectly match adjacent text fields, buttons, and rows.

### 5.6 `PillTabRow` & `SortPill`

- Horizontal scrollable pill row (`ic_mn_*` icon + label) for sub-tab navigation (History, Watched, Folders, Favorites, Playlists, Channels).
- Sort pill button (`[↓ Date]`) opening a modal sort sheet (Date, Name, Duration, Size, Status; Ascending / Descending).
- **Single-category toggle semantics:** sort menus show **ONE entry per category** (not separate asc/desc rows). Re-selecting the already-active category **toggles its direction** between descending (`↓` `ic_mn_arrow_down`) and ascending (`↑` `ic_mn_arrow_up`). Sort display and menus use **icon + text**.
- **Playlist & Channel duration sort:** Playlists and Channels compute **total duration** and support sorting by `durationSeconds` (longest/shortest), `uploadDate` (newest/oldest), and `title` (A–Z).

### 5.7 `UnifiedVideoCard` & `UnifiedVideoRow`

- Top-left media type badge (`ic_mn_video` or `ic_mn_music`).
- Bottom edge YouTube Red watched progress strip (`#D21F31`).
- Clean title and metadata row.
- **Title tap toggles 2-line clamp:** tapping a card title expands/collapses the clamped 2-line title **in place** (does not navigate). Navigation to Video Detail is via the thumbnail/stage, not the title.
- Single 3-dot action button (`ic_mn_more`) opening `VideoActionBottomSheet`.

### 5.8 `VideoActionBottomSheet`

- Triggered from 3-dot menu on any video card across Home, Collections, and Downloads.
- Header: Video thumbnail, title, channel name, and duration.
- Actions:
  - `Play Video` (`ic_mn_play`)
  - `Add to / Move to Folder` (`ic_mn_folder`)
  - `Download / Quality Options` (`ic_mn_download`)
  - `Toggle Favorite` (`ic_mn_heart`, active state = accent-tinted fill)
  - `Share URL / File` (`ic_mn_share`)
  - `Delete / Remove Video` (`ic_mn_trash`, red destructive style)

---

## 6. In-App Notifications Hub Subsystem

### 6.1 Subsystem Overview

`design-2` contains a dedicated in-app notification center that records download events, subscription releases, sync updates, and system maintenance alerts for up to 365 days. Android currently lacks this internal notification store.

> **Schema migration note:** `app/src/main/java/com/example/medianest/data/` currently ships `AppDatabase` at **version 17**. Adding `app_notifications` (and any new indexes/columns) requires a **version bump to 18 plus a Room `Migration`** (or a destructive fallback only if explicitly accepted). Do not add the entity without the migration — the app will crash on upgrade for existing installs.

### 6.2 Data Layer Architecture

```kotlin
// Room Entity
@Entity(tableName = "app_notifications")
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: NotificationType, // DOWNLOAD, SUBSCRIPTION, SYNC, SYSTEM
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val targetVideoId: String? = null,
    val targetDownloadId: Long? = null
)

// Room DAO
@Dao
interface NotificationDao {
    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<AppNotificationEntity>>

    @Query("SELECT * FROM app_notifications WHERE type = :type ORDER BY timestamp DESC")
    fun getNotificationsByTypeFlow(type: NotificationType): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: AppNotificationEntity)

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM app_notifications")
    suspend fun clearAll()

    @Query("DELETE FROM app_notifications WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)
}
```

### 6.3 UI Integration

- Top app bar bell icon displays live unread count badge (`Flow<Int>`).
- Tapping bell navigates to `NotificationsScreen`.
- Filter chips: `All`, `Downloads`, `Subscriptions`, `System`, `Sync`.
- Actions: `Mark All Read`, `Clear All`.
- Tapping a notification navigates directly to the target video player or downloads screen.

---

## 7. Screen-by-Screen Implementation Specifications

### 7.1 App Shell, Navigation & Mini-Player (`MainScreen.kt`)

1. **Tab Reordering:** Ensure bottom navigation order matches prototype: `Home` → `Collections` → `Downloads` → `Settings`.
2. **Active Tab Indicator:** 40dp × 3dp rounded pink bar pinned to top of active navigation tab.
3. **Mini-Player:**
   - Float 80dp from screen bottom above navbar.
   - GlassStrong translucent background (`0xB8382027`) with border `#4B2B33`.
   - Add `Next Track` button (`ic_mn_next`) alongside Play/Pause.
   - Add bottom 3dp accent progress strip.
4. **Global Header:**
   - App Statistics shortcut (`ic_mn_chart`) and Notifications Bell (`ic_mn_bell`) with unread badge.

### 7.2 Home Screen (`HomeScreen.kt`)

1. **Hero Extraction Panel (`.mn-hero`):**
   - Radial gradient card (`#382027` with deep red glow).
   - Eyebrow: `"Offline-first"`, Title: `"MediaNest"`, Subtitle: `"Download, organize and play your YouTube library — even offline."`.
   - Embedded URL text field with left YouTube icon and embedded `"Extract"` action button.
   - Demo pill chips: `"Demo: Playlist"` and `"Demo: Channel"`.
   - **Accuracy note:** `design-2/js/screens-home.js` uses an **embedded** Extract button (`.mn-field__action`), **not** a floating `ExtendedFloatingActionButton`. The current Android `ExtendedFloatingActionButton "Extract"` is a divergence and must be converted to the embedded field action. The `design-2/README.md` "Floating Extract Button" feature-matrix row is inaccurate on this point.
   - **Duplicate extract control removal:** `HomeScreen.kt` currently renders **two** extract affordances — a floating `ExtendedFloatingActionButton "Extract"` (the `floatingActionButton` scaffold slot) **and** a separate inline `Button("Extract")` beside the URL field. **Both** must be consolidated into the single embedded `.mn-field__action`-style button inside the hero URL field. Remove the `floatingActionButton` slot entirely (delete `ExtendedFloatingActionButton` and its `import`); keep one embedded `"Extract"` action bound to `viewModel.onUrlSubmitted(urlInput.trim())`.
2. **Playlist / Channel Result Cards:**
   - 16:9 banner cover image, avatar thumbnail, formatted video count, description.
   - Action buttons: `"Save to Playlist"` / `"Subscribe"`, `"Download All"`, and `"Show Shorts"` toggle switch.
3. **Continue Watching Section:**
   - Horizontal carousel of 220dp video cards with media type badge, centered play button, and YouTube Red watched progress bar.
4. **Link History Rows:**
   - Type icon badges (`ic_mn_video`, `ic_mn_playlist`, `ic_mn_channel`).
   - Quick clipboard copy, reload button, delete button, and "Clear All" confirmation modal.

### 7.3 Collections Hub (`LibraryScreen.kt` & `SubscriptionsScreen.kt`)

1. **6-Tab Sub-Navigation:**
   - Pill chips with icons: **History** (`ic_mn_history`), **Watched** (`ic_mn_watched`), **Folders** (`ic_mn_folder`), **Favorites** (`ic_mn_heart`), **Playlists** (`ic_mn_playlist`), **Channels** (`ic_mn_channel`).
2. **Scoped Search & Stats Line:**
   - Per-tab search query retention and dynamic placeholder (`"Search history..."`, `"Search folders..."`, etc.).
   - Dynamic summary metadata line (e.g., `<icon:history> 12 videos · 4h 12m watched`).
3. **Sort Pills & Bottom Sheet:**
   - Interactive sort pill on section headers opening Sort Sheet (Date, Name, Duration; Ascending / Descending).
4. **Full Grid vs List Parity Across All Sub-Tabs:**
   - Unify `CollectionsPreferences.viewMode` across `LibraryViewModel` and `SettingsScreen`.
   - Render 2-column Grid cards for Channels, Playlists, Folders, and Videos.
5. **Folder & Subfolder Organization:**
   - Folder creation dialog, Edit/Delete folder sheet, and Move-to-Folder selection sheet supporting nested subfolder trees.

### 7.4 Downloads Screen (`DownloadsScreen.kt`)

1. **Storage & Status Breakdown Card:**
   - Top card displaying total items, total size, status pills (`Downloading`, `Queued`, `Paused`, `Failed`, `Completed`), and disk space footprint.
2. **Thumbnail Status Badges:**
   - Top-right badges on thumbnails for active, paused, queued, failed, and completed downloads.
3. **Multi-Stage Progress Bars:**
   - Segmented progress colors: Video download (`#FFB1B6`), audio extraction (`#FF9800`), muxing/merging (`#67D98A`), error (`#EA4A59`).
4. **Single-Category Toggle Sort:**
   - Sort categories (`Date`, `Progress`, `Size`, `Status`) with instant asc/desc direction flip.

### 7.5 Player Screen (`PlayerScreen.kt`)

1. **YouTube Red Seekbar:**
   - Force `MediaNestColors.YouTubeRed` (`#D21F31`) on active seekbar track and thumb.
2. **Time-Based Watched Threshold (partially implemented):**
   - `PlayerViewModel.checkAndMarkWatched(pos, duration)` **already** marks watched when remaining time is `≤ 1 minute` (`(duration - pos) <= 60000L`).
   - **Remaining work:** gate the auto-mark behavior on the new `autoMarkWatched` preference (see §7.7.3) instead of always applying it.
   - **Explicitly NO percentage clause:** do **not** mark watched at 95% (or any % progress). A percentage rule would prematurely mark very long videos (e.g. a 20-hour video at 95% still has ~1 hour left). Watched is **time-remaining-only**: a video is watched only when the remaining time falls to **≤ 1 minute** (optionally **≤ 2 minutes** max). Keep `(duration - pos) <= 60000L`, or `<= 120000L` if a 2-minute tolerance is preferred.
3. **Transport Controls & Equalizer:**
   - Add Previous (`ic_mn_prev`) and Next (`ic_mn_next`) track buttons in main transport row.
   - Animated 5-bar equalizer overlay on 1:1 artwork for audio-only playback.
4. **Segmented Controls & Queue:**
   - Speed (0.25x–2.0x), Quality selector, and Up-Next Queue sheet with drag-and-drop reorder and Autoplay toggle.

### 7.6 Statistics Screen (`StatisticsScreen.kt`)

1. **Top Overview Cards:**
   - Tracked Videos, Library Ratio (Video vs Audio %), Completion %, Total Plays, Favorites, Subscriptions.
2. **Watch Analytics:**
   - Total Watch Time, Weekly Watch Time, Average Session, Longest Session.
3. **Storage Footprint & Download Health:**
   - Video/Audio storage bar %, disk footprint, success rate %, link extraction metrics.
4. **Ranked Top Content:**
   - Top 5 ranked videos (design-2 sorts by `watchCount` desc and `slice(0, 5)`).
   - **Android parity fix:** `StatisticsScreen.kt` currently initializes `topVideosLimit = 10` and renders `topVideos.take(topVideosLimit)`. Align to **top 5** for parity (or keep the "Show more" batching but cap the initial batch at 5).
   - Breakdowns by Resolution, Channel, and Folder with 10-item batching and `EndOfListIndicator` (Android already implements `channelsLimit`/`foldersLimit` batching + `EndOfListIndicator`; Resolution is a small ~5-bucket list).

### 7.7 Settings Screen (`SettingsScreen.kt`)

> **Verified gap audit (against `SettingsScreen.kt` + `screens-settings.js`):** the Android screen already implements VPS sync fields, Register/Sync buttons, sync-interval dropdown, sync log, download location + SAF picker + migration dialog, default resolution dropdown, Show Shorts switch, Collections view mode chips, App Statistics link, full export/import/restore/repair/cleaner flows, and the update lifecycle. The items below are the **actual** remaining deltas.

1. **VPS Cloud Sync:**
   - Add a **MnNoteBox** overview callout (currently absent — fields render with no explanation header).
   - Add **Device ID with a copy button** (currently Device ID is shown as plain text with no copy affordance).
   - Restructure into the design-2 **5 named groups** with per-group stat-line chips (`VPS Sync & Cloud`, `Downloads & Network`, `Preferences`, `Data Management & Storage`, `About & Updates`) instead of the current loose `Text("VPS Sync")` / `Text("Downloads")` / `Text("Preferences")` / `Text("Data Management")` / `Text("About")` headers.
2. **Downloads & Network:**
   - Add **Max Concurrent Downloads (1–5)** control — it currently exists only on `DownloadsScreen.kt`, **not** in Settings.
   - Add a MnNoteBox overview callout.
   - *(Note: "Download over Wi-Fi only" is omitted per design specification.)*
3. **Content & Display Preferences:**
   - Add **Auto-mark as Watched** switch (time-based: ≤ 1 minute remaining, **no % clause**) — does **not** exist yet; must be backed by a new `autoMarkWatched` DataStore key (see §7.5).
   - Add **Background Audio Playback** switch — does **not** exist yet; must be backed by a new `backgroundPlayback` DataStore key (neither key currently exists in `DownloadPreferences`/`PlaybackPreferences`).
   - Add a MnNoteBox overview callout.
   - *(Show Shorts + Collections view mode already present and wired to `SubscriptionsPreferences` / `CollectionsPreferences`.)*
4. **Data Management & Storage:**
   - Replace inline `Text("Notes:\n• ...")` blocks with structured **MnNoteBox** components (Backup/Restore, Library Repair, Missing Files).
   - Group the currently-scattered cards under one `Data Management & Storage` header (repair + cleaner currently sit as separate top-level cards).
5. **About & Updates:**
   - Use the **real build version** (`BuildConfig`/`packageManager` versionName = `1.0.9`, versionCode `9`) — do **not** hardcode the prototype's mock `v1.0.0 (Build 2408)`.
   - Add the **Notifications Hub** nav row with unread count (currently missing; §7.9 wires it to the new `NotificationsScreen`).
   - Add a MnNoteBox overview callout.

### 7.8 Video Detail Screen (`VideoDetailScreen.kt`)

1. **Media Stage & Header:**
   - 16:9 video thumbnail or audio artwork stage.
   - Title, Media Type badge (`ic_mn_video` / `ic_mn_music`), Resolution pill, and channel row with Subscribe button.
2. **Downloaded Versions List:**
   - Card listing all local file formats on disk with file size, bitrate, Play button, and Delete button.
3. **Watch Position & Metrics:**
   - Last watch position badge (`MM:SS`), personal watch count vs public view count, and description panel.

### 7.9 Notifications Hub Screen (`NotificationsScreen.kt`)

1. **Filter Header:**
   - Category filter pills: `All`, `Downloads`, `Subscriptions`, `System`, `Sync`.
2. **Notification Cards:**
   - Icon badge matching event type (`ic_mn_download`, `ic_mn_channel`, `ic_mn_cloud`, `ic_mn_info`).
   - Title, descriptive message, timestamp relative formatter (`MN.fmt.rel`).
   - Unread indicator dot in `#FFB1B6`.
3. **Actions:**
   - Top bar: `Mark All Read` and `Clear All` confirmation dialog.
   - Tap item: Marks read and deep-links to associated video/download/sync view.

---

## 8. Phased Execution Roadmap & Verification Gates

```text
┌────────────────────────────────────────────────────────────────────────────────┐
│ PHASE 1: Design System & Theme Engine                                         │
│ • Enforce DarkColorScheme universally (Theme.kt)                               │
│ • Configure themes.xml and colors.xml for dark window canvas (#120B0E)         │
│ • Establish 23-token MediaNestColors & LocalMediaNestColors                    │
│ • Fix CollectionsPreferences view mode desynchronization                       │
└───────────────────────┬────────────────────────────────────────────────────────┘
                        │
┌───────────────────────▼────────────────────────────────────────────────────────┐
│ PHASE 2: Vector Assets Pipeline                                               │
│ • Generate all 67 XML Vector Drawables in app/src/main/res/drawable/ic_mn_*.xml │
│ • Replace stock Material icon usages with painterResource(R.drawable.ic_mn_*) │
└───────────────────────┬────────────────────────────────────────────────────────┘
                        │
┌───────────────────────▼────────────────────────────────────────────────────────┐
│ PHASE 3: Core UI Component Architecture                                       │
│ • Implement GlassCard, MediaNestBottomNav (72dp + indicator), MediaNestTopAppBar│
│ • Implement MiniPlayer (with Next button), MnNoteBox, PillTabRow, SortPill     │
│ • Implement UnifiedVideoCard, UnifiedVideoRow, and VideoActionBottomSheet     │
└───────────────────────┬────────────────────────────────────────────────────────┘
                        │
┌───────────────────────▼────────────────────────────────────────────────────────┐
│ PHASE 4: Notifications Hub Subsystem                                          │
│ • Bump AppDatabase 17→18 + add Room Migration (app_notifications)            │
│ • Implement AppNotificationEntity, NotificationDao, NotificationRepository     │
│ • Implement NotificationsViewModel and NotificationsScreen.kt                  │
│ • Wire TopAppBar notification bell count badge and navigation                  │
└───────────────────────┬────────────────────────────────────────────────────────┘
                        │
┌───────────────────────▼────────────────────────────────────────────────────────┐
│ PHASE 5: Screen Migrations & Parity                                           │
│ • HomeScreen: Hero extraction panel, rich result cards, link history           │
│ • Collections: 6-tab icon pills, scoped search, sort sheets, subfolder tree    │
│ • DownloadsScreen: Storage header card, thumbnail status badges, progress bars │
│ • PlayerScreen: YouTube Red seekbar, time watched threshold, transport row     │
│ • SettingsScreen: Missing preference toggles, note boxes, update lifecycle     │
│ • VideoDetailScreen & StatisticsScreen: Version lists, metric charts           │
│ • Apply §1.3 universal 10-item lazy loading + EndOfListIndicator everywhere    │
│ • Apply §7.6 top-content = top 5 (StatisticsScreen)                            │
│ • PRESERVE §1.4 native capabilities (SAF, WorkManager, NewPipe extractor, etc.)│
└───────────────────────┬────────────────────────────────────────────────────────┘
                        │
┌───────────────────────▼────────────────────────────────────────────────────────┐
│ PHASE 6: Build & Visual Verification Gates                                    │
│ • Clean Gradle compilation via build-debug.bat                                │
│ • Screen-by-screen visual fidelity inspection against design-2/ prototype      │
│ • End-to-end user interaction and regression verification                      │
└────────────────────────────────────────────────────────────────────────────────┘
```
