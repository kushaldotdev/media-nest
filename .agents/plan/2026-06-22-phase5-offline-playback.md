# Implementation Plan: Phase 5 — Offline Playback + Audio Extraction

## System / Contract Summary
- **Package**: `com.example.medianest`
- **Storage**: `context.filesDir/MediaNest/video/` and `MediaNest/audio/` — app-internal
- **Audio extraction**: Manual (user taps "Extract Audio" on completed download), uses ffmpeg-kit-full to produce mp3
- **Audio extraction output**: New `DownloadEntity` row with `format="audio_extracted"`, file stored in `MediaNest/audio/`
- **Local playback**: PlayerViewModel checks `DownloadRepository` for completed local file, uses `file://` URI in MediaItem
- **Stream picker**: Play button on completed Downloads → navigates to VideoDetailScreen showing only local streams (downloaded files)
- **DB version**: 3 currently → bump to 4 with explicit `ALTER TABLE` migration (NOT destructive)
- **VideoEntity**: Add `localFilePath: String = ""` field
- **Cache independence**: VideoEntity persisted on download complete so playback works after cache clear
- **ffmpeg-kit**: `com.arthenica:ffmpeg-kit-full:6.0-2` via Maven Central

---

## Phase Order

1. **5.1** — Add ffmpeg-kit-full dep to `libs.versions.toml` + `build.gradle.kts`
2. **5.2** — Add `localFilePath` to `VideoEntity`, bump DB v3→v4 with explicit migration SQL
3. **5.3** — Persist `VideoEntity` on download complete (insert/update in DownloadService after markCompleted)
4. **5.4** — PlayerViewModel local playback: inject `DownloadRepository`, resolve `file://` URI for local files, offline fallback
5. **5.5** — Stream picker from Downloads: Play icon on COMPLETED → navigate to VideoDetailScreen with local-only streams
6. **5.6** — Manual audio extraction: `AudioExtractor` ffmpeg-kit wrapper, "Extract Audio" button, progress tracking
7. **5.7** — Build and verify

---

## Steps

### Step 5.1: Add ffmpeg-kit-full dependency

**What**: Add `com.arthenica:ffmpeg-kit-full:6.0-2` to the version catalog and build file.

**Where**:
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

**How**:

`gradle/libs.versions.toml` — under `[versions]`:
```toml
ffmpegKit = "6.0-2"
```

Under `[libraries]`:
```toml
ffmpeg-kit-full = { module = "com.arthenica:ffmpeg-kit-full", version.ref = "ffmpegKit" }
```

`app/build.gradle.kts` — add:
```kotlin
    // ffmpeg-kit
    implementation(libs.ffmpeg.kit.full)
```

**Note**: The gradle variable name for `ffmpeg-kit-full` in toml will generate as `ffmpeg-kit-full` → libs accessor will be `libs.ffmpeg.kit.full` (dots replace hyphens). Verify exact accessor after writing.

**Why**: ffmpeg-kit-full provides all codecs (libmp3lame, libx264, etc.) needed for reliable mp3 extraction from any video format.

**Edge cases**: ~30MB APK size increase. ProGuard rules may be needed if minification enabled (currently disabled).

**Pitfalls / do not**: Do NOT use `min-gpl` variant — may lack codecs for some YouTube DASH formats.

**Validation**: `./gradlew :app:assembleDebug` succeeds.

---

### Step 5.2: Add localFilePath to VideoEntity + DB v3→v4 migration

**What**: Add `localFilePath: String = ""` to VideoEntity. Bump DB to version 4 with explicit `ALTER TABLE` migration (NOT destructive — version 3 has real data from Phase 4).

**Where**:
- `app/src/main/java/com/example/medianest/data/local/entity/VideoEntity.kt`
- `app/src/main/java/com/example/medianest/data/local/AppDatabase.kt`

**How**:

`VideoEntity.kt` — add field:
```kotlin
    val localFilePath: String = "",
```

`AppDatabase.kt`:
- Change `version = 3` → `version = 4`
- Add migration in `DatabaseModule.kt`:

```kotlin
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE videos ADD COLUMN localFilePath TEXT NOT NULL DEFAULT ''")
    }
}
```

