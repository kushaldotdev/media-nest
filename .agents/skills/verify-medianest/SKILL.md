---
name: verify-medianest
description: "Drive the MediaNest Android app on a real emulator and prove a feature works — launch, doctor, tap through the UI, assert persisted state, capture screenshots and a recording. Use before reporting any MediaNest change as done, or when asked to test/verify/check/run the app."
---

# verify-medianest

The app under test is **MediaNest** (`com.example.medianest.debug`) — an offline-first Android
media hub. The surface a user touches is the Compose UI on an emulator: bottom nav
(`Home` / `Collections` / `Downloads` / `Settings`), a player, and a download pipeline.

This skill exists because "it compiled" and "unit tests pass" are not proof. Three scripts are
the whole harness — `scripts/nest.sh` (build/boot/install), `scripts/ad.sh` (drive via
`agent-device`), and `.agents/skills/verify-medianest/scripts/nest-state.sh` (read/write
persisted state). Do not hand-roll `adb` on top of them.

## Launch

```bash
scripts/android-env-check.sh                  # read-only: is this machine able to build+drive?
scripts/nest.sh up                            # build (if changed) + boot emulator (if needed) + install
scripts/ad.sh launch                          # open $NEST_PKG --foreground, returns initial @e* refs
```

`scripts/nest.sh up` is idempotent — a repeat run reuses the APK, the running emulator and the
installed package. `--force` rebuilds, `--uninstall` is not a `nest.sh` flag (pass it to
`scripts/dev/install.sh` if a signature mismatch blocks the install).

**Ready means usable, not "a process answered".** The gate is: `agent-device open` returned
interactive refs *and* the bottom navigation is in the tree.

```bash
scripts/ad.sh launch                       # launch returns the initial interactive snapshot
scripts/ad.sh wait text "Downloads"        # nav bar reachable = the app is actually drawn
```

**Teardown** (leave the emulator up for the next run unless you need the RAM):

```bash
scripts/nest.sh down                # stop the emulator
scripts/nest.sh clean               # stop it and wipe app/build + build + install marker
```

**Which emulator.** Detected, not chosen. Under WSL the AVD boots **on the Windows host** and is
driven over TCP adb: a guest emulator inside WSL2 cannot read its own framebuffer — `screencap`,
`screenrecord` **and** `uiautomator` all return nothing — so the app installs there but can never
be driven or verified. `scripts/lib/common.sh` detects WSL, finds the host adb server on the
default gateway and exports `ADB_SERVER_SOCKET`; `scripts/ad.sh` and `nest-state.sh` both inherit
it, so nothing else changes. `scripts/nest.sh status` prints which instance you got, and
`screencap works` / `screencap broken (headless WSL limitation)` is the verdict. **Never run the
emulator inside WSL** — `-gpu swiftshader*` and `-feature -ReadColorBufferDma` were both tried and
both fail.

**Fresh worktree, first run.** Git only tracks the executable bit when it was set, so
`scripts/ad.sh` can arrive non-executable: `chmod +x scripts/ad.sh` (or call it as
`bash scripts/ad.sh …`). Same for `.agents/skills/verify-medianest/scripts/nest-state.sh`.

## Doctor

Two read-only checks answer "is this instance worth driving?". Run `doctor` first whenever
anything looks off, and again after any failed drive:

```bash
scripts/android-env-check.sh                                  # host: KVM openable, JDK 21, SDK, AVD, APK age
scripts/nest.sh status                                        # device: booted / installed / pixels readable
.agents/skills/verify-medianest/scripts/nest-state.sh doctor  # app: installed, running, foreground, run-as, DB + DataStore
```

`nest-state.sh doctor` is the one that decides whether state assertions are possible at all: it
verifies `run-as "$NEST_PKG"` works (a release build refuses it), and that
`databases/media_nest.db` and `files/datastore/` exist.

The conditions that produce a misleading "healthy" app:

| Symptom | Real cause |
| --- | --- |
| App runs, screenshots black | guest has no readable framebuffer → run on the host (handled by `nest.sh up`) |
| `Activity not found` | used `com.example.medianest`; the debug id is `com.example.medianest.debug` |
| emulator dies instantly | `/dev/kvm` present but not **openable** — probe `python3 -c "import os; os.open('/dev/kvm', os.O_RDWR)"` |
| `run-as: package not debuggable` | release APK installed — reinstall the debug build |
| update download/install does nothing | `UpdateManager.downloadAndInstallUpdate()` / `installApk()` are `BuildConfig.DEBUG`-guarded: **debug builds** refuse with `"Updates are disabled in debug builds."` and never notify. The GitHub *check* still runs in debug, so `Check for Updates` can find a release you cannot install from this build |
| two agents fight over the screen | one emulator (`nest_hw`), one driver — **never run parallel agents against it** |

