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

## Per-feature verification (`verify-medianest`)

`scripts/nest.sh` + `scripts/ad.sh` prove you can drive the app. They do **not** tell you what to
drive, and a tap that painted is not a feature that works. That is what
[`.agents/skills/verify-medianest/`](../verify-medianest/SKILL.md) adds: a maintained map of
user-facing features, one recipe each, plus a helper that reads the bytes behind the screen.

Follow this order:

1. **Start with the skill.** Read
   [`.agents/skills/verify-medianest/SKILL.md`](../verify-medianest/SKILL.md) — it defines the loop
   (Launch → Doctor → Drive → Evidence → Cleanup), the proof bar, and the one-emulator rule.
   `scripts/nest.sh up` is Launch, `scripts/nest.sh status` + `nest-state.sh doctor` are Doctor.
2. **Look up the feature before touching the UI.**
   [`.agents/skills/verify-medianest/features/README.md`](../verify-medianest/features/README.md)
   indexes every mapped feature (`app-shell`, `search-and-intake`, `download-queue`,
   `library-folders`, `subscriptions`, `statistics`, `notifications`, `video-detail`,
   `playback-and-player`, `settings-maintenance`). Read the matching `.md` — it lists the real
   entry points, the exact labels to tap, and the gotchas that otherwise cost an hour. A feature
   with no file there is **not driven**, not "probably fine".
3. **Pair UI actions with persisted state.**
   `.agents/skills/verify-medianest/scripts/nest-state.sh` reads the same Room DB and DataStore the
   app reads, through `run-as`:

   ```bash
   S=.agents/skills/verify-medianest/scripts/nest-state.sh
   $S doctor                                        # run-as + DB + DataStore reachable
   $S db-count downloads "status='COMPLETED'"       # the row behind the list
   $S db-query "select id,title,status from downloads order by id desc limit 5"
   $S pref-bool collections_prefs full_titles       # a toggle that reached disk, not just paint
   $S pref-dump subscriptions_prefs                 # every key in a store
   $S crash-check                                   # non-zero on FATAL / ANR
   ```

   A toggle assertion needs both halves: `scripts/ad.sh is checked 'role=switch label="X"'` **and**
   `$S pref-bool <store> <key>`. A screen that re-rendered is not a value that persisted.
4. **Look at the screenshots, then clean up.** Open the images with your file-reading tool — a
   passing `snapshot -i` with a black or wrong-screen PNG is a failed run. Allocate evidence with
   `ART=$($S evidence-dir <label>)` (`artifacts/verify-<UTC>-<label>/{screens,snapshots,db,logs}`),
   finish with `$S crash-check`, restore any state you changed (a flipped switch breaks the next
   run), and `scripts/ad.sh close`. **Never delete `artifacts/verify-*`** — that is the proof.

Those four steps are the difference between "I tapped it" and "it works". Report per-step
PASS/FAIL against the feature file, with the screenshot and the state assertion side by side.

## Rules that cost time when ignored

- **Debug package id is `com.example.medianest.debug`.** Using `com.example.medianest` gives
  `Activity not found`, which looks like a crash but is a typo.
- **`/dev/kvm` must be openable, not present.** It shows as `660 root:kvm` either way; probe
  with `python3 -c "import os; os.open('/dev/kvm', os.O_RDWR)"`. Fix is
  `sudo usermod -aG kvm $USER` + re-login.
- **Build on ext4, not `/mnt/d`.** 9p mounts are several× slower and break file watching.
- **Test with `debug`.** Release signing reads `$user.home/.android/debug.keystore`.
- **`scripts/ad.sh` may arrive non-executable in a fresh worktree.** Git only tracks the exec bit
  when it was set, so run `chmod +x scripts/ad.sh` once, or call it as `bash scripts/ad.sh …`.
  Same for `.agents/skills/verify-medianest/scripts/nest-state.sh`.
- **One emulator, one driver.** `nest_hw` is a single device with a single `agent-device` session;
  two agents driving it in parallel fight over the screen and both runs are invalid. Serialize.
- **Deep-link intents beat pixel taps** for jumping to a screen: `scripts/ad.sh jump downloads` /
  `scripts/ad.sh jump updates` (raw form, if you need an action `ad.sh` does not wrap:
  `adb shell am start -n com.example.medianest.debug/com.example.medianest.MainActivity -a com.example.medianest.ACTION_NAVIGATE_DOWNLOADS`).

## Reporting

Report per-step PASS/FAIL (with observed UI diffs/screenshots and clean logcat), keyed to the
feature file you followed (`features/<name>.md`) and the entry point you used.
Anything not driven is reported as **not driven** — never implied as working. A UI step with no
paired state assertion (`nest-state.sh db-*` / `pref-*`) is a repaint, not a proof.