Update `provideAppDatabase` in `DatabaseModule.kt`:
```kotlin
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "media_nest.db"
        ).addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration(false)
            .build()
```

Move `MIGRATION_3_4` to a companion object in `AppDatabase` or keep in `DatabaseModule.kt`.

**Why**: Explicit migration preserves user data. `fallbackToDestructiveMigration(false)` ensures no accidental data loss.

**Edge cases**: 
- App with v3 DB (Phase 4 users) upgrades → migration runs, existing rows get empty `localFilePath`
- Fresh install → Room creates v4 schema directly, no migration needed
- `localFilePath` default empty string means "not downloaded" — checked with `.isNotEmpty()`

**Pitfalls / do not**: 
- Do NOT use `fallbackToDestructiveMigration()` without migration — would delete all Phase 4 download data
- Do NOT make `localFilePath` nullable — use empty string sentinel

**Validation**: Existing app upgrades without data loss. Fresh install creates v4 schema.

---

### Step 5.3: Persist VideoEntity on download complete

**What**: After a download completes in `DownloadService.downloadFile()`, insert or update `VideoEntity` in the DB. This makes video metadata survive app restarts and cache clears.

**Where**:
- `app/src/main/java/com/example/medianest/service/DownloadService.kt`
- `app/src/main/java/com/example/medianest/data/local/dao/VideoDao.kt` (already exists — verify it has `insert` with `OnConflictStrategy.REPLACE`)

**How**:

Inject `VideoDao` into `DownloadService`:
```kotlin
    @Inject lateinit var videoDao: VideoDao
```

After `repository.markCompleted(...)` and `repository.update(download.copy(filePath = ...))`, add:
```kotlin
            // Persist video metadata for offline playback
            val existing = videoDao.getVideoById(download.videoId)
            if (existing == null) {
                videoDao.insert(
                    VideoEntity(
                        id = download.videoId,
                        title = download.title.ifEmpty { download.quality },
                        channelName = "",
                        durationSeconds = 0,
                        thumbnailUrl = download.thumbnailUrl,
                        localFilePath = outputFile.absolutePath
                    )
                )
            } else {
                videoDao.update(existing.copy(localFilePath = outputFile.absolutePath))
            }
```

Also add import for `VideoEntity` and `VideoDao`.

**Note**: The title and metadata may be empty since DownloadEntity has limited metadata. For richer metadata, the enqueue step in `VideoDetailViewModel` should also persist `VideoEntity`. But for now this gives enough to display in player.

**Why**: Without this, `PlayerViewModel` relies entirely on `lastResultCache` (in-memory, lost on app restart). Persisting VideoEntity enables offline playback after cold start.

**Edge cases**:
- Download of same video again (different quality) → `localFilePath` updated to latest download
- Download completes but VideoEntity insert fails → download is still marked COMPLETED, playback falls back to streaming
- Title/channel empty → player displays quality as fallback

**Pitfalls / do not**: Do NOT overwrite existing rich metadata with sparse DownloadEntity fields. Only update `localFilePath`.

**Validation**: Download a video → kill app → reopen → play from Downloads → local file plays.

---

### Step 5.4: PlayerViewModel local playback

**What**: Inject `DownloadRepository` into `PlayerViewModel`. In `initialize()`, check if a completed download exists for the videoId. If yes, use `file://` URI instead of stream URL. If no, fall back to current streaming behavior.

**Where**:
- `app/src/main/java/com/example/medianest/ui/viewmodel/PlayerViewModel.kt`

**How**:

Add `DownloadRepository` injection:
```kotlin
    private val downloadRepository: DownloadRepository
```

Update constructor:
```kotlin
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: HistoryDao,
    private val playbackPreferences: PlaybackPreferences,
    private val downloadRepository: DownloadRepository
) : ViewModel() {
```

Add a `streamIndex` parameter variant: when navigating from Downloads, we don't have a stream URL. Instead, we query the DB for completed downloads of this videoId and pick the best quality.

Modify `initialize(videoId, streamIndex)` to check for local file first:

