# Combined Review: MediaNest Full Implementation — All 9 Phases

## Anchor status
Built fresh — written to `.agents/anchors/medianest.md`

## Mode
Both — Architecture first, then Code

## Files checked
~50+ source files across all 9 plan phases (Phase 1–9) + build config + sync server.

## Subsystems reviewed
1. Phase 1 — Project scaffold, DI, DB, Navigation
2. Phase 2 — Media extraction (YouTubeExtractor, DownloaderProvider, HomeViewModel)
3. Phase 3 — Playback (PlayerViewModel, PlayerScreen, PlaybackService)
4. Phase 4 — Downloads (DownloadService, DownloadEntity, DownloadsScreen)
5. Phase 5 — Offline playback + Audio extraction (AudioExtractor, local file playback)
6. Phase 6 — Organization (Folders, favorites, LibraryScreen)
7. Phase 7 — Subscriptions (SubscriptionWorker, WorkScheduler, SubscriptionsScreen)
8. Phase 8 — Export/Import (BackupRepository, RestoreRepository, LibraryRepair, SettingsScreen)
9. Phase 9 — VPS Sync (sync-server Python, SyncManager, SyncRepository, SyncWorker)

---

# Architecture Review

## Anchor status
Built — see `.agents/anchors/medianest.md`

## Subsystems reviewed
All 9 phases above.

## Issues found

### Arch-1: Direct DAO injection in ViewModels (violates MVVM)
- **Subsystem**: Extraction (Phase 2) + Subscriptions (Phase 7)
- **File**: `HomeViewModel.kt:29-30`, `VideoDetailViewModel.kt:27-29`
- **Problem**: `HomeViewModel` injects `VideoDao` and `SubscriptionRepository` directly. `VideoDetailViewModel` injects both `SubscriptionRepository` and `SubscriptionDao`. Plan's architecture (MVVM + Repository) requires ViewModels to only inject Repositories.
- **Impact**: Future data layer changes (caching, validation, sync) require ViewModel changes instead of being centralized in repositories.
- **Root cause**: shortcut taken during implementation.
- **Solution**: Move `toggleFavorite` to `VideoRepository`, move subscription methods to `SubscriptionRepository`, remove direct DAO injections from ViewModels.
- **Validation**: All ViewModel → Repository calls compile. No DAO references remain in ViewModels.

### Arch-2: Player lifecycle ownership ambiguity
- **Subsystem**: Playback (Phase 3)
- **File**: `PlayerViewModel.kt:66`, `PlaybackService.kt:44-49`, `PlayerResolver.kt`
- **Problem**: Both `PlayerViewModel.onCleared()` and `PlaybackService.onDestroy()` call `player.release()` on the shared `ExoPlayer` instance (via `PlayerResolver`). The first release invalidates the player; the second crashes.
- **Impact**: Production crash when user leaves player and service stops. Double `player.release()` → `IllegalStateException`.
- **Root cause**: No single owner for the player lifecycle.
- **Solution**: ViewModel owns the player lifecycle. Service only owns `MediaSession`. Change `PlaybackService.onDestroy()` to release only the session, not the player. Remove `PlayerResolver` — ViewModel provides the surface view, service binds to the player via MediaSession.
  ```kotlin
  // PlaybackService.onDestroy()
  override fun onDestroy() {
      mediaSession?.release()
      mediaSession = null
      super.onDestroy()
  }
  ```
- **Edge cases**: Service outlives ViewModel → player already released, session release is safe. ViewModel killed while service alive → player released, session has no player, controls stop working.
- **Validation**: Navigate in/out of player 10 times. No crash. LeakCanary shows no leaked player.

