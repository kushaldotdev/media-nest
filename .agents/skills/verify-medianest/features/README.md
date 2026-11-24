# MediaNest verification map

This directory is the maintained source for verifying the user-facing behavior of MediaNest.
Read this index before driving the app, then use the matching feature file as the recipe.

## Baseline preconditions

- App installed: `com.example.medianest.debug` (debug `applicationIdSuffix`).
- Emulator booted and **pixels readable** — under WSL that means the AVD runs on the Windows
  host; `scripts/nest.sh status` reports `screencap works`. Do not accept a run whose
  screenshots are unreadable.
- Bottom navigation present in the semantics tree (Home / Downloads / Collections / Settings).
  This is the "usable, not merely running" gate.
- Database path if a recipe asserts stored state:
  `/data/data/com.example.medianest.debug/databases/media_nest.db`.
- Never drive an instance this run did not start or health-check.

## Driving conventions

- Start every recipe with `launch` (it clears logcat, so crashes after it belong to the run).
- Match nodes by **text or `content-desc`** via `tap_label`; never hard-code coordinates.
- Prefer `wait_for` / `wait_gone` over `sleep`; animations make `uiautomator` return a partial
  tree, which `tap_label` already retries through.
- Use `assert_any` when several markers are equally correct (a header can scroll off, an
  empty state has different text). One exact string per screen is how a good run reads red.
- The first install lands on the POST_NOTIFICATIONS dialog — `launch` clears it.
- A `BACK` from a root destination exits to the launcher; that is expected, not a crash.
- Treat every command as literal. Keep quoted labels unchanged.

## Proof and skip reporting

- Capture the user action **and** the resulting state, not just the final screen.
- UI proof = screenshot with the app visible + the semantics tree at that step.
- State proof = a second, read-only view of the stored value (file listing, DB row, relaunch).
- Record the feature ID and entry point used with every artifact.
- Report an unreachable path with the attempted command and the unmet precondition
  (`verified-unreachable`), never as verified through a different path.
- Report an undriven feature as **"not driven"**.

## Feature entry contract

Each feature file starts with an H1 title and one paragraph describing the user-visible
behavior. Four H2 sections, in this order:

1. `Sub-features` — short IDs, one line each.
2. `How to get to it (user POV)` — every entry point the user has.
3. `Driving it with drive.sh` — `Preconditions:` then labeled bullets pairing each user
   action with the exact helper call and the observable result.
4. `Gotchas` — traps that waste or invalidate a run.

Keep implementation detail out of the map. Name only user paths, stable handles, required
state, commands, and observable proof.

## Features

- [App shell and navigation](./app-shell.md) — cold start, the four destinations, back/exit.
- [Search and link intake](./search-and-intake.md) — the Home search field, link history,
  metadata fetch entry point.
- [Download queue](./download-queue.md) — active queue states, pause/resume-all, completed
  list and its storage footer.
- [Library and folders](./library-folders.md) — All Videos, folder creation, multi-select.
- [Settings and maintenance](./settings-maintenance.md) — repair library, sync, backup
  export/import, update check.

Features not yet mapped (driven only by hand or not at all): playback/player gestures,
subscriptions and inline subscription screens, notifications screen, statistics, video
detail, the download worker itself (network + yt-dlp pipeline), server-side sync payloads.
Anything touching those is **not driven** until it gets a file here.
