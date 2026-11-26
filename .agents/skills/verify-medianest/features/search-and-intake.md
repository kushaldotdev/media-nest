# Search and link intake

Home is the intake surface: a top bar with a list/grid toggle and a notifications bell, a hero
panel whose pill takes a pasted URL, a bare video id or an `@channel` handle (`Paste`, `Clear`,
`Extract`) plus a `Full titles` switch, three format filter pills, then whichever result the
extraction produced — an extracted video, a playlist, a channel, an error, or the link history list
that survives restarts.

## Sub-features

- `intake-topbar` — `MediaNest` / `Offline Media Hub`, the `Switch to list view` / `Switch to grid view`
  toggle, and the `Notifications` bell with its unread badge.
- `intake-field` — the pill accepts text, `Paste` fills it from the clipboard, `Extract` submits it,
  `Clear` empties it, and the small `Clear` link under the panel resets the result.
- `intake-full-titles` — the `Full titles` switch in the hero panel (screen-local, **not** persisted;
  the global toggle that persists lives in `Settings` as `Always Show Full Titles`).
- `intake-filters` — `All` / `Videos` / `Audio` pills filter the result list and the history list.
- `intake-blank-url` — submitting nothing produces `Extraction Error` with `Please enter a URL`.
- `intake-extract-success` — a real URL ends in `Extracted Video` with a `Play Video` action.
- `intake-extract-failure` — an unreachable or unparseable URL ends in `Extraction Error` + `Dismiss`.
- `intake-history` — extracted links appear under `History` with per-row `Load` / `Delete`, a
  `Clear all` action, and a `No link history` empty state on a clean install.
- `intake-playlist-channel` — playlist and channel results render their own headers with
  `Download All` and a `Show Shorts` switch.
- `intake-bulk` — `Download All by Resolution` → `Fetching Video Metadata` → `Confirm Bulk Download`
  → queued `downloads` rows.
- `intake-continue` — the `Continue watching` carousel on Home, only with partial playback progress.

## How to get to it (user POV)

- Home is the first destination after launch; the hero card (tagged `OFFLINE-FIRST`) holding the
  search/URL pill is the first card in the list, above the filter pills.
