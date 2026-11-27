# Library and folders

Collections is the library surface: six pill tabs — `History`, `Watched`, `Folders`, `Favorites`,
`Playlists`, `Channels` — over the same library, plus nested user-created folders that can be
created, opened, renamed, moved into and deleted.

## Sub-features

- `lib-tabs` — the six pill tabs switch content: `History` (default), `Watched`, `Folders`,
  `Favorites`, `Playlists`, `Channels`.
- `lib-toolbar` — top bar `Collections`; `Switch to list view` / `Switch to grid view`,
  `Statistics`, Notifications, plus the search field and a session-local `Full titles` switch.
- `lib-root-folders` — root `Folders` tab: `N Folders` header, `New Folder`, `No Folders Yet` when
  empty, `N folders` stats line, per-folder `N items`, and a `More options` menu.
- `lib-folder-create` — `New Folder` → `Create` dialog (title `New Subfolder` inside a folder).
- `lib-folder-open` — open a folder: title becomes the folder name, subtitle `Folder collection`,
  breadcrumbs, `Folder is Empty`, `Subfolders`, `Videos (N)`.
- `lib-folder-rename` — `More options` → `Rename` → `Rename Folder` dialog.
- `lib-folder-duplicate` — an exact-case **and** a different-case duplicate are refused with
  `A folder with that name already exists here`, the dialog stays open and no row is inserted.
- `lib-folder-delete` — `Delete Folder?` confirmation, optional
  `Delete downloaded videos in this folder`, snackbar `Folder deleted`.
- `lib-multiselect` — long-press a video → `N Selected` / `N selected`, `Select All` / `Deselect`,
  `Move`, delete, `Cancel selection`; `Move to Folder` dialog; batch delete confirmation.
- `lib-sort-view` — per-tab sort sheet (`Sort By`, in memory only) and grid/list mode (persisted).

## How to get to it (user POV)

- Tap `Collections` in the bottom navigation.
- Tabs are the pill row under the top bar: `History`, `Watched`, `Folders`, `Favorites`,
  `Playlists`, `Channels`. Each tab is a full field of its own, and the search field is
  per-tab: its placeholder is `Search ${tab.label.lowercase()}...` — literally
  `Search folders...` on `Folders` (ASCII dots), so match the prefix with
  `scripts/ad.sh find text "Search folders" exists` rather than a whole-field label.
- A folder is opened by tapping its card on the `Folders` tab. Inside, the breadcrumb row
  (`Folders` → folder name) navigates back one level, and `New subfolder` creates a child.
- Videos enter selection mode by long-pressing any video card on any video tab.

## Driving it with ad.sh

Preconditions:

- `scripts/nest.sh up` ran and `scripts/nest.sh status` reports `screencap works`.
- `scripts/ad.sh launch` succeeded.
- `.agents/skills/verify-medianest/scripts/nest-state.sh doctor` passes, so `run-as` reads work.
- One agent-device session at a time; end the run with `scripts/ad.sh close`, and start it with
  `$S logcat-clear` when you intend to use `crash-check`.
- Record whether the library has videos. Folder recipes work on an empty library; move/select and
  sort-order recipes need at least one video and are **not driven** otherwise.

```bash
S=.agents/skills/verify-medianest/scripts/nest-state.sh
```

- **Land on the screen.** `scripts/ad.sh launch`, tap the `Collections` nav ref from
  `scripts/ad.sh snapshot -i`, then `scripts/ad.sh wait text "Collections" 10000`. The nav label
  and the screen title are both `Collections`, so select the nav item by `@ref`.
