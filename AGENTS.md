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
filesystem remote. To snapshot history, push `origin`.

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

If asked to **test, verify, check, or run the app**, follow
[`.agents/TESTING_PROTOCOL.md`](.agents/TESTING_PROTOCOL.md) — full procedure in
[`docs/TESTING.md`](docs/TESTING.md). Repo skill: `.agents/skills/medianest-app-testing/SKILL.md`.

**The bar.** "Tested" means the app **launched on an emulator, a control was driven, the screen
changed, the screenshot was looked at, and logcat had no `FATAL`**. Not a pass:

- `./gradlew assembleDebug` succeeding — that is compiled, not tested
- green unit tests — logic only, no UI
- `adb shell input tap` exiting `0` — meaningless, it returns `0` against a crashed process too

**The loop.**

```bash
scripts/dev/host-emulator.sh up   # WSL only: emulator on the Windows host + TCP adb (see below)
scripts/nest.sh test              # build (if changed) + emulator (if needed) + install (if changed) + drive
```

`scripts/nest.sh test` is idempotent: the second run reuses the APK, the running emulator and
the installed package, so it costs ~75 s instead of minutes. `--force` rebuilds.

For an auditable run with a written report use `scripts/verify.sh` (build → unit tests →
emulator → install → flow → `artifacts/verify-<UTC>/RESULT.md`). Its exit code is the verdict.
Add flows as `scripts/flows/<name>.sh` exporting `flow_<name>()`, then
`scripts/nest.sh test <name>`.

**Do not run the emulator inside WSL.** Its framebuffer cannot be read back
(`Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`), so `screencap`,
`screenrecord` **and** `uiautomator` all return nothing — the app can be installed there but
never driven or verified. `-gpu swiftshader`/`swiftshader_indirect`/`off` and
`-feature -ReadColorBufferDma` were all tried and all fail. Run the emulator on the host OS
(`scripts/dev/host-emulator.sh up`); `scripts/lib/common.sh` detects WSL, finds the host adb
server on the default gateway and exports `ADB_SERVER_SOCKET`, so every other script works
unchanged.

**A real device** works the same way — `scripts/phone.sh` (Wi-Fi pairing, USB routes,
`install`, `test`). Wi-Fi reaches the LAN from WSL2 directly; USB either lets Windows own the
phone while WSL borrows its adb server, or needs `usbipd-win`.

**Driving primitives** — `. scripts/lib/drive.sh`, then:

| Helper | Does |
| --- | --- |
| `launch` | force-stop + start, asserts the app reached the foreground |
| `tap_label "Downloads"` | dumps the Compose semantics tree, taps the node's centre, retries through animations |
| `assert_text "Library"` | fails unless that string is in the live tree |
| `snap "02-home"` | screenshot into `$ART/screens/`, detects blank frames |
| `dump_ui` | raw `uiautomator` XML into `$ART/ui/` |
| `ck_logcat` | fails on `FATAL EXCEPTION` / `ANR in <pkg>` |
| `start_recording` / `stop_recording` | `screenrecord` → `$ART/run.mp4` |

**Evidence.** `artifacts/verify-<UTC>/` — `RESULT.md`, `screens/*.png`, `ui/*.xml`, `run.mp4`,
`logcat.txt`, `logcat-crashes.txt`. Attach the screenshots and recording; never summarise from
memory. Live view while driving: `scripts/watch.sh` (scrcpy via WSLg). Taps come from
`adb shell input`, so **no cursor is drawn** — the UI reacts, the pointer does not move.

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

## Build & Run Commands

Windows environment does not have standard `JAVA_HOME` in path. Use bundled build scripts:

- **Build Debug**: `& "D:\dev\media-nest\build-debug.bat"` (or `build-debug.bat clean` for a clean build)
- **Build Release**: `& "D:\dev\media-nest\build-release.bat"` (or `build-release.bat clean` for a clean build)

These scripts set `JAVA_HOME` to Android Studio's JBR (`C:\Program Files\Android\Android Studio\jbr`) and execute `gradlew.bat`.
Do not run `./gradlew` or `gradlew` directly unless `JAVA_HOME` is manually configured.

### Running the build from WSL

Build **natively in WSL**. Do not shell out to `cmd.exe`/`gradlew.bat`, and do not build from
`/mnt/d` — it is ~126x slower for Gradle's file I/O.

```bash
scripts/dev/build.sh            # incremental, skips Gradle when sources are unchanged
scripts/dev/build.sh --force    # always run Gradle
scripts/dev/build.sh clean      # clean build
```

Requires `JAVA_HOME` = JDK 21 (installed at `/usr/lib/jvm/java-21-openjdk-amd64`) and
`local.properties` pointing at `sdk.dir=/home/kushal/Android/Sdk`; `build.sh` sets both.

The `build-debug.bat` / `build-release.bat` / `publish-to-github.ps1` scripts are the **legacy
Windows path** (they expect Android Studio's JBR). They still exist but are not the workflow
here — do not use them unless explicitly asked.

### Build logs

Each workflow writes its **own** log file at the repo root (all git-ignored):

| Workflow | Log file |
| --- | --- |
| Debug build | `D:\dev\media-nest\build-debug.log` |
| Release build | `D:\dev\media-nest\build-release.log` |
| Publish to GitHub | `D:\dev\media-nest\build-publish.log` |

Every log has a timestamped header (start time), per-line elapsed timers (`[+mm:ss]`), and a footer with end time, total duration, and exit code. Output is streamed **live** to the terminal and to the log simultaneously.

On failure the script prints the exit code and exits non-zero. The full log is always saved to the per-workflow file above (also echoed live to the terminal).
When a build fails, **read the log file** to diagnose the actual error instead of relying on the truncated console tail — from WSL: `tail -n 100 D:/dev/media-nest/build-release.log` or `grep -n "error:\|e: \|FAILED" D:/dev/media-nest/build-release.log`.
Note: the logs are written to the repo root and are git-ignored (see `.gitignore`).
