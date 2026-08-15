# MediaNest design-2 — Session Handover & Resume Context

> Purpose: a complete, self-contained snapshot of this session so a fresh agent
> (or human) can resume the work in a new tab without losing context or
> re-explaining anything. Read this first, then `README.md`.

---

## 1. What this session was about

Build a **complete visual/interaction prototype** of a fully redesigned
**MediaNest** Android app as plain HTML/CSS/JS (zero dependencies) inside
`/mnt/d/dev/media-nest/design-2/`. The prototype must follow the dark-red brand
guidelines, preserve every existing feature, and demonstrate all interaction
states (downloads, playback, autoplay, sorting, history reload, notifications).

The prototype is a **reference for a future Android implementation**, not the
app itself. It exists so an implementer can point at it and know exactly what
to build.

---

## 2. Absolute rules (never violate)

- **DO NOT touch `/design-1/`** — read, write, or delete. It is out of scope and
  another process may be working in it. All work is under `/design-2/` only.
- **No raw `.innerHTML` / `.outerHTML` / `.insertAdjacentHTML`** (blocking lint).
- **Build HTML with single-quote concatenation** (`'<div>' + x + '</div>'`),
  never a template literal that starts with `` `< `` (formatter corrupts it).
- Escape data with `MN.esc()`; icons via `MN.icon()`.
- Keep **≤4 primary bottom tabs** (Home, Collections, Downloads, Settings).
- Every media card/row shows an **audio/video icon badge** (`MN.typeBadge`),
  not a text `AUDIO`/`VIDEO` label. Audio items keep their thumbnail as the image.
- **Watched-progress** bar is **YouTube Red** (`#D21F31`), not the pink accent.

---

## 3. Final file tree

```text
design-2/
  index.html                  # App shell + inline icon sprite + script wiring
  css/
    tokens.css                # Brand palette & scale (single source of truth)
    base.css                  # Reset, primitives, phone frame
    components.css            # Shared component system
    screens.css               # Per-screen layout helpers
  js/
    data.js                   # Mock data (mirrors real Room entities)
    app.js                    # Runtime: router, store, overlays, playback + downloads
    screens-home.js           # Home (URL extraction, link history, bulk download)
    screens-collections.js    # Collections (history/watched/folders/favorites/playlists/channels)
    screens-downloads.js      # Downloads (queue, progress, statuses, extract audio)
    screens-settings.js       # Settings (sync, downloads, backup/repair, updates)
    screens-player.js         # Player (seek, transport, autoplay, speed, quality, queue)
    screens-video-detail.js   # Video detail (hero, metadata, stats, downloads, streams)
    screens-statistics.js     # Statistics (thorough analytics)
  README.md                   # Why it exists, run guide, DB schema, screen contract & global API
  HANDOVER.md                 # This file (handover context, notification audit & opportunities)
```

---

## 4. Key decisions made this session

1. **Parent writes foundation first, then fans out screen writers.** One
   coherent design system (tokens/components/runtime) is authored by the parent;
   screen modules are written by parallel `worker` subagents against `README.md`
   (`## Screen Module Contract & Global API`).
2. **`MN` global API** (`window.MN`) is the shared contract every screen uses:
   `render`, `icon`, `esc`, `h`, `qs`, `stateView`, `typeBadge`, `isAudioTrack`,
   `linkKind`, `refreshAppbar`, `history.{copy,remove,clear}`, `fmt.*`,
   `router.*`, `toast`, `sheet/closeSheet`, `dialog/closeDialog`, `store.*`,
   `playback.*`, `downloads.*`, `notify`, `boot`.
3. **Navigation model:** 4 tabs (`home`, `collections`, `downloads`, `settings`);
   secondary routes `video-detail/:id`, `player`, `player-offline`,
   `statistics`, `notifications`. Mini-player hides on player routes.
4. **Phone-frame on desktop:** `#app` is constrained to `max-width: 420px` in a
   centered bezel so it always looks mobile; on ≤480px viewports it goes
   full-bleed (no frame).
