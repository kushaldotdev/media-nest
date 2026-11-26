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
#   host-emulator.sh warm     is a quickboot snapshot cached, i.e. will 'up' be fast?
#
# Env: NEST_WIN_AVD (default nest_hw), NEST_ADB_PORT (5037)
#
# Speed: quickboot is left enabled on purpose. There is no `-no-snapshot-save`, so
# the emulator writes its state on exit and the next start resumes it (~5s instead
# of a full ~55s cold boot). `warm` reports whether that snapshot exists.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/../lib/common.sh"

AVD="${NEST_WIN_AVD:-nest_hw}"
PORT="${NEST_ADB_PORT:-5037}"

# Windows-side paths. This WSL has `appendWindowsPath=false`, so cmd.exe/
# powershell.exe are NOT on PATH and `<name>.exe` fails to exec — call them by
# absolute path. Interop also refuses a UNC working directory, so every Windows
# call runs from /mnt/c (see winpath/win).
WINROOT=/mnt/c/Windows/System32
CMDW="$WINROOT/cmd.exe"
PSW="$WINROOT/WindowsPowerShell/v1.0/powershell.exe"

win_ok() { [ -x "$CMDW" ] && [ -x "$PSW" ]; }

# Only usable from a drvfs path; from a UNC cwd Windows refuses to start.
win() { ( cd /mnt/c && "$PSW" -NoProfile -Command "$1" ) 2>/dev/null; }
winpath() { ( cd /mnt/c && wslpath -w "$1" ); }

if win_ok; then
  :
else
  warn "Windows interop not reachable at $WINROOT — cannot boot a host emulator"
fi

WIN_SDK="${NEST_WIN_SDK:-$(win 'echo $env:LOCALAPPDATA')}"
[ -n "$WIN_SDK" ] && WIN_SDK="$(wslpath -u "$(printf '%s' "$WIN_SDK" | tr -d '\r')" 2>/dev/null)/Android/Sdk"
WIN_SDK="${WIN_SDK:-/mnt/c/Users/$USER/AppData/Local/Android/Sdk}"
EMU="$WIN_SDK/emulator/emulator.exe"
ADBW="$WIN_SDK/platform-tools/adb.exe"

host_ip() { ip route show default | awk '{print $3; exit}'; }

# Where the host-side AVD keeps its quickboot snapshot. Same $LOCALAPPDATA the
# SDK is found under, so this follows the same resolution order.
HOST_AVD_DIR="$(printf '%s' "$WIN_SDK" | sed 's|/AppData/Local/Android/Sdk$|/.android/avd|')/$AVD.avd"
HOST_SNAP="$HOST_AVD_DIR/snapshots/default_boot"

# A snapshot is only usable if it finished: the emulator writes snapshot.pb plus
# a ram.img of the guest's memory. A directory left behind by a killed emulator
# has neither, and claiming "warm" then would be a lie.
snap_ready() {
  [ -d "$HOST_SNAP" ] || return 1
  local pb
  pb="$(find "$HOST_SNAP" -maxdepth 1 -name 'snapshot.pb' -size +1k 2>/dev/null | head -1)"
  [ -n "$pb" ] && find "$HOST_SNAP" -maxdepth 1 -name 'ram.img' -size +10M 2>/dev/null | head -1 | grep -q .
}

# Animations off is what keeps uiautomator dumps complete: with them on, a dump
# taken mid-transition returns a PARTIAL tree (no nav bar), which reads exactly
# like "the control is missing". The local path in emulator.sh already does
# this; the host path never did, and scales measured 1.0 here.
#
# Ordering matters for quickboot: do this BEFORE any snapshot is saved, or the
# animation-on state is baked into the snapshot and every later boot restores
# it — the optimisation would silently undo itself.
host_animations_off() {
  local s
  for s in window transition animator; do
    "$ADBW" -s emulator-5554 shell settings put global ${s}_animation_scale 0 >/dev/null 2>&1 || true
  done
  ok "animations off (window/transition/animator scale = 0)"
}

# Always keep the emulator window cleanly inside the left side of the primary
# Windows monitor (DPI-aware so 125%/150% Windows scaling never clips it).
host_position_window() {
  win '
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class NestWin {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr hWnd, int X, int Y, int nWidth, int nHeight, bool bRepaint);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
  public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
[NestWin]::SetProcessDPIAware() | Out-Null
Add-Type -AssemblyName System.Windows.Forms
$wa = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$moved = $false
Get-Process | Where-Object { $_.ProcessName -match "qemu|emulator" -and $_.MainWindowTitle -like "*Android Emulator*" -and $_.MainWindowHandle -ne [IntPtr]::Zero } | ForEach-Object {
  $r = New-Object NestWin+RECT
  [NestWin]::GetWindowRect($_.MainWindowHandle, [ref]$r) | Out-Null
  $w = $r.Right - $r.Left
  $h = $r.Bottom - $r.Top
  if ($w -lt 200 -or $h -lt 300) { return }
  $maxH = [Math]::Min(920, $wa.Height - 40)
  if ($h -gt $maxH) {
    $w = [int]([Math]::Round($w * ($maxH / [double]$h)))
    $h = $maxH
  }
  $x = $wa.Left + 16
  $y = $wa.Top + 20
  [NestWin]::MoveWindow($_.MainWindowHandle, $x, $y, $w, $h, $true) | Out-Null
  $moved = $true
}
if (-not $moved) { exit 1 }
'
}

