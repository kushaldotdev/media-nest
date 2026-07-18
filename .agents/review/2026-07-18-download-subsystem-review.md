# Download Subsystem Review and Remediation Handoff

**Date:** 2026-07-18  
**Scope:** Download queue, transfer, merge, audio extraction, Downloads tab, download persistence, download sync, storage migration, subscription handoff.  
**Review type:** Static code review. Current worktree includes uncommitted changes in `AudioExtractor.kt`, `DownloadService.kt`, and `DownloadsViewModel.kt`.  
**Verdict:** Block release. Critical file-identity, sync-identity, and cancellation defects can corrupt or delete wrong media. Several major defects leave jobs permanently stuck, falsely completed, or orphaned.

## Purpose

This document is standalone. It describes current behavior, concrete failure cases, remediation contracts, and validation targets so an engineer can fix subsystem without prior review context.

Do not treat staged changes as complete fixes. They introduce audio extraction progress and cancellation handling, but still have global FFmpeg interference, cancellation gaps, and lifecycle failures described below.

## User-visible requirements

1. Cancel from Downloads tab must retain row as `CANCELED`; it must expose Restart and Delete.
2. Cancel must remove owned partial `.tmp` artifacts. Restart starts transfer from byte zero.
3. Delete must stop all work owning row before database row and owned files are removed.
4. Merge and extraction progress must update while work runs, cancel promptly, and never cancel unrelated work.
5. A failed, canceled, or interrupted audio extraction must be retryable.
6. Distinct downloads must never share temp or final files.
7. Resume must only append bytes from exact same remote representation.

## Current architecture

### Data model and ownership

`DownloadEntity` represents transfer and extracted-audio rows. Database uniqueness is `(videoId, format, quality)`. `DownloadService` owns ordinary download jobs, an in-memory `activeJobs` map, in-memory progress, and an in-memory paused-download cache. `DownloadsViewModel` creates and deletes rows, sends service intents, and now starts Downloads-screen audio extraction in `viewModelScope`.

Other screens can enqueue downloads and audio extraction:

- `HomeViewModel.kt`
- `LibraryViewModel.kt`
- `VideoDetailViewModel.kt`

`AudioExtractor` performs native extraction, FFmpeg stream copy, then MP3 transcode fallback. `DownloadService` downloads video/audio streams, merges video-only plus audio using FFmpeg, and records completion in Room. `SyncManager` syncs download rows. `ExportImportViewModel` can migrate output folders while service jobs run.

### Current staged changes

The working tree modifies:

- `service/AudioExtractor.kt`
- `service/DownloadService.kt`
- `ui/viewmodel/DownloadsViewModel.kt`

Staged behavior adds extraction progress callbacks, native/FFmpeg fallback paths, a process-wide FFmpeg session cancellation scan, global FFmpeg statistics callbacks, and `activeExtractions` job tracking in `DownloadsViewModel`. It also changes merge flow to FFmpeg stream-copy plus transcode fallback.

Those changes do not solve operation ownership. FFmpeg callbacks and cancellation are process-global, while several operations can run concurrently. Extraction remains ViewModel-owned rather than durable service/worker-owned.

## Critical findings

### C1. Distinct database rows collide on temp and final files

**Locations**

- `app/src/main/java/com/example/medianest/data/local/entity/DownloadEntity.kt:27`
- `app/src/main/java/com/example/medianest/service/DownloadService.kt:746-747`
- `app/src/main/java/com/example/medianest/service/DownloadService.kt:858-860`

**Problem**

Room allows both `video` and `video_only` rows for same `videoId` and quality because `format` is part of unique key. Temp and final filenames omit `format`, so both valid rows resolve to same files.

**Failure case**

Queue progressive video and video-only stream at same quality with concurrency above one. Both jobs append same `.tmp`. One job can rename or delete it while other writes. Final paths collide too. Rows can report success for corrupt data, delete each other's files, or point at same output.

**Required fix contract**

