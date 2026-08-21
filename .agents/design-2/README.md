# MediaNest — design-2 Prototype

## Why this folder exists (read this first)

This folder is a **visual and interaction prototype** for the redesigned
MediaNest Android app. It is *not* the app itself and contains *no* Android
code. It exists for two reasons:

1. **To lock in the look & feel** — the dark-red brand palette, type scale,
   glassmorphism surfaces, spacing, radius, and component language — as an
   exact, inspectable reference *before* the Android UI is rebuilt.
2. **To verify every interaction and feature state** — how downloads progress,
   how the player works (seek, speed, quality, autoplay, queue), how sorting,
   list/grid toggling, history reloading, deletion confirmations, sheets,
   dialogs, toasts, and notifications *behave* — so an implementer can point at
   this prototype and know exactly what to build.

Everything is **plain HTML + CSS + JavaScript with zero dependencies** so it can
be opened instantly in any browser, on any machine, without a build step. It is
a single-page app that simulates a phone screen.

> **Hand-off rule:** if you point another agent at this folder, tell it to read
> `README.md` and `HANDOVER.md`. Those two files are self-explanatory and
> describe the whole structure and every convention, so you should not need to
> re-explain the project each time.

---

## Run it

Serve the folder and open `index.html`, or just open `index.html` directly:

```text
cd design-2
python3 -m http.server 8080   # then visit http://localhost:8080
```

`index.html` loads the CSS, the icon sprite, and the JS modules, then calls
`MN.boot()` on `DOMContentLoaded`.

**Phone-frame note:** on a desktop the app is constrained to a phone-shaped
column (`max-width: 420px`, rounded bezel, centered on a dark backdrop) so it
*always* looks like a mobile view — it does not stretch full-screen. On a real
phone (viewport ≤ 480px) the frame disappears and the app fills the screen.

---

## File structure

```
design-2/
  index.html                  # App shell + inline icon sprite + script wiring
  css/
    tokens.css                # Single source of truth: brand palette & scale
    base.css                  # Reset, primitives, phone frame
    components.css            # Shared component system (the design system)
    screens.css               # Per-screen layout helpers
  js/
    data.js                   # Mock data (mirrors the real Room entities)
    app.js                    # Runtime: router, store, overlays, playback + download simulation
    screens-home.js           # Home (URL extraction, results, link history, bulk download)
    screens-collections.js    # Collections (history/watched/folders/favorites/playlists/channels)
    screens-downloads.js      # Downloads (queue, progress, statuses, extract audio)
    screens-settings.js       # Settings (sync, downloads, backup/repair, updates)
    screens-player.js         # Player (seek, transport, autoplay, speed, quality, queue)
    screens-video-detail.js   # Video detail (hero, metadata, stats, downloads, streams)
    screens-statistics.js     # Statistics (thorough usage analytics)
  README.md                   # Why it exists, architecture, screen module contract & global API, DB schema
  HANDOVER.md                 # Session handover, resume context, notification audit & opportunities
```

---

## Navigation

Four primary tabs (bottom navigation, Android-first):

| Tab | Route | Contains |
| --- | --- | --- |
| Home | `home` | URL extraction, playlist/channel results, link history (one-tap load), bulk download |
| Collections | `collections` | History, Watched, Folders, Favorites, Playlists, Channels, continue-watching |
| Downloads | `downloads` | Download queue, resume-watching, progress & statuses |
| Settings | `settings` | VPS sync, downloads, data management, updates, notifications |

Secondary routes: `video-detail/:id`, `player`, `player-offline`,
`statistics`, `notifications`.

**Clicking a video title/card** now opens the **Video Detail** page (not the
player directly). From there you can play, see the description, per-video
statistics, existing downloads, and the available stream ladder.

---

## Brand compliance

The visual identity is a strict translation of
[`MEDIANEST_BRAND_GUIDELINES.md`](../MEDIANEST_BRAND_GUIDELINES.md):

- **Palette** — every color is a CSS custom property in `css/tokens.css`
  (`--mn-*`), mapped 1:1 from the brand `MediaNestColors` tokens. No raw hex
  values appear in component rules.
