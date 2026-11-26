# Video detail

The video detail screen is the per-video hub: a 16:9 hero with a centred `Play Video` overlay and
the personal watch count, the `Favorite` toggle in the top bar, the local `Downloaded versions (N)`
list with delete options, the `YouTube` and `Set Watched` actions, a collapsible `Description`, a
`Your Statistics` card with the watch-history log and a saved-position reset, and the full stream
ladder (`Available streams:`, `Audio Only`) with per-stream `Play` and `Download`.

## Sub-features

- `detail-open` — reachable from every list that renders a video card, and from a notification deep link.
- `detail-hero` — the `Play Video` overlay starts playback, preferring local downloads over streams.
- `detail-favorite` — the top-bar `Favorite` toggle persists to `videos.favorite`.
- `detail-downloads` — `Downloaded versions (N)` lists completed local copies with `Play` and `Delete`.
- `detail-delete-download` — `Delete` offers `List Only` / `Delete File & List` / `Keep`.
- `detail-watch-count` — `Set Watched` opens `Update Watch Count` and writes `videos.watchCount`.
- `detail-description` — `Description` expands with `Show more` / `Show less`.
- `detail-stats` — `Your Statistics` shows watch count, last position, total time and the history log.
- `detail-reset-position` — `Reset Last Watch Position` zeroes `playback_history.positionMillis`.
- `detail-streams` — `Available streams:` plus `Audio Only`, with `Show more streams (N remaining)`.
- `detail-youtube` — the `YouTube` button leaves the app for the public watch page.

## How to get to it (user POV)

- Home: tap a `Continue watching` card or an extracted-video card, or the `Play Video` action on the
  `Extracted Video` header.
- Collections: tap any card in `All Videos`, `History`, `Favorites`, `Watched`, a folder, a playlist
  or a channel list.
- Downloads: tap the row body of a `Completed Downloads` card (title/thumbnail area) — the play icon
  on the row goes straight to the player instead. Rows do not navigate while batch mode is on.
- Notifications: tap a notification that points at a video → `videoDetail/<videoId>`.
- There is no external deep link for this route: `NavigationRoutes.VIDEO_DETAIL` is
  `videoDetail/{videoId}` with `videoId` a String path argument, and `MainActivity` handles only
  `ACTION_NAVIGATE_DOWNLOADS`, `ACTION_NAVIGATE_UPDATES` and `ACTION_CONFIRM_RESTART`.

## Driving it with ad.sh

Preconditions:

- `scripts/nest.sh up` has run; `scripts/ad.sh launch` reached Home and the bottom nav is in the tree
  (see [app-shell](app-shell.md)).
- `$S logcat-clear` first — `ad.sh launch` does not clear logcat.
- The screen needs a video to show. Two supported ways in, pick one and say which:

  **Seeded, offline, deterministic.** With the app stopped, seed one video plus one completed
  download. The `favorite = 1` is load-bearing — see the pruning gotcha — and it also decides which
  Collections tab can see the row (`Favorites`), because `History` lists only videos with a
  `lastPlayedAt`.

  ```bash
  S=.agents/skills/verify-medianest/scripts/nest-state.sh
  $S db-push-sql "
  insert or replace into videos
    (id, title, channelName, channelId, durationSeconds, thumbnailUrl, description, uploadDate,
     localFilePath, favorite, addedAt, lastPlayedAt, downloadedAt, syncVersion, watchCount, mediaType)
  values
    ('verifyDetail1', 'Verify Detail Fixture', 'Verification Channel', 'UCverify', 212, null,
     'Fixture description used by the video-detail recipe.', '20260101',
     '', 1, strftime('%s','now') * 1000, null, null, 0, 0, 'VIDEO');
  insert or replace into downloads
    (downloadUuid, outputRoot, videoId, url, format, quality, title, filePath, fileSizeBytes,
     downloadedAt, status, progress, retryCount, updatedAt, syncVersion)
  values
    ('u-verify-detail-1', '', 'verifyDetail1', 'https://example.invalid/a.mp4', 'video',
     '360p (avc1)', 'Verify Detail Fixture',
     '/sdcard/Android/data/com.example.medianest.debug/files/MediaNest/video/verify-detail-360.mp4',
     12582912, strftime('%s','now') * 1000, 'COMPLETED', 1.0, 0, strftime('%s','now') * 1000, 0);
  "
  ```

  Every timestamp column is `strftime('%s','now') * 1000`: Room writes `System.currentTimeMillis()`,
  so a seconds value reads as January 1970 and sorts to the bottom of every list. The `filePath`
  sits in **app-private** external storage (`/sdcard/Android/data/<pkg>/files/…`) on purpose —
  `DownloadService.deleteDownload` runs `File(filePath).delete()` inside the app's own uid, and
  scoped storage silently fails that on `/sdcard/Download` on API 30+.

  `db-push-sql` force-stops the app, so follow it with `scripts/ad.sh launch`.

  **Extracted, online.** Extract a real URL on Home (see [search-and-intake](search-and-intake.md))
  and tap the extracted card. This path re-runs the yt-dlp extraction inside the detail screen, so
  the title, description and stream ladder are whatever YouTube returned.
