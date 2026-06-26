---
project: MediaNest
built: 2026-06-26 18:55
source-files:
  - path: .agents/plan/2026-06-22-phase1-project-scaffold.md
    mtime: 2026-06-22 00:00
  - path: .agents/plan/2026-06-22-phase2-media-extraction.md
    mtime: 2026-06-22 00:00
  - path: .agents/plan/2026-06-22-phase3-playback.md
    mtime: 2026-06-22 00:00
  - path: .agents/plan/2026-06-22-phase4-downloads.md
    mtime: 2026-06-22 00:00
  - path: .agents/plan/2026-06-22-phase5-offline-playback.md
    mtime: 2026-06-22 00:00
  - path: .agents/plan/2026-06-22-phase6-organization.md
    mtime: 2026-06-22 00:00
  - path: .agents/plan/2026-06-22-phase7-subscriptions.md
    mtime: 2026-06-22 00:00
  - path: .agents/plan/2026-06-22-phase8-export-import.md
    mtime: 2026-06-22 00:00
  - path: .agents/plan/2026-06-22-phase9-vps-sync.md
    mtime: 2026-06-22 00:00
  - path: MediaNest_Project_Plan.md
    mtime: 2026-06-22 00:00
  - path: app/src/main/java/com/example/medianest/data/backup/LibraryRepair.kt
    mtime: 2026-06-26 18:44
  - path: app/src/main/java/com/example/medianest/data/backup/RestoreRepository.kt
    mtime: 2026-06-23 01:22
  - path: app/src/main/java/com/example/medianest/data/sync/SyncManager.kt
    mtime: 2026-06-23 01:23
  - path: app/src/main/java/com/example/medianest/data/sync/SyncRepository.kt
    mtime: 2026-06-23 01:20
  - path: app/src/main/java/com/example/medianest/di/DatabaseModule.kt
    mtime: 2026-06-26 18:44
  - path: app/src/main/java/com/example/medianest/service/DownloadService.kt
    mtime: 2026-06-26 18:44
  - path: app/src/main/java/com/example/medianest/service/PlaybackService.kt
    mtime: 2026-06-23 01:19
  - path: app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt
    mtime: 2026-06-23 01:20
  - path: app/src/main/java/com/example/medianest/ui/screens/PlayerScreen.kt
    mtime: 2026-06-23 01:19
  - path: app/src/main/java/com/example/medianest/ui/viewmodel/LibraryViewModel.kt
    mtime: 2026-06-23 01:20
  - path: app/src/main/java/com/example/medianest/ui/viewmodel/PlayerViewModel.kt
    mtime: 2026-06-23 01:19
  - path: app/src/main/java/com/example/medianest/worker/SubscriptionWorker.kt
    mtime: 2026-06-23 01:20
  - path: app/src/main/java/com/example/medianest/worker/SyncWorker.kt
    mtime: 2026-06-23 01:20
  - path: build.bat
    mtime: 2026-06-23 01:28
  - path: sync-server/app/routes/devices.py
    mtime: 2026-06-23 01:21
  - path: sync-server/app/routes/sync.py
    mtime: 2026-06-23 01:21
---

# Architecture Anchor: MediaNest

## End Goal (2 sentences max)
An Android-first offline YouTube media manager and playback library that maintains local metadata and handles downloads, playback persistence, subscriptions, and optional metadata sync across devices using a lightweight FastAPI VPS sync server.

## Critical Invariants
- **Local DB is Source of Truth**: The local Room database on the Android app is the single source of truth for the local device; actual media files are stored on the local filesystem, never in DB blobs.
- **Video ID Consistency**: `VideoEntity.id` must always be the YouTube video short ID (not the full URL). All downstream tables and references use this short ID.
- **Service Player Ownership**: Playback player lifecycle is owned and managed directly by `PlaybackService`. Views and ViewModels connect to the service using a `MediaController`.
- **WorkManager Hilt Requirements**: `@HiltWorker` requires `MediaNestApp` to implement `Configuration.Provider` with `HiltWorkFactory`.
- **Foreground Service Invariant**: `startForeground()` must be called on EVERY `onStartCommand()` invocation of foreground services (e.g. `DownloadService`), not just on the first start.
- **Sync Path Sanitization**: Sync metadata does not transmit media files or absolute local device filesystem paths (e.g., `/data/user/0/...`) to avoid path leakage and configuration conflicts.
- **SQLite Concurrency & Transactions**: Transactions on both client (Room) and server (SQLite WAL) databases must be immediate/exclusive under write conditions to prevent database locking and deadlocks.