- Paste or type a YouTube URL, a bare video id, an `@channel` handle or a playlist link into the
  pill and press `Extract` (or the keyboard's Go / IME action, which calls the same handler).
  Despite the placeholder, there is no full-text search: anything that is not one of those shapes is
  treated as a bare video id, so typing `cats` extracts `watch?v=cats` and errors.
- The extraction result replaces the history list in place; `History` does not render while a
  result is showing, so `Clear` (the small link under the panel) or a relaunch brings it back.
- Tap a `History` row body to copy its URL to the clipboard (snackbar `Link copied to clipboard`, or
  `Failed to copy link`); tap the row's title text to expand/collapse a truncated title. Only `Load`
  re-extracts; `Delete` asks for confirmation.
- Tap `Download All` on a playlist or channel result to open the bulk flow.
- Tap `Notifications` (bell, top-right) → Notifications screen.

## Driving it with ad.sh

Preconditions:

- `scripts/nest.sh up` has run; `scripts/ad.sh launch` reached Home and the bottom nav is in the tree
  (see [app-shell](app-shell.md)).
- `S=.agents/skills/verify-medianest/scripts/nest-state.sh` — every state check below goes through
  that script, which resolves the same serial as `scripts/ad.sh` (including the WSL → host bridge).
- `$S logcat-clear` first — `ad.sh launch` does not clear logcat.
- Decide and write down whether the emulator has network. Every extraction below is split into a
  network-dependent path and a deterministic, network-free path. A network-dependent step that
  times out is **not** a UI failure.

- **Top bar.** Run `scripts/ad.sh is visible 'label="Offline Media Hub"'`,
  `scripts/ad.sh is visible 'label="Switch to list view"'` (in grid mode; the label flips to
  `Switch to grid view` once toggled) and `scripts/ad.sh is visible 'label="Notifications"'`. The
  bell's badge count comes from `NotificationsViewModel.unreadCount`; its absence means zero unread,
  not a broken bell.
- **Toggle the layout.** `scripts/ad.sh click 'label="Switch to list view"' --settle`, then
  `scripts/ad.sh is visible 'label="Switch to grid view"'`. The persistence check is the DataStore,
  not the pixels: `$S pref-dump collections_prefs` must contain `view_mode=LIST`
  (the stored strings are `LIST` / `GRID`; `GRID` is the default).
- **Hero card.** `scripts/ad.sh is visible 'label="OFFLINE-FIRST"'` and
  `scripts/ad.sh is visible 'label="Download, organize and play your YouTube library — even offline."'`
  prove you are looking at the hero and not a leftover result.
- **Field present.** Run `scripts/ad.sh snapshot -i`. The pill's editable node has **no label** —
  `Search or paste URL...` is a sibling placeholder `Text`, so `fill 'label="Search or paste URL..."'`
  does not find a field. Fill by ref (`scripts/ad.sh fill @eN "https://…" --settle`) or by
  `editable=true`; Home has exactly one editable field, so that selector is unambiguous here.
- **Paste from the clipboard.** Deterministic and typo-proof:
  `scripts/ad.sh clipboard write "https://www.youtube.com/watch?v=dQw4w9WgXcQ"`, then
  `scripts/ad.sh click 'label="Paste"' --settle`, then
  `scripts/ad.sh is visible 'label="Clear"'` — the `Clear` icon only renders when the field is
  non-empty, which is the proof the paste landed.
- **Clear the field.** Take the icon's ref from `snapshot -i` and `scripts/ad.sh click @eN --settle`, then
  `snapshot -i` no longer lists it. Do **not** use a bare `label="Clear"`: the small `Clear` link under
  the panel is a second node with the same text, so the selector returns `AMBIGUOUS_MATCH` whenever that
  link is visible (`urlInput.isNotEmpty() || uiState !is HomeUiState.Idle`).
- **Blank submission (network-free).** With the field empty, `scripts/ad.sh click 'label="Extract"'`
  and `scripts/ad.sh wait text "Extraction Error" 5000`. The message under the heading reads
  `Please enter a URL` (`HomeUiState.Error`). This path needs no network and is the cheapest proof
  the error surface works.
- **Dismiss the error.** `scripts/ad.sh click 'label="Dismiss"' --settle`, then
  `scripts/ad.sh wait absent 'label="Extraction Error"' 5000`. `Dismiss` calls `resetState()`.
- **Successful extraction (network-dependent).** Put a real YouTube URL in the pill, press
  `Extract`, and `scripts/ad.sh wait text "Extracted Video" 15000`. The section header also carries
  the `Play Video` action, which navigates to `videoDetail/<videoId>`. While the fetch is running
  the panel shows `Extracting…` with a spinner and `Extract` is disabled — that is a loading state,
  never a result.
- **Failed extraction (network-dependent).** Submit `https://www.youtube.com/watch?v=not_a_video`
  (or any URL with the network off) and
  `scripts/ad.sh wait text "Extraction Error" 15000`. Report which of success/failure the run
  produced instead of assuming one.
- **History appears.** Relaunch (`scripts/ad.sh launch`) and wait for Home to settle; the history
  section renders only in the idle state:
  `scripts/ad.sh wait text "History" 10000`, then
  `$S db-query "select url, linkType from link_history order by extractedAt desc limit 5"`.
  On a clean install the same slot shows `No link history` /
  `Links you extract will appear here for quick re-load.`
- **History row actions.** Each row exposes `Load` and `Delete` (both are icon
  `contentDescription`s) plus a `Video` / `Playlist` / `Channel` type badge. `Delete` opens
  `Delete history item` with `Remove this link from your history?` and `Delete` / `Cancel`.
  `Delete` is ambiguous while the dialog is up (every row behind the scrim has one too), so take the
  dialog button's ref from `snapshot -i` and click that. Prove the delete with
  `$S db-count link_history` decreasing by one.
- **Clear all.** `Clear all` is rendered twice — once in the `History` header and once as the
  dialog's confirm button — so take the header's ref from `snapshot -i` and
  `scripts/ad.sh click @eN --settle`. It opens `Clear link history` /
  `Remove all link history entries? This cannot be undone.` with `Clear all` / `Cancel`; confirm with
  the dialog's ref. Then `$S db-count link_history` must be `0` and the empty
  state must be back.
- **Format pills.** The three pills are `All`, `Videos`, `Audio`, taken from `snapshot -i` refs —
  `label="Audio"` is ambiguous because every card's media-type badge also carries
  `contentDescription = "Audio"`. Clicking `Videos` filters history, the playlist grid and the
  channel grid to video rows; clicking `Audio` empties all three (see the gotcha below).
- **Playlist / channel result (network-dependent).** Submit a playlist URL and wait for `PLAYLIST`;
  a channel URL waits for `CHANNEL` (the header also carries `Download All` and `Show Shorts`).
  `$S db-count subscriptions` is the side effect of `Add to Playlist` /
  `Subscribe`, not of the result rendering.
- **Bulk download chain (network-dependent).** From a playlist or channel result,
  `scripts/ad.sh click 'label="Download All"' --settle` opens `Download All by Resolution` with
  `Select target resolution/format:` and the radio rows `1080p (Full HD)`, `720p (High Definition)`,
  `480p (Standard Definition)`, `360p (Low Data Usage)`, `Audio Only (Opus/M4A 128kbps)`. Pick one,
  press `Next`, and expect `Fetching Video Metadata` with
  `Retrieving stream details to calculate size and check disk space.` and a `Progress:` line. That
  dialog cannot be dismissed by back or an outside tap — `Cancel` is the only exit. When it
  finishes, `Confirm Bulk Download` lists `Quality:`, `Total Videos:`, `Total Download Size:`,
  `Available Space:` and either `Storage check: Sufficient space available.` or
  `Warning: Insufficient storage! Extra … needed.`, with `Download` (or `Download Anyway`) and
  `Cancel`.
- **Bulk state proof.** While preparing:
  `$S db-query "select id, status, totalVideos, processedVideos, downloadableVideos, unavailableVideos, failedVideos from bulk_download_jobs order by id desc limit 1"`
  walks `PENDING` → `RUNNING` → `READY`. Then, **before** pressing `Download`, capture the READY set —
  after confirmation it is gone:

  ```bash
  $S db-query "select videoId from bulk_download_items where jobId=<jobId> and status='READY' order by videoId"
  $S db-count bulk_download_items "jobId=<jobId> and status='READY'"
  ```

  Press `Download`. `HomeViewModel.confirmBulkDownload` enqueues those items, calls
  `markJobConfirmed(jobId)` and then `pruneFinishedJobs()`, which **deletes** the now-`CONFIRMED`
  job row and cascades `ON DELETE CASCADE` into `bulk_download_items`. So the post-click assertion is
  the opposite of accumulation:

  ```bash
  $S db-count bulk_download_jobs "id=<jobId>"          # must be 0 — pruned, not CONFIRMED
  $S db-count downloads "videoId in ('<id1>','<id2>')"  # captured READY count
  ```

  Only `READY` items are enqueued; an all-unavailable playlist enqueues nothing and only toasts
  `All selected videos are already in the queue or downloaded`.
- **Full titles.** The hero panel renders the label `Full titles` **twice** — once as the visible
  `Text` and once as the switch's `contentDescription` — so scope the selector:
  `scripts/ad.sh is checked 'role=switch label="Full titles"'`, then
  `scripts/ad.sh click 'role=switch label="Full titles"' --settle`. `false` is the default
  (`CollectionsPreferences.DEFAULT_FULL_TITLES`). Beware the persistence caveat: the Home toggle only
  updates local `fullTitlesHome` state, and `setFullTitles` is only called from
  `Settings → Always Show Full Titles`, so after a Home toggle
  `$S pref-bool collections_prefs full_titles` may still read `false`. To verify
  the persisted bit, flip it in Settings (that row *does* write), then relaunch and read the pref.
- **Continue watching.** Present only when a `playback_history` row sits between 1% and 98% of the
  video's duration (`currentContinueWatching` drops anything outside that band, and needs a matching
  `videos` row with `durationSeconds > 0`): `scripts/ad.sh scroll top --settle` then
  `scripts/ad.sh is visible 'label="Continue watching"'`. Cards carry a `Resume` overlay and
  `Left off at <duration>` instead of the channel name. Seed one with
  `$S db-push-sql "insert or replace into playback_history (videoId, positionMillis, playedAt, totalWatchTimeMillis, syncVersion) values ('<videoId>', 65000, strftime('%s','now') * 1000, 65000, 0)"`
  — 65s of a ~212s video is ~31%, inside the band. The `* 1000` matters: Room stores
  `System.currentTimeMillis()`, so a seconds value reads as 1970 and the card sorts to the bottom.
