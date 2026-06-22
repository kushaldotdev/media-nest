# MediaNest — Full Project Code Review & Remediation Plan

**Date**: 2026-06-23
**Reviewer**: Automated multi-agent review (Phase 1–9)
**Mode**: Both (Architecture + Code)
**Anchor**: [medianest.md](file:///d:/dev/media-nest/.agents/anchors/medianest.md) `[anchor:loaded]`

---

## Files Reviewed

| Phase | Files | Count |
|-------|-------|-------|
| 1 — Scaffold | `build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts`, `MediaNestApp.kt`, `MainActivity.kt`, `DatabaseModule.kt`, `AppDatabase.kt`, all DAOs, all entities, `AndroidManifest.xml`, `MainScreen.kt`, navigation, theme | ~20 |
| 2 — Extraction | `YouTubeExtractor.kt`, `DownloaderProvider.kt`, `VideoRepository.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, mapper, model files | ~8 |
| 3 — Playback | `PlaybackService.kt`, `PlayerViewModel.kt`, `PlayerScreen.kt`, `PlaybackPreferences.kt` | 4 |
| 4 — Downloads | `DownloadService.kt`, `DownloadRepository.kt`, `DownloadsViewModel.kt`, `DownloadsScreen.kt`, `VideoDetailViewModel.kt`, `VideoDetailScreen.kt`, `AudioExtractor.kt` | 7 |
| 5 — Offline | `PlayerViewModel.kt` (local path), `DownloadEntity.kt` (audio_extracted) | 2 |
| 6 — Organization | `LibraryScreen.kt`, `LibraryViewModel.kt`, `FolderEntity.kt`, `FolderDao.kt`, `VideoFolderJoin.kt`, `VideoFolderDao.kt` | 6 |
| 7 — Subscriptions | `SubscriptionWorker.kt`, `SubscriptionRepository.kt`, `SubscriptionsViewModel.kt`, `SubscriptionsScreen.kt`, `SubscriptionEntity.kt`, `SubscriptionDao.kt`, `WorkScheduler.kt` | 7 |
| 8 — Export/Import | `BackupRepository.kt`, `RestoreRepository.kt`, `LibraryRepair.kt`, `BackupModels.kt`, `ExportImportViewModel.kt`, `SettingsScreen.kt` | 6 |
| 9 — VPS Sync | `SyncManager.kt`, `SyncRepository.kt`, `SyncWorker.kt`, `SyncModels.kt`, server `main.py`, `database.py`, `config.py`, `schemas.py`, `devices.py`, `sync.py`, `Dockerfile`, `docker-compose.yml` | 12 |
| **Total** | | **~72 files** |

---

## CRITICAL Issues (Must Fix Before Ship)

---

### C1. Missing `androidx.hilt:hilt-compiler` KSP processor — Workers crash at runtime

- **File**: [build.gradle.kts](file:///d:/dev/media-nest/app/build.gradle.kts#L67) + [libs.versions.toml](file:///d:/dev/media-nest/gradle/libs.versions.toml#L43)
- **Problem**: Only `com.google.dagger:hilt-compiler` is registered via `ksp()`. The `@HiltWorker` annotation (used by `SubscriptionWorker` and `SyncWorker`) requires `androidx.hilt:hilt-compiler` to ALSO be processed. Without it, `HiltWorkerFactory` cannot find generated `*_AssistedFactory` classes → runtime crash when any worker is invoked.
- **Impact**: All WorkManager workers crash. Subscription checks and sync never run.
- **Root cause**: `androidx.hilt:hilt-work` runtime library is included (line 69), but its annotation processor is not.
- **Solution**:

1. Add to `libs.versions.toml` under `[libraries]`:
```toml
androidx-hilt-work-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "hiltNavigationCompose" }
```

2. Add to `app/build.gradle.kts` in dependencies block:
```kotlin
ksp(libs.androidx.hilt.work.compiler)
```

- **Validation**: Build succeeds. Run `SubscriptionWorker` via WorkManager test — no `ClassNotFoundException`.

---

### C2. `DownloadStatus` enum stored as ordinal but DAO queries compare against string literals

- **File**: [DownloadEntity.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/local/entity/DownloadEntity.kt#L8-L14) + [DownloadDao.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/local/dao/DownloadDao.kt#L30-L34)
- **Problem**: Room stores Kotlin enums as their `ordinal` (integer) by default. The DAO raw SQL queries use string comparisons:
  - Line 30: `WHERE status = 'QUEUED' OR status = 'DOWNLOADING'`
  - Line 33: `WHERE status = 'DOWNLOADING'`
  - Line 51: `WHERE status = 'COMPLETED'`
  - Line 54: `WHERE videoId = :videoId AND status = 'COMPLETED'`
  - Line 57: `WHERE videoId = :videoId AND format = 'audio_extracted'`

  These will **never match** because Room stores `QUEUED` as `0`, `DOWNLOADING` as `1`, etc. The queries compare integers against strings.
- **Impact**: `getActiveDownloads()`, `getActiveDownloadCount()`, `getCompletedDownloadsForVideo()`, `getAudioExtraction()` all return empty/zero. Downloads queue never processes. Audio extraction never found.
- **Root cause**: Missing `@TypeConverter` for `DownloadStatus`.
- **Solution**: Add a TypeConverter class and register it:

```kotlin
// data/local/Converters.kt
import androidx.room.TypeConverter
import com.example.medianest.data.local.entity.DownloadStatus

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        try { DownloadStatus.valueOf(value) } catch (_: Exception) { DownloadStatus.QUEUED }
}
```

Register in `AppDatabase.kt`:
```kotlin
@TypeConverters(Converters::class)
@Database(...)
abstract class AppDatabase : RoomDatabase() { ... }
```

- **Edge cases**: Existing databases with ordinal values need a migration to convert int→string, or a destructive migration.
- **Validation**: Query `SELECT status FROM downloads` — values should be `'QUEUED'`, `'COMPLETED'`, etc., not `0`, `1`.

---

### C3. `DownloadService.downloadFile()` — double update reverts COMPLETED status

- **File**: [DownloadService.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/service/DownloadService.kt#L209-L210)
- **Problem**: After download finishes:
  - Line 209: `repository.markCompleted(download.id, bytesRead)` → sets `status=COMPLETED, progress=1.0`
  - Line 210: `repository.update(download.copy(filePath=..., fileSizeBytes=...))` → `download` is the **original** object from line 130 with `status=QUEUED, progress=0f`. This **overwrites** the COMPLETED status back to QUEUED.
- **Impact**: Completed downloads show as QUEUED with 0% progress in UI. File exists on disk but status is wrong.
- **Root cause**: `.copy()` called on stale `download` object instead of re-fetching from DB.
- **Solution**: Remove line 210. Modify `markCompleted` DAO query to also set `filePath` and `fileSizeBytes`:

```kotlin
// DownloadDao.kt — replace existing markCompleted
@Query("UPDATE downloads SET status = 'COMPLETED', progress = 1.0, fileSizeBytes = :fileSize, filePath = :filePath WHERE id = :id")
suspend fun markCompleted(id: Long, fileSize: Long, filePath: String)
```

```kotlin
// DownloadRepository.kt — update signature
suspend fun markCompleted(id: Long, fileSize: Long, filePath: String) =
    downloadDao.markCompleted(id, fileSize, filePath)
