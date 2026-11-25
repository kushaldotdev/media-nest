#!/usr/bin/env bash
# scripts/dev/perf.sh — measure the dev loop instead of guessing about it.
#
#   scripts/dev/perf.sh build      time an incremental build twice (cold, then warm daemon)
#   scripts/dev/perf.sh test       time two repeat runs of scripts/nest.sh test
#   scripts/dev/perf.sh verify     time two repeat runs of scripts/verify.sh
#   scripts/dev/perf.sh all        all of the above, in that order
#
# `build` times BOTH states on purpose: the cold number is what a killed daemon
# costs, the warm one is what a repeat build costs. Comparing them is how the
# daemon-policy change is shown to help rather than asserted to.
#
# Writes a table to tmp/perf-<UTC>.md (gitignored). Never writes to the repo root
# and never commits. Exit code is non-zero if any measured step failed, so a
# broken loop cannot report a fast time.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/../lib/common.sh"
cd "$NEST_ROOT" || die "cannot cd $NEST_ROOT"

CMD="${1:-all}"
case "$CMD" in build|test|verify|all) ;; -h|--help) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  *) die "usage: perf.sh {build|test|verify|all}" ;; esac

OUT_DIR="$NEST_ROOT/tmp"
mkdir -p "$OUT_DIR"
OUT="$OUT_DIR/perf-$(date -u +%Y%m%dT%H%M%SZ).md"
ROWS=(); FAILED=0

# run <label> <command...> — time it, record the wall clock, keep going on failure
# but remember it: a failing step's duration is not a useful speed number.
run() {
  local label="$1"; shift
  local s e dur rc
  printf '  %-42s' "$label"
  s=$(date +%s)
  "$@" > "$NEST_CACHE/perf-step.log" 2>&1
  rc=$?
  e=$(date +%s); dur=$(( e - s ))
  if [ "$rc" -eq 0 ]; then printf '%ss\n' "$dur"; else printf '%ss  (FAILED rc=%s)\n' "$dur" "$rc"; FAILED=$((FAILED+1)); fi
  ROWS+=("$label|$dur|$rc")
}

hdr "perf: $CMD   ($(git rev-parse --short HEAD 2>/dev/null || echo unknown))"

if [ "$CMD" = build ] || [ "$CMD" = all ]; then
  # Cold first: two warm numbers in a row would hide the daemon cost entirely.
  hdr "build — cold daemon vs warm daemon"
  ./gradlew --stop >/dev/null 2>&1 || true
  run "build.sh --force (cold daemon)" "$HERE/build.sh" --force
  run "build.sh --force (warm daemon)" "$HERE/build.sh" --force
  # The no-op fast path: this is the number that matters for an unchanged tree.
  run "build.sh (sources unchanged, hash hit)" "$HERE/build.sh"
fi

if [ "$CMD" = test ] || [ "$CMD" = all ]; then
  hdr "test — two repeat runs of nest.sh test"
  # Both runs are AFTER the first, so they measure the steady state a repeat
  # loop actually pays rather than paying first-run setup twice.
  run "nest.sh test (run 1)" "$NEST_ROOT/scripts/nest.sh" test
  run "nest.sh test (run 2, fully warm)" "$NEST_ROOT/scripts/nest.sh" test
fi

if [ "$CMD" = verify ] || [ "$CMD" = all ]; then
  hdr "verify — two repeat runs"
  run "verify.sh (run 1)" "$NEST_ROOT/scripts/verify.sh"
  run "verify.sh (run 2, emulator + daemon warm)" "$NEST_ROOT/scripts/verify.sh"
fi

{
  echo "# MediaNest perf — $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "- commit: \`$(git rev-parse --short HEAD 2>/dev/null || echo unknown)\`"
  echo "- host:   \`$(uname -r)\`"
  echo "- daemon: \`$(./gradlew --status 2>/dev/null | grep -cE '^ *[0-9]+ +IDLE' || echo 0)\` idle"
  echo
  echo '| Step | Seconds | Result |'
  echo '| --- | --- | --- |'
  for r in "${ROWS[@]}"; do
    IFS='|' read -r l d rc <<< "$r"
    [ "$rc" -eq 0 ] && echo "| $l | $d | ok |" || echo "| $l | $d | **FAILED rc=$rc** |"
  done
} > "$OUT" 2>/dev/null

say ""
if [ "$FAILED" -eq 0 ]; then ok "wrote $OUT"
else bad "$FAILED step(s) failed — results in $OUT are not comparable"; fi
exit "$([ "$FAILED" -eq 0 ] && echo 0 || echo 1)"
