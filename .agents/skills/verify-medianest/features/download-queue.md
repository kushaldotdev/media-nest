# Download queue

Downloads turns background work into visible rows: an `Active Queue` of running, queued, paused,
failed and canceled jobs, a `Completed Downloads` list with per-item playback, audio extraction and
deletion, and a `Storage & Status` card summarising counts and bytes.

## Sub-features

- `dl-empty` — with no rows the screen renders `No downloads yet` instead of a blank list.
- `dl-toolbar` — sort button + `Sort By` sheet, `Max: N` concurrency dialog (1–5), `Full titles`.
- `dl-active` — `Active Queue` cards expose the actions for their own status: `Pause` + `Cancel`
  (queued/downloading), `Resume` + `Restart` + `Delete` (paused), `Retry` + `Delete` (failed),
  `Restart` + `Delete` (canceled), `Redownload` + `Delete` (missing file).
- `dl-bulk` — `Pause All` / `Resume All`, and `Batch Select Mode` → `Select All` /
  `Deselect All` / `Delete` over the completed list.
- `dl-completed` — `Completed Downloads` with `All` / `Video` / `Audio` filter chips, `Play`,
  `Extract Audio`, the delete icon, and `Source missing` + `Redownload` for missing files.
- `dl-storage` — `Storage & Status` card: `N items · <size>`, a status pill per present state and
  a `Completed Storage` footer.
- `dl-persist` — rows survive relaunch in the `downloads` table; the foreground download service
  and its notification reflect the queue.

## How to get to it (user POV)

- Tap `Downloads` in the bottom navigation.
- Deep link: `scripts/ad.sh jump downloads` sends
  `com.example.medianest.ACTION_NAVIGATE_DOWNLOADS`.
- A download that needs a restart confirmation opens the app on Downloads with the restart dialog
  (`com.example.medianest.ACTION_CONFIRM_RESTART` plus a `download_id` Long extra).
- Start a download from Home or Video detail; the new row appears here immediately as `QUEUED`.

## Driving it with ad.sh

Preconditions:

- `scripts/nest.sh up` ran and `scripts/nest.sh status` reports `screencap works`.
- `scripts/ad.sh launch` succeeded; `scripts/nest.sh status` names `com.example.medianest.debug`.
- `.agents/skills/verify-medianest/scripts/nest-state.sh doctor` passes (`run-as` works), so the DB
  assertions below are possible.
- One agent-device session at a time; end the run with `scripts/ad.sh close`. `scripts/ad.sh launch`
  does not clear logcat, so start the run with `$S logcat-clear` (shorthand below) — otherwise
  `crash-check` reports crashes from older runs.
- Record whether the account already has downloads: a fresh install has none and every assertion
  built on an existing row is then **not driven**, not failed.

Two shorthands, because the seed block and the dumpsys checks need adb while WSL must borrow the
host's adb server:

```bash
S=.agents/skills/verify-medianest/scripts/nest-state.sh
. scripts/lib/common.sh     # exports ADB_SERVER_SOCKET on WSL; bare `adb` then reaches the device
```

- **Land on the screen.** Run `scripts/ad.sh launch` (or `scripts/ad.sh jump downloads`), then
  `scripts/ad.sh wait text "Downloads" 10000` and `scripts/ad.sh snapshot -i`. The top bar is
  `Downloads` / `Offline downloads & queue`; the subtitle disambiguates it from the nav label.
- **Empty state.** Only when `$S db-count downloads` is `0`, run
  `scripts/ad.sh wait text "No downloads yet" 5000`. That string is the empty-queue marker;
  `Active Queue` does **not** render when nothing is active, and `Completed Downloads` does not
  render when nothing is completed.
- **Toolbar.** `scripts/ad.sh snapshot -i`. The toolbar is always present: a sort button rendered as
  `[button] "Sort, Date"` (the second part is the current sort category, `Date` by default), a
  concurrency button rendered as `[button] "Max concurrent, Max: N"` (default `Max: 2`), and a
  `Full titles` switch (`[group] "Full titles" [checked|unchecked]`).