- Give every row immutable filesystem identity independent of display metadata. Prefer stable row UUID created before work starts; a local Room numeric ID is acceptable only after insert and must not be sync identity.
- Include that identity in every temp, audio-temp, merge-temp, final, and extraction path.
- Persist actual owned paths, or derive them only from immutable identity plus persisted output directory captured at job creation.
- Never identify files only by `videoId`, format label, title, or quality.

**Acceptance checks**

- Concurrent `video`, `video_only`, and audio rows for one video produce distinct files.
- Cancel, restart, and delete one row do not affect any other row's files.

### C2. Sync uses device-local Room IDs as global download identity

**Location**

- `app/src/main/java/com/example/medianest/data/sync/SyncManager.kt:387-417`

**Problem**

Sync resolves downloaded rows by local auto-generated numeric `id` before business identity. Room IDs are not portable across devices.

**Failure case**

Device A has download ID 5 for video X. Device B has unrelated download ID 5 for video Y. Pull from A finds B row by ID and overwrites it with A's URL, status, metadata, and progress.

**Required fix contract**

- Add sync-safe immutable download UUID, generated locally once.
- Match downloads only by UUID. Do not use Room primary key across devices.
- Define whether sync carries only download intent/history or transient transfer state. Do not blindly apply remote `DOWNLOADING`, progress, local paths, ephemeral URLs, or active-job status to a local active job.
- Add sync tombstones for delete and an `applyDelete` branch for downloads.

**Acceptance checks**

- Two devices with colliding numeric Room IDs retain unrelated downloads after sync.
- Delete on one device cannot resurrect from another device/server.

### C3. Sync-controlled values enter file paths and FFmpeg command text unsafely

**Locations**

- `app/src/main/java/com/example/medianest/data/sync/SyncManager.kt:393-411`
- `app/src/main/java/com/example/medianest/service/DownloadService.kt:746-747`
- `app/src/main/java/com/example/medianest/service/DownloadService.kt:858-860`
- `app/src/main/java/com/example/medianest/service/DownloadService.kt:968-986`
- `app/src/main/java/com/example/medianest/service/DownloadService.kt:1151-1166`

**Problem**

Synced `videoId` and `quality` become path components and FFmpeg command content without strict local validation. Title sanitization does not protect these fields.

**Failure case**

Malformed or compromised sync payload supplies separators, traversal segments, or quote characters. Download, cleanup, restart, or merge can write/delete outside intended storage root or alter command parsing.

**Required fix contract**

- Use opaque immutable local filenames instead of user/sync metadata in paths.
- Validate remote identifiers against strict expected grammar before persisting or using them.
- Build FFmpeg arguments using API argument lists if supported. Otherwise quote every controlled path with FFmpeg-safe escaping and never interpolate untrusted metadata.
- Canonicalize any resolved file and assert it remains under owned storage root before read/delete/rename.

**Acceptance checks**

- Payload values containing `..`, `/`, `\\`, quotes, or control characters cannot escape storage root or change FFmpeg arguments.

### C4. Cancel UI deletes row instead of retaining canceled history

**Locations**

- `app/src/main/java/com/example/medianest/ui/screens/DownloadsScreen.kt:287-313`
- `app/src/main/java/com/example/medianest/ui/viewmodel/DownloadsViewModel.kt:282-322`

**Problem**

Active "Cancel Download" confirmation calls `deleteDownload`, not `cancelDownload`. It removes history despite required Cancel behavior.

**Failure case**

User taps Cancel expecting Restart/Delete. Row disappears. Partial artifacts and service races may remain.

**Required fix contract**

- Bind Cancel action to service-owned cancel operation.
- Operation must acknowledge job stop and temp cleanup, then persist `CANCELED` row.
- Show Restart/Delete only after cancellation reaches terminal state.
- Keep Delete separate and destructive.

**Acceptance checks**

- Cancel active transfer, merge, and extraction. Each remains listed as `CANCELED`; partial temp files are gone; Restart begins at zero.