- **Typography** — Inter (falling back to Roboto/system-ui), using the brand
  scale (display 28px → section 17px → card 14px → metadata 11.5px).
- **Radius / spacing** — 12–14px cards, 16px hero panels, 16px screen padding,
  72px bottom nav, 44px touch targets, 16:9 thumbnails.
- **Dark aesthetic** — restrained chrome, tasteful glassmorphism on repeated
  surfaces (`--mn-glass`), no decorative gradients on controls.
- **Watched-progress bar** — uses **YouTube Red** (`--mn-progress-watched`,
  `#D21F31`), per the brand guide, not the pink accent color. This applies to
  the "how much have I watched" progress strip on cards/thumbnails.

---

## Interaction notes (what the prototype demonstrates)

The prototype simulates (no real backend):

- **Download** — enqueues a mock item that visibly progresses, then completes
  with a toast + notification.
- **Playback** — play/pause/next/prev update the mini-player and full player;
  seeking, speed, and quality change visible state.
- **Autoplay** — toggleable; when a track ends the queue auto-advances.
- **Sorting** — media lists offer Date asc/desc, Name, Duration; Downloads adds
  Progress/Size/Status. Sort menus show **icon + text**.
- **Link history** — each entry shows a **type identifier icon** (video /
  playlist / channel), supports **one-tap load** (re-extracts the link exactly
  like pasting it), per-item **delete confirmation**, and a **Clear all** button.
- **Audio/video identity** — every media card/row shows a compact **audio or
  video icon badge** (not a text label). Audio items keep their thumbnail as the
  card image.
- **List/grid toggle** — the square header icon on Collections toggles list vs
  grid across the *entire* Collections tab.
- **Per-tab search** — Collections has no global search bar; each tab has its
  own scoped search field.
- **Collection tabs are not sticky** — they scroll with the content.
- **Statistics** — Settings → App Statistics opens a thorough analytics screen
  (engagement, storage, download health, watch metrics, top content, and
  breakdowns by resolution / channel / folder).
- **Notifications** — a bell icon opens the Notifications screen.

---

## Screen Module Contract & Global API

This section is the single source of truth for screen modules under `js/`. Read this fully before writing or editing screen code.

### Hard lint rules (blocking — will fail review)