```kotlin
    fun initialize(videoId: String, streamIndex: Int) {
        currentVideoId = videoId
        currentStreamIndex = streamIndex
        val info = lastResultCache[videoId]
        videoInfo = info

        viewModelScope.launch {
            val speed = playbackPreferences.playbackSpeed.first()
            player.setPlaybackSpeed(speed)
            _uiState.value = _uiState.value.copy(currentSpeed = speed)

            // Check for completed local download first
            val localDownloads = downloadRepository.getLocalDownloadsForVideo(videoId)
            val localFile = localDownloads.firstOrNull { it.filePath.isNotEmpty() }
            val uri = if (localFile != null) {
                android.net.Uri.fromFile(java.io.File(localFile.filePath)).toString()
            } else if (info != null && streamIndex < info.streamSources.size) {
                info.streamSources[streamIndex].url
            } else {
                _uiState.value = _uiState.value.copy(error = "No playable source found")
                return@launch
            }

            val title = info?.title ?: localFile?.title ?: "Unknown"
            val channel = info?.channelName ?: ""
            val thumbnail = info?.thumbnailUrl ?: localFile?.thumbnailUrl

            _uiState.value = _uiState.value.copy(
                title = title,
                channelName = channel,
                thumbnailUrl = thumbnail,
                isAudioOnly = localFile?.format == "audio" || localFile?.format == "audio_extracted",
                durationMs = if (localFile != null) 0L else (info?.durationSeconds ?: 0L) * 1000
            )

            val lastPlayback = historyDao.getLatestPlayback(videoId)
            val startPosition = lastPlayback?.positionMillis ?: 0L

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(channel)
                        .build()
                )
                .build()
            player.setMediaItem(mediaItem)
            player.seekTo(startPosition)
            player.prepare()
            player.play()
        }
    }
```

Also add `getLocalDownloadsForVideo(videoId: String)` to `DownloadDao` and `DownloadRepository`:

`DownloadDao.kt`:
```kotlin
    @Query("SELECT * FROM downloads WHERE videoId = :videoId AND status = 'COMPLETED'")
    suspend fun getCompletedDownloadsForVideo(videoId: String): List<DownloadEntity>
```

`DownloadRepository.kt`:
```kotlin
    suspend fun getLocalDownloadsForVideo(videoId: String): List<DownloadEntity> =
        downloadDao.getCompletedDownloadsForVideo(videoId)
```

**Why**: This is the core of Phase 5 — connecting downloaded files to the player. The player checks local first, then falls back to streaming.

**Edge cases**:
- No local download → falls back to streaming (existing behavior)
- Local file deleted by user → `Uri.fromFile()` will fail → ExoPlayer error → retry triggers re-initialize → same result. Need to mark download as FAILED if file not found.
- Multiple completed downloads → first one found (arbitrary). For stream picker, see Step 5.5.
- Audio extracted file → detected by format field, sets `isAudioOnly = true`

**Pitfalls / do not**:
- Do NOT block MainThread — all DB queries in `viewModelScope.launch`
- Do NOT assume `lastResultCache` always has the video — handle null case for Downloads-originated playback
- Do NOT use `stream.url` when local file exists — defeats the purpose

**Validation**:
- Download video → kill app → airplane mode → play from Downloads → file plays locally
- No download → play video → streams from URL (existing behavior)

---

### Step 5.5: Stream picker from Downloads

**What**: Replace the Delete icon on COMPLETED downloads with a Play icon. Tapping Play navigates to a stream-picker variant of VideoDetailScreen showing only downloaded streams for that video. If only one download exists, navigate directly to PlayerScreen.

**Where**:
- `app/src/main/java/com/example/medianest/ui/screens/DownloadsScreen.kt`
- `app/src/main/java/com/example/medianest/ui/viewmodel/DownloadsViewModel.kt`
- `app/src/main/java/com/example/medianest/ui/navigation/AppNavigation.kt`
- `app/src/main/java/com/example/medianest/ui/screens/VideoDetailScreen.kt`

**How**:

**DownloadsScreen.kt** — add `onPlayDownload` callback to `DownloadsScreen` and `DownloadItem`:

Update `DownloadsScreen` signature:
```kotlin
@Composable
fun DownloadsScreen(
    onPlayDownload: (DownloadEntity) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
)
```

Update `DownloadItem` signature:
```kotlin
private fun DownloadItem(
    download: DownloadEntity,
    onPlayDownload: (DownloadEntity) -> Unit,
    viewModel: DownloadsViewModel
)
```