- **Sort.** Tap the category button (by its `@eN` ref from `snapshot -i`, or `label="Date"`,
  `label="Progress"`, `label="Size"`, `label="Status"` which taps the inner text cleanly):
  `scripts/ad.sh click 'label="Date"' --settle`. The `Sort By` sheet opens with a `Close` icon,
  then `SORT BY` (`Date Added`, `Progress`, `File Size`, `Download Status`), then `ORDER` with
  `Descending (↓)` / `Ascending (↑)`, then the `Full titles` switch, then `Cancel` / `Apply`.
  Tap `label="File Size"`, `label="Descending (↓)"`, then
  `scripts/ad.sh click 'label="Apply"' --settle`, and assert the toolbar button now reads `Size`.
  This is persisted and is the one sort choice on this screen that survives a relaunch:
  `$S pref-get download_prefs sort_mode` → `SIZE_DESC`.
- **Concurrency.** Tap `scripts/ad.sh find text "Max:" click` (the visible label changes with the
  value, so a whole-field `label="Max: 2"` is brittle). The dialog title is
  `Max Concurrent Downloads`; options are `1 concurrent download` … `5 concurrent downloads`.
  Tap `scripts/ad.sh click 'label="4 concurrent downloads"' --settle`, then assert
  `$S pref-get download_prefs max_concurrent` → `4` and the button reads `Max: 4`. The dialog
  dismisses itself on selection; `Close` is only for backing out.
- **Full titles — session-local.** Run `scripts/ad.sh snapshot -i`, take the toolbar's
  `[group] "Full titles" [checked|unchecked]` `@eN` ref (do **not** use switch-role selectors,
  `is checked 'role=group label="Full titles"'`, or `is checked @eN`), flip it with
  `scripts/ad.sh click @eN --settle`, and assert the `- [checked] / + [unchecked]` diff (or
  re-snapshot `-i`). The observable result is **row rendering**: with it off, a long title is one
  truncated line on the cards; with it on, the title wraps. Do **not** assert a DataStore value —
  this toggle is local screen state and writes nothing, so `$S pref-bool collections_prefs full_titles`
  stays at the last value Settings wrote regardless of what the card shows. Reopening the screen
  resets the switch to the global value.
- **Active queue states.** With rows in `QUEUED` / `DOWNLOADING`, assert `Active Queue` plus the
  per-card actions read from source: `Pause` + `Cancel` (queued/downloading), `Resume` +
  `Restart` + `Delete` (paused), `Retry` + `Delete` (failed), `Restart` + `Delete` (canceled),
  `Redownload` + `Delete` (completed but missing on disk). Pause a live row with
  `scripts/ad.sh click 'label="Pause"' --settle` and prove the persisted state:
  `$S db-count downloads "id=<id> and status='PAUSED'"` → `1`. `Restart` does not act directly — it
  opens a `Restart Download` confirmation ("… start downloading from scratch.") with `Restart` /
  `Cancel`, so a tap alone changes nothing in the database.
- **Bulk pause/resume.** `Pause All` renders only when a row is `DOWNLOADING`/`QUEUED`, and
  `Resume All` only when one is `PAUSED`; both are conditional, so assert only the one the DB
  state justifies. Probe first:
  `$S db-count downloads "status in ('QUEUED','DOWNLOADING')"` and
  `$S db-count downloads "status='PAUSED'"`. A missing button with no matching rows is correct
  behaviour, and a tap on an empty queue proves nothing.
- **Batch select.** With at least one completed row, tap the `Batch Select Mode` icon
  (its `@eN` ref or `label="Batch Select Mode"`), then assert the `Select All` (or `Deselect All`)
  toggle, `<n> selected`, and `Delete`. `Select All` selects only the rows passing the current chip
  filter. Run `scripts/ad.sh snapshot -i` to take the batch bar's `Delete` `@eN` ref and run
  `scripts/ad.sh click @eN --settle` — do **not** run `click 'label="Delete"'`, which fails with
  `AMBIGUOUS_MATCH` against the per-card `Delete` icons. It opens a dialog titled
  `Delete 1 Download` (plural `Delete N Downloads`) offering `List Only` /
  `Delete Files & List` / `Cancel`; `List Only` removes rows and keeps the files,
  `Delete Files & List` removes both. Prove the row is gone:
  `$S db-count downloads "id=<id>"` → `0`.