- **Tabs.** The pill tabs (`History`, `Watched`, `Folders`, `Favorites`, `Playlists`, `Channels`)
  surface in `snapshot -i` as `[group]` nodes, never as a tab role. Take `scripts/ad.sh snapshot -i`
  and tap each visible pill by its `@eN` ref (e.g. `Folders` → `scripts/ad.sh click @eN --settle`).
  `Playlists` and `Channels` start off-screen inside the horizontal pill row; bring them into view
  with `scripts/ad.sh swipe 950 288 150 288` (no `--settle`, no 5th duration arg — both return
  `INVALID_ARGS`), then re-run `snapshot -i` and tap the pill's `@eN` ref. Content per tab:
  `History` → `No Watch History` (empty) or rows; `Watched` → `No Watched Videos`;
  `Favorites` → `No Favorites Yet`; `Folders` → `No Folders Yet` or `N Folders`;
  `Playlists` and `Channels` render the subscription list — assert only that the pill switched.
- **Toolbar + persisted view mode.** The top bar exposes `"Switch to list view"` /
  `"Switch to grid view"` (the label flips with the current mode, after the companion accessibility
  fix; take its `@eN` ref from `snapshot -i` or match by `label=`), `Statistics`, and the
  Notifications bell. Tap the view toggle and prove it persisted:
  `$S pref-get collections_prefs view_mode` → `LIST` or `GRID`, and the cards change shape
  (single column vs two). This is the only Collections preference this screen writes — see the
  sort section.
- **Root folder count.** On `Folders`, the section header reads `N Folders` (capital F) and the
  stats line reads `N folders` (lowercase). Assert `No Folders Yet` only when
  `$S db-count folders` is `0`; otherwise report the existing count instead of forcing the empty
  state.
- **Create a folder.** Tap `scripts/ad.sh click 'label="New Folder"' --settle`. The dialog title is
  `New Folder` and its only input is the dialog field — but the tab search box is also an editable
  field, so take `scripts/ad.sh snapshot -i` **after** the dialog opens and use the dialog's own
  ref: `scripts/ad.sh fill @e<dialog-field> "<RunName>" --settle`. Write the ref as `@e<what-it-is>`
  and copy the real `@eNN` from your snapshot. Tap
  `scripts/ad.sh click 'label="Create"' --settle`, then
  `scripts/ad.sh wait text "<RunName>" 10000`. Use a unique per-run prefix such as
  `VerifyLib-<pid>` and clean up everything the run created.
- **Database proof for create.** `$S db-count folders "name = '<RunName>'"` → `1`. The snackbar
  alone is not proof the mutation landed.
- **Open the folder.** Do **not** tap the folder-name `Text` — that node has its own clickable that
  only toggles title truncation. In Grid mode, the folder card's accessible ref in `snapshot -i` is
  `[group] "<N> items"` (NOT `"<Name>, <N> items"`), because `<Name>` is that separate clickable
  `[text]` node. Pick the card's `[group] "<N> items"` `@eN` ref from `snapshot -i` and run
  `scripts/ad.sh click @eN --settle`. Then assert the `Folder collection` subtitle plus
  `Folder is Empty` (or `Videos (N)`), and `Subfolders` when children exist.
- **Leave the folder.** Run `scripts/ad.sh snapshot -i` and tap the breadcrumb `Back` `@eN` ref (the
  first `[button] "Back"`, or the breadcrumb `[text] "Folders"` ref) with
  `scripts/ad.sh click @eN --settle`, and assert the `N folders` stats line is back. (The
  top-bar back button exposes `[button] "Back"` in `snapshot -i` because `MediaNestIconButton`
  places its outer label on `onClickLabel` rather than `contentDescription`, so use `[button] "Back"`
  or breadcrumb `"Folders"`.) Do not use `scripts/ad.sh back`: the screen intercepts BACK to leave
  the folder, clear the selection or clear the search, so it never exits the screen and proves
  nothing about navigation.
- **Rename — grid vs list.** In GRID the folder card carries an overflow button rendered in
  `snapshot -i` as `[button] "More options"` (the inner `Icon` holds `content-desc "More options"`,
  while `MediaNestIconButton` puts `"Folder options"` on `onClickLabel`) whose menu holds `Rename`
  and `Delete`. In LIST mode the `FolderRow` exposes the two actions directly as icon buttons with
  `content-desc "Rename folder"` and `content-desc "Delete folder"`. Pick whichever matches the
  current view mode — the grid dropdown does not exist in list mode and vice versa.
