#!/usr/bin/env bash
# scripts/dev/install.sh — install the built APK fast, and correctly.
#
#   scripts/dev/install.sh            install if changed (idempotent)
#   scripts/dev/install.sh --force    always reinstall
#   scripts/dev/install.sh --uninstall  remove first (fixes signature mismatch)
#
# Two real pitfalls this encodes:
#  1. This APK is ~196MB with ~103MB of .so. `adb install` (streamed) dies with
#     "Failure calling service package: Broken pipe" — so we push then `pm install`.
#  2. Reinstalling over an APK signed with a different key fails with
#     INSTALL_FAILED_UPDATE_INCOMPATIBLE. --uninstall handles that.
set -uo pipefail
. "$(dirname "$0")/../lib/common.sh"

FORCE=0; UNINSTALL=0
for a in "$@"; do
  case "$a" in --force) FORCE=1 ;; --uninstall) UNINSTALL=1 ;; *) die "unknown arg: $a" ;; esac
done

[ -f "$NEST_APK" ] || die "no APK at $NEST_APK — run scripts/dev/build.sh"
nest_is_booted || die "no booted emulator — run scripts/dev/emulator.sh up"

# Skip if the installed APK is already the current one (compare size+mtime marker).
MARK="$NEST_CACHE/.installed"
sig="$(stat -c '%s-%Y' "$NEST_APK" 2>/dev/null || stat -f '%z-%m' "$NEST_APK")"
if [ "$FORCE" -eq 0 ] && [ "$UNINSTALL" -eq 0 ] && [ -f "$MARK" ] \
   && [ "$(cat "$MARK")" = "$sig" ] && adb shell pm path "$NEST_PKG" >/dev/null 2>&1; then
  ok "already installed ($sig) — nothing to do"
  exit 0
fi

if [ "$UNINSTALL" -eq 1 ]; then
  hdr "uninstalling $NEST_PKG"
  adb uninstall "$NEST_PKG" 2>&1 | tail -1
fi

hdr "installing $(ls -lh "$NEST_APK" | awk '{print $5}')"
start=$(date +%s)
adbt push "$NEST_APK" /data/local/tmp/nest.apk 2>&1 | tail -1
out="$(adb shell pm install -r -t -d /data/local/tmp/nest.apk 2>&1 | tr -d '\r')"
dur=$(( $(date +%s) - start ))

if ! printf '%s' "$out" | grep -q "Success"; then
  bad "install failed in ${dur}s: $out"
  case "$out" in
    *UPDATE_INCOMPATIBLE*) warn "signature mismatch — retry: scripts/dev/install.sh --uninstall" ;;
    *Broken\ pipe*)        warn "streamed-install pipe broke — push+pm path was already used; retry" ;;
  esac
  exit 1
fi
printf '%s' "$sig" > "$MARK"
ok "installed in ${dur}s"
adb shell pm grant "$NEST_PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