- **Completed item actions.** `Completed Downloads` carries a count badge and `All` / `Video` /
  `Audio` chips; each completed card offers `Play` (or `Pause` while it is the current media),
  `Extract Audio` when the row is video without an extraction, `Left off at <mm:ss>` when there is
  history, and a trash icon whose `content-desc` is `Delete`. Tapping the trash on a completed row
  opens `Delete Download` — "Choose how you want to delete …" — with `List Only` /
  `Delete File & List` / `Keep`.
- **Only `DOWNLOADING`/`QUEUED` say "Cancel".** Those two statuses open a dialog titled
  `Cancel Download` ("Are you sure you want to cancel downloading …") whose confirm button is also
  `Cancel Download` and whose dismiss is `Keep`. `PAUSED`, `FAILED`, `CANCELED` and completed rows
  all open `Delete Download` with `List Only` / `Delete File & List` / `Keep`. Asserting
  `Cancel Download` on a paused row reads red against correct code.
- **Extract audio.** Tap `Extract Audio` on a completed video row; the control disables while that
  video extracts and a **new** `audio_extracted` row appears. Prove both layers:
  `$S db-count downloads "videoId='<videoId>' and format='audio_extracted'"` → `1`, then
  `$S db-query "select format,filePath,status from downloads where videoId='<videoId>'"` shows a
  non-empty `filePath` once it completes. The original video row is untouched, and the button
  disappears from the video card once the sibling exists.
- **Storage card.** With any rows, assert `Storage & Status`, the `N items · <size>` line, and one
  pill per present state (`Downloading · N`, `Queued · N`, `Paused · N`, `Failed · N`,
  `Completed · N`). The `Completed Storage` footer renders only when the completed rows carry
  bytes — a queue made purely of queued rows legitimately has no footer.
- **Foreground service + notification.** While a job is running:
  ```bash
  adb shell dumpsys activity services com.example.medianest.debug | grep -i DownloadService
  adb shell dumpsys notification --noredact | grep -i Downloads
  ```
  The service entry is the foreground-service proof; the notification channel id is `downloads`.
  A completed-only queue may show no foreground service — that is not a failure, say which state
  you observed.
- **Deterministic completed row (no network).** Live downloads need yt-dlp plus YouTube. To drive
  the completed-card path offline, generate one real clip, stream it into the app's data dir and
  seed two rows. `db-push-sql` force-stops the app, so relaunch afterwards.
  ```bash
  # 90 s: long enough that the player's 60 s "watched" rule is not already satisfied at t=0
  [ -f /tmp/verify-clip.mp4 ] || ffmpeg -y -v error \
    -f lavfi -i testsrc=size=320x180:rate=15 -f lavfi -i sine=frequency=440 \
    -t 90 -c:v libx264 -pix_fmt yuv420p -preset veryfast -c:a aac -b:a 64k \
    -movflags +faststart /tmp/verify-clip.mp4
  adb push /tmp/verify-clip.mp4 /data/local/tmp/verify-clip.mp4
  # `run-as` starts in the app data dir; piping writes the file as the app uid (a `cp` from
  # /data/local/tmp trips SELinux/0600 on some builds).
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
  Downloads then shows `Completed Downloads` with the seeded card. Flip the same row to a broken
  state with `$S db-push-sql "update downloads set filePath='/data/data/com.example.medianest.debug/files/gone.mp4'
  where videoId='verify-clip';"` and relaunch: the row moves into `Active Queue` with
  `Source missing` and `Redownload` instead of `Play`, which is the missing-file path.
- **Cleanup — remove every layer.** A UI delete removes the `downloads` row, never the `videos`
  row, and `db-push-sql` does not enable foreign keys, so cascades do not fire on their own:
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
  Then `$S db-count videos "id='verify-clip'"` → `0` and
  `$S db-count downloads "videoId='verify-clip'"` → `0`.
- **Persistence.** `scripts/ad.sh launch`, open Downloads, and assert the same rows are listed.
  Cross-check with `$S db-query "select id,status,format,filePath from downloads order by id"`.
- **Crash gate.** End the run with `$S crash-check`.
- **Evidence.** `ART=$($S evidence-dir downloads)`; keep one
  `scripts/ad.sh screenshot "$ART/screens/…"` plus the matching `snapshot -i` per state.

## Gotchas

- **`Restart` opens a dialog and changes nothing by itself.** The row button only confirms
  ("… start downloading from scratch."); the mutation happens on the dialog's `Restart`. A
  `db-count` taken right after the row tap proves nothing.