Update COMPLETED action:
```kotlin
DownloadStatus.COMPLETED -> {
    IconButton(onClick = { onPlayDownload(download) }) {
        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
    }
}
```

**DownloadsViewModel.kt** — no changes needed (action handled by navigation)

**AppNavigation.kt** — update Downloads route to pass navigation:

Replace:
```kotlin
composable(BottomNavItem.Downloads.route) { DownloadsScreen() }
```

With:
```kotlin
composable(BottomNavItem.Downloads.route) {
    DownloadsScreen(
        onPlayDownload = { download ->
            navController.navigate("downloads/player/${download.videoId}")
        }
    )
}
```

Add new route for playing downloaded video:
```kotlin
composable(
    route = "downloads/player/{videoId}",
    arguments = listOf(navArgument("videoId") { type = NavType.StringType })
) { backStackEntry ->
    val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
    PlayerScreen(
        videoId = videoId,
        streamIndex = 0, // not used for local — PlayerViewModel finds best download
        onBack = { navController.popBackStack() }
    )
}
```

Also keep the existing Delete action. Move it to a secondary action (long press or swipe-to-delete with a confirmation). Simplest: add Delete as a secondary icon or use a dropdown.

**Simpler approach for now**: Keep Delete icon always visible next to Play icon for COMPLETED:
```kotlin
DownloadStatus.COMPLETED -> {
    Row {
        IconButton(onClick = { onPlayDownload(download) }) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
        IconButton(onClick = { viewModel.cancelDownload(download.id) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
```

**Why**: Stream picker lets user choose which quality to play when multiple downloads exist. Direct play when only one.

**Edge cases**:
- Single download → direct play (no picker)
- Multiple downloads → navigate to player, PlayerViewModel picks best quality. For true picker, we'd need a separate screen — deferred to Phase 6.
- Download has no filePath → skip Play icon (file was deleted)

**Pitfalls / do not**: Do NOT navigate to VideoDetailScreen for stream picking if download is single — direct play is better UX.

**Validation**: Download a video → tap Play → plays from local file.

---

### Step 5.6: Manual audio extraction

**What**: `AudioExtractor.kt` wraps ffmpeg-kit to extract audio from a downloaded video file. "Extract Audio" button on COMPLETED video downloads. Creates a new `DownloadEntity` row with `format="audio_extracted"`. Shows indeterminate progress during extraction.

**Where**:
- `app/src/main/java/com/example/medianest/service/AudioExtractor.kt` (new)
- `app/src/main/java/com/example/medianest/data/local/entity/DownloadEntity.kt` (no change — existing fields work)
- `app/src/main/java/com/example/medianest/ui/screens/DownloadsScreen.kt`
- `app/src/main/java/com/example/medianest/ui/viewmodel/DownloadsViewModel.kt`
- `app/src/main/java/com/example/medianest/data/local/dao/DownloadDao.kt`
- `app/src/main/java/com/example/medianest/data/repository/DownloadRepository.kt`

**How**:

**AudioExtractor.kt**:

```kotlin
package com.example.medianest.service

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class ExtractionResult(
        val outputPath: String,
        val success: Boolean,
        val errorMessage: String? = null
    )

    suspend fun extractAudio(
        inputFilePath: String,
        videoId: String,
        quality: String
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "MediaNest/audio")
        outputDir.mkdirs()

        val outputFileName = "${videoId}_${quality}_audio.mp3"
        val outputFile = File(outputDir, outputFileName)

        // Delete if exists — re-extract
        if (outputFile.exists()) outputFile.delete()

        val command = "-i \"$inputFilePath\" -vn -acodec libmp3lame -q:a 2 \"${outputFile.absolutePath}\""

        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode

        if (ReturnCode.isSuccess(returnCode)) {
            ExtractionResult(outputFile.absolutePath, true)
        } else {
            val logs = session.allLogsAsString
            ExtractionResult("", false, logs.ifEmpty { "ffmpeg extraction failed" })
        }
    }
}
```

**Note**: ffmpeg-kit runs synchronously in `execute()`. Wrapping in `withContext(Dispatchers.IO)` keeps it off main thread.