- **Rename.** Open the menu (`click @e<more-options> --settle`), run `scripts/ad.sh snapshot -i`,
  and tap the menu item's `@eN` ref for `Rename` — do **not** run `click 'label="Rename"'`, which
  fails with `AMBIGUOUS_MATCH` (`[group] "Rename"` + `[text] "Rename"`). Assert the `Rename Folder`
  dialog. The field is **pre-filled** with the current name, so
  `scripts/ad.sh fill @e<rename-field> "<NewName>" --settle` replaces it (`type` would append).
  Take the dialog's `Rename` confirm button `@eN` ref from `snapshot -i` (or `click 'label="Rename"' --settle`
  once the menu is closed) to confirm. Then `wait text "<NewName>" 10000` and prove
  the row: `$S db-count folders "name = '<NewName>'"` → `1` and
  `$S db-count folders "name = '<RunName>'"` → `0`.
- **Duplicate refusal — the snackbar is behind the dialog.** The refused submission keeps the
  dialog open, and the open dialog window hides the app window — including the snackbar — from a UI
  dump. So capture the `Cancel` ref **before** submitting, then dismiss immediately and only then
  look for the text:
  1. `scripts/ad.sh snapshot -i` while the dialog is still empty; record `@e<cancel>`.
  2. Fill the duplicate name and tap `label="Create"`.
  3. **Immediately** `scripts/ad.sh click @e<cancel> --settle` — no snapshot, no screenshot in
     between (each dump costs seconds and eats the ~4 s snackbar).
  4. Then `scripts/ad.sh wait text "A folder with that name already exists here" 4000`.
  Finally prove nothing leaked:
  `$S db-count folders "lower(name) = lower('<NewName>')"` → `1`.
- **Duplicate — different case.** Repeat with the upper-cased name. The refusal must be identical
  and the row count must not change:
  `$S db-query "select count(*) from folders"` before and after, equal. The comparison is
  case-insensitive on trimmed names scoped to the same parent, so this is the case the grid alone
  cannot disprove — a case-insensitive text match hits the existing card.
- **A different name is accepted (control).** Create `<RunName>B` and assert it reaches the DB.
  Without this control, "everything is refused" would look like a pass.
- **Multi-select and move.** Long-press a video card:
  `scripts/ad.sh longpress @e<card> --settle`. The top bar title becomes `N Selected` (capital S)
  with subtitle `Batch selection active`; the bottom bar shows `N selected` (lowercase s), a
  `Select All` (or `Deselect`) chip, `Move`, the trash icon (`content-desc "Delete selected"`) and
  `Cancel selection`. Both counts render — assert each in its own place, they are different strings.
  Tap `scripts/ad.sh click 'label="Move"' --settle`; the `Move to Folder` dialog lists folders with
  `N items` and a `Cancel` button, or `No folders available. Create a folder first.`. Tap the
  target folder row, then prove the join row:
  `$S db-count video_folder_join "folderId=<id>"` → expected count.
- **Batch delete.** With a selection, tap the trash icon (`content-desc "Delete selected"`) and
  assert the title is `Delete 1 Video?` for one item (`Delete N Videos?` for several), the body
  `Are you sure you want to remove the selected videos from your library?`, the checkbox
  `Delete downloaded files from storage`, and `Delete` / `Cancel`. Confirm and prove both the
  `videos` rows and the `video_folder_join` rows disappear.
