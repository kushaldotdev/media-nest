# App shell and navigation

MediaNest opens on Home and exposes four bottom destinations — Home, Collections, Downloads,
Settings — plus two navigation intents, a download-restart confirmation intent, a floating
mini-player once playback is loaded, and a back gesture that leaves the app from any root
destination without crashing.

## Sub-features

- `shell-launch` — cold start reaches an interactive Home with the bottom nav in the tree.
- `shell-notifications` — the first-install `POST_NOTIFICATIONS` prompt is answered, not left sitting.
- `shell-nav` — each of the four destinations is reachable and swaps the content tree.
- `shell-deeplinks` — `ACTION_NAVIGATE_DOWNLOADS` / `ACTION_NAVIGATE_UPDATES` land where they claim.
- `shell-restart-confirm` — `ACTION_CONFIRM_RESTART` with a `download_id` pauses that download and lands on Downloads.
- `shell-miniplayer` — the bar appears only while a video is loaded, and only off the two player routes.
- `shell-back` — back from a root destination gives up the foreground cleanly.

## How to get to it (user POV)

- Tap the launcher icon, or the app is already in front from a previous run.
- Tap a bottom-nav item: `Home`, `Collections`, `Downloads`, `Settings`. Rendered order is Home,
  Collections, Downloads, Settings — from `MediaNestBottomNavDefaults.Destinations`, which is not
  the declaration order in `BottomNavItem.kt`.
- Tap the `Notifications` bell in the top bar of Home, Collections, Downloads or Settings.
- A download that needs a restart confirmation launches `MainActivity` with
  `com.example.medianest.ACTION_CONFIRM_RESTART` and a `download_id` Long extra.
- External intents: `com.example.medianest.ACTION_NAVIGATE_DOWNLOADS` → Downloads,
  `com.example.medianest.ACTION_NAVIGATE_UPDATES` → Settings (there is no standalone Updates screen).

## Driving it with ad.sh

Preconditions:

- `scripts/nest.sh up` has run and `scripts/nest.sh status` reports `screencap works`.
- `scripts/nest.sh status` names the installed package `com.example.medianest.debug`.
- `scripts/ad.sh` resolves the serial itself through `scripts/lib/common.sh` (including the WSL →
  host adb bridge `ADB_SERVER_SOCKET`), so nothing below takes `-s` or `--serial`.
- One agent-device session at a time. End every run with `scripts/ad.sh close`.
- `scripts/ad.sh launch` does **not** clear logcat. Start a run with `$S logcat-clear`.
- `S=.agents/skills/verify-medianest/scripts/nest-state.sh` — every state check below goes through
  that script, which resolves the same serial as `scripts/ad.sh` (including the WSL → host bridge).

- **Launch.** Run `scripts/ad.sh launch`. It force-starts the app via
  `open com.example.medianest.debug --foreground` and prints the initial interactive snapshot with
  `@e*` refs. `--foreground` makes this the assertion: a launch that does not take the foreground
  fails with `open-not-foreground` instead of passing silently. It is a warm start: the process is
  left running. Pass `--relaunch` to `scripts/ad.sh` when the run needs a forced cold start —
  `--relaunch` terminates the process first; there is no `--restart` flag.
- **Gated readiness.** Run `scripts/ad.sh wait text "Offline Media Hub" 20000`. That subtitle lives
  only in `HomeTopBar`; a node count is a weak gate because a partial tree can be non-empty.
- **Clear the permission prompt.** First install only. Run `scripts/ad.sh alert get` to read the
  buttons, then `scripts/ad.sh alert accept` to grant or `scripts/ad.sh alert dismiss` to decline.
  `MainActivity.onCreate` requests `POST_NOTIFICATIONS` on API 33+. Declining still leaves the app
  usable — record which one happened rather than assuming a dismissal.
