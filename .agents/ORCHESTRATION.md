# Agent Orchestration Playbook

> **Purpose:** a reusable, task-agnostic playbook for running multi-agent work in
> this repo. The **root/orchestrator** agent delegates all real work to
> **subagents** (`worker` / `researcher` / `scout`), reviews their output, and
> merges + commits. Subagents do the hard work in **isolated git worktrees** and
> verify with a **build** before handing back.
>
> This file is about *how to run* the workflow — not about any specific feature
> plan. Read it once per session so we never re-derive the workflow.

---

## 1. Roles (strict separation)

| Role | Agent | Responsibilities |
| --- | --- | --- |
| **Orchestrator (root)** | you | Plan phases, split into tasks, create worktrees, spawn subagents, review diffs, merge/squash/commit, run final builds. **Never writes feature code directly.** |
| **Worker** | `worker` | Implements one task inside one worktree. Runs its own build. Reports files + `BUILD SUCCESSFUL`. |
| **Researcher** | `researcher` | Read-only investigation: locate files, read specs, answer "where is X / how does X work". No edits. |
| **Scout** | `scout` | Lightweight discovery before committing to a plan. |
| **Reviewer** | `reviewer` | Read-only audit of the merged `git diff` for a phase before it is committed. Its allowlist is `read, grep, find, ls` — it *cannot* edit, so there is no "no edits" acceptance warning to suppress. |

Rules:

- Partition by **disjoint concerns**, not disjoint files. Multiple workers may touch
  the same file when their edits are in **different regions** (different
  functions/composables) — git merge auto-resolves non-overlapping hunks.
- Keep a **single-writer** rule only for **signature/interface changes** (data
  classes, DAO methods, function/composable parameters, public contracts) that
  ripple to call sites. Two workers editing the same call-site chain is the real
  source of merge-time breakage — not file identity.
- Orchestrator is the **only** actor that runs `git add/commit/merge/rebase` and deletes branches/worktrees.

---

## 2. Worktree isolation (WSL → Windows)

**Critical blocker:** the subagent runtime's auto-worktrees are created under WSL
`os.tmpdir()` (`/tmp/...`). Windows `cmd.exe`/Gradle **cannot reach `/tmp`**.
Therefore worktrees MUST live on the Windows-mounted drive.

### Create a worktree (manual, correct recipe)

```bash
# repo root
mkdir -p /mnt/d/dev/media-nest-worktrees
git worktree add /mnt/d/dev/media-nest-worktrees/<name> HEAD -b <branch>
cp local.properties /mnt/d/dev/media-nest-worktrees/<name>/   # gitignored, contains SDK path
git worktree list
```

Conventions:

- Namespace branches per phase/task: `p1a`, `p2b`, `p3c`, etc.
- Always `cp local.properties` into the worktree (it is gitignored and carries
  `ANDROID_HOME` / SDK path needed by Gradle).

### Clean up a worktree

```bash
git worktree remove /mnt/d/dev/media-nest-worktrees/<name> --force
git branch -D <branch>
# after all removed, optionally:
git worktree prune
rm -rf /mnt/d/dev/media-nest-worktrees   # only when the dir is empty
git worktree list                         # verify
```

---

## 3. Build verification recipes (WSL → Windows)

The Windows environment has no standard `JAVA_HOME`; use the bundled `.bat` scripts
(`build-debug.bat` / `build-release.bat`), which are **worktree-relative** (they use
`%~dp0`) and set `JAVA_HOME` to Android Studio's JBR.

### Full build (per-worktree, worktree-relative)

```bash
cd /mnt/d/dev/media-nest-worktrees/<name>
/mnt/c/Windows/System32/cmd.exe /c build-debug.bat -nopause
```

### Compile-only gate (fast, ~16s, minimal memory)

```bash
cd /mnt/d/dev/media-nest-worktrees/<name>
/mnt/c/Windows/System32/cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat --no-daemon :app:compileDebugKotlin --console=plain"
```

- **`JAVA_HOME` MUST use backslashes.** Forward slashes fail.
- `cmd.exe` is at `/mnt/c/Windows/System32/cmd.exe` (add that dir to PATH or use the full path). `powershell.exe` is usually NOT on the WSL PATH.
- Warm-daemon full build ≈ 3–5s; cold ≈ 4–5 min. Compile-only ≈ 16s.

### On failure

Read the log instead of the truncated console tail:

```bash
tail -n 120 /mnt/d/dev/media-nest-worktrees/<name>/build.log
grep -n "error:\|e: \|FAILED" /mnt/d/dev/media-nest-worktrees/<name>/build.log
```

---

## 4. Spawning subagents (the pattern that works)

