#!/usr/bin/env bash
# scripts/verify.sh — full verification run with a written report.
#
#   scripts/verify.sh                 build + unit tests + emulator + install + smoke flow
#   scripts/verify.sh --flow <name>   use scripts/flows/<name>.sh
#   scripts/verify.sh --fast          reuse a booted emulator and an up-to-date APK
#   scripts/verify.sh --quick         alias for --fast (older docs use this name)
#   scripts/verify.sh --no-keep       stop the emulator when the run ends
#   scripts/verify.sh --keep          accepted, now the default (no-op)
#
# The emulator is KEPT by default: a 55s boot per run is the single largest
# avoidable cost in this loop, and the next run reuses it. Pass --no-keep when
# you actually want the RAM back.
#
# When the emulator is not already running, its boot is started in the
# BACKGROUND before the build and joined afterwards, so the ~55s boot overlaps
# the build instead of being added to it. The gates are unchanged: the flow runs
# only after BOTH the build and the boot have succeeded, and a failed build
# still fails the run.
#
# This is the slow, auditable path: it writes artifacts/verify-<UTC>/RESULT.md with
# an evidence table. For fast iteration use scripts/nest.sh test instead.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib/common.sh"
cd "$NEST_ROOT" || die "cannot cd $NEST_ROOT"

FLOW=smoke; FAST=0; KEEP=1
while [ $# -gt 0 ]; do
  case "$1" in
    --flow) FLOW="${2:?--flow needs a name}"; shift ;;
    --fast|--quick) FAST=1 ;;
    --keep) KEEP=1 ;;
    --no-keep) KEEP=0 ;;
    -h|--help) sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown arg: $1" ;;
  esac; shift
done

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
export ART="$NEST_ROOT/artifacts/verify-$STAMP"
mkdir -p "$ART/screens" "$ART/ui"
FAILED=0

report() {
  {
    echo "# Verify — $STAMP"
    echo
    echo "- commit: \`$(git rev-parse --short HEAD 2>/dev/null || echo unknown)\`"
    echo "- flow:   \`$FLOW\`"
    echo "- result: $([ "$FAILED" -eq 0 ] && echo '**PASS**' || echo "**FAIL** ($FAILED)")"
    echo
    echo "| Evidence | Path |"; echo "| --- | --- |"
    for f in build.log logcat.txt logcat-crashes.txt run.mp4; do
      [ -e "$ART/$f" ] && echo "| $f | \`$f\` |"
    done
    echo "| screenshots | \`screens/\` ($(ls "$ART/screens"/*.png 2>/dev/null | wc -l)) |"
    echo "| ui dumps | \`ui/\` ($(ls "$ART/ui"/*.xml 2>/dev/null | wc -l)) |"
    echo
    echo "## Crashes"; if [ -s "$ART/logcat-crashes.txt" ]; then
      echo '```'; head -60 "$ART/logcat-crashes.txt"; echo '```'; else echo "none"; fi
  } > "$ART/RESULT.md"
  say ""
  say "artifacts: $ART"
  if [ "$KEEP" -eq 1 ]; then say "keeping emulator running (--no-keep to stop it)"
  else say "stopping emulator (--no-keep)"; adb emu kill >/dev/null 2>&1; fi
}

# Boot the emulator while the build runs. Started only when nothing is booted,
# so the common repeat run pays neither a boot nor a wait. The PID/handle is
# joined after the build; its exit code is a gate like any other.
EMU_PID=""
EMU_LOG="$ART/emulator-boot.log"
start_emulator_early() {
  nest_is_booted && { say "  emulator already booted — no parallel boot needed"; return 0; }
  ( "$HERE/dev/emulator.sh" up ) > "$EMU_LOG" 2>&1 &
  EMU_PID=$!
  say "  emulator boot started in the background (log: emulator-boot.log)"
}
# Join the background boot. A boot failure fails the run exactly like the
# foreground call it replaces — the build result is not allowed to mask it.
join_emulator_early() {
  [ -n "$EMU_PID" ] || return 0
  if wait "$EMU_PID"; then
    EMU_PID=""
    ok "emulator ready (boot overlapped the build)"
    return 0
  fi
  EMU_PID=""
  bad "emulator failed to boot"
  tail -20 "$EMU_LOG" 2>/dev/null
  return 1
}

if [ "$FAST" -eq 0 ]; then
  hdr "build + unit tests"
  # Same worktree trap as build.sh: gradlew arrives as 664 when core.fileMode=false.
  [ -x ./gradlew ] || chmod +x ./gradlew 2>/dev/null || true
  start_emulator_early
  # No --force: build.sh's content hash decides. A changed tree still builds —
  # that is the point of the hash — while an unchanged one skips ~8-11s of Gradle.
  if "$HERE/dev/build.sh" > "$ART/build.log" 2>&1; then ok "assembleDebug"
  else
    bad "build failed"; tail -20 "$ART/build.log"
    # A failed build is the verdict; the boot is left running deliberately so
    # the retry does not pay 55s of boot again. Nothing was driven.
    [ -n "$EMU_PID" ] && say "  (emulator boot continues in the background: $EMU_LOG)"
    report; exit 1
  fi
  if ./gradlew :app:testDebugUnitTest --console=plain > "$ART/unit-tests.log" 2>&1; then ok "unit tests"
  else bad "unit tests failed (see unit-tests.log)"; FAILED=$((FAILED+1)); fi
  # Same condition as build.sh: only free the daemon when an emulator boots in
  # this VM. An unconditional --stop here cost the NEXT verify ~10-20s of JVM
  # startup and contradicted build.sh's deliberate warm-daemon policy.
  if nest_daemon_must_stop; then ./gradlew --stop >/dev/null 2>&1 || true; fi
  # Gates compose before anything is driven: a failed boot stops the run here.
  join_emulator_early || { report; exit 1; }
fi

hdr "device + install"
# Still called unconditionally: it is a no-op when already booted, and it is the
# only place that repairs a half-booted device (present but not answering).
"$HERE/dev/emulator.sh" up || { bad "emulator"; report; exit 1; }
"$HERE/dev/install.sh" || { bad "install"; report; exit 1; }
adb logcat -c >/dev/null 2>&1 || true

[ -f "$HERE/flows/$FLOW.sh" ] || die "no flow: scripts/flows/$FLOW.sh (available: $(ls "$HERE/flows"/*.sh 2>/dev/null | xargs -r -n1 basename | sed 's/.sh$//' | grep -v '^_' | tr '\n' ' '))"
. "$HERE/lib/drive.sh"
. "$HERE/flows/$FLOW.sh"

hdr "flow: $FLOW"
start_recording
if "flow_$FLOW"; then ok "flow completed"; else bad "flow failed"; FAILED=$((FAILED+1)); fi
ck_logcat || FAILED=$((FAILED+1))
stop_recording
adb logcat -d > "$ART/logcat.txt" 2>/dev/null
grep -E "FATAL EXCEPTION|ANR in $NEST_PKG" "$ART/logcat.txt" > "$ART/logcat-crashes.txt" 2>/dev/null || : > "$ART/logcat-crashes.txt"

report
[ "$FAILED" -eq 0 ] && { ok "PASS"; exit 0; } || { bad "FAIL ($FAILED)"; exit 1; }