### Arch-3: Hilt+WorkManager not wired — workers fail to instantiate
- **Subsystem**: All phases using WorkManager (7, 9)
- **File**: `MediaNestApp.kt:12`
- **Problem**: `@HiltWorker` requires `MediaNestApp` to implement `Configuration.Provider` and return a config with `HiltWorkFactory`. It does not.
- **Impact**: `SubscriptionWorker` and `SyncWorker` will crash with `Cannot instantiate worker` when WorkManager tries to create them.
- **Root cause**: Missing Hilt-WorkManager integration infrastructure.
- **Solution**: Make `MediaNestApp` implement `Configuration.Provider`:
  ```kotlin
  @HiltAndroidApp
  class MediaNestApp : Application(), Configuration.Provider {
      @Inject lateinit var hiltWorkFactory: HiltWorkFactory
      override val workManagerConfiguration: Configuration
          get() = Configuration.Builder()
              .setWorkerFactory(hiltWorkFactory)
              .build()
      // ... existing code ...
  }
  ```
- **Validation**: Schedule `SubscriptionWorker` → starts without crash. `doWork()` executes.

### Arch-4: Subscription checking never scheduled
- **Subsystem**: Subscriptions (Phase 7)
- **File**: `WorkScheduler.kt:12`, `MediaNestApp.kt`
- **Problem**: `WorkScheduler.scheduleSubscriptionCheck()` is defined but never called anywhere.
- **Impact**: Periodic subscription checking and auto-download is completely non-functional.
- **Root cause**: Missing initialization call in app startup.
- **Solution**: Add to `MediaNestApp.onCreate()`:
  ```kotlin
  WorkScheduler.scheduleSubscriptionCheck(this)
  ```
- **Validation**: WorkManager's `PeriodicWorkRequest` appears in `adb shell dumpsys jobscheduler`.

### Arch-5: SyncWorker returns success before sync completes
- **Subsystem**: VPS Sync (Phase 9)
- **File**: `SyncWorker.kt:18-21`
- **Problem**: `SyncManager.sync()` launches a fire-and-forget coroutine on a private `CoroutineScope`. The Worker returns `Result.success()` immediately without awaiting completion.
- **Impact**: WorkManager considers work done before sync finishes. App kill mid-sync loses data. No retry on failure.
- **Root cause**: Fire-and-forget coroutine pattern in a suspend Worker.
- **Solution**: Make `SyncManager.sync()` a suspend function, or return a `Deferred<SyncState>`:
  ```kotlin
  // SyncWorker.kt
  override suspend fun doWork(): Result {
      return try {
          syncManager.sync()  // make this suspend
          Result.success()
      } catch (e: Exception) {
          Result.retry()
      }
  }
  ```
  Change `SyncManager.sync()` from `fun sync()` to `suspend fun sync()` and remove the standalone `CoroutineScope`.
- **Validation**: Worker runs sync to completion before returning. `doWork()` duration matches actual sync time.

### Arch-6: applyUpsert missing 2 tables
- **Subsystem**: VPS Sync (Phase 9)
- **File**: `SyncManager.kt:276-341`
- **Problem**: `applyUpsert()` handles `videos`, `folders`, `video_folder_join`, `playlists`, `subscriptions` but NOT `downloads` or `playback_history`. Data pushed for these tables is silently lost on pull.
- **Impact**: Download status and playback history never sync across devices. One-way sync for these tables.
- **Root cause**: Incomplete implementation.
- **Solution**: Add cases:
  ```kotlin
  "downloads" -> {
      val entity = DownloadEntity(
          videoId = payload["videoId"] as? String ?: return,
          url = payload["url"] as? String ?: "",
          format = payload["format"] as? String ?: "",
          quality = payload["quality"] as? String ?: "",
          status = DownloadStatus.valueOf(payload["status"] as? String ?: "QUEUED"),
          // ... all other fields from payload ...
      )
      downloadDao.insert(entity)
  }
  "playback_history" -> {
      val entity = HistoryEntity(
          videoId = payload["videoId"] as? String ?: return,
          positionMillis = (payload["positionMillis"] as? Number)?.toLong() ?: 0L,
          playedAt = (payload["playedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
      )
      historyDao.upsert(entity)
  }
  ```
- **Validation**: Push download → pull on another device → download appears.

---

## Seam issues
All identified in Arch section above.

