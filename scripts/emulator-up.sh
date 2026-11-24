#!/usr/bin/env bash
# scripts/emulator-up.sh — compatibility alias for "scripts/dev/emulator.sh up".
#
# docs/TESTING.md, .agents/TESTING_PROTOCOL.md and the medianest-app-testing skill
# all spell this command, so the name has to exist. The real implementation lives in
# scripts/dev/emulator.sh; this only forwards, and accepts "down|status" too.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
exec "$HERE/dev/emulator.sh" "${1:-up}" "${@:2}"