- **`Full titles` here writes nothing.** The Downloads switch is local screen state; only the
  Settings screen writes the global preference. It seeds from that global value each time the
  screen is composed, so "my toggle was forgotten after navigating away and back" is the designed
  behaviour and a `pref-get`/`pref-bool` check can only ever report the Settings value.
- **Two `Full titles` switches, one label — both surface as `[group]`.** One sits on the toolbar,
  one inside the `Sort By` sheet. Close the sheet first or take the target `@eN` ref from
  `snapshot -i` (and read the `- [checked] / + [unchecked]` diff from `click @eN --settle` instead
  of switch-role or `is checked` selectors). Neither one persists anything from this screen.
- **Toolbar buttons combine icon + text in `snapshot -i`.** The sort and concurrency buttons appear
  as `[button] "Sort, Date"` (category varies) and `[button] "Max concurrent, Max: N"`. `label="Date"`
  taps the inner text cleanly, or take the button's `@eN` ref from `snapshot -i`.
- **Batch `Delete` collides with per-card `Delete` icons.** In batch select mode, `click 'label="Delete"'`
  fails with `AMBIGUOUS_MATCH` (`3`+ actionable elements). Always take the batch bar `Delete` `@eN`
  ref from `snapshot -i`.
- **`scroll top --settle` fails at the bottom of Downloads.** It reports `"Already at top; no hidden
  content above detected"` without scrolling back up; use `scripts/ad.sh scroll up --until 'label="Downloads"' --settle`
  (or target `Batch Select Mode` / `Date`) instead.
- **Sort order persists, drag order does not.** `sort_mode` in `download_prefs` survives a
  relaunch; reordering with the `Reorder Drag Handle` only changes in-memory queue order and is
  dropped on restart. "Order survived" is only a valid claim for the stored sort mode.
- **`Max: N` changes with the value.** Use `find text "Max:" click` or a snapshot ref; a
  hard-coded `label="Max: 2"` breaks the moment someone changes it. The pref is
  `download_prefs max_concurrent`, clamped to `1..5`.
- **"Cancel" is status-specific.** Only `DOWNLOADING` and `QUEUED` rows produce `Cancel Download`
  (title and confirm button); every other status produces `Delete Download` with
  `List Only` / `Delete File & List` / `Keep`. The batch dialog is a third wording again:
  `Delete 1 Download` / `Delete N Downloads` with `List Only` / `Delete Files & List` / `Cancel`.
- **`Pause All` / `Resume All` and `Active Queue` are conditional.** They render only when a row
  justifies them; on a fresh install none of the three appear. Use `No downloads yet` or
  `Completed Downloads` as the loaded gate.
- **`Extract Audio` is conditional and additive.** It is hidden for audio rows and for a video
  that already has an `audio_extracted` sibling, and disabled while that video is extracting. It
  inserts a new queued row rather than replacing the video, so its disappearance after a
  successful extraction is correct.
- **`Completed Storage` needs bytes, not rows.** The footer is gated on the completed rows' total
  size, so a queue of zero-byte or non-completed rows shows the status card without it.
- **`Source missing` moves a row back into the queue.** A completed row whose `filePath` no longer
  exists is flagged and rendered in `Active Queue` with `Source missing` and `Redownload` — no
  `Play`. Finding it outside `Completed Downloads` is correct, not a filter bug.
- **Force-stopping cancels in-flight work.** Both `db-push-sql` and an `am force-stop` kill the
  foreground service; a download that stops mid-flight is expected. Relaunch with
  `scripts/ad.sh launch` before continuing.
- **The `downloads` table has a cascading FK to `videos`, but the seeder does not enable it.**
  Insert the `videos` row first, and delete children explicitly on cleanup — do not rely on the
  cascade. `status` is stored as the enum name (`QUEUED`, `DOWNLOADING`, `PAUSED`, `COMPLETED`,
  `FAILED`, `CANCELED`).
- **Writing into the app dir needs the pipe, not `cp`.** `run-as … cp /data/local/tmp/…` fails on
  some builds (SELinux / `0600`); `cat … | run-as … sh -c 'cat > …'` writes as the app uid.
- **Network is a real precondition.** Without YouTube access the queue can only prove rendering
  and the seeded path. Report the live-extraction path as **not driven** instead of inferring it
  from a compiled build.