### C5. Extraction has no durable execution owner or restart recovery

**Locations**

- `app/src/main/java/com/example/medianest/ui/viewmodel/DownloadsViewModel.kt:455-484`
- `app/src/main/java/com/example/medianest/data/local/dao/DownloadDao.kt:84-85`
- `HomeViewModel.kt`, `LibraryViewModel.kt`

**Problem**

Downloads-screen extraction now runs in `viewModelScope`; Home and Library use separate ad-hoc scopes. Navigation, process death, or ViewModel destruction ends work. Stale recovery explicitly excludes `audio_extracted` rows.

**Failure case**

Extraction starts, user leaves screen or process is reclaimed. Row can remain `DOWNLOADING` forever with partial output. Queue recovery does not repair it.

**Required fix contract**

- Use one durable owner for extraction: DownloadService state machine or a dedicated foreground-capable worker/service.
- Represent extraction as explicit durable state and recover interrupted work on service startup.
- Route Home, Library, Video Detail, and Downloads actions through same command path.
- Use one atomic duplicate guard: insert/claim must report conflict before any output work begins.

**Acceptance checks**

- Start extraction from every entry point, rotate/navigate/process-restart, then verify deterministic recovery to resumable failure/canceled state or restartable queued state.

## Major findings: transfer integrity and queue behavior

### M1. Refreshed URL can target different stream and append incompatible bytes

**Locations**

- `HomeViewModel.kt:441-465`
- `LibraryViewModel.kt:283-307`
- `VideoDetailViewModel.kt:318-342`
- `DownloadService.kt:807-845`

Video producers persist `quality` as `"${stream.quality} (${stream.codec})"`. URL refresh compares it to extractor raw quality. Match fails and code falls back to arbitrary same-format stream. Existing partial bytes are kept.

**Fix contract:** Persist stable stream identity such as itag/stream ID, container, codecs, exact raw quality, content length, and validators. Refresh must resolve exact representation. If identity or representation validators differ, discard partial and restart from zero after user-visible state transition.

### M2. Range response accepts wrong byte interval and false completion

**Locations**

- `DownloadService.kt:399-460`
- `DownloadService.kt:586-600`

`206` response is accepted without validating requested `Content-Range` start and end. Completion uses local length compared to total, so duplicate bytes can reach expected length.

**Fix contract:** Parse and require `Content-Range`. Start must equal requested offset. End must be >= start and body length must match interval when known. Total must be consistent with existing metadata. Completion needs exact byte count, not `>=`.

### M3. HTTP 416 with unknown total is accepted as complete

**Location:** `DownloadService.kt:412-425`

When total is unknown, any non-empty partial plus `416` is treated as success without authoritative `Content-Range: bytes */total`.

**Fix contract:** Treat 416 as completion only when parsed authoritative server total equals local length and representation identity matches. Otherwise refresh/restart or fail with actionable error.

### M4. Non-advancing successful responses can loop forever

**Locations**

- `DownloadService.kt:449-460`
- `DownloadService.kt:482-600`

Empty/non-advancing `206` does not consume retry budget. Queue slot and foreground service can remain busy forever.

**Fix contract:** Every response must advance offset. Non-advancing or zero body is retryable failure with bounded retries/backoff. Unknown-size transfers require explicit EOF semantics and minimum forward progress.

### M5. Retry cleanup differs by caller and leaves stale artifacts

**Locations**

- `DownloadsViewModel.kt:334-353`
- `DownloadService.kt:746-747`
- `DownloadService.kt:921-948`
- `DownloadService.kt:1174-1205`

Screen retry deletes primary temp only. It does not remove audio temp/final output or reset size metadata. Service restart has separate broader cleanup.

**Fix contract:** One service-owned restart/reset operation. It removes every owned transient/output artifact required by restart policy, resets URL/size/progress/error/phase coherently, and returns row to queue. UI must never implement partial cleanup.

### M6. Pause All can persist stale progress