```

```kotlin
// DownloadService.kt line 209-210 — replace both lines with:
repository.markCompleted(download.id, bytesRead, outputFile.absolutePath)
```

- **Pitfall**: Do NOT just swap the order of the two calls. The `download.copy()` will always have stale status fields.
- **Validation**: Download a file → check `SELECT status, progress, filePath FROM downloads WHERE id = <id>` → must be `COMPLETED, 1.0, <path>`.

---

### C4. `DownloadService.onStartCommand()` — no `ForegroundServiceStartNotAllowedException` handling

- **File**: [DownloadService.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/service/DownloadService.kt#L89-L90) + companion methods (lines 50-72)
- **Problem**: `startForeground()` on line 90 can throw `ForegroundServiceStartNotAllowedException` on Android 12+ (API 31) when the app is in the background. The companion methods `pause()`, `resume()`, `cancel()` call `context.startForegroundService()` which will also crash from background.
- **Impact**: Crash on Android 12+ when download triggered from background (e.g., from `SubscriptionWorker`).
- **Root cause**: Missing try-catch. `SubscriptionWorker` already handles this (line 66) but `DownloadService` does not.
- **Solution**:

```kotlin
// DownloadService.onStartCommand() — wrap startForeground
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
        startForeground(NOTIFICATION_ID, buildNotification(0, 0))
    } catch (e: Exception) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            && e is android.app.ForegroundServiceStartNotAllowedException) {
            stopSelf()
            return START_NOT_STICKY
        }
        throw e
    }
    // ... rest of method
}
```

Also wrap companion `startForegroundService` calls in try-catch:
```kotlin
fun pause(context: Context, downloadId: Long) {
    val intent = Intent(context, DownloadService::class.java).apply {
        action = ACTION_PAUSE
        putExtra(EXTRA_DOWNLOAD_ID, downloadId)
    }
    try { context.startForegroundService(intent) }
    catch (_: Exception) { /* Log and handle gracefully */ }
}
```

- **Validation**: Kill app → trigger download from subscription worker → no crash.

---

### C5. `PlayerScreen` — `setVideoSurfaceView` not available on `Player` interface

- **File**: [PlayerScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/PlayerScreen.kt#L104-L108)
- **Problem**: `player?.setVideoSurfaceView(surfaceView)` and `player?.clearVideoSurfaceView(surfaceView)` — the `player` variable is a `Player` (from `MediaController`). `setVideoSurfaceView()` is an `ExoPlayer`-specific method, not on the `Player` interface. Compilation error or runtime `NoSuchMethodError`.
- **Impact**: Player screen won't compile, or video won't render.
- **Root cause**: Using raw `SurfaceView` + `ExoPlayer`-specific API instead of Media3 UI component.
- **Solution**: Replace the `AndroidView` with `androidx.media3.ui.PlayerView`:

```kotlin
AndroidView(
    factory = { ctx ->
        androidx.media3.ui.PlayerView(ctx).apply {
            useController = false // we have custom controls
        }
    },
    update = { playerView ->
        playerView.player = player
    },
    onRelease = { playerView ->
        playerView.player = null
    },
    modifier = Modifier.fillMaxSize()
)
```

- **Pitfall**: Don't cast `MediaController` to `ExoPlayer` — it's a different process/proxy.
- **Validation**: Build succeeds. Play a video → video renders in the surface.

---

### C6. `VideoDetailViewModel.enqueueDownload()` — FK violation when VideoEntity doesn't exist

- **File**: [VideoDetailViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/VideoDetailViewModel.kt) → `enqueueDownload()` ~line 89-98
- **Problem**: `DownloadEntity` has a `ForeignKey` on `videoId` → `VideoEntity.id`. The `enqueueDownload()` method inserts a `DownloadEntity` but doesn't ensure the `VideoEntity` exists first. The video is inserted separately in `HomeViewModel.searchAndSave()`, but the ordering isn't guaranteed.
- **Impact**: `SQLiteConstraintException` crash if download enqueued before video is saved.
- **Root cause**: Missing upsert-or-verify step before download insert.
- **Solution**: Before inserting `DownloadEntity`, ensure `VideoEntity` exists:

```kotlin
// In enqueueDownload(), before downloadRepository.insert(entity):
val existingVideo = videoRepository.getVideoById(videoInfo.videoId)
if (existingVideo == null) {
    videoRepository.insert(videoInfo.toVideoEntity())
}
```

Or use `OnConflictStrategy.IGNORE` on VideoDao.insert so it's safe to call redundantly.

- **Validation**: Navigate directly to video detail → tap download → no crash.

---

### C7. `VideoDetailViewModel.enqueueDownload()` — missing `videoUrl` field

- **File**: [VideoDetailViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/VideoDetailViewModel.kt) → `enqueueDownload()` ~line 89-98
- **Problem**: `DownloadEntity` created without setting `videoUrl`. The `DownloadService.downloadFile()` (line 155) uses `download.videoUrl ?: download.url` to re-extract fresh stream URLs on 403/410 errors. Without `videoUrl`, it falls back to `download.url` which is the CDN stream URL (not the YouTube page URL) → re-extraction fails.
- **Impact**: Downloads that get a 403/410 error can never recover via URL refresh.
- **Root cause**: Missing field assignment.
- **Solution**: Add `videoUrl` to entity creation:

```kotlin
val entity = DownloadEntity(
    videoId = videoInfo.videoId,
    url = stream.url,
    videoUrl = "https://www.youtube.com/watch?v=${videoInfo.videoId}",  // ADD THIS
    format = stream.format,
    quality = stream.quality,
    title = videoInfo.title,
    thumbnailUrl = videoInfo.thumbnailUrl
)
```

- **Validation**: Trigger download with expired stream URL → verify retry extracts fresh URL from page URL.

---

## MAJOR Issues

---

### M1. `DownloadService` never calls `stopSelf()` — runs forever

- **File**: [DownloadService.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/service/DownloadService.kt)
- **Problem**: Uses `START_STICKY`, never calls `stopSelf()` or `stopForeground()` when queue is empty. Service runs indefinitely showing "Downloading..." notification.
- **Impact**: Persistent notification, battery drain.
- **Solution**: In `processQueue()`, after checking queue, if no active or queued downloads remain:

```kotlin
private fun processQueue() {
    serviceScope.launch {
        val maxConcurrent = preferences.maxConcurrentDownloads.first()
        val queue = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
        val active = repository.getActiveDownloadCount()
        val slots = (maxConcurrent - active).coerceAtLeast(0)

        if (queue.isEmpty() && active == 0 && activeJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return@launch
        }
        queue.take(slots).forEach { enqueueDownload(it) }
    }
}
```

---

### M2. `DownloadService.updateNotification()` — `Long.toInt()` overflow

- **File**: [DownloadService.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/service/DownloadService.kt#L322)
- **Problem**: `setProgress(totalBytes.toInt(), bytesDownloaded.toInt(), false)` — for files >2GB, `.toInt()` overflows producing negative or wrong progress.
- **Impact**: Notification progress bar broken for large files.
- **Solution**: Normalize to percentage:

```kotlin
if (totalBytes > 0) {
    val pct = ((bytesDownloaded * 100) / totalBytes).toInt()
    setProgress(100, pct, false)
    setContentText("${bytesDownloaded / 1024}KB / ${totalBytes / 1024}KB")
}
```

---

### M3. `DownloadService.pauseDownload()` — race condition loses progress

- **File**: [DownloadService.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/service/DownloadService.kt#L256-L265)
- **Problem**: `pauseDownload()` cancels the job first (`activeJobs[id]?.cancel()`), then in a **separate** coroutine sets status to PAUSED. The `CancellationException` in the download loop fires before the DB status is set. Progress at pause point may be lost.
- **Impact**: Resume restarts from scratch; downloaded bytes wasted.
- **Solution**: Don't cancel the job. Set status to PAUSED in DB and let the download loop detect it via `isPaused()`:

```kotlin
private fun pauseDownload(id: Long) {
    serviceScope.launch {
        val download = repository.getDownloadById(id) ?: return@launch
        repository.updateStatus(id, DownloadStatus.PAUSED, download.progress)
        // The download loop will detect PAUSED on next isPaused() check and exit gracefully
    }
}
```

Then in `downloadFile()`, after the `isPaused()` block saves progress and returns, remove the job from `activeJobs`:
```kotlin
if (isPaused(download.id)) {
    repository.updateStatus(download.id, DownloadStatus.PAUSED,
        if (contentLength > 0) bytesRead.toFloat() / contentLength else 0f)
    activeJobs.remove(download.id)
    return
}
```

---

### M4. `DownloadsViewModel.extractAudio()` — same double-update bug as C3

- **File**: [DownloadsViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/DownloadsViewModel.kt) → `extractAudio()` ~line 125-134
- **Problem**: Calls `markCompleted(insertId, ...)` then `update(extractionEntity.copy(...))` with stale fields. Same pattern as C3.
- **Impact**: Extracted audio shows as DOWNLOADING forever.
- **Solution**: Use a single update with correct status:

```kotlin
val updatedEntity = extractionEntity.copy(
    id = insertId,
    filePath = outputFile.absolutePath,
    fileSizeBytes = outputFile.length(),
    status = DownloadStatus.COMPLETED,
    progress = 1f
)
repository.update(updatedEntity)
// Remove the markCompleted call
```

---

### M5. `SubscriptionsScreen` unreachable — not in bottom navigation

- **File**: [MainScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/MainScreen.kt#L36-L39)
- **Problem**: Bottom bar includes `Home`, `Downloads`, `Library`, `Settings` but NOT `Subscriptions`. The `SubscriptionsScreen` route IS registered in `AppNavigation.kt` but no navigation entry point exists.
- **Impact**: Users can never reach the Subscriptions screen.
- **Solution**: Add `Subscriptions` to the bottom nav items list in `MainScreen.kt`:

```kotlin
val items = listOf(
    BottomNavItem.Home,
    BottomNavItem.Downloads,
    BottomNavItem.Subscriptions,  // ADD THIS
    BottomNavItem.Library,
    BottomNavItem.Settings
)
```

Also ensure `BottomNavItem.Subscriptions` sealed class member exists with the correct route and icon.

---

### M6. `DownloadEntity` — FK constraint prevents audio extraction if VideoEntity missing

- **File**: [DownloadEntity.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/local/entity/DownloadEntity.kt#L18-L25) + `DownloadsViewModel.extractAudio()`
- **Problem**: Audio extraction creates a new `DownloadEntity` with the same `videoId`. The FK constraint requires `videoId` to exist in `videos` table. If `VideoEntity` was never saved (e.g., from subscription auto-download), insert crashes.
- **Impact**: Audio extraction crashes with `SQLiteConstraintException`.
- **Solution**: Before creating extraction entity, ensure VideoEntity exists (same pattern as C6). The download's source video should already exist, but verify defensively:

```kotlin
// In extractAudio(), before insert:
if (videoDao.getVideoById(download.videoId) == null) {
    videoDao.insert(VideoEntity(
        id = download.videoId,
        title = download.title,
        channelName = "",
        thumbnailUrl = download.thumbnailUrl
    ))
}
```

---

### M7. `DownloadService.isPaused()` — DB query on every 8KB buffer read

- **File**: [DownloadService.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/service/DownloadService.kt#L247-L250)
- **Problem**: `isPaused()` is called on every iteration of the `while(true)` download loop (8KB buffer). Each call queries the database. Extremely wasteful I/O.
- **Impact**: Performance degradation, unnecessary disk I/O, slows downloads.
- **Solution**: Use an in-memory flag:

```kotlin
private val pauseFlags = ConcurrentHashMap<Long, Boolean>()

