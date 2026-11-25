#!/usr/bin/env bash
# scripts/dev/fast-test.sh — the unit-test inner loop.
#
#   scripts/dev/fast-test.sh FolderNameConflictTest
#   scripts/dev/fast-test.sh FolderNameConflictTest DownloadsSortingTest
#   scripts/dev/fast-test.sh 'com.example.medianest.data.**'
#
# Runs ONLY the matching classes, incrementally: no --rerun-tasks (Gradle's up-to-date
# check is the point), no daemon kill (a cold daemon costs ~21s), no --force.
#
# Fails loudly when the filter matches NOTHING. `--tests` with a typo silently runs
# zero tests and reports success, which is the worst possible outcome for a fast loop:
# green, and it proved nothing. The test-count check below is what catches that.
#
# This is for iteration. `scripts/verify.sh` runs the FULL suite and is the gate.
set -uo pipefail
. "$(dirname "$0")/../lib/common.sh"
cd "$NEST_ROOT" || die "cannot cd $NEST_ROOT"

[ $# -gt 0 ] || die "usage: fast-test.sh <TestClass|pattern> [more...]   e.g. fast-test.sh FolderNameConflictTest"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
printf 'sdk.dir=%s\n' "$NEST_SDK" > local.properties
[ -x ./gradlew ] || chmod +x ./gradlew 2>/dev/null || true

FILTERS=()
for a in "$@"; do FILTERS+=(--tests "$a"); done

hdr "unit tests (incremental): $*"
start=$(date +%s)
LOG="$NEST_CACHE/fast-test.log"
if ./gradlew :app:testDebugUnitTest --console=plain "${FILTERS[@]}" > "$LOG" 2>&1; then
  rc=0
else
  rc=$?
fi
dur=$(( $(date +%s) - start ))

# Read the counts from the XML report, which is what actually ran. Gradle's "BUILD
# SUCCESSFUL" alone cannot distinguish "12 tests passed" from "0 tests matched".
REPORTS="$NEST_ROOT/app/build/test-results/testDebugUnitTest"
# awk, not bc: this repo's scripts depend on nothing outside bash + coreutils.
sum_attr() { grep -ho "$1=\"[0-9]*\"" "$REPORTS"/*.xml 2>/dev/null \
  | sed 's/[^0-9]//g' | awk '{s+=$1} END{print s+0}'; }
TOTAL=0; FAILS=0
if [ -d "$REPORTS" ]; then
  TOTAL="$(sum_attr tests)"
  FAILS="$(sum_attr failures)"
  TOTAL="${TOTAL:-0}"; FAILS="${FAILS:-0}"
fi

if [ "$rc" -ne 0 ]; then
  bad "unit tests FAILED in ${dur}s (log: $LOG)"
  grep -E "^e: |error:|FAILED|tests completed" "$LOG" | head -20
  exit 1
fi

# A surviving report from an earlier run can satisfy the count check, so require
# Gradle to have actually executed the task in this invocation.
if ! grep -qE "^> Task :app:testDebugUnitTest" "$LOG"; then
  warn "task was up to date — nothing executed (no source change); counts below are the last run's"
fi
if [ "$TOTAL" -eq 0 ]; then
  bad "the filter matched no tests: $*"
  say "  a typo here runs zero tests and exits 0 — that is the trap this check exists for."
  say "  available classes:"
  find "$NEST_ROOT/app/src/test" -name '*Test.kt' 2>/dev/null | xargs -r -n1 basename | sed 's/\.kt$//;s/^/    /'
  exit 1
fi
if [ "$FAILS" -ne 0 ]; then
  bad "${FAILS} failure(s) of ${TOTAL} tests (log: $LOG)"
  exit 1
fi
ok "${TOTAL} tests passed in ${dur}s (filter: $*)"
