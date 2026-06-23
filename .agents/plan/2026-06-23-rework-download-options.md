# Implementation Plan - Grouped Download Options, Player Controls, and Throttled Progress

Optimize progress updates (throttled updates to avoid database I/O bottlenecks), implement a local audio retry loop to prevent resets to 0%, add a Play button next to the Download button, and bind skip controls on player.

## System / Contract Summary
- **Download Page UI**: Add a Play/Stream button next to the Download button for each grouped video stream card. Change the label `"Video Only (Auto-merges audio)"` and `"Video + Audio"` to just **`"Video"`**.
- **Quick Download Dropdown**: Filter out all audio streams. Change the stream labels to simply show **`"Video"`**.
- **Continuous Progress**:
  - Video stream download: scale progress to `0.0f - 0.90f`.
  - Audio stream download: set `errorMessage = "downloading_audio"` and scale progress to `0.90f - 0.95f`.
  - Merging phase: set `errorMessage = "merging"` and display progress as `0.97f`.
  - Completion: clear `errorMessage` to `NULL` and set `status = COMPLETED` and `progress = 1.0f`.
- **UI Progress Display**:
  - If `errorMessage == "downloading_audio"`, display **"Downloading audio..."** and show the current progress bar.
  - If `errorMessage == "merging"`, display **"Merging video & audio..."** and show an indeterminate progress bar.
- **Progress Throttling**:
  - Main video loop: only write to the DB when progress increases by >= 1% (or every 1 MB if total size is unknown), preventing disk I/O bottlenecks.
  - Audio loop: write to the DB using a targeted `updateProgressAndMessage` query when progress increases by >= 1%.
- **Player controls**: Bind skip backward/forward 10s buttons to `seekRelative` in `PlayerViewModel`.

## Phase Order
- **Phase 1**: Update database query to clear `errorMessage` on completion and add `updateProgressAndMessage` in `DownloadDao.kt` and `DownloadRepository.kt`.
- **Phase 2**: Implement relative seek and bind player controls in `PlayerViewModel.kt` and `PlayerScreen.kt`.
- **Phase 3**: Rework UI options in `LibraryScreen.kt` and `VideoDetailScreen.kt` (adding Play button and removing "Video Only" reference).
- **Phase 4**: Implement throttled progress updates, audio download local retry, and FFmpeg merging in `DownloadService.kt`.
- **Phase 5**: Run `build.bat` to compile and verify.

---

## Steps

### Phase 1: Database Operations
#### Step 1.1: Clear errorMessage on markCompleted and add updateProgressAndMessage
- **Where**: [DownloadDao.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/local/dao/DownloadDao.kt) and [DownloadRepository.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/repository/DownloadRepository.kt)
- **How**:
  1. In `DownloadDao.kt`, update `markCompleted` and add `updateProgressAndMessage`:
     ```kotlin
     @Query("UPDATE downloads SET status = 'COMPLETED', progress = 1.0, errorMessage = NULL, fileSizeBytes = :fileSize, filePath = :filePath WHERE id = :id")
     suspend fun markCompleted(id: Long, fileSize: Long, filePath: String)

     @Query("UPDATE downloads SET progress = :progress, errorMessage = :errorMessage WHERE id = :id")
     suspend fun updateProgressAndMessage(id: Long, progress: Float, errorMessage: String?)
     ```
  2. In `DownloadRepository.kt`, add:
     ```kotlin
     suspend fun updateProgressAndMessage(id: Long, progress: Float, errorMessage: String?) =
         downloadDao.updateProgressAndMessage(id, progress, errorMessage)
     ```

---

### Phase 2: Player skip controls
#### Step 2.1: Implement seekRelative in PlayerViewModel
- **Where**: [PlayerViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/PlayerViewModel.kt)
- **How**: Add `seekRelative` method to relatively offset playback:
  ```kotlin
  fun seekRelative(offsetMs: Long) {
      val controller = _player.value ?: return
      val newPosition = (controller.currentPosition + offsetMs).coerceIn(0L, maxOf(controller.duration, 0L))
      controller.seekTo(newPosition)
      _uiState.value = _uiState.value.copy(positionMs = newPosition)
      savePosition()
  }
  ```

#### Step 2.2: Update buttons in PlayerScreen
- **Where**: [PlayerScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/PlayerScreen.kt)
- **How**: Bind `Replay10` and `Forward10` icons to click actions calling `viewModel.seekRelative`.

---

### Phase 3: Grouped Download Options UI
#### Step 3.1: LibraryScreen Dropdown
- **Where**: [LibraryScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt)
- **How**:
  - Filter out all audio streams.
  - Change `typeLabel` inside the dropdown list to simply show `"Video"`.

#### Step 3.2: VideoDetailScreen
- **Where**: [VideoDetailScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/VideoDetailScreen.kt)
- **How**:
  - Add import `import androidx.compose.material3.IconButton`.
  - Inside `StreamQualityRow`, wrap the download button in a `Row` and prepended with an `IconButton` using `Icons.Default.PlayArrow` to call `onPlay(stream)`.
  - Change `typeLabel` mapping for `"video"` and `"video_only"` formats to `"Video"`.

---

### Phase 4: Throttled Progress & Audio Local Retry
#### Step 4.1: Update DownloadsScreen progress rendering
- **Where**: [DownloadsScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/DownloadsScreen.kt)
- **How**:
  - Update `DownloadStatus.DOWNLOADING` status text selection to handle `"downloading_audio"` and `"merging"`.
  - Update progress indicator rendering to show an indeterminate animated progress bar when the status is merging (`errorMessage == "merging"`).

#### Step 4.2: Throttled Download Loops and Local Retry in DownloadService.kt
- **Where**: [DownloadService.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/service/DownloadService.kt)
- **How**:
  - In `downloadFile`, throttle video download database writes using a progress scale of `0.0f - 0.90f` for `video_only` streams:
    ```kotlin
    val currentProgress = if (download.format == "video_only") {
        (bytesRead.toFloat() / totalLength) * 0.90f
    } else {
        bytesRead.toFloat() / totalLength
    }
    val shouldUpdate = if (totalLength > 0) {
        (currentProgress - lastProgressSent >= 0.01f) || (bytesRead == totalLength)
    } else {
        bytesRead - lastProgressUpdate > 1024 * 1024
    }
    if (shouldUpdate) {
        repository.updateStatus(download.id, DownloadStatus.DOWNLOADING, currentProgress)
        lastProgressSent = currentProgress
        lastProgressUpdate = bytesRead
    }
    ```
  - Wrap the audio download and merge inside a local retry loop (max 3 retries):
    - Update progress to `0.90f` and `errorMessage = "downloading_audio"` once at the start of the audio loop.
    - Inside the audio download loop, throttle progress updates (`0.90f - 0.95f` range) using `updateProgressAndMessage` (saving only on >= 1% change).
    - Update progress to `0.97f` and `errorMessage = "merging"` once before executing `FFmpegKit.execute`.
    - Catch failures during the audio download/merge phase locally, clean up `_audio.tmp`, increment local retries, and sleep for 2 seconds.
    - If all 3 local retries fail, propagate the exception to fail the download task, keeping the video temp file intact.

---

## Final Verification Checklist
- [x] Project compiles successfully.
- [x] Downloads tab shows "Downloading audio..." and "Merging video & audio..." labels.
- [x] Video Detail page contains Play buttons next to grouped resolution cards.
- [x] Labels do not contain "Video Only" references.
- [x] Multi-codec resolution downloads do not conflict or overwrite each other in the database.
- [x] UI displays both video and audio sizes together (e.g. `7.2 MB + 1.2 MB`).