private fun isPaused(id: Long): Boolean = pauseFlags[id] == true

private fun pauseDownload(id: Long) {
    pauseFlags[id] = true
    // ... rest of pause logic
}

private fun resumeDownload(id: Long) {
    pauseFlags.remove(id)
    // ... rest of resume logic
}
```

---

### M8. `HomeViewModel.onUrlSubmitted` — `return@launch` inside `runCatching` bypasses error handling

- **File**: [HomeViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/HomeViewModel.kt) → `onUrlSubmitted()` ~line 48-56
- **Problem**: Playlist branch uses `_uiState.value = PlaylistResult(playlist)` then `return@launch` inside `runCatching { }`. If an exception occurs in the playlist path before `return@launch`, it bypasses `onFailure` because the `return@launch` exits the entire coroutine, not just the `runCatching` block.
- **Impact**: Errors in playlist extraction silently swallowed; no error state shown to user.
- **Solution**: Move state assignment to after `runCatching`:

```kotlin
viewModelScope.launch {
    _uiState.value = HomeUiState.Loading
    val result = runCatching { /* extraction logic */ }
    result.onSuccess { state -> _uiState.value = state }
        .onFailure { _uiState.value = HomeUiState.Error(it.message ?: "Failed") }
}
```

Don't use `return@launch` inside `runCatching`. Return the state value instead.

---

### M9. `DownloaderProvider` — `HttpURLConnection` never disconnected

- **File**: [DownloaderProvider.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/extraction/DownloaderProvider.kt) ~line 11-33
- **Problem**: `HttpURLConnection` opened but never `disconnect()`ed. Resource leak.
- **Impact**: Connection pool exhaustion under heavy extraction use.
- **Solution**: Add `finally { connection.disconnect() }` block.

---

### M10. `fallbackToDestructiveMigration()` deprecated in Room 2.7.x

- **File**: [DatabaseModule.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/di/DatabaseModule.kt#L145)
- **Problem**: Room 2.7.2 deprecates the no-arg `fallbackToDestructiveMigration()`. May cause compilation warning or error depending on exact API surface.
- **Impact**: Compilation warning/error.
- **Solution**: Change to:

```kotlin
.fallbackToDestructiveMigration(dropAllTables = true)
```

---

### M11. Sync server `push` route — `version` field always `0`

- **File**: [sync.py](file:///d:/dev/media-nest/sync-server/app/routes/sync.py#L28-L29)
- **Problem**: When inserting changes, `version` is hardcoded to `0`:
  ```python
  (req.device_id, item.table, item.row_id, item.operation, json.dumps(item.payload), 0, now)
  ```
  The `version` column is always `0`. The pull endpoint uses `id` (autoincrement PK) as the version via `item["version"] = item["id"]` (line 62). So the `version` column in the DB is dead weight — the actual versioning uses `id`.
- **Impact**: Confusing schema. The `version` column is unused but exists in the table definition and index.
- **Solution**: Either remove the `version` column from the `changes` table schema entirely, or set it equal to the auto-generated `id` via a trigger. Current behavior works because pull uses `id > after_version`, but the field is misleading.

---

### M12. Sync server — `register` endpoint has no authentication

- **File**: [devices.py](file:///d:/dev/media-nest/sync-server/app/routes/devices.py#L9-L24)
- **Problem**: Anyone can call `POST /device/register` to create a new device with a new API key. No rate limiting, no master secret, no auth.
- **Impact**: Open registration means anyone with the server URL can register devices and push/pull data.
- **Solution**: Add a `MASTER_API_KEY` environment variable. Require it as a header on registration:

```python
MASTER_KEY = os.getenv("MASTER_API_KEY", "")

