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
subagent_wait({ id: "<runId>", timeoutMs: 600000 })   // 10 min
subagent_wait({ all: true, timeoutMs: 600000 })        // wait for all active
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

- **Time budget:** `timeoutMs: 600000` (10 min) per subagent.
- **Turn budget:** ~15 turns. If a worker loops, interrupt it and retry with a
  more precise prompt.
- **Max concurrent writers:** default ~5; see §5 for the build caveat.
- When two workers share a file, tell each worker which region/concern it owns,
  and warn it not to touch the other region. The orchestrator then treats the
  merge of that file as conflict-prone and reconciles via one fixup worker.
- Every worker prompt MUST include:
  - the **absolute worktree path** to edit,
  - the **exact build command** to run,
  - a **"Do NOT run git add/commit/push"** instruction,
  - the **mandatory reads** (spec files),
  - a **"report files changed + diff summary + BUILD SUCCESSFUL"** instruction.

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
