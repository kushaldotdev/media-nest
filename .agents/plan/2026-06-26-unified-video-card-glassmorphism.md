# Implementation Plan: Unified Video Card + App-Wide Glassmorphism

## System / Contract Summary

MediaNest is a Jetpack Compose Android app with multiple screens that each define their own video card composables. This plan unifies all video list cards into a single reusable composable matching the Downloads tab's glassmorphism style, fixes the favourite icon colour to red, adds a tappable title expand feature, introduces DRY abbreviated+exact relative time formatting, re-layouts the Downloads tab buttons, and applies glassmorphism to all Card components app-wide (including Settings).

### Current State — Video Card Implementations (Verified)

| Location | Composable | Style | Lines |
|----------|-----------|-------|-------|
| [LibraryScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt#L511-L678) | `VideoCard` (grid) | `surfaceVariant` solid, elevation 2dp | L531-534 |
| [LibraryScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt#L683-L858) | `VideoListRow` (list) | `surfaceVariant` solid, elevation 2dp | L703-706 |
| [HomeScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/HomeScreen.kt#L367-L453) | `VideoResultCard` | Default `Card()` — no custom colours | L372 |
| [HomeScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/HomeScreen.kt#L456-L514) | `VideoListItem` | No Card — bare `Row` | L458-462 |
| [HomeScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/HomeScreen.kt#L315-L363) | `HistoryItemRow` | `surfaceVariant.copy(alpha = 0.5f)` | L322-328 |
| [DownloadsScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/DownloadsScreen.kt#L307-L736) | `DownloadItem` | **Glassmorphism** — `surfaceVariant.copy(alpha = 0.3f)` + `BorderStroke(1dp, outlineVariant.copy(alpha = 0.5f))` | L333-344 |

### Target State

All video list cards will use the Downloads tab's glassmorphism recipe:
```kotlin
shape = RoundedCornerShape(12.dp),
colors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
),
border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
```

---

## Phase Order

1. **Phase 1 — DRY Foundation:** New utility functions in `UiUtils.kt` + new shared `GlassCard.kt` composable
2. **Phase 2 — Unified Video Card:** New `UnifiedVideoCard.kt` component (grid + list variants)
3. **Phase 3 — Screen Integration:** Replace all per-screen card composables with the unified ones + grid/list toggle for ALL Library tabs
4. **Phase 4 — Downloads Screen Fixes:** Size badge above thumbnail, green tick next to size, evenly-spaced buttons
5. **Phase 5 — Favourite Colour Fix:** Red favourite icon app-wide
6. **Phase 6 — App-Wide Glassmorphism:** Settings, Home, MainScreen, Subscriptions cards

---

## Steps

### Phase 1 — DRY Foundation

---

#### Step 1.1: Add `formatRelativeTime` universal function to UiUtils

- **What:** Replace `getRelativeTimeString` (private, lines 59-96) with a single public `formatRelativeTime(date: Date, abbreviated: Boolean = true): String` function. When `abbreviated = true` → `"1y 5mo 2d 12h"`. When `abbreviated = false` → `"1 year, 5 months, 2 days, 12 hours ago"`.
- **Where:** [UiUtils.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/utils/UiUtils.kt) — replace `getRelativeTimeString` (L59-96)
- **How (exact):**
  1. Delete the existing `getRelativeTimeString` function (L59-96).
  2. Add new `formatRelativeTime(date: Date, abbreviated: Boolean = true): String`:
     - Compute `diffMs = System.currentTimeMillis() - date.time`. If < 0, return `"just now"`.
     - Compute `years = diffDay / 365`, `months = (diffDay % 365) / 30`, `days = (diffDay % 365) % 30`, `hours = diffHour % 24`.
     - If `abbreviated`:
       - Build string by appending non-zero components: `"${years}y"`, `"${months}mo"`, `"${days}d"`, `"${hours}h"`.
       - If all zero but `diffMin > 0`: `"${diffMin}m"`.
       - If all zero: `"just now"`.
     - If not `abbreviated`:
       - Build string joining non-zero components with `", "`: `"1 year"`, `"5 months"`, `"2 days"`, `"12 hours"`.
       - Append `" ago"` at the end.
       - Handle plural/singular: `year` vs `years`, `month` vs `months`, etc.
       - If all zero but `diffMin > 0`: `"$diffMin minute(s) ago"`.
       - If all zero: `"just now"`.
  3. Update `formatReleaseDate` (L98-105) to call `formatRelativeTime(date, abbreviated = true)` instead of `getRelativeTimeString(date)`.
  4. Add new public function `formatRelativeDateExact(rawDate: String?): String?` that calls `formatRelativeTime(date, abbreviated = false)`.
  5. Make `parseUploadDate` public so callers (like card composables doing tappable time toggle) can parse the date if needed, or alternatively expose a `formatRelativeTimestamp(epochMillis: Long, abbreviated: Boolean): String` overload for timestamps.
- **Why:** DRY — one function serves both abbreviated and exact relative time across the entire app.
- **Edge cases:**
  - `diffMs < 0` (future date) → return `"just now"`.
  - `date == null` or unparseable → return raw string as-is.
  - Very recent (< 1 minute) → `"just now"` for both modes.
- **Pitfalls / do not:**
  - Do NOT remove `formatAbsoluteDate` or `formatAbsoluteReleaseDate` — they are used elsewhere.
  - Do NOT change the signature of `formatReleaseDate` — it's called from many places. Its behaviour just changes from `"2 years ago"` → `"2y"`.
- **Validation:** Compile. Grep all callers of `formatReleaseDate` / `getRelativeTimeString` and confirm they still compile.
- **Docs:** Add KDoc to `formatRelativeTime` documenting both modes with examples.

---

#### Step 1.2: Create `GlassCard.kt` — reusable glassmorphism Card wrapper

- **What:** A composable `GlassCard` that wraps `Card` with the glassmorphism styling, so every screen can call `GlassCard { content }` instead of repeating the same `Card(colors = ..., border = ...)` pattern.
- **Where:** [NEW] `app/src/main/java/com/example/medianest/ui/components/GlassCard.kt`
- **How (exact):**
  ```kotlin
  package com.example.medianest.ui.components

  @Composable
  fun GlassCard(
      modifier: Modifier = Modifier,
      onClick: (() -> Unit)? = null,
      shape: Shape = RoundedCornerShape(12.dp),
      content: @Composable ColumnScope.() -> Unit
  ) {
      Card(
          modifier = modifier,
          shape = shape,
          colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
          ),
          border = BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
          ),
          onClick = onClick ?: {},
          enabled = onClick != null
      ) {
          content()
      }
  }
  ```
  - If `onClick` is null, use a non-clickable `Card` overload (the one without `onClick`). Provide two overloads or use conditional logic.
- **Why:** DRY — all glassmorphism cards use a single source of truth. If the user later tweaks the alpha/border, one change propagates everywhere.
- **Edge cases:** `onClick = null` → card is not clickable (no ripple).
- **Pitfalls / do not:** Do NOT add elevation — the glassmorphism effect relies on the translucent background, not shadow.
- **Validation:** Compile.
- **Docs:** KDoc with usage example.

---

### Phase 2 — Unified Video Card Component

---

#### Step 2.1: Create `UnifiedVideoCard.kt` — the single video card for the whole app

- **What:** A composable that replaces `VideoCard`, `VideoListRow`, `VideoResultCard`, and `VideoListItem`. Supports grid and list layouts. Uses `GlassCard`. Features tappable title expand. Uses `formatRelativeTime` for dates. Uses red favourite icon.
- **Where:** [NEW] `app/src/main/java/com/example/medianest/ui/components/UnifiedVideoCard.kt`
- **How (exact):**
  1. Define a data class `VideoCardConfig` to encapsulate optional features:
     ```kotlin
     data class VideoCardConfig(
         val showFavoriteButton: Boolean = false,
         val showMoveToFolderButton: Boolean = false,
         val showDownloadButton: Boolean = false,
         val showSelectionCheckbox: Boolean = false,
         val showFolderBadges: Boolean = false,
         val showPlaybackProgress: Boolean = false,
         val showDownloadedBadge: Boolean = false,
     )
     ```
  2. Define `UnifiedVideoCard` composable (for grid layout):
     ```kotlin
     @OptIn(ExperimentalFoundationApi::class)
     @Composable
     fun UnifiedVideoCard(
         title: String,
         channelName: String,
         thumbnailUrl: String?,
         durationSeconds: Long = 0,
         uploadDate: String? = null,
         isFavorite: Boolean = false,
         isDownloaded: Boolean = false,
         isSelected: Boolean = false,
         playbackProgressFraction: Float = 0f,  // 0..1
         folders: List<FolderEntity> = emptyList(),
         config: VideoCardConfig = VideoCardConfig(),
         onClick: () -> Unit = {},
         onLongClick: () -> Unit = {},
         onFavoriteToggle: () -> Unit = {},
         onMoveToFolder: () -> Unit = {},
         onDownloadClick: () -> Unit = {},
         onSelectionToggle: () -> Unit = {},
         // Download menu support
         downloadMenuContent: (@Composable () -> Unit)? = null,
         modifier: Modifier = Modifier
     )
     ```
  3. Inside the composable:
     - Use `GlassCard` as the outer container.
     - `var isTitleExpanded by remember { mutableStateOf(false) }` for tappable title expand.
     - **Thumbnail Box:** `AsyncImage` with `ContentScale.Crop`, fixed height 120dp. Overlay:
       - Duration badge (bottom-end, same as existing).
       - Downloaded badge: green `CheckCircle` icon (**top-left**, `Alignment.TopStart`) — using `Color(0xFF4CAF50)` (Material green). Icon only (no file size text) on unified cards. The Downloads screen's own card shows icon + size together at the same position.
       - Playback progress bar (bottom-center, red fill over grey track, 2dp height).
     - **Content Column** (`Modifier.padding(12.dp)`):
       - Folder badges (if `config.showFolderBadges`).
       - Title `Text`: `maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2`, `overflow = TextOverflow.Ellipsis`, wrapped in `Modifier.clickable { isTitleExpanded = !isTitleExpanded }` with `animateContentSize()` on the parent Column for smooth expansion.
       - Metadata row: `channelName • formattedDate` using `UiUtils.formatReleaseDate(uploadDate)`.
       - Action buttons row (if not in selection mode): favourite (red), move-to-folder, download.
       - Selection checkbox (if in selection mode).
  4. Define `UnifiedVideoRow` composable (for list layout):
     - Same parameters as `UnifiedVideoCard`.
     - Horizontal `Row` layout: thumbnail (120×68dp) on left, content on right.
     - Same tappable title, same action buttons, same glassmorphism via `GlassCard`.
  5. **Favourite button implementation (both composables):**
     ```kotlin
     IconToggleButton(
         checked = isFavorite,
         onCheckedChange = { onFavoriteToggle() }
     ) {
         Icon(
             if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
             contentDescription = "Favorite",
             tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
         )
     }
     ```
- **Why:** Single source of truth for all video cards. DRY. Tappable title expand (Option A). Red favourite icon. Glassmorphism.
- **Edge cases:**
  - `durationSeconds == 0` → hide duration badge.
  - `uploadDate == null` → hide date from metadata.
  - `channelName` empty → metadata shows only date (or nothing).
  - `playbackProgressFraction == 0f` → hide progress bar.
  - Title is short enough to fit 2 lines → tapping does nothing visible (no ellipsis shown), which is fine.
- **Pitfalls / do not:**
  - Do NOT use `Modifier.basicMarquee()` — we chose Option A (tappable).
  - Do NOT forget `animateContentSize()` on the Column wrapping the title — without it the expand is jarring.
  - Do NOT pass raw `HistoryEntity` — compute `playbackProgressFraction` at the call site to keep the component clean.
- **Validation:** Compile.
- **Docs:** KDoc with parameter docs and usage example for both grid and list.

---

### Phase 3 — Screen Integration

---

#### Step 3.1: Replace Library's `VideoCard` + `VideoListRow` + `VideoListLayout` with unified components

- **What:** Delete `VideoCard` (L511-678), `VideoListRow` (L683-858), update `VideoListLayout` (L429-507) to use `UnifiedVideoCard` / `UnifiedVideoRow`.
- **Where:** [LibraryScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt)
- **How (exact):**
  1. Import `UnifiedVideoCard`, `UnifiedVideoRow`, `VideoCardConfig` from `com.example.medianest.ui.components`.
  2. In `VideoListLayout` (L449):
     - In the grid branch (L450-478), replace `VideoCard(...)` call with:
       ```kotlin
       val history = playbackHistory.find { it.videoId == video.id }
       val progressFraction = if (video.durationSeconds > 0 && (history?.positionMillis ?: 0) > 0) {
           ((history?.positionMillis ?: 0).toFloat() / 1000f / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
       } else 0f

       UnifiedVideoCard(
           title = video.title,
           channelName = video.channelName,
           thumbnailUrl = video.thumbnailUrl,
           durationSeconds = video.durationSeconds,
           uploadDate = video.uploadDate,
           isFavorite = video.favorite,
           isDownloaded = video.localFilePath.isNotEmpty(),
           isSelected = selectedIds.contains(video.id),
           playbackProgressFraction = progressFraction,
           folders = videoFolderMap[video.id] ?: emptyList(),
           config = VideoCardConfig(
               showFavoriteButton = !isSelectionMode,
               showMoveToFolderButton = !isSelectionMode,
               showDownloadButton = !isSelectionMode,
               showSelectionCheckbox = isSelectionMode,
               showFolderBadges = true,
               showPlaybackProgress = true,
               showDownloadedBadge = true,
           ),
           onClick = { if (isSelectionMode) onToggleSelection(video.id) else onVideoClick(video.id) },
           onLongClick = { onVideoLongClick(video.id) },
           onFavoriteToggle = { onFavoriteToggle(video) },
           onMoveToFolder = { /* call LocalMoveToFolder */ },
           onDownloadClick = { onDownloadIconClick(video.id) },
           onSelectionToggle = { onToggleSelection(video.id) },
           downloadMenuContent = {
               QuickDownloadMenu(
                   isExpanded = expandedDownloadVideoId == video.id,
                   onDismiss = onDismissDownloadMenu,
                   isFetching = fetchingStreamsFor == video.id,
                   fetchedStreams = fetchedStreams,
                   allDownloads = allDownloads,
                   videoId = video.id,
                   onEnqueueDownload = onEnqueueDownload,
                   onDeleteDownload = onDeleteDownload,
                   onExtractAudio = onExtractAudio
               )
           }
       )
       ```
     - In the list branch (L479-507), same pattern but use `UnifiedVideoRow(...)`.
  3. In `FolderContent` (L862-1008), update `VideoCard(...)` calls (L961, L983) the same way.
  4. Delete the old `VideoCard` (L511-678) and `VideoListRow` (L683-858) composables.
  5. Move `QuickDownloadMenu` composable (L1156-1255) to a shared components file since it's now referenced from `UnifiedVideoCard` via slot content. Alternatively keep it in `LibraryScreen.kt` but make it `internal` instead of `private`.
  6. Move `FolderBadges` composable to `UnifiedVideoCard.kt` or a shared file so it can be called from the unified card.
- **Why:** DRY — no more duplicated card code in Library.
- **Edge cases:**
  - `LocalMoveToFolder` CompositionLocal is provided in `LibraryScreen`. The unified card's `onMoveToFolder` callback is wired to it at the call site.
  - `FolderBadges` depends on `FolderEntity` — the unified card already takes `folders: List<FolderEntity>`.
- **Pitfalls / do not:**
  - Do NOT break the `QuickDownloadMenu` — it's complex and works well. Just move it; don't refactor its internals.
  - Do NOT remove `VideoListLayout` — it still orchestrates grid vs list; just update its body.
- **Validation:** Compile. Navigate to Library → History, Folders, Favorites tabs. Verify cards render with glassmorphism, tappable title, red favourite, green downloaded badge.

---

#### Step 3.2: Replace HomeScreen's `VideoResultCard` and `VideoListItem` with unified components

- **What:** Update `VideoResultCard` (L367-453) and `VideoListItem` (L456-514) to use `UnifiedVideoRow` / `UnifiedVideoCard`.
- **Where:** [HomeScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/HomeScreen.kt)
- **How (exact):**
  1. `VideoResultCard` (search result for a single video) — replace the Card body at L372-453 with:
     ```kotlin
     UnifiedVideoCard(
         title = video.title,
         channelName = video.channelName,
         thumbnailUrl = video.thumbnailUrl,
         durationSeconds = video.durationSeconds,
         uploadDate = video.uploadDate,
         isFavorite = isFavorited,  // local state
         config = VideoCardConfig(showFavoriteButton = onFavoriteToggle != null),
         onClick = onSelectQuality,
         onFavoriteToggle = { ... },
     )
     ```
     Note: `VideoResultCard` currently has a "Select Quality" button. This is unique to the search result. Add it below the `UnifiedVideoCard` or integrate it as an extra action slot in the unified card API.
     **Decision:** Keep `VideoResultCard` as a wrapper that places `UnifiedVideoCard` followed by a "Select Quality" `Button`. This avoids polluting the unified card with search-specific UI.
  2. `VideoListItem` (channel/playlist video rows) — replace the bare `Row` at L458-513 with the **full** `UnifiedVideoRow` (same as Library list view, NOT a minimal variant). Channel/playlist videos get the same card with progress bar, favourite, move-to-folder, and download buttons:
     ```kotlin
     UnifiedVideoRow(
         title = video.title,
         channelName = if (showChannelName) video.channelName else "",
         thumbnailUrl = video.thumbnailUrl,
         durationSeconds = video.durationSeconds,
         uploadDate = video.uploadDate,
         isFavorite = video.favorite,
         isDownloaded = video.localFilePath.isNotEmpty(),
         playbackProgressFraction = progressFraction,
         config = VideoCardConfig(
             showFavoriteButton = true,
             showMoveToFolderButton = true,
             showDownloadButton = true,
             showPlaybackProgress = true,
             showDownloadedBadge = true,
         ),
         onClick = onClick,
         onFavoriteToggle = { onFavoriteToggle(video.id, video.favorite) },
         onMoveToFolder = { onMoveToFolder(video.id) },
         onDownloadClick = { onDownloadClick(video.id) },
     )
     ```
  3. `HistoryItemRow` (link history, L315-363) — this is a URL history item, not a video card. Keep it but apply `GlassCard` wrapper instead of the current `Card` styling to match the glassmorphism theme.
  4. Delete the old `VideoResultCard` body and `VideoListItem` body (keep the function as a thin wrapper if needed for backwards compatibility).
- **Why:** Consistent look across the app. All video items everywhere look identical.
- **Edge cases:**
  - Channel/playlist videos now get full action buttons — the `HomeViewModel` must expose `toggleFavorite`, `fetchStreamsFor`, etc. If these are missing, wire them from the ViewModel or pass no-ops and add TODOs.
  - `VideoResultCard`'s "Select Quality" button is handled outside the unified card.
- **Pitfalls / do not:**
  - Do NOT remove the "Select Quality" button — it's the primary action on the Home search result.
  - The `VideoResultCard` has a local `isFavorited` state (`var isFavorited by remember { mutableStateOf(false) }`). This must be preserved; wire it to `UnifiedVideoCard`'s `isFavorite` parameter.
  - Do NOT create a "minimal variant" of the video row — all video rows look the same everywhere.
- **Validation:** Compile. Search for a video on Home. Verify the result card and channel/playlist video items render with glassmorphism, progress bar, and full action buttons.

---

#### Step 3.3: Enable grid/list toggle for ALL Library tabs (Playlists + Channels)

- **What:** Currently the grid/list toggle button in the Library top bar is hidden for Playlists and Channels tabs (condition at [LibraryScreen.kt:L93](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt#L93): `uiState.currentTab != LibraryTab.SUBSCRIPTIONS && uiState.currentTab != LibraryTab.PLAYLISTS`). Remove this exclusion so the toggle appears for ALL tabs.
- **What:** Update Library top bar to show grid/list toggle for Playlists and Channels.
- **Where:** [LibraryScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt) — L93, L305-310 · [SubscriptionsScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/SubscriptionsScreen.kt) — L76-95
- **How (exact):**
  1. In `LibraryScreen.kt` L93, change the condition to always show the toggle (except Folders root which has no videos):
     ```kotlin
     // After:
     if (uiState.currentTab != LibraryTab.FOLDERS || uiState.selectedFolder != null) {
     ```
  2. Pass `viewMode` to `SubscriptionsScreen`:
     ```kotlin
     // L305-310:
     LibraryTab.PLAYLISTS -> {
         SubscriptionsScreen(
             sourceType = "playlist",
             searchQuery = uiState.searchQuery,
             viewMode = uiState.viewMode,           // NEW
             onSubscriptionClick = onSubscriptionClick
         )
     }
     LibraryTab.SUBSCRIPTIONS -> {
         SubscriptionsScreen(
             sourceType = "channel",
             searchQuery = uiState.searchQuery,
             viewMode = uiState.viewMode,           // NEW
             onSubscriptionClick = onSubscriptionClick
         )
     }
     ```
  3. Update `SubscriptionsScreen` composable signature to accept `viewMode: ViewMode = ViewMode.LIST`.
  4. In `SubscriptionsScreen`, replace the `LazyColumn` (L76-95) with a conditional:
     ```kotlin
     if (viewMode == ViewMode.GRID) {
         LazyVerticalGrid(
             columns = GridCells.Fixed(2),
             modifier = Modifier.fillMaxSize().padding(8.dp),
             verticalArrangement = Arrangement.spacedBy(8.dp),
             horizontalArrangement = Arrangement.spacedBy(8.dp)
         ) {
             items(filtered, key = { it.id }) { sub ->
                 SubscriptionCard(sub, ...)
             }
         }
     } else {
         LazyColumn(
             modifier = Modifier.fillMaxSize().padding(8.dp),
             verticalArrangement = Arrangement.spacedBy(8.dp)
         ) {
             items(filtered, key = { it.id }) { sub ->
                 SubscriptionCard(sub, ...)
             }
         }
     }
     ```
  5. The `SubscriptionCard` composable itself doesn't need separate grid/list variants — it adapts via `fillMaxWidth()` which works in both grid cells and list rows. The grid cells will naturally make it narrower, stacking content vertically.
- **Why:** User wants grid/list toggle on every Library tab.
- **Edge cases:**
  - Grid layout for subscription cards may look cramped with long names + buttons. The card's Row layout will naturally wrap or clip. If this looks bad, consider a vertical variant of `SubscriptionCard` for grid mode (avatar on top, name below, buttons below that). Assess during implementation.
  - Folders root (no selected folder) still hides the toggle since there are no video cards to toggle — only folder rows.
- **Pitfalls / do not:**
  - Do NOT change the ViewMode enum or ViewModel logic — `toggleViewMode()` already cycles between GRID and LIST. Just wire it through.
  - Do NOT forget to add `LazyVerticalGrid` imports to `SubscriptionsScreen.kt`.
- **Validation:** Compile. Navigate to Library → Playlists, toggle grid/list. Navigate to Library → Channels, toggle grid/list. Verify both layouts render correctly.

---

### Phase 4 — Downloads Screen Fixes

---

#### Step 4.1: Move download size badge + green tick above thumbnail, remove "Completed" text

- **What:** Currently the file size (`"X.X MB"`) and a "✅ Completed" / "Done" status are shown in a row below the thumbnail (L428-446). Move the size badge to overlay the thumbnail (top-end corner) with a semi-transparent dark background pill. Place a green `CheckCircle` icon right next to the size text, in the same badge. Remove the "✅ Completed" status row entirely for completed downloads — the green tick on the thumbnail is sufficient.
- **Where:** [DownloadsScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/DownloadsScreen.kt) — `DownloadItem` composable, L356-382 (thumbnail Box) and L427-446 (status display)
- **How (exact):**
  1. In the thumbnail `Box` (L356-382), add a new `Row` overlay at `Alignment.TopStart` containing both the green tick and the size:
     ```kotlin
     if (download.status == DownloadStatus.COMPLETED &&
         download.errorMessage != "file_missing"
     ) {
         Row(
             modifier = Modifier
                 .align(Alignment.TopStart)
                 .padding(4.dp)
                 .background(
                     color = Color.Black.copy(alpha = 0.7f),
                     shape = RoundedCornerShape(4.dp)
                 )
                 .padding(horizontal = 4.dp, vertical = 2.dp),
             verticalAlignment = Alignment.CenterVertically,
             horizontalArrangement = Arrangement.spacedBy(3.dp)
         ) {
             Icon(
                 Icons.Default.CheckCircle,
                 contentDescription = "Downloaded",
                 tint = Color(0xFF4CAF50),
                 modifier = Modifier.size(12.dp)
             )
             if (download.fileSizeBytes > 0L) {
                 Text(
                     text = "%.1f MB".format(download.fileSizeBytes / (1024f * 1024f)),
                     color = Color.White,
                     style = MaterialTheme.typography.labelSmall
                 )
             }
         }
     }
     ```
  2. Remove the entire completed status row (the "✅ Completed" / "Done" / size text) from the status area (L427-446). For completed downloads, the status area is now empty — go straight to the action buttons divider.
  3. Keep the status row for non-completed states (QUEUED, DOWNLOADING, PAUSED, FAILED, CANCELED) — those still show progress text, retry info, etc.
- **Why:** User requested: (1) size above thumbnail, (2) green tick next to size, (3) remove redundant "Completed" text.
- **Edge cases:**
  - `fileSizeBytes == 0` → show green tick only, no size text.
  - `errorMessage == "file_missing"` → don't show the badge at all (file is gone).
- **Pitfalls / do not:**
  - Do NOT keep any "Completed" or "Done" text — the green tick is the indicator.
  - Do NOT touch the status display for non-completed states.
- **Validation:** Compile. Navigate to Downloads tab with completed downloads. Verify green tick + size badge on thumbnail top-left. Verify no "Completed" text below.

---

#### Step 4.2: Space action buttons evenly in Downloads (play / extract audio / delete)

- **What:** Currently the completed-download action buttons (Play, Extract Audio, Delete) at L530-733 use `Arrangement.End` with manual `Spacer(Modifier.width(8.dp))` between them. Change to `Arrangement.SpaceBetween` so they spread across the full row width.
- **Where:** [DownloadsScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/DownloadsScreen.kt) — L531-533
- **How (exact):**
  1. Change the action buttons `Row` at L531-533:
     ```kotlin
     // Before:
     Row(
         modifier = Modifier.fillMaxWidth(),
         horizontalArrangement = Arrangement.End,
         verticalAlignment = Alignment.CenterVertically
     )

     // After:
     Row(
         modifier = Modifier.fillMaxWidth(),
         horizontalArrangement = Arrangement.SpaceBetween,
         verticalAlignment = Alignment.CenterVertically
     )
     ```
  2. Remove `Spacer(Modifier.width(8.dp))` calls between the buttons in the `COMPLETED` branch (L654-731). The `SpaceBetween` arrangement handles spacing.
- **Why:** User requested evenly-spaced buttons.
- **Edge cases:**
  - When only 1 button is visible (e.g., file_missing with audio_extracted → only Delete), `SpaceBetween` with 1 item centers it. This is fine.
  - When 2 buttons visible → they go to opposite ends. This is acceptable.
- **Pitfalls / do not:**
  - Do NOT change button arrangement for non-COMPLETED statuses (QUEUED, DOWNLOADING, PAUSED, FAILED, CANCELED) — those have different button sets and the `End` arrangement may still be appropriate. Actually, apply `SpaceBetween` to all statuses in this Row for consistency since the user wants them evenly spaced.
- **Validation:** Compile. Check Downloads tab with completed downloads. Verify Play, Extract Audio, Delete are evenly spaced.

---

### Phase 5 — Favourite Colour Fix (Red)

---

#### Step 5.1: Fix favourite icon tint to red in all screens

- **What:** Change the favourite icon's active tint from `MaterialTheme.colorScheme.primary` (purple/blue) to `Color.Red` everywhere.
- **Where:** All 4 locations:
  1. [LibraryScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt) — `VideoCard` L641, `VideoListRow` L817 — **these will be deleted in Phase 3, so the fix is handled by the unified card's red tint.**
  2. [HomeScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/HomeScreen.kt) — `VideoResultCard` L446 — **handled by unified card.**
  3. [VideoDetailScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/VideoDetailScreen.kt) — L120: `tint = if (isFavorite) MaterialTheme.colorScheme.primary else ...`
- **How (exact):**
  - The unified card (Phase 2) already uses `Color.Red` for the active favourite tint. This covers Library + Home.
  - For `VideoDetailScreen.kt` L120, change:
    ```kotlin
    // Before:
    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    // After:
    tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
    ```
  - Add `import androidx.compose.ui.graphics.Color` if not already present.
- **Why:** User wants red favourites everywhere.
- **Edge cases:** None — colour change only.
- **Pitfalls / do not:** Do NOT change the filled vs outlined icon logic — only the tint colour.
- **Validation:** Compile. Toggle favourite on Video Detail screen. Verify icon fills red.

---

### Phase 6 — App-Wide Glassmorphism

---

#### Step 6.1: Apply glassmorphism to SettingsScreen cards

- **What:** Replace all `Card(...)` calls in SettingsScreen with `GlassCard(...)`.
- **Where:** [SettingsScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/SettingsScreen.kt) — 13 Card instances at lines: 171, 317, 387, 455, 473, 533, 553, 744, 789, 817, 853, 873, 906
- **How (exact):**
  1. Import `GlassCard` from `com.example.medianest.ui.components`.
  2. For each `Card(` occurrence:
     - Replace `Card(` with `GlassCard(`.
     - Remove the `colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)` parameter (GlassCard handles this).
     - Remove any `elevation` parameter (GlassCard has no elevation).
     - Keep `modifier` and `shape` parameters.
     - The inner content lambda stays the same.
  3. For cards that use `Card(modifier = Modifier.fillMaxWidth()) {` (no explicit colors) at L387, L473, L744, L853, L873 — replace with `GlassCard(modifier = Modifier.fillMaxWidth()) {`.
  4. For inner status cards at L533, L553, L789, L817, L906 — these use `containerColor = MaterialTheme.colorScheme.primaryContainer` or `errorContainer`. **Keep these as-is** or apply glassmorphism with the special colour. Decision: Apply glassmorphism to the outer cards only. Inner status cards (success/error indicators) keep their distinct colours for clarity.
- **Why:** Consistent glassmorphism app-wide per user request.
- **Edge cases:** Inner cards with semantic colours (primary/error) should remain distinct.
- **Pitfalls / do not:**
  - Do NOT apply glassmorphism to the inner status cards (backup success, backup error, repair success, repair error, update available) — these use colour to convey state.
  - Do NOT break `Card(onClick = ...)` calls — `GlassCard` must support `onClick`.
- **Validation:** Compile. Navigate to Settings. Verify all section cards have the translucent glassmorphism effect. Verify status indicator cards still show their colours.

---

#### Step 6.2: Apply glassmorphism to HomeScreen cards

- **What:** Apply glassmorphism to the error card (L158), the `HistoryItemRow` (L322), and ensure `VideoResultCard` wrapper uses `GlassCard`.
- **Where:** [HomeScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/HomeScreen.kt)
- **How (exact):**
  1. Error card at L158-170: Replace `Card(... colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer))` — **keep as-is** since it's an error indicator.
  2. `HistoryItemRow` at L322-328: Replace `Card(... colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)))` with `GlassCard(...)`.
  3. `VideoResultCard` wrapper: Already handled by Phase 3 — the unified card uses `GlassCard` internally.
- **Why:** Consistent glassmorphism.
- **Edge cases:** Error card stays with `errorContainer` colour.
- **Pitfalls / do not:** Do NOT glassmorphize the error card.
- **Validation:** Compile. Navigate to Home. Verify link history cards have glassmorphism.

---

#### Step 6.3: Apply glassmorphism and UI updates to SubscriptionsScreen

- **What:** Update `SubscriptionCard` at [SubscriptionsScreen.kt:L110](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/SubscriptionsScreen.kt#L110) to use `GlassCard`, remove the repetitive "Automatically downloads..." text, make the title tap-to-expand, and add a single italic note at the bottom of the list.
- **Where:** [SubscriptionsScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/SubscriptionsScreen.kt)
- **How (exact):**
  1. Import `GlassCard`.
  2. Replace `Card(onClick = onClick, modifier = ...)` with `GlassCard(onClick = onClick, modifier = ...)`.
  3. Inside `SubscriptionCard`, add `var isTitleExpanded by remember { mutableStateOf(false) }`.
  4. Find the title `Text(text = subscription.name, ...)` and update it to be tappable and expandable:
     ```kotlin
     Text(
         text = subscription.name,
         style = MaterialTheme.typography.titleMedium,
         maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2,
         overflow = TextOverflow.Ellipsis,
         modifier = Modifier.clickable { isTitleExpanded = !isTitleExpanded }
     )
     ```
  5. Delete the `Text` below it that says "Automatically downloads new uploads" (and its preceding `Spacer`).
  6. Add the note to the bottom of the list. In `SubscriptionsScreen` where the `LazyColumn` or `LazyVerticalGrid` is (updated in Step 3.3):
     ```kotlin
     // For LazyColumn:
     item {
         Text(
             text = "*Automatically downloads new uploads*",
             style = MaterialTheme.typography.bodySmall,
             fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
             color = MaterialTheme.colorScheme.onSurfaceVariant,
             modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
             textAlign = androidx.compose.ui.text.style.TextAlign.Center
         )
     }
     // For LazyVerticalGrid (requires item(span = { GridItemSpan(maxLineSpan) })):
     item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
         Text(
             text = "*Automatically downloads new uploads*",
             style = MaterialTheme.typography.bodySmall,
             fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
             color = MaterialTheme.colorScheme.onSurfaceVariant,
             modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
             textAlign = androidx.compose.ui.text.style.TextAlign.Center
         )
     }
     ```
- **Why:** Consistent glassmorphism, less clutter, titles aren't truncated, and the auto-download feature is still communicated.
- **Edge cases:** None.
- **Pitfalls / do not:**
  - The `GlassCard` must support `onClick` — verify this in Step 1.2.
  - Don't forget `animateContentSize()` on the Column containing the title if you want smooth expansion, though instant is also fine.
- **Validation:** Compile. Navigate to Library → Playlists / Channels. Verify subscription cards have glassmorphism, long titles expand on tap, the subtitle is gone, and the italic note appears at the bottom.

---

#### Step 6.4: Apply glassmorphism to FolderRow card in LibraryScreen

- **What:** The `FolderRow` at [LibraryScreen.kt:L1119](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt#L1119) uses `Card(... surfaceVariant, elevation 2dp)`. Apply glassmorphism.
- **Where:** [LibraryScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt) — L1119-1123
- **How (exact):**
  1. Replace:
     ```kotlin
     Card(
         shape = RoundedCornerShape(12.dp),
         elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
         modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
     )
     ```
     with:
     ```kotlin
     GlassCard(
         modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
     )
     ```
- **Why:** Consistent glassmorphism.
- **Validation:** Compile. Navigate to Library → Folders. Verify folder cards have glassmorphism.

---

#### Step 6.5: Apply glassmorphism to MainScreen MiniPlayer card

- **What:** The `MiniPlayer` at [MainScreen.kt:L254](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/MainScreen.kt#L254) uses a custom `primaryContainer.copy(alpha = 0.95f)` style with a coloured border. This is a distinct design intentionally different from content cards — it's a persistent media control.
- **Where:** [MainScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/MainScreen.kt)
- **Decision:** **Skip.** The MiniPlayer has a deliberately distinct design (primary colour, higher elevation, coloured border) to stand out as an interactive control. Applying the same glassmorphism as content cards would reduce its visual prominence. Keep as-is.

---

## Beginner Implementation Guide

### Execution Order
1. Start with `UiUtils.kt` changes (Step 1.1) — pure logic, no UI risk.
2. Create `GlassCard.kt` (Step 1.2) — new file, zero impact until used.
3. Create `UnifiedVideoCard.kt` (Step 2.1) — new file, zero impact until used.
4. Integrate into `LibraryScreen.kt` (Step 3.1) — biggest change, test thoroughly.
5. Integrate into `HomeScreen.kt` (Step 3.2).
6. Fix Downloads screen (Steps 4.1, 4.2).
7. Fix favourite colour in `VideoDetailScreen.kt` (Step 5.1).
8. Apply glassmorphism to all other screens (Steps 6.1–6.4).

### Key Files to Create
- `app/src/main/java/com/example/medianest/ui/components/GlassCard.kt`
- `app/src/main/java/com/example/medianest/ui/components/UnifiedVideoCard.kt`

### Key Files to Modify
- `app/src/main/java/com/example/medianest/ui/utils/UiUtils.kt`
- `app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt`
- `app/src/main/java/com/example/medianest/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/example/medianest/ui/screens/DownloadsScreen.kt`
- `app/src/main/java/com/example/medianest/ui/screens/VideoDetailScreen.kt`
- `app/src/main/java/com/example/medianest/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/example/medianest/ui/screens/SubscriptionsScreen.kt`

### Do NOT Touch
- `app/src/main/java/com/example/medianest/ui/MainScreen.kt` (MiniPlayer stays as-is)
- Data layer (entities, DAOs, repositories)
- ViewModel layer (no logic changes needed)
- Theme files (glassmorphism is applied at the composable level, not theme level)

---

## Final Verification Checklist

- [ ] `UiUtils.formatRelativeTime(date, true)` returns abbreviated like `"1y 5mo 2d"`.
- [ ] `UiUtils.formatRelativeTime(date, false)` returns exact like `"1 year, 5 months, 2 days ago"`.
- [ ] `UiUtils.formatReleaseDate` now returns abbreviated format by default.
- [ ] `GlassCard` renders with `surfaceVariant.copy(alpha = 0.3f)` background and `outlineVariant.copy(alpha = 0.5f)` border.
- [ ] `UnifiedVideoCard` (grid) renders correctly with glassmorphism, tappable title expand, red favourite, green downloaded badge, playback progress bar.
- [ ] `UnifiedVideoRow` (list) renders correctly with same features.
- [ ] Library → History tab: cards use unified component.
- [ ] Library → Folders tab: cards use unified component + folder badges.
- [ ] Library → Favorites tab: cards use unified component, favourite icon is red when active.
- [ ] Library → Playlists tab: subscription cards have glassmorphism.
- [ ] Library → Channels tab: subscription cards have glassmorphism.
- [ ] Home → search result: card uses unified component with glassmorphism.
- [ ] Home → channel/playlist videos: rows use unified component with glassmorphism.
- [ ] Home → link history: cards use glassmorphism.
- [ ] Downloads → completed: green tick + size badge on thumbnail top-left, no "Completed" text.
- [ ] Downloads → completed: Play, Extract Audio, Delete buttons evenly spaced.
- [ ] VideoDetail → favourite icon fills red.
- [ ] Settings → all section cards use glassmorphism.
- [ ] Settings → inner status cards (success/error) keep their coloured backgrounds.
- [ ] No duplicated card styling code — all go through `GlassCard` or `UnifiedVideoCard`/`UnifiedVideoRow`.
- [ ] App compiles and runs without crashes.

---

## Stop Conditions

- If `GlassCard`'s translucent background makes text unreadable on certain device themes (light mode especially), STOP and add a `darkTheme` check that adjusts alpha values. Test on both light and dark modes before proceeding past Phase 1.
- If moving `QuickDownloadMenu` to a shared location causes circular dependency issues, keep it in `LibraryScreen.kt` and pass it as slot content via lambda.
- If Compose `animateContentSize()` causes layout jank on the tappable title expand with long video lists, consider removing the animation and using instant expand/collapse.

---

## Visual Mockups — Final State

> All card borders below represented by `┌─┐│└─┘` use the glassmorphism style:
> `containerColor = surfaceVariant.copy(alpha = 0.3f)` + `border = BorderStroke(1dp, outlineVariant.copy(alpha = 0.5f))`
>
> `❤` = Red filled favourite · `♡` = Unfilled grey favourite
>
> Time format examples: `1y 5mo 2d 12h` (abbreviated) · Tap to see `"1 year, 5 months, 2 days, 12 hours ago"` (exact)

---

### 1. Unified Video Card — Grid Mode

Used in: **Library → History, Favorites, Folders (grid view)**

```
┌─────────────────────────────────────┐  ← glassmorphism border
│  ┌─────────────────────────────────┐│
│  │                                 ││
│  │        [ THUMBNAIL IMAGE ]      ││  ← 120dp height, ContentScale.Crop
│  │                                 ││
│  │ ✅                               ││  ← green CheckCircle (TOP-LEFT, icon only)
│  │                                 ││
│  │                          3:42:15 ││  ← duration badge (bottom-right)
│  │▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░││  ← red playback progress bar (2dp)
│  └─────────────────────────────────┘│
│                                     │
│  📁 Music  📁 Favourites            │  ← folder badges (if applicable)
│                                     │
│  How to Build a Mass Spectrometer   │  ← title: maxLines=2, TAPPABLE
│  in Your Garage Using Only...       │     tap → expands to full title
│                                     │
│  Veritasium • 1y 5mo               │  ← channel • abbreviated time
│                                     │
│     ❤        📂        ⬇           │  ← red fav, move-to-folder, download
│                                     │
└─────────────────────────────────────┘
```

**After tapping title (expanded):**
```
┌─────────────────────────────────────┐
│  ┌─────────────────────────────────┐│
│  │        [ THUMBNAIL IMAGE ]      ││
│  │ ✅                               ││
│  │                          3:42:15 ││
│  │▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░││
│  └─────────────────────────────────┘│
│                                     │
│  How to Build a Mass Spectrometer   │  ← full title, no ellipsis,
│  in Your Garage Using Only Spare    │     animateContentSize() smooth
│  Parts and a Dream                  │     expand. Tap again → collapse.
│                                     │
│  Veritasium • 1y 5mo               │
│                                     │
│     ❤        📂        ⬇           │
└─────────────────────────────────────┘
```

**In selection mode:**
```
┌─────────────────────────────────────┐
│  ┌─────────────────────────────────┐│
│  │        [ THUMBNAIL IMAGE ]      ││
│  │ ✅                      3:42:15 ││  ← green icon top-left
│  └─────────────────────────────────┘│
│                                     │
│  How to Build a Mass Spectr...  [☑] │  ← checkbox replaces action buttons
│  Veritasium • 1y 5mo               │
└─────────────────────────────────────┘
```

---

### 2. Unified Video Row — List Mode

Used in: **Library → History, Favorites, Folders (list view) · Home → channel/playlist videos**

> **No minimal variant.** All video rows everywhere look identical — same progress bar, same action buttons.

```
┌──────────────────────────────────────────────────────────────┐
│ ┌──────────────┐                                             │
│ │              │  How to Build a Mass Spectrometer in...      │  ← TAPPABLE
│ │  THUMBNAIL   │  Veritasium • 1y 5mo                        │
│ │  120×68dp    │                                             │
│ │ ✅           │     ❤        📂        ⬇                   │  ← green icon top-left
│ │              │                                             │
│ │       3:42:15│                                             │  ← duration bottom-right
│ │▓▓▓▓▓▓░░░░░░░│                                             │  ← red progress bar
│ └──────────────┘                                             │
│  📁 Music                                                    │  ← folder badges (if any)
└──────────────────────────────────────────────────────────────┘
```

---

### 3. Downloads Card — Completed Download

Used in: **Downloads tab**

> No "✅ Completed" text. The green tick icon sits **next to the file size** on the thumbnail badge.

```
┌──────────────────────────────────────────────────────────────┐
│ ┌──────────────┐                                             │
│ │  THUMBNAIL   │  Building a Mass Spectrometer...            │
│ │  110×62dp    │                                             │
│ │  ✅ 245.3 MB │  ┌──────┐                                   │  ← green tick + size badge
│ │       3:42:15│  │ VIDEO │  1080p                            │     on thumbnail (TOP-LEFT)
│ └──────────────┘  └──────┘                                   │
│                                                              │
│ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  │  ← thin divider (no status text)
│                                                              │
│  [ ▶ Play ]          [ ♫ Extract Audio ]        [ 🗑 Delete ]│  ← Arrangement.SpaceBetween
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**Downloading state** (unchanged layout, for reference):

```
┌──────────────────────────────────────────────────────────────┐
│ ┌──────────────┐                                             │
│ │  THUMBNAIL   │  Building a Mass Spectrometer...            │
│ │  110×62dp    │                                             │
│ │       3:42:15│  ┌──────┐                                   │
│ └──────────────┘  │ VIDEO │  1080p                            │
│                   └──────┘                                   │
│  120.5MB / 245.3MB (49%) • 2.3 MB/s                          │
│  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░                       │  ← progress bar
│ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  │
│  [ ⏸ Pause ]                                    [ 🗑 Cancel ]│  ← SpaceBetween
└──────────────────────────────────────────────────────────────┘
```

---

### 4. Home Screen — Search Result Card (VideoResultCard)

Used in: **Home → after searching/pasting a video URL**

```
┌──────────────────────────────────────────────────────────────┐
│  ┌──────────────────────────────────────────────────────────┐│
│  │                                                          ││
│  │              [ THUMBNAIL IMAGE ]                         ││  ← 200dp height
│  │                                                          ││
│  │                                               3:42:15   ││
│  └──────────────────────────────────────────────────────────┘│
│                                                              │
│  How to Build a Mass Spectrometer in Your Garage Us...       │  ← TAPPABLE title
│  Veritasium • 1y 5mo                                        │
│                                                              │
│  ┌────────────────────────────────────────────────────┐  ❤  │  ← red fav icon
│  │              [ Select Quality ]                    │      │
│  └────────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────────┘
```

---

### 5. Home Screen — Link History Item

Used in: **Home → bottom section showing previously pasted URLs**

```
┌──────────────────────────────────────────────────────────────┐
│                                                          🗑  │
│  How to Build a Mass Spectrometer...                         │  ← title
│  https://youtube.com/watch?v=dQw4w9WgXcQ                    │  ← URL in primary
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

### 6. Library → Playlists / Channels Tab (Subscription Card)

Used in: **Library → Playlists tab · Library → Channels tab**

> Grid/list toggle available. Same toggle button as other Library tabs.

**List mode (default):**
```
┌──────────────────────────────────────────────────────────────┐
│  ┌────┐                                                      │
│  │ 🖼 │  Veritasium                         [ Unsubscribe ] │  ← circle avatar
│  │    │  (Tappable Title Expand)                    Auto [⬚] │     56dp
│  └────┘                                                      │
└──────────────────────────────────────────────────────────────┘
...
  *Automatically downloads new uploads*                          ← italic text at bottom
```

**Grid mode (2 columns):**
```
┌─────────────────────┐  ┌─────────────────────┐
│      ┌────┐         │  │      ┌────┐         │
│      │ 🖼 │         │  │      │ 🖼 │         │
│      └────┘         │  │      └────┘         │
│    Veritasium       │  │    3Blue1Brown      │
│  [ Unsubscribe ]    │  │  [ Unsubscribe ]    │
│         Auto [⬚]    │  │         Auto [☑]    │
└─────────────────────┘  └─────────────────────┘
...
  *Automatically downloads new uploads*                          ← italic text at bottom
```

---

### 7. Library → Folders Tab — Folder Row

Used in: **Library → Folders tab (folder list)**

```
┌──────────────────────────────────────────────────────────────┐
│  📁  Music Videos                                   ✏️  🗑   │
│      12 items • 1.2 GB                                       │
└──────────────────────────────────────────────────────────────┘
```

---

### 8. Settings Screen — Section Cards

Used in: **Settings → VPS Sync, Download Path, Backup, Repair, About, Updates**

```
  VPS Sync                                     ← section title (not in card)

┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  VPS Server URL                                              │
│  ┌──────────────────────────────────────────────────────────┐│
│  │ https://your-vps-ip:8000                                 ││  ← OutlinedTextField
│  └──────────────────────────────────────────────────────────┘│
│                                                              │
│  API Key                                                     │
│  ┌──────────────────────────────────────────────────────────┐│
│  │ ••••••••••••                                             ││
│  └──────────────────────────────────────────────────────────┘│
│                                                              │
│  [ Test Connection ]    [ Sync Now ]                         │
│                                                              │
└──────────────────────────────────────────────────────────────┘

  Download Settings

┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  Download Path: /storage/emulated/0/MediaNest                │
│  [ Change Path ]                                             │
│                                                              │
│  Preferred Quality: 1080p                                    │
│  ...                                                         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

> Inner status cards (backup success, error, repair result) **keep** their coloured backgrounds:

```
┌──────────────────────────────────────────────────────────────┐  ← glassmorphism outer
│  Backup & Restore                                            │
│                                                              │
│  [ Create Backup ]    [ Restore ]                            │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ ✅ Backup completed successfully          (green bg) │    │  ← keeps primaryContainer
│  └──────────────────────────────────────────────────────┘    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

### 9. Video Detail Screen — Top Bar Favourite

Used in: **VideoDetailScreen top app bar**

```
┌──────────────────────────────────────────────────────────────┐
│  ← Back    How to Build a Mass Spectro...              ❤    │
│                                                   (RED fill) │
└──────────────────────────────────────────────────────────────┘
│                                                              │
│  (rest of video detail content unchanged)                    │
```

---

### 10. MiniPlayer (UNCHANGED)

Kept as-is with `primaryContainer.copy(alpha = 0.95f)` + coloured border:

```
┌══════════════════════════════════════════════════════════════┐  ← primary border
║ ┌────┐  How to Build a Mass S...              ▶   ✕        ║     (distinct design,
║ │ 🖼 │  Veritasium                                          ║      NOT glassmorphism)
║ └────┘                                                      ║
║ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ ║
└══════════════════════════════════════════════════════════════┘
```

---

### Time Format Examples

| Raw Date / Timestamp | Abbreviated (default) | Exact (tap to toggle) |
|---|---|---|
| `2024-12-25T10:30:00` | `1y 6mo` | `1 year, 6 months, 1 day ago` |
| `2026-06-20T14:00:00` | `5d 10h` | `5 days, 10 hours ago` |
| `2026-06-25T23:50:00` | `22m` | `22 minutes ago` |
| `2026-06-25T23:59:50` | `just now` | `just now` |
| `2025-01-15` | `1y 5mo` | `1 year, 5 months, 11 days ago` |

---

### Full Tab Overviews

#### Library → History Tab
```
┌─ Search: [________________________] ─┐
│                                       │
│  History  Folders  Favorites  ...     │  ← selected tab pill
│                                       │
│  ┌─ UnifiedVideoCard (grid) ─────┐   │
│  │  [THUMB]  ✅          2:15:00 │   │  ← glassmorphism
│  │  ▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░ │   │
│  │  React Full Course 2025...    │   │
│  │  Fireship • 3mo 2d            │   │
│  │     ❤     📂     ⬇           │   │
│  └───────────────────────────────┘   │
│                                       │
│  ┌─ UnifiedVideoCard (grid) ─────┐   │
│  │  [THUMB]              0:12:34 │   │
│  │  Understanding Quantum...     │   │
│  │  3Blue1Brown • 2y 1mo         │   │
│  │     ♡     📂     ⬇           │   │
│  └───────────────────────────────┘   │
│  ...                                  │
└───────────────────────────────────────┘
```

#### Library → Favorites Tab
```
│  History  Folders  [Favorites]  ...   │
│                                       │
│  ┌─ UnifiedVideoCard (grid) ─────┐   │
│  │  [THUMB]  ✅          2:15:00 │   │
│  │  React Full Course 2025...    │   │
│  │  Fireship • 3mo 2d            │   │
│  │     ❤     📂     ⬇           │   │  ← ❤ is RED (always, since
│  └───────────────────────────────┘   │     this tab only has favourites)
```

#### Downloads Tab
```
│  All  Completed  In Progress          │
│                                       │
│  ┌───────────────────────────────────┐│
│  │ [THUMB ✅ 245.3MB]  Title...      ││  ← green tick + size on thumb
│  │ [          3:42:15]  VIDEO 1080p  ││
│  │ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  ││  ← no "Completed" text
│  │ [▶ Play]  [♫ Extract]  [🗑 Del]  ││  ← evenly spaced
│  └───────────────────────────────────┘│
```

#### Library → Playlists Tab (grid mode)
```
│  History  Folders  Favorites [Playlists]│  ← toggle: ☰/▦
│                                         │
│  ┌─────────────┐  ┌─────────────┐      │
│  │    ┌────┐   │  │    ┌────┐   │      │  ← 2-column grid
│  │    │ 🖼 │   │  │    │ 🖼 │   │      │
│  │    └────┘   │  │    └────┘   │      │
│  │  Veritasium │  │  3Blue1Br.. │      │
│  │ [Unsub] [⬚] │  │ [Unsub] [☑] │      │
│  └─────────────┘  └─────────────┘      │
│                                         │
│   *Automatically downloads new uploads* │
```

#### Library → Channels Tab (list mode)
```
│  History  Folders  Favorites  ... [Channels]│  ← toggle: ☰/▦
│                                              │
│  ┌──────────────────────────────────────────┐│
│  │  🖼  Veritasium          [Unsub] [⬚]    ││  ← list row
│  │                                          ││
│  └──────────────────────────────────────────┘│
│  ┌──────────────────────────────────────────┐│
│  │  🖼  3Blue1Brown         [Unsub] [☑]    ││
│  │                                          ││
│  └──────────────────────────────────────────┘│
│                                              │
│     *Automatically downloads new uploads*    │
```

#### Settings Tab
```
│  VPS Sync                             │
│  ┌─ GlassCard ───────────────────┐   │
│  │  Server URL: [___________]    │   │  ← glassmorphism
│  │  API Key:    [•••••••••••]    │   │
│  │  [Test]  [Sync Now]           │   │
│  └───────────────────────────────┘   │
│                                       │
│  Download Settings                    │
│  ┌─ GlassCard ───────────────────┐   │
│  │  Path: /storage/...           │   │  ← glassmorphism
│  │  [Change Path]                │   │
│  └───────────────────────────────┘   │
│                                       │
│  Backup & Restore                     │
│  ┌─ GlassCard ───────────────────┐   │
│  │  [Create Backup] [Restore]    │   │  ← glassmorphism outer
│  │  ┌─ Success ────────────┐     │   │
│  │  │ ✅ Backup done (green)│     │   │  ← keeps coloured bg
│  │  └──────────────────────┘     │   │
│  └───────────────────────────────┘   │
```
