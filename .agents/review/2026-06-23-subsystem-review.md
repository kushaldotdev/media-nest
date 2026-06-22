# MediaNest System Review and Technical Remediation Plan

## Metadata
- **Project**: MediaNest
- **Built**: 2026-06-23 01:18
- **Mode**: Both (Architecture and Code Review)
- **Verdict**: BLOCK (issues must be fixed before release)

---

# Architecture Review

## Subsystems Reviewed
- **Scaffold & Extraction**: JitPack, NewPipeExtractor, YouTubeExtractor, DownloaderProvider
- **Playback & Database**: PlaybackService, PlayerResolver, Room DAOs/Entities, AppDatabase, PlayerViewModel, PlayerScreen
- **Downloads & Storage**: DownloadService, DownloadRepository, AudioExtractor, DownloadsViewModel, DownloadsScreen
- **Subscriptions**: SubscriptionRepository, SubscriptionWorker, WorkScheduler
- **Export/Import & Repair**: BackupRepository, RestoreRepository, LibraryRepair, ExportImportViewModel
- **VPS Sync**: SyncManager, SyncRepository, FastAPI sync-server (routes, database, main)

---

## Active Issues & Solutions

### Issue 1: Room Migration Mismatch (Startup Crash)
- **Severity**: Critical
- **Files**: [DatabaseModule.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/di/DatabaseModule.kt) (lines 99-109, 137-153) and [AppDatabase.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/local/AppDatabase.kt) (line 30)
- **Problem**: In `AppDatabase.kt`, the database version is set to `8`. However, `MIGRATION_7_8` only adds the `updatedAt` column to the `downloads` table. The `videoUrl` column is added in `MIGRATION_8_9`, which will never run because the active database version is 8.
- **Impact**: Upgrading users from version 7 to 8 will crash instantly on startup with `IllegalStateException` due to a schema validation mismatch.
- **Root Cause**: Splitting column migrations across version 8 and 9 but leaving the active database version at 8.
- **Solution**: Combine the addition of both `updatedAt` and `videoUrl` into `MIGRATION_7_8` and remove `MIGRATION_8_9`.
- **Implementation Shape**:
  ```kotlin
  private val MIGRATION_7_8 = object : Migration(7, 8) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE downloads ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE downloads ADD COLUMN videoUrl TEXT")
      }
  }
  ```
- **Edge Cases**: Fresh installs are unaffected as Room creates the schema from current entities.
- **Pitfalls / Do Not**: Do not bump database version to 9 without matching database class annotation version.
- **Validation**: Upgrade a device/emulator from v7 APK to v8 APK and verify no startup crash.
- **Docs**: None.

---

### Issue 2: ExoPlayer Double Release & Background Playback Crash
- **Severity**: Critical
- **Files**: [PlayerViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/PlayerViewModel.kt) (lines 52-68, 198-202) and [PlaybackService.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/service/PlaybackService.kt)
- **Problem**: `PlayerViewModel` directly instantiates and manages the lifecycle of the `ExoPlayer` instance, exposing it via `PlayerResolver.player = this`. In `onCleared()`, the ViewModel releases the player. `PlaybackService` is declared in `AndroidManifest.xml` but never started or bound.
- **Impact**: 
  1. Background audio playback is impossible: when leaving `PlayerScreen`, `PlayerViewModel.onCleared()` is called, releasing the player and halting playback immediately.
  2. If the foreground service is active, the `MediaSession` keeps running in the background. Clicking a button (lockscreen, notification, headset) on the released player triggers an immediate crash with `IllegalStateException: Player was already released`.
- **Root Cause**: The ViewModel owns the ExoPlayer instance instead of letting `PlaybackService` manage the player's lifecycle.
- **Solution**:
  1. Move the `ExoPlayer` instance creation and lifecycle ownership to `PlaybackService`.
  2. The `PlayerViewModel` must connect to `PlaybackService` using a `MediaController` via a `ListenableFuture`.
  3. When `PlayerViewModel.onCleared()` is called, it should only release the `MediaController` client, keeping the `ExoPlayer` playing inside the service.
