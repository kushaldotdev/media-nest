# Playback and player

The player is where a video or audio item actually plays: a Media3 surface with transport
controls, speed and quality selectors, a resume banner, an `Up Next` queue, a fullscreen mode, and
a minimisable bar that keeps the media playing while the user browses the rest of the app.

## Sub-features

- `play-open` — reach the player from Video detail (`Play Video`) or a completed download (`Play`)
  and get a playing surface.
- `play-controls` — `Rewind 30s`, `Rewind 5s`, `Previous Track`, `Play` / `Pause`, `Next Track`,
  `Forward 5s`, `Forward 30s`, plus the top-bar `Back` and `Fullscreen`.
- `play-speed` — the `Playback Speed` segment cycles one step per tap and opens a menu of
  `0.25x`…`2.0x` on long-press; the choice persists in `playback_prefs`.
- `play-quality` — the `Video Quality` segment cycles stream resolutions and lists them with a
  `Downloaded` mark on long-press (absent for audio-only media).
- `play-queue` — the `Up Next` sheet: `NOW PLAYING`, per-row `Move Up` / `Move Down` /
  `Remove from Queue`, `Autoplay` switch, `Nothing in the queue` when empty.
- `play-resume` — the `Last watched: <mm:ss>` banner with `Resume` / `Update` / a `Dismiss` icon.
- `play-errors` — the portrait error overlay with `Retry` and `Tap to dismiss`.
- `play-fullscreen` — landscape rotation, hidden system bars, a self-hiding control overlay and
  `Exit Fullscreen`.
- `play-miniplayer` — the floating bar on any non-player route: `Play`/`Pause`, `Previous`, `Next`,
  `Close`.
- `play-persist` — `videos.watchCount`, `videos.lastPlayedAt`, a `watch_sessions` row and
  `playback_history.positionMillis` are written; the media session service owns playback.

## How to get to it (user POV)

- **Video detail** — the big centre `Play Video` icon over the thumbnail, or the play control on a
  downloaded version in the local-versions list. This is the entry point for every streamed video.
- **Downloads** — `Play` on a `Completed Downloads` card. This is the offline path (local file, no
  network) and it opens the offline player route rather than the streaming one.
- **Channel or playlist detail** — a channel/playlist opened from `Collections` → `Playlists` or
  `Collections` → `Channels` is the only list that reaches the player and fills the `Up Next`
  queue; its per-item play control starts playback from the middle of that list.
- **Not entry points:** video cards on `Home`, on the History / Favorites / Watched / Folders tabs
  of `Collections`, in `Notifications`, and in `Statistics` all open Video detail. The player is one
  tap further in, and playback started that way carries **no queue**.
- **MiniPlayer** — while something is loaded, leave the player with the top-bar `Back`: the bar
  appears on **every** route except the two player routes (Home, Collections, Downloads, Settings,
  Video detail, Notifications, Statistics), and tapping it reopens the player.

## Driving it with ad.sh

Preconditions:

- `scripts/nest.sh up` ran and `scripts/nest.sh status` reports `screencap works`.
- `scripts/ad.sh launch` succeeded; `.agents/skills/verify-medianest/scripts/nest-state.sh doctor`
  passes (`run-as` works) so the persistence assertions below are possible.
- One agent-device session at a time; end the run with `scripts/ad.sh close`, and start it with
  `$S logcat-clear` when you intend to use `crash-check`.
- Test media exists. **Prefer the offline path**: a seeded local row plays with no network.
  Streamed playback, quality switching between online streams and live metadata need YouTube and
  are **not driven** when the emulator is offline — say so instead of inferring them.

Two shorthands, because the seed block and the dumpsys checks need adb while WSL must borrow the
host's adb server:

```bash
S=.agents/skills/verify-medianest/scripts/nest-state.sh
. scripts/lib/common.sh     # exports ADB_SERVER_SOCKET on WSL; bare `adb` then reaches the device
```

