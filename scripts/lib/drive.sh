#!/usr/bin/env bash
# Flow helpers for scripts/nest.sh and scripts/verify.sh.
# Sourced AFTER scripts/lib/common.sh (which provides adb shims, paths, logging).
#
# Every helper that acts leaves evidence. A tap alone is never "passed": `snap`
# records pixels (or explicitly reports the framebuffer is unreadable), and
# `ck_logcat` fails the run on a crash.

set -uo pipefail
: "${ART:=$NEST_CACHE/session-$(date +%H%M%S)}"
mkdir -p "$ART/screens" "$ART/ui"
STEP="${STEP:-0}"
FAILED="${FAILED:-0}"

step() { STEP=$((STEP+1)); }
snap_name() { printf '%02d-%s' "$STEP" "${1:-step}"; }

# Screenshot into $ART/screens. Returns 0 and prints the path; returns 1 when the
# framebuffer cannot be read at all (headless-WSL gfxstream limitation). Callers
# must NOT treat the failure as "the screen looked fine".
snap() {
  local label="${1:-step}" out
  step
  out="$ART/screens/$(snap_name "$label").png"
  if nest_screenshot "$out"; then
    ok "screenshot $(basename "$out")"
    printf '%s\n' "$out"
    return 0
  fi
  warn "no pixels: $(basename "$out") (framebuffer readback broken)"
  printf '%s\n' "$ART/screens/NO-PIXELS-$label.png"
  return 1
}

# Centre of the first node matching text or content-desc. Shared with poke.sh so
# the label-matching logic exists exactly once.
# uiautomator occasionally returns a PARTIAL tree (nav bar missing) right after a
# transition, so retry with a fresh dump before declaring the node absent.
find_xy() {
  local want="$1" xml i
  for i in 1 2 3; do
    xml="$(nest_ui_dump "$NEST_UI/find.xml")" || { sleep 1; continue; }
    _xy_of "$xml" "$want" && return 0
    sleep 1
  done
  return 1
}

_xy_of() {
  local xml="$1" want="$2"
  python3 - "$xml" "$want" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, want = sys.argv[1], sys.argv[2].strip().lower()
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)
# ElementTree elements with no children are FALSY, so leaf nodes (exactly the
# ones that carry text/content-desc) must be compared with `is not None` —
# `exact = exact or n` silently discards every leaf and finds nothing.
exact = None
sub = None
for n in root.iter('node'):
    for attr in ('text', 'content-desc'):
        v = (n.get(attr) or '').strip().lower()
        if not v:
            continue
        if v == want and exact is None:
            exact = n
        elif want in v and sub is None:
            sub = n
node = exact if exact is not None else sub
if node is None:
    sys.exit(1)
m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds') or '')
if not m:
    sys.exit(1)
x1, y1, x2, y2 = map(int, m.groups())
print((x1 + x2) // 2, (y1 + y2) // 2)
PY
}

dump_ui() {
  local label="${1:-step}" out
  step
  out="$ART/ui/$(snap_name "$label").xml"
  nest_ui_dump "$out" >/dev/null && { printf '%s\n' "$out"; return 0; }
  bad "ui dump failed ($label)"
  return 1
}

tap_label() {
  local want="$1" xy
  step
  xy="$(find_xy "$want")" || { bad "no node matching \"$want\" (try: scripts/dev/poke.sh ui)"; return 1; }
  adb shell input tap $xy
  sleep 1
  ok "tapped \"$want\" @ $xy"
}

assert_text() {
  local want="$1" label="${2:-assert}" xml
  step
  xml="$ART/ui/$(snap_name "$label").xml"
  if nest_ui_dump "$xml" >/dev/null && grep -qiF -- "$want" "$xml"; then
    ok "visible: \"$want\""; return 0
  fi
  bad "NOT visible: \"$want\""
  return 1
}

# System dialogs ("System UI isn't responding", app ANR sheets) sit on top of
# the app and make every later assertion lie: the window focus is still the
# app, but uiautomator returns the dialog's 3 nodes. Clear them before driving.
dismiss_system_dialogs() {
  local xml xy
  xml="$(nest_ui_dump "$NEST_UI/sysdlg.xml")" || return 0
  grep -qiE "isn't responding|keeps stopping" "$xml" || return 0
  for label in "Wait" "Close app"; do
    if xy="$(find_xy "$label")"; then
      adb shell input tap $xy; sleep 2
      ok "dismissed system dialog ('$label')"
      return 0
    fi
  done
  adb shell input keyevent 4 >/dev/null 2>&1
  say "  dismissed system dialog via BACK"
}

launch() {
  adb logcat -c >/dev/null 2>&1 || true
  dismiss_system_dialogs
  adb shell am force-stop "$NEST_PKG"
  adb shell am start -n "$NEST_PKG/$NEST_ACT" >/dev/null 2>&1
  local i cur
  for i in $(seq 1 15); do
    # `dumpsys activity` prints the package inside ActivityRecord{...}; a
    # `[^ ]*` pattern stops before the package and never matches. Read the
    # window focus line instead and substring-match the package name.
    cur="$(adb shell dumpsys window 2>/dev/null | tr -d '\r' \
           | grep -m2 -E 'mCurrentFocus|mFocusedApp')"
    case "$cur" in
      *"$NEST_PKG"*) ok "launched (foreground)"; return 0 ;;
    esac
    sleep 1
  done
  # A first install lands on the POST_NOTIFICATIONS dialog, not the app.
  if find_xy "Allow" >/dev/null 2>&1; then
    ok "dismissing permission dialog"
    adb shell input tap $(find_xy "Allow") ; sleep 2
    cur="$(adb shell dumpsys window 2>/dev/null | tr -d '\r' | grep -m2 -E 'mCurrentFocus|mFocusedApp')"
    case "$cur" in *"$NEST_PKG"*) ok "launched (after permission grant)"; return 0 ;; esac
  fi
  bad "app did not reach the foreground (missing activity or crashing)"
  say "    focus was: ${cur:-<none>}"
  return 1
}

ck_logcat() { nest_clean_logcat; }

start_recording() {
  adb shell rm -f /data/local/tmp/run.mp4 >/dev/null 2>&1
  adb shell screenrecord --time-limit 180 --bit-rate 4000000 /data/local/tmp/run.mp4 >/dev/null 2>&1 &
  ok "recording started"
}

stop_recording() {
  adb shell pkill -l2 screenrecord >/dev/null 2>&1 || true
  sleep 2
  if adbt pull /data/local/tmp/run.mp4 "$ART/run.mp4" >/dev/null 2>&1 && [ -s "$ART/run.mp4" ]; then
    ok "recording: $ART/run.mp4"
  else
    warn "no recording (same framebuffer limitation as screenshots)"
  fi
}
