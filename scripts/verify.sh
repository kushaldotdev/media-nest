#!/usr/bin/env bash
# scripts/verify.sh — full verification run with a written report.
#
#   scripts/verify.sh                 build + unit tests + emulator + install + smoke flow
#   scripts/verify.sh --flow <name>   use scripts/flows/<name>.sh
#   scripts/verify.sh --fast          reuse a booted emulator and an up-to-date APK
#   scripts/verify.sh --keep          leave the emulator running
#
# This is the slow, auditable path: it writes artifacts/verify-<UTC>/RESULT.md with
# an evidence table. For fast iteration use scripts/nest.sh test instead.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib/common.sh"
cd "$NEST_ROOT" || die "cannot cd $NEST_ROOT"

FLOW=smoke; FAST=0; KEEP=0
while [ $# -gt 0 ]; do
  case "$1" in
    --flow) FLOW="${2:?--flow needs a name}"; shift ;;
    --fast) FAST=1 ;;
    --keep) KEEP=1 ;;
    -h|--help) sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
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
  [ "$KEEP" -eq 1 ] || { say "stopping emulator (--keep to retain)"; adb emu kill >/dev/null 2>&1; }
}

if [ "$FAST" -eq 0 ]; then
  hdr "build + unit tests"
  if "$HERE/dev/build.sh" --force > "$ART/build.log" 2>&1; then ok "assembleDebug"
  else bad "build failed"; tail -20 "$ART/build.log"; report; exit 1; fi
  if ./gradlew :app:testDebugUnitTest --console=plain > "$ART/unit-tests.log" 2>&1; then ok "unit tests"
  else bad "unit tests failed (see unit-tests.log)"; FAILED=$((FAILED+1)); fi
  ./gradlew --stop >/dev/null 2>&1 || true
fi

hdr "device + install"
"$HERE/dev/emulator.sh" up || { bad "emulator"; report; exit 1; }
"$HERE/dev/install.sh" || { bad "install"; report; exit 1; }
adb logcat -c >/dev/null 2>&1 || true

[ -f "$HERE/flows/$FLOW.sh" ] || die "no flow: scripts/flows/$FLOW.sh"
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
