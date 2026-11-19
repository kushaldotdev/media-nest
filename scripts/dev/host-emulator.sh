#!/usr/bin/env bash
# scripts/dev/host-emulator.sh — run the emulator on the HOST OS, drive it from WSL.
#
# Why this exists: inside WSL2 the emulator cannot read its own framebuffer
#    Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma
# so screencap, screenrecord and uiautomator all return nothing. On the host
# OS the GPU path works, and WSL can still build, install and drive it over a
# TCP adb connection. This is the only configuration where you get pixels AND
# a Linux toolchain.
#
#   host-emulator.sh up       start the Windows emulator + a TCP adb server
#   host-emulator.sh down     stop both
#   host-emulator.sh status   what is running, and the socket WSL should use
#
# Env: NEST_WIN_AVD (default nest_hw), NEST_ADB_PORT (5037)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/../lib/common.sh"

AVD="${NEST_WIN_AVD:-nest_hw}"
PORT="${NEST_ADB_PORT:-5037}"

# Windows-side paths. Adjust NEST_WIN_SDK if the SDK lives elsewhere.
WIN_SDK="${NEST_WIN_SDK:-$(wslpath -u "$(cmd.exe /c 'echo %LOCALAPPDATA%' 2>/dev/null | tr -d '\r')" 2>/dev/null)/Android/Sdk}"
WIN_SDK="${WIN_SDK:-/c/Users/$USER/AppData/Local/Android/Sdk}"
EMU="$WIN_SDK/emulator/emulator.exe"
ADBW="$WIN_SDK/platform-tools/adb.exe"

win() { powershell -NoProfile -Command "$1" 2>/dev/null; }

host_ip() { ip route show default | awk '{print $3; exit}'; }

case "${1:-up}" in
  up)
    [ -x "$EMU" ] || die "no host emulator at $EMU (set NEST_WIN_SDK)"
    [ -x "$ADBW" ] || die "no host adb at $ADBW — install platform-tools into the SDK root"
    hdr "host adb server (TCP, all interfaces)"
    "$ADBW" kill-server >/dev/null 2>&1 || true
    win "Start-Process -FilePath '$(wslpath -w "$ADBW")' -ArgumentList '-a','-P','$PORT','nodaemon','server' -WindowStyle Hidden"
    sleep 3
    ok "adb server on 0.0.0.0:$PORT"

    hdr "emulator $AVD"
    if "$ADBW" devices 2>/dev/null | grep -q "emulator-.*device"; then
      ok "already running"
    else
      win "Start-Process -FilePath '$(wslpath -w "$EMU")' -ArgumentList '-avd','$AVD','-gpu','host','-no-snapshot-save','-no-audio' -WorkingDirectory '$(wslpath -w "$WIN_SDK/emulator")'"
      say "  booting (first boot after creation is slowest)..."
      for i in $(seq 1 40); do
        [ "$("$ADBW" -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" = "1" ] && { ok "booted in ~$((i*5))s"; break; }
        sleep 5
      done
    fi

    hdr "WSL side"
    export ADB_SERVER_SOCKET="tcp:$(host_ip):$PORT"
    ok "ADB_SERVER_SOCKET=$ADB_SERVER_SOCKET"
    say "  scripts/nest.sh auto-detects this — no export needed."
    say "  verify:  scripts/android-env-check.sh"
    ;;

  down)
    hdr "stopping"
    [ -x "$ADBW" ] && "$ADBW" emu kill >/dev/null 2>&1
    win "Get-Process emulator,qemu-system-x86_64 -ErrorAction SilentlyContinue | Stop-Process -Force"
    [ -x "$ADBW" ] && "$ADBW" kill-server >/dev/null 2>&1
    ok "stopped"
    ;;

  status)
    hdr "host emulator"
    if [ -x "$ADBW" ]; then
      "$ADBW" devices 2>&1 | tr -d '\r' | sed 's/^/  /'
    else
      warn "no host adb at $ADBW"
    fi
    if "$ADBW" devices 2>/dev/null | grep -q "emulator-.*device"; then
      export ADB_SERVER_SOCKET="tcp:$(host_ip):$PORT"
      if nest_probe_display; then ok "framebuffer readable — screenshots work"
      else warn "framebuffer still unreadable"; fi
    fi
    ;;

  *) sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//' ;;
esac
