# Subscriptions

Subscriptions is the channel/playlist surface: the two `Collections` tabs that list everything the
user follows, each subscription's per-row `Auto-download` and `Audio only` switches, and the channel
and playlist detail screens where the user subscribes, downloads a whole feed, and unfollows.

## Sub-features

- `sub-tabs` — `Collections` → `Playlists` and `Channels` pills, both rendering
  `SubscriptionsScreen` with `sourceType = "playlist"` / `"channel"`.
- `sub-list` — per-row card: thumbnail, name, `Auto-download` switch, `Audio only` switch, delete
  icon (`Unsubscribe` on channels, `Delete playlist` on playlists).
- `sub-shorts` — the `Show Shorts` switch on the list toolbar and on both detail headers; one
  shared preference (`subscriptions_prefs` → `show_shorts`).
- `sub-auto-download` — toggling `Auto-download` / `Audio only` writes the `subscriptions` row and
  shows `Auto-download enabled for <name>` / `Audio-only auto-download enabled for <name>`.
- `sub-unsubscribe` — delete icon → `Remove?` dialog → `Remove` (or `Cancel`) with snackbar
  `Unsubscribed from <name>` on list rows, distinct from the detail headers'
  `Unsubscribed from Channel` / `Subscribed to Channel` and `Removed from Playlist` /
  `Added to Playlist`.
- `sub-inline` — `InlineSubscriptionScreen` for a channel or playlist: header card, `Subscribed` /
  `Subscribe` (channels) or `Saved` / `Add to Playlist` (playlists), `Download All`, `Show Shorts`.
- `sub-persist` — the follow/unfollow and switch changes land in the `subscriptions` table.

## How to get to it (user POV)

- `Collections` (bottom nav) → `Playlists` pill: followed playlists.
- `Collections` → `Channels` pill: followed channels.
- Tapping a card on either tab opens the inline detail screen
  (`InlineSubscriptionScreen`, `sourceType`+`sourceId`), whose back arrow returns to the tab.
- From `Home`, pasting a playlist or channel URL renders a result header (`PLAYLIST` / `CHANNEL`)
  whose action button is `Add to Playlist` / `Saved` (playlists) or `Subscribe` / `Subscribed`
  (channels) — subscribing there puts the row into the same two tabs.
- `Settings` → `Preferences` → `Show YouTube Shorts` is the same `show_shorts` preference, so the
  two switches always agree.

## Driving it with ad.sh

Preconditions:

- `scripts/nest.sh up` ran; `scripts/ad.sh launch` succeeded.
- `.agents/skills/verify-medianest/scripts/nest-state.sh doctor` passes.
- Record the row count first — every recipe here mutates, and cleanup restores it:

  ```bash
  S=.agents/skills/verify-medianest/scripts/nest-state.sh
  $S db-count subscriptions
  $S db-query "select id,sourceType,name,autoDownload,audioOnly from subscriptions order by id"
  ```

  A library with **no** subscriptions can still verify the list's empty state and the `Show Shorts`
  preference; the per-row and detail recipes are then **not driven**.

- **Land on the tabs.** `scripts/ad.sh launch`, tap `Collections`, then
  `scripts/ad.sh click 'role=tab label="Playlists"' --settle` /
  `'role=tab label="Channels"' --settle`. Assert the pill selection; the content area is the same
  composable with a different `sourceType`.
- **Empty vs populated.** With zero playlist rows the tab shows `No subscriptions yet` (or
  `No results found` when the tab search box has text) and still renders `Show Shorts`. Otherwise
  one `GlassCard` per row. Never force the empty state — assert whichever is true and report it.
- **Show Shorts (list).** Scope to the role, because the row label is a sibling text node:

  ```bash
  scripts/ad.sh is checked 'role=switch label="Show Shorts"'
  scripts/ad.sh click 'role=switch label="Show Shorts"' --settle
  $S pref-bool subscriptions_prefs show_shorts
  ```

  Both tabs and both detail headers write the same `subscriptions_prefs` key through
  `SubscriptionsPreferences.setShowShorts`, so flipping it on `Channels` must change the value read
  on `Playlists`. Restore the starting value at the end.
