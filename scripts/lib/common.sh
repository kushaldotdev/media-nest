#!/usr/bin/env bash
# Shared environment + fast helpers for the MediaNest dev loop.
# Sourced by scripts/nest.sh and scripts/lib/drive.sh. Not meant to be executed.
#
# Design goals, in order:
#   1. A repeat run must not pay for work that has not changed (incremental build,
#      already-running emulator, snapshot boot).
#   2. Every action that could fail leaves a diagnosable artifact.
#   3. Same commands on Linux/WSL and Windows-Git-Bash; no per-machine edits.

set -uo pipefail

# ── paths ───────────────────────────────────────────────────────────────────
NEST_ROOT="${NEST_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
NEST_CACHE="${NEST_CACHE:-$HOME/.cache/medianest}"
NEST_SHOTS="$NEST_CACHE/shots"
NEST_UI="$NEST_CACHE/ui"
mkdir -p "$NEST_CACHE" "$NEST_SHOTS" "$NEST_UI"

# ── Android SDK discovery (first hit wins) ──────────────────────────────────
nest_find_sdk() {
  local c
  for c in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" \
           "$HOME/Android/Sdk" \
           "/c/Users/$USER/AppData/Local/Android/Sdk" \
           "${LOCALAPPDATA:-}/Android/Sdk"; do
    [ -n "$c" ] && [ -x "$c/platform-tools/adb" ] && { printf '%s\n' "$c"; return 0; }
  done
  return 1
}

NEST_SDK="$(nest_find_sdk || true)"
export ANDROID_HOME="${NEST_SDK:-}"; export ANDROID_SDK_ROOT="${NEST_SDK:-}"
if [ -n "$NEST_SDK" ]; then
  export PATH="$NEST_SDK/platform-tools:$NEST_SDK/emulator:$NEST_SDK/cmdline-tools/latest/bin:$PATH"
fi

# ── adb transport: local server, or a host-OS server over TCP ───────────────
# Inside WSL2 you cannot render the emulator (framebuffer readback is broken),
# so the emulator runs on Windows and its adb server is bound to all
# interfaces. Pointing ADB_SERVER_SOCKET at it makes every command below work
# unchanged — emulator or a physical phone plugged into Windows.
#
#   export NEST_ADB_SERVER=172.29.208.1:5037      # or let it auto-detect
nest_bridge_host_adb() {
  [ -n "${ADB_SERVER_SOCKET:-}" ] && return 0
  local cand="${NEST_ADB_SERVER:-}"
  if [ -z "$cand" ]; then
    local gw
    gw="$(ip route show default 2>/dev/null | awk '{print $3; exit}')"
    [ -n "$gw" ] || return 1
    cand="$gw:5037"
  fi
  local host="${cand%:*}" port="${cand#*:}"
  # only adopt it if something is actually listening there
  timeout 3 bash -c "cat < /dev/null > /dev/tcp/$host/$port" 2>/dev/null || return 1
  export ADB_SERVER_SOCKET="tcp:$cand"
}
# Only meaningful under WSL; harmless elsewhere (no gateway match, no port).
case "$(uname -r)" in *microsoft*|*WSL*) nest_bridge_host_adb || true ;; esac

# ── project facts (kept in one place so a rename is a one-line change) ──────
NEST_PKG="${NEST_PKG:-com.example.medianest.debug}"      # debug applicationIdSuffix
NEST_ACT="${NEST_ACT:-com.example.medianest.MainActivity}"
NEST_AVD="${NEST_AVD:-nest_api36}"
NEST_RAM="${NEST_RAM:-4096}"
NEST_APK="$NEST_ROOT/app/build/outputs/apk/debug/app-debug.apk"

# ── logging ─────────────────────────────────────────────────────────────────
if [ -t 1 ]; then
  C_R=$'\033[31m'; C_G=$'\033[32m'; C_Y=$'\033[33m'; C_B=$'\033[1m'; C_0=$'\033[0m'
else
  C_R=''; C_G=''; C_Y=''; C_B=''; C_0=''
fi
say()  { printf '%s\n' "$*"; }
hdr()  { printf '\n%s▶ %s%s\n' "$C_B" "$*" "$C_0"; }
ok()   { printf '  %s✓%s %s\n' "$C_G" "$C_0" "$*"; }
warn() { printf '  %s!%s %s\n' "$C_Y" "$C_0" "$*"; }
bad()  { printf '  %s✗%s %s\n' "$C_R" "$C_0" "$*"; }
die()  { bad "$*"; exit 1; }

