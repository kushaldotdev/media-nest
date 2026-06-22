---
name: implement-mode
description: >
  Full rules, code quality standards, and output templates for Implement phase. Read this skill
  whenever the user issues /do, or when code is being written, edited, or executed. Covers
  plan reading, phased execution, self-review, linting, and done criteria. Trigger on: /do,
  "implement this", "make the change", "write the code", "apply the fix", "build it", or any
  request that results in file creation or code modification.
---

# Implement Phase Rules

## Entry — always do this first
1. Check context for `AGENTS.md [read]` marker. If absent → open `AGENTS.md` now.
2. Check context for plan marker. If absent → open the plan now.
3. Check the plan is solve-ready before editing.
4. Do not write a single line of code before both are loaded and the plan is usable.

### Plan Usability Gate

Before editing, verify the plan includes:
- exact files/functions/lines to change
- ordered steps
- edge cases
- pitfalls / do not
- validation/tests
- docs impact
- stop conditions

If the plan is missing any of these for a risky change:
- stop before editing
- state what is missing
- update the plan/doc first or ask for clarification

Never improvise across an unclear BE/FE/docs contract.

## Read-Once Pattern
After reading a file, mark it `[read]` in your reply. Do not re-open it unless:
- You're about to edit it (re-read immediately before editing — stale context causes bugs)
- User says it changed

Always re-read a file immediately before editing it, even if marked `[read]` earlier. Memory
drifts. One re-read before edit is mandatory; repeated re-reads mid-phase are not.

## Execution Rules
- **Small/medium tasks** — complete in one go
- **Large tasks** — meaningful chunks, pause after each, wait for go-ahead
  - Override: "stop after each phase" or "do it all"
- One line per task before doing it
- Follow the plan phase order; do not jump ahead unless the codebase proves the order is wrong
- After each phase: re-read changed files, self-review, self-correct before reporting
- Lint after each phase — not after individual files
- Plan conflicts codebase → **stop immediately**. Never improvise. Surface the conflict.
- If a fix reveals a broader contract issue, update the plan/doc before continuing.

---

## Code Quality (every file touched)

### Clean
- Meaningful names. Named constants — no magic values.
- One function, one job.
- Early returns over deep nesting.
- Explicit over clever.

### DRY
- No copy-paste logic. Single source of truth.
- Abstract real duplication — not anticipated duplication.

### Simple
- Boring solution over clever.
- If hard to explain → simplify.
- No premature abstraction.

### Production-ready
- All errors handled explicitly. No silent catch.
- No leftover logs / debug / hacks.
- No unresolved TODO without approval.
- Types complete. Edge cases covered.
- Pitfalls from the plan are explicitly avoided.
- Self-review before reporting done.

---

## Checklist (apply every file touched)
Null/empty/invalid input · boundary/off-by-one · auth/permissions · async/race conditions ·
stale cache · broken imports/exports · config/env/schema mismatch · backward compatibility ·
missing validation/error handling/cleanup · loading/error/empty states · test gaps ·
API contract · dead code/naming/complexity

Also check:
- edge cases listed in the plan
- pitfalls / do-not items listed in the plan
- docs updates listed in the plan
- tests listed in the plan
- final verification searches listed in the plan

---

## Done Criteria
"Done" = all steps complete + self-reviewed + quality standards met + no open blockers.
Never report done with unresolved issues. Surface them explicitly.

Done also requires:
- changed files re-read after edits
- plan edge cases checked
- plan pitfalls checked
- validation commands run or explicitly reported as not run
- docs updated when behavior/contracts changed

---

## Output Template (each phase)

```
# Phase N Summary
## Implemented
## Files changed
## Self-review
## Edge cases checked
## Pitfalls avoided
## Validation
## Docs updated
## Open issues / blockers
```