@router.post("/register", response_model=RegisterResponse)
def register(req: RegisterRequest, x_master_key: str = Header(...)):
    if not MASTER_KEY or x_master_key != MASTER_KEY:
        raise HTTPException(403, "Invalid master key")
    # ... rest of registration
```

Update `.env.example`:
```
DATABASE_PATH=/app/data/sync.db
MASTER_API_KEY=your-secret-master-key-here
```

---

### M13. Sync server — `on_event("startup")` is deprecated in modern FastAPI

- **File**: [main.py](file:///d:/dev/media-nest/sync-server/app/routes/../main.py#L20-L23)
- **Problem**: `@app.on_event("startup")` is deprecated in FastAPI ≥0.115. Should use lifespan context manager.
- **Impact**: Deprecation warning; will break in future FastAPI versions.
- **Solution**:

```python
from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    os.makedirs(os.path.dirname(os.environ.get("DATABASE_PATH", "/app/data/sync.db")), exist_ok=True)
    init_db()
    yield

app = FastAPI(title="MediaNest Sync Server", version="1.0.0", lifespan=lifespan)
```

---

## MINOR Issues

---

### m1. `MainScreen` — route string prefix matching is fragile

- **File**: [MainScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/MainScreen.kt#L27-L29)
- **Problem**: `route?.startsWith("player/")` to hide bottom bar. Breaks if routes change.
- **Fix**: Use sealed class or route constant.

---

### m2. `HomeViewModel.lastResultCache` — unbounded static cache

- **File**: [HomeViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/HomeViewModel.kt) ~line 32
- **Problem**: `companion object { val lastResultCache = mutableMapOf<>() }` grows unbounded.
- **Fix**: Use `LruCache(maxSize = 50)` or clear on app lifecycle boundary.

---

### m3. `PlayerViewModel.initialize` — no re-initialization guard

- **File**: [PlayerViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/PlayerViewModel.kt) ~line 99
- **Problem**: Called from `LaunchedEffect(videoId, streamIndex)`. Navigation back+forth restarts playback.
- **Fix**: Guard: `if (currentVideoId == videoId && currentStreamIndex == streamIndex) return`.

---

### m4. `YouTubeExtractor.extractVideo` — `info.id` not validated as short ID

- **File**: [YouTubeExtractor.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/extraction/YouTubeExtractor.kt) ~line 93
- **Problem**: Critical invariant: "VideoEntity.id must always be YouTube video short ID (not full URL)." `info.id` from NewPipeExtractor usually returns short ID, but it's not validated.
- **Fix**: Apply `extractVideoIdFromUrl()` or regex validation on `info.id`.

---

### m5. `PlayerScreen` — speed chips overflow on narrow screens

- **File**: [PlayerScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/PlayerScreen.kt#L166-L177)
- **Problem**: 6 `FilterChip` items in a non-scrolling `Row`. Overflows on small screens.
- **Fix**: Wrap in `LazyRow` or `FlowRow`.

---

### m6. `LibraryScreen.FolderRow` — delete icon is misleading

- **File**: [LibraryScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt) → `FolderRow()` ~line 326
- **Problem**: Delete button uses `Icons.Default.FolderOpen` instead of a delete icon.
- **Fix**: Use `Icons.Default.Delete`.

---

### m7. `LibraryScreen` — nested scrollable containers

- **File**: [LibraryScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt#L255-L278)
- **Problem**: `LazyColumn` (child folders) + `LazyVerticalGrid` (videos) in same direction. Layout measurement issues.
- **Fix**: Single `LazyColumn` with folder items + video items, or use `NestedScrollConnection`.

---

### m8. `DownloadsScreen` — `format.startsWith("video")` may not match

- **File**: [DownloadsScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/DownloadsScreen.kt) ~line 167
- **Problem**: If format values are `"mp4"`, `"webm"` (not `"video/mp4"`), the check won't match. "Extract Audio" button never shows.
- **Fix**: Check `format != "audio" && format != "audio_extracted"` instead.

---

### m9. `AudioExtractor` — `allLogsAsString` potentially null

- **File**: [AudioExtractor.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/service/AudioExtractor.kt) ~line 44
- **Problem**: `session.allLogsAsString` could be null on some FFmpegKit versions → NPE.
- **Fix**: `session.allLogsAsString ?: "ffmpeg extraction failed"`.

---

### m10. `PlayerViewModel` — `durationMs = 0` for local files

- **File**: [PlayerViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/PlayerViewModel.kt) ~line 133
- **Problem**: `(info?.durationSeconds ?: 0L) * 1000` — for offline files without metadata, duration is 0. Seek bar doesn't work.
- **Fix**: In `onPlaybackStateChanged`, when `state == STATE_READY`, update `durationMs = controller.duration`.

---

### m11. `DownloadService` — no byte-range resumption support

- **File**: [DownloadService.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/service/DownloadService.kt)
- **Problem**: Paused downloads restart from scratch. No `Range: bytes=<start>-` header.
- **Fix**: Store `bytesRead` in entity. On resume, use Range header and append to existing temp file.

---

### m12. `SubscriptionWorker` — unused `import javax.inject.Inject`

- **File**: [SubscriptionWorker.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/worker/SubscriptionWorker.kt#L18)
- **Fix**: Remove unused import.

---

### m13. `BackupMetadata.schemaVersion` hardcoded to `7`, DB is at version `8`

- **File**: [BackupModels.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/backup/BackupModels.kt#L11)
- **Problem**: `val schemaVersion: Int = 7` but actual DB version is `8`. Importing a backup from a newer version on an older app could silently lose the `updatedAt` and `videoUrl` columns.
- **Fix**: Change to `val schemaVersion: Int = 8`.

---

### m14. `RestoreRepository` — `localFilePath` restored as filename, not full path

- **File**: [RestoreRepository.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/backup/RestoreRepository.kt#L158-L163)
- **Problem**: `BackupRepository.toBackup()` strips `localFilePath` to just the filename (`File(it).name`). On restore, `toEntity()` uses this raw filename as `localFilePath`. But `localFilePath` should be an absolute path for file access. After restore, offline playback breaks.
- **Fix**: In `RestoreRepository.toEntity()` for `BackupVideo`, reconstruct the full path:

```kotlin
private fun BackupVideo.toEntity() = VideoEntity(
    // ...
    localFilePath = if (localFilePath.isNotEmpty()) {
        File(context.filesDir, "MediaNest/video/$localFilePath").absolutePath
    } else "",
    // ...
)
```

Or rely on `LibraryRepair` to fix paths post-restore (which it does, but user must manually trigger it).

---

### m15. Sync server `docker-compose.yml` — deprecated `version` key

- **File**: [docker-compose.yml](file:///d:/dev/media-nest/sync-server/docker-compose.yml#L1)
- **Problem**: `version: "3.9"` is deprecated in Docker Compose v2+.
- **Fix**: Remove the `version` line entirely.

---

### m16. `SyncManager.collectLocalChanges()` — full table scan on every sync

- **File**: [SyncManager.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/sync/SyncManager.kt#L162-L274)
- **Problem**: On first sync (`since == 0`), ALL records from ALL tables are pushed. On subsequent syncs, records are filtered by `addedAt > since` or `updatedAt > since`. However, this still reads ALL rows and filters in Kotlin. For large libraries, this is slow.
- **Fix**: Add DAO queries that filter by timestamp:
  ```kotlin
  @Query("SELECT * FROM videos WHERE addedAt > :since")
  suspend fun getVideosSince(since: Long): List<VideoEntity>
  ```

---

## Seam Issues (Cross-subsystem)

1. **DownloadEntity ↔ VideoEntity FK**: Multiple subsystems (SubscriptionWorker, VideoDetailViewModel, SyncManager.applyUpsert for downloads) can insert `DownloadEntity` without ensuring `VideoEntity` exists first. The FK constraint is the root cause of crashes across C6 and M6.

2. **DownloadStatus storage format**: The TypeConverter fix (C2) affects all DAO queries across DownloadService, DownloadsViewModel, SyncManager, and BackupRepository. Must be applied as a single atomic change.

3. **Backup ↔ Restore path mismatch**: BackupRepository strips paths to filenames; RestoreRepository stores filenames as paths. LibraryRepair can fix this but only if triggered manually. Consider auto-running repair after restore.

---

## Verification Checklist

| # | Check | Command / Method |
|---|-------|-----------------|
| 1 | Build compiles | `gradlew clean :app:assembleDebug` (via `build.bat`) |
| 2 | Workers don't crash | Enqueue `SubscriptionWorker` via WorkManager test API |
| 3 | Download completes with correct status | Download file → query DB: `SELECT status, progress, filePath FROM downloads` |
| 4 | Download queue processes | Verify `getActiveDownloads()` returns results after enqueue |
| 5 | PlayerScreen renders video | Navigate to player → video plays in surface |
| 6 | Subscriptions accessible | Bottom nav → Subscriptions tab visible and tappable |
| 7 | Export + Import round-trip | Export → clear app data → Import → verify videos/folders/history restored |
| 8 | Sync push+pull | Configure server URL → sync → verify changes on another device |
| 9 | Foreground service on API 31+ | Background start → no crash |
| 10 | TypeConverter active | `SELECT status FROM downloads` returns string values not integers |

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 7 |
| Major | 13 |
| Minor | 16 |
| **Total** | **36** |

**Verdict**: **Block** — 7 critical issues prevent compilation or cause runtime crashes. Must fix C1–C7 before any testing is possible. Major issues M1–M13 affect core functionality (downloads, playback, navigation, sync security).

**Recommended fix order**:
1. C2 (TypeConverter) → C1 (hilt-compiler) → C5 (PlayerScreen) → builds
2. C3 + M4 (double update) → C6 + C7 (FK + videoUrl) → downloads work
3. C4 (foreground exception) → M1 (stopSelf) → service stability
4. M5 (subscriptions nav) → M3 (pause race) → M7 (isPaused perf) → UX
5. M10–M13 (server fixes) → sync works
6. Minor issues → polish