case "${1:-up}" in
  up)
    [ -x "$EMU" ] || die "no host emulator at $EMU (set NEST_WIN_SDK)"
    [ -x "$ADBW" ] || die "no host adb at $ADBW — install platform-tools into the SDK root"
    hdr "host adb server (TCP, all interfaces)"
    "$ADBW" kill-server >/dev/null 2>&1 || true
    win "Start-Process -FilePath '$(winpath "$ADBW")' -ArgumentList '-a','-P','$PORT','nodaemon','server' -WindowStyle Hidden"
    sleep 3
    ok "adb server on 0.0.0.0:$PORT"

    hdr "emulator $AVD"
    if "$ADBW" devices 2>/dev/null | grep -q "emulator-.*device"; then
      ok "already running"
      # A device that is attached but has not finished booting must still get its
      # animation scales set before anything drives it.
      host_animations_off
      host_position_window || true
    else
      if snap_ready; then say "  quickboot snapshot present — resuming (fast)"
      else say "  no snapshot — cold boot (first boot after creation is slowest)"; fi
      # Keep window on the left side with a scale that fits inside 1080p @ 125% DPI.
      if [ -f "$HOST_AVD_DIR/emulator-user.ini" ]; then
        sed -i -E 's/^window\.x = .*/window.x = 16/; s/^window\.y = .*/window.y = 20/; s/^window\.scale = .*/window.scale = 0.34/' "$HOST_AVD_DIR/emulator-user.ini" 2>/dev/null || true
      fi
      # Suppress the compatibility warning dialog via QSettings registry key (avoiding
      # ANDROID_EMU_SKIP_SYSTEM_CHECKS=1 which triggers its own "checks disabled" popup).
      win "\$reg = 'HKCU:\Software\Android Open Source Project\Emulator'; if (-not (Test-Path \$reg)) { New-Item -Path \$reg -Force | Out-Null }; Set-ItemProperty -Path \$reg -Name 'showCompatibilityWarning_$AVD' -Value 'false' -Type String"
      # NOTE: no -no-snapshot-save, and no -no-snapshot-load. The emulator must be
      # free to restore a snapshot on start and to save one on exit; suppressing
      # either makes every boot the full ~55s. Do not add them back.
      win "Start-Process -FilePath '$(winpath "$EMU")' -ArgumentList '-avd','$AVD','-gpu','host','-no-audio','-no-nested-warnings' -WorkingDirectory '$(winpath "$WIN_SDK/emulator")'"
      say "  booting (first boot after creation is slowest)..."
      positioned=0
      for i in $(seq 1 60); do
        if [ "$positioned" -eq 0 ]; then
          host_position_window && positioned=1
        fi
        [ "$("$ADBW" -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" = "1" ] && { ok "booted in ~$((i*5))s"; break; }
        sleep 5
      done
      # Only now: the guest is up, so the settings stick and the snapshot that
      # `down` will write carries the animation-off state.
      host_animations_off
      host_position_window || true
    fi

    hdr "WSL side"
    export ADB_SERVER_SOCKET="tcp:$(host_ip):$PORT"
    ok "ADB_SERVER_SOCKET=$ADB_SERVER_SOCKET"
    say "  scripts/nest.sh auto-detects this — no export needed."
    say "  verify:  scripts/android-env-check.sh"
    ;;

  down)
    hdr "stopping"
    [ -x "$ADBW" ] && "$ADBW" -s emulator-5554 emu kill >/dev/null 2>&1 || true
    # Wait up to 10s for a clean Qt/QEMU exit so quickboot + window prefs save.
    win '
$deadline = (Get-Date).AddSeconds(10)
while ((Get-Date) -lt $deadline) {
  if (-not (Get-Process emulator,qemu-system-x86_64 -ErrorAction SilentlyContinue)) { break }
  Start-Sleep -Milliseconds 500
}
Get-Process emulator,qemu-system-x86_64 -ErrorAction SilentlyContinue | Stop-Process -Force
'
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

  warm)
    hdr "host emulator quickboot"
    say "  avd      : $AVD"
    say "  snapshot : $HOST_SNAP"
    if snap_ready; then
      ok "snapshot present — the next 'up' resumes instead of cold booting (~5s not ~55s)"
    else
      warn "no usable snapshot — the next 'up' is a full cold boot (~55s)"
      say "  created automatically when the emulator exits cleanly (no -no-snapshot-save)."
      say "  force one now: adb emu avd snapshot save default_boot"
    fi
    ;;

  *) sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//' ;;
esac