- **Open it.** `Collections` opens on the `History` tab, which is
  `SELECT * FROM videos WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC` — a seed with
  `lastPlayedAt = null` is **not** there. Take the nav ref for `Collections` out of `snapshot -i`
  (the tab label and the top-bar title are both `Collections`, so a bare `label=` is
  `AMBIGUOUS_MATCH`) and `scripts/ad.sh click @eN --settle`, then switch to the `Favorites` pill
  (`SELECT * FROM videos WHERE favorite = 1`), which is where the seed above lands. Stamp
  `lastPlayedAt` in the seed as well if you want it under `History` instead. Then tap the card:
  `scripts/ad.sh find text "Verify Detail Fixture" click --first`, which needs no `--settle`
  (`find` is not a settle-capable action). The card's thumbnail also carries
  `contentDescription = title`, so the title matches twice — `--first` is required. Wait for the
  screen with `scripts/ad.sh wait text "Details" 10000`.
- **Confirm the screen, not just the title.** `Details` is the top-bar title; assert something only
  this screen renders: `scripts/ad.sh is visible 'label="Play Video"'`.
- **Hero.** `Play Video` is the centre overlay on the thumbnail; the top-end badge is the watch
  count, so a count above zero renders a number there. The overlay takes one of two routes:
  - **a completed local download exists** → `downloads/player/<videoId>?downloadId=<id>`, i.e. the
    offline player, after picking the highest-resolution completed *video* download (or the first
    completed audio download when the item is audio-only). With the seed above this is the path;
  - **no usable download** → `player/<videoId>?streamIndex=<n>`, choosing the first
    `format == "video"` stream whose quality starts with `360p`, else the first `format == "video"`
    stream, else the highest-resolution `video`/`video_only` stream, else `bestAudioStream`, else
    `streamSources.first()`. With none at all it only toasts `No playable streams available`.

  `scripts/ad.sh appstate` plus `snapshot -i` tell you which player you landed on (transport
  controls that can seek locally vs a buffering network surface). `scripts/ad.sh back --settle`
  returns to detail.
- **Favorite.** `scripts/ad.sh click 'label="Favorite"' --settle`, then read the toggle's state with
  `scripts/ad.sh get attrs 'label="Favorite"'` (it is an `IconToggleButton`, so the node reports
  selected/checked — do not infer it from pixels) and prove the write:
  `$S db-query "select favorite from videos where id='verifyDetail1'"` must flip
  `0` ↔ `1`. A snackbar `Added to favorites` / `Removed from favorites` accompanies it, but the row
  is the proof.
- **Downloaded versions.** With the seed above, `scripts/ad.sh is visible 'label="Downloaded versions (1)"'`
  and the row shows the quality `360p (avc1)` with a `size • bitrate • date` line. Only
  `status='COMPLETED'` rows render here; a `QUEUED` row is invisible on this screen. A second
  seeded download changes the count to `(2)`.
- **Play a local version.** Take the row's play icon ref from `snapshot -i` and
  `scripts/ad.sh click @eN --settle`. Never use a bare `label="Play"`: the hero overlay and every
  stream row also expose `Play`, which is `AMBIGUOUS_MATCH`. Expect the offline player route
  `downloads/player/<videoId>?downloadId=<id>`, then `scripts/ad.sh back --settle`.
  `$S db-query "select id,status from downloads where videoId='verifyDetail1'"` is untouched by
  playback: `DownloadDao` never writes `downloads.lastPlayedAt`; the resume position that playback
  records lands in `playback_history`, and the recency stamp in `videos.lastPlayedAt`.
