#!/usr/bin/env bash
# scripts/dev/poke.sh — drive the UI without a full verify run.
#
#   scripts/dev/poke.sh launch              start the app, confirm foreground
#   scripts/dev/poke.sh tap "Downloads"     tap a node by text or content-desc
#   scripts/dev/poke.sh tap-xy 540 2100     tap raw coordinates
#   scripts/dev/poke.sh see "Library"       assert text is on screen
#   scripts/dev/poke.sh shot [name]         screenshot into the cache dir
#   scripts/dev/poke.sh ui                  dump the view hierarchy (prints text+bounds)
#   scripts/dev/poke.sh nav                 tap through every bottom-nav item
#   scripts/dev/poke.sh logcat              show crashes only
#   scripts/dev/poke.sh screen              show display-readback health
#
# Taps go through `adb shell input`, so nothing animates as a cursor — the UI simply
# reacts. Use `ui` to find labels; `uidump` gives the Compose semantics tree even when
# framebuffer readback is broken.
set -uo pipefail
. "$(dirname "$0")/../lib/common.sh"

needs_device() { nest_is_booted || die "no booted emulator — scripts/dev/emulator.sh up"; }

# Centre of the first node matching text or content-desc (exact, then substring).
_find_xy() {
  local want="$1" xml
  xml="$(nest_ui_dump)" || return 1
  python3 - "$xml" "$want" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, want = sys.argv[1], sys.argv[2].lower()
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)
exact = None
sub = None
for n in root.iter('node'):
    for attr in ('text', 'content-desc'):
        v = (n.get(attr) or '').strip().lower()
        if not v:
            continue
        # leaf elements are falsy in ElementTree — compare with `is not None`
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

cmd="${1:-help}"; shift || true
case "$cmd" in
  launch)
    needs_device
    hdr "launch $NEST_PKG"
    adb logcat -c >/dev/null 2>&1 || true
    adb shell am force-stop "$NEST_PKG"
    # `am start` returns success even when the activity is missing; verify foreground.
    adb shell am start -n "$NEST_PKG/$NEST_ACT" >/dev/null 2>&1
    for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
      fg="$(sh_ dumpsys window | grep -m2 -E 'mCurrentFocus|mFocusedApp')"
      case "$fg" in *"$NEST_PKG"*) ok "foreground: $(echo "$fg" | head -1)"; exit 0 ;; esac
      sleep 1
    done
    bad "app never reached the foreground (activity not found or crashing)"
    say "    focus: ${fg:-<none>}"
    adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$NEST_PKG" 2>&1 | tail -2
    nest_clean_logcat || true
    exit 1
    ;;
  tap)
    needs_device; want="${1:?usage: poke.sh tap \"<label>\"}"
    xy="$(_find_xy "$want")" || { bad "no node matching \"$want\" (try: poke.sh ui)"; exit 1; }
    hdr "tap \"$want\" @ $xy"
    adb shell input tap $xy
    ok "tapped"
    ;;
  tap-xy)
    needs_device; x="${1:?x}"; y="${2:?y}"
    hdr "tap $x $y"; adb shell input tap "$x" "$y"; ok "tapped"
    ;;
  see)
    needs_device; want="${1:?usage: poke.sh see \"<text>\"}"
    xml="$(nest_ui_dump)" || { bad "ui dump failed (animation? retry)"; exit 1; }
    if grep -qiF -- "$want" "$xml"; then ok "on screen: \"$want\""; else bad "not on screen: \"$want\""; exit 1; fi
    ;;
  shot)
    needs_device; name="${1:-$(date +%H%M%S)}"
    out="$(nest_screenshot "$NEST_SHOTS/$name.png")" \
      && ok "screenshot: $out" || bad "screenshot failed (framebuffer readback broken — see docs/TESTING.md)"
    ;;
  ui)
    needs_device
    xml="$(nest_ui_dump)" || { bad "ui dump failed"; exit 1; }
    python3 - "$xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    t = (n.get('text') or '').strip()
    d = (n.get('content-desc') or '').strip()
    if not t and not d:
        continue
    cls = (n.get('class') or '').split('.')[-1]
    b = n.get('bounds') or ''
    print(f"  {cls:<22} text={t[:38]!r:<40} desc={d[:30]!r:<32} {b}")
PY
    ;;
  nav)
    needs_device
    rc=0
    for label in Home Downloads Collections Settings; do
      if xy="$(_find_xy "$label")"; then
        adb shell input tap $xy; sleep 1
        ok "tapped $label ($xy)"
      else
        bad "nav item not found: $label"; rc=1
      fi
    done
    exit $rc
    ;;
  logcat)
    needs_device; nest_clean_logcat
    ;;
  screen)
    needs_device
    hdr "display readback health"
    say "  surfaceflinger: $(sh_ pidof surfaceflinger || echo none)"
    say "  display state : $(sh_ dumpsys display | grep -m1 -oE 'state [A-Z]+' || echo unknown)"
    if nest_probe_display; then ok "screencap works"; else
      bad "screencap yields nothing (gfxstream hasReadColorBufferDma assertion)"
      warn "workaround: run the emulator on the host OS, not inside WSL2"
    fi
    ;;
  *) sed -n '2,20p' "$0" ;;
esac
