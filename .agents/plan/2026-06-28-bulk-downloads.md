# Bulk Downloads by Resolution

Add download all by resolution in playlist or channel, check available disk space, show total size, and show confirmation.

## Proposed Changes

### ViewModel & Preferences

#### [MODIFY] [HomeViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/HomeViewModel.kt)
- Inject `DownloadPreferences`.
- Implement stream selection fallback logic (`selectStream`).
- Add states for bulk selection dialog, fetch progress, and bulk confirmation dialog.
- Implement sequential asynchronous fetching of video metadata using a cancelable Coroutine Job.
- Implement disk space checking using `file.usableSpace`.
- Implement bulk enqueuing of the selected video streams.

### UI Screens

#### [MODIFY] [HomeScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/HomeScreen.kt)
- Update `PlaylistResult` and `ChannelResult` items to display a "Download All" button side-by-side with subscription buttons.
- Display Quality Selection, Progress, and Confirmation dialogs based on ViewModel states.

## Verification Plan

### Automated/Manual Tests
- Click "Download All" on a playlist or channel.
- Verify quality dialog displays.
- Select a quality (e.g., 720p).
- Verify fetching progress dialog appears and counts up, and cancellation stops the process.
- Verify confirmation dialog shows correct counts, size formatting, and disk space details.
- Verify warning displays if space is insufficient.
- Confirm bulk downloads are correctly enqueued in the downloader service.