- **Delete a download.** Click the row's trash icon by ref (`@eN` from `snapshot -i`; every
  downloaded row carries its own `Delete`). It opens `Delete Download` with
  `Choose how you want to delete "Verify Detail Fixture".` and `List Only` / `Delete File & List` /
  `Keep`.
  - `List Only` → `onDeleteDownload(download, deleteFile = false)`: the `downloads` row goes away,
    the file stays. Snackbar `Removed from downloads list`.
  - `Delete File & List` → `deleteFile = true`: row and file both go. Snackbar `Download deleted`.
  - `Keep` closes the dialog with no DB change.

  Prove with `$S db-count downloads "videoId='verifyDetail1'"` and a file listing from the app's own
  uid — two independent views, and they must disagree for `List Only`. Source the shared lib first
  (WSL reaches the device through the host adb bridge), then check the seeded path:

  ```bash
  . scripts/lib/common.sh            # ADB_SERVER_SOCKET bridge; no-op on native Linux
  ADB="adb -s $(nest_device_serial)"
  $ADB shell 'echo x > /sdcard/Android/data/com.example.medianest.debug/files/MediaNest/video/verify-detail-360.mp4'
  $ADB shell run-as com.example.medianest.debug \
    ls files/MediaNest/video/verify-detail-360.mp4
  ```

  The seeded row's `filePath` is only a string: no file exists until something writes one, hence the
  `echo x` above. Keep the fixture in `/sdcard/Android/data/<pkg>/files/…` — `deleteDownload` runs
  `File(filePath).delete()` as the app uid, and scoped storage refuses that for `/sdcard/Download`
  on API 30+, so a public-directory fixture makes `Delete File & List` look broken when it is not.
- **Set Watched.** `scripts/ad.sh click 'label="Set Watched"' --settle` (the button text) opens
  `Update Watch Count` with the video title, the current count, and `Decrease count` /
  `Increase count` icon buttons (`contentDescription`s), plus `Confirm` and `Cancel`. Press
  `Increase count` twice and `Confirm`, then:

  ```bash
  $S db-query "select watchCount from videos where id='verifyDetail1'"
  $S db-count watch_sessions "videoId='verifyDetail1'"
  ```

  `videos.watchCount` must equal what the dialog showed. `watch_sessions` only gains a row when the
  new count is **greater** than the old one — decreasing and confirming writes the count but adds no
  session. On screen the count is two sibling `Text` nodes, `Your watch count: ` and `N views`
  (`view` when N is 1), so assert each separately; a single `label="Your watch count: 3 views"`
  matches nothing because `label=` compares the whole field.
- **Description.** `scripts/ad.sh is visible 'label="Description"'`, then
  `scripts/ad.sh click 'label="Show more"' --settle` and `scripts/ad.sh is visible 'label="Show less"'`.
  The card only renders when `videoInfo.description` is non-blank, so a fixture without a description
  has no toggle at all — that is not a failure.
- **Your Statistics.** The card renders only when there is a `playback_history` row, at least one
  `watch_sessions` row, or `watchCount > 0`. With the seed plus `Set Watched`:
  `scripts/ad.sh wait text "Your Statistics" 5000`, then assert the rows it renders —
  `Your watch count: `, `Total Watch Time: ` and `Play Count: ` as separate sibling `Text` labels
  (never one concatenated string), plus `View Watch History Log (1)` once a session exists. `Play
  Count: ` reads `N plays` / `1 play` from `sessionCount = if (watchSessions.isNotEmpty())
  watchSessions.size else 1`, so an empty history renders `1 play` — not `0 plays`. The history row is
  clickable, not a button: click `View Watch History Log (1)` by that exact label to expand, and it
  then lists `• Watched on: <date>` rows and ends with either `Show more (N remaining)` or the
  end-of-list indicator.