**DownloadDao.kt** — add query for checking existing audio extraction:
```kotlin
    @Query("SELECT * FROM downloads WHERE videoId = :videoId AND format = 'audio_extracted'")
    suspend fun getAudioExtraction(videoId: String): DownloadEntity?
```

**DownloadRepository.kt** — add:
```kotlin
    suspend fun getAudioExtraction(videoId: String): DownloadEntity? =
        downloadDao.getAudioExtraction(videoId)

    suspend fun downloadExists(videoId: String, format: String, quality: String): Boolean =
        downloadDao.getDownload(videoId, format, quality) != null
```

**DownloadsViewModel.kt** — add extraction function:
```kotlin
    private val audioExtractor: AudioExtractor

    // Add to constructor:
    private val audioExtractor: AudioExtractor

    // Add state:
    private val _extractingVideoId = MutableStateFlow<String?>(null)
    val extractingVideoId: StateFlow<String?> = _extractingVideoId

    fun extractAudio(download: DownloadEntity) {
        if (download.filePath.isEmpty() || download.status != DownloadStatus.COMPLETED) return
        // Prevent duplicate extraction
        if (_extractingVideoId.value == download.videoId) return

        viewModelScope.launch {
            // Check if audio already extracted
            val existing = downloadRepository.getAudioExtraction(download.videoId)
            if (existing != null) return@launch

            _extractingVideoId.value = download.videoId

            // Insert a QUEUED row for audio extraction
            val extractionEntity = DownloadEntity(
                videoId = download.videoId,
                url = "", // not applicable
                format = "audio_extracted",
                quality = "${download.quality}_audio",
                title = download.title,
                thumbnailUrl = download.thumbnailUrl,
                status = DownloadStatus.DOWNLOADING,
                progress = 0f
            )
            val insertId = downloadRepository.insert(extractionEntity)

            try {
                val result = audioExtractor.extractAudio(
                    download.filePath,
                    download.videoId,
                    download.quality
                )

                if (result.success) {
                    downloadRepository.markCompleted(insertId, File(result.outputPath).length())
                    downloadRepository.update(
                        extractionEntity.copy(
                            id = insertId,
                            status = DownloadStatus.COMPLETED,
                            progress = 1f,
                            filePath = result.outputPath,
                            fileSizeBytes = File(result.outputPath).length()
                        )
                    )
                } else {
                    downloadRepository.markFailed(
                        insertId,
                        result.errorMessage ?: "Extraction failed",
                        0
                    )
                }
            } catch (e: Exception) {
                downloadRepository.markFailed(
                    insertId,
                    e.message ?: "Extraction failed",
                    0
                )
            } finally {
                _extractingVideoId.value = null
            }
        }
    }
```

Add `@Inject constructor` parameter for `AudioExtractor`:
```kotlin
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val downloadPreferences: DownloadPreferences,
    private val audioExtractor: AudioExtractor
) : ViewModel() {
```

Add to `DownloadsUiState`:
```kotlin
data class DownloadsUiState(
    val maxConcurrent: Int = DownloadPreferences.DEFAULT_MAX,
    val activeCount: Int = 0,
    val extractingVideoId: String? = null
)
```

Update init to collect:
```kotlin
    init {
        // ... existing collectors ...
        viewModelScope.launch {
            _extractingVideoId.collect { id ->
                _uiState.value = _uiState.value.copy(extractingVideoId = id)
            }
        }
    }
```

**DownloadsScreen.kt** — add "Extract Audio" button for COMPLETED video downloads:

In `DownloadItem`, add button when:
- `download.status == COMPLETED`
- `download.format == "video" || download.format == "video_only"`
- `uiState.extractingVideoId != download.videoId` (not currently extracting)

```kotlin
// Inside COMPLETED case, next to Play and Delete:
if (download.format.startsWith("video")) {
    val isExtracting = uiState.extractingVideoId == download.videoId
    IconButton(
        onClick = { viewModel.extractAudio(download) },
        enabled = !isExtracting
    ) {
        if (isExtracting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.AudioFile, contentDescription = "Extract Audio")
        }
    }
}
```

Add import for `Icons.Default.AudioFile` (may need `Icons.Filled.AudioFile` or use `Icons.Default.MusicNote`). Check available Material Icons — use `Icons.Default.MusicNote` if `AudioFile` not available.