**Locations**

- `DownloadService.kt:1208-1228`
- `DownloadService.kt:307-329`

Pause-all joins jobs, then reads progress map after job finalizer removed it. It falls back to stale entity snapshot and can overwrite more recent progress.

**Fix contract:** Job owns final persisted progress under per-row synchronization. Pause-all issues stop requests then awaits terminal acknowledgements; it must not write stale snapshots afterward.

### M7. Concurrency limit only controls future starts

**Locations**

- `DownloadService.kt:253-275`
- `DownloadsViewModel.kt:357-360`

Reducing maximum concurrent downloads does not reduce active jobs.

**Fix contract:** Define desired policy. If setting claims hard maximum, scheduler must pause/cancel excess work deterministically. If only future-start policy, UI text must state that. Prefer service-owned scheduler with observable permit count.

### M8. Subscription worker reports success when FGS start is forbidden

**Location:** `SubscriptionWorker.kt:35-80`

Android 12+ can reject foreground-service start in background. Worker catches exception and returns success, leaving queued rows idle.

**Fix contract:** Hand work to supported WorkManager foreground execution or return retry with policy-aware backoff. Do not mark successful until execution was reliably scheduled.

## Major findings: merge and FFmpeg operation ownership

### M9. Zero/unknown duration merge cannot be canceled

**Location:** `DownloadService.kt:677-681`

Cancellation monitor is created only when duration is positive. Zero-duration branch blocks in synchronous `FFmpegKit.execute`.

**Fix contract:** Start every FFmpeg operation asynchronously, retain exact session ID/handle, and cancel that exact session regardless of duration/progress availability. Progress is optional; cancellability is not.

### M10. FFmpeg callbacks and cancellation are process-global

**Locations**

- `DownloadService.kt:684-726`
- `AudioExtractor.kt:47-54`
- `AudioExtractor.kt:107-122`

Global statistics callback is replaced/cleared by concurrent operations. Cancel behavior scans all running sessions and cancels all of them.

**Failure case:** Cancel one extraction while another merge runs. Both sessions can be canceled. One operation can disable another operation's progress callback.

**Fix contract:** Build per-operation session ownership. Register callback/session through API mechanisms that do not overwrite process-global state, or serialize FFmpeg operations if library cannot provide isolated callbacks. Never enumerate and cancel global sessions for a row action.

### M11. Merge flow bypasses native merger and relies only on FFmpeg

**Locations**

- `DownloadService.kt:968-1006`
- dormant `mergeAudioVideoNative` around `DownloadService.kt:1650-1752`

Current staged path bypasses native merger despite stale error claiming native and FFmpeg both failed. This makes merge depend entirely on FFmpeg and leaves dead fallback code.

**Fix contract:** Choose deliberate architecture: restore native-first fallback with tested codec/container checks, or remove native merger and correct user errors/docs. Do not leave misleading unreachable fallback.

### M12. Extension and command construction use unstable metadata

**Locations**

- `DownloadService.kt:661-668`
- `DownloadService.kt:968-986`

`getFileExtension` infers from URL/display quality and can be wrong after URL refresh. FFmpeg command construction interpolates text.

**Fix contract:** Persist authoritative selected stream container/codec. Derive extension from it. Use safely formed argument arrays or correctly escaped paths only.

## Major findings: cancel, delete, restart, and extraction cleanup

### M13. Deleting paused row leaves service paused cache and artifacts

**Locations**

- `DownloadsViewModel.kt:282-322`
- `DownloadService.kt:145`
- `DownloadService.kt:229-269`
- `DownloadService.kt:1127-1135`

Paused-row deletion does not notify service. `pausedDownloads` retains stale entry, potentially keeping notification/service alive. List-only paths leave temp artifacts.

**Fix contract:** Delete is one service command. It removes row from active, paused, and extraction ownership maps; waits for job termination; applies explicit file policy; then removes DB row. List-only policy must explicitly define whether temp files survive. Current user requirement for Cancel is remove partial temp files.

