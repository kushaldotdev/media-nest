#!/usr/bin/env bash
# scripts/dev/emulator.sh — fast, idempotent emulator control.
#
#   scripts/dev/emulator.sh up        boot (snapshot if available, else cold) — idempotent
#   scripts/dev/emulator.sh down      stop it
#   scripts/dev/emulator.sh snap      save a snapshot so the NEXT `up` is ~5s not ~2min
#   scripts/dev/emulator.sh status    what's running
#   scripts/dev/emulator.sh recreate  wipe the AVD and rebuild from the system image
#
# Speed notes:
#  - `up` is a no-op if a booted device is already attached (most common case).
#  - Snapshot boot skips a full Android cold start; `-no-snapshot-load` forces cold.
#  - A failed display probe is reported once, not per screenshot.
set -uo pipefail
. "$(dirname "$0")/../lib/common.sh"

ACTION="${1:-up}"
EMU_OPTS=(-avd "$NEST_AVD" -no-window -no-audio -no-boot-anim
          -gpu swiftshader_indirect -memory "$NEST_RAM" -partition-size 6144
          -netdelay none -netspeed full)

wait_boot() {
  local limit="${1:-180}" i=0
  adb start-server >/dev/null 2>&1
  adb wait-for-device
  printf 'waiting for boot'
  until [ "$(sh_ getprop sys.boot_completed)" = "1" ]; do
    sleep 2; i=$((i+2)); printf '.'
    [ "$i" -ge "$limit" ] && { echo; bad "boot timeout (${limit}s) → /tmp/emulator-$NEST_AVD.log"; return 1; }
  done
  echo
  # pm must answer before installs/taps are meaningful
  until adb shell pm path android >/dev/null 2>&1; do sleep 1; done
  # animations off = uiautomator stops failing with "could not get idle state"
  for s in window transition animator; do
    adb shell settings put global ${s}_animation_scale 0 >/dev/null 2>&1
  done
  adb shell input keyevent 82 >/dev/null 2>&1   # dismiss keyguard
  ok "booted $(nest_device_serial)"
}

case "$ACTION" in
  up)
    if nest_is_booted; then
      ok "already booted ($(nest_device_serial)) — nothing to do"
      exit 0
    fi
    if [ -n "$(nest_device_serial)" ]; then
      hdr "device present but not booted; waiting"
      wait_boot 180 && exit 0
    fi
    hdr "booting $NEST_AVD"
    # KVM gate: present != openable
    python3 -c "import os; os.open('/dev/kvm', os.O_RDWR)" 2>/dev/null \
      || die "/dev/kvm not openable → sudo usermod -aG kvm \$USER, then re-login"
    setsid nohup emulator "${EMU_OPTS[@]}" > "/tmp/emulator-$NEST_AVD.log" 2>&1 < /dev/null &
    disown 2>/dev/null || true
    wait_boot 240
    nest_probe_display && ok "framebuffer readable" \
      || warn "framebuffer NOT readable — screenshots will fail; see docs/TESTING.md (headless WSL: use host-side emulator)"
    ;;
  down)
    hdr "stopping emulator"
    adb emu kill 2>/dev/null || true
    sleep 3
    pkill -f "qemu-system-x86_64.*$NEST_AVD" 2>/dev/null || true
    ok "stopped"
    ;;
  snap)
    hdr "saving snapshot (makes the next 'up' fast)"
    adb emu avd snapshot save nest-ready 2>&1 | tail -2
    ok "snapshot 'nest-ready' saved"
    ;;
  status)
    hdr "emulator status"
    say "  serial : $(nest_device_serial || echo none)"
    say "  booted : $(sh_ getprop sys.boot_completed 2>/dev/null || echo -)"
    say "  sdk    : $(sh_ getprop ro.build.version.sdk 2>/dev/null || echo -)"
    # The device may be the host-OS emulator (ADB_SERVER_SOCKET set), not a
    # local AVD — reporting $NEST_AVD here would be a lie.
    say "  avd    : $(sh_ getprop ro.boot.qemu.avd_name 2>/dev/null || echo unknown)"
    say "  model  : $(sh_ getprop ro.product.model)"
    if [ -n "${ADB_SERVER_SOCKET:-}" ]; then
      say "  adb    : $ADB_SERVER_SOCKET (host-OS emulator)"
    else
      say "  adb    : local ($(nest_device_serial))"
    fi
    say "  apk    : $([ -f "$NEST_APK" ] && ls -lh "$NEST_APK" | awk '{print $5" "$6" "$7" "$8}' || echo 'not built')"
    if [ -z "${ADB_SERVER_SOCKET:-}" ]; then say "  log    : /tmp/emulator-$NEST_AVD.log"; fi
    ;;
  recreate)
    hdr "recreating $NEST_AVD"
    adb emu kill 2>/dev/null || true; sleep 3
    pkill -f qemu-system-x86_64 2>/dev/null || true; sleep 2
    avdmanager delete avd -n "$NEST_AVD" >/dev/null 2>&1 || true
    echo no | avdmanager create avd -n "$NEST_AVD" -k "system-images;android-36.1;google_apis;x86_64" \
      -d pixel_7 --force >/dev/null || die "avdmanager failed"
    CFG="$HOME/.android/avd/$NEST_AVD.avd/config.ini"
    # NOTE: never set hw.gpu.enabled=no here — it fights -gpu on the CLI and the
    # guest loses its display stack entirely.
    sed -i '/^hw\.gpu\./d' "$CFG" 2>/dev/null || true
    ok "recreated; run 'up'"
    ;;
  *) die "usage: emulator.sh {up|down|snap|status|recreate}" ;;
esac
