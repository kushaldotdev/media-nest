# Search and link intake

Home is the entry screen for intake: a search/paste field, a link history list, and the
metadata-fetch path that turns a pasted URL into a download option sheet.

## Sub-features

- `intake-field` — the search field is present and accepts focus.
- `intake-metadata` — submitting a URL starts a fetch and surfaces a loading state.
- `intake-failure` — an invalid URL or network failure produces an error state, not a silent
  no-op.
- `intake-history` — previously submitted links appear under `History` and can be cleared.

## How to get to it (user POV)

- Launch the app; Home is the first destination.
- Tap the field labelled `Search or paste URL...`.

## Driving it with drive.sh

Preconditions:

- App launched and Home interactive (see [app-shell](app-shell.md)).
- No expectation of network: every assertion below must hold offline or report the offline
  path. Note explicitly which one was exercised.

- **Field present.** Run `assert_text "Search or paste URL..."`. The placeholder is in the
  live tree.
- **History empty state.** Run `assert_text "No link history"` on a clean install. Once links
  exist this assertion no longer applies — record which state the run saw.
- **Submit a URL.** Run `tap_label "Search or paste URL..."`, then
  `adb shell input text "https%3A%2F%2Fexample.com%2Ftest.mp4"` (the `input text` escape for
  `:` and `/`), then `adb shell input keyevent 66` (ENTER). Run
  `wait_for "Fetching Video Metadata" 20`.
- **Observe the outcome.** Either a metadata sheet with `AVAILABLE QUALITIES`, or an
  `Extraction Error`. Both are real results; which one appears must be reported, never
  assumed.
- **Proof.** Run `snap "01-intake"` then `ck_logcat`.
- **Persistence.** Run `launch` again and check the History list. A submitted link that
  survives restart is the proof the intake path persisted, not just displayed.

## Gotchas

- `adb shell input text` cannot type space or `:` `/` directly; escape them or the command
  silently types a partial URL.
- Network-dependent: on an emulator without internet the metadata fetch fails by design. That
  is an offline result, not a passing feature.
- The option sheet is a modal; a `BACK` dismisses it rather than leaving the screen.
- Do not treat "Fetching Video Metadata" appearing as success — it is a loading state. Wait for
  the sheet or the error.
