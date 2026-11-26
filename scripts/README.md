# MediaNest — scripts

Build, boot, and install MediaNest cleanly so it can be driven with **`agent-device`**.
Every command is idempotent: running it twice skips the work already done.

## Quick start

```bash
scripts/android-env-check.sh     # read-only: is this machine able to build+boot? changes nothing
scripts/nest.sh up               # build (if changed) + boot emulator (if needed) + install (if changed)
scripts/ad.sh launch             # open com.example.medianest.debug via agent-device
```

## Commands

### `scripts/nest.sh` — the entry point

| Command | What it does | Typical time |
| --- | --- | --- |
| `nest.sh up` | build → emulator → install, each skipped if already current | 0 s when everything is current |
| `nest.sh status` | what is built, booted, installed; display-readback health | instant |
| `nest.sh down` | stop the emulator | ~3 s |
| `nest.sh clean` | stop emulator, delete build outputs | seconds |

Pass `--force` through to `up` to rebuild and reinstall unconditionally.

### `scripts/dev/*` — the individual steps

| Script | Use |
| --- | --- |
| `dev/emulator.sh up\|down\|snap\|status\|recreate` | emulator control. `up` is a no-op when already booted |
| `dev/host-emulator.sh up\|down\|status\|warm` | **emulator on the host OS + TCP adb — the config that can actually be verified** |
| `dev/build.sh [--force] [clean]` | incremental build; prints `up to date` and exits 0 when nothing changed |
| `dev/install.sh [--force] [--uninstall]` | install the APK; `--uninstall` fixes signature mismatches |
| `dev/fast-test.sh <TestClass\|pattern>` | incremental unit-test runner |
| `dev/warm.sh` | pre-warms Gradle daemon, emulator, animation scales, and installed APK |

### `scripts/phone.sh` — a real device, from WSL

```bash
scripts/phone.sh list                     # what adb can see
scripts/phone.sh usb                      # the two USB routes, explained
scripts/phone.sh pair 192.168.1.42:37713 123456   # wireless debugging pairing
scripts/phone.sh wifi 192.168.1.42:5555   # connect over Wi-Fi
scripts/phone.sh install                  # build + install to the phone
scripts/phone.sh use <serial>             # pin one device when several are attached
```

Wi-Fi works directly from WSL2 (it reaches the LAN). USB needs either Windows to own the
phone while WSL borrows its adb server (`dev/host-emulator.sh up`), or `usbipd-win` to
forward the USB device into WSL. Once installed, drive the phone with `agent-device`.

## Driving the UI (`scripts/ad.sh` / `agent-device`)

Use `scripts/ad.sh` (which auto-bridges `ADB_SERVER_SOCKET` and selects the booted Android
serial before calling `agent-device`):

```bash
scripts/ad.sh launch                   # open com.example.medianest.debug --foreground
scripts/ad.sh jump downloads           # deep-link straight to Downloads (or 'updates')
scripts/ad.sh click @e24 --settle      # tap node and wait for UI settle
scripts/ad.sh snapshot -i              # capture interactive UI tree with fresh @e* refs
scripts/ad.sh wait text "Downloads"    # wait for text
scripts/ad.sh screenshot /tmp/shot.png # capture screenshot
scripts/ad.sh close                    # close session
```

## What "tested" means here

The app **launched, real controls were driven, the screen changed, and logcat had no `FATAL`**.

Not a pass: `assembleDebug` succeeding, green unit tests, or `adb shell input tap` exiting
`0` — that command returns `0` against a crashed process too.

## Environment overrides

```bash
NEST_AVD=nest_api36 NEST_RAM=4096 NEST_PKG=com.example.medianest.debug \
ANDROID_HOME=$HOME/Android/Sdk scripts/nest.sh up
```

## Known host limitation (WSL2) — and the fix

Inside WSL2 the emulator cannot read its own framebuffer:

```
Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma
```

Every capture path returns nothing — `screencap`, `screenrecord`, **and** `uiautomator`.
Tried and failed: `-gpu swiftshader_indirect`, `-gpu swiftshader`, `-gpu off`,
`-feature -ReadColorBufferDma`. So an in-WSL emulator can be built for and installed to, but
**it cannot be driven or verified**.

**The working configuration is emulator-on-host, driven-from-WSL:**

```bash
scripts/dev/host-emulator.sh up     # Windows emulator (hw.gpu.mode=host) + TCP adb server
```

`scripts/lib/common.sh` detects WSL, finds the host's adb server on the default gateway and
exports `ADB_SERVER_SOCKET`, so every other script works untouched. Confirmed working with
`agent-device`: launch, navigation, snapshots, screenshots, and logcat verification.

This is also how a **physical phone** is driven from WSL — see `scripts/phone.sh`.

## Where this repo lives

One working tree, in WSL ext4 — `~/projects/media-nest`. Open it from Windows at
`\\wsl.localhost\Ubuntu-24.04\home\kushal\projects\media-nest` (VS Code Remote-WSL, Explorer).
GitHub (`origin`) is the only remote and the only backup.

Don't build from `/mnt/d`: same 224 files measure 126× slower to read and 73× slower to create
there, and a second copy causes phantom mode-only diffs and stale-mtime builds.

```bash
git push origin main      # publish + back up
```
