---
name: medianest-app-testing
description: Always use when asked to test, verify, check, run, or drive the MediaNest Android app, or to confirm a UI change actually works on a device. Covers building in WSL, booting the Windows host emulator, driving UI with agent-device, and checking logcat.
---

# MediaNest app testing (drive it, don't just build it)

## What counts as "tested"

The app **launched on an emulator, real controls were driven, the screen changed, and logcat had no `FATAL`.** Nothing less is a pass:

- `./gradlew assembleDebug` succeeding → compiled, not tested
- unit tests green → logic only, no UI
- `adb shell input tap` exiting `0` → meaningless; it returns `0` against a crashed process

## Run it (`scripts/nest.sh up` + `scripts/ad.sh`)

```bash
scripts/android-env-check.sh          # read-only readiness probe
scripts/nest.sh up                    # build (if changed) + boot Windows host AVD + install APK
scripts/ad.sh launch                  # open com.example.medianest.debug via agent-device (auto-bridges ADB)
```

* **Live view:** The Windows host emulator window (`Android Emulator - nest_hw:5554`) opens directly on your Windows desktop so you can watch and interact with it alongside your agent.
* **Agent driving (`scripts/ad.sh` → `agent-device`):** Use `scripts/ad.sh` so WSL `ADB_SERVER_SOCKET` and `--platform android --serial` are always wired automatically:

```bash
scripts/ad.sh launch
scripts/ad.sh click @e24 --settle
scripts/ad.sh snapshot -i
scripts/ad.sh wait text "Downloads"
scripts/ad.sh screenshot /tmp/screen.png
scripts/ad.sh jump downloads          # or: jump updates (MainActivity deep links)
scripts/ad.sh close
```

* Keep `refs` fresh: after any `--settle` action that changes the screen, use the newly issued `@e*~s...` refs from the diff or run `scripts/ad.sh snapshot -i`.
* If a system surface blocks the app (e.g., `"System UI isn't responding"` or permission prompt), dismiss it with `scripts/ad.sh alert dismiss` or tap `Allow`/`Wait` before continuing.
* Always verify no crashes occurred via `adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime.*com.example.medianest.debug|ANR in com.example.medianest.debug"`.

## Rules that cost time when ignored

- **Debug package id is `com.example.medianest.debug`.** Using `com.example.medianest` gives
  `Activity not found`, which looks like a crash but is a typo.
- **`/dev/kvm` must be openable, not present.** It shows as `660 root:kvm` either way; probe
  with `python3 -c "import os; os.open('/dev/kvm', os.O_RDWR)"`. Fix is
  `sudo usermod -aG kvm $USER` + re-login.
- **Build on ext4, not `/mnt/d`.** 9p mounts are several× slower and break file watching.
- **Test with `debug`.** Release signing reads `$user.home/.android/debug.keystore`.
- **Deep-link intents beat pixel taps** for jumping to a screen:
  `adb shell am start -n com.example.medianest.debug/com.example.medianest.MainActivity -a com.example.medianest.ACTION_NAVIGATE_DOWNLOADS`.

## Reporting

Report per-step PASS/FAIL (with observed UI diffs/screenshots and clean logcat).
Anything not driven is reported as **not driven** — never implied as working.
