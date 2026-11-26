#!/usr/bin/env bash
# scripts/nest.sh — entry point for building, booting, and installing MediaNest.
#
#   scripts/nest.sh up            build (if needed) + emulator (if needed) + install (if changed)
#   scripts/nest.sh status        what is built / booted / installed
#   scripts/nest.sh down          stop the emulator
#   scripts/nest.sh clean         stop emulator, wipe build outputs
#
# Drive and verify the UI interactively with `agent-device` after `scripts/nest.sh up`.
# Every step is idempotent and skips completed work.
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
    ok "ready in $(( $(date +%s) - t0 ))s — drive with: scripts/ad.sh launch"
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
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
    ;;
esac