## Key Data Flow
```
YouTube URL → NewPipeExtractor → ExtractedVideoInfo → VideoEntity (Room)
                                                      ↓
DownloadService (Foreground) → media file at filesDir/MediaNest/{video,audio}/
                                                      ↓
PlayerViewModel → MediaController → PlaybackService (ExoPlayer local file:// or stream URL) → PlayerScreen
                                                      ↓
HistoryDao.upsert(videoId, positionMs, playedAt) — periodic save every 5s
                                                      ↓
SyncManager → SyncRepository (OkHttp) → FastAPI sync server (autoincrement id sequence version) → other devices
```

## Subsystems
| Name | Responsibility | Key Files |
|------|---------------|-----------|
| Phase 1 — Scaffold | Hilt, Room, Nav, build config | `AppDatabase.kt`, `DatabaseModule.kt`, `MainActivity.kt`, `libs.versions.toml` |
| Phase 2 — Extraction | YouTube metadata + streams | `YouTubeExtractor.kt`, `DownloaderProvider.kt`, `VideoRepository.kt`, `HomeViewModel.kt` |
| Phase 3 — Playback | ExoPlayer, controls, background | `PlayerViewModel.kt`, `PlayerScreen.kt`, `PlaybackService.kt`, `PlaybackPreferences.kt` |
| Phase 4 — Downloads | Queue, foreground service, progress | `DownloadService.kt`, `DownloadRepository.kt`, `DownloadsViewModel.kt`, `DownloadEntity.kt` |
| Phase 5 — Offline | Local file playback, audio extraction | `AudioExtractor.kt`, `PlayerViewModel.kt` (local file path), `DownloadEntity` (audio_extracted) |
| Phase 6 — Organization | Folders, subfolders, favorites, search | `FolderEntity.kt`, `VideoFolderJoin.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt` |
| Phase 7 — Subscriptions | Channel/playlist subs, periodic check | `SubscriptionEntity.kt`, `SubscriptionRepository.kt`, `SubscriptionWorker.kt`, `WorkScheduler.kt` |
| Phase 8 — Export/Import | ZIP backup/restore, library repair | `BackupRepository.kt`, `RestoreRepository.kt`, `LibraryRepair.kt`, `ExportImportViewModel.kt` |
| Phase 9 — VPS Sync | FastAPI server, Android sync client | `sync-server/` (Python), `SyncManager.kt`, `SyncRepository.kt`, `SyncWorker.kt` |

## Known Danger Zones
1. **Zip Slip / Path Traversal**: Extracting ZIP backups containing relative paths (`../`) that can escape the sandbox directory. Checked via `canonicalPath.startsWith` validation.
2. **SQLite Locking / Deadlocks**: Concurrent read/write connections in Python/SQLite when transactions start deferred or exceptions leave connections open. Resolved with `BEGIN IMMEDIATE` and `conn.rollback()` in python exception handlers.
3. **Absolute Path Leakage**: Propagating local device paths to other client devices via the sync server. Fixed by clearing absolute paths on push and ignoring/resolving them on pull.
4. **WorkManager Assisted Injection**: Missing `@AssistedInject` or `@Assisted` annotations on `@HiltWorker` classes causing runtime crashes.
5. **OkHttp Socket Leakage**: Unclosed response streams from missing `.use` wrappers.
6. **Double player.release()** — ViewModel and PlaybackService both releasing player. Resolved by moving player lifecycle ownership to PlaybackService.
7. **Room Migration Mismatch** — Startup crash due to mismatch between v8/v9 schema columns. Consolidated inside a single `MIGRATION_7_8` migration step.

## Cross-cutting Concerns
- **Foreground Service Start Restrictions**: Background service startup failures under Android 12+. Must be wrapped in try-catch blocks for `ForegroundServiceStartNotAllowedException`.
- **Explicit Migrations**: All DB migrations must be explicit (no destructive migrations where user data matters).
- **Coroutine flow collection leakage**: Infinite flows (like Datastore/Preferences) collected inside repeating methods, creating duplicate subscriptions and memory leaks. Resolved by using `.first()`.
- Every `suspend` DAO call off MainThread — Room enforces this, but ViewModel scope must be correct.
- `FileOutputStream` vs `OutputStream` — SAF returns different stream types than raw file access.
