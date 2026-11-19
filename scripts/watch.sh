#!/usr/bin/env bash
# scripts/watch.sh — mirror the device in a window while an agent drives it.
# Taps originate from `adb shell input`, so no mouse cursor is drawn: you see the
# UI react, not a pointer move.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib/common.sh"

nest_is_booted || die "no booted emulator — scripts/dev/emulator.sh up"

if ! command -v scrcpy >/dev/null; then
  bad "scrcpy not installed"
  say "  sudo apt install -y scrcpy      # WSL draws it via WSLg"
  say "  fallback stills: scripts/dev/poke.sh shot"
  exit 1
fi

if ! nest_probe_display; then
  warn "framebuffer readback is broken on this host — the mirror window will be blank."
  warn "Screenshots/video cannot work either. Run the emulator on the host OS instead."
fi

exec scrcpy --window-title "MediaNest test device" --max-size 900 --stay-awake "$@"