5. **Clicking a video title/card opens the Video Detail page** (not the player).
   From there the user can play, view description, per-video stats, downloads,
   and the stream ladder. This was a late, deliberate UX decision.
6. **Collections tabs are NOT sticky** (they scroll with content); **no global
   search** — each tab has its own scoped search field.
7. **Card actions collapsed into a three-dot menu** (favorite/download/move/
   watch live inside the dropdown sheet) to give headings more horizontal space.
8. **List/grid toggle** (square header icon) works across the *whole* Collections
   tab and re-renders + refreshes the appbar.
9. **Sort menus show icon + text** and the current sort is shown with its icon.
10. **History rows**: type-identifier icon (video/playlist/channel), one-tap
    load (re-extracts), per-item delete confirmation, and Clear-all button.
11. **Player**: scrollable controls (stage stays visible), audio art +
    equalizer, full transport + autoplay + speed + quality + resume, and a
    **Queue/Up Next** sheet.
12. **Statistics** screen is thorough (engagement, storage, download health,
    watch metrics, top content, resolution/channel/folder breakdowns).
13. **Feature Comparison & Android Migration Roadmap Index:** Created an exhaustive matrix and roadmap mapping between the native Android App and the design-2 prototype in `README.md` (and summarized here in section 7). This records ExoPlayer, SAF, yt-dlp, and WorkManager features kept in Android, plus prototype UI enhancements, Room DB schema additions (`mediaType`, `linkType`), 365-day notification pruning rules, and DataStore preferences (`defaultResolution: '360p'`, `showShorts: false`, per-tab sort/grid state persistence).

---

## 5. What's verified / done

- All 9 JS files pass `node -c` (syntax valid).
- Zero **blocking** lint errors; only non-blocking `jscpd`/`slop` style hints
  (spurious duplicate-text detections and minor style suggestions) remain.
- All 8 routes registered correctly: `home`, `collections`, `downloads`,
  `settings`, `player`, `player-offline`, `statistics`, `video-detail/:id`
  (plus inline `notifications` in `app.js`).
- No forbidden patterns in screen files (no innerHTML, no backtick-`<`
  template literals, no `console.log`).
- `/design-1/` was never touched.

---

## 6. Mock data shapes (MN.DATA)

- `videos` — `{id, title, channelName, channelId, durationSeconds, thumbnailUrl,
  description, uploadDate, localFilePath, favorite, addedAt, lastPlayedAt,
  downloadedAt, watchCount, resolution}`. `resolution === 'Audio'` = audio-only.
- `playlistVideos`, `playlist`, `channel`.
- `subscriptions` — `{id, sourceType:'channel'|'playlist', sourceId, name,
  thumbnailUrl, autoDownload, audioOnly}`.
- `folders` — `{id, name, parentId, createdAt}`; `videoFolderMap` — `{videoId: folder[]}`.
- `downloads` — `{id, videoId, title, thumbnailUrl, format, quality, status,
  progress, fileSizeBytes, downloadedBytes, speedBytesPerSec, elapsedMs,
  remainingMs, filePath, downloadedAt, errorMessage}`.
  `status`: `QUEUED|DOWNLOADING|PAUSED|COMPLETED|FAILED|CANCELED`.
  `format`: `video|video_only|audio|audio_extracted`.
- `history` — `{videoId, positionMillis, playedAt, totalWatchTimeMillis}`.
- `watchSessions` — `{videoId, watchedAt}`.
- `linkHistory` — `{url, title, extractedAt}`.
- `notifications` — `{id, type, title, desc, time, channel}`.

---

## 7. Database / schema additions & Migration Index (for the real Android implementation)

Documented in detail in `README.md` under `## Feature Comparison & Android Migration Roadmap`. Summary for instant reference:

### Room DB Schema Additions

