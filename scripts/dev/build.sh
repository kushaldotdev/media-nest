#!/usr/bin/env bash
# scripts/dev/build.sh — incremental build with a fast no-op path.
#
#   scripts/dev/build.sh          build debug APK (skips Gradle entirely if up to date)
#   scripts/dev/build.sh --force  always run Gradle
#   scripts/dev/build.sh clean    clean then build
#
# Speed notes:
#  - Keeps the Gradle daemon warm unless an emulator competes for RAM inside
#    this VM (see the daemon section at the bottom). A cold daemon costs ~21s of
#    JVM start + configuration-cache deserialisation on the NEXT build.
#  - Configuration cache + build cache are already on in gradle.properties.
#  - `--offline` when the dependency cache is warm, so a flaky network cannot stall a loop.
set -uo pipefail
. "$(dirname "$0")/../lib/common.sh"
cd "$NEST_ROOT" || die "cannot cd $NEST_ROOT"

FORCE=0; DO_CLEAN=0
for a in "$@"; do
  case "$a" in --force) FORCE=1 ;; clean) DO_CLEAN=1 ;; *) die "unknown arg: $a" ;; esac
done

[ -n "$NEST_SDK" ] || die "Android SDK not found; set ANDROID_HOME"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
printf 'sdk.dir=%s\n' "$NEST_SDK" > local.properties

# Fast path: a content hash, not mtime. mtime lies — `cp`, `rsync`, `git
# checkout` and editors all bump it without changing bytes, which silently
# turns every run into a full Gradle invocation.
stamp="$NEST_CACHE/.srchash"
srchash() {
  find app/src gradle app/build.gradle.kts build.gradle.kts settings.gradle.kts \
       gradle.properties -type f 2>/dev/null \
    | sort | xargs -r sha256sum 2>/dev/null | sha256sum | cut -d' ' -f1
}
CUR_HASH="$(srchash)"
if [ "$FORCE" -eq 0 ] && [ "$DO_CLEAN" -eq 0 ] && [ -f "$NEST_APK" ] \
   && [ -f "$stamp" ] && [ "$(cat "$stamp")" = "$CUR_HASH" ]; then
  ok "up to date: sources unchanged, $(ls -lh "$NEST_APK" | awk '{print $5}') APK  (--force to rebuild)"
  exit 0
fi

ARGS=(:app:assembleDebug --console=plain)
[ "$DO_CLEAN" -eq 1 ] && ARGS=(clean "${ARGS[@]}")

# `core.fileMode=false` is set in this repo (Windows-era config), so a freshly created
# `git worktree` checks out gradlew as 664 and `./gradlew` fails with "Permission denied"
# before Gradle ever runs — which reads like a build error. Fix the bit, don't work around it.
[ -x ./gradlew ] || chmod +x ./gradlew 2>/dev/null || true

# A worktree also has no local.properties (gitignored); build.sh writes sdk.dir above.

hdr "gradle ${ARGS[*]}"
start=$(date +%s)
if ./gradlew "${ARGS[@]}" 2>&1 | tee "$NEST_CACHE/build.log" \
     | grep -E "^> Task|BUILD|error:|e: |FAILED|Could not (find|resolve)"; then
  :
fi
rc=${PIPESTATUS[0]}
dur=$(( $(date +%s) - start ))

if [ "$rc" -ne 0 ] || [ ! -f "$NEST_APK" ]; then
  bad "build failed in ${dur}s (log: $NEST_CACHE/build.log)"
  grep -E "error:|e: |Could not|FAILED" "$NEST_CACHE/build.log" | head -15
  exit 1
fi
ok "built in ${dur}s → $NEST_APK ($(ls -lh "$NEST_APK" | awk '{print $5}'))"
printf '%s' "$CUR_HASH" > "$stamp"

# Only free the daemons when an emulator is competing for RAM *inside* this VM.
# The condition is answered by nest_daemon_must_stop (lib/common.sh), because
# the naive test — "is ADB_SERVER_SOCKET set?" — is false at this point even
# when the emulator runs on the host: scripts/nest.sh builds BEFORE it boots,
# so the guard used to fire on every run and cost the next build ~21s.
#
# The caller can state the answer up front, before any emulator exists:
#   NEST_EMULATOR_LOCATION=host scripts/dev/build.sh   # or vm
#   NEST_KEEP_DAEMON=1 / NEST_KILL_DAEMON=1
if nest_daemon_must_stop; then
  ./gradlew --stop >/dev/null 2>&1 || true
  ok "gradle daemons stopped (frees RAM for an emulator in this VM)"
else
  say "  keeping gradle daemon warm (emulator is not in this VM)"
fi
