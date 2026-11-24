#!/usr/bin/env bash
# Flow template — copy, rename the function, replace the three TODO steps.
#
#   cp scripts/flows/_template.sh scripts/flows/downloads.sh
#   # edit: flow_downloads(), the assertions, the title
#   scripts/nest.sh test downloads      # fast loop while writing it
#   scripts/verify.sh --flow downloads  # auditable run once it passes
#
# Sourced by scripts/nest.sh after lib/common.sh + lib/drive.sh, so every helper
# in scripts/lib/drive.sh is already available.
#
# THE BAR (docs/TESTING.md): launch → drive a control → screen changed → state
# asserted → logcat has no FATAL. `launch` succeeding is not the test; the
# assertions are.
#
# Helper cheat sheet:
#   launch                     force-stop + start, asserts the app is foreground
#   tap_label "Text"           tap by text or content-desc (retries animations)
#   assert_text "Text"         fail unless that string is on screen NOW
#   assert_any "A" "B"         pass if any one marker is present (scroll/empty states)
#   wait_for "Text" 30         poll up to 30s for it (use for slow work)
#   wait_gone "Text" 30        poll until it disappears (proves the job finished)
#   assert_state "what" "cmd"  assert something off-screen: a file, a DB row
#   snap "01-name"             screenshot into $ART/screens/ (reports if unavailable)
#   dump_ui                    raw uiautomator XML into $ART/ui/
#   ck_logcat                  fail on FATAL EXCEPTION / ANR
#
# Every helper returns non-zero on failure. Do NOT end with `return 0` unless the
# assertions above it are real — a flow that cannot fail verifies nothing.

flow_template() {
  local rc=0

  # 1. Start from a known state. launch clears logcat, so any crash below is ours.
  launch || return 1

  # 2. Drive the feature under test.
  # TODO: navigate to the screen and exercise the control.
  #   tap_label "Downloads"          || rc=1
  #   tap_label "Search"             || rc=1

  # 3. Assert the screen actually changed (not just "a tap exited 0").
  # TODO: one assertion per claim you intend to report.
  #   assert_text "Library"          || rc=1
  #   wait_for "Downloading" 30      || rc=1
  #   snap "01-feature" >/dev/null || true

  # 4. Assert persisted state the screen cannot show.
  # TODO: e.g. the file landed, the row exists, the preference stuck.
  #   assert_state "file present" "ls /sdcard/Download/nest" || rc=1

  # 5. Survive a restart — the cheapest way to catch "it only worked once".
  # TODO: remove if the feature is deliberately transient.
  #   launch || rc=1
  #   assert_text "Library" || rc=1

  # 6. Leave the app clean, then let ck_logcat decide whether it was clean.
  snap "99-final" >/dev/null || true
  ck_logcat || rc=1

  return $rc
}
