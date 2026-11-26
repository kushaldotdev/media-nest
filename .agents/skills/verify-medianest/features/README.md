# Features — MediaNest

The maintained verification map for MediaNest, an offline-first Android media hub. Read this
index before driving the app, then use the matching feature file as the recipe. One file per
user-facing feature; each one says what it is, how a user reaches it, how to drive it with
`scripts/ad.sh`, and the traps that invalidate a run.

## Baseline preconditions

- App installed: `com.example.medianest.debug` (debug `applicationIdSuffix`).
- Emulator booted and **pixels readable** — under WSL the AVD runs on the Windows host and adb is
  bridged via `ADB_SERVER_SOCKET`; `scripts/nest.sh status` must report `screencap works`. A run
  whose screenshots are unreadable is not evidence.
- Bottom navigation present in the snapshot (`Home` / `Collections` / `Downloads` / `Settings`).
  That is the "usable, not merely running" gate.
- `.agents/skills/verify-medianest/scripts/nest-state.sh doctor` passes: `run-as` works, so the
  DB and DataStore can be asserted.
- Database path for state assertions:
  `/data/data/com.example.medianest.debug/databases/media_nest.db` (WAL mode — always pull
  `.db` + `-wal` + `-shm` together, i.e. use `nest-state.sh db-pull`/`db-query`).
- Never drive an instance this run did not start or health-check. **Never run two agents against
  the same emulator** (`nest_hw`) — there is one device and one driver.

## Driving conventions

- Start every recipe with `scripts/ad.sh launch`; it returns the initial `@e*` refs and a clean
  baseline. Clear logcat with `nest-state.sh logcat-clear` if you want a per-step crash window.
- Match nodes by snapshot ref or by `label=` / `id=` / `role=` selectors — never by coordinates
  unless the tree is empty (`screenshot`, then `press <x> <y>`).
- After any mutating action use `--settle` and continue from the printed diff; run
  `snapshot -i` only when the diff lacks the next target.
- Prefer `wait text "…"` / `wait '<selector>'` over fixed sleeps; animations make a snapshot
  partial and refs stale.
- Use `is` to read a control's state (`is checked 'role=switch label="X"'`) instead of inferring
  it from pixels.
- The first install lands on the POST_NOTIFICATIONS dialog; `scripts/dev/install.sh` grants the
  permission, and `scripts/ad.sh alert dismiss` clears anything left over.
- A `back` from a root destination exits to the launcher; that is expected, not a crash.
- Treat every command as literal. Keep quoted labels unchanged.

## Proof and skip reporting

- Capture the user action **and** the resulting state, not just the final screen.
- UI proof = screenshot with the app visible + the semantics tree at that step.
- State proof = a second, read-only view of the stored value (`nest-state.sh db-query`,
  `pref-get`, a file listing, or a relaunch that still shows the change).
- End with `nest-state.sh crash-check`; it exits non-zero on `FATAL EXCEPTION` or `ANR`.
- Record the feature ID and entry point used with every artifact.
- Report an unreachable path with the attempted command and the unmet precondition
  (`verified-unreachable`), never as verified through a different path.
- Report an undriven feature as **"not driven"**.

## Features

| # | Feature | Verifies |
| --- | --- | --- |
| 1 | [App shell](./app-shell.md) | Launch, first-run notification permission, bottom navigation (`Home`, `Collections`, `Downloads`, `Settings`), MiniPlayer bar, deep links, back behavior |
| 2 | [Search and intake](./search-and-intake.md) | URL extraction, YouTube search, link history filters, Quick Actions, resolution picker, bulk downloads |
| 3 | [Video detail](./video-detail.md) | Detail screen, local downloaded versions & deletion, watch count & history log, saved position reset, available stream downloads |
| 4 | [Playback and player](./playback-and-player.md) | ExoPlayer/Media3 playback controls, speed & quality switching, Up Next queue & autoplay, resume/update prompt, MiniPlayer handoff |
| 5 | [Download queue](./download-queue.md) | Active queue, pause/resume/cancel/retry/restart, completed batch actions, extract audio, concurrency limit, sort modes |
| 6 | [Library and folders](./library-folders.md) | `Collections` 6 tabs, search, grid/list & per-tab sort, folder create/rename/duplicate-refusal/delete, multi-select move/delete |
| 7 | [Subscriptions](./subscriptions.md) | `Subscriptions` screen & inline channel/playlist detail, Show Shorts, Auto-download, Audio-only toggles, Unsubscribe/Delete playlist |
| 8 | [Statistics](./statistics.md) | `App Statistics` screen overview, watch metrics, storage breakdown, download health, top content, resolution/channel/folder charts |
| 9 | [Notifications](./notifications.md) | `Notifications` screen, 5 type filter chips, unread badge, mark-all-read, clear-all dialog, deep-link tap navigation |
| 10 | [Settings and maintenance](./settings-maintenance.md) | Settings sections (`Downloads & Network`, `Preferences`, `Data Management & Storage`, `About & Updates`, `VPS Sync & Cloud`) & DataStore persistence |

**Out of scope for now.** The sync backend (`sync-server/`, a FastAPI service) has no recipe and
is intentionally deferred: `verify-sync-server` does not exist yet. Anything that requires a
running sync server — device pairing, payload upload/download, cross-device restore — is
**not driven**, and must be reported that way rather than inferred from the app's UI.

## Feature entry contract

Each feature file starts with an H1 title and one paragraph describing the user-visible behavior.
It then uses exactly four H2 sections, in this order:

1. `## Sub-features` — short stable IDs, one line each.
2. `## How to get to it (user POV)` — every entry point the user has.
3. `## Driving it with ad.sh` — `Preconditions:` then labeled bullets pairing each user action
   with the exact `scripts/ad.sh` (or `nest-state.sh`) command and the observable result.
4. `## Gotchas` — traps that waste or invalidate a run.

Keep implementation detail out of the map. Name only user paths, stable handles, required state,
commands, and observable proof. A new user-facing feature without a file here is not verified —
add the file before claiming it works.