## Cross-cutting violations
- **Timestamp tracking**: No `updatedAt` on `DownloadEntity` — incremental sync can't detect status changes (Arch-5 downstream).
- **No transaction isolation** in RestoreRepository — partial restore possible.
- **Inconsistent query patterns** across DAOs (Flow vs one-shot) in SyncManager.

## Edge cases / pitfalls
- Existing app DB at version 2 will crash on upgrade (missing MIGRATION_2_3).
- Race between `pauseDownload()` coroutine save and the IO download loop writing progress.
- `Semaphore` permits never released if `enqueueDownload` coroutine crashes before `finally`.

## Docs to update
- `AGENTS.md` — add checklist items for the verification steps below.
- `MediaNest_Project_Plan.md` — mark sync-incremental-limitation in Phase 9.

## Verdict
**BLOCK** — Architecture issues (Arch-1 through Arch-6) affect all subsystems. Player, workers, and sync are fundamentally broken at the design level.

---

# Code Review

## Files checked
All files listed in subsystem coverage above.

## Verified correct
- Room entity definitions: FolderEntity self-referencing FK, VideoFolderJoin composite PK, SubscriptionEntity booleans → correct.
- MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7 SQL syntax → correct.
- LibraryViewModel `flatMapLatest` with `@OptIn(ExperimentalCoroutinesApi)` → correct.
- PlaybackService manifest declaration → correct.
- AudioExtractor uses `withContext(Dispatchers.IO)` → correct.
- `BackupModels.kt` all serializable → correct.

## Issues found

---

### Critical Issues (must fix before release)

#### C1 — YouTubeExtractor: videoId set to full URL, not short ID
- **File**: `YouTubeExtractor.kt:94,97`
- **Problem**: `item.url` returns `"https://www.youtube.com/watch?v=abc123"` but should be `item.id` which returns `"abc123"`. The full URL becomes the Room PK (`VideoEntity.id`). Cache lookups (`lastResultCache`) by short videoId miss. Navigation routes `videoDetail/{videoId}` contain URLs.
- **Impact**: PK becomes full URL. All downstream operations that use short videoId (cache lookup, navigation, PlayerViewModel) fail consistently.
- **Root cause**: `url` vs `id` confusion on `StreamInfoItem`.
- **Solution**: Change line 94 from `item.url` to `item.id`. Same for line 97 (channelId extraction in playlist items).
- **Edge cases**: Some YouTube IDs contain special characters → OK as String PK.
- **Pitfalls**: Do NOT truncate the URL — use the actual `item.id` property.
- **Validation**: Extract a video → PK in Room is `"abc123"`. Navigate to detail → cache hit.

#### C2 — YouTubeExtractor.extractChannel returns empty uploads
- **File**: `YouTubeExtractor.kt:115-126`
- **Problem**: After fetching channel info via `ChannelInfo.getInfo()`, `uploads = emptyList()` hardcoded. `info.relatedItems` contains actual uploads but is ignored.
- **Impact**: Channel extraction completely broken. Subscription sync detects zero uploads. HomeScreen channel result shows empty.
- **Root cause**: Placeholder code never filled in.
- **Solution**: Replace `emptyList()` with extraction from `info.relatedItems`:
  ```kotlin
  val uploads = info.relatedItems?.mapNotNull { item ->
      runCatching {
          ExtractedVideoInfo(
              videoId = item.id,
              title = item.name ?: "Unknown",
              channelName = info.name ?: "Unknown",
              channelId = info.url ?: "",
              durationSeconds = (item as? StreamInfoItem)?.duration ?: 0L,
              thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
              description = null,
              uploadDate = null
          )
      }.getOrNull()
  } ?: emptyList()
  ```
- **Edge cases**: Channel with no uploads → empty list (correct). Very large channel → only first page returned.
- **Validation**: `extractChannel("youtube.com/@ChannelName")` returns >0 videos.