- **Seed a deterministic local video** (offline-safe, and the fastest way to reach every control).
  The clip must be **longer than 60 s**: the watch counter fires as soon as the remaining time is
  ≤ 60 s, so a 20 s clip is already "watched" at t=0 and every seek assertion below would pass for
  the wrong reason. `db-push-sql` force-stops the app, so relaunch afterwards.

  ```bash
  # 90 s, ~1 MB: real H.264/AAC so Media3 actually decodes it
  [ -f /tmp/verify-clip.mp4 ] || ffmpeg -y -v error \
    -f lavfi -i testsrc=size=320x180:rate=15 -f lavfi -i sine=frequency=440 \
    -t 90 -c:v libx264 -pix_fmt yuv420p -preset veryfast -c:a aac -b:a 64k \
    -movflags +faststart /tmp/verify-clip.mp4
  adb push /tmp/verify-clip.mp4 /data/local/tmp/verify-clip.mp4
  # `run-as` starts in the app data dir; piping writes the file as the app uid.
  # A `run-as cp` from /data/local/tmp trips SELinux/0600 on some builds.
  adb shell "cat /data/local/tmp/verify-clip.mp4 | run-as com.example.medianest.debug \
    sh -c 'cat > files/verify-clip.mp4 && chmod 600 files/verify-clip.mp4'"
  $S db-push-sql "
  insert or replace into videos (id,title,channelName,channelId,durationSeconds,thumbnailUrl,
    description,uploadDate,localFilePath,favorite,addedAt,lastPlayedAt,downloadedAt,syncVersion,
    watchCount,mediaType)
  values ('verify-clip','Verify Clip','Verify Channel',NULL,90,NULL,NULL,NULL,
    '/data/data/com.example.medianest.debug/files/verify-clip.mp4',0,1700000000000,NULL,
    1700000000000,0,0,'VIDEO');
  delete from downloads where videoId='verify-clip';
  insert into downloads (downloadUuid,outputRoot,videoId,url,format,quality,title,filePath,
    fileSizeBytes,downloadedAt,status,progress,retryCount,updatedAt,syncVersion)
  values ('00000000-0000-4000-8000-000000000001',
    '/data/data/com.example.medianest.debug/files','verify-clip','','video','360p',
    'Verify Clip','/data/data/com.example.medianest.debug/files/verify-clip.mp4',1012551,
    1700000000000,'COMPLETED',1.0,0,1700000000000,0);
  "
  scripts/ad.sh launch
  ```

  `format='audio'` or `'audio_extracted'` on the download row exercises the audio-only layout. The
  `videos` row must exist first — `downloads.videoId` is a foreign key to `videos`.
- **Open the player from Downloads.** Navigate to Downloads, find the seeded card, and tap its
  `Play` button (`label="Play"`) — the card action, not the title (the title toggles truncation).
  Sources whose file is missing show `Source missing` + `Redownload` and no `Play`, and such rows
  render in `Active Queue`, not `Completed Downloads`; if `Play` is absent, re-check the seed
  before blaming the player. Then take `scripts/ad.sh snapshot -i` and assert the transport row.
  The top bar carries a quality chip reading `<quality> • Local`, which proves local playback.
- **Portrait layout has no hidden overlay.** Everything below the video box is rendered
  permanently: the slider, the time row, the transport row, and the segment bar with
  `Playback Speed` (plus `Video Quality` and the queue control where they apply). A snapshot taken
  at any moment shows
  them; there is no fade and no "tap to reveal". Tapping the video/artwork surface **toggles
  play/pause**, and a double-tap on the left/right half seeks −10 s/+10 s.
- **Transport.** Every control is `content-desc`, so `label=` is the selector:
  `Rewind 30s`, `Rewind 5s`, `Previous Track`, `Play` / `Pause`, `Next Track`, `Forward 5s`,
  `Forward 30s`. Tap `Pause` and prove both layers:
  `scripts/ad.sh wait 'label="Play"' 3000` and
  `adb shell dumpsys media_session | grep -i "state=PlaybackState {state"` flipping from `3`
  (playing) to `2` (paused).
- **Seek.** `scripts/ad.sh click 'label="Forward 30s"' --settle`. The skip buttons persist
  immediately, so `$S db-query "select positionMillis from playback_history where videoId='verify-clip'"`
  must show the new position within about a second. Dragging the **slider** seeks on release and
  is picked up by the one-second position tracker rather than by the drag itself; either way the
  stored value never goes **down**, so a rewind leaves the old, larger position in the database.
  Assertions must be `>=`, never `==`.