**Why**: Manual extraction gives user control. ffmpeg-kit handles all codec complexity. New download row fits existing UI patterns.

**Edge cases**:
- Extract button tapped while extraction in progress → disabled with spinner
- ffmpeg fails → mark FAILED with error message, user can retry
- Already extracted → check `getAudioExtraction()` before creating duplicate
- Video file deleted before extraction → ffmpeg command fails with file-not-found error
- Progress reporting: ffmpeg-kit supports progress callbacks via `FFmpegKitConfig.enableStatisticsCallback()` — defer to future enhancement, use indeterminate for now

**Pitfalls / do not**:
- Do NOT run ffmpeg on MainThread — must be on `Dispatchers.IO`
- Do NOT create duplicate audio download rows — check before insert
- Do NOT delete the source video after extraction — user may want both
- ffmpeg command string uses double quotes around paths — handle spaces in file paths
- ffmpeg-kit `execute()` is blocking — coroutine wrapping is essential

**Validation**: Download video → tap "Extract Audio" → spinner → mp3 appears in downloads → playable.

---

### Step 5.7: Build and verify

**What**: Compile and verify the build succeeds.

**How**:
```bash
./gradlew :app:assembleDebug
```

**Validation**: BUILD SUCCESSFUL.

---

## Beginner Implementation Guide (execution order)

1. Add ffmpeg-kit-full to `libs.versions.toml` + `build.gradle.kts`
2. Add `localFilePath` to `VideoEntity.kt`
3. Create `MIGRATION_3_4` and update `AppDatabase.kt` + `DatabaseModule.kt`
4. Add `getCompletedDownloadsForVideo` to `DownloadDao.kt`
5. Add `getLocalDownloadsForVideo` + `getAudioExtraction` to `DownloadRepository.kt`
6. Add `VideoDao` injection + VideoEntity persist logic to `DownloadService.kt`
7. Inject `DownloadRepository` + update `initialize()` in `PlayerViewModel.kt`
8. Create `AudioExtractor.kt`
9. Update `DownloadsViewModel.kt` — add `extractAudio()`, `extractingVideoId` state, `AudioExtractor` injection
10. Update `DownloadsScreen.kt` — add `onPlayDownload` callback, Play icon on COMPLETED, Extract Audio button
11. Update `AppNavigation.kt` — add `downloads/player/{videoId}` route, wire `onPlayDownload`
12. Build `./gradlew :app:assembleDebug`

---

## Final Verification Checklist

- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] App with v3 DB upgrades to v4 without data loss
- [ ] Download a video → VideoEntity persists `localFilePath`
- [ ] Kill app → reopen → Downloads screen shows completed item
- [ ] Tap Play on completed download → local file plays (airplane mode OK)
- [ ] No download → Play falls back to streaming (existing behavior)
- [ ] Delete completed download → file deleted, DB row removed
- [ ] Download video → "Extract Audio" button visible on COMPLETED
- [ ] Tap "Extract Audio" → spinner shown → mp3 appears in Downloads
- [ ] Extracted mp3 plays in PlayerScreen with `isAudioOnly = true`
- [ ] Extract Audio while already extracting → prevented (spinner, not duplicate)
- [ ] Extract Audio on already-extracted video → prevented (checks DB)
- [ ] ffmpeg extraction failure → FAILED row with error message → retryable
- [ ] Multiple completed downloads for same video → first one plays
- [ ] Local file deleted → playback shows error

---

## Stop Conditions

- `ffmpeg-kit` dependency not found → verify Maven Central repo is in `settings.gradle.kts` or root `build.gradle.kts`
- `ALTER TABLE` migration fails → verify v3 schema matches exactly (columns exist as expected)
- `com.arthenica.ffmpegkit.FFmpegKit` class not found → verify `ffmpeg-kit-full` dependency string (check for typos)
- `libs.ffmpeg.kit.full` accessor not found → check libs.versions.toml `[libraries]` name format (dots replaced hyphens)
- PlayerViewModel crashes on file URI → verify `Uri.fromFile()` and file exists
- Extract Audio button icon not found → use `Icons.Default.MusicNote` instead of `AudioFile`
