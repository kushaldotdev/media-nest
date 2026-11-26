#!/usr/bin/env bash
# scripts/phone.sh — install and test MediaNest on a PHYSICAL phone from WSL.
# No Android Studio, no adb on Windows required (though a Windows adb server is
# the easiest USB route — see below).
#
#   scripts/phone.sh list                  devices adb can see right now
#   scripts/phone.sh wifi <ip[:port]>       connect over Wi-Fi (Android 11+ pairing)
#   scripts/phone.sh pair <ip:port> <code>  wireless-debugging pairing step
#   scripts/phone.sh usb                   explain/verify the USB routes
#   scripts/phone.sh install               build (if needed) + install to the phone
#   scripts/phone.sh use <serial>          pin one device when several are attached
#
# Once connected and installed, drive the phone with `agent-device`.
#
# ── Wi-Fi (wireless debugging) ─────────────────────────────────────────────
#   Phone: Settings → Developer options → Wireless debugging → ON
#          "Pair device with pairing code" → note IP:PORT and the 6-digit code
#   WSL:   scripts/phone.sh pair 192.168.1.42:37713 123456
#          scripts/phone.sh wifi 192.168.1.42:5555
#   WSL2 reaches the LAN directly, so this needs no Windows help.
#
# ── USB ────────────────────────────────────────────────────────────────────
#   WSL2 has no direct USB access. Two working options:
#   (a) easiest — let WINDOWS own the phone, WSL borrows its adb server:
#         plug in the phone, enable USB debugging, accept the RSA prompt
#         scripts/dev/host-emulator.sh up      # starts adb server on TCP
#         scripts/phone.sh list                # phone appears
#       Nothing else installed; the APK still builds in WSL.
#   (b) native — forward the USB device into WSL (needs usbipd-win + admin):
#         winget install usbipd.win
#         usbipd list && usbipd bind --busid <id> && usbipd attach --wsl --busid <id>
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib/common.sh"

pin() { [ -n "${NEST_SERIAL:-}" ] && printf '%s' "-s $NEST_SERIAL"; }

case "${1:-list}" in
  list)
    hdr "devices"
    adb devices -l 2>&1 | tr -d '\r' | sed 's/^/  /'
    if nest_is_booted; then
      ok "using: $(nest_device_serial)  $(if nest_is_physical; then echo '(physical)'; else echo '(emulator)'; fi)"
      row_abi="$(sh_ getprop ro.product.cpu.abi)"
      say "  abi:  $row_abi"
      say "  sdk:  $(sh_ getprop ro.build.version.sdk)   model: $(sh_ getprop ro.product.model)"
      say "  app:  $(sh_ pm list packages | grep -c medianest) medianest package(s) installed"
    else
      warn "no device ready"
    fi
    ;;

  pair)
    p="${2:?usage: phone.sh pair <ip:port> <code>}"; c="${3:?usage: phone.sh pair <ip:port> <code>}"
    hdr "pairing $p"
    adb pair "$p" "$c"
    ;;

  wifi)
    t="${2:?usage: phone.sh wifi <ip[:port]>}"
    case "$t" in *:*) ;; *) t="$t:5555" ;; esac
    hdr "connect $t"
    adb connect "$t"
    sleep 1
    adb devices | tr -d '\r' | sed 's/^/  /'
    ;;

  usb)
    hdr "USB options"
    say "  (a) Windows owns the phone, WSL borrows adb — no extra install:"
    say "      scripts/dev/host-emulator.sh up   # binds adb server on TCP"
    say "      scripts/phone.sh list"
    say ""
    say "  (b) forward USB into WSL (needs admin once):"
    say "      winget install usbipd.win"
    say "      usbipd list"
    say "      usbipd bind --busid <id>"
    say "      usbipd attach --wsl --busid <id>"
    say ""
    if command -v usbipd.exe >/dev/null 2>&1; then
      hdr "usbipd devices"; usbipd.exe list 2>&1 | tr -d '\r' | sed 's/^/  /'
    else
      warn "usbipd-win not installed (optional)"
    fi
    ;;

  use)
    export NEST_SERIAL="${2:?usage: phone.sh use <serial>}"
    ok "pinned serial $NEST_SERIAL (export it to keep it for later calls)"
    ;;

  install)
    "$HERE/dev/build.sh" || exit 1
    nest_is_booted || die "no device — see: scripts/phone.sh usb"
    hdr "install to $(nest_device_serial)"
    # Large APK + physical device: push then pm install is far more reliable
    # than a streamed install.
    adb push "$NEST_APK" /data/local/tmp/nest.apk 2>&1 | tail -1
    adb shell pm install -r -t -d /data/local/tmp/nest.apk
    ok "installed"
    ;;

  *) sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//' ;;
esac