#### C3 — PlayerViewModel position tracking never updates UI
- **File**: `PlayerViewModel.kt:142-149`
- **Problem**: `startPositionTracking()` saves position to DB every 5s but never writes to `_uiState.value.positionMs`. The Slider uses `state.positionMs` which is only set at `initialize()` and never updated.
- **Impact**: Seek slider frozen at 0 throughout playback. Time display shows `0:00` permanently.
- **Root cause**: Missing UI state update in tracking loop.
- **Solution**: Add position update:
  ```kotlin
  private fun startPositionTracking() {
      positionTrackingJob?.cancel()
      positionTrackingJob = viewModelScope.launch {
          while (true) {
              delay(1_000)
              val pos = player.currentPosition
              _uiState.value = _uiState.value.copy(positionMs = pos)
              savePosition()
          }
      }
  }
  ```
- **Edge cases**: Duration 0 (livestream) → slider range is 0..1, positionMs = 0, no issue.
- **Validation**: Play video → slider moves with playback.

#### C4 — DownloadService missing startForeground() on actions
- **File**: `DownloadService.kt:92-110`
- **Problem**: `onStartCommand()` only calls `startForeground()` in the `else` branch. For `ACTION_PAUSE`, `ACTION_RESUME`, `ACTION_CANCEL`, the service starts but never calls `startForeground()`. Android requires it within ~5 seconds.
- **Impact**: `RemoteServiceException` / ANR on Android 12+ for any pause/resume/cancel action.
- **Root cause**: `startForeground()` not called in action-specific branches.
- **Solution**: Move `startForeground()` before the `when` block:
  ```kotlin
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      startForeground(NOTIFICATION_ID, buildNotification(0, 0))
      when (intent?.action) { ... }
      processQueue()
      return START_STICKY
  }
  ```
- **Edge cases**: Multiple rapid intents → `startForeground()` called multiple times → safe (idempotent after first). Service already foreground → also safe (no-op update).
- **Validation**: Pause a download → no ANR.

#### C5 — SettingsScreen unsafe FileOutputStream cast
- **File**: `SettingsScreen.kt:77`
- **Problem**: `context.contentResolver.openOutputStream(it) as FileOutputStream` — SAF returns `ParcelFileDescriptor.AutoCloseOutputStream` on Android 10+, not `FileOutputStream`.
- **Impact**: `ClassCastException` crashes export on most modern devices.
- **Root cause**: Wrong assumption about SAF return type.
- **Solution**: Change to `OutputStream` parameter type:
  ```kotlin
  // SettingsScreen.kt
  val outputStream = context.contentResolver.openOutputStream(it) ?: return@let
  viewModel.exportToFile(outputStream)
  ```
  Change `BackupRepository.exportToZip(outputStream: FileOutputStream)` → `exportToZip(outputStream: OutputStream)`. Change `ZipOutputStream(outputStream)` constructor (takes `OutputStream`, already works).
- **Validation**: Export on Android 14 device → no crash.

#### C6 — SyncWorker returns success before sync completes
- **File**: `SyncWorker.kt:18-21`
- **Problem**: `syncManager.sync()` launches fire-and-forget coroutine. Worker returns immediately.
- **Impact**: WorkManager considers work done. App kill mid-sync = data loss. No retry.
- **Root cause**: Non-suspend `sync()` + standalone `CoroutineScope`.
- **Solution**: Make `sync()` suspend, remove standalone scope, let Worker manage scope:
  ```kotlin
  // SyncManager.kt
  suspend fun sync(): SyncState = withContext(Dispatchers.IO) {
      // existing logic, no scope.launch
  }
  // SyncWorker.kt
  override suspend fun doWork(): Result {
      return try {
          val state = syncManager.sync()
          if (state is SyncState.Error) Result.retry() else Result.success()
      } catch (e: Exception) {
          Result.retry()
      }
  }
  ```
- **Validation**: Worker duration reflects actual sync time. `Result.retry()` returned on failure.