- **Crash gate.** `$S crash-check`.
- **Proof.** `ART=$($S evidence-dir intake)` then one
  `scripts/ad.sh screenshot "$ART/screens/0N-<state>.png"` per state (idle, loading, success or
  error, history, bulk dialog) and a matching `snapshot -i` capture.

## Gotchas

- **`Search or paste URL...` is a placeholder, not a label.** It is a `Text` drawn next to a bare
  `BasicTextField`, and only while the field is empty. `fill`/`wait text` will not match the field by
  that string; select the field by ref or `editable=true`. The field submits on the keyboard's Go
  action as well as on `Extract`.
- **`Extract` is a content-desc that disappears while loading.** `Clear`, `Paste` and `Extract`
  are `IconButton` `contentDescription`s. While `isLoading` the `Extract` button swaps its icon for
  a `CircularProgressIndicator`, so `label="Extract"` matches nothing mid-extraction — wait for
  `Extracted Video` or `Extraction Error` instead of re-clicking. `Dismiss` is the opposite case: it
  is a plain `TextButton` label.
- **`Extracting…` is a loading state.** Success is `Extracted Video`, failure is `Extraction Error`.
  Waiting on the spinner and calling it a pass is the classic wrong report.
- **`Extraction Error` covers client-side validation too.** A blank submit renders the same banner
  with `Please enter a URL`. An error banner alone does not prove the network was reached.
