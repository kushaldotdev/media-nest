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

# ── screen-dump cache: one uiautomator capture per unchanged screen ─────────
# A uiautomator dump costs 3.3-7.7s and a repeat flow takes 4+ of them of the
# same settled screen. Cache the last captured XML and reuse it until something
# could have changed the screen.
#
# Two-part key, and both parts matter:
#  - the SESSION id (`$$`, stable for the whole process tree, including the
#    subshells `$(...)` creates) means a cache left by an earlier run is never
#    reused. A screen that a *previous* script saw proves nothing about now, and
#    no caller should have to remember to clear it.
#  - the GENERATION bumps on every input event: `adb shell input`, `am start`,
#    `am force-stop`, `keyevent`. Flows that change the screen with no input
#    event (an app-driven transition) call invalidate_dump explicitly.
# A stale tree would make every later assertion a lie, so invalidation lives in
# one place — nest_input/nest_am — rather than in each flow's memory.
NEST_SESSION="$$"
nest_cache_generation() {
  local g
  g="$(awk '{print $2}' "$NEST_CACHE/.ui-cache.meta" 2>/dev/null)" || g=0
  case "$g" in ''|*[!0-9]*) g=0 ;; esac
  printf '%s\n' "$g"
}
nest_invalidate_dump() {
  printf '%s %s\n' "$NEST_SESSION" "$(( $(nest_cache_generation) + 1 ))" > "$NEST_CACHE/.ui-cache.meta"
  rm -f "$NEST_CACHE/.ui-cache.xml"
}
# Populated by this session, for the current generation?
nest_dump_cache_hit() {
  local sess gen
  read -r sess gen < "$NEST_CACHE/.ui-cache.meta" 2>/dev/null || return 1
  [ "$sess" = "$NEST_SESSION" ] && [ "$gen" = "$(nest_cache_generation)" ] \
    && [ -s "$NEST_CACHE/.ui-cache.xml" ]
}

# Input events go through these, never through a bare `adb shell input`/`am`:
# the invalidation is the whole reason the cache is safe.
nest_input() { nest_invalidate_dump; adb shell input "$@"; }
nest_am()    { nest_invalidate_dump; adb shell am "$@"; }

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
  local settle="${2:-${NEST_SETTLE:-1}}"
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

# ── state assertions (text alone cannot prove a feature worked) ────────────
# Poll until a node appears (or disappears). A fixed sleep is how flows become
# flaky: fast enough on the dev box, too slow on a cold emulator.
#
# `limit` is SECONDS (every caller already assumed it was). Each attempt costs a
# dump (~4s), so the loop checks the deadline after each attempt instead of
# counting a fixed number of attempts — a limit of 20 means ~20s, not 4 dumps
# (~86s) and not one attempt when the number is small.
nest_wait_node() {
  local want="$1" limit="${2:-10}" mode="${3:-present}"
  local deadline=$(( $(date +%s) + limit ))
  while :; do
    if nest_ui_dump "$NEST_UI/wait.xml" 5 1 >/dev/null 2>&1; then
      if grep -qiF -- "$want" "$NEST_UI/wait.xml"; then
        [ "$mode" = present ] && return 0
      else
        [ "$mode" = absent ] && return 0
      fi
    fi
    # Always make one attempt: a 0/1s budget must not skip the check entirely.
    [ "$(date +%s)" -ge "$deadline" ] && return 1
    sleep 1
  done
}

# Assert a persisted value the UI cannot show: a row in the app database, a file
# on disk, a preference. Anything that survives a restart is checkable here, and
# that is what makes "the download actually happened" provable.
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

# ── uiautomator dump (retries through animations) ──────────────────────────
# `fresh` (3rd arg) forces a real capture even when the cache is warm. Polling
# loops pass it: they exist precisely because the screen is still settling, so a
# cached frame would re-assert the state they are waiting to leave.
nest_ui_dump() {
  local out="${1:-$NEST_UI/ui-$(date +%H%M%S).xml}" tries="${2:-5}" fresh="${3:-0}" i=0
  if [ "$fresh" -eq 0 ] && nest_dump_cache_hit; then
    cp "$NEST_CACHE/.ui-cache.xml" "$out" 2>/dev/null && { printf '%s\n' "$out"; return 0; }
  fi
  while [ "$i" -lt "$tries" ]; do
    i=$((i+1))
    adbt shell uiautomator dump /data/local/tmp/.ui.xml >/dev/null 2>&1
    adbt pull /data/local/tmp/.ui.xml "$out" >/dev/null 2>&1
    if [ -s "$out" ] && grep -q "<hierarchy" "$out" 2>/dev/null; then
      cp "$out" "$NEST_CACHE/.ui-cache.xml" 2>/dev/null \
        && printf '%s %s\n' "$NEST_SESSION" "$(nest_cache_generation)" > "$NEST_CACHE/.ui-cache.meta"
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