#### C7 — player.release() called twice (ViewModel + Service)
- **File**: `PlayerViewModel.kt:197`, `PlaybackService.kt:46`
- **Problem**: Both call `player.release()` on the same ExoPlayer via `PlayerResolver`. First release invalidates the instance.
- **Impact**: `IllegalStateException` crash when leaving player screen while service is active.
- **Root cause**: Shared player with two owners.
- **Solution**: Service releases only MediaSession, not the player. ViewModel manages player lifecycle:
  ```kotlin
  // PlaybackService.onDestroy()
  override fun onDestroy() {
      mediaSession?.release()
      mediaSession = null
      super.onDestroy()
  }
  ```
- **Validation**: Play → leave screen → swipe app from recents → no crash.

---

### High Issues

| ID | File | Line | Problem | Solution |
|----|------|------|---------|----------|
| H1 | AndroidManifest.xml | 7-8 | Missing `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission for API 34+ | Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />` |
| H2 | DownloaderProvider.kt | 11-17 | `errorStream` not read on HTTP 4xx/5xx | Read `errorStream` when responseCode >= 400 |
| H3 | YouTubeExtractor.kt | 79 | `channelId` set to channel URL, not raw ID | Strip channel ID from URL or use `NewPipe.getService(0).getChannelLHFactory().fromUrl(url).getId()` |
| H4 | DownloadService.kt | 158 | Re-extraction uses stream URL, not video page URL | Add `videoUrl` field to `DownloadEntity`, use it for re-extraction |
| H5 | DownloadService.kt | 260 | `pauseDownload()` overwrites saved progress with 0f | Remove progress update from `pauseDownload()` — download loop already saves it |
| H6 | DownloadService.kt | 82,87-89 | `Semaphore` never updated when user changes max concurrent | Replace with dynamic permit count or remove semaphore (slot calc in `processQueue` is sufficient) |
| H7 | DownloadService.kt | 80 | `activeJobs` mutableMap not thread-safe | Use `ConcurrentHashMap` or wrap in `Mutex` |
| H8 | SubscriptionWorker.kt | 20 | `@AssistedInject` should be `@Inject` | Change to `@Inject constructor(` |
| H9 | WorkScheduler.kt + MediaNestApp.kt | 12 | `scheduleSubscriptionCheck()` never called | Add to `MediaNestApp.onCreate()` |
| H10 | MediaNestApp.kt | 12 | Missing `Configuration.Provider` for Hilt workers | Implement `Configuration.Provider` with `@Inject lateinit var hiltWorkFactory: HiltWorkFactory` |
| H11 | SyncManager.kt | 276-341 | `applyUpsert` missing download + history tables | Add `"downloads"` and `"playback_history"` cases |

### Medium Issues (18)