- **History only renders in the idle state.** `if (uiState is HomeUiState.Idle)` gates the whole
  section, so after any result the history list is gone from the tree even though `link_history`
  rows still exist. Reset with the small `Clear` link under the panel (or relaunch) before asserting
  history again.
- **The `Audio` pill filters to nothing.** Every consumer of `selectedFormatFilter` treats `Audio`
  as an empty result: history keeps only `linkType='AUDIO'` rows (and `saveLinkToHistory` only ever
  writes `VIDEO`, `PLAYLIST` or `CHANNEL`), while playlist and channel results map `Audio` to
  `emptyList()`. So `Audio` shows `No link history` / an empty grid on a populated install, and
  `Videos` shows the video rows. Record it as current behaviour, not a broken run.
- **The pills are screen-local, non-persistent, and do not survive navigation.**
  `selectedFormatFilter` is a `remember` in `HomeScreen` (`"All"` on every fresh composition), and
  no `pref-bool`/`pref-get` reflects it — do not look for one.
- **`label="Audio"` / `label="Video"` are ambiguous on Home.** The media-type badge on every card and
  every history row carries `contentDescription` `Audio` or `Video` (chosen from the row's
  `mediaType` / `format`), so the pill and the badges collide. Take pill refs from `snapshot -i`.
- **`Full titles` matches two nodes.** Visible label plus switch `contentDescription`; always
  qualify with `role=switch`.
- **The Home `Full titles` toggle is not persisted.** `onFullTitlesChange` in `HomeScreen` only sets
  the screen-local `fullTitlesHome`; `CollectionsPreferences.setFullTitles` is called exclusively
  from the Settings row `Always Show Full Titles`. So `pref-bool collections_prefs full_titles`
  staying `false` after a Home toggle is the current behaviour — verify persistence through Settings.
- **Extraction is network-dependent and slow.** Use `wait text "…" 15000`, never a fixed sleep. On
  an offline emulator the extractor fails by design; report the offline path rather than marking the
  feature broken.
- **A local `videos` row short-circuits extraction.** `onUrlSubmitted` resolves the video id first and,
  when `videoDao.getVideoById(id)` hits, builds mock stream sources straight from that video's
  `downloads` instead of going to the network. A "network-free extraction" that suddenly succeeds is
  usually this path, not a cache — check `$S db-count videos` before crediting the network.
- **`Fetching Video Metadata` cannot be dismissed.** `onDismissRequest = {}` on that dialog, so
  back and outside taps are ignored. `Cancel` is the only exit, and it cancels the unique
  WorkManager job (`BULK_DOWNLOAD_PREP_WORK_NAME`) as well as the job row.
- **`Confirm Bulk Download`'s primary button changes label.** It reads `Download` when
  `hasSpace`, `Download Anyway` when the storage check fails. Assert the pair, not one string.
- **Seeding rows from the host is fragile in two ways.** `MediaNestApp.onCreate` runs
  `videoDao.deleteSearchedOrphans()` on every start:
  `DELETE FROM videos WHERE favorite = 0 AND lastPlayedAt IS NULL AND (localFilePath = '' OR
  localFilePath IS NULL) AND id NOT IN (SELECT videoId FROM video_folder_join)` — a
  `$S db-push-sql` seed that is not `favorite=1`, not `lastPlayedAt`-stamped, not
  `localFilePath`-backed and not in a folder is deleted on the very next launch. Separately,
  `downloads`, `playback_history` and `watch_sessions` carry `FOREIGN KEY(videoId) REFERENCES
  videos(id) ON DELETE CASCADE` (checked by Room at runtime, not by the `sqlite3` behind
  `db-push-sql`), so the `videos.id` row must exist first or the child is a silent orphan that
  nothing renders. `link_history` has no foreign key and is the safest table to seed on its own.
- **`db-push-sql` force-stops the app.** It has to (an open connection would overwrite the write),
  and it drops the device-side `-wal`/`-shm`. Relaunch with `scripts/ad.sh launch` before driving
  again, and re-read `$S doctor` if `run-as` starts refusing.
- **The pill's own `Clear` and the panel's `Clear` are different controls.** The icon clears the
  text box only; the small `Clear` link under the panel calls `resetState()` and also drops the
  extraction result.
