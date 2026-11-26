#!/usr/bin/env bash
# scripts/ad.sh — run agent-device against the active Android emulator or phone.
#
# Automatically bridges WSL -> Windows host adb (ADB_SERVER_SOCKET) via
# scripts/lib/common.sh and targets the booted Android serial so agent-device
# never falls back to the Linux desktop target.
#
#   scripts/ad.sh launch                   open com.example.medianest.debug --foreground
#   scripts/ad.sh jump downloads|updates   deep-link straight to Downloads or Settings/Updates
#   scripts/ad.sh <agent-device args...>   forward any agent-device command (click, snapshot, ...)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib/common.sh"

if [ $# -eq 0 ] || [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
fi

command -v agent-device >/dev/null 2>&1 || die "agent-device not found on PATH (npm install -g agent-device)"
nest_is_booted || die "no booted Android device — run: scripts/nest.sh up"

SERIAL="${NEST_SERIAL:-$(nest_device_serial)}"
[ -n "$SERIAL" ] || die "no ready Android serial found"

case "$1" in
  launch)
    shift
    exec agent-device --platform android --serial "$SERIAL" open "$NEST_PKG" --foreground "$@"
    ;;
  jump)
    case "${2:-}" in
      downloads) ACTION="com.example.medianest.ACTION_NAVIGATE_DOWNLOADS" ;;
      updates)   ACTION="com.example.medianest.ACTION_NAVIGATE_UPDATES" ;;
      *) die "usage: scripts/ad.sh jump {downloads|updates}" ;;
    esac
    exec adb -s "$SERIAL" shell am start --activity-single-top --activity-clear-top -n "$NEST_PKG/$NEST_ACT" -a "$ACTION"
    ;;
  *)
    exec agent-device --platform android --serial "$SERIAL" "$@"
    ;;
esac