- **Speed.** The `Playback Speed` segment advances one step **per tap** (0.25 → 0.5 → 0.75 → 1.0 →
  1.25 → 1.5 → 2.0, wrapping) and persists immediately; a long-press opens the full menu.
  Tap `scripts/ad.sh click @e<speed-segment> --settle` twice from `1.0x` and assert the segment
  reads `1.5x`, then `$S pref-get playback_prefs playback_speed` → `1.5`. For a specific value use
  `scripts/ad.sh longpress @e<speed-segment> --settle` then
  `scripts/ad.sh click 'label="0.5x"' --settle`. Restoring `1.0x` keeps the next run's baseline
  sane.
- **Quality.** The `Video Quality` segment renders only for video media. It is disabled when no
  stream list is known — the normal case for a purely local download, where the only quality is
  the one on the row. A long-press lists the resolutions and marks a locally available one with a
  `Downloaded` icon. For local-only media record "quality menu unavailable — no stream sources"
  instead of failing.
- **Queue and autoplay — only from a subscription list.** The queue control (`Up Next queue` icon,
  with the text `Queue` or `Queue (<n>)`) renders only when the player was opened with a list
  context, and the only code path that supplies one is the channel/playlist detail screen under
  `Collections` → `Playlists` / `Channels`. Video cards on Home, History, Favorites, Watched and
  Folders open Video detail and seed nothing. So: `Collections` → `Playlists` → open a playlist →
  press play on an item, then
  `scripts/ad.sh click 'label="Up Next queue"' --settle`. The sheet shows `Up Next`, the item
  count, a `Full titles` switch, `Autoplay` with a `role=switch label="Autoplay"` control,
  `NOW PLAYING` on the current row, and per-row `Move Up`, `Move Down`, `Remove from Queue`.
  Prove the toggle: `scripts/ad.sh is checked 'role=switch label="Autoplay"'`, flip it, read it
  again. Removing the last item leaves `Nothing in the queue`. Playing a single item from Downloads
  has no queue context at all, which is why the control is absent there — record that as the
  correct result for that entry point.
- **Resume banner.** It renders only when the saved position is between 5 s and
  `duration − 10 s` and the banner has not been dismissed. Drive it deterministically by seeding
  the history row before opening the player:

  ```bash
  $S db-push-sql "insert or replace into playback_history
    (videoId,positionMillis,playedAt,totalWatchTimeMillis,syncVersion)
    values ('verify-clip',8000,1700000000000,8000,0);"
  scripts/ad.sh launch        # push force-stopped the app
  ```

  Open the seeded clip and assert the banner with a partial match — `wait text` matches the whole
  field and the banner text is `Last watched: <mm:ss>`:
  `scripts/ad.sh find text "Last watched" exists`. `Resume` seeks to the saved position and clears
  it; `Update` writes the **current** position as the saved one, and because it also resets the
  high-water mark it can move the stored position **backwards** — the one action that lowers
  `positionMillis`; the close icon (`content-desc "Dismiss"`) hides the banner without touching
  history. All three are real outcomes — record which one was exercised.
- **Error overlay — portrait only.** Delete the media behind a loaded session (the file under
  `files/`, or a download row whose `filePath` no longer exists) and reopen it through Video detail
  → `Play Video`, or open a bogus id offline. The portrait layout then shows an error scrim with
  the message, a `Retry` button and `Tap to dismiss`; tapping the scrim clears the error. `Retry`
  re-runs the load for the same source, so a `Retry` on a still missing file returns to the same
  overlay — that is a correct result, not a stuck retry. Fullscreen has no error overlay of its
  own; leave fullscreen to read it.
- **Fullscreen — the only place controls hide.** `scripts/ad.sh click 'label="Fullscreen"' --settle`.
  The activity rotates to sensor landscape and hides the system bars, so the tree changes shape and
  **every ref from before the rotation is stale** — `scripts/ad.sh snapshot -i` again. In here a
  tap on the video toggles the control overlay, and the overlay fades itself about 3 s after the
  last interaction while playing. If the transport row is missing, tap the video area and
  re-snapshot. The overlay carries `Back` (which exits fullscreen, not the player), the title and
  watch count, the quality chip, `Exit Fullscreen`, and a larger transport row — but **no segment
  bar**: `Playback Speed`, `Video Quality` and the queue control do not render in fullscreen.
  Change speed or quality in portrait, then enter fullscreen.
