#!/usr/bin/env bash
# scripts/dev/warm.sh — put the whole fast path into a known-warm state, once.
#
#   scripts/dev/warm.sh
#
# Brings up everything a repeat `nest.sh up` / `agent-device` session assumes is already true:
#   1. Gradle daemon running (no JVM start on the next build)
#   2. emulator booted (quickboot snapshot if one exists, else cold)
#   3. animation scales at 0 (no transition flakiness during UI snapshots)
#   4. current APK installed (no 10s push+install mid-loop)
# ...then prints one line saying what it cost.
#
# Idempotent by construction: every step is one of the existing scripts, each of
# which skips work already done. Running it twice costs seconds the second time.
# It changes nothing about evidence — it only front-loads the setup.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/../lib/common.sh"
cd "$NEST_ROOT" || die "cannot cd $NEST_ROOT"

T0=$(date +%s)
STEPS=()

elapsed() { printf '%s' "$(( $(date +%s) - T0 ))s"; }

# 1. Gradle daemon. `--status` starts nothing, so check for a live one first
#    instead of paying a build just to warm it.
hdr "1/4 gradle daemon"
if ./gradlew --status 2>/dev/null | grep -qE '^ *[0-9]+ +IDLE'; then
  ok "daemon already running"
else
  s=$(date +%s)
  # :help is the cheapest task that fully configures the project and leaves a
  # live, configured daemon behind. It runs no compilation.
  ./gradlew :help --console=plain > "$NEST_CACHE/warm-gradle.log" 2>&1 \
    && ok "daemon started in $(( $(date +%s) - s ))s" \
    || { bad "gradle failed to start (log: $NEST_CACHE/warm-gradle.log)"; tail -10 "$NEST_CACHE/warm-gradle.log"; exit 1; }
fi
STEPS+=("daemon:$(elapsed)")

# 2. Emulator. `emulator.sh up` is a no-op when booted and snapshot-aware when
#    not, so there is nothing to decide here.
hdr "2/4 emulator"
s=$(date +%s)
if nest_is_booted; then
  ok "already booted ($(nest_device_serial))"
else
  "$HERE/emulator.sh" up || die "emulator failed to boot"
fi
STEPS+=("emulator:+$(( $(date +%s) - s ))s")

# 3. Animations. emulator.sh sets these on the local path and host-emulator.sh on
#    the host path, but a device that was already booted when this script started
#    may predate either — so state it directly. This makes dumps MORE reliable.
hdr "3/4 animations"
for s in window transition animator; do
  adb shell settings put global ${s}_animation_scale 0 >/dev/null 2>&1 || true
done
ok "animation scales = 0"

# 4. APK. build.sh skips Gradle by content hash; install.sh skips the 188MB push
#    when the installed build already matches.
hdr "4/4 apk + install"
s=$(date +%s)
"$HERE/build.sh" || die "build failed"
"$HERE/install.sh" || die "install failed"
STEPS+=("apk+install:+$(( $(date +%s) - s ))s")

TOTAL=$(( $(date +%s) - T0 ))
say ""
ok "warm in ${TOTAL}s — next run only drives the app via agent-device"
say "  ${STEPS[*]}"
say "  revert animations with: scripts/nest.sh clean   (or adb shell settings put global window_animation_scale 1)"