## Drive

Harness = `scripts/ad.sh`, a thin wrapper that pins `agent-device` to the booted serial:
`agent-device --platform android --serial "$SERIAL"`. Handles are **refs from the live snapshot**
(`@e12`, `@e12~s4`) or `key=value` selectors — never coordinates unless the tree is empty.

```bash
scripts/ad.sh launch                     # open the app, returns the initial @e* refs
scripts/ad.sh snapshot -i                # fresh interactive tree when refs went stale
scripts/ad.sh click @e24 --settle        # tap a ref; prints the resulting UI diff
scripts/ad.sh click 'label="Downloads"' --settle
scripts/ad.sh fill 'label="Search or paste URL..."' "https://youtu.be/…" --settle
scripts/ad.sh scroll bottom --settle     # or: scroll down 0.5 --settle (amount = viewport fraction, ≤0.8)
                                        # pixels: scroll down --pixels 400 --settle
scripts/ad.sh wait text "Active Queue"   # named expectation instead of a sleep
scripts/ad.sh is checked 'role=switch label="Always Show Full Titles"'
scripts/ad.sh back --settle              # back from a root destination exits to the launcher
scripts/ad.sh jump downloads             # deep link; also: jump updates
scripts/ad.sh screenshot artifacts/.../screens/01-home.png
scripts/ad.sh record start artifacts/.../run.mp4  ;  scripts/ad.sh record stop
scripts/ad.sh logs path                  # where agent-device keeps session app logs
scripts/ad.sh close                      # end the session
```

Selector rules that cost runs when ignored:

- `--settle` is for `click/fill/press/longpress/scroll/back` only — never `open`, `snapshot`, `close`.
- Refs go stale after any mutation. Continue from the diff that `--settle` printed; `snapshot -i`
  only when the diff lacks the next target.
- Text matches the **whole** field, case- and whitespace-insensitive: `label="sign in"` does not
  match `Sign In Now`. Use `find text "Sign" click` for partials.
- Compose `IconButton`s often carry no `text` — match `content-desc` (it surfaces as `label=`) first.
- Deep links beat taps where they exist: `MainActivity` handles
  `com.example.medianest.ACTION_NAVIGATE_DOWNLOADS` and `…ACTION_NAVIGATE_UPDATES` (`scripts/ad.sh jump`).

Real labels in this app (from source, not guessed): `Home`, `Downloads`, `Collections`,
`Settings`, `Search or paste URL...`, `History`, `Offline Media Hub`, `Active Queue`,
`Completed Downloads`, `Pause All`, `Resume All`, `All Videos`, `Create`, `New Folder`,
`Repair Library`, `Sync Now`, `Export Backup`, `Import Backup`, `Check for Updates`.

```bash
# raw deep link, if you need one that is not wired into ad.sh
. scripts/lib/common.sh
adb -s "$(nest_device_serial)" shell am start \
  --activity-single-top --activity-clear-top \
  -n com.example.medianest.debug/com.example.medianest.MainActivity \
  -a com.example.medianest.ACTION_NAVIGATE_DOWNLOADS
```

**Asserting state the UI cannot show.** Screens lie by omission: a queue row that never wrote a
download, a toggle that never reached disk. `.agents/skills/verify-medianest/scripts/nest-state.sh`
reads the same bytes the app reads.

```bash
S=.agents/skills/verify-medianest/scripts/nest-state.sh
$S db-count downloads "status='COMPLETED'"       # row count, 1=1 default
$S db-query "select id,status from downloads order by id desc limit 5"
$S pref-bool collections_prefs full_titles       # true/false, decoded from the protobuf
$S pref-dump download_prefs                      # every key=value in that store
$S pref-get  playback_prefs playback_speed
$S db-push-sql "update downloads set status='QUEUED' where id=3"   # force-stops the app first
```

