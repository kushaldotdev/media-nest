---
project: MediaNest
built: 2026-06-22 21:32
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
---

# Architecture Anchor: MediaNest

## End Goal (2 sentences max)
Android-first offline YouTube media library with download, playback, organization, metadata sync across devices. All 9 phases implemented: scaffold → extraction → playback → downloads → offline playback → organization → subscriptions → export/import → VPS sync.

## Critical Invariants
- Local Room DB is source of truth. Media files on filesystem, never in DB blobs.
- `VideoEntity.id` = YouTube video short ID (not full URL). All downstream references use short ID.
- Player lifecycle owned by ViewModel only. Service owns only MediaSession.
- `@HiltWorker` requires `MediaNestApp` to implement `Configuration.Provider` with `HiltWorkFactory`.
- `startForeground()` must be called on EVERY `onStartCommand()` invocation, not just on first start.

## Key Data Flow
```
YouTube URL → NewPipeExtractor → ExtractedVideoInfo → VideoEntity (Room)
                                                      ↓
DownloadService (Foreground) → media file at filesDir/MediaNest/{video,audio}/
                                                      ↓
PlayerViewModel → ExoPlayer (local file:// or stream URL) → PlayerScreen
                                                      ↓
HistoryDao.upsert(videoId, positionMs, playedAt) — periodic save every 5s
                                                      ↓
SyncManager → SyncRepository (OkHttp) → FastAPI sync server → other devices
```

## Subsystems
| Name | Responsibility | Key Files |
|------|---------------|-----------|
| Phase 1 — Scaffold | Hilt, Room, Nav, build config | `AppDatabase.kt`, `DatabaseModule.kt`, `MainActivity.kt`, `libs.versions.toml` |
| Phase 2 — Extraction | YouTube metadata + streams | `YouTubeExtractor.kt`, `DownloaderProvider.kt`, `VideoRepository.kt`, `HomeViewModel.kt` |
| Phase 3 — Playback | ExoPlayer, controls, background | `PlayerViewModel.kt`, `PlayerScreen.kt`, `PlaybackService.kt`, `PlaybackPreferences.kt` |
| Phase 4 — Downloads | Queue, foreground service, progress | `DownloadService.kt`, `DownloadRepository.kt`, `DownloadsViewModel.kt`, `DownloadEntity.kt` |
| Phase 5 — Offline | Local file playback, audio extraction | `AudioExtractor.kt`, `PlayerViewModel.kt` (local file path), `DownloadEntity` (audio_extracted) |
| Phase 6 — Organization | Folders, favorites, search | `FolderEntity.kt`, `VideoFolderJoin.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt` |
| Phase 7 — Subscriptions | Channel/playlist subs, periodic check | `SubscriptionEntity.kt`, `SubscriptionRepository.kt`, `SubscriptionWorker.kt`, `WorkScheduler.kt` |
| Phase 8 — Export/Import | ZIP backup/restore, library repair | `BackupRepository.kt`, `RestoreRepository.kt`, `LibraryRepair.kt`, `ExportImportViewModel.kt` |
| Phase 9 — VPS Sync | FastAPI server, Android sync client | `sync-server/` (Python), `SyncManager.kt`, `SyncRepository.kt`, `SyncWorker.kt` |

## Known Danger Zones
1. **Double player.release()** — `PlayerViewModel` + `PlaybackService` both release the same ExoPlayer. Fix: Service releases only MediaSession.
2. **Missing Hilt-WorkManager config** — `MediaNestApp` must implement `Configuration.Provider` or `@HiltWorker` crashes.
3. **Missing `startForeground()` on action intents** — DownloadService crashes on PAUSE/RESUME/CANCEL.
4. **Fire-and-forget SyncManager.sync()** — Worker returns before sync completes.
5. **`applyUpsert` incomplete** — Downloads + history tables not handled on pull.
6. **Missing MIGRATION_2_3** — DB upgrade from v2 crashes.
7. **`channelId` = URL not ID** — Subscription lookups mismatch.
8. **`videoId` = full URL in playlist/channel extraction** — PK corruption.

## Cross-cutting Concerns
- All DB migrations must be explicit (no destructive migration where data matters).
- Every `suspend` DAO call off MainThread — Room enforces this, but ViewModel scope must be correct.
- `FileOutputStream` vs `OutputStream` — SAF returns different stream types than raw file access.
- `Semaphore` permits are fixed at construction — don't use for dynamic concurrent limits.
