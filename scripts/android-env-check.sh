#!/usr/bin/env bash
# scripts/android-env-check.sh — read-only readiness probe. Changes nothing.
#
# Answers, in one shot: can this machine build and drive the app right now, and
# if not, what exactly is missing? Safe to run repeatedly.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib/common.sh"

row() { printf '  %-20s %s\n' "$1" "$2"; }

hdr "Host"
row "kernel"  "$(uname -sr)"
row "arch"    "$(uname -m)"
row "cpu"     "$(nproc) cores"
row "distro"  "$(. /etc/os-release 2>/dev/null && echo "$PRETTY_NAME")"

hdr "KVM (the gate for emulation)"
if [ -e /dev/kvm ]; then
  if python3 -c "import os; os.open('/dev/kvm', os.O_RDWR)" 2>/dev/null; then
    ok "/dev/kvm openable"
  else
    bad "/dev/kvm present but NOT openable (mode $(stat -c %a /dev/kvm 2>/dev/null))"
    say "      fix: sudo usermod -aG kvm \$USER  &&  re-login"
    say "      or from Windows: wsl -u root -- usermod -aG kvm \$USER"
  fi
else
  warn "/dev/kvm missing — emulator will not accelerate (macOS/Windows hosts unaffected)"
fi

hdr "Java"
if command -v java >/dev/null; then
  row "version" "$(java -version 2>&1 | head -1)"
else
  bad "java not found → sudo apt install -y openjdk-21-jdk"
fi

hdr "Android SDK"
if [ -n "$NEST_SDK" ]; then
  ok "$NEST_SDK"
  for d in platform-tools platforms build-tools emulator cmdline-tools/latest/bin; do
    [ -e "$NEST_SDK/$d" ] && row "$d" "present" || bad "$d MISSING"
  done
  for p in "$NEST_SDK"/platforms/*; do [ -d "$p" ] && row "platform" "$(basename "$p")"; done
  for s in "$NEST_SDK"/system-images/*/*/*; do [ -d "$s" ] && row "image" "${s#$NEST_SDK/system-images/}"; done
  row "avds" "$(emulator -list-avds 2>/dev/null | tr '\n' ' ')"
  row "accel" "$(emulator -accel-check 2>&1 | head -1)"
else
  bad "no SDK found (looked at ANDROID_HOME, ~/Android/Sdk, %LOCALAPPDATA%/Android/Sdk)"
fi

hdr "Emulator"
if nest_is_booted; then
  ok "booted: $(nest_device_serial)  API $(sh_ getprop ro.build.version.sdk)"
  if nest_probe_display; then ok "framebuffer readable (screenshots work)"
  else warn "framebuffer NOT readable — gfxstream 'hasReadColorBufferDma'; see docs/TESTING.md"; fi
else
  warn "not running — scripts/dev/emulator.sh up"
fi

hdr "Project"
row "root" "$NEST_ROOT"
row "apk"  "$([ -f "$NEST_APK" ] && ls -lh "$NEST_APK" | awk '{print $5" "$6" "$7" "$8}' || echo 'not built')"
row "pkg"  "$NEST_PKG"
command -v scrcpy >/dev/null && row "scrcpy" "$(scrcpy --version 2>/dev/null | head -1)" \
                           || warn "scrcpy absent (optional, for live view)"

hdr "Resources"
row "mem"  "$(free -h 2>/dev/null | awk '/^Mem:/{print $2" total, "$7" available"}')"
row "disk" "$(df -h "$NEST_ROOT" | awk 'NR==2{print $4" free"}')"
row "heaps" "$(grep -hoE '\-Xmx[0-9]+m' "$NEST_ROOT/gradle.properties" 2>/dev/null | tr '\n' ' ')"