- **MiniPlayer.** With media loaded, tap the portrait top-bar `Back`
  (`scripts/ad.sh click @e<top-bar-back> --settle`) to return to the previous route. The bar
  renders at the bottom of whatever route you landed on (above the bottom navigation where that
  route has one) with `Play`/`Pause` and `Close`, plus a progress strip;
  `Previous` and `Next` appear **only when a queue exists**. Assert presence with
  `scripts/ad.sh wait 'label="Close"' 5000`.
- **MiniPlayer close.** `scripts/ad.sh click 'label="Close"' --settle` stops playback: the bar
  disappears and the loaded media is cleared. Assert
  `scripts/ad.sh wait absent 'label="Close"' 5000` — `Close` is the one control that is always
  present while the bar is up, so it is the only honest marker. Waiting for `Previous` to vanish
  passes vacuously, because a queue-less session never rendered it. Record that media must be
  reopened.
- **Watch count and history — mid-clip exit must not count.** The counter increments at most once
  per session, and only once the remaining time is ≤ 60 s (or playback ends) with
  `playback_prefs auto_mark_watched` true. On the 90 s clip, seek past 30 s:
  `scripts/ad.sh click 'label="Forward 30s"' --settle`, wait a second, then read:

  ```bash
  $S db-query "select watchCount,lastPlayedAt from videos where id='verify-clip'"
  $S db-count watch_sessions "videoId='verify-clip'"
  $S db-query "select positionMillis,totalWatchTimeMillis from playback_history where videoId='verify-clip'"
  ```

  Expect `watchCount` → `1` and one `watch_sessions` row. Then re-seed `watchCount = 0`, delete the
  watch-session rows, open the clip, let it play **less than 30 s**, leave the player, and assert
  `watchCount` is still `0` with **no** `watch_sessions` row — a mid-clip exit writes history but
  not a view. Without that negative case the whole assertion is untested.
- **Media session / foreground service.** While playing:

  ```bash
  adb shell dumpsys media_session | grep -i medianest
  adb shell dumpsys activity services com.example.medianest.debug | grep -i PlaybackService
  ```

  The service is declared with `foregroundServiceType="mediaPlayback"` and the Media3
  `MediaSessionService` intent filter; its presence with an active session is the background
  playback proof. It stops itself when the task is removed while nothing is playing.
- **Crash gate.** `$S crash-check` (no `FATAL EXCEPTION`, no `ANR`).
- **Evidence.** `ART=$($S evidence-dir playback)`; per step keep a
  `scripts/ad.sh screenshot "$ART/screens/…"` plus the matching `snapshot -i`, and use
  `scripts/ad.sh record start "$ART/run.mp4"` / `record stop` around the transport sequence.
- **Cleanup — delete every layer explicitly.** A UI delete removes the download row and never the
  video row, and `db-push-sql` does not enable foreign keys, so a bare `delete from videos` leaves
  orphans behind. Do it in order:

  ```bash
  $S db-push-sql "PRAGMA foreign_keys = ON;
    delete from downloads where videoId='verify-clip';
    delete from playback_history where videoId='verify-clip';
    delete from watch_sessions where videoId='verify-clip';
    delete from videos where id='verify-clip';"
  adb shell rm -f /data/local/tmp/verify-clip.mp4
  adb shell run-as com.example.medianest.debug rm -f files/verify-clip.mp4
  scripts/ad.sh launch
  ```

  Then `$S db-count videos "id='verify-clip'"` → `0`,
  `$S db-count downloads "videoId='verify-clip'"` → `0`. `db-push-sql` force-stops the app; a
  player session that dies with it is expected.

## Gotchas

- **Portrait never hides its controls.** The transport row, slider, speed/quality/queue bar and
  resume banner are always rendered below the video box; only fullscreen uses a fading overlay.
  "The buttons vanished" in portrait means the media finished or an error scrim is covering them,
  not that an overlay timed out.
