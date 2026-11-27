# Notifications

Notifications is the in-app event inbox: download completions, subscription/upload events, sync
results and system messages, each typed, with an unread badge on the bell, type filter chips,
mark-all-read, clear-all and tap-through to the related video or the download queue.

## Sub-features

- `notif-open` — the `Notifications` bell in the top bar of `Home`, `Collections`, `Downloads` and
  `Settings`, carrying the unread badge.
- `notif-list` — cards in `timestamp DESC` order: title, relative time, message, unread dot,
  `Tap to view →` when the row has a target.
- `notif-filters` — the five chips `All`, `Downloads`, `Subscriptions`, `System`, `Sync`.
- `notif-mark-all` — the `Mark all read` action, clearing the badge and every unread dot.
- `notif-clear` — the `Clear all` action → `Clear all notifications?` → `Clear All` / `Cancel`.
- `notif-paginate` — `Showing N of M` plus `Show more (N remaining)` in +10 batches.
- `notif-tap` — a row with `targetVideoId` opens video detail; a download row opens Downloads.
- `notif-persist` — every state change lands in the `app_notifications` table.

## How to get to it (user POV)

- Bell icon (`content-desc "Notifications"`) in the top bar, present on all four bottom-nav
  destinations.
- `Settings` → `About & Updates` → the `In-App Notifications` card
  (`Download completions, background sync alerts & updates`) navigates to the same screen.
- Inside the screen: back arrow top-left; `Mark all read` (check glyph) and `Clear all` (trash glyph)
  top-right; the five filter chips sit directly under the title; the unread count is the subtitle
  (`N unread`).

## Driving it with ad.sh

Preconditions:

- `scripts/nest.sh up` ran; `scripts/ad.sh launch` succeeded.
- `.agents/skills/verify-medianest/scripts/nest-state.sh doctor` passes.
- Notification permission granted on first install (the POST_NOTIFICATIONS dialog is cleared by
  `scripts/dev/install.sh`; `scripts/ad.sh alert dismiss` clears a leftover).

```bash
S=.agents/skills/verify-medianest/scripts/nest-state.sh
ART=$($S evidence-dir notifications)
$S db-count app_notifications                       # starting row count
$S db-count app_notifications "isRead=0"            # starting unread count -> badge
```

- **Open it.** `scripts/ad.sh launch`, then
  `scripts/ad.sh click 'label="Notifications"' --settle`, then
  `scripts/ad.sh wait text "Notifications" 10000`. **Not** `role=button`: `MediaNestAppBarAction`
  wraps the icon in a plain clickable `Box` with no `role = Role.Button`, so
  `'role=button label="Notifications"'` matches zero nodes. `contentDescription = "Notifications"`
  sits on the inner `Icon`, and the whole box is the click target — a `label=` match lands on the
  icon and the tap bubbles to the clickable parent. On the four destinations that share the bar the
  name is unique; if it ever is not, take the `@ref` from `snapshot -i`. By contrast the in-screen
  `Mark all read` / `Clear all` buttons are Material `IconButton`s and *do* carry a button role.
- **Empty state.** With zero rows: `No notifications` plus
  `Download completions, new uploads, sync and other events will appear here.` On a filtered chip it
  becomes `No <ChipLabel> notifications found.` — it uses the chip's text, so it reads
  `No Downloads notifications found.` / `No Sync notifications found.`, never the enum name.
- **Badge ↔ unread count.** The badge is `unreadCount` capped at `99+`; the subtitle is
  `N unread`. Both read `$S db-count app_notifications "isRead=0"`. If they disagree, the UI or the
  flow is stale, not the DB.
- **Filter chips.** Filter chips (`All`, `Downloads`, `Subscriptions`, `System`, `Sync`) surface in
  `snapshot -i` as `[group]` nodes, never as a tab role — and bare `label="Downloads"` collides
  (`AMBIGUOUS_MATCH`: chip text plus the bottom-nav `Downloads` group/text). Run
  `scripts/ad.sh snapshot -i`, select the desired chip's `@eN` ref, and run
  `scripts/ad.sh click @eN --settle`; then `$S db-count app_notifications "type='DOWNLOAD'"` must
  equal the visible card count and the `Showing N of M` line. `ALL` is the only chip that shows
  every row; the other four map to `DOWNLOAD`, `SUBSCRIPTION`, `SYSTEM`, `SYNC` case-insensitively.
- **Mark all read.** `scripts/ad.sh click 'label="Mark all read"' --settle`, then prove the DB:
  `$S db-count app_notifications "isRead=0"` → `0`, the subtitle disappears and no unread dot
  remains. This is the cheapest full round-trip proof and it is reversible only by seeding.
- **Clear all — cancel path.** `scripts/ad.sh click 'label="Clear all"' --settle`, assert the dialog
  `Clear all notifications?` with body
  `Are you sure you want to clear all notifications? This action cannot be undone.`, then `Cancel`.
  Row count unchanged — that is a real proof, not a skipped one.
- **Clear all — confirm path.** Only on an instance whose rows you do not need. Tap `Clear All`
  (capital A in the button, lowercase in the title — two different strings), then
  `$S db-count app_notifications` → `0` and the screen shows the empty state.
- **Pagination.** With more than 10 rows the line reads `Showing 10 of M` (M = rows matching the
  current filter) and the button reads `Show more (<M − 10> remaining)`; each tap adds 10
  (`visibleCount += 10`). Changing the filter resets `visibleCount` to 10 and recomputes M. Assert
  those two numbers against `$S db-count app_notifications` (or the per-type count), never a row
  count read off a lazy list.
