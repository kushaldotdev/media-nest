#!/usr/bin/env bash
# Shared environment + fast helpers for the MediaNest dev loop.
# Sourced by scripts/nest.sh and scripts/dev/*.sh. Not meant to be executed.
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
mkdir -p "$NEST_CACHE"

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
nest_is_wsl() {
  case "$(uname -r)" in *microsoft*|*WSL*) return 0 ;; *) return 1 ;; esac
}
# Only meaningful under WSL; harmless elsewhere (no gateway match, no port).
if nest_is_wsl; then nest_bridge_host_adb || true; fi

# ── where the emulator lives, and the Gradle daemon policy that follows ────
# The daemon (6GB) + Kotlin daemon (3GB) + emulator (4GB) oversubscribe 16GB, so
# a daemon must be stopped before booting an emulator *inside this VM*. It must
# NOT be stopped when the emulator runs somewhere else (host-OS emulator, a
# phone bridged over TCP): nothing competes, and a cold daemon costs ~21s of JVM
# start plus configuration-cache deserialisation on every later build.
#
# The guard this replaces tested `ADB_SERVER_SOCKET`, which scripts/nest.sh
# cannot have set at that point — it builds BEFORE it boots the emulator — so the
# daemon was killed on every normal run. Answer the actual question instead: is
# there a qemu emulator in this VM, or will nest.sh boot one here?
nest_emulator_in_vm() {
  # Match the qemu process by exact comm, never `pgrep -f`: a full-command-line
  # match also hits the shell that is running this function.
  local n
  for n in qemu-system-x86_64 qemu-system-x86_64-headless qemu-system-aarch64; do
    pgrep -x "$n" >/dev/null 2>&1 && return 0
  done
  return 1
}
# host = emulator on the host OS / a bridged device; vm = an AVD boots here.
nest_emulator_location() {
  [ -n "${NEST_EMULATOR_LOCATION:-}" ] && { printf '%s\n' "$NEST_EMULATOR_LOCATION"; return; }
  # emulator.sh boots on the host whenever this is WSL (guest readback is broken).
  if nest_is_wsl; then printf 'host\n'; return; fi
  if nest_emulator_in_vm; then printf 'vm\n'; return; fi
  if [ -n "${ADB_SERVER_SOCKET:-}" ]; then printf 'host\n'; return; fi
  printf 'vm\n'
}
# True when the daemon should be stopped. Explicit overrides win, so a caller can
# state the answer before the emulator step has run: NEST_KEEP_DAEMON=1 /
# NEST_KILL_DAEMON=1.
nest_daemon_must_stop() {
  case "${NEST_KILL_DAEMON:-}" in 1|true|yes) return 0 ;; esac
  case "${NEST_KEEP_DAEMON:-}" in 1|true|yes) return 1 ;; esac
  [ "$(nest_emulator_location)" = vm ]
}

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

# Assert a persisted value the UI cannot show: a row in the app database, a file
# on disk, a preference. Anything that survives a restart is checkable here.
#   nest_assert_shell "download file exists" "ls /sdcard/Download"
#   nest_assert_shell "db has a songs row" "sqlite3 /data/data/$NEST_PKG/databases/x.db 'select 1'"
nest_assert_shell() {
  local what="$1" cmd="$2" out
  out="$(sh_ "$cmd" 2>/dev/null)"
  if [ -n "$out" ]; then
    printf '  \033[32m✓\033[0m %s: %s\n' "$what" "$(printf '%s' "$out" | head -1)"
    return 0
  fi
  printf '  \033[31m✗\033[0m %s (command produced nothing)\n' "$what"
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
