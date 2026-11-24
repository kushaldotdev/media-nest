#!/usr/bin/env bash
# scripts/nest.sh — ONE entry point for building, running and testing MediaNest.
#
#   scripts/nest.sh up            build (if needed) + emulator (if needed) + install (if changed)
#   scripts/nest.sh test          up, then run the smoke flow and report PASS/FAIL
#   scripts/nest.sh test <flow>   run scripts/flows/<flow>.sh
#   scripts/nest.sh watch         mirror the device in a window (scrcpy via WSLg)
#   scripts/nest.sh status        what is built / booted / installed
#   scripts/nest.sh down          stop the emulator
#   scripts/nest.sh clean         stop emulator, wipe build outputs
#
# Every step is idempotent and skips completed work, so the second run of
# `scripts/nest.sh test` costs only the flow itself (seconds, not minutes).
#
# Env overrides: NEST_AVD, NEST_PKG, NEST_RAM, ANDROID_HOME
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib/common.sh"

CMD="${1:-help}"; shift || true

bootstrap() {
  "$HERE/dev/build.sh" "$@" || return 1
  "$HERE/dev/emulator.sh" up || return 1
  "$HERE/dev/install.sh" "$@" || return 1
}

case "$CMD" in
  up)
    hdr "MediaNest up"
    t0=$(date +%s)
    bootstrap "$@"
    ok "ready in $(( $(date +%s) - t0 ))s — try: scripts/nest.sh test"
    ;;
  test)
    FLOW="${1:-smoke}"; shift || true
    hdr "MediaNest test: $FLOW"
    t0=$(date +%s)
    bootstrap || exit 1
    case "$FLOW" in _*) die "\"$FLOW\" is a template, not a flow — copy it: cp scripts/flows/_template.sh scripts/flows/myfeature.sh" ;; esac
    [ -f "$HERE/flows/$FLOW.sh" ] || die "no flow: scripts/flows/$FLOW.sh (see scripts/flows/_template.sh)"
    . "$HERE/lib/drive.sh"
    . "$HERE/flows/$FLOW.sh"
    "flow_$FLOW"
    rc=$?
    echo
    # A passing tap means nothing if the process died — always check the log.
    nest_clean_logcat || rc=1
    dur=$(( $(date +%s) - t0 ))
    if [ "$rc" -eq 0 ]; then ok "PASS ($FLOW, ${dur}s)"; else bad "FAIL ($FLOW, ${dur}s)"; fi
    say "  artifacts: ${ART:-$NEST_CACHE}"
    [ -d "${ART:-}/screens" ] && say "  shots:     $ART/screens ($(ls "$ART/screens"/*.png 2>/dev/null | wc -l) files)"
    exit "$rc"
    ;;
  watch)
    exec "$HERE/watch.sh" "$@"
    ;;
  status)
    "$HERE/dev/emulator.sh" status
    hdr "install state"
    if adb shell pm path "$NEST_PKG" >/dev/null 2>&1; then
      ok "$NEST_PKG installed"
    else
      warn "$NEST_PKG not installed"
    fi
    hdr "display readback"
    if nest_is_booted; then
      nest_probe_display && ok "screencap works" || warn "screencap broken (headless WSL limitation)"
    else
      say "  (no device)"
    fi
    ;;
  down)   "$HERE/dev/emulator.sh" down ;;
  clean)
    "$HERE/dev/emulator.sh" down
    rm -rf "$NEST_ROOT/app/build" "$NEST_ROOT/build" "$NEST_CACHE/.installed"
    ok "cleaned"
    ;;
  *)
    sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
    ;;
esac
