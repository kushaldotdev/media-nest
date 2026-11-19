# MediaNest — scripts

Build, run and **actually drive** the app on an emulator. Every command is idempotent:
running it twice skips the work already done, so the second `test` costs seconds, not minutes.

## Quick start

```bash
scripts/android-env-check.sh     # read-only: is this machine able to build+drive? changes nothing
scripts/nest.sh up               # build (if changed) + boot emulator (if needed) + install (if changed)
scripts/nest.sh test             # up, then drive the smoke flow → PASS/FAIL
```

That is the whole loop. `scripts/nest.sh test` a second time reuses the APK and the running
emulator, so only the flow itself runs.

## Commands

### `scripts/nest.sh` — the one entry point

| Command | What it does | Typical time |
| --- | --- | --- |
| `nest.sh up` | build → emulator → install, each skipped if already current | 0 s when everything is current |
| `nest.sh test [flow]` | `up`, then run `scripts/flows/<flow>.sh` (default `smoke`) | seconds |
| `nest.sh status` | what is built, booted, installed; display-readback health | instant |
| `nest.sh watch` | mirror the device in a window (scrcpy via WSLg) | instant |
| `nest.sh down` | stop the emulator | ~3 s |
| `nest.sh clean` | stop emulator, delete build outputs | seconds |

Pass `--force` through to `up`/`test` to rebuild and reinstall unconditionally.

### `scripts/dev/*` — the individual steps

| Script | Use |
| --- | --- |
| `dev/emulator.sh up\|down\|snap\|status\|recreate` | emulator control. `up` is a no-op when already booted |
| `dev/host-emulator.sh up\|down\|status` | **emulator on the host OS + TCP adb — the config that can actually be verified** |
| `dev/build.sh [--force] [clean]` | incremental build; prints `up to date` and exits 0 when nothing changed |
| `dev/install.sh [--force] [--uninstall]` | install the APK; `--uninstall` fixes signature mismatches |
| `dev/poke.sh <verb>` | interactive driving (below) |

### `scripts/phone.sh` — a real device, from WSL

```bash
scripts/phone.sh list                     # what adb can see
scripts/phone.sh usb                      # the two USB routes, explained
scripts/phone.sh pair 192.168.1.42:37713 123456   # wireless debugging pairing
scripts/phone.sh wifi 192.168.1.42:5555   # connect over Wi-Fi
scripts/phone.sh install                  # build + install to the phone
scripts/phone.sh test                     # run the smoke flow on the phone
```

Wi-Fi works directly from WSL2 (it reaches the LAN). USB needs either Windows to own the
phone while WSL borrows its adb server (`dev/host-emulator.sh up`), or `usbipd-win` to
forward the USB device into WSL.

### `scripts/dev/poke.sh` — drive the UI

```bash
scripts/dev/poke.sh launch            # start the app, verify it reached the foreground
scripts/dev/poke.sh ui                # dump the view tree: class, text, content-desc, bounds
scripts/dev/poke.sh tap "Downloads"   # tap a node by text or content-desc
scripts/dev/poke.sh tap-xy 540 2100   # tap coordinates
scripts/dev/poke.sh see "Library"     # assert text is on screen
scripts/dev/poke.sh shot home         # screenshot → ~/.cache/medianest/shots/home.png
scripts/dev/poke.sh nav               # walk every bottom-nav destination
scripts/dev/poke.sh logcat            # show crashes only
scripts/dev/poke.sh screen            # display-readback health
```

`ui` is the one to reach for first — it lists what is actually tappable.

### `scripts/verify.sh` — auditable run

The slow path, for a record you can hand to someone:

```bash
scripts/verify.sh                  # build + unit tests + emulator + flow + report
scripts/verify.sh --fast           # reuse the APK and the running emulator
scripts/verify.sh --flow smoke     # choose a flow
```

Writes `artifacts/verify-<UTC>/` with `RESULT.md`, screenshots, ui dumps, `run.mp4`,
`logcat.txt`. Exit code is the verdict.

### `scripts/watch.sh`

Live mirror while an agent drives. Taps come from `adb shell input`, so **no cursor is
drawn** — the UI reacts, the pointer does not move.

## What "tested" means here

The app **launched, a control was driven, the screen changed, and logcat had no `FATAL`**.

Not a pass: `assembleDebug` succeeding, green unit tests, or `adb shell input tap` exiting
`0` — that command returns `0` against a crashed process too.

The smoke flow asserts through the **Compose semantics tree** (`uiautomator dump`), so it
still proves something when framebuffer readback is unavailable. Screenshots are captured
when possible and reported as unavailable when not — never silently skipped.

## Adding a flow

```bash
cp scripts/flows/smoke.sh scripts/flows/myflow.sh
# edit: rename flow_smoke -> flow_myflow
scripts/nest.sh test myflow
```

Helpers available inside a flow (from `scripts/lib/drive.sh`): `launch`, `tap_label "X"`,
`assert_text "X"`, `snap "name"`, `dump_ui`, `ck_logcat`, `start_recording`,
`stop_recording`.

## Environment overrides

```bash
NEST_AVD=nest_api36 NEST_RAM=4096 NEST_PKG=com.example.medianest.debug \
ANDROID_HOME=$HOME/Android/Sdk scripts/nest.sh test
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
scripts/nest.sh test                # everything else unchanged
```

`scripts/lib/common.sh` detects WSL, finds the host's adb server on the default gateway and
exports `ADB_SERVER_SOCKET`, so every other script works untouched. Confirmed working:
launch, all four bottom-nav destinations tapped, screenshots captured, logcat clean.

This is also how a **physical phone** is driven from WSL — see `scripts/phone.sh`.

## Where this repo lives

One working tree, in WSL ext4 — `~/projects/media-nest`. Open it from Windows at
`\\wsl.localhost\Ubuntu-24.04\home\kushal\projects\media-nest` (VS Code Remote-WSL, Explorer).
Backup: bare repo `D:\dev\media-nest-backup.git`, remote `backup`.

Don't build from `/mnt/d`: same 224 files measure 126× slower to read and 73× slower to create
there, and a second copy causes phantom mode-only diffs and stale-mtime builds.

```bash
git push backup main      # snapshot to D:
git push origin main      # publish to GitHub
```