Store files (`files/datastore/<name>.preferences_pb`): `collections_prefs`, `playback_prefs`,
`subscriptions_prefs`, `download_prefs`, `update_prefs`, `sync_prefs`. DataStore writes **lazily**:
a fresh install has no `.preferences_pb` (and no key inside an existing one) until something
calls `.edit`, so `pref-get`/`pref-bool` exit non-zero for a key that has never been changed.
That is a real state, not a bug — toggle the control in the UI first, or treat the missing file as
"default value in effect". `pref-get` exits `3` for an unset key, `4` for an undecodable store,
`1` when the store cannot be read at all. Room DB:
`databases/media_nest.db`, **WAL mode** — a bare `.db` without its `-wal` can be missing the last
commits, which is why `db-pull` copies `.db`, `-wal` and `-shm` together and `db-push-sql`
checkpoints then deletes the device-side `-wal`/`-shm` (a stale WAL replays old frames over the
pushed file).

## Evidence

Start a run by allocating its directory, then point every artifact at it:

```bash
ART=$(.agents/skills/verify-medianest/scripts/nest-state.sh evidence-dir settings-toggle)
# artifacts/verify-20260101-120000Z-settings-toggle/{screens,snapshots,db,logs}
```

| Artifact | Proves |
| --- | --- |
| `screens/*.png` | what the screen actually looked like, per driven step |
| `snapshots/*.txt` | the semantics tree at that step (`scripts/ad.sh snapshot -i`) |
| `db/*` | `nest-state.sh db-pull "$ART/db"` — the rows behind the screen |
| `logs/logcat-crashes.txt` | `nest-state.sh crash-check --out "$ART/logs/logcat-crashes.txt"` — written only when it finds a `FATAL EXCEPTION`, a fatal signal, or an `ANR`, with 25 lines of stack per hit. Its absence **is** the clean result |
| `run.mp4` | the whole flow (`scripts/ad.sh record start|stop`) |
| `RESULT.md` | PASS/FAIL per step + commit + artifact table |

**Write `RESULT.md` yourself** — there is no script that generates it any more.

Proof standards for this repo:

- Exercise the user path (tap the real control), not a test-only hook.
- Capture the **action and the result**, not just the final screen.
- Assert side effects — a file on disk under `/sdcard/Download`, a row in
  `databases/media_nest.db`, a preference that survives a relaunch — not only what is visible.
- A tap that exited `0` proves nothing. `scripts/ad.sh click` prints a diff; if the diff is empty
  and the next assertion reads the old screen, the tap was swallowed by a fling. Retry, do not
  re-report.
- End every run with the crash gate:

```bash
.agents/skills/verify-medianest/scripts/nest-state.sh crash-check --out "$ART/logs/logcat-crashes.txt"
# non-zero on FATAL EXCEPTION / fatal signal / ANR; pass = logcat clean
```

- Report an undriven area as **"not driven"**, never as working.

## Cleanup

```bash
scripts/ad.sh close                       # end the agent-device session (releases the device)
scripts/nest.sh down                      # stop the emulator this run booted
```

Never kill by process name, and never delete `artifacts/verify-*/` — that is the proof, and it
must survive teardown. Scratch state lives in `~/.cache/medianest/` and is safe to remove.
`db-push-sql` force-stops the app; relaunch it (`scripts/ad.sh launch`) before driving again.

## Helpers

| Script | Purpose |
| --- | --- |
| `scripts/nest.sh {up,status,down,clean}` | build + boot + install, idempotent |
| `scripts/ad.sh {launch,jump …}` / `scripts/ad.sh <agent-device args>` | drive the UI; auto-bridges WSL adb and pins the serial |
| `.agents/skills/verify-medianest/scripts/nest-state.sh {doctor,db-pull,db-query,db-count,db-push-sql,pref-dump,pref-get,pref-bool,logcat-clear,crash-check,evidence-dir}` | persisted state + crash gate + evidence dirs |
| `scripts/android-env-check.sh` | read-only readiness probe |
| `scripts/dev/build.sh [--force|clean]` | build only |
| `scripts/dev/emulator.sh {up,down,status}` | boot/stop the AVD (host AVD under WSL) |
| `scripts/dev/install.sh [--force|--uninstall]` | install only (push + `pm install`, not streamed) |
| `scripts/phone.sh` | real device over Wi-Fi or USB |
| `scripts/build.ps1` | Windows-side build (legacy, manual use only) |

Feature recipes live in [`features/`](./features/README.md) — read the index before driving, and
add a file there for any user-facing feature that does not have one yet.