- **Reset Last Watch Position.** The button only renders while `videoHistory.positionMillis > 0`, so
  seed a position first:

  ```bash
  $S db-push-sql "insert or replace into playback_history \
    (videoId, positionMillis, playedAt, totalWatchTimeMillis, syncVersion) values \
    ('verifyDetail1', 65000, strftime('%s','now') * 1000, 65000, 0)"
  ```

  Relaunch, reopen detail, `scripts/ad.sh scroll down --until 'label="Reset Last Watch Position"' --settle`,
  click it, and confirm in `Reset watch position?` /
  `This clears your saved playback position for this video.` with `Reset` (destructive) or `Cancel`.
  `$S db-query "select positionMillis from playback_history where videoId='verifyDetail1'"`
  must read `0` afterwards — the row is updated, not deleted.
- **Available streams.** `scripts/ad.sh scroll down --until 'label="Available streams:"' --settle`.
  (`scroll bottom` cannot take `--until` — `agent-device` answers `INVALID_ARGS` because it already
  scrolls to the edge; the directional form is the only one that accepts a target.)
  Resolution headers (`1080p`, `720p`, …) group the video streams, then `Audio Only` groups the audio
  ones. Each row's label is `"<typeLabel> • <CODEC>"` when the stream has a codec and the bare
  `typeLabel` when it does not, where `typeLabel` is the quality for video and
  `Audio (<quality>)` — suffixed ` [<language>]` when the stream carries one — for audio. The size is
  `~N.N MB`, and a `video_only` stream with a known audio length reads `~<video> + <audio>`; a
  missing length reads `Resolving size…`. Each row also carries a `Play` icon plus exactly one
  download control: `Download` for a stream with no row, `Downloaded` + `Saved` for `COMPLETED`, a
  disabled `Downloading` for `DOWNLOADING`/`QUEUED`, a disabled `Paused` for `PAUSED`, and
  `Download` again only for `FAILED`/`CANCELED`. `Show more streams (N remaining)` appears only when
  the ladder exceeds the current `streamsLimit` (10, +10 per press); `No streams available` replaces
  the ladder when both lists are empty and the screen is not fetching.
- **Queue a stream.** `scripts/ad.sh click` on a `Download` icon, snackbar `Added to download queue`,
  then `$S db-query "select format, quality, status from downloads where videoId='verifyDetail1' order by id"`
  — a new row in `QUEUED` (the `dbQuality` is `"<quality> (<codec>)"` for video, the bare quality for
  audio). There is no re-tap to try: as soon as the row exists in `QUEUED`/`DOWNLOADING`/`PAUSED`/
  `COMPLETED`, that stream's `Download` icon is gone, replaced by a disabled `Downloading`, `Paused`
  or a `Downloaded` + `Saved` pair. Only `FAILED`/`CANCELED` brings the `Download` icon back, and
  re-enqueueing a duplicate by another path toasts `Download already exists in queue` and writes
  nothing — the unique `(videoId, format, quality)` index would reject it anyway.
- **YouTube.** `scripts/ad.sh click 'label="YouTube"' --settle` fires `ACTION_VIEW` for
  `https://www.youtube.com/watch?v=<videoId>`; `scripts/ad.sh appstate` should no longer name
  MediaNest. Come back with `scripts/ad.sh launch`. On an emulator without a browser this can raise
  `Unable to open YouTube link` instead — record which happened.
- **Crash gate.** `$S crash-check`.
- **Proof.** `ART=$($S evidence-dir video-detail)`, one
  `scripts/ad.sh screenshot "$ART/screens/0N-<state>.png"` per state (hero, downloaded versions,
  watch-count dialog, stats card, stream ladder), a matching `snapshot -i` capture, and
  `$S db-pull "$ART/db"` for the rows.

## Gotchas

- **`videos` has no `isWatched` column.** `VideoEntity` is `id, title, channelName, channelId,
  durationSeconds, thumbnailUrl, description, uploadDate, localFilePath, favorite, addedAt,
  lastPlayedAt, downloadedAt, syncVersion, watchCount, mediaType`. "Watched" is derived —
  `getWatchedVideos()` is `WHERE watchCount > 0` — so a query on `isWatched` fails with
  `no such column`, which reads like a broken schema.
- **Seeded `videos` rows are pruned on the next app start.** `MediaNestApp.onCreate` runs
  `videoDao.deleteSearchedOrphans()`:
  `DELETE FROM videos WHERE favorite = 0 AND lastPlayedAt IS NULL AND (localFilePath = '' OR
  localFilePath IS NULL) AND id NOT IN (SELECT videoId FROM video_folder_join)`.
  A fixture with `favorite = 0` and no `localFilePath` disappears before you can open it.