- **Snapshot before navigating.** Run `scripts/ad.sh snapshot -i`. The four bottom items are
  siblings labelled `Home`, `Collections`, `Downloads`, `Settings`; copy their `@eN` refs. Screen
  titles repeat the tab labels (Downloads top bar reads `Downloads`, Collections reads
  `Collections`, Settings reads `Settings`), so a bare `label="Downloads"` matches two nodes.
- **Navigate.** For each destination run `scripts/ad.sh click @eN --settle` with the nav ref, then
  assert that destination's own content — never its title:
  - Home → `scripts/ad.sh is visible 'label="Offline Media Hub"'`
  - Collections → `scripts/ad.sh is visible 'label="Organized media, playlists & history"'`. The
    screen opens on the `History` tab (`LibraryViewModel.currentTab` defaults to `LibraryTab.HISTORY`),
    so the six pills read `History`, `Watched`, `Folders`, `Favorites`, `Playlists`, `Channels`, and an
    empty library shows the `No Watch History` empty state. `All Videos` is not a destination gate:
    it is a section header inside the `History` list that only renders when continue-watching is
    non-empty, and folder grids use `Videos (N)` instead.
  - Downloads → `scripts/ad.sh wait text "Offline downloads & queue" 5000` (`Active Queue` only renders when active rows exist)
  - Settings → `scripts/ad.sh is visible 'label="App preferences & data management"'`
  The `--settle` diff is the navigation proof: the old content tree leaves and the new one arrives.
  Re-run `snapshot -i` only when the diff lacks the next target.
- **Deep link — Downloads.** Run `scripts/ad.sh jump downloads`, which sends
  `ACTION_NAVIGATE_DOWNLOADS` to `com.example.medianest.debug/com.example.medianest.MainActivity`,
  then `scripts/ad.sh wait text "Offline downloads & queue" 10000`.
- **Deep link — Updates.** Run `scripts/ad.sh jump updates`, then scroll to the button itself:
  `scripts/ad.sh scroll down --until 'label="Check for Updates"' --settle`, then
  `scripts/ad.sh is visible 'label="Check for Updates"'`. This intent lands on Settings; the
  Updates card is `Software Updates`, not a screen of its own. `scroll bottom` cannot take
  `--until` (`INVALID_ARGS`: it already scrolls to the edge), so the directional form is the only
  one that works.
- **Restart-confirm intent.** `scripts/ad.sh jump` only covers the two navigation actions, so send
  this one by hand with a real row's id. Source `scripts/lib/common.sh` first — under WSL the device
  is the Windows-host emulator and the bare `adb` needs the same bridge `scripts/ad.sh` uses:

  ```bash
  . scripts/lib/common.sh            # ADB_SERVER_SOCKET bridge; no-op on native Linux
  adb -s "$(nest_device_serial)" shell am start \
    --activity-single-top --activity-clear-top \
    -n com.example.medianest.debug/com.example.medianest.MainActivity \
    -a com.example.medianest.ACTION_CONFIRM_RESTART --el download_id 7
  ```

  Expect the Downloads destination plus the `Restart Download` dialog
  (`Are you sure you want to restart downloading "…"? This will start downloading from scratch.`
  with `Restart` / `Cancel`), and that row flipped to `PAUSED`. Two preconditions: the row must be
  present in the collected list — `DownloadsScreen` waits for the id to match a loaded `downloads`
  row, so a bogus id navigates and shows nothing — and `MainActivity` calls
  `DownloadService.ACTION_PAUSE` before the dialog, so only a row that can be paused (`QUEUED`,
  `DOWNLOADING`, `PAUSED`) is meaningful; a `COMPLETED` row is rewritten to `PAUSED` by that call.
- **Mini-player.** Load playback first (see [playback-and-player](playback-and-player.md) and
  [video-detail](video-detail.md)), then navigate back to Home and run `scripts/ad.sh snapshot -i`.
  The bar carries `Play`/`Pause`, `Close` and a progress strip that is **two stacked**
  `LinearProgressIndicator`s (a buffer track plus a `YouTubeRed` position bar). `Previous` / `Next`
  only exist when the queue is non-empty (`if (q.isNotEmpty())`) and are disabled unless
  `canPrev` / `canNext` — a single-video session shows a bar with neither. The bar is suppressed on
  `player/{videoId}?streamIndex=` and `downloads/player/{videoId}?downloadId=`.