- **Tapping the video in portrait toggles playback, it does not reveal controls.** A single tap
  starts or pauses; a **double-tap** on the left/right half seeks −10 s/+10 s. An accidental second
  `click` pauses the media and invalidates every following assertion.
- **Fullscreen rotates the device.** Entry requests landscape and hides the system bars, exit
  restores both. Refs do not survive the rotation — snapshot again after every enter/exit.
- **Two different `Back` controls.** In portrait the top-bar back leaves the player; in fullscreen
  the overlay's `Back` only exits fullscreen, and `Exit Fullscreen` does the same. `scripts/ad.sh
  back` sends a system BACK, which pops the route — a third, different behaviour.
- **`Play` and `Pause` are the same node.** The label flips with state, so after a tap wait for the
  *other* label instead of trusting the tap's exit code.
- **`Previous Track` / `Next Track` are conditional.** They render only with a non-empty queue and
  are disabled at the ends. Their absence on a single downloaded video is correct.
- **The queue needs a subscription list.** Only a channel/playlist detail screen under
  `Collections` supplies a queue. Home, History, Favorites, Watched, Folders, Notifications and
  Statistics all reach the player through Video detail with an empty queue, so `Queue` and
  `Autoplay` are legitimately absent there. Do not report that as a missing feature.
- **A short clip is "watched" from the start.** The counter fires when the remaining time is ≤ 60 s,
  so any clip of 60 s or less increments on the first tick and every "seeking marks it watched"
  claim proves nothing. Seed **more than** 60 s, and keep the mid-clip negative case.
- **The saved position only moves forward — except from `Update`.** Ordinary saves keep a
  high-water mark, so a rewind leaves the larger stored value in place. The resume banner's
  `Update` resets that mark to the current position and is the one path that can move
  `positionMillis` **downwards**. An `==` assertion on a rewound position fails against correct
  code.
- **`Resume` and `Dismiss` both clear the banner, differently.** `Resume` seeks to the saved
  position *and* clears it; the `Dismiss` icon hides the banner and also clears the pending
  position without seeking. Only `Update` rewrites history from the current position.
- **The resume banner needs a middle position.** It renders only for
  `5 s < position < duration − 10 s` and only while it has not been dismissed: a fresh clip shows
  nothing, an almost-finished one shows nothing. Seed `playback_history` to drive it.
- **The error overlay is portrait-only.** Fullscreen has no error surface, so an error raised while
  fullscreen is only readable after leaving it. And a downloaded row whose file vanished is not a
  playback error at all — that row moves into `Active Queue` with `Source missing` and
  `Redownload` instead of `Play`, so it cannot be played until it is downloaded again.
- **Speed is a segment, not a button.** One tap cycles a step and persists; the list of
  `0.25x`…`2.0x` needs a long-press. Using `click` when you wanted a specific value walks the speed
  away from the baseline.
- **Quality may have nothing to choose.** The segment is absent for audio-only media and disabled
  when no stream list is known (a purely local download). Report that state rather than calling the
  control broken.
- **Audio-only keeps the full control set.** For `format='audio'` / `'audio_extracted'` the artwork
  replaces the video box (a tap on it toggles play/pause) and the same slider, transport row,
  `Playback Speed` and queue bar render below it in portrait. Only the quality segment disappears.
  In fullscreen an audio item shows the artwork plus the transport row and `Exit Fullscreen`, and no
  segment bar at all — same as video.
- **MiniPlayer is state, not a route.** It appears only while a media item is loaded and only off
  the two player routes. `Close` stops playback and clears that state — a bar that stays after
  `Close` is the bug; a bar that vanishes is not. `Close` is also the name of the sort-sheet and
  settings icon, so check the bar is actually the only `Close` visible, or take the ref.
- **`db-push-sql` force-stops the app.** Any player or download session dies with it; relaunch and
  re-open media before continuing, and never attribute the killed session to a playback bug.
- **Writing into the app dir needs the pipe, not `cp`.** `run-as … cp /data/local/tmp/…` fails on
  some builds; `cat … | run-as … sh -c 'cat > …'` writes as the app uid.
- **Network is a real precondition for streaming.** Seeded local playback needs none; streamed
  playback, quality switching between online streams and live metadata do. Report those as **not
  driven** rather than inferring them from a compiled build.