- **`VideoEntity.mediaType`** (`"VIDEO"` | `"AUDIO"`) — Explicitly differentiates audio vs video media across all cards/views.
- **`LinkHistoryEntity.linkType`** (`"VIDEO"` | `"PLAYLIST"` | `"CHANNEL"` | `"UNKNOWN"`) — Populated during extraction to render correct history icons and support one-tap re-extraction.
- **`SubscriptionEntity.audioOnly`** (Boolean) — Exposed via explicit UI toggle on subscription cards alongside `autoDownload`.
- **Notification Retention Pruning Rule** — Enforce automatic deletion of `NotificationEntity` records older than 365 days.

### DataStore Preferences Inventory

- **`DownloadPreferences.defaultResolution`** — String preference, defaulting to `"360p"` (options: `"1080p"`, `"720p"`, `"480p"`, `"360p"`, `"Audio"`).
- **`SubscriptionsPreferences.showShorts`** — Boolean preference, defaulting to `false` (hides Shorts until explicitly toggled ON).
- **`CollectionsPreferences.viewMode`** — String preference, `"GRID"` | `"LIST"`, persisting header toggle across Collections tabs.
- **`CollectionsPreferences.sortMode_<tab>`** — Per-tab sorting state preserving category and direction (`DATE_DESC`, `NAME_ASC`, etc.).
- **`DownloadsPreferences.sortMode`** — Queue sorting state (`DATE_DESC`, `PROGRESS_DESC`, `SIZE_DESC`, `STATUS_ASC`).

### Migration Index Summary

- **Android App Native Strengths (Preserved in Migration):** ExoPlayer gesture controls & surface rendering, SAF custom folder authorization, WorkManager background jobs, native yt-dlp binary extraction pipeline, file relocation logic, native system notifications, DB backup/restore ZIP exports, VPS REST Sync.
- **Prototype UI/UX Improvements (To Implement in Android):** Audio/Video icon badges (`MN.typeBadge`), 4-tab shell with consolidated 6-subtab Collections hub, single-category toggle sorting with directional arrows (`↓`/`↑`), universal 10-item lazy loading & end-of-list visual indicators, floating extract button, audio-only subscription toggle, default 360p download resolution, queue drag-to-reorder, statistics analytics dashboard, time-based watched threshold (remaining ≤ 1 min), and link history type icons with one-tap reload.

Real entity files: `app/src/main/java/com/example/medianest/data/local/entity/*.kt`
(`VideoEntity`, `DownloadEntity`, `FolderEntity`, `HistoryEntity`,
`LinkHistoryEntity`, `PlaylistEntity`, `SubscriptionEntity`,
`WatchSessionEntity`).

---

## 8. Build / verification commands

```text
# syntax-check every JS file
cd /mnt/d/dev/media-nest
for f in design-2/js/*.js; do node -c "$f" || echo "FAIL: $f"; done

# serve the prototype
cd design-2 && python3 -m http.server 8080   # open http://localhost:8080
```

---

## 9. How to resume in a new tab (handover prompt)

Copy everything below this line into the new session's first message:

---

> Resume the **MediaNest design-2 prototype** work in `/mnt/d/dev/media-nest/design-2/`.
>
> **Context:** This is a plain HTML/CSS/JS visual & interaction prototype (zero
> dependencies) for a redesigned MediaNest Android app, following the dark-red
> brand guidelines. It is a reference for a future Android implementation — not
> the app itself.
>
> **First, read these files in order:**
>
> 1. `/mnt/d/dev/media-nest/design-2/HANDOVER.md`
> 2. `/mnt/d/dev/media-nest/design-2/README.md`
> 3. [`/mnt/d/dev/media-nest/MEDIANEST_BRAND_GUIDELINES.md`](../MEDIANEST_BRAND_GUIDELINES.md)
>
> **Hard rules:**
>
> - NEVER touch `/design-1/` (read/write/delete). All work is under `/design-2/`.
> - No `.innerHTML`/`.outerHTML`/`.insertAdjacentHTML` (blocking lint). Use
>   `MN.render(el, html)`.
> - Build HTML with single-quote concatenation, never a template literal starting
>   with `` `< ``.
> - Escape data with `MN.esc()`; icons via `MN.icon()`.
> - ≤4 primary tabs. Audio/video icon badge on every card (`MN.typeBadge`).
>   Watched-progress = YouTube Red `#D21F31`.
>
> **Current state:** all 9 JS files pass `node -c`; no blocking lint errors; all
> routes wired; prototype functional. The last session ended after fixing:
> phone-frame on desktop, history one-tap load + type icons + clear-all + delete
> confirm, audio/video icon badges, non-sticky collections tabs, per-tab search,
> three-dot-only card menus, YouTube-red watched progress, sort icons, per-tab
> stats, working list/grid toggle, reachable video-detail page (with downloads
> list + title toggle), and a redesigned scrollable player with queue/autoplay.
>
> **My request now:** <STATE YOUR NEW TASK HERE — e.g. "review the prototype for
> any remaining UX gaps", "add feature X", or "begin mapping the design to the
> Android/Compose implementation".>
>
> **Reminder (per HANDOVER.md section 10):** on every new session, ask whether
> to implement the time-based watched logic now or keep it deferred.
>
> Please begin by confirming you have read the handover files, then proceed.