- **Delete a folder.** In grid mode, open `[button] "More options"`, run `scripts/ad.sh snapshot -i`,
  and tap the menu item's `@eN` ref for `Delete` (do **not** run `click 'label="Delete"'`, which
  fails with `AMBIGUOUS_MATCH` against `[group] "Delete"` + `[text] "Delete"`); in list mode tap
  the `Delete folder` icon. Assert `Delete Folder?` with
  `Are you sure you want to delete folder '<name>'? This cannot be undone.` plus the checkbox
  `Delete downloaded videos in this folder`. Confirm using the dialog's `Delete` `@eN` ref from a
  fresh `snapshot -i` (prefer the ref over `label="Delete"`, which can still collide). Then
  `$S db-count folders "name = '<name>'"` → `0`. The checkbox deletes
  downloaded files for videos in the folder **and its subfolders**; leave it unchecked unless the
  run seeded throwaway files.
- **Sort — where the chip actually is.** The sort chip lives inside the secondary-filter block,
  which renders for `History`, `Watched`, `Favorites`, `Playlists`, `Channels` and for a folder
  with content — **root `Folders` with no folder selected and no folder videos renders no chip at
  all**. So verify sorting on `History` (or `Favorites`), starting from the default
  `Published Date ↓`. Tap the chip, choose a category and `Ascending (↑)` / `Descending (↓)`, tap
  `Apply`, and assert the visible order of the rows changes and the chip label follows — the chip
  prints the category's own label, so choosing the sheet row `Title` renders `Name ↑` / `Name ↓`
  (sheet wording and chip wording differ for that one category; the others match). This choice is
  **in-memory only**: nothing in the library writes
  `sort_mode_history` / `sort_mode_watched` / `sort_mode_folders` / `sort_mode_favorites` /
  `sort_mode_playlists` / `sort_mode_channels`, so `pref-get` on those keys reports the default no
  matter what the UI shows. Do not claim sort persistence; assert order and the chip text, and
  confirm the selection is forgotten after leaving the tab.
- **Cleanup.** Delete every folder the run created through the UI; if the UI path failed, say so and
  remove only the run's own names:
  ```bash
  $S db-push-sql "delete from folders where name like 'VerifyLib-%';"
  scripts/ad.sh launch
  ```
  `db-push-sql` force-stops the app — relaunch before driving again.
- **Crash gate.** `$S crash-check` must pass (no `FATAL EXCEPTION`, no `ANR`).
- **Evidence.** `ART=$($S evidence-dir library)`; one screenshot plus the matching `snapshot -i`
  per mutation, and `$S db-pull "$ART/db"` for the rows behind them.

## Gotchas

- **Two editable fields on this screen.** The tab search box and the alert dialog's input are both
  text fields. "Fill the first editable field" types the folder name into the search box and the
  dialog never receives it. Take `snapshot -i` **after** the dialog opens and use its own ref.
- **Never dismiss the keyboard with BACK.** BACK dismisses the `AlertDialog` and throws away the
  typed name, and the screen also intercepts BACK in selection mode, inside a folder and with a
  non-empty search. The keyboard does not cover the dialog buttons here — tap `Create` / `Rename` /
  `Cancel` directly. If it must go, `scripts/ad.sh keyboard dismiss` is the only safe call.
- **A refused duplicate hides its own snackbar.** The dialog window masks the app window from a
  dump, so a snapshot taken before dismissing sees no snackbar and the ~4 s toast expires during
  the dump. Capture `Cancel` first, submit, cancel immediately, then `wait text … 4000`.
- **Duplicate refusal is per parent, not global.** Sibling names are compared trimmed and
  case-insensitively. The same name is legal in a different folder, and a folder may keep its own
  name when only its case changes — a global "name already exists" assumption turns a correct app
  red.
- **A refusal that leaked a row is the real bug.** Always pair the snackbar with a
  `db-count`/before-after total, and test both the exact-case and the different-case collision.
- **The folder title toggles truncation, and Grid cards surface as `[group] "<N> items"`.** The
  title `Text` carries its own clickable, so a nearest-clickable lookup or text match hits it
  instead of the card and the folder does not open. In Grid mode, tap the card's
  `[group] "<N> items"` `@eN` ref from `snapshot -i` (not `"<Name>, <N> items"`).
