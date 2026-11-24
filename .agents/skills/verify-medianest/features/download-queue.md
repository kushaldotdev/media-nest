# Download queue

Downloads shows a live active queue with pause/resume controls and a completed list with a
storage footer. It is the screen where background work becomes visible.

## Sub-features

- `dl-empty-active` — with nothing running, the active section is empty rather than blank.
- `dl-completed-list` — completed items render with their count and a storage footer.
- `dl-bulk-controls` — `Pause All` / `Resume All` are present and act on the queue.
- `dl-item-actions` — a completed item offers its actions (redownload, delete, extract audio).
- `dl-survives-restart` — queue state comes back after a relaunch.

## How to get to it (user POV)

- Tap `Downloads` in the bottom nav.
- Or send the deep link: `com.example.medianest.ACTION_NAVIGATE_DOWNLOADS`.

## Driving it with drive.sh

Preconditions:

- App launched; `Downloads` reachable from the nav.
- Note whether the account has any completed downloads; the recipes below branch on it.

- **Land on the screen.** Run `tap_label "Downloads"`, then
  `assert_text "Active Queue"`. Both `Active Queue` and `Completed Downloads` headings are the
  real labels.
- **Empty vs populated.** Run `assert_text "Completed Downloads"`; then check for a count or a
  file row. Report which state the run saw — an empty list proves rendering, not downloading.
- **Bulk controls.** Run `assert_text "Pause All"` then `assert_text "Resume All"`. Tapping
  either with an empty queue must not crash: run `tap_label "Pause All"`, then `ck_logcat`.
- **Item actions.** With at least one completed item: `tap_label` on the item, then
  `assert_text "Redownload"` / `assert_text "Delete"` / `assert_text "Extract Audio"`. Dismiss
  with `BACK` and confirm the queue screen is back.
- **State proof.** With a real download, run
  `assert_state "downloaded file" "ls /sdcard/Download | head -3"` or list the app's files
  directory. The row in `media_nest.db` is the second, read-only view.
- **Restart proof.** Run `launch`, then `tap_label "Downloads"` and assert the same items are
  still listed.
- **Proof.** `snap "01-downloads"` per state, and `ck_logcat` at the end.

## Gotchas

- The queue is driven by a foreground service (`DownloadService`). Killing the app with
  `am force-stop` cancels in-flight work — always state whether a restart killed a download.
- `Pause All` / `Resume All` are no-ops on an empty queue; a no-op is not a failure, but it
  is also not proof the control works. Say which it was.
- Completed count and storage footer both come from live state; a stale screenshot after a
  delete verifies the wrong thing. Re-dump the tree after every mutation.
- Downloads need network and the yt-dlp extraction pipeline. Without them, this screen proves
  rendering only.