---

## 10. Deferred logic change (DO NOT implement in the prototype yet)

> **Standing instruction for every future session:** the user has asked to be
> prompted about this item **each time a new session starts** so it is never
> forgotten. When resuming this work, ask: *"Should we implement the
> time-based watched logic now, or leave it deferred for the real Android
> implementation?"* Do not silently implement it.

**The issue (in the current/real app):** watched-video detection is currently
computed on a **percentage** basis. This is wrong, because a 1–2% watch of a
3-hour video and a 20-hour video represent very different absolute lengths of
time remaining.

**The intended fix (for the future Android implementation):** switch the
watched calculation from percentage-based to **time-based** — mark a video or
audio item as **watched when its REMAINING time is ≤ 1 minute** (instead of any
percentage threshold). This keeps the behavior consistent regardless of total
length.

### Notification Requirements (for Real Android Implementation)

- **Notification Retention Limit:** Keep notifications for a maximum of 1 year (automatically prune DB notifications older than 365 days).
- **Dynamic Infinite Scroll Loading:** Notifications must be loaded dynamically on scroll as the user scrolls down (infinite scroll loading, NO page numbers or manual pagination UI), loading notification records lazily from Room DB into memory.

### Universal Lazy Loading Requirements (for Real Android Implementation)

- **Universal Dynamic Lazy Loading:** EVERY list and grid in the app (Home results, Link History, Collections [History, Watched, Folders, Favorites, Playlists, Channels], Downloads queue, Continue Watching horizontal row) MUST use dynamic lazy loading (Compose `LazyColumn`/`LazyVerticalGrid`/`LazyRow` / Paging3) with an initial batch size of 10 items (never rendering more than 10–20 items in memory at once), lazily fetching additional batches as the user scrolls.
- **End-of-List Indicator:** EVERY dynamically loaded list and grid in the app (Home results, Link History, Collections tabs [History, Watched, Folders, Favorites, Playlists, Channels], Downloads queue, Notifications) MUST display a clear end-of-list notification at the bottom when all items have been fetched (e.g. '• You have reached the end of the list •') so the user knows all content is loaded.

### Download Resolution Preference Requirements (for Real Android Implementation)

- **Default Download Resolution Preference:** `DownloadPreferences.defaultResolution` (String, default `'360p'`). Stores the user's preferred download resolution setting (1080p, 720p, 480p, 360p, Audio).

### Sorting & Duration Requirements (for Real Android Implementation)

- **Playlist & Channel Duration & Sorting:** Playlists and Channels compute total duration and support item sorting by `durationSeconds` (longest/shortest), `uploadDate` (newest/oldest), and `title` (A-Z).
- **Single-Category Toggle Sort UI:** Sort menus display only ONE entry per category (Date, Name, Duration, Size, Progress, Status). Selecting an already-active category toggles its direction between descending (`↓` arrow-down) and ascending (`↑` arrow-up), eliminating redundant list items.

### Subscription Requirements (for Real Android Implementation)

