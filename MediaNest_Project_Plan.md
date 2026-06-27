# MediaNest — Personal Offline Media Library
## Project Master Plan & Logic Definition

> **Note**: This document contains high-level intent, business rules, and technical specifics. 
> For visual maps of the system, data models, and logic flows, refer to:
> **[Architecture Diagrams (docs/architecture.drawio)](./docs/architecture.drawio)**

---

# 1. Core Mission & Principles

## Objective
Build an Android-first, offline-centric media manager for personal consumption of YouTube content.

## "The Laws" (Non-Negotiable)
1. **Offline-First**: App must be fully functional without internet for all downloaded content.
2. **Local-First Storage**: The device is the source of truth; media blobs (MP4/M4A) are stored on the filesystem, never in the database.
3. **Metadata-Only Sync**: Optional VPS synchronization is limited to history, subscriptions, and settings. No media is synced via VPS.
4. **YouTube extraction**: Performed via `NewPipeExtractor` integrated as a library.

---

# 2. Visual Blueprint Map
Refer to the following pages in `docs/architecture.drawio` for deep technical context:

| Context Needed | Page Reference |
| :--- | :--- |
| **System Integration** | Page 1: System Context |
| **Code Structure/Layers** | Page 2: App Container |
| **End-to-End User Flow** | Page 3: User Journey |
| **Process Logic (DL/Sync)** | Page 4: Process Deep-Dive |
| **Database Schema** | Page 5: Data Model |
| **Cloud/Local Topology** | Page 6: Deployment Topology |
| **Auth & Navigation** | Page 7: Security & Navigation |

---

# 3. Technical Stack

| Component | Technology |
| :--- | :--- |
| **Mobile** | Kotlin + Jetpack Compose |
| **Playback** | ExoPlayer (Video/Audio/Background/Resume) |
| **Database** | Room (SQLite) |
| **Background** | WorkManager (Scheduled checks/Sync) |
| **Extraction** | NewPipeExtractor (direct Gradle library) |
| **Processing** | FFmpeg (Merging streams / Audio extraction) |
| **DI** | Hilt |
| **Sync Server** | Python FastAPI + SQLite + Docker |

---

# 4. Critical Logic & Edge Cases

## A. Download Reliability
- **Throttling/Expirations**: Retries must use exponential backoff and metadata refresh.
- **Background Integrity**: Must use Foreground Service with persistent notifications to prevent OS kills.

## B. Storage Management
- **Persistence**: Graceful handling of missing files (SD card removal/Manual deletion) via a "Library Repair" scan.
- **Deduplication**: Use YouTube ID as the primary key to prevent duplicate file downloads.

## C. Sync Conflict Strategy
- **Version Based**: All syncable entities must track `syncVersion` and `updatedAt`.
- **Merge Logic**: "Newest Update Wins" policy for device-server collisions.

---

# 5. Roadmap Checklist

- [x] **Foundation**: Hilt/Room/Navigation Setup
- [x] **Architecture**: System Design & Blueprinting
- [ ] **Phase 2 — Extraction**: URL metadata & stream resolution
- [ ] **Phase 3 — Playback**: ExoPlayer integration & persistence
- [ ] **Phase 4 — Downloads**: Foreground service & FFmpeg merging
- [ ] **Phase 5 — Library**: Nested folders & searching
- [ ] **Phase 6 — Subs**: Automated background upload checks
- [ ] **Phase 7 — Sync**: Device pairing & VPS REST integration

---

# 6. Future Scope Decisions
- **Desktop**: Deferred.
- **Thumbnail Strategy**: Cache locally, don't re-download unless missing.
- **Backup Format**: Hybrid (SQLite snapshot + JSON metadata).
