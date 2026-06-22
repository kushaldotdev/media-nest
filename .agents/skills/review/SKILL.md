---
name: review-mode
description: >
  Full rules and output templates for Review phase. Read this skill whenever the user issues
  /review, or when reviewing code changes, implementation correctness, or system architecture.
  Handles two modes: code review (what changed, is it correct) and architecture review
  (upstream/downstream design analysis). Trigger on: /review, "check this", "review the changes",
  "review the architecture", "does this look right", "verify the implementation", "check the design",
  PR review, or any request to evaluate correctness of code, a plan, or a system design.
---

# Review Phase Rules

## Read-Once Pattern
Before reading any file, check if it's already marked `[read]` in this conversation.
If marked → skip re-read, use existing context.
Exception: fresh-pass required on phase entry — treat all files as unread when switching into Review.

---

## Mode Detection — decide before reading anything

Run both modes unless context is clearly one-sided.

| Signal | Mode |
|---|---|
| "review changes", "check the PR", plan exists, specific files mentioned | Code review |
| "review the architecture", "check the design", structural question | Arch review |
| Both signals, or no clear signal, or user says nothing specific | **Both — arch first, then code** |

Arch first always. If the design is wrong, code correctness is irrelevant.

State selected mode at the top of your reply, e.g.:
`Review mode: Code` or `Review mode: Arch` or `Review mode: Both (arch first)`

---

## Step 0: Anchor Check — do this before anything else

The anchor is a compact project summary built from source files. It keeps the big picture
in context during focused subsystem passes without loading full docs every time.

Anchor location:
- Project-wide review: `.agents/anchors/<project-name>.md`
- Focused subsystem review: `.agents/anchors/<project-name>-<subsystem-name>.md`

Use a subsystem-specific anchor in large codebases when the user names a focused area
(example: streaming, auth, reports, broker sync, frontend routing).

### Decision tree

```
Does the correct project/subsystem anchor exist?
  NO  → run Anchor Build (below), then continue to Step 1
  YES → run Anchor Staleness Check (below)
          STALE   → run Anchor Partial Rebuild, then continue to Step 1
          CURRENT → load anchor, mark [anchor:loaded], continue to Step 1
```

### Anchor Build (first time)
1. Read all architecture docs and key source files the user listed
2. Write `.agents/anchors/<project>.md` using the Anchor Format below
3. Mark `[anchor:built]` in your reply

### Anchor Staleness Check (anchor exists)
1. Read the `built` date and `source-files` list from the anchor header
2. For each file in the list: check its last modified timestamp (`os.path.getmtime` or `stat`)
3. Any file modified after `built` date → anchor is stale for that file
4. All files same → anchor is current

### Anchor Partial Rebuild (stale)
1. Re-read only the files newer than the anchor `built` date
2. Update only the affected sections of the anchor
3. Update the `built` date to now
4. Mark `[anchor:rebuilt — changed files: x, y, z]` in your reply

### Anchor Format
```
---
project: <name>
built: <YYYY-MM-DD HH:MM>
source-files:
  - path: <file>
    mtime: <YYYY-MM-DD HH:MM>
  - path: <file>
    mtime: <YYYY-MM-DD HH:MM>
---

# Architecture Anchor: <project>

## End Goal (2 sentences max)

## Critical Invariants
(what must always be true — data integrity, ordering, consistency rules)

## Key Data Flow
(the main path data takes through the system, one level deep)

## Subsystems
(name · responsibility · key files)

## Known Danger Zones
(areas flagged in past reviews, fragile seams, known tech debt)

## Cross-cutting Concerns
(things to check in every subsystem: e.g. float vs Decimal, auth at wrong layer)
```

---

## Step 1: Subsystem Passes

With the anchor loaded, review each subsystem separately.
Anchor stays in context throughout — it is the big picture reference.

For each subsystem:
1. Read only that subsystem's files → mark each `[read]`
2. Apply the relevant checklist (arch or code below)
3. Check cross-cutting concerns from the anchor on every file
4. Note findings — do not write final output yet

Keep passes focused. Do not re-read files from other subsystems unless a dependency
requires it.

---

## Step 2: Synthesis Pass

After all subsystem passes, do one final pass with NO new file reads.
Input: all findings from subsystem passes only.

Check for:
- Issues that interact across subsystems (seam bugs)
- Findings that contradict each other
- Gaps at subsystem boundaries not caught in individual passes
- Update anchor `Known Danger Zones` with any new findings

---

## Step 3: Present Summary (before file write)
- **Chat-first**: Present a summary in chat with bullet points. Cover: mode, files reviewed, key findings by severity, verdict. Enough to understand the review without reading the full file — not 1-2 sentences, not exhaustive detail.
- **Ask to confirm**: After summary, ask user to confirm. If confirmed, ask them to switch to implement mode (`/do`). Do NOT write files from review mode.