- **Audio-Only Auto-Download UI Toggle:** In `SubscriptionsScreen.kt`, expose `SubscriptionEntity.audioOnly` as an explicit `Audio-only` switch alongside the `Auto-download` switch on subscription cards (and update `SubscriptionsViewModel.updateAutoDownload`), allowing users to toggle auto-downloading audio-only tracks for specific channels/playlists.
- **Show Shorts Default OFF:** In `HomeViewModel` and `SubscriptionsViewModel`, the `showShorts` filter toggle MUST default to `false` (OFF) whenever a playlist or channel is loaded/selected, hiding Shorts videos until the user explicitly toggles the switch ON.

This is a *product/logic change to the real app*, not the design-2 prototype,
so it is deliberately **not** implemented here.

---

## 11. Session trivia / gotchas (worth remembering)

- The first fanout attempt (nested `worker` children) detached and wrote nothing;
  the fix was to launch a single `workflowScript` with `runs.all([...])` and
  `await` it, then block with `subagent_wait({ all: true })`.
- The settings worker once produced CSS into its output log instead of its JS
  file; after it finished, the correct `screens-settings.js` landed.
- `MN.refreshAppbar()` exists to re-render appbar actions after in-screen state
  changes (e.g. the list/grid toggle).
- The `.mn-player__queue-item` style was briefly duplicated across
  `components.css` and `screens.css`; it now lives only in `components.css`.
- The `.mn-tag--yt` class uses `--mn-youtube` (#D21F31); it is defined but not
  currently referenced by any screen.

---

## 12. Notification Audit & Opportunities

This documents the app's **existing** notification behavior (from the Android
source) and identifies **missing** notification opportunities relevant to the
actual feature set. The HTML prototype *demonstrates* notification UI (bell icon
→ Notifications screen) but does not implement real Android notifications.

### Existing notifications (already implemented in the app)

Found in `app/src/main/java/com/example/medianest/`:

| Channel / source | ID | Event |
| --- | --- | --- |
| `downloads` | `DownloadService` | Foreground-service progress while downloads are active (IMPORTANCE_LOW). Cancelled when queue drains. |
| `bulk_downloads` | `BulkDownloadPreparationWorker` | Bulk download "ready" (N downloadable, total size) and "failed" status notifications. |
| `app_updates` | `UpdateCheckWorker` | "Update available" notification (version + changelog). |
| `app_updates` | `UpdateDownloadWorker` | Update APK download completion and download-error notifications. |

These are foreground/status notifications required for reliability (download
progress, bulk prep, app updates).

### Missing notification opportunities (proposed, not yet in app)

Derived from the existing feature set — these are *new concepts*, kept distinct
from what already exists:

1. **Download completion** — when a single download finishes (the service today
   shows aggregate progress but no clear "X is ready" event).
2. **Auto-download pickup** — when a subscription's `autoDownload` check finds
   and queues new uploads (`SubscriptionWorker` runs silently).
3. **New uploads available** — when a followed channel/playlist has new content,
   even when auto-download is off.
4. **Sync completed / sync failed** — `SyncManager`/`SyncWorker` currently run
   without a user-facing completion event.
5. **Auto-backup completed** — `AutoBackupWorker` runs on a schedule with no
   completion signal.
6. **Storage low before bulk download** — bulk download already computes usable
   space; a warning when space is insufficient would be valuable.
7. **Playback "continue watching"** — optional, low priority: a resume shortcut
   for long-paused media.

### How the prototype represents this

The prototype's Notifications screen shows a mix of **existing** events
(download progress/complete, new uploads) and **proposed** events (sync
complete, auto-download queued), each tagged with its channel. This is a visual
illustration of how the notification surface would look — not a claim that all
of these are implemented today.

---

## 13. Design Changes Overview — What Must Be Ported Into The App

> **Purpose:** a scannable summary of the **design-level changes** made in the
> `design-2` prototype versus the existing Android app, so a future session can
> pick up the work and know *what was changed and what needs to be connected*.
> It is intentionally high-level — it lists *themes* (spacing, layout, card
> system, navigation), not every individual tweak. See `README.md` →
> `## Feature Comparison & Android Migration Roadmap` for the feature-level
> matrix and exact Room/DataStore schema items.

