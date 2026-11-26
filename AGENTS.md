# MediaNest — agent instructions

## Where the code lives (one copy only)

The working tree is **only** in WSL ext4:

```
WSL:     ~/projects/media-nest
Windows: \\wsl.localhost\Ubuntu-24.04\home\kushal\projects\media-nest
```

Open that UNC path in VS Code / Cursor / Explorer (Remote-WSL).

**GitHub is the only remote and the only backup.** There is one working tree (WSL ext4) and one
copy of the history (`origin` → `github.com/kushaldotdev/media-nest`). No bare repo on `D:`, no
`backup` remote, no second Windows tree — never sync with `cp`/`rsync`, and never re-add a
filesystem remote.

**Do not push on your own initiative.** The operator normally publishes to GitHub manually.
Pushing is allowed only when the request explicitly says to push ("push everything", "push now");
silence is not permission, and neither is a subagent brief that says to "land the fix" — a child
must commit and stop. Default behaviour: commit locally, leave the tree clean, report the commit.
When a push IS requested and it has to rewrite published history (rebasing out a commit), use
`git push --force-with-lease`, never `--force`.

**Do not copy the tree to `D:\` and build there.** Measured on this machine, from WSL, over
the same 224 source files:

| Operation | `/mnt/d` (9p) | ext4 | Factor |
| --- | --- | --- | --- |
| read every file | 2021 ms | 16 ms | 126x |
| create 300 small files | 2346 ms | 32 ms | 73x |
| stat every file | 391 ms | 7 ms | 56x |

A second Windows copy also silently caused real bugs: `rsync -a` reported hundreds of phantom
"changed" files (mode-only), and `cp` bumping a mtime defeated the build fast path. If a
Windows-side copy is ever needed, make it a **git peer**, not a file copy.

# Codebase Knowledge Graph (codebase-memory-mcp)

This project uses codebase-memory-mcp to maintain a knowledge graph of the codebase.
ALWAYS prefer MCP graph tools over grep/glob/file-search for code discovery.

## Priority Order

1. `search_graph` — find functions, classes, routes, variables by pattern
2. `trace_path` — trace who calls a function or what it calls
3. `get_code_snippet` — read specific function/class source code
4. `query_graph` — run Cypher queries for complex patterns
5. `get_architecture` — high-level project summary

## When to fall back to grep/glob

- Searching for string literals, error messages, config values
- Searching non-code files (Dockerfiles, shell scripts, configs)
- When MCP tools return insufficient results

## Examples

- Find a handler: `search_graph(name_pattern=".*OrderHandler.*")`
- Who calls it: `trace_path(function_name="OrderHandler", direction="inbound")`
- Read source: `get_code_snippet(qualified_name="pkg/orders.OrderHandler")`

## Testing the app (read before claiming it works)

If asked to **test, verify, check, or run the app**, follow [`docs/TESTING.md`](docs/TESTING.md),
[`.agents/skills/medianest-app-testing/SKILL.md`](.agents/skills/medianest-app-testing/SKILL.md),
and — for a real feature proof — [`verify-medianest`](.agents/skills/verify-medianest/SKILL.md):

- **The loop** lives in [`.agents/skills/verify-medianest/SKILL.md`](.agents/skills/verify-medianest/SKILL.md)
  (Launch → Doctor → Drive → Evidence → Cleanup).
- **What to drive** lives in [`.agents/skills/verify-medianest/features/README.md`](.agents/skills/verify-medianest/features/README.md).
  Look up the affected feature and read its `.md` *before* tapping; a feature with no file there is
  **not driven**.
- **Persisted state** is checked with
  [`.agents/skills/verify-medianest/scripts/nest-state.sh`](.agents/skills/verify-medianest/scripts/nest-state.sh)
  (`db-count`, `db-query`, `db-push-sql`, `pref-bool`, `pref-dump`, `crash-check`). A screen that
  re-rendered is not a value that reached disk — pair every toggle with `pref-bool` and every row
  with `db-count`.

**The bar.** "Tested" means the app **launched on an emulator, a control was driven, the screen
changed, the screenshot was looked at, and logcat had no `FATAL`**. Not a pass:

- `./gradlew assembleDebug` succeeding — that is compiled, not tested
- green unit tests — logic only, no UI
- `adb shell input tap` exiting `0` — meaningless, it returns `0` against a crashed process too

**The loop.**

```bash
scripts/nest.sh up                # build (if changed) + boot Windows host emulator + install APK
scripts/ad.sh launch              # open com.example.medianest.debug via agent-device (auto-bridges ADB)
```

`scripts/nest.sh up` is idempotent: repeat runs reuse the APK, the running emulator, and the
installed package. `--force` rebuilds. Drive and verify UI screens using **`scripts/ad.sh`**
(`click @e* --settle`, `snapshot -i`, `wait text "..."`, `is checked`, `screenshot`, `logs`,
`close`), and check `adb logcat` for `FATAL EXCEPTION` / `ANR in com.example.medianest.debug`.

`scripts/ad.sh` can arrive **non-executable** in a fresh worktree (git only tracks the exec bit when
it was set): `chmod +x scripts/ad.sh`, or call it as `bash scripts/ad.sh …`. Same for
`.agents/skills/verify-medianest/scripts/nest-state.sh`.

**One emulator, one driver.** `nest_hw` is a single AVD with a single `agent-device` session. Never
run two agents against it in parallel — they fight over the screen and both runs are invalid.
Serialize the work, or give each lane its own device.

**Do not run the emulator inside WSL.** Its framebuffer cannot be read back
(`Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`), so `screencap`,
`screenrecord` **and** `uiautomator` all return nothing — the app can be installed there but
never driven or verified. `-gpu swiftshader`/`swiftshader_indirect`/`off` and
`-feature -ReadColorBufferDma` were all tried and all fail. Run the emulator on the host OS
(`scripts/dev/host-emulator.sh up`); `scripts/lib/common.sh` detects WSL, finds the host adb
server on the default gateway and exports `ADB_SERVER_SOCKET`, so every other script works
unchanged.

**A real device** works the same way — `scripts/phone.sh` (Wi-Fi pairing, USB routes,
`install`). Wi-Fi reaches the LAN from WSL2 directly; USB either lets Windows own the
phone while WSL borrows its adb server, or needs `usbipd-win`.

**Driving (`scripts/ad.sh` / `agent-device`)** — see `.agents/skills/medianest-app-testing/SKILL.md`:

| Command | Does |
| --- | --- |
| `scripts/ad.sh launch` | launches app via `agent-device` and returns initial `@e*` refs |
| `scripts/ad.sh jump downloads\|updates` | deep-links straight to Downloads or Updates |
| `scripts/ad.sh click @e24 --settle` | taps element, waits for UI settle, prints diff |
| `scripts/ad.sh snapshot -i` | captures interactive UI tree with fresh `@e*` refs |
| `scripts/ad.sh wait text "Downloads"` | waits for expected text without fixed sleeps |
| `scripts/ad.sh screenshot <path>` | captures screenshot evidence |
| `scripts/ad.sh close` | closes the driving session |

**Live view.** The Windows host emulator window (`Android Emulator - nest_hw:5554`) opens
directly on your Windows desktop so you can watch and interact with the app in real time.

**Environment facts that waste hours if rediscovered:**

- The debug package id is **`com.example.medianest.debug`** (`applicationIdSuffix`). Using
  `com.example.medianest` gives `Activity not found`, which reads like a crash but is a typo.
- Build inside WSL on **ext4** (`~/projects/media-nest`), not `/mnt/d`. 9p mounts are several
  times slower for Gradle's file churn and break file watching.
- `/dev/kvm` must be **openable**, not merely present — it shows as `660 root:kvm` either way.
  Probe: `python3 -c "import os; os.open('/dev/kvm', os.O_RDWR)"`. Fix:
  `sudo usermod -aG kvm $USER` then re-login (from Windows without a password prompt:
  `wsl -d Ubuntu-24.04 -u root -- usermod -aG kvm $USER`).
- `-gpu swiftshader_indirect` is required on a host with no GPU passthrough. Without it the
  guest renders nothing and screenshots are black **while adb reports the app healthy**. Do not
  *also* set `hw.gpu.enabled=no` in `config.ini` — the two conflict and kill the display stack,
  which makes `screencap` emit a 72-byte file and `monkey` fail with a null `DisplayManager`.
- `uiautomator dump` returns "could not get idle state" while an animation runs. Wait, retry;
  it is not a broken app.
- Compose `IconButton`s often carry no `text`; match `content-desc` first.
- A large APK (~196 MB here, 103 MB of `.so`) fails `adb install` with
  `Failure calling service package: Broken pipe`. Use `adb push` then
  `adb shell pm install -r -t /data/local/tmp/app.apk`.
- Test with the `debug` build; `signingConfigs.release` reads `$user.home/.android/debug.keystore`.
- An emulator launched with a bare `nohup ... &` is killed when the WSL session exits. Use
  `setsid nohup ... < /dev/null &` so it survives.

## Parallel work: worktrees in, worktrees out

Every parallel or delegated change runs in its own `git worktree`, never in this checkout.
The lane is not finished when the code compiles — it is finished when the change is committed on
local `main`, the worktree is gone, and the branch is deleted. Publication is the operator's
separate step unless the request asks for it.

**Get the change onto `main` — staging is not landing.**

`git apply --index`, `git add`, and a subagent "apply patch" step only write to the index.
`main` does not move until you commit. A dirty index with `HEAD` still on the old commit is the
usual reason a fix "does not show up". Run this before claiming anything landed:

```bash
git log --oneline -1            # must be the fix commit, not the previous one
git status --porcelain           # must be empty
git merge-base --is-ancestor <lane-branch> main && echo "lane work is on main"
```

Use `--is-ancestor`, **not** `git diff <lane-branch> main`. A subagent branch whose patch you
applied by hand still points at its original base, so `git diff` reports your own commit as
"missing from the lane" and looks like the lane has unsaved work. Ancestry answers the real
question: does `main` contain everything the lane had?

**Then clean up — untracked worktrees leak Gradle caches and disk.**

```bash
wtd <branch|dir>                        # normal path: removes worktree + drops app_wt_<slug>
git worktree remove --force --force <dir>   # when wtd refuses: branch checked out / staged changes
```

`wtd` cannot remove a worktree whose branch is checked out there and which holds staged
changes — it exits without doing anything and leaves the directory in place. In that situation
the two commands above are the working pair. Then:

```bash
git worktree prune
git branch -D <lane-branch>       # -D, not -d: lanes are never squash-merged
rmdir <parent> 2>/dev/null        # leave ~/projects/worktrees/media-nest/ empty, not littered
```

Verify afterwards: `git worktree list` shows only this checkout, and
`ls ~/projects/worktrees/media-nest/` is empty. Do not leave orphan `app_wt_*` databases either
— if the worktree had an `.env`, `wtd` drops its database; if you removed the directory by
hand, drop the database yourself.

Worktrees created by pi-subagents (`isolation: worktree`) live at
`~/projects/worktrees/media-nest/pi-worktree-<runid>-s0-0` on branch
`pi-subagents/<key>-<runid>-s0-t0`. They are **not** registered with `wt`, so `wtd` will not
find them by branch — pass the directory, or use `git worktree remove` directly.

## Codebase Map

| Area | Path | Key files / responsibility |
| --- | --- | --- |
| App entry | `app/src/main/java/com/example/medianest/` | `MainActivity.kt` (deep links, intent routing), `MediaNestApp.kt` |
| Navigation & shell | `.../ui/navigation/`, `.../ui/` | `AppNavigation.kt`, `BottomNavItem.kt`, `MainScreen.kt` |
| Screens | `.../ui/screens/` | `HomeScreen.kt`, `DownloadsScreen.kt`, `LibraryScreen.kt`, `SettingsScreen.kt`, `PlayerScreen.kt`, `VideoDetailScreen.kt`, `SubscriptionsScreen.kt`, `StatisticsScreen.kt`, `NotificationsScreen.kt` |
| ViewModels | `.../ui/viewmodel/` | `HomeViewModel.kt`, `DownloadsViewModel.kt`, `LibraryViewModel.kt`, `PlayerViewModel.kt`, `VideoDetailViewModel.kt`, `SubscriptionsViewModel.kt`, `StatisticsViewModel.kt`, `NotificationsViewModel.kt`, `ExportImportViewModel.kt` |
| UI components & theme | `.../ui/components/`, `.../ui/theme/` | `UnifiedVideoCard.kt`, `MediaNestBottomNavigation.kt`, `Color.kt`, `Theme.kt`, `Type.kt` |
| Data, DB & sync | `.../data/` | `local/AppDatabase.kt`, `repository/*`, `preferences/*`, `backup/*`, `sync/*` |
| Extraction & services | `.../extraction/`, `.../service/` | `YouTubeExtractor.kt`, `DownloaderProvider.kt`, `DownloadService.kt`, `PlaybackService.kt`, `AudioExtractor.kt` |
| Background & updates | `.../worker/`, `.../updates/`, `.../di/` | `WorkScheduler.kt`, `SyncWorker.kt`, `UpdateManager.kt`, Hilt `*Module.kt` |
| Sync server | `sync-server/app/` | `main.py`, `database.py`, `models.py`, `schemas.py`, `routes/{devices,health,sync}.py` |

## Build & Run Commands

Build **natively in WSL** on ext4 (`~/projects/media-nest`). Do not shell out to
`cmd.exe`/`gradlew.bat`, and do not build from `/mnt/d` (~126x slower for Gradle's file I/O).

```bash
scripts/dev/build.sh            # incremental debug APK (skips Gradle when sources are unchanged)
scripts/dev/build.sh --force    # always run Gradle
scripts/dev/build.sh clean      # clean then build
scripts/dev/fast-test.sh <Test> # incremental unit test loop
```

Requires `JAVA_HOME` = JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`) and `local.properties`
pointing at `sdk.dir=/home/kushal/Android/Sdk`; `build.sh` sets both and writes logs to
`~/.cache/medianest/build.log`.

*(Legacy Windows scripts `build-debug.bat`, `build-release.bat`, `publish-to-github.bat`, and
`publish-to-github.ps1` remain at the repo root for manual Windows use only; do not use them
for WSL dev/test loops unless explicitly asked.)*