1. Build HTML with **single-quote concatenation** only:
   `'<div class="x">' + MN.esc(v.title) + '</div>'`.
   - NEVER start a template literal with `` `< `` (the formatter misparses it as JSX and corrupts the file).
2. NEVER use `.innerHTML`, `.outerHTML`, or `.insertAdjacentHTML`.
   - Inject markup with `MN.render(el, html)`.
   - Set plain text with `el.textContent = ...`.
3. Escape all dynamic/user data with `MN.esc(...)`.
4. Use `MN.icon('name')` for every icon — do not hand-write raw `<svg>`.
5. You may ONLY write your assigned file. Do not touch `index.html`, `app.js`, `data.js`, any `css/*`, or anything under `/design-1/`.
6. No `console.log`. No `.reverse()` (use `slice().reverse()` or `toReversed`). No nested ternaries. Use `.includes()` not `.indexOf()` for existence. Use `Number.isNaN` not `isNaN`. Use `[].at(-1)` not `[length-1]`.

### Global API (window.MN)

- `MN.DATA` — mock data (see data shapes in `HANDOVER.md`).
- `MN.render(el, html)` — replace element children from an HTML string.
- `MN.icon(name[, cls])` — returns `<svg class="mn-icon"><use href="#i-NAME"></use></svg>`.
- `MN.esc(s)` — HTML-escape a string.
- `MN.h(tag, cls, html)` — `'<tag class="cls">html</tag>'`.
- `MN.qs(sel[, root])` — `querySelector`.
- `MN.stateView(icon, title, desc)` — empty/loading/error block.
- `MN.typeBadge(v)` — audio/video icon badge (`music`/`video`).
- `MN.isAudioTrack(v)` — true when `v.resolution === 'Audio'` or `v.format` is an audio type.
- `MN.linkKind(url)` — `'video' | 'playlist' | 'channel' | 'unknown'`.
- `MN.refreshAppbar()` — re-render the appbar actions for the current route.
- `MN.fmt.duration(sec)`, `MN.fmt.bytes(b)`, `MN.fmt.speed(bps)`, `MN.fmt.rel(ts)`.
- `MN.router.navigate('name')`, `MN.router.back()`, `MN.router.register('name', {title, back, actions, mount})`, `MN.router.current()` → `{ route, params }`.
- `MN.toast(msg[, type])` — type: `info|success|error`.
- `MN.sheet({title, body, onOpen})`, `MN.closeSheet()`.
- `MN.dialog({title, body, actions, dismissible})`, `MN.closeDialog()`.
- `MN.store.get()`, `MN.store.set(patch)`, `MN.store.subscribe(fn)`. State keys: `playing`, `queue`, `queueIndex`, `isPlaying`, `positionMs`, `durationMs`, `bufferedMs`, `autoplay`, `speed`, `quality`, `isLocal`, `maxConcurrent`, `notifCount`.
- `MN.playback.play(queue, startIndex)`, `.toggle()`, `.next()`, `.prev()`, `.seek(ms)`, `.seekRelative(delta)`, `.setAutoplay(b)`, `.setSpeed(n)`, `.setQuality(q)`.
- `MN.downloads.enqueue(video, format, quality)`, `.pause(dl)`, `.resume(dl)`, `.retry(dl)`, `.cancel(dl)`, `.remove(dl)`, `.onTick(fn)`.
- `MN.notify({type, title, desc, channel})`.
- `MN.history.copy(url)`, `MN.history.remove(url)`, `MN.history.clear()`.

### Route registration

```js
MN.router.register('name', {
  title: 'Screen Title',
  back: false,          // or true for detail/settings sub-screens
  actions: () => html,  // optional appbar actions
  mount: (el, params) => {
    // render into el, wire events
    return { unmount: () => {} }; // optional
  },
});
```

Routes registered across modules:

| Module | Route | back |
| --- | --- | --- |
| `screens-home.js` | `home` | false |
| `screens-collections.js` | `collections` | false |
| `screens-downloads.js` | `downloads` | false |
| `screens-settings.js` | `settings` | false |
| `screens-player.js` | `player` and `player-offline` | false (appbar hidden) |
| `screens-video-detail.js` | `video-detail/:id` | true |
| `screens-statistics.js` | `statistics` | true |

### Cross-cutting requirements

- **Audio/video icon badge** on every media card/row: use `MN.typeBadge(v)` (NOT the old text `AUDIO`/`VIDEO` label). Audio items keep their thumbnail as the card image.
- **Watched-progress bar** must use YouTube-red fill (`--mn-progress-watched`, `#D21F31`).
- Every screen section should expose a small **statistics line** (count / aggregate) describing that tab/section.
- Icons plus text in sort menus and sort display.

---

## Database / schema additions required for the redesign

This section lists what must change in the real Android Room layer so the
features shown in this prototype can be implemented. Read
`app/src/main/java/com/example/medianest/data/local/entity/*.kt` alongside this.

### 1. Media type (audio vs video) on every media item

The prototype shows a **audio/video icon badge on every card** and renders
audio items with their thumbnail as the card image. Today `VideoEntity` has no
explicit "is this audio" field (it is only implied by `resolution == "Audio"`
or by the download's `format`). This is fragile.

**Recommended change:**

- Add to `VideoEntity`:

  ```kotlin
  val mediaType: String = "VIDEO"   // "VIDEO" | "AUDIO"
  ```

  (or a boolean `val isAudio: Boolean = false`). Migrate existing rows by
  deriving from `resolution`/`format` where possible.
- Add a matching `mediaType` to any result/DTO models surfaced to the UI so
  cards can render the icon directly without string-guessing.

`DownloadEntity` already has `format` (`video`, `video_only`, `audio`,
`audio_extracted`), which is sufficient for the Downloads screen, but a
normalized `mediaType` there too would remove ambiguity.

### 2. Link-history type identifier

The Home screen now shows an icon identifying each history entry as a
**video / playlist / channel**, and one-tap load re-runs extraction. Today
`LinkHistoryEntity` only stores `url`, `title`, `extractedAt` — the type is
re-derived from the URL string each time (fragile for channel URLs and
short-links).

**Recommended change:**

- Add to `LinkHistoryEntity`:

  ```kotlin
  val linkType: String = "VIDEO"   // "VIDEO" | "PLAYLIST" | "CHANNEL" | "UNKNOWN"
  ```

  Populate it at extraction time (when the app already knows the kind), not at
  render time.

### 3. (Optional) Persist per-tab UI preferences

The prototype keeps per-tab sort order, per-tab search query, and the global
list/grid view toggle in memory only. If these should survive app restarts,
add a small preferences table (or use DataStore):

- `collections_view_mode` (`LIST` | `GRID`)
- `collections_sort_<tab>` (`DATE_DESC` | `DATE_ASC` | `NAME_ASC` | `DURATION`)
- `downloads_sort` (`DATE_DESC` | `DATE_ASC` | `PROGRESS` | `SIZE` | `STATUS`)

These are *UI preferences*, not business data, so DataStore (Jetpack) is the
more idiomatic home for them than a Room table.

### 4. (Optional) Statistics aggregation

The Statistics screen aggregates many counters (play counts, watch time,
storage by type, resolution/channel/folder breakdowns). No new table is
strictly required — all of this is derivable from existing entities — but for
large libraries a cached/denormalized statistics row (or a Room `@Query`
aggregation layer + a periodic refresh) keeps the screen instant. A `stats`
cache table or an in-memory `StateFlow` populated by a repository query is
sufficient for v1.

### Summary table

| Feature (from prototype) | Data source today | Schema change needed |
| --- | --- | --- |
| Audio/video icon on every card | implied by `resolution`/`format` | Add `mediaType` to `VideoEntity` (and optionally `DownloadEntity`) |
| Link-history type identifier + one-tap load | re-derived from URL | Add `linkType` to `LinkHistoryEntity` |
| Per-tab sort/search + list/grid persistence | in-memory only | DataStore prefs (optional) |
| Statistics screen | aggregate queries over existing tables | none required (optional cache) |

---

## Feature Comparison & Android Migration Roadmap

This section provides an exhaustive index comparing capabilities between the real Android App (`app/`) and the `design-2` Prototype, followed by the exact Room DB schema additions and DataStore preferences required for Android implementation.

### 1. Feature Comparison Matrix

| Feature / Subsystem | Present in Android App | Present in Prototype | Summary & Migration Action |
| --- | --- | --- | --- |
| **ExoPlayer Gestures & Surface** | Yes (Native ExoPlayer/Media3, vertical swipe brightness/volume, double-tap seek, pinch-to-zoom) | Simulated (HTML5 `<video>` / audio player controls, basic seek & playback rates) | **Android App Only:** Preserve native ExoPlayer gesture detectors and hardware accelerated surface rendering when migrating UI to Compose player. |
| **Storage Access Framework (SAF)** | Yes (SAF document picker, URI permission persistence, SD card access) | Simulated (mock file paths in memory) | **Android App Only:** Retain `DownloadPathResolver` and SAF picker integration for folder selection. |
| **Background WorkManager Jobs** | Yes (`BulkDownloadPreparationWorker`, `SubscriptionWorker`, `SyncWorker`, `AutoBackupWorker`, `UpdateCheckWorker`) | Simulated (In-memory timers & state flow simulation) | **Android App Only:** Preserve background execution infrastructure; link new UI signals to existing WorkManager triggers. |
| **yt-dlp Binary Pipeline** | Yes (Native Python/yt-dlp wrapper & format extraction engine) | Simulated (`data.js` mock metadata generator) | **Android App Only:** Keep production extraction engine; adapt DTOs to provide audio/video badges and link types. |
| **Storage Migration & Relocation** | Yes (File relocation between internal storage and SAF paths) | No | **Android App Only:** Retain existing file movement logic during storage path changes. |
| **System Notifications** | Yes (`DownloadService` foreground notification, update alerts) | UI Only (Notifications screen & toast triggers) | **Android App Only:** Map prototype notification feeds to Android system notification channels; enforce 365-day DB retention limit. |
| **Backup, Export & Restore** | Yes (`BackupRepository`, `RestoreRepository`, library repair) | Simulated (Settings export/repair UI options) | **Android App Only:** Retain real database export/import zip creation and validation logic. |
| **VPS Sync via REST API** | Yes (`SyncManager`, `SyncRepository`) | Simulated (Settings sync UI) | **Android App Only:** Retain REST sync logic and trigger points. |
| **Audio/Video Type Badges** | No (Relies on implicit text formats or missing indicators) | Yes (`MN.typeBadge` icon badges on all media cards/rows) | **Prototype Improvement:** Add explicit `mediaType` field to models/entities and render compact icon badges in `UnifiedVideoCard`. |
| **4-Tab Shell & 6-Subtab Collections** | No (Legacy multi-tab navigation structure) | Yes (4 bottom tabs: Home, Collections, Downloads, Settings; Collections consolidates History, Watched, Folders, Favorites, Playlists, Channels) | **Prototype Improvement:** Refactor Android `BottomNavItem` shell to 4 main tabs with `CollectionsScreen` horizontal scrollable subtab hub. |
| **Single-Category Toggle Sorting** | No (Multiple redundant sort menu items) | Yes (Single category items for Date, Name, Duration, Size, Progress, Status with directional arrows `↓`/`↑`) | **Prototype Improvement:** Redesign sort menus to toggle ascending/descending on re-selection with arrow indicators. |
| **Universal 10-Item Lazy Loading** | Partial (Standard LazyColumn without constrained initial batch loading) | Yes (Universal dynamic batch loading of 10 items for performance) | **Prototype Improvement:** Implement Compose `LazyColumn`/`LazyVerticalGrid` with initial 10-item batch paging across all lists. |
| **End-of-List Indicators** | No | Yes (`• You have reached the end of the list •` shown when all items loaded) | **Prototype Improvement:** Append visual end-of-list component to bottom of every lazy-loaded list/grid. |
| **Floating Extract Button** | No (Standard inline text field button) | Yes (Prominent floating action button / overlay for link extraction) | **Prototype Improvement:** Add floating action button on Home screen for quick link extraction trigger. |
| **Audio-Only Subscription Switch** | Partial (Backend field exists in `SubscriptionEntity`, missing UI control) | Yes (Explicit `Audio-only` toggle switch on subscription cards alongside Auto-Download) | **Prototype Improvement:** Expose `SubscriptionEntity.audioOnly` toggle switch in `SubscriptionsScreen` & ViewModel. |
| **Default 360p Download Resolution** | No (Hardcoded resolution selection or prompt) | Yes (Default 360p resolution setting in Download Preferences) | **Prototype Improvement:** Implement `DownloadPreferences.defaultResolution` defaulting to `"360p"`. |
| **Queue Drag-to-Reorder** | No | Yes (Drag handles on player up-next queue and downloads queue) | **Prototype Improvement:** Add drag-to-reorder list support in Player bottom sheet and Downloads queue. |
| **Statistics & Analytics Dashboard** | Basic (Simple count labels) | Yes (Comprehensive analytics screen: watch hours, storage breakdowns by format/resolution/folder/channel) | **Prototype Improvement:** Rebuild `StatisticsScreen` to aggregate and display thorough engagement and storage metrics. |
| **Time-Based Watched Logic** | No (Uses percentage threshold) | Spec (Defined for migration: marked watched when remaining time ≤ 1 minute) | **Prototype Improvement:** Update watched status calculation in `VideoRepository` to remaining time ≤ 1 min. |
| **Link History Type Icons & One-Tap** | Basic (Plain URL history) | Yes (Icons for Video/Playlist/Channel, one-tap instant re-extract, Clear All button) | **Prototype Improvement:** Add `linkType` to `LinkHistoryEntity`, display type icons, and support one-tap extraction reload. |

---

### 2. Room DB Schema Additions Inventory

To support prototype features in the real Android app, update the Room database schema (`com.example.medianest.data.local`):

1. **`VideoEntity` (`app/src/main/java/com/example/medianest/data/local/entity/VideoEntity.kt`)**
   - Add column: `val mediaType: String = "VIDEO"` (`"VIDEO"` | `"AUDIO"`).
   - Migration logic: Populate existing rows with `"AUDIO"` if `resolution == "Audio"` or `format` contains `"audio"`, else `"VIDEO"`.

2. **`LinkHistoryEntity` (`app/src/main/java/com/example/medianest/data/local/entity/LinkHistoryEntity.kt`)**
   - Add column: `val linkType: String = "VIDEO"` (`"VIDEO"` | `"PLAYLIST"` | `"CHANNEL"` | `"UNKNOWN"`).
   - Migration logic: Set default `"VIDEO"`; populate going forward during extraction based on extracted URL target.

3. **`SubscriptionEntity` (`app/src/main/java/com/example/medianest/data/local/entity/SubscriptionEntity.kt`)**
   - Ensure `val audioOnly: Boolean = false` is mapped to UI state and editable via `SubscriptionDao`.

4. **Notification DB Pruning (`NotificationEntity` / System Log)**
   - Add DAO cleanup query: `@Query("DELETE FROM notifications WHERE timestamp < :cutoffTimestamp")`.
   - Execution: Enforce automatic deletion of notifications older than 365 days during app launch or WorkManager maintenance jobs.

---

### 3. DataStore Preferences Inventory

All UI state and download defaults should be stored in Jetpack DataStore (`com.example.medianest.data.preferences`):

1. **`DownloadPreferences`**
   - `defaultResolution`: `PreferenceKey<String>` — Default `"360p"` (Options: `"1080p"`, `"720p"`, `"480p"`, `"360p"`, `"Audio"`).

2. **`SubscriptionsPreferences`**
   - `showShorts`: `PreferenceKey<Boolean>` — Default `false` (Controls whether Shorts appear when viewing channels/playlists).

3. **`CollectionsPreferences`**
   - `viewMode`: `PreferenceKey<String>` — Default `"GRID"` (`"GRID"` | `"LIST"`). Persists list/grid toggle across Collections.
   - `sortMode_history`, `sortMode_watched`, `sortMode_folders`, `sortMode_favorites`, `sortMode_playlists`, `sortMode_channels`: `PreferenceKey<String>` — Stores per-tab category and direction (e.g., `"DATE_DESC"`, `"NAME_ASC"`).

4. **`DownloadsPreferences`**
   - `sortMode`: `PreferenceKey<String>` — Default `"DATE_DESC"` (`"DATE_DESC"`, `"PROGRESS_DESC"`, `"SIZE_DESC"`, `"STATUS_ASC"`).

---

## How to traverse this design to implement it (for an implementer agent)

1. Read `README.md` (this file) and `HANDOVER.md`.
2. Open `index.html` and click through every tab and secondary route.
3. For any screen, open the matching `js/screens-*.js` — the route name in the
   `MN.router.register(...)` call maps 1:1 to an Android `NavDestination`.
4. Map each `MN.*` call to the corresponding Android service/ViewModel:
   - `MN.downloads.*` → `DownloadService` / download manager.
   - `MN.playback.*` → the player (ExoPlayer/Media3) controller.
   - `MN.router.navigate` → `NavController.navigate`.
   - `MN.store` → a shared `StateFlow`/ViewModel state holder.
   - `MN.notify` → the Android `NotificationManager` channels documented in
     `HANDOVER.md` (section 12).
5. Match CSS classes (`css/components.css`) to Compose `GlassCard`,
   `UnifiedVideoCard`, and theme tokens (`ui/theme/Color.kt`, `Type.kt`).
6. Apply the **Database / schema additions** section above to the Room layer
   before wiring the UI.