- **Back from a root destination.** Run `scripts/ad.sh back --settle`, then `scripts/ad.sh appstate`.
  Root means a bottom destination with nothing pushed on top. Any foreground app afterwards is a
  pass — the assertion is that MediaNest gave up the foreground, not where it went.
- **State proof.** `scripts/ad.sh launch` twice in one run must show the same database, so read it:
  `$S db-query "select name from sqlite_master where type='table' and name not like 'sqlite_%' order by name"`
  must list the Room v18 tables — `app_notifications`, `bulk_download_items`, `bulk_download_jobs`,
  `downloads`, `folders`, `link_history`, `playback_history`, `playlists`, `subscriptions`,
  `video_folder_join`, `videos`, `watch_sessions`. `$S db-count videos` is the cheap "the app really
  opened its DB" check.
- **Post-intent proof.** After the restart intent,
  `$S db-query "select id, status from downloads order by id"` must show that id as `PAUSED`.
- **Crash gate.** Run `$S crash-check`. Pass means no `FATAL EXCEPTION` in logcat, no `ANR in
  com.example.medianest.debug` in `dumpsys activity`, and no native `Fatal signal` block naming the
  package's 15-char process name.
- **Proof.** `$S evidence-dir shell` prints the run directory; write one
  `scripts/ad.sh screenshot <dir>/screens/0N-<destination>.png` per destination and keep the
  matching `snapshot -i` output beside it. A black or missing frame fails the run — semantics alone
  is not proof here.

## Gotchas

- **Bottom nav items, Collections pills, and filter chips surface as `[group]` nodes.**
  `MediaNestBottomNavigationItemView` uses `Modifier.clickable(interactionSource, indication, onClick)`
  with no `Role`, and even `PillTabRow` / `FilterChip` surface in `agent-device` snapshots as
  `[group]`, never as a tab role. Select nav items, pills, and filter chips by their `@ref` from
  `snapshot -i`.
- **Titles duplicate nav labels.** `Downloads` is both the top-bar title and the nav label,
  `Collections` likewise, `Settings` likewise. An unscoped `label=` selector returns
  `AMBIGUOUS_MATCH`; pick the ref.
- **Rendered nav order ≠ `BottomNavItem` declaration order.** The bar iterates
  `MediaNestBottomNavDefaults.Destinations` (Home, Collections, Downloads, Settings) while the
  sealed class declares Home, Downloads, Collections, Settings. Do not address items by ordinal.
- **The mini-player is state, not a target.** It needs `playerUiState.videoId != null`; its own
  `Close` calls `stopPlayback()` and clears it. "Mini-player missing" after a close is correct
  behaviour, not a broken shell.
- **`broadcast`/intent actions self-clear.** `MainActivity.handleRestartIntent` sets
  `intent.action = null` after handling each action, so re-sending the intent is required to
  re-navigate; an `onNewIntent` with the same action is not a no-op test.
- **`ACTION_CONFIRM_RESTART` talks to a foreground service first.** It sends
  `DownloadService.ACTION_PAUSE` before showing the dialog, and a failure is only logged as
  `Failed to pause download before confirmation dialog`. If the row did not flip, read logcat
  before blaming the navigation.
- **Back from root is not "lands on the launcher".** Whatever was underneath comes back — the
  launcher, or the dialer, or another app. Requiring `NexusLauncher` turns a correct run red.
- **The first-install prompt is a system dialog.** `alert` resolves platform alerts only; if
  `alert get` reports none while the app is not interactive, the prompt was already answered or the
  app has not painted yet — re-run `wait text "Offline Media Hub" 20000`.
- **`uiautomator` idle failures are not app bugs.** "could not get idle state" during a nav
  animation resolves on retry; `--settle` exists to absorb exactly that.
