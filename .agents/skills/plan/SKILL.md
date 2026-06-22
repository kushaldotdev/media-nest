---
name: plan-mode
description: >
  Full rules and output templates for Plan phase. Read this skill whenever the user issues /plan,
  or when the conversation starts in default Plan phase and a task is being scoped, explored, or
  discussed. Covers file reading, classification, risk analysis, plan writing, and gap checks.
  Trigger on: /plan, "let's plan", "what's the approach", "how do we tackle", task analysis,
  architecture discussion, or any time structured planning output is needed.
---

# Plan Phase Rules

## Read-Once Pattern
Before reading any file this phase, check if it's already been marked `[read]` in the current
conversation (e.g. `AGENTS.md [read]`, `task.md [read]`). If marked → skip re-read, use existing
context. Only re-read if user says the file changed.

## Steps — never skip, never reorder

### 1. Read
- Open `AGENTS.md` first. If missing → state it, ask how to proceed. Never invent contents.
- Open task file + all relevant source files. Not from memory.
- Map files, exports, connections. Cite what you find.
- Mark each file `[read]` in your reply header after opening it.

### 2. Classify
- **Verified** — cite file + function/line
- **Inferred** — label clearly, never present as fact
- **Unknown** — ask before proceeding

### 3. Risks
Surface: contradictions, fragile areas, missing requirements, regression risks, ordering dependencies.

### 4. Ask
Ask if user wants a plan. Do not present one until confirmed.

### 5. Present Summary (before file write)
- **Chat-first**: Present a summary in chat with bullet points. Cover main points: what, where, approach, phases, key risks. Enough to understand the plan without reading the full file — not 1-2 sentences, not exhaustive detail.
- **Ask to confirm**: After summary, ask user to confirm. If confirmed, ask them to switch to implement mode (`/do`). Do NOT write files from plan mode.
- Every plan must be:
  - **Self-explanatory** — readable cold, zero prior context needed
  - **Beginner-friendly** — exact file + function/line, exact changes, no "handle appropriately"
  - **Exhaustive** — every file, side effect, dependency. Nothing hand-waved.
  - **Ordered** — each step builds on the last
  - **Solve-ready** — includes edge cases, pitfalls, validation, docs, and rollback/stop conditions

Per step format: `What · Where (file+fn/line) · How (exact) · Why · Edge cases · Pitfalls / do not · Validation · Docs`

### 6. Gap Check
Re-read plan vs task vs actual files. Fix vague/missing/risky steps. State what changed.
- Runs after user confirms and switches to implement mode. Append results to plan file.

Fail the gap check if any step:
- says "handle appropriately", "update logic", "fix tests", or similar vague wording
- lacks exact file/function targets
- lacks edge cases for boundary-sensitive logic
- lacks pitfalls for async, money, time, auth, cache, or API-contract changes
- lacks validation/tests
- changes behavior without docs impact analysis

---

## Audience Requirements

Write for two readers:
- **Regular engineer:** direct implementation path and tradeoffs.
- **Beginner / low-context model:** exact order, exact files, do-not-do warnings, test commands.

Plans must be usable without prior conversation context.
If the task is complex, add a `Beginner Implementation Guide` section.
If the task crosses BE/FE/docs, define the end-to-end contract before steps.

---

## Checklist (apply every plan)
Null/empty/invalid input · boundary/off-by-one · auth/permissions · async/race conditions ·
stale cache · broken imports/exports · config/env/schema mismatch · backward compatibility ·
missing validation/error handling/cleanup · loading/error/empty states · test gaps ·
API contract · dead code/naming/complexity

Also include:
- Edge cases
- Pitfalls / do not
- Final verification checklist
- Required docs updates
- Stop conditions when code and plan disagree

---

## Output Templates

### Discussion output
```
# Discussion Findings
## Verified
## Inferred
## Unknown / needs clarification
## Edge cases / failure modes
## Pitfalls / do not
## Issues / pushback
## Questions
```

### Plan output (Written to `.agents/plan/YYYY-MM-DD-<brief-description-slug>.md` after mode switch)
```
# Implementation Plan
## System / Contract Summary
## Phase Order
## Steps
Each step:
  What
  Where
  How
  Why
  Edge cases
  Pitfalls / do not
  Validation
  Docs
## Beginner Implementation Guide
## Final Verification Checklist
## Stop Conditions
```

### Post-plan gap check output (Appended to plan file after mode switch)
```
# Plan Gap Check
## Gaps / risks found
## Changes made
## Remaining edge cases
## Remaining pitfalls
## Open questions
```

### Chat Summary Output
```
## Plan Summary
- What: <one line>
- Where: <key files/modules>
- Approach: <how we'll solve it>

### Phases
- Phase 1: <name> — <what>
- Phase 2: <name> — <what>

### Key Risks
- <risk 1>
- <risk 2>

Want me to write the full plan to `.agents/plan/YYYY-MM-DD-<slug>.md`? Switch to implement mode (`/do`) and confirm.
```