# ── adb shims (strip CR; never hang forever) ────────────────────────────────
sh_() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
adbt() { timeout "${NEST_ADB_TIMEOUT:-45}" adb "$@"; }

nest_device_serial() {
  # any ready device: an emulator here, or a physical phone bridged from Windows
  adb devices 2>/dev/null | awk '$2=="device"{print $1; exit}'
}
nest_is_booted() {
  [ -n "$(nest_device_serial)" ] && [ "$(sh_ getprop sys.boot_completed)" = "1" ]
}
# Hardware device (not an emulator) — installed APKs there are the real thing.
nest_is_physical() {
  case "$(nest_device_serial)" in emulator-*) return 1 ;; "") return 1 ;; *) return 0 ;; esac
}

# ── the display bug, probed once and cached ────────────────────────────────
# On this WSL host every framebuffer read returns nothing:
#   "Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma"
# Detect it rather than letting each screenshot fail mysteriously.
NEST_DISPLAY_WORKS=""
nest_probe_display() {
  [ -n "$NEST_DISPLAY_WORKS" ] && { [ "$NEST_DISPLAY_WORKS" = 1 ]; return; }
  local p="$NEST_CACHE/.display-probe"
  adbt shell screencap -p /data/local/tmp/.probe.png >/dev/null 2>&1
  adbt pull /data/local/tmp/.probe.png "$p" >/dev/null 2>&1
  if [ -s "$p" ] && [ "$(wc -c <"$p")" -gt 10000 ]; then
    NEST_DISPLAY_WORKS=1
  else
    NEST_DISPLAY_WORKS=0
  fi
  [ "$NEST_DISPLAY_WORKS" = 1 ]
}

# ── screenshot: fast path then fallback ────────────────────────────────────
# exec-out is fastest but corrupts binary on some adb builds; device-file+pull
# is slower but reliable. Try both, keep whichever produces a real PNG.
#
# The emulator also serves a STALE composited frame if you capture immediately
# after a tap, so settle first and keep the second capture — otherwise the
# screenshot shows the previous screen while the UI tree already shows the new
# one, and you "verify" the wrong thing.
nest_screenshot() {
  local out="${1:-$NEST_SHOTS/$(date +%H%M%S).png}"
  local settle="${2:-${NEST_SETTLE:-2}}"
  mkdir -p "$(dirname "$out")"
  sleep "$settle"
  adbt shell screencap -p /data/local/tmp/.warm.png >/dev/null 2>&1   # discard: stale
  adbt shell screencap -p /data/local/tmp/.shot.png >/dev/null 2>&1
  adbt pull /data/local/tmp/.shot.png "$out" >/dev/null 2>&1
  if [ -s "$out" ] && [ "$(wc -c <"$out")" -gt 10000 ]; then
    printf '%s\n' "$out"; return 0
  fi
  rm -f "$out"
  adb exec-out screencap -p > "$out" 2>/dev/null
  if [ -s "$out" ] && [ "$(wc -c <"$out")" -gt 10000 ]; then
    printf '%s\n' "$out"; return 0
  fi
  rm -f "$out"; return 1
}

# ── uiautomator dump (retries through animations) ──────────────────────────
nest_ui_dump() {
  local out="${1:-$NEST_UI/ui-$(date +%H%M%S).xml}" i=0
  while [ "$i" -lt "${2:-5}" ]; do
    i=$((i+1))
    adbt shell uiautomator dump /data/local/tmp/.ui.xml >/dev/null 2>&1
    adbt pull /data/local/tmp/.ui.xml "$out" >/dev/null 2>&1
    if [ -s "$out" ] && grep -q "<hierarchy" "$out" 2>/dev/null; then
      printf '%s\n' "$out"; return 0
    fi
    sleep 1
  done
  return 1
}

# ── crash gate ─────────────────────────────────────────────────────────────
nest_crashes() {
  adb logcat -d 2>/dev/null \
    | grep -E "FATAL EXCEPTION|AndroidRuntime.*Process: ${NEST_PKG}|ANR in ${NEST_PKG}" || true
}

nest_clean_logcat() {
  local hits; hits="$(nest_crashes)"
  if [ -n "$hits" ]; then
    printf '%s\n' "$hits" > "$NEST_CACHE/last-crashes.txt"
    bad "crash detected → $NEST_CACHE/last-crashes.txt"
    printf '%s\n' "$hits" | head -20
    return 1
  fi
  ok "logcat clean"
}
