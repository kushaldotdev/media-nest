# Testing MediaNest by Actually Driving the App

This file is the contract for **any agent** (pi, Claude Code, Codex, opencode, Cursor, or a
human) working in this repo. It is not documentation about testing — it is the procedure you
are expected to follow, and the scripts it names are the ones you are expected to run.

"Tested" in this repo has exactly one meaning:

> The app launched on a real Android emulator, a control was driven, the screen changed, the
> screenshot was looked at, and logcat had no `FATAL`.

A green `assembleDebug`, a passing `testDebugUnitTest`, or a series of `adb shell input tap`
calls that returned exit code 0 **do not meet that bar**. `adb shell input tap` returns 0
against a crashed app. Only the screenshot and logcat settle it.

---

## 0. The one-command loop

```bash
scripts/verify.sh            # build + unit tests + boot emulator + install + smoke flow
scripts/verify.sh --keep     # leave the emulator running afterwards (for interactive poking)
scripts/verify.sh --quick    # reuse a running emulator, skip the rebuild
```

Artifacts land in `artifacts/verify-<UTC timestamp>/`:

| File | What it is |
| --- | --- |
| `build.log` | Full Gradle output |
| `unit-tests.log` | `testDebugUnitTest` result |
| `screens/NN-*.png` | One screenshot per driven step, in order |
| `run.mp4` | Video of the whole driven flow |
| `ui/NN-*.xml` | The uiautomator tree captured at each step |
| `logcat.txt` | Full dump, filtered copy at `logcat-crashes.txt` |
| `RESULT.md` | Pass/fail per step, with the evidence table |

`scripts/verify.sh` exits non-zero if any step fails, a screenshot is blank, or logcat
contains a crash. **Report the exit code and attach `RESULT.md`, `run.mp4` and the
screenshots.** Do not summarise from memory.

---

## 1. Environment

Everything runs **inside WSL2 Ubuntu**, against the Linux filesystem. Do not build over
`/mnt/d` — 9p mounts are several times slower for Gradle's file churn and break file
watching.

```
JDK      openjdk-21            (apt; AGP 9.x needs 17+)
SDK      ~/Android/Sdk         (cmdline-tools/latest, platform-tools, emulator)
compile  platforms;android-36.1   ← must match compileSdk in app/build.gradle.kts
build    build-tools;36.1.0
image    system-images;android-36.1;google_apis;x86_64
AVD      nest_api36
```

`local.properties` is gitignored — set it per machine:

```properties
sdk.dir=/home/<user>/Android/Sdk
```

### KVM is mandatory, and `ls` lies about it

The emulator needs `/dev/kvm` **openable**, not present. The node usually exists as
`660 root:kvm`, so `ls -l /dev/kvm` looks fine while `open()` returns `EACCES` and the
emulator dies with a cryptic GPU/render error instead of a permissions one.

```bash
python3 -c "import os; os.open('/dev/kvm', os.O_RDWR); print('kvm ok')"
```

Fix: `sudo usermod -aG kvm $USER`, then **re-login** (a new `wsl -d Ubuntu-24.04` session
picks it up; the group change never applies to the live shell). From Windows without a
password prompt: `wsl -d Ubuntu-24.04 -u root -- usermod -aG kvm $USER`.

Verify it actually works before blaming the emulator:

```bash
emulator -accel-check     # must say KVM is installed and usable
```

### Probe without changing anything

```bash
scripts/android-env-check.sh    # prints KVM, JDK, SDK, AVD, disk, network reach
```

---

## 2. Build, then free memory, then boot

`gradle.properties` reserves a 6 GB Gradle heap plus a 3 GB Kotlin daemon. The WSL VM has
10 GB and the emulator wants ~2 GB. Build **first**, stop the daemons, **then** boot:

```bash
./gradlew :app:assembleDebug
./gradlew --stop
scripts/emulator-up.sh          # boots nest_api36 headless, waits for sys.boot_completed
```

If you need them concurrent, raise `memory=` in `C:\Users\<you>\.wslconfig` (14 GB is
comfortable) and `wsl --shutdown` to apply. Say which machine got which treatment.

### Boot flags are not optional

```bash
emulator -avd nest_api36 -no-window -gpu swiftshader_indirect \
         -no-audio -no-snapshot -no-boot-anim &
```

`-gpu swiftshader_indirect` is required on any host without GPU passthrough. Without it the
guest renders nothing and **every screenshot is black while adb reports the app healthy** —
the single most misleading failure mode in this loop. `-no-snapshot` stops a stale, possibly
corrupt snapshot from restoring a half-dead system.

---

## 3. Driving the app

