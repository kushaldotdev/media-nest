# Implementation Plan - Fix Audio Bitrate Formatting

Fix the "0kbps" bug displayed for audio streams where the raw bitrate from NewPipe is already in kbps.

## System / Contract Summary
- **Target File**: `app/src/main/java/com/example/medianest/extraction/YouTubeExtractor.kt`
- **Impacted Area**: Formatting of audio stream qualities returned by stream extractor.
- **Contract**: Streams with bitrates under 1000 are already in kbps and should not be divided by 1000. Streams with bitrates >= 1000 are in bps and must be divided by 1000.

## Phase Order
1. **Phase 1: Test & Verify Bug** — Write/update test in `ExampleUnitTest.kt` to extract a live stream and observe `0kbps`.
2. **Phase 2: Fix & Validate** — Update `YouTubeExtractor.kt` formatting logic, re-run test to verify correct kbps values.

## Steps

### Phase 1: Test & Verify Bug
- **What**: Modify `ExampleUnitTest.kt` to load NewPipe and log audio stream qualities.
- **Where**: `app/src/test/java/com/example/medianest/ExampleUnitTest.kt`
- **How**: Replace with test code that initializes `NewPipe` via `DownloaderProvider.getDownloader()` and runs `YouTubeExtractor().extractVideo` on a public YouTube video.
- **Why**: Reproduce the "0kbps" format to verify the exact bug behavior.
- **Edge cases**: Network connectivity failure during unit test.
- **Pitfalls / do not**: Do not skip the unit test.
- **Validation**: Run `./gradlew :app:testDebugUnitTest` with `JAVA_HOME`.
- **Docs**: None.

### Phase 2: Fix & Validate
- **What**: Update bitrate format detection.
- **Where**: `app/src/main/java/com/example/medianest/extraction/YouTubeExtractor.kt#L55-L56`
- **How**: Re-read file, then change:
  ```kotlin
  val rawBitrate = if (track.averageBitrate > 0) track.averageBitrate else track.bitrate
  val bitrateKbps = if (rawBitrate > 0) {
      if (rawBitrate < 1000) rawBitrate else rawBitrate / 1000
  } else {
      0
  }
  val qualityStr = if (bitrateKbps > 0) "${bitrateKbps}kbps" else "Unknown bitrate"
  ```
- **Why**: Prevent integer division from turning a kbps value (like `128`) into `0`.
- **Edge cases**: `bitrate <= 0` should format as `"Unknown bitrate"`.
- **Pitfalls / do not**: Do not format as `0kbps` or crash on null/empty.
- **Validation**: Re-run the unit test and verify it prints real bitrates (e.g. `128kbps`) instead of `0kbps`.
- **Docs**: None.

## Beginner Implementation Guide
- Set `JAVA_HOME` to Android Studio's bundled runtime when running `./gradlew`.
- Run tests via PowerShell using: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew :app:testDebugUnitTest`

## Final Verification Checklist
- [ ] Reproduce `0kbps` in unit test.
- [ ] Fix logic in `YouTubeExtractor.kt`.
- [ ] Run test again to verify `0kbps` is gone and replaced by actual kbps values.
- [ ] Verify test suite compiles and succeeds completely.

## Stop Conditions
- Stop if NewPipe API structures in the project do not match expected types.

# Plan Gap Check
## Gaps / risks found
- None. The task is straightforward and only touches formatting inside `YouTubeExtractor.kt`.
## Changes made
- None. Setting up verification tests first.
## Remaining edge cases
- Bitrates that are missing or returned as negative/zero values are handled correctly via the `"Unknown bitrate"` fallback.
## Remaining pitfalls
- Ensure that the threshold (1000) is correct for separating kbps from bps. Standard audio streams on YouTube are either < 500 kbps (such as 128 kbps, 160 kbps, 256 kbps) or > 30000 bps. 1000 is a safe threshold.
## Open questions
- None.

