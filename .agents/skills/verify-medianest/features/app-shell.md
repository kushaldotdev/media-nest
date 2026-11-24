# App shell and navigation

The app opens on Home and exposes four bottom destinations — Home, Downloads, Collections,
Settings — and a back gesture that leaves the app from any root destination without crashing.

## Sub-features

- `shell-launch` — cold start reaches an interactive screen with the bottom nav present.
- `shell-notifications` — the first-run POST_NOTIFICATIONS prompt is cleared and the app
  becomes interactive.
- `shell-nav` — each of the four destinations is reachable and swaps the content tree.
- `shell-back` — back from a root destination exits to the launcher, cleanly.

## How to get to it (user POV)

- Tap the launcher icon, or the app is already foregrounded from a previous run.
- Tap any of the four bottom-nav items: `Home`, `Downloads`, `Collections`, `Settings`.

## Driving it with drive.sh

Preconditions:

- Emulator booted, APK installed, `scripts/nest.sh status` reports `screencap works`.
- `launch` runs first so the permission dialog and any stale process are gone.

- **Launch.** Start the app. Run `launch`. It prints `launched (foreground)`; the focus line
  names `com.example.medianest.debug`.
- **Gated readiness.** Wait for the nav bar, not just a non-empty tree. Run
  `wait_for "Downloads" 20`. A node count alone is a weak gate — 12 nodes passes while the nav
  bar is still missing.
- **Navigate.** For each of `Downloads`, `Collections`, `Settings`, `Home`: run
  `tap_label "<label>"` then `assert_text "<label>"`. The content tree changes per
  destination (`Active Queue` on Downloads, `All Videos` on Collections).
- **Proof.** Run `snap "01-shell"` and `dump_ui`. A readable 1080x2400 PNG exists in
  `$ART/screens/` — a black or missing frame fails the run.
- **Back.** Run `adb shell input keyevent 4`, then read `dumpsys window | grep mCurrentFocus`.
  The app is either still in front or the launcher is; anything else is a failure.
- **Crash gate.** Run `ck_logcat`. No `FATAL EXCEPTION` and no `ANR in` the package.

## Gotchas

- The first install shows POST_NOTIFICATIONS; `launch` taps `Allow` for you.
- A fresh emulator may raise "System UI isn't responding" on top of the app, which makes
  `uiautomator` return the dialog's ~3 nodes and every later assertion lie. `launch` and
  `dismiss_system_dialogs` clear it first.
- `uiautomator dump` reports "could not get idle state" mid-animation. Retry; it is not a bug.
- A passing tap means nothing on its own — the screenshot plus `ck_logcat` are the proof.