Every subagent call **detaches** (config has `asyncByDefault` + `forceTopLevelAsync`).
So the pattern is: launch async → wait → inspect.

### Launch a wave (one or more workers)

```js
// subagent({ workflowScript: "..." })  — plain JS, NOT a backtick template literal
const task = [ "line1", "line2", "..." ].join("\n");

const r = await runs.all([
  { key: 'p1a', agent: 'worker', task, cwd: '/mnt/d/dev/media-nest-worktrees/p1a', context: 'fresh' },
  { key: 'p1b', agent: 'worker', task, cwd: '/mnt/d/dev/media-nest-worktrees/p1b', context: 'fresh' }
]);
return r.map(x => ({ key: x.key, status: x.status, output: x.output }));
```

Rules:

- Use `runs.all([{ ...plain items... }])`. Do **NOT** do `runs.run(...)` then
  `runs.all(promises)` — that combination is buggy.
- Pass explicit `cwd` into the worktree. Do **not** rely on `worktree: true`.
- `context: 'fresh'` gives each worker a clean child context.

### Wait for completion

```js
// blocking wait (use for run-to-completion turns)
subagent_wait({ id: "<runId>", timeoutMs: 1500000 })    // 25 min (worker)
subagent_wait({ all: true, timeoutMs: 300000 })          // 5-min heartbeat for monitor loop (§6a)
```

### Inspect status / result

```js
subagent({ action: "status", id: "<runId>" })          // one-shot, not a poll loop
```

Artifacts live at:

