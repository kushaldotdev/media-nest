# Testing MediaNest by Actually Driving the App

This file is the contract for **any agent** (pi, Claude Code, Codex, opencode, Cursor, or a
human) working in this repo. It is not documentation about testing — it is the procedure you
are expected to follow.

"Tested" in this repo has exactly one meaning:

> The app launched on a real Android emulator or device, real controls were driven, the screen
> changed, the result was verified (snapshot/screenshot), and logcat had no `FATAL`.

A green `assembleDebug`, a passing `testDebugUnitTest`, or a series of `adb shell input tap`
calls that returned exit code 0 **do not meet that bar**. `adb shell input tap` returns 0
against a crashed app. Only verified UI state/screenshots and logcat settle it.

---

## 0. The loop

```bash
scripts/nest.sh up               # build (if changed) + boot emulator + install APK
scripts/ad.sh launch             # open com.example.medianest.debug via agent-device (auto-bridges ADB)
```

**Which emulator.** Under WSL, `scripts/dev/emulator.sh up` boots the AVD **on the Windows
host** (`scripts/dev/host-emulator.sh up`) and drives it over TCP adb (`ADB_SERVER_SOCKET`),
because the guest cannot read its own framebuffer — `screencap`, `screenrecord` and
`uiautomator` all return nothing there. On a Linux host or a physical device the same command
boots locally. `scripts/nest.sh status` prints which one you got.

**What to drive, and what proves it.** Compose UI tests and `agent-device` runs are only as good as
the recipe. Every mapped feature lives in
[`../.agents/skills/verify-medianest/features/`](../.agents/skills/verify-medianest/features/README.md)
— read its `.md` before tapping, and pair the UI step with a state assertion (next section).

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
scripts/dev/emulator.sh up      # boots on Windows host in WSL, or nest_api36 locally on Linux
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

## 3. Driving the app (`scripts/ad.sh` / `agent-device`)

### Drive with interactive `@refs`, don't guess coordinates

```bash
scripts/ad.sh launch                   # auto-bridges ADB_SERVER_SOCKET + targets booted serial
scripts/ad.sh click @e24 --settle
scripts/ad.sh snapshot -i
scripts/ad.sh wait text "Downloads"
scripts/ad.sh screenshot /tmp/screen.png
scripts/ad.sh close
```

Rules that save time:

- Always keep `@e*` refs fresh: after any `--settle` mutation that changes the screen, use the
  newly issued `@e*~s...` refs from the diff or run `scripts/ad.sh snapshot -i`.
- If a system surface covers the app (e.g., `"System UI isn't responding"` or permission
  prompt), run `scripts/ad.sh alert dismiss` (or tap `Allow`/`Wait`).
- The bottom nav in this app is `MediaNestBottomNavigation` driven by
  `MediaNestBottomNavDefaults.Destinations` (`ui/components/MediaNestBottomNavigation.kt`), which
  lists `Home`, `Collections`, `Downloads`, `Settings` with their `contentDescription`s. Read that
  file (and `ui/navigation/BottomNavItem.kt` for routes) before inventing selectors.

### Prefer the intent over the tap where an intent exists

`MainActivity` handles deep-link intents — these are more stable than pixel taps and worth
using to jump straight to a screen:

```bash
scripts/ad.sh jump downloads
scripts/ad.sh jump updates
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

## 3b. Per-feature map and persisted state (`verify-medianest`)

Driving a screen proves it renders. It does not prove the app stored anything. Two things close that
gap, both under [`.agents/skills/verify-medianest/`](../.agents/skills/verify-medianest/SKILL.md):

| Thing | Where | Answers |
| --- | --- | --- |
| Feature map | [`features/README.md`](../.agents/skills/verify-medianest/features/README.md) | Which controls exist, how a user reaches them, what to assert |
| State helper | [`scripts/nest-state.sh`](../.agents/skills/verify-medianest/scripts/nest-state.sh) | Did the toggle reach DataStore, did the row reach Room |

The helper reads and writes exactly what the app reads — `databases/media_nest.db` (WAL, so `.db`
+ `-wal` + `-shm` together) and `files/datastore/*.preferences_pb` — via `run-as`, which is why a
**debug** build is mandatory:

```bash
S=.agents/skills/verify-medianest/scripts/nest-state.sh
$S doctor                                      # run-as + DB + DataStore reachable
$S db-count downloads "status='COMPLETED'"     # the row behind the list
$S db-query "select id,title,status from downloads order by id desc limit 5"
$S pref-bool collections_prefs full_titles     # a toggle that actually persisted
$S pref-dump sync_prefs                        # every key in a store
$S db-push-sql "delete from folders where name like 'Verify-%'"   # force-stops the app
$S crash-check                                 # non-zero on FATAL EXCEPTION / ANR
```

Rules that matter:

- `db-push-sql` **force-stops the app** (a live SQLite connection owns the file); relaunch with
  `scripts/ad.sh launch` before driving again.
- Room stores booleans and enums as text/int — `status='COMPLETED'`, `favorite=1`, `isRead=0`.
  `=true` silently matches nothing.
- DataStore files/keys are created lazily on the first `.edit`, so `pref-get`/`pref-bool` exit `3`
  for a key that was never written. That means *default*, not broken — read the default from the
  Kotlin file.
- Restore any state a run changed (a flipped switch makes the next run pass for the wrong reason),
  and never delete `artifacts/verify-*` — that is the evidence.

Start a run with `ART=$($S evidence-dir <label>)` and write `RESULT.md` yourself: PASS/FAIL per step,
with the screenshot next to the state assertion that backs it.

---

## 4. Watch it, and leave evidence

With `scripts/dev/host-emulator.sh up`, the **Windows host emulator window**
(`Android Emulator - nest_hw:5554`) opens directly on your desktop so you can watch the UI
react live as `agent-device` drives it.

You can also capture screenshots or video when needed:

```bash
scripts/ad.sh screenshot /tmp/step.png      # preferred: pinned serial + WSL adb bridge
# or record the whole flow
scripts/ad.sh record start artifacts/verify-<UTC>/run.mp4
scripts/ad.sh record stop
```

---

## 5. Automated regression tests

Once a flow works interactively via `agent-device`, put it in `app/src/androidTest/` as
Compose UI (`createAndroidComposeRule` + `onNodeWithText(...).performClick()`):

```bash
./gradlew :app:connectedDebugAndroidTest
```

This is what makes verification repeatable without an agent in the loop. Prefer it over
maintaining custom shell driving scripts.

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
  ✅ Collections   tapped content-desc=Collections         screens/03-collections.png
  ✅ Downloads     tapped content-desc=Downloads           screens/04-downloads.png
  ✅ Settings      tapped content-desc=Settings            screens/05-settings.png
  ✅ state         nest-state.sh crash-check clean, pref-bool read back
  ✅ logcat        no FATAL
```

The four bottom-nav destinations are `Home`, `Collections`, `Downloads`, `Settings` — there is no
`Library` and no `Subscriptions` destination. The library lives in `Collections` (pills `History`,
`Watched`, `Folders`, `Favorites`, `Playlists`, `Channels`), and `Subscriptions`, `Statistics` and
`Notifications` are reached from inside it (`Playlists` / `Channels` pills, top-bar `Statistics` and
bell icons) or from `Settings`.

Anything you did not drive is reported as untested, never implied.
