---
name: verify-medianest
description: "Drive the MediaNest Android app on a real emulator and prove a feature works — launch, doctor, tap through the UI, assert persisted state, capture screenshots and a recording. Use before reporting any MediaNest change as done, or when asked to test/verify/check/run the app."
---

# verify-medianest

The app under test is **MediaNest** (`com.example.medianest.debug`) — an offline-first Android
media hub. The surface a user touches is the Compose UI on an emulator: bottom nav
(Home / Downloads / Collections / Settings), a player, and a download pipeline.

This skill exists because "it compiled" and "unit tests pass" are not proof. Everything below
is scripted and already in the repo; do not hand-roll `adb` on top of it.

## Launch

```bash
scripts/nest.sh up          # build (if sources changed) + boot emulator (if needed) + install
```

Ready means **usable**, not "a process answered": the app is in the foreground and the bottom
navigation is in the semantics tree. `scripts/nest.sh up` asserts the APK is installed and the
device is booted; the flow's own `launch` asserts the foreground.

Teardown (leave the emulator for the next run unless you need the RAM):

```bash
scripts/nest.sh down
```

**Which emulator.** Detected, not chosen. Under WSL the AVD is booted **on the Windows host**
by `scripts/dev/host-emulator.sh` and driven over TCP adb (`ADB_SERVER_SOCKET`), because a
guest emulator inside WSL2 cannot read its own framebuffer — `screencap`, `screenrecord` and
`uiautomator` all return nothing, so the app can be installed there but never driven. On a
Linux host or a physical phone the same commands boot locally. `scripts/nest.sh status` prints
which one you got; when `screencap` is unreadable the run **fails** rather than passing on
semantics alone.

## Doctor

One read-only check that answers "is this instance worth driving?":

```bash
scripts/android-env-check.sh      # KVM openable, JDK 21, SDK, AVD, APK age
scripts/nest.sh status            # what is booted / installed / whether pixels read back
```

Run it first whenever anything looks off, and again after any failed drive. The three
conditions that produce a misleading "healthy" app:

| Symptom | Real cause |
| --- | --- |
| App runs, screenshots black | guest has no readable framebuffer → run on the host (handled by `emulator.sh`) |
| `Activity not found` | used `com.example.medianest`; the debug id is `com.example.medianest.debug` |
| emulator dies instantly | `/dev/kvm` present but not **openable** — probe with `python3 -c "import os; os.open('/dev/kvm', os.O_RDWR)"` |

## Drive

Harness = `scripts/lib/drive.sh`, sourced by every flow. Stable handles are **Compose text and
`content-desc`**, read from the live semantics tree — never coordinates.

```bash
. scripts/lib/drive.sh
launch                         # force-stop + start, asserts foreground, clears logcat
tap_label "Downloads"          # tap by text or content-desc, retries through animations
assert_text "All Videos"       # fail unless the string is on screen now
assert_any "No downloads yet" "Active Queue"   # one of several correct markers
                                               # (sections scroll off; one exact
                                               # string turns a pass into a red herring)
wait_for "Completed Downloads" 30   # poll instead of sleeping
wait_gone "Downloading" 30          # prove a job finished
assert_state "db row" "sqlite3 ..." # assert state the screen cannot show
snap "01-home"                 # screenshot into $ART/screens/
ck_logcat                      # fail on FATAL EXCEPTION / ANR
```

Real labels in this app (from source, not guessed): `Home`, `Downloads`, `Collections`,
`Settings`, `Search or paste URL...`, `History`, `Offline Media Hub`, `Active Queue`,
`Completed Downloads`, `Pause All`, `Resume All`, `All Videos`, `Create`, `New Folder`,
`Repair Library`, `Sync Now`, `Export Backup`, `Import Backup`, `Check for Updates`.

Deep links beat taps where they exist — `MainActivity` handles
`com.example.medianest.ACTION_NAVIGATE_DOWNLOADS` and `...ACTION_NAVIGATE_UPDATES`.

```bash
adb shell am start -n com.example.medianest.debug/com.example.medianest.MainActivity \
  -a com.example.medianest.ACTION_NAVIGATE_DOWNLOADS
```

## Evidence

Every run leaves, under `artifacts/verify-<UTC>/` (auditable path) or
`~/.cache/medianest/session-<UTC>/` (fast path):

| Artifact | Proves |
| --- | --- |
| `RESULT.md` | PASS/FAIL per step + commit + evidence table |
| `screens/*.png` | what the screen actually looked like, per driven step |
| `ui/*.xml` | the semantics tree at that step (works even without pixels) |
| `run.mp4` | the whole flow |
| `logcat.txt`, `logcat-crashes.txt` | no `FATAL`, no `ANR` |
| `build.log`, `unit-tests.log` | the code that was driven |

Proof standards for this repo:

- Exercise the user path (tap the real control), not a test-only hook.
- Capture the **action and the result**, not just the final screen.
- Assert side effects — a downloaded file on disk, a row in
  `/data/data/com.example.medianest.debug/databases/media_nest.db`, a preference that
  survives a relaunch — not only what is visible.
- A tap that exited `0` proves nothing; `adb shell input tap` returns `0` against a crashed
  process. `ck_logcat` and the screenshot are what settle it.
- Report an undriven area as **"not driven"**, never as working.

## Cleanup

```bash
scripts/nest.sh down              # stop the emulator this run booted
```

Never kill by process name, and never delete `artifacts/verify-*/` — that is the proof, and it
must survive teardown. Scratch state lives in `~/.cache/medianest/` and is safe to remove.

## Helpers

| Script | Purpose |
| --- | --- |
| `scripts/nest.sh {up,test <flow>,watch,status,down,clean}` | one entry point, idempotent |
| `scripts/verify.sh [--flow N] [--fast] [--keep]` | auditable run + `RESULT.md` |
| `scripts/emulator-up.sh` | alias for `scripts/dev/emulator.sh up` |
| `scripts/dev/host-emulator.sh {up,down,status}` | boot the AVD on the Windows host, expose TCP adb |
| `scripts/dev/poke.sh {launch,tap,see,shot,ui,nav,logcat}` | interactive poking between runs |
| `scripts/watch.sh` | scrcpy mirror (taps come from `adb`, so no cursor moves) |
| `scripts/android-env-check.sh` | read-only readiness probe |