- `/tmp/pi-subagents-uid-1000/async-subagent-runs/<runId>/` → `status.json`, `events.jsonl`, child logs
- `/tmp/pi-subagents-uid-1000/async-subagent-results/<runId>.json` → `results[0].output` (the worker's final report)

---

## 5. Parallel vs sequential discipline

- **Pure-editing waves (disjoint concerns, NO build):** can fan out wider (up to ~10
  workers) because there is no Gradle daemon / memory pressure.
- **Any wave where workers must BUILD:** keep it small (≈5 max) or **sequential**.
  5 concurrent full Gradle builds would OOM this machine (7.8 GB RAM / 12 cores,
  `-Xmx6144m`).
- Preferred orchestration shape:
  1. Parallel **edit-only** wave on disjoint concerns (shared files OK if different
     regions; flag any shared file so the merge is checked for conflicts).
  2. Root merges, then runs **one serial full build** on main.
  3. If cross-file compile errors appear, spawn a **single fixup worker** to resolve them.
- When two tasks are tightly coupled (one screen + its ViewModel), a **single
  worker owning the whole vertical slice** is safer than splitting across workers
  with a fragile API contract. Prefer sequential waves for layered work.

---

## 6. Subagent budget & limits

- **Time budget — raise the defaults, don't starve deep work.** Set a generous
  `timeoutMs` on the workflow itself (not just the child). These numbers are
  **calibrated from observed runs** (a 4-reviewer wave recorded child durations
  of 11.7 / 14.7 / 17.5 / **25.0 min** — the slowest child finished exactly at
  the 25-min ceiling, so do NOT go lower):
  - **Workers (edit + build):** `timeoutMs: 1500000` (25 min) per child. A worker
    that builds often needs read → edit → build → fix → rebuild cycles, and a
    cold compile-only build alone is ~4–5 min. 15 min is too tight for two
    compile-fix rounds.
  - **Reviewers / researchers (read-only, deep thinking):** `timeoutMs: 2400000`
    (40 min) per child — the 25-min tail is at the ceiling, not comfortably
    inside it.
  - **Parent `workflowScript` must be the LARGEST number in the graph:** give it
    ~1.5× the longest child (e.g. workers `1500000` → parent `2250000`;
    reviewers `2400000` → parent `3600000`). A parent cap ≤ the child cap is
    what silently truncated the first 5-reviewer wave: the parent timed out,
    children's final outputs never flushed, and their result artifacts landed
    empty (0 bytes).
- **Turn budget:** ~15 turns is a *soft* signal, not a hard wall. If a subagent is
  still making forward progress (files read, notes taken, last activity recent),
  let it run past the turn count — do not interrupt a productive agent. Interrupt
  only on a genuine loop (same tool call repeated, no new state, last activity
  stale).
- **Max concurrent writers:** default ~5; see §5 for the build caveat. Read-only
  reviewer/researcher waves can run wider (they share no Gradle daemon).
- When two workers share a file, tell each worker which region/concern it owns,
  and warn it not to touch the other region. The orchestrator then treats the
  merge of that file as conflict-prone and reconciles via one fixup worker.
- Every worker prompt MUST include:
  - the **absolute worktree path** to edit,
  - the **exact build command** to run,
  - a **"Do NOT run git add/commit/push"** instruction,
  - the **mandatory reads** (spec files),
  - a **"report files changed + diff summary + BUILD SUCCESSFUL"** instruction.

### 6a. Monitor-and-nudge loop (check every 5 minutes)

Do **not** fire-and-forget a wave. Act as a supervisor with a 5-minute heartbeat:

1. After launching a wave, `subagent_wait({ all: true, timeoutMs: 300000 })` —
   this blocks ~5 min and returns either on completion or on timeout.
2. On timeout, `subagent({ action: "status", id: "<runId>" })` and read each
   child's `last activity` + state.
3. **Nudge when a child is off-path, stalled, or erroring:**
   - Off-path: `subagent({ action: "steer", id, index, message: "<correction>" })`.
   - Stalled > 5 min (no recent activity) but alive: steer with a reminder of the
     remaining checklist and the required output format.
   - Errored/failed: check its output artifact; if empty, relaunch that one
     concern fresh (not the whole wave) with a longer parent timeout.
4. Repeat steps 1–3 until every child is `completed` (or `failed` with a captured
   report). Read `results[0].output` / the per-child output artifact for the text.
5. Track a 4-column table (child / state / last activity / action) so you can
   report progress on demand and never lose a finished child's result.

---

## 7. Commit / merge / review discipline

1. Worker leaves edits **uncommitted** in its worktree.
2. Orchestrator inspects `git -C <worktree> diff` for sanity.
3. Commit in the worktree: `git -C <worktree> add -A && git -C <worktree> commit -m "wip: Task X.Y <subject>"`.
4. Merge into main:

   ```bash
   cd /mnt/d/dev/media-nest
   git merge --no-ff <branch> -m "wip: Task X.Y <subject>"
   ```

   If it fails with the transient `fatal: stash failed`, **just retry** — it works on retry.
5. Run the main-branch full build.
6. Spawn a **review subagent** (`agent: "reviewer"`) to audit
   `git diff <base>..HEAD`. It is read-only by design (allowlist
   `read, grep, find, ls`) and returns a `Correct / Fixed / Blocker / Note`
   report. Read `results[0].output` for the audit text.
7. Fix via a focused worker if needed, then squash WIP commits into one high-level
   commit per phase:

   ```bash
   git reset --soft <base> && git commit -m "<scope>: <high-level summary>"
   ```

8. **NEVER `git push`.** Commits stay local unless the user explicitly asks.

---

## 8. Known blockers & remedies (memorized)

| Blocker | Remedy |
| --- | --- |
| Auto-worktrees under WSL `/tmp` unreachable by Windows | Create manual worktrees on `/mnt/d/...` (§2). |
| `worktreeBaseDir` config override doesn't reload mid-session | Don't fight it — pass explicit `cwd`; create worktrees manually. Env fallback `PI_SUBAGENTS_WORKTREE_DIR` exists but only applies when the config key is *unset* and only for `worktree: true` runs (which we don't use). |
| `build-debug.bat` used to hardcode `cd /d D:\dev\media-nest` | Now worktree-relative via `%~dp0`; workers run it from the worktree root. |
| `JAVA_HOME` with forward slashes fails | Use backslashes: `C:\Program Files\Android\Android Studio\jbr`. |
| `local.properties` is gitignored | `cp` it into every new worktree. |
| Concurrent full Gradle builds OOM (7.8 GB) | Compile-only gate in worktrees (§3); one serial full build on main. |
| `workflowScript` SyntaxError from escaped backslashes/quotes | Build task text with `array.join("\n")`; avoid raw backtick template literals; prefer `build-debug.bat -nopause` (no backslashes) over the `set JAVA_HOME=...` form in prompts. |
| Parent workflow times out before children finish → children's outputs lost (empty artifacts) | Raise parent `workflowScript` `timeoutMs` above every child (§6). Restart only the unfinished concerns fresh, never the whole wave. |
| `git merge --no-ff` → `fatal: stash failed` | Retry the merge (transient). |
| (removed) Review subagent "no edits" acceptance flags | No longer applies — use `agent: "reviewer"`, which is read-only by design. |

---

## 9. Quick sanity checklist (per phase)

- [ ] `git status --short` clean on main before starting.
- [ ] Worktrees created on D: drive + `local.properties` copied.
- [ ] Each worker owns a disjoint **concern** (shared files OK if different regions; no shared signature/interface changes).
- [ ] Each worker ran its build and reported `BUILD SUCCESSFUL`.
- [ ] Root merged each branch and ran a serial full build on main.
- [ ] A read-only review subagent audited the merged diff.
- [ ] WIP commits squashed into one high-level commit.
- [ ] No `git push` performed.
- [ ] Worktrees/branches cleaned up (§2) after the phase.
