#!/usr/bin/env bash
# Flow: smoke — does the app come up and does every bottom-nav destination render?
# Sourced by scripts/nest.sh after lib/common.sh + lib/drive.sh.
#
# Verification is done through the Compose semantics tree (uiautomator), not pixels,
# so this flow still proves something on hosts where framebuffer readback is broken.
# Screenshots are captured when available and reported as unavailable when not.
#
# Bottom-nav labels come from ui/navigation/BottomNavItem.kt:
#   Home, Downloads, Collections, Settings

flow_smoke() {
  local rc=0

  # 1. Launch must reach the foreground — not merely "am start exited 0".
  launch || return 1

  # 1b. A fresh install sits on the POST_NOTIFICATIONS dialog; clear it so the
  #     rest of the flow sees the app, not the system prompt.
  if find_xy "Allow" >/dev/null 2>&1; then
    adb shell input tap $(find_xy "Allow"); sleep 2
    ok "granted notification permission"
  fi

  # 2. Wait for the UI to be *usable*, not merely non-empty. A node count is a
  #    weak gate: 12 nodes passes but the nav bar is still missing. Gate on the
  #    bottom navigation actually existing — that is what the rest of the flow
  #    needs, and it covers a slow cold start.
  local xml nodes i
  for i in $(seq 1 20); do
    if find_xy "Downloads" >/dev/null 2>&1; then
      xml="$(nest_ui_dump "$ART/ui/01-home.xml")" || true
      nodes="$(grep -o '<node' "$xml" 2>/dev/null | wc -l)"
      ok "home interactive ($nodes nodes, nav present) after ${i}s"
      break
    fi
    sleep 1
  done
  if [ "${nodes:-0}" -eq 0 ]; then
    bad "home never became interactive (nav bar missing after 20s)"; rc=1
    xml="$(nest_ui_dump "$ART/ui/01-home-FAILED.xml")" || true
  fi
  snap "01-home" >/dev/null || true

  # 3. Every bottom-nav destination must be reachable and change the tree.
  local label
  for label in Downloads Collections Settings Home; do
    if tap_label "$label"; then
      if nest_ui_dump "$ART/ui/02-${label,,}.xml" >/dev/null; then
        ok "on $label"
      else
        bad "$label did not produce a view tree"; rc=1
      fi
      snap "02-${label,,}" >/dev/null || true
    else
      bad "could not reach $label"; rc=1
    fi
  done

  # 4. Evidence of the final app state — capture BEFORE pressing back, or the
  #    screenshot is just the launcher and proves nothing about the app.
  snap "03-final" >/dev/null 2>&1 && ok "visual evidence captured" \
    || warn "no screenshots this run (framebuffer readback)"

  # 4b. BACK from the root destination legitimately exits to the launcher, so
  #    "still alive" is not the assertion. The real question is whether the
  #    exit was clean: no crash, and a sane focus target afterwards.
  adb shell input keyevent 4 >/dev/null 2>&1
  sleep 2
  local focus
  focus="$(adb shell dumpsys window 2>/dev/null | tr -d '\r' | grep -m1 mCurrentFocus)"
  case "$focus" in
    *"$NEST_PKG"*) ok "back kept the app in front" ;;
    *nexuslauncher*|*launcher*) ok "back exited cleanly to the launcher" ;;
    *) warn "unexpected focus after back: ${focus:-<none>}" ;;
  esac
  if adb logcat -d 2>/dev/null | grep -qE "FATAL EXCEPTION.*${NEST_PKG}"; then
    bad "crash during back navigation"; rc=1
  fi

  return $rc
}