- **Edge Cases**: Handle rotation correctly where the Activity/Screen is recreated but the service remains active.
- **Pitfalls**: Do not try to solve this by removing `player.release()` from the ViewModel, as this will lead to severe memory leaks since the ViewModel context will keep the player alive.
- **Validation**: Open a video, background the app/navigate away, verify music keeps playing, and verify lockscreen controls function without crashing.
- **Docs**: None.

---

### Issue 3: Broken VPS Sync Versioning (Data Loss / Out-of-Sync)
- **Severity**: Critical
- **File**: [sync.py](file:///d:/dev/media-nest/sync-server/app/routes/sync.py) (lines 24-25, 54-57)
- **Problem**: Sync version numbers are incremented per-device on push, but the pull query checks them globally (`version > after_version` across other devices).
- **Impact**: Devices will permanently miss sync updates from other devices. For example, if Device A pushes version 100, and Device B syncs, B's locally tracked version becomes 100. If Device C then pushes version 5, B's query `version > 100` will permanently exclude C's changes.
- **Root Cause**: Scoping version tracking per-device instead of using a global database sequence.
- **Solution**: Use the SQLite autoincrementing `id` column of the `changes` table as the global version indicator on pull.
- **Implementation Shape**:
  ```python
  # In sync.py pull route:
  cur = conn.execute(
      "SELECT * FROM changes WHERE device_id != ? AND id > ? ORDER BY id ASC LIMIT ?",
      (device_id, after_version, limit + 1),
  )
  ```
- **Validation**: Connect three clients, sync back and forth, and verify version sequence increments globally.
- **Docs**: None.

---

### Issue 4: Zip Slip Path Traversal Vulnerability
- **Severity**: Critical
- **File**: [RestoreRepository.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/backup/RestoreRepository.kt) (lines 66-72)
- **Problem**: The zip extraction logic resolves files using `File(target, name)` without validating path containment.
- **Impact**: Maliciously crafted backup ZIP files can overwrite arbitrary system or app-internal files via path traversal (`../`).
- **Root Cause**: Trusting input filenames from a zip entry.
- **Solution**: Validate that canonicalized target files remain within the destination directory.
- **Implementation Shape**:
  ```kotlin
  val canonicalTargetDir = target.canonicalFile
  val file = File(target, name)
  val canonicalDestFile = file.canonicalFile
  if (!canonicalDestFile.path.startsWith(canonicalTargetDir.path + File.separator)) {
      throw SecurityException("Path traversal attempt detected: ${entry.name}")
  }
  ```
- **Validation**: Restore test ZIP with entry `media/../../escaped.txt` and verify it aborts.
- **Docs**: Update restoration developer guides.

---

### Issue 5: Data Loss in Library Repair
- **Severity**: Major
- **File**: [LibraryRepair.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/backup/LibraryRepair.kt) (line 86)
- **Problem**: Extracts video IDs using `name.substringBefore("_")`.
- **Impact**: If a YouTube video ID contains an underscore (e.g. `dQw_w9WgXcQ`), the ID is truncated to `dQw`. The DB lookup fails, and the repair tool deletes the downloaded video as an orphan.
- **Root Cause**: Splitting filename on the first underscore.
- **Solution**: Split using `substringBeforeLast("_")` or cross-reference database path sets directly.
- **Validation**: Run repair with video ID `a_b_c` and verify files are not deleted.
- **Docs**: None.

---

### Issue 6: Background Foreground Service Launch Crash
- **File**: [SubscriptionWorker.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/worker/SubscriptionWorker.kt) (lines 60-62)
- **Problem**: Attempts to call `startForegroundService` from a background WorkManager worker.
- **Impact**: Throws `ForegroundServiceStartNotAllowedException` on Android 12+.
- **Root Cause**: Background restrictions on foreground services.
- **Solution**: Delegate downloading to a dedicated `DownloadWorker` or start the service only when the app is in the foreground.
- **Validation**: Trigger a subscription check in the background and verify no crashes.
- **Docs**: None.

---

### Issue 7: SQLite Transaction Leak & Database Locks
- **Severity**: Critical
- **Files**: [devices.py](file:///d:/dev/media-nest/sync-server/app/routes/devices.py), [sync.py](file:///d:/dev/media-nest/sync-server/app/routes/sync.py)
- **Problem**: SQLite write operations on thread-local connections implicitly open transactions, which are not rolled back on exceptions.
- **Impact**: Subsequent API calls on the same thread block indefinitely with `database is locked` errors.
- **Solution**: Add explicit `try-except` blocks and call `conn.rollback()` on exceptions.
- **Validation**: Force an insert exception and verify that subsequent requests succeed.
- **Docs**: None.

---

### Issue 8: Sync Client Path Leak & Broken Updates
- **Severity**: Major
- **File**: [SyncManager.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/sync/SyncManager.kt)
- **Problem**: Synced entities include device-specific absolute filesystem paths (`localFilePath`, `filePath`). Additionally, pull updates are ignored for existing local records.
- **Impact**: Remote clients receive invalid local file paths, breaking local playback. Metadata edits (like favorites) never sync to other devices.
- **Solution**: Clear/ignore file path values on push/pull, and update video metadata fields for existing entries on pulls.
- **Validation**: Toggle a favorite on Device A, sync, and verify the favorite state propagates to Device B.
- **Docs**: None.

---

### Issue 9: Flow Leak in Download Queue Processing
- **Severity**: Major
- **File**: [DownloadService.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/service/DownloadService.kt) (lines 109-118)
- **Problem**: Every invocation of `processQueue()` launches a new coroutine that starts a long-running, active collection of `preferences.maxConcurrentDownloads`.
- **Impact**: Flow collections leak and pile up in memory, causing database query storms and coroutine leaks.
- **Solution**: Use `first()` instead of `collect` to read values on demand.
- **Validation**: Monitor active coroutines during multiple download actions.
- **Docs**: None.

---

### Issue 10: Sync API Client Connection / Socket Leak
- **Severity**: Major
- **File**: [SyncRepository.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/sync/SyncRepository.kt) (lines 32-74)
- **Problem**: Network calls do not close the Response object on failures or null bodies.
- **Impact**: Socket/connection leaks lead to request failures over time.
- **Solution**: Wrap Response execution in a `.use { }` block.
- **Validation**: Run 100 sync requests sequentially and monitor open sockets.
- **Docs**: None.

---

### Issue 11: Broken Subfolder Nesting in UI
- **Severity**: Major
- **Files**: [LibraryViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/LibraryViewModel.kt), [LibraryScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt)
- **Problem**: Room database supports parentId for folder nesting, but the UI has no code to fetch subfolders, display them, or pass `parentId` during creation.
- **Impact**: Users cannot create or view subfolders.
- **Solution**: Add child subfolders flow inside `LibraryViewModel` and display them in `LibraryScreen`.
- **Validation**: Create a folder, open it, create a subfolder, and verify it appears.
- **Docs**: None.

---

### Issue 12: Missing File Check in Playback Resolver
- **Severity**: Major
- **File**: [PlayerViewModel.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/viewmodel/PlayerViewModel.kt)
- **Problem**: Player starts local file playback if database shows the download is completed, without checking if the physical file still exists on disk.
- **Impact**: Manual file deletion causes player crashes (black screen) instead of streaming fallback.
- **Solution**: Add `File(path).exists()` validation.
- **Validation**: Delete a downloaded file manually and play it; verify it falls back to streaming.
- **Docs**: None.

---

# Combined Verdict
- **Verdict**: BLOCK (issues must be fixed before release)
- **Overall Recommendation**: Switch to implement mode (`/do`) and fix the issues in order of severity, starting with database migrations and playback service lifecycles.

---

# Final Verification Checklist
- [ ] DB upgraded from v7 to v8 without startup crashes.
- [ ] Background audio playback functions properly when app is closed.
- [ ] Sync server handles versioning correctly across 3+ devices.
- [ ] ZIP extraction blocks path traversal files.
- [ ] Database transactions roll back on sync server crashes.
- [ ] No file orphans remain on video deletions.