- **Per-row Auto-download.** Tap the row's `Auto-download` switch:
  `scripts/ad.sh click 'role=switch label="Auto-download"' --settle`. The snackbar names the row
  (`Auto-download enabled for <name>`), and the row is the read-only second view:

  ```bash
  $S db-count subscriptions "autoDownload=1 and id=<id>"
  ```

  `autoDownload` is stored by Room as **0/1**, so use `=1` / `=0`, not `=true`.
- **Per-row Audio only.** Same pattern with `role=switch label="Audio only"` and
  `audioOnly=1`. Note this switch is independent in the DB but the UI calls
  `updateAutoDownload(id, autoDownload, audioOnly)` with the *other* current value, so toggling one
  must never flip the other — assert both columns, not just the one you tapped.
- **Unsubscribe / Delete playlist.** The delete icon's `content-desc` differs by row type:
  `Delete playlist` on the `Playlists` tab, `Unsubscribe` on `Channels`. Tap it, assert the dialog
  title `Remove?` and body `Are you sure you want to remove "<name>"?`, then `Cancel` for a
  non-destructive proof. Confirming with `Remove` deletes the row — do it only on a subscription the
  run created, then prove `$S db-count subscriptions "id=<id>"` → `0`.
- **Inline channel detail.** From `Channels`, tap a card (not the name text on other screens — here
  the whole `GlassCard` is clickable). Assert `Channel`, the channel name, the count line (`Videos: N`,
  or `Videos: N loaded • Scroll to load more` when more pages remain), the `Subscribed` button,
  `Download All`, and the `Show Shorts` switch. Toggling `Subscribed` writes the same table and shows
  `Unsubscribed from Channel` / `Subscribed to Channel`, then drops the row from the `Channels` tab
  on return.
- **Inline playlist detail.** From `Playlists`, tap a card. Assert `Playlist`, the playlist name, the
  `Saved` button (reads `Add to Playlist` when the playlist is **not** followed), `Download All`,
  `Show Shorts`, and the count line. The count line is **conditional**: `Videos: N` when everything
  is loaded, `Videos: N loaded • Scroll to load more` when more pages remain, and
  `Videos: N loaded of M • Scroll to load more` when the API reported a larger total. Assert
  `Videos: ` and let the tail vary, or read the exact form off the snapshot; a hard-coded
  `Scroll to load more` goes red on a fully-loaded playlist. Toggling the button shows
  `Removed from Playlist` / `Added to Playlist`.
- **Download All.** Tapping `Download All` on either header opens `Download All by Resolution`. The
  radio rows carry **long labels**, not bare resolutions — match them exactly:
  `1080p (Highest video quality)`, `720p (High definition)`, `480p (Standard definition)`,
  `360p (Low data usage)`, `Audio Only (M4A/WebM)` — then `Next` / `Cancel`. `label="1080p"` and
  `label="Audio"` are whole-field matches and find nothing; use the full label or `find text` with
  the prefix. That flow is owned by the download-queue recipe — assert the dialog appeared and
  `Cancel` out, or follow the bulk-download recipe; do not count the dialog as a download.
- **Subscribe from Home.** Type a channel URL into the Home input (`Search or paste URL...` is its
  **placeholder**, so prefer the `@ref` from `snapshot -i` or `label=` on the field over the
  placeholder text), wait for the `CHANNEL` header, tap the `Subscribe` button, and assert
  `Subscribed` **and** the DB:
  `$S db-count subscriptions "sourceType='channel' and name='<channel>'"` → `1`. Playlists use
  `Add to Playlist` → `Saved` and `sourceType='playlist'`.
- **Cleanup.** Unsubscribe anything the run subscribed (UI first; only if the UI path failed, remove
  the run's own rows with
  `$S db-push-sql "delete from subscriptions where id in (<ids>)"` — it force-stops the app, so run
  `scripts/ad.sh launch` afterwards), restore `show_shorts` and both per-row switches to their
  starting values, and leave the tab the run started on.
- **Crash gate and evidence.** `$S crash-check`; screenshots of the tab, the dialog and the detail
  header; `$S db-pull "$ART/db"` for the rows behind them.