### M14. Delete All races active download and extraction work

**Locations**

- `DownloadsViewModel.kt:385-429`
- `DownloadsViewModel.kt:59`
- `DownloadsViewModel.kt:455-458`

Delete-all sends async cancel then immediately deletes rows/files. It does not cancel `activeExtractions`.

**Fix contract:** Batch delete must use same acknowledged service operation as single delete. It must include ordinary jobs, FFmpeg sessions, native extraction loops, and paused cache. No output can be committed after acknowledgement.

### M15. Fixed 500 ms delay and unbounded joins do not provide correct cancellation

**Locations**

- `DownloadsViewModel.kt:318-322`
- `DownloadService.kt:190-203`
- `DownloadService.kt:1138-1146`

Single delete uses arbitrary wait. `cancelDownload` performs unbounded `job.join`; blocked synchronous FFmpeg can hold `intentMutex` and prevent later actions.

**Fix contract:** Use operation state and completion acknowledgement, not delays. Do not hold global intent mutex while waiting for long-running work. Apply bounded timeout and transition to recoverable error if executor does not terminate.

### M16. Extraction cancellation is marked failed and cannot reliably retry

**Locations**

- `DownloadsViewModel.kt:479-484`
- `DownloadDao.kt:75-76`
- `DownloadsViewModel.kt:437-440`
- `DownloadService.kt:233-238`

`CancellationException` is caught as `Throwable` and recorded `FAILED`. DAO returns any extraction row, so canceled/failed record blocks a new extraction. Generic retry queues `audio_extracted`, but download service filters that format from queue.

**Fix contract:** Re-throw cancellation before generic error handling. Query only nonterminal active extraction for duplicate prevention. Add extraction-specific restart command/state; never feed extracted-audio rows into ordinary transfer queue unless service supports that state.

### M17. Native extraction ignores cancellation and leaves partial outputs

**Locations**

- `AudioExtractor.kt:149-207`
- `AudioExtractor.kt:60-88`
- `AudioExtractor.kt:117-143`

Native loop has no cancellation checks. Failed native/copy/transcode stages leave partial `.m4a`, `.ogg`, or `.mp3` files. Stream-copy lacks `-y`.

**Fix contract:** Check coroutine cancellation inside native copy loop. Write each candidate to operation-specific temp path and atomically rename only after validation. Delete failed candidates. Use `-y` with controlled temp path. Choose native muxer only when source codec is compatible; current code always chooses MPEG-4/m4a.

## Major findings: persistence, files, lifecycle, and sync

### M18. `VideoEntity.localFilePath` has no stable ownership policy

**Locations**

- `DownloadService.kt:1039-1062`
- `DownloadsViewModel.kt:322-329`

Every completed format, including audio, overwrites sole video path. Delete only clears path if no completed row remains; it does not select valid remaining output.

**Fix contract:** Define policy: preferred playable video path, preferred audio path in separate field, or derive from completed rows. On completion/delete, recompute canonical path from remaining validated rows. Never let audio overwrite video playback path unless product explicitly wants that.

### M19. Folder migration races active download ownership

**Locations**

- `ExportImportViewModel.kt:244-326`
- `DownloadService.kt:743-747`
- `DownloadService.kt:1151-1166`

Active job captures old directory. Preference changes; cancel/restart recomputes from new directory and misses old temp. Migration cancellation still stores new preference.

**Fix contract:** Block folder migration while work exists, or pause/cancel and await all jobs before migration. Persist output root/path per row. Never derive owned active paths solely from mutable current preference. Commit preference only after migration completes.

### M20. Service startup races paused-cache loading

**Locations**

- `DownloadService.kt:149-171`
- `DownloadService.kt:173-203`
- `DownloadService.kt:261-269`

Paused cache loading and queue processing run independently. Service can stop before cache fills.

**Fix contract:** Load initial service state before queue/stop decision, or remove separate cache and derive from DB under service synchronization.