| ID | File | Line | Problem | Solution |
|----|------|------|---------|----------|
| M1 | LibraryRepair.kt | 51 | `startsWith` matches wrong files (`"abc"` matches `"abcdef.mp4"`) | Change to `name.startsWith("${video.id}_")` |
| M2 | BackupRepository.kt + RestoreRepository.kt | 88-109 | `preferences.json` never written; restore silently loses prefs | Write `preferences.json` to ZIP or extract prefs from `BackupData` JSON |
| M3 | SyncManager.kt | 162-248 | Push payloads missing `createdAt`, `updatedAt`, `downloadedAt` | Add all entity fields to each `SyncPushItem` |
| M4 | SyncManager.kt | 298-340 | `applyUpsert` folders/playlists/subscriptions use local timestamps | Extract timestamp fields from payload JSON |
| M5 | SyncManager.kt | 176 | No `updatedAt` on DownloadEntity → incremental sync misses status changes | Add `updatedAt` to `DownloadEntity`, filter on that |
| M6 | YouTubeExtractor.kt | 61 | `videoOnlyStreams` format `"video_only"` invisible in UI filter | Include in filter: `format == "video" \|\| format == "video_only"` |
| M7 | PlayerViewModel.kt | 110 | Restored position not reflected in UI slider | Add `_uiState.value = _uiState.value.copy(positionMs = startPosition)` after seek |
| M8 | PlayerScreen.kt | 172-190 | Error overlay constrained inside Column | Wrap Scaffold content in a `Box` and overlay error at Box level |
| M9 | DatabaseModule.kt | — | Missing `MIGRATION_2_3` — v2 DB upgrade crashes | Add migration or switch to `fallbackToDestructiveMigration()` |
| M10 | DownloadService.kt | 209 | `tmpFile.renameTo()` return value ignored | Check return, throw `IOException` on failure |
| M11 | DownloadService.kt | 235-243 | `tmpFile` not deleted on exception | Add `tmpFile.delete()` in catch block |
| M12 | libs.versions.toml | 22,60 | ffmpeg-kit artifact mismatch (jamaismagic fork vs plan's arthecnica) | Verified: jamaismagic 6.1.4 fork resolves correctly, official arthenica fails to resolve |
| M13 | DownloadService.kt | 87-89 | `maxConcurrent` loaded once in `onCreate`, never re-collected | Collect `preferences.maxConcurrentDownloads` as ongoing Flow |
| M14 | VideoDetailViewModel.kt | 27-29 | Injects `SubscriptionDao` directly, bypassing repository | Remove `SubscriptionDao` injection, use `SubscriptionRepository` |
| M15 | LibraryScreen.kt | 188 | Favorite icon always filled for unchecked state | Use `Icons.Outlined.FavoriteBorder` when `!video.favorite` |
| M16 | AppNavigation.kt | 47-51 | `onSubscribe` not passed to HomeScreen | Wire `onSubscribe` callback in `AppNavigation` |
| M17 | sync-server/routes/sync.py | 22-33 | Concurrent push race condition on version counter | Use `BEGIN EXCLUSIVE` transaction around SELECT+INSERT loop |
| M18 | RestoreRepository.kt | 62 | Reads entire ZIP media into memory | Write media files to disk as read from ZIP stream |

### Minor / Nitpick

| ID | File | Problem |
|----|------|---------|
| N1 | DatabaseModule.kt | MIGRATION_5_6 uses `''` default for `uploaderName` but entity has `null` default — inconsistency |
| N2 | LibraryRepair.kt:60 | `pathsFixed` counter conflates repair with clearing paths |
| N3 | sync-server/config.py:4 | Dead `SYNC_API_KEY` global config (unused code) |
| N4 | SyncManager.kt:66 | Uncancelled `CoroutineScope` in singleton — coroutine leak |
| N5 | sync-server/main.py:22 | Duplicated `DATABASE_PATH` default (also in config.py) |
| N6 | RestoreRepository.kt:75-108 | No transaction wrapping — slow restore for many rows |
| N7 | sync-server/routes/sync.py:9 | Misleading `Header(...)` default in `verify_device` helper |
| N8 | sync-server/routes/devices.py:23 | API key as query param instead of header (log leakage) |
| N9 | BackupModels.kt | No `@EncodeDefault` — nullable fields always serialized, bloat |

### Edge cases / pitfalls
- `DownloadService.pauseDownload()` race with download loop: the IO loop detects PAUSED status and saves progress. `pauseDownload()` then cancels the job and overwrites progress with 0. Order depends on dispatcher scheduling.
- Player `STATE_ENDED` listener fires for old video if user navigates quickly: `saveFinalPosition()` saves the stale `currentVideoId`.
- `BackupRepository` serializes `localFilePath` as absolute path — on restore, path is wrong (different device). Backup needs relative paths.

---

## Regression risks
1. Fixing C7 (double player release) changes service session lifecycle — test background playback still works.
2. Fixing C5 (FileOutputStream → OutputStream) changes backup API — verify Hilt provides correct stream.
3. Fixing H8 (subscription worker) touches WorkManager config — test other workers.

## Docs to update
- `AGENTS.md` — add `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission note, Hilt-WorkManager config note.
- `MediaNest_Project_Plan.md` — update `DownloadEntity` schema with `videoUrl`, `updatedAt` fields.

---

# Combined Verdict

## Arch issues that affect code correctness
All 6 architecture issues (Arch-1 through Arch-6) directly affect code correctness:
- Direct DAO injection = untestable, unmaintainable ViewModels
- Ambiguous player lifecycle = crash
- Missing Hilt-WorkManager config = workers unusable
- Unwired subscription checking = dead feature
- Fire-and-forget sync = unreliable sync
- Missing `applyUpsert` tables = data loss

## Seam issues found in synthesis pass
- **Chained failure**: Missing Hilt-WorkManager config (Arch-3) → `SubscriptionWorker` can't instantiate (H8) → `scheduleSubscriptionCheck()` never called (H9) → Phase 7 entirely broken. Fixing all three is required.
- **Chained failure**: `sync()` non-suspend (Arch-5) → Worker returns early (C6) → no retry on failure → missed pushes permanently lost. Fix requires both the suspend refactor AND the `applyUpsert` table additions.
- **Chained failure**: Missing `MIGRATION_2_3` (M9) + DB version at 7 = existing users with v2 DB crash. Only manifests if there are existing users. For a fresh install, no issue. But the migration chain must be complete for any upgrade path.

## Beginner implementation notes
1. Fix in order: Arch issues first (architecture), then Critical bugs (code), then High, then Medium.
2. Do NOT attempt to fix all 45+ issues at once. Batch by subsystem:
   - **Batch 1 (Player)**: C3, C7, M7, M8, H1, H3 — fixes in PlayerViewModel, PlaybackService, AndroidManifest
   - **Batch 2 (Extraction)**: C1, C2, H2, H3, M6 — fixes in YouTubeExtractor, DownloaderProvider
   - **Batch 3 (Downloads)**: C4, H4, H5, H6, H7, M9, M10, M11, M13 — fixes in DownloadService, DatabaseModule
   - **Batch 4 (Subs + Workers)**: H8, H9, H10, Arch-3 — fixes in SubscriptionWorker, MediaNestApp
   - **Batch 5 (Export/Import)**: C5, M1, M2, M18 — fixes in SettingsScreen, BackupRepository, RestoreRepository
   - **Batch 6 (Sync)**: C6, C8, H11, M3, M4, M5, M17 — fixes in SyncManager, SyncWorker, sync-server
   - **Batch 7 (UI)**: M14, M15, M16 — UX fixes
3. After each batch: `./gradlew :app:assembleDebug` must succeed.
4. After Batch 1: test player (play, seek, speed, background, resume).
5. After Batch 3: test download (start, pause, resume, cancel, retry, concurrent limit).

## Final verification checklist
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] Extract video → Room PK is short videoId
- [ ] Channel extraction returns >0 uploads
- [ ] Player slider moves with playback
- [ ] Player screen → home → no crash
- [ ] Download starts, pauses, resumes, cancels without ANR
- [ ] Export on Android 14+ device works
- [ ] SubscriptionWorker can be instantiated (no Hilt crash)
- [ ] `scheduleSubscriptionCheck()` called on app start
- [ ] SyncWorker actually performs sync before returning
- [ ] Pulled sync data includes downloads + history
- [ ] v2 DB upgrade path exists (or destructive migration enabled)
- [ ] Concurrent download limit changes take effect immediately
- [ ] LibraryScreen favorite icon shows outline when not favorited
- [ ] HomeScreen subscribe buttons wired to ViewModel
- [ ] Push payloads include all entity fields
- [ ] `applyUpsert` handles all 7 tables
- [ ] LibraryRepair filename matching uses `_` separator

## Overall recommendation
**BLOCK** — Do NOT release until all Critical (7) and High (11) issues are fixed. The player crashes, export crashes, and foreground service crashes on modern Android make the app unusable for production. The channel extraction, subscription checking, and sync features are completely broken at the architectural level and work at no level.

Recommended minimum for Beta release: Fix all Critical + all High issues. Medium issues can be deferred to Beta 2.