- **`toggleFavorite` is an `UPDATE`, so it can silently do nothing.** `setFavorite` runs
  `UPDATE videos SET favorite = :favorite WHERE id = :videoId`; with no `videos` row the heart still
  flips while nothing is written. Online extraction normally inserts the row
  (`VideoRepository.searchAndSave`), but a screen opened purely from the in-memory result cache may
  not have one. Always read `videos.favorite` back.
- **The screen is network-aware, and that changes what you see.** `loadVideoInfo` resolves
  `videoDetailLoadMode`: no network → `LocalOnly` (the local row plus stream sources mocked from
  `downloads`, never awaiting extraction); network → `Online` (local row first, then replaced by the
  extracted result); pull-to-refresh → `OnlineRefresh` (never caches the stale local row). So with
  the network on, the seeded title/description/stream ladder can be overwritten mid-run — assert
  after the screen settles, or keep the device offline for fixture-based checks.
- **`Your Statistics` may simply not exist.** It is gated on `videoHistory != null ||
  watchSessions.isNotEmpty() || watchCount > 0`, and `Reset Last Watch Position` additionally needs
  `positionMillis > 0`. An absent card on a fresh fixture is correct behaviour.
- **`Downloaded versions (N)` counts `COMPLETED` rows only**, and the list caps at 10
  (`downloadedLimit`) with `Show more (N remaining)`. A `QUEUED` or `FAILED` download exists in the
  table and never appears here.
- **The delete dialog's title and buttons depend on status.** `Delete Download` with
  `List Only` / `Delete File & List` / `Keep` for finished rows; `Cancel Download` with a single
  `Cancel Download` button for `QUEUED`/`DOWNLOADING`. Asserting one pair unconditionally fails on
  the other state.
- **`Play` and `Delete` are not unique labels.** The downloaded-versions rows, the stream ladder and
  the hero all expose `Play`; `Delete` appears on each downloaded row. Use refs from the settled
  snapshot instead of a bare `label=`.
- **The description body is not a stable assertion target.** It is rendered by an `AndroidView`
  `TextView` from `HtmlCompat.fromHtml`, so it is HTML-flattened, line-clamped to 3 lines until
  expanded, and its exact text belongs to YouTube, not to the app. Assert `Description` and
  `Show more` / `Show less`.
- **`Set Watched` creates a row if the video is missing.** `updateWatchCount` inserts a
  `VideoEntity` when one does not exist, and falls back to `title = "Video (<id>)"`,
  `channelName = "Unknown Channel"` if even that fails. A typo'd fixture id becomes a real, ugly row
  instead of an error.
- **Watch sessions are add-only on increase.** `historyDao.insertWatchSession` runs only when
  `newCount > oldCount`; `View Watch History Log (N)` and `Play Count: N plays` count sessions, not
  the watch count. Reading `watchCount` as the session count is the usual wrong conclusion.
- **Reset clears the position, never the history.** `resetPlaybackPositionForVideo` runs
  `UPDATE playback_history SET positionMillis = 0 WHERE videoId = :videoId`. The `playback_history`
  row (with `playedAt` and `totalWatchTimeMillis`) survives, so `Your Statistics` stays populated.
- **Stream sizes are resolved lazily.** `resolveStreamSizes` issues `HEAD` requests when
  `contentLength` is missing, so a row can read `Resolving size…` for a few seconds per stream. Wait
  for the number instead of asserting the placeholder.
- **Child rows need the parent first.** `downloads`, `playback_history` and `watch_sessions` all carry
  `FOREIGN KEY(videoId) REFERENCES videos(id) ON DELETE CASCADE`, and Room enables FK enforcement at
  runtime. `db-push-sql` runs through plain `sqlite3` (foreign keys off), so a child seeded without
  its `videos` row is accepted silently and then shows up as an orphan: nothing renders it, and
  `deleteSearchedOrphans` does not clean children. Keep the order in the seed above — `videos` before
  `downloads`/`playback_history`.
- **`db-push-sql` force-stops the app and drops the device-side `-wal`/`-shm`.** Relaunch with
  `scripts/ad.sh launch` before driving, and re-open the detail screen — a seeded
  `playback_history` position is only visible after the screen reloads.