- **Tap to view.** The routing in `onItemClick` is a two-branch cascade: a non-blank
  `targetVideoId` first, then `targetDownloadId != null || type == TYPE_DOWNLOAD` → `Downloads`.
  So **every `DOWNLOAD` row navigates to `Downloads` even with both target ids null** — only a
  non-`DOWNLOAD` row with null targets stays on the Notifications screen. The `Tap to view →` label
  is narrower than the routing: it renders only when a target id is present, so a target-less
  `DOWNLOAD` row navigates without advertising it. Tapping also sets `isRead=1` — assert
  `$S db-count app_notifications "id=<id> and isRead=1"` → `1` *after* coming back, because
  navigation leaves the screen.
- **Seeding a precondition.** When the instance has no notifications, seed them instead of faking
  the drive (this force-stops the app — relaunch before driving):

  ```bash
  $S db-push-sql "insert into app_notifications (type,title,message,timestamp,isRead,targetVideoId,targetDownloadId) values
    ('DOWNLOAD','VerifySeeder download','seeded by verify-medianest',strftime('%s','now')*1000,0,'dQw4w9WgXcQ',null),
    ('SUBSCRIPTION','VerifySeeder upload','seeded by verify-medianest',strftime('%s','now')*1000,0,null,null),
    ('SYSTEM','VerifySeeder system','seeded by verify-medianest',strftime('%s','now')*1000,0,null,null),
    ('SYNC','VerifySeeder sync','seeded by verify-medianest',strftime('%s','now')*1000,0,null,null);"
  scripts/ad.sh launch
  ```

  Then assert one card per chip. Clean up with
  `$S db-push-sql "delete from app_notifications where title like 'VerifySeeder%';"`.
- **Cleanup and evidence.** Restore what you changed: re-seed rows you cleared only if the run's
  purpose needed them, and never leave `isRead` flipped on a user's real inbox without saying so.
  `$S crash-check`, a screenshot per chip plus the two dialogs, `snapshot -i` for the badge, and
  `$S db-pull "$ART/db"`.
- **System tray is a different surface.** `MediaNestApp.kt` registers the Android channels
  `downloads`, `bulk_downloads` and `app_updates` — those are OS notifications, not this screen.
  Verifying the in-app inbox proves nothing about them; drive them (or report them **not driven**)
  separately.

## Gotchas

- **Only four types exist, five chips.** `ALL`, `DOWNLOAD`, `SUBSCRIPTION`, `SYSTEM`, `SYNC`;
  `AppNotificationEntity` holds exactly the four `TYPE_*` constants. A row whose `type` is anything
  else is invisible under every chip except `All` — a hand-written INSERT with `type='downloads'`
  looks like a broken filter and is actually bad seed data. Use upper-case enum values.
- **The chip row scrolls, chips surface as `[group]` nodes, and `Downloads` collides.**
  `MediaNestFilterRow` (`horizontalScroll`) can leave `Sync` off-screen on a narrow viewport (use
  `scripts/ad.sh swipe` horizontally — no `--settle`, no 5th duration arg — if it is out of view),
  and filter chips surface as `[group]`, never as a tab role. Always select chips by their `@eN` ref
  from `snapshot -i`; a bare `label="Downloads"` returns `AMBIGUOUS_MATCH` against the bottom-nav
  `Downloads` item.
- **`Mark all read` and `Clear all` are icons, not text.** `content-desc` is `Mark all read` /
  `Clear all`; the dialog buttons are the text `Clear All` (confirm) and `Cancel`. Case matters when
  you copy a string from one place to the other.
- **Clearing is irreversible and unconfirmed by the app's data.** `Clear All` issues
  `DELETE FROM app_notifications`; nothing restores it. Cancel-path verification is the default;
  confirm-path only with seeded throwaway rows.
- **`Show more` is not a load-more from disk.** All rows are already in memory
  (`notifications.take(visibleCount)`); the button only raises a local `visibleCount`. Filter changes
  reset it to 10, so a run that switches tabs and expects its page to persist is wrong.
- **`Tap to view →` is narrower than the routing.** The label needs a target id, but the click
  falls through to `Downloads` for any `TYPE_DOWNLOAD` row even with null targets. A row with neither
  a target nor the download type taps nowhere (it still marks itself read). Assert the CTA before
  using a row as a navigation probe, and do not infer a null target from a missing CTA.
- **`db-push-sql` force-stops the app.** Seed first, then `scripts/ad.sh launch`; driving immediately
  after the write hits a dead process and reads as a crash.
- **Timestamps are epoch millis.** The screen renders `UiUtils.formatRelativeTime`, so seeded rows
  must use milliseconds (`strftime('%s','now')*1000`), and ordering is `timestamp DESC` — a seed with
  seconds lands in 1970 and sorts to the bottom.
- **`db-count` on booleans uses integers.** `isRead=0` / `isRead=1`, never `false`/`true`.
- **The badge is only as fresh as the flow.** `unreadCount` comes from a Room `Flow`; after a
  `db-push-sql` write the app was force-stopped, so the badge is correct only after a relaunch.
- **The POST_NOTIFICATIONS dialog is not this screen.** A first-install permission prompt covers the
  app; dismiss it with `scripts/ad.sh alert dismiss` before asserting anything.
