# Library and folders

Collections is the library surface: every downloaded video, continue-watching, and
user-created folders with rename/delete/move.

## Sub-features

- `lib-list` — `All Videos` renders with a live item count.
- `lib-folder-create` — a new folder can be created from `Create` / `New Folder`.
- `lib-folder-rename` — an existing folder can be renamed.
- `lib-folder-delete` — deleting a folder asks for confirmation.
- `lib-multiselect` — selecting items shows "N selected" and enables `Move to Folder`.
- `lib-continue` — `Continue Watching` appears only when there is partial progress.

## How to get to it (user POV)

- Tap `Collections` in the bottom nav.
- Folders are reachable from the `Folders` section on the same screen.

## Driving it with drive.sh

Preconditions:

- App launched; `Collections` reachable from the nav.
- Record whether the library has any videos; folder recipes work either way.

- **Land on the screen.** Run `tap_label "Collections"`, then `assert_text "All Videos"`.
- **Empty vs populated.** Run `assert_text "Videos ("` — the header carries a live count.
  Report the count seen; an empty library proves rendering, not playback.
- **Create a folder.** Run `tap_label "Create"` (or `tap_label "New Folder"`), type a unique
  name with `adb shell input text "verify-$$````````, confirm, then `wait_for "<name>" 10`.
  Use a name unique per run so re-runs do not collide.
- **Rename it.** Long-press or open the folder's menu, `tap_label "Rename"`, enter the new
  name, then `assert_text "<new name>"`.
- **Delete it.** `tap_label "Delete"` from the folder menu; the dialog `Delete Folder?` must
  appear, then confirm. `wait_gone "<new name>" 10` proves the delete landed.
- **Multi-select.** With at least one video: long-press an item and `assert_text " selected"`.
  `Move to Folder` becomes available; `BACK` or `Cancel` clears the selection.
- **State proof.** After a folder mutation, relaunch and re-open `Collections`:
  `launch`, `tap_label "Collections"`, `assert_text "<name>"` (or `wait_gone` after a delete).
  Folder rows live in `media_nest.db` as a second, read-only view.
- **Proof.** `snap "01-library"` per state, `ck_logcat` at the end.

## Gotchas

- Folder names are user text; use one unique per run or the next run asserts against a stale
  folder. Clean up what the run created.
- `Videos (n)` includes the live count — assert the prefix, not a hard-coded number.
- `Continue Watching` is conditional on partial playback progress; its absence is not a bug.
- The delete confirmation is a modal: a `BACK` cancels the delete rather than deleting.
- Empty library means no assertion about playback is possible here — say "not driven".