- **Grid and list expose folder actions differently.** Grid has one overflow button
  (`[button] "More options"` in `snapshot -i`, whose dropdown items `Rename` / `Delete` each have
  both `[group]` and `[text]` nodes so bare `label="Rename"` / `label="Delete"` fails with
  `AMBIGUOUS_MATCH` — always take the `@eN` ref); list has two direct icon buttons (`Rename folder` /
  `Delete folder`). `Rename`/`Delete` also exist inside the open dialog, so take dialog `@eN` refs too.
- **Leaving a folder uses `[button] "Back"` or breadcrumb `"Folders"`.** `MediaNestIconButton`
  puts the top-bar back action's outer label on `onClickLabel`, so the accessible nodes visible in
  `snapshot -i` are `[button] "Back"` (breadcrumb arrow + empty-state button) and breadcrumb
  `[text] "Folders"`.
- **`N Selected` and `N selected` are different nodes.** The top-bar title is capitalised, the
  bottom batch bar is lowercase. Asserting one where the other lives fails for a cosmetic reason.
- **`Full titles` on this screen writes nothing either.** The header toggle only changes local
  state; no library path calls the persisted setter, so `pref-bool collections_prefs full_titles`
  reports whatever Settings last wrote. Verify it by row rendering only.
- **`view_mode` is shared with Home.** The same stored value backs Home's list/grid toggle, so a
  `pref-get collections_prefs view_mode` change may have come from either screen. Assert the
  Library rows changed shape *and* note that Home's mode moved with it.
- **The sort chip and the sort sheet word one category differently.** The sheet row reads `Title`,
  the chip after applying it reads `Name`. Asserting the chip against the sheet's wording fails for
  a naming reason, not a sorting reason.
- **Sort is not persisted on this screen.** The chip's category and direction live in memory and
  reset when the state is rebuilt; only `view_mode` reaches DataStore. Verify sort by looking at
  the row order, and verify persistence only for grid/list.
- **No sort chip on root `Folders`.** With no folder selected and no folder videos the
  secondary-filter block is not rendered, so there is nothing to tap. That is the layout, not a
  missing feature.
- **`New Folder` vs `New Subfolder`.** The root header button is `New Folder`; inside a folder the
  breadcrumb action is `New subfolder` and the dialog title becomes `New Subfolder`. Both use the
  same create path with a `parentId`.
- **Rename starts pre-filled.** Use `fill` (replace) rather than `type` (append); `fill ""` is the
  documented clear. Submitting an unchanged name is not a duplicate of itself — the folder's own
  row is excluded from the conflict check.
- **Counts are live.** `N folders`, `N Folders`, `Videos (N)` and `N items` all change with state;
  assert the prefix or the DB, never a number copied from an earlier run.
- **`Favorites`, `Watched`, `History` are video tabs.** They have no folder overflow; `Rename` /
  `Delete` on a folder only exist on folder cards and on subfolder rows inside a folder.
- **Pill tabs surface as `[group]` nodes and `Playlists` / `Channels` start off-screen.** Both
  `PillTabRow` and filter chips render in `snapshot -i` as `[group]`, never as a tab role. Swipe
  horizontally (`scripts/ad.sh swipe 950 288 150 288` — no `--settle`, no 5th duration arg) to
  bring `Playlists` / `Channels` into view, then re-run `snapshot -i` and tap the pill's `@eN` ref.
- **`Collections` view toggle exposes `"Switch to list view"` / `"Switch to grid view"`.** After
  the companion accessibility fix on `Collections`, the top-bar view-mode button surfaces those
  labels directly (and can always be clicked via its `@eN` ref from `snapshot -i`).
- **`Playlists` and `Channels` are not folder content.** They render the subscription list; a
  verdict on their content belongs to the subscriptions recipe — assert the tab switch only.
- **Folder deletion cascades in the schema, not in the seeder.** Join rows are cascade-deleted by
  Room, and with the checkbox on, files for videos in the folder and its subfolders are deleted
  too. A run that seeds files must clean them up explicitly.