## Gotchas

- **There is no bottom-nav `Subscriptions` destination.** It is a pill tab inside `Collections`
  (`Playlists` / `Channels`). Old docs and old flows that tap a `Subscriptions` nav item fail with
  `Activity not found`-style confusion; the top bar on `Collections` reads `Collections`, and the
  pills are `role=tab`.
- **`Show Shorts` is one preference, not per list.** `SubscriptionsScreen` and
  `InlineSubscriptionScreen` both read `SubscriptionsPreferences.showShorts`; the switch on one
  surface must move the other. Assert `subscriptions_prefs.show_shorts`, not the widget alone.
- **Three `Show Shorts` call sites, one on screen at a time.** The list toolbar, the playlist header
  and the channel header share the label, but `InlineSubscriptionScreen` *replaces*
  `LibraryScreen`, that screen mounts one tab at a time, and the two detail headers are mutually
  exclusive — so a well-formed run never has two mounted together. Still scope with
  `role=switch label="Show Shorts"`, because the visible `Text("Show Shorts")` beside it is a
  sibling node with no `checked` state; an unscoped tap can hit the label instead of the control.
- **Full titles is a list toggle, not a subscription setting — and it does not persist there.**
  `CollectionsPreferences.setFullTitles` is called only from `SettingsScreen`. On `Collections`,
  `LibraryScreen` keeps `fullTitles` in a local `remember` (`onFullTitlesChange` just assigns), and
  `CollectionsPreferences.setFullTitlesFor` is a documented no-op, so toggling it on any library tab
  changes nothing on disk. Prove `collections_prefs.full_titles` **only** from the
  `Settings → Preferences` switch. `SubscriptionsScreen` renders this toggle with
  `showFullTitlesToggle = false`, so on `Playlists`/`Channels` the only `FullTitlesToggle` switch is
  the shared, non-persisting one.
- **`autoDownload` / `audioOnly` are Room booleans = integers.**
  `db-count subscriptions "autoDownload=1"` works; `"autoDownload=true"` silently counts 0 and
  reads as "the toggle did nothing".
- **The list only shows rows whose `sourceType` matches the tab.** A subscription created with
  `sourceType = "channel"` never appears under `Playlists` even if its name contains a playlist id.
  Assert per tab when a row is missing.
- **Delete-icon `content-desc` differs by row type.** `Unsubscribe` (channel) vs
  `Delete playlist` (playlist). Matching `Unsubscribe` on the `Playlists` tab finds nothing.
- **Removing is immediate and silent in the UI.** `Remove` deletes the row and shows a snackbar; the
  confirmation dialog itself is the only guard, so verify the `subscriptions` count after, not the
  snackbar.
- **Three different follow/unfollow snackbars say three different things.** List-row delete →
  `Unsubscribed from <name>`; inline channel button → `Unsubscribed from Channel` /
  `Subscribed to Channel`; inline playlist button → `Removed from Playlist` /
  `Added to Playlist`. Wait for the one the surface you are on actually emits; mixing them turns a
  correct run red.
- **`Download All` opens bulk download, it does not download.** The first screen is
  `Download All by Resolution`; the actual queueing is `Confirm Bulk Download` → `Download`. The
  download-queue recipe owns that path.
- **Auto-download itself is background work.** `SubscriptionWorker` runs every 6 h under
  `NetworkType.CONNECTED` (`WorkScheduler.scheduleSubscriptionCheck`, enqueued from
  `MediaNestApp`) and only then reads `subscriptions.autoDownload` / `audioOnly` to queue new
  uploads. Toggling the switch proves the *configuration*; it does not queue a download, and a run
  must not report it as one.
- **Every row lives in one scroll container.** `snapshot -i` shows only what is on screen; use
  `scripts/ad.sh scroll down --until 'role=switch label="Auto-download"'` (amount is a viewport
  fraction — `scroll down 0.5` or `--pixels 400`) rather than a fixed count of swipes.
- **`UpdateSubscription` keeps both flags consistent.** Tapping one switch sends the other's current
  value with it; a row that flips both when you tapped one is a real bug, so assert the untouched
  column too.