For every non-nitpick issue, include enough information for a lower-context implementer to fix it
without reading this conversation.

Each issue must include:
- **Problem** — exact observed behavior, with file + function/line
- **Impact** — what breaks, corrupts, races, leaks, or misleads
- **Root cause** — ownership/contract/code mismatch
- **Solution** — ordered implementation steps, not just a recommendation
- **Implementation shape** — small pseudocode/snippet when it prevents ambiguity
- **Edge cases** — boundary cases the fix must handle
- **Pitfalls / do not** — common wrong fixes to avoid
- **Validation** — specific tests, commands, searches, or manual checks
- **Docs** — docs that must be updated, if any

If an issue crosses BE/FE/docs, state the end-to-end contract explicitly.
If an identifier can be ambiguous, name the canonical routing/storage identifier.
If previous docs are wrong, say they are wrong and cite actual code as source of truth.

---

## Audience Requirements

Write for two readers:
- **Regular engineer:** concise cause, impact, fix, tests.
- **Beginner / low-context model:** exact files, exact order, edge cases, pitfalls, and stop conditions.

Do not assume the reader remembers prior conversation.
Do not write "handle appropriately", "update logic", "fix tests", or similar vague steps.
Do not say an issue is solved by a local patch when the real contract spans multiple layers.

---

## Mode A: Architecture Review

**Goal:** Is the design sound? Are upstream/downstream relationships correct?

### Checklist per subsystem
Circular dependencies · wrong layer ownership · interface contract mismatches ·
missing error propagation at boundaries · undocumented side effects · config/env assumptions ·
single points of failure · scalability assumptions baked in · auth/permissions at wrong layer ·
data ownership ambiguity

---

## Mode B: Code Review

**Goal:** Is the implementation correct, complete, and regression-safe?

### Steps per subsystem
1. Read changed files → mark each `[read]`
2. Read adjacent files (callers, dependents, shared utils) for regressions
3. Verify against task + plan — cite file+fn for every claim
4. Docs can be wrong — compare against actual code behavior, not comments or naming
5. Classify every issue: **Critical / Major / Minor / Nitpick**

### Checklist per subsystem
Null/empty/invalid input · boundary/off-by-one · auth/permissions · async/race conditions ·
stale cache · broken imports/exports · config/env/schema mismatch · backward compatibility ·
missing validation/error handling/cleanup · loading/error/empty states · test gaps ·
API contract · dead code/naming/complexity

---

## Output Templates

### Architecture Review output (Written to `.agents/review/YYYY-MM-DD-<brief-description-slug>.md` after mode switch)
```
# Architecture Review
## Anchor status (built / loaded / rebuilt)
## Subsystems reviewed
## Issues found per subsystem
(subsystem · file · fn/line · exact problem · impact · severity · solution)
## Seam issues (synthesis pass)
## Cross-cutting violations
## Edge cases / pitfalls
## Docs to update
## Verdict
```

### Code Review output (Written to `.agents/review/YYYY-MM-DD-<brief-description-slug>.md` after mode switch)
```
# Code Review
## Anchor status (built / loaded / rebuilt)
## Files checked
## Verified correct
## Issues found
(file · fn/line · exact problem · why wrong · severity · solution · validation)
## Seam issues (synthesis pass)
## Regression risks
## Nitpicks
## Edge cases / pitfalls
## Docs to update
## Verdict
```

### Combined output (both modes) (Written to `.agents/review/YYYY-MM-DD-<brief-description-slug>.md` after mode switch)
Run arch review block first, then code review block.
Add a final section:

```
# Combined Verdict
## Arch issues that affect code correctness
## Seam issues found in synthesis pass
## Beginner implementation notes
## Final verification checklist
## Overall recommendation
```

### Documentation / Remediation Plan output (Written to `.agents/review/YYYY-MM-DD-<brief-description-slug>.md` after mode switch)
When the user asks to write or overwrite a review/remediation doc, use this shape:

```
# <Subsystem> Review And Remediation Plan
## System Summary
## Core Contracts
## Previous Review Recheck
## Active Issues And Solutions
For each issue:
  Severity
  Files
  Problem
  Impact
  Root cause
  Solution
  Implementation shape
  Edge cases
  Pitfalls / do not
  Validation
  Docs to update
## Beginner Implementation Guide
## Final Verification Checklist
## Test Plan
```

### Chat Summary Output
```
## Review Summary
- Mode: <code / arch / both>
- Files: <count> checked across <subsystems>

### Critical / Major
- <issue 1> — <file:line>
- <issue 2> — <file:line>

### Minor / Nitpick
- <issue 1>
- <issue 2>

### Verdict
<pass / pass with issues / block>

Want me to write the full review to `.agents/review/YYYY-MM-DD-<slug>.md`? Switch to implement mode (`/do`) and confirm.
```
