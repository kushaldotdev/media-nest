# Agent testing protocol — MediaNest

Every coding agent working in this repo (pi, Claude Code, Codex, opencode, Cursor, or a
human) follows this file when asked to **test**, **verify**, **check**, or **run** the app.

The full procedure lives in [docs/TESTING.md](../docs/TESTING.md). This file is the short
version plus the rules that are easy to get wrong.

## Non-negotiables

1. **"Tested" means driven.** Launch on an emulator, tap a real control, confirm the screen
   changed, look at the screenshot, and check logcat for `FATAL`. Compiling is not testing.
   Unit tests are not testing. A sequence of `adb shell input tap` calls that exited `0` is
   **not** testing — that command returns `0` against a crashed app.
2. **Use the scripts, don't hand-roll adb.** `scripts/verify.sh` is the entry point. It
   builds, boots, installs, drives a flow, records, and writes `artifacts/verify-<UTC>/RESULT.md`.
   Adding ad-hoc `adb` calls on top of it is how runs become unreproducible.
3. **Attach evidence.** The screenshots and `run.mp4` are the deliverable. A summary without
   them is not a verified result.
4. **Never report an undriven area as working.** Say "not driven" explicitly.
5. **The debug package is `com.example.medianest.debug`** (applicationIdSuffix). Using
   `com.example.medianest` against a debug build gives `Activity not found`, which reads
   like a crash but is a typo.

## The loop

```bash
scripts/android-env-check.sh     # read-only: is KVM/JDK/SDK/AVD ready?
./gradlew :app:assembleDebug
./gradlew --stop                 # free the 6GB Gradle + 3GB Kotlin daemons before boot
scripts/emulator-up.sh           # headless boot, waits for sys.boot_completed
scripts/verify.sh --quick        # install + drive the smoke flow, leave artifacts
```

Optional live view while it drives: `scripts/watch.sh` (scrcpy via WSLg). Taps originate
from `adb shell input`, so **no mouse cursor is drawn** — the UI reacts, nothing moves.

## Driving primitives

`. scripts/lib/drive.sh` then use:

| Helper | Does |
| --- | --- |
| `launch` | force-stop + start, verifies the app reached the foreground |
| `tap_label "Downloads"` | dumps the Compose semantics tree, finds the node, taps its centre, retries through animations |
| `assert_text "Library"` | fails unless that string is in the live tree |
| `snap "name"` | screenshot into `$ART/screens/`, catches blank frames |
| `dump_ui` | raw `uiautomator` XML into `$ART/ui/` |
| `ck_logcat` | fails on `FATAL EXCEPTION` / `ANR in <pkg>` |
| `start_recording` / `stop_recording` | `screenrecord` → `$ART/run.mp4` |

Write flows as `scripts/flows/<name>.sh` exporting `flow_<name>()`, then
`scripts/verify.sh --flow <name>`.

## Environment facts you will otherwise rediscover

- Build inside WSL on **ext4** (`~/projects/media-nest`). Building over `/mnt/d` is several
  times slower and breaks file watching.
- `/dev/kvm` must be **openable**, not merely present. `ls -l` shows `660 root:kvm` either
  way. Probe with `python3 -c "import os; os.open('/dev/kvm', os.O_RDWR)"`.
- The emulator needs `-gpu swiftshader_indirect`. Without it the guest renders nothing and
  screenshots are black **while adb reports the app healthy** — the most misleading failure
  mode here.
- `uiautomator dump` returns "could not get idle state" while an animation is running. Retry;
  do not conclude the app is broken.
- Compose `IconButton`s often have no `text`; match `content-desc` first.
- Release signing reads `$user.home/.android/debug.keystore` — test with `debug`.

## When a run fails

Read `artifacts/verify-<UTC>/` in this order: `RESULT.md` → `logcat-crashes.txt` →
the failing screenshot → `build.log`. Fix the cause, re-run the same command, and only then
report. Do not switch to manual `.bat`/Windows builds to "get a pass" — that exercises a
different toolchain than the one being verified.
