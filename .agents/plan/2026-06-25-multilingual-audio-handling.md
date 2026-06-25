# Implementation Plan - Multilingual Audio Handling

Resolve the "0kbps" formatting bug, filter out duplicate dubbed audio tracks (retaining only the default/original track), and display the track language in the UI.

## System / Contract Summary
- **Target Files**:
  - `app/src/main/java/com/example/medianest/data/model/ExtractedVideoInfo.kt`
  - `app/src/main/java/com/example/medianest/extraction/YouTubeExtractor.kt`
  - `app/src/main/java/com/example/medianest/ui/screens/VideoDetailScreen.kt`
- **Impacted Area**: Models, extraction logic, and UI display of stream qualities.
- **Contract**:
  - Only keep audio streams where the track type is not `DUBBED` (original/default only).
  - `StreamSource` will include a nullable `language` field.
  - `VideoDetailScreen.kt` will display this language next to the quality label.

## Phase Order
1. **Phase 1: Update Model** — Add `language: String? = null` to `StreamSource`.
2. **Phase 2: Implement Extraction Filter & Language Extraction** — Modify `YouTubeExtractor.kt` to dynamically format bitrates, exclude `DUBBED` audio tracks, and extract language metadata.
3. **Phase 3: Update UI** — Display the language next to the quality in `VideoDetailScreen.kt`.
4. **Phase 4: Validate** — Run unit tests to confirm all changes compile and function correctly.

## Steps

### Phase 1: Update Model
- **What**: Modify `StreamSource` data class.
- **Where**: `app/src/main/java/com/example/medianest/data/model/ExtractedVideoInfo.kt`
- **How**: Add `val language: String? = null` parameter to the `StreamSource` constructor.
- **Why**: Store track language information without breaking backward compatibility or database parsing.

### Phase 2: Implement Extraction Filter & Language Extraction
- **What**: Filter out dubbed audio streams and set language value.
- **Where**: `app/src/main/java/com/example/medianest/extraction/YouTubeExtractor.kt`
- **How**:
  - Format bitrates correctly (check if `< 1000` to prevent division of kbps values).
  - Skip/ignore streams if `track.audioTrackType == AudioTrackType.DUBBED` (safeguarded so we only filter if at least one non-dubbed track exists).
  - Populate the `language` field:
    ```kotlin
    language = if (!track.audioTrackName.isNullOrBlank()) track.audioTrackName else track.audioLocale?.displayLanguage
    ```
- **Why**: Prevent duplicate dubbed audio streams from cluttering the UI and ensure the download manager only enqueues original/default tracks.

### Phase 3: Update UI
- **What**: Render the language suffix.
- **Where**: `app/src/main/java/com/example/medianest/ui/screens/VideoDetailScreen.kt`
- **How**: Update the label construction in `StreamQualityRow`:
  ```kotlin
  "audio" -> {
      val langSuffix = if (!stream.language.isNullOrBlank()) " [${stream.language}]" else ""
      "Audio Only (${stream.quality})$langSuffix"
  }
  ```
- **Why**: Clearly show the track language in the audio options list.

### Phase 4: Validate
- **What**: Verify build and run tests.
- **Where**: `app/src/test/java/com/example/medianest/ExampleUnitTest.kt`
- **How**: Run unit tests on a multilingual video (e.g. MrBeast) to verify that exactly 5 original language audio streams are extracted with their correct language attributes populated.

# Plan Gap Check
## Gaps / risks found
- None.
## Changes made
- Merged separate filter and display plans into this single unified document.
## Remaining edge cases
- For videos that have no multilingual tracks, `language` will be `null` and the UI will gracefully show standard labels without empty brackets.
- Safely retains the fallback to keep all streams if they are all classified as `DUBBED` (prevents empty list).
## Remaining pitfalls
- Ensure that the new parameter `language` in the `StreamSource` constructor has a default value so that existing invocations elsewhere do not cause compilation errors.
