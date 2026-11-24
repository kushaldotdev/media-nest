# Settings and maintenance

Settings holds the maintenance surface: library repair, server sync, backup export/import,
and the update check. These are the actions most likely to be reported as "works" without
ever being run.

## Sub-features

- `set-open` — the Settings destination renders its sections.
- `set-repair` — `Repair Library` / `View & Clean Broken Files` reachable and safe to open.
- `set-sync` — `Sync Now` and `Register Device` reachable; their outcome is observable.
- `set-backup` — `Export Backup` / `Import Backup` reachable.
- `set-updates` — `Check for Updates` surfaces a result, not silence.
- `set-persist` — a changed preference survives a relaunch.

## How to get to it (user POV)

- Tap `Settings` in the bottom nav.
- Or send the update deep link: `com.example.medianest.ACTION_NAVIGATE_UPDATES`.

## Driving it with drive.sh

Preconditions:

- App launched; `Settings` reachable from the nav.
- Know whether a server/sync account is configured — several actions no-op without one.

- **Land on the screen.** Run `tap_label "Settings"`, then `assert_text "Repair Library"`.
- **Repair surface.** Run `tap_label "View & Clean Broken Files"` and assert the resulting
  screen or dialog, then `BACK`. Destructive actions (`Delete All Broken Files`) are **not**
  driven against seeded data — say so explicitly instead of driving them.
- **Sync.** Run `tap_label "Sync Now"`. Then wait for its terminal state: a progress
  indication followed by success or an error. `wait_gone` on the progress label, then
  `assert_text` on whichever outcome appeared. With no account configured, the correct report
  is "sync not configured — not driven".
- **Backup.** Run `tap_label "Export Backup"` and drive the system file picker to completion,
  or record that the picker appeared and stop. Then
  `assert_state "backup file" "ls /sdcard/Download | grep -i backup"`.
- **Updates.** Run `tap_label "Check for Updates"`, then wait for a result label
  (`Download & Install` / `Install Update` / an error). Silence after 30s is a failure to
  report, not a pass.
- **Preference persistence.** Change one toggle, `launch` again, return to `Settings` and
  assert it kept the value. The preference also lives in the app's DataStore/db as a
  read-only second view.
- **Proof.** `snap` per action, `ck_logcat` at the end. Never leave the app mid-picker.

## Gotchas

- Destructive maintenance actions are one-way. Do not drive `Delete All Broken Files` or
  `Import Backup` (it overwrites the database) against an instance whose data you need.
- Several actions require a configured server; without one they no-op silently. Report that
  as an unmet precondition, never as a pass.
- The file picker is system UI — `uiautomator` returns it, not the app. Re-dump before
  asserting anything app-side again.
- `Sync Now` gives no feedback until the request completes; a fixed sleep will both slow the
  run and produce false passes. Use `wait_for` on the real outcome.
- Release signing reads `$user.home/.android/debug.keystore` — always test the debug build.
