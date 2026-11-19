---
name: medianest-app-testing
description: Always use when asked to test, verify, check, run, or drive the MediaNest Android app, or to confirm a UI change actually works on a device. Covers building in WSL, booting a headless emulator, tapping real controls, and producing screenshot/recording evidence.
---

# MediaNest app testing (drive it, don't just build it)

## What counts as "tested"

The app **launched on an emulator, a control was driven, the screen changed, the screenshot was
looked at, and logcat had no `FATAL`.** Nothing less is a pass:

- `./gradlew assembleDebug` succeeding → compiled, not tested
- unit tests green → logic only, no UI
- `adb shell input tap` exiting `0` → meaningless; it returns `0` against a crashed process

## Run it

```bash
scripts/android-env-check.sh          # read-only readiness probe
./gradlew :app:assembleDebug
./gradlew --stop                      # free 6GB Gradle + 3GB Kotlin daemons before booting
scripts/emulator-up.sh                # headless AVD + wait for sys.boot_completed
scripts/verify.sh --quick             # install, drive flow, record, write RESULT.md
```

`scripts/verify.sh` (no flags) does the full loop: build → unit tests → emulator → install →
flow → artifacts. Exit code is the verdict. Evidence lands in `artifacts/verify-<UTC>/`:
`RESULT.md`, `screens/*.png`, `ui/*.xml`, `run.mp4`, `logcat.txt`, `logcat-crashes.txt`.

Watch live while it drives: `scripts/watch.sh` (scrcpy through WSLg). Taps come from
`adb shell input`, so no cursor is drawn — the UI reacts, the pointer doesn't move. Say that
to the user up front instead of letting them wonder.

## Driving primitives

```bash
. scripts/lib/drive.sh
launch                       # force-stop + start, asserts foreground
tap_label "Downloads"        # uiautomator tree → bounds centre → input tap, retries animations
assert_text "Library"        # fails if absent from the live tree
snap "02-home"              # screenshot + blank-frame detection
ck_logcat                    # fails on FATAL EXCEPTION / ANR
```

Add flows as `scripts/flows/<name>.sh` defining `flow_<name>()`, run with `--flow <name>`.

## Rules that cost time when ignored

- **Debug package id is `com.example.medianest.debug`.** Using `com.example.medianest` gives
  `Activity not found`, which looks like a crash but is a typo.
- **`-gpu swiftshader_indirect` is mandatory.** Without it the guest renders nothing and
  screenshots are black *while adb reports the app healthy*.
- **`/dev/kvm` must be openable, not present.** It shows as `660 root:kvm` either way; probe
  with `python3 -c "import os; os.open('/dev/kvm', os.O_RDWR)"`. Fix is
  `sudo usermod -aG kvm $USER` + re-login.
- **`uiautomator dump` says "could not get idle state" during animations.** Wait and retry.
- **Build on ext4, not `/mnt/d`.** 9p mounts are several× slower and break file watching.
- **Test with `debug`.** Release signing reads `$user.home/.android/debug.keystore`.
- **Deep-link intents beat pixel taps** for jumping to a screen:
  `adb shell am start -n com.example.medianest.debug/com.example.medianest.MainActivity -a com.example.medianest.ACTION_NAVIGATE_DOWNLOADS`.

## Reporting

Attach the screenshots and `run.mp4`; report per-step PASS/FAIL with the screenshot name.
Anything not driven is reported as **not driven** — never implied as working.