### 1. Navigation & Screen Structure

- **4 bottom tabs** (`Home`, `Collections`, `Downloads`, `Settings`) instead of
  the app's current split (`Home`, `Downloads`, `Library`, `Settings`).
- **Collections becomes a single consolidated hub** with 6 non-sticky sub-tabs:
  `History`, `Watched`, `Folders`, `Favorites`, `Playlists`, `Channels`.
- **Playlists & Channels open inline inside Collections** (full-width banner +
  video list + back button), not by navigating to Home.
- **Statistics is reachable from both** Settings and the Collections appbar
  (chart icon).
- **Clicking a media card/title opens Video Detail** (not the player directly).

### 2. Visual System & Consistency

- **Unified card system**: every list row / grid card / download card / stream
  row / queue item shares the same radius, glass surface, border, and padding —
  only the content inside changes. This is the single biggest consistency
  change to port.
- **Compact sizing everywhere**: smaller buttons (less padding), smaller
  thumbnail overlay badges (runtime, audio/video icon, download icon), tighter
  stream-row spacing.
- **Audio/video identity** is shown as a compact icon badge on every card
  (not a text `AUDIO`/`VIDEO` label).
- **Watched-progress bar is YouTube Red** (`#D21F31`), distinct from the accent
  pink; download progress keeps separate video (pink) vs audio (orange) vs
  merge (green) colors.
- **Cards use a three-dot (⋮) menu** at the bottom-right for secondary actions,
  giving titles full width instead of crowding inline icon buttons.

### 3. Layout & Spacing Themes

- **Full-width playlist/channel banner** on top, with title/description/buttons
  stacked below (no side-by-side thumbnail + text).
- **Folder badges under thumbnails** are single-line, ellipsized, with a
  clickable `+N` that opens the folder list.
- **View count moved onto the thumbnail** (bottom-left overlay) instead of
  wrapping onto a third text line.
- **Buttons/controls in cards collapse into one compact icon row** with
  tooltips, not stacked text buttons.

### 4. Interaction & List Behavior (must connect to Compose lazy lists)

- **Universal 10-item lazy loading** with scroll-based fetching across all
  lists/grids (Home, Collections tabs, Downloads, Notifications, Continue
  Watching).
- **End-of-list indicator** (`• You have reached the end of the list •`) on
  every dynamically loaded list.
- **Single-category toggle sorting** (`Date`, `Name`, `Duration`; Downloads
  adds `Progress`, `Size`, `Status`) — re-tapping the active category toggles
  ascending/descending with `↓`/`↑` arrows.
- **Title tap toggles expand/collapse** of the 2-line clamp instead of
  navigating.
- **Link history**: tap row = copy URL; dedicated play button = re-extract;
  trash = delete; Clear-all present.
- **Player**: fullscreen button, segmented options row (speed/quality/queue),
  autoplay inline, resume-from-position, and drag-to-reorder Up Next queue with
  index/"Now playing"/count.

### 5. Settings & Defaults (must connect to DataStore/preferences)

- **Default download resolution = `360p`** (selectable: `1080p`/`720p`/`480p`/
  `360p`/`Audio`). Three-dot "Download" uses this default.
- **Max concurrent downloads = `2`**.
- **Audio-only auto-download toggle** exposed per subscription (next to
  Auto-download).
- **Show Shorts defaults to OFF** for playlists/channels.
- **Notifications**: 10 at a time, infinite scroll, prune older than 365 days.

### 6. Content Gaps To Wire Into The App

- **Public vs personal view counts** on Video Detail (public `publicViews` +
  personal `watchCount`).
- **Downloaded-versions list** showing every completed resolution/size.
- **Last-watch-position** shown as a thumbnail overlay + stat row.
- **Descriptions on playlist/channel headers**.
- **Statistics dashboard** expanded with ratios, storage, download health,
  watch metrics, link extractions, and breakdowns.