### M21. Download updates are not sync-visible

**Locations**

- `DownloadDao.kt:48-70`
- `DownloadRepository.kt:34-56`
- `SyncManager.kt:162-200`

Status, progress, URL, retries, size, failure, and completion updates do not update `updatedAt`, yet sync collects `getDownloadsSince`.

**Fix contract:** Every sync-worthy mutation updates timestamp/version transactionally. Prefer not syncing high-frequency progress. Sync only durable intent/history fields under defined conflict rules.

### M22. Remote sync state can fight active local job

**Locations**

- `SyncManager.kt:387-417`
- `DownloadService.kt:229-279`

Remote pull overwrites local row while local service owns stale job snapshot. Job continues writing and later overwrites fields.

**Fix contract:** Service owns operational state. Sync must not mutate active rows directly; route changes through service reconciliation with generation/version checks, or exclude transient transfer state entirely.

## Minor findings

| ID | Location | Finding | Required cleanup |
|---|---|---|---|
| m1 | `DownloadsViewModel.kt:432-440`, `HomeViewModel.kt:499-504`, `LibraryViewModel.kt:341-346` | "Audio extraction started" toast happens before duplicate guard. | Emit start feedback only after work claim succeeds. |
| m2 | `DownloadEntity.kt:40`, `DownloadDao.kt:30-31` | `downloadedAt` means enqueue time, completion time, and queue order. | Split into `createdAt`, `completedAt`, and queue ordering field or document one invariant. |
| m3 | `DownloadEntity.kt:44`, `DownloadService.kt:555-570`, `DownloadsScreen.kt:602-683` | `errorMessage` carries both failure text and delimiter-encoded progress protocol. | Add typed phase/progress fields; reserve error field for user-visible error. |
| m4 | `AudioExtractor.kt:69-79` | FFmpeg stream copy omits `-y`. | Use controlled overwrite into unique temp path. |
| m5 | staged `AudioExtractor.kt` | Trailing whitespace. | Remove during implementation. |

## Boundary contracts to restore

| Boundary | Required contract |
|---|---|
| producer -> URL refresh | Persisted stream identity resolves exact same representation. Partial data is discarded when continuity cannot be proven. |
| entity -> filesystem | Every database row exclusively owns paths. Mutable display metadata never determines ownership. |
| sync -> database | UUID, not Room ID, identifies row. Sync does not blindly overwrite operational state or local paths. |
| HTTP -> writer | `206` interval exactly matches requested offset; forward progress and exact completion are proven. |
| screen -> service | Screen requests operation and observes result. It never races service with direct row/file deletion. |
| service -> FFmpeg | Each job owns exactly one session/callback/cancel handle. No global cancellation or callback replacement. |
| extraction -> lifecycle | All entry points use one durable executor and recover terminal/retryable state after interruption. |
| folder preference -> files | Per-row persisted ownership path survives later preference changes. |
| completion -> video path | Explicit policy picks valid preferred local playback file. |

## Recommended remediation sequence

Do not attempt broad refactor in one change. Maintain compilable, testable checkpoints.

### Phase 1: Establish identities and file ownership

1. Add immutable local download UUID and sync UUID strategy.
2. Add persisted owned paths/root identity or deterministic UUID-based path builder.
3. Migrate/handle existing rows safely. Existing file naming must be discovered and adopted or treated as legacy during one-time migration.
4. Validate all paths remain below output root.
5. Define `VideoEntity.localFilePath` policy.

**Done when:** concurrent formats cannot collide; file cleanup is row-scoped; path traversal tests pass.

### Phase 2: Centralize operations under service ownership

1. Create service commands/results for cancel, delete, restart, pause, and extraction operations.
2. Add per-row operation state and acknowledgement. Do not wait under global mutex.
3. Remove UI direct cleanup and fixed delay behavior.
4. Include paused cache and extraction ownership in same lifecycle.

**Done when:** cancel retains row and clears temp; delete cannot be followed by recreated output; restart behavior is same from every UI entry point.