### Get the tree, don't guess coordinates

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml -
```

This returns the **Compose semantics tree** — `text`, `content-desc`, `bounds`. Find the
node, take the bounds centre, tap it:

```bash
adb shell input tap <cx> <cy>
```

Rules that save time:

- `uiautomator dump` fails with **"could not get idle state"** while any animation runs.
  Wait for the transition and retry. It is not a broken app.
- Compose `IconButton`s frequently carry **no `text`** — match `content-desc` first, then
  `resource-id`, and fall back to position only as a last resort.
- The bottom nav in this app uses `NavigationBarItem` with `contentDescription` from
  `ui/navigation/BottomNavItem.kt`. Read that file before inventing selectors.
- After every tap: screenshot + logcat check. In `scripts/lib/drive.sh` these are wrapped as
  `tap_label`, `tap_desc`, `assert_text`, `snap`, `expect_clean_logcat`.

### Prefer the intent over the tap where an intent exists

`MainActivity` handles deep-link intents — these are more stable than pixel taps and worth
using to jump straight to a screen:

```bash
adb shell am start -n com.example.medianest.debug/com.example.medianest.MainActivity \
  -a com.example.medianest.ACTION_NAVIGATE_DOWNLOADS
adb shell am start -n com.example.medianest.debug/com.example.medianest.MainActivity \
  -a com.example.medianest.ACTION_NAVIGATE_UPDATES
```

Note the debug applicationId suffix: the debug variant installs as
**`com.example.medianest.debug`**, not `com.example.medianest`. Using the release id against
a debug build gives `Activity not found` and looks like a crash.

### Crash check is part of every step

```bash
adb logcat -d | grep -E "FATAL|AndroidRuntime" | tail -20
```

A tap that lands on a dying process is indistinguishable from success in the tap log.

---

## 4. Watch it, and leave a recording

Taps come from `adb shell input`, so **no mouse cursor moves on screen** — the user sees
buttons depress and screens change, nothing else. Say that before they ask.

```bash
scripts/watch.sh          # scrcpy window on WSLg, mirrors the headless emulator live
```

`scrcpy` draws through WSLg onto the Windows desktop. Sufficiently recent scrcpy supports
`--no-audio`; on the Ubuntu 24.04 package (1.25) use plain `scrcpy` and ignore audio
warnings.

Always record as well, because a live window is not evidence:

```bash
adb shell screenrecord --time-limit 180 /sdcard/run.mp4 &
# ... drive ...
adb pull /sdcard/run.mp4 artifacts/run.mp4
```

Attach `run.mp4` and the named screenshots. `screenrecord` caps at 180 s per file; chain
multiple segments for longer flows.

---

## 5. Encode what you drove

Once a flow works by hand, put it in `app/src/androidTest/` as Compose UI (`createAndroidComposeRule`
+ `onNodeWithText(...).performClick()`), and add it to `scripts/verify.sh`. The dependencies
are already declared (`androidx-compose-ui-test-junit4`, `espresso-core`, `androidx-junit`).

```bash
./gradlew :app:connectedDebugAndroidTest
```

This is what makes verification repeatable without an agent in the loop. Prefer it over
adding more one-off taps.

---

## Pitfalls

| Symptom | Cause |
| --- | --- |
| Screenshots black, app "running" | Missing `-gpu swiftshader_indirect` |
| Build fails at *configuration*, not compile | `platforms;android-<compileSdk>` not installed — install the exact id from the error |
| `Activity not found` | Used `com.example.medianest` against the debug build; it is `.debug` |
| Release build signing failure | `signingConfigs.release` reads `$user.home/.android/debug.keystore`; build `debug` for testing |
| `uiautomator dump` "could not get idle state" | Animation still running — wait and retry |
| Emulator dies instantly, permission-looking error | `/dev/kvm` not openable; check with the `python3 os.open` probe |
| Gradle daemon OOM alongside the emulator | `./gradlew --stop` before boot, or raise WSL `memory=` |
| `x86_64` image on an ARM host | Silently degrades to slow emulation; check `uname -m` |

---

## Reporting

State what was **driven**, not what was **built**:

```
Flow: bottom nav smoke
  ✅ launch        foreground=com.example.medianest.debug  screens/01-launch.png
  ✅ Home          "Home" heading present                 screens/02-home.png
  ✅ Library       tapped content-desc=Library             screens/03-library.png
  ✅ Downloads     tapped content-desc=Downloads           screens/04-downloads.png
  ✅ Subscriptions tapped content-desc=Subscriptions       screens/05-subscriptions.png
  ✅ Settings      tapped content-desc=Settings            screens/06-settings.png
  ✅ logcat        no FATAL
```

Anything you did not drive is reported as untested, never implied.