### Phase 3: Fix transfer representation and HTTP integrity

1. Persist authoritative stream metadata and response validators.
2. Make refresh resolve exact stream only.
3. Validate `Content-Range`, total, offset advance, `416`, and completion exactness.
4. Bound retries and fail recoverably.

**Done when:** wrong-range, wrong-stream, empty-body, and changed-validator responses cannot silently corrupt or hang transfer.

### Phase 4: Build durable extraction and FFmpeg isolation

1. Move extraction to service/worker state machine.
2. Use per-operation FFmpeg sessions and cancellation.
3. Ensure native loops are cooperative with cancellation.
4. Use unique temp files, codec-aware container selection, atomic output commit, and cleanup on every failure/cancel path.

**Done when:** concurrent merge/extraction display independent progress; cancel affects only selected row; interrupted extraction is restartable.

### Phase 5: Repair sync, migration, and scheduler lifecycle

1. Sync only defined durable fields by UUID, with tombstones and timestamps/versioning.
2. Reconcile sync changes through service for active items.
3. Coordinate folder migration with all active work.
4. Fix paused-cache initialization, background scheduling restrictions, pause-all progress, and dynamic concurrency policy.

**Done when:** two-device conflicts do not overwrite unrelated rows; folder move cannot orphan active files; background enqueue has deterministic retry/handoff.

## Verification matrix

Implement focused automated tests where feasible. Manual Android/device checks remain required for foreground-service and media compatibility behavior.

| Scenario | Expected result |
|---|---|
| Queue progressive and video-only same quality concurrently | Different temp/final paths; valid independent outputs. |
| Cancel active HTTP transfer | Row becomes `CANCELED`; all row temp files removed; other jobs continue. |
| Cancel active merge with known and unknown duration | Exact FFmpeg session stops; UI reaches `CANCELED`; no global session interruption. |
| Cancel native extraction | Loop stops promptly; no partial final output; row retryable. |
| Restart canceled download | Starts byte zero; stale temp/audio/merge output absent. |
| Delete paused row | Service cache and notification clear; row files follow selected delete policy. |
| Delete all during transfer/extraction | All jobs acknowledged stopped before DB/file removal; no output reappears. |
| Refresh expired stream URL | Same stable stream resumes; changed stream discards partial and restarts/fails explicitly. |
| Server returns `206` from byte 0 for nonzero request | Transfer rejects response; never appends duplicate bytes. |
| Server returns repeated empty `206` | Bounded retry then failed/retryable state; queue permit releases. |
| Server returns `416` | Complete only when authoritative total equals local byte count. |
| Reduce concurrency from 5 to 1 | Behavior matches documented policy and is observable. |
| App process dies during extraction | On restart row recovers to explicit restartable state; no stuck `DOWNLOADING`. |
| Two devices with same Room ID | Sync does not overwrite unrelated row. |
| Delete download on device A then sync | Row remains deleted after device B/server synchronization. |
| Change folder during active transfer | Operation blocks/coordinates safely; cleanup finds actual old path. |

## Non-goals for first fixes

- Do not silently retain partial bytes after representation mismatch.
- Do not solve cancellation by globally canceling all FFmpeg sessions.
- Do not add another ViewModel-specific extraction implementation.
- Do not preserve compatibility with ambiguous legacy filenames without explicit migration/ownership decision.
- Do not make UI delay longer to hide service race.

## Review limitations and open product decisions

- Review is static. No build, instrumentation, network mock, or media fixture tests were run by request.
- Decide whether sync should replicate completed download history, download intent, or active transfer state. Active transfer URLs/progress/local paths are generally device-local and unsafe to sync directly.
- Decide `localFilePath` policy for multiple completed formats and audio-only output.
- Decide List Only policy for paused/active rows. It must be explicit and consistent with service ownership.
- Custom folder selection appears path-based rather than SAF URI-based. Scoped-storage access behavior must be verified separately.
