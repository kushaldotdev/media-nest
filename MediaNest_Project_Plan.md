
# MediaNest — Personal Offline Media Library
## Android-First Offline YouTube Media Manager

---

# 1. Goal Summary

## Objective

Build an Android-first offline media library application focused on personal media consumption and organization.

The app should allow users to:

- Download YouTube videos and audio locally
- Stream/play content inside the app
- Resume playback from last watched/listened position
- Download playlists and channel uploads
- Subscribe to channels/playlists
- Automatically check for new uploads
- Optionally auto-download new uploads
- Organize media into folders/categories/tags
- Maintain playback history
- Export/import full application metadata and settings
- Optionally sync metadata across devices using a VPS
- Work fully offline after media is downloaded

The application is intended for:
- Personal use
- Offline media consumption
- Audio-first listening experience
- Long-form story/video organization

The application is NOT intended to:
- Publicly host copyrighted media
- Replace YouTube
- Act as a cloud streaming platform
- Sync actual media files through VPS initially

---

# Core Product Principles

## Offline-first
The app must function without internet access after content is downloaded.

## Local-first
The local device database is the source of truth.

## Media files stay local
Videos/audio files are stored directly on device storage.

## VPS is optional
The app should work completely without a VPS.

## Metadata sync only
VPS sync is only for:
- settings
- subscriptions
- history
- organization metadata

NOT media files.

---

# Suggested Product Name

# MediaNest

Alternative names:
- EchoVault
- StoryDock
- AudioHarbor
- VaultPlay
- OfflineNest

---

# 2. Tech Stack

# Mobile Platform

## Recommended
- Kotlin
- Jetpack Compose

Reason:
- Best Android integration
- Native background processing
- Better media APIs
- Better download handling
- Better battery optimization support

---

# Media Playback

## ExoPlayer

Use for:
- video playback
- audio playback
- background playback
- playback speed
- seek/resume
- lockscreen controls

---

# Database

## Room (SQLite)

Reason:
- official Android ORM
- offline-first friendly
- stable
- lightweight

---

# Dependency Injection

## Hilt

---

# Networking

## Retrofit
OR
## Ktor

---

# Background Tasks

## WorkManager

Use for:
- sync tasks
- scheduled channel checks
- retries
- maintenance jobs

---

# Download Service

## Android Foreground Service

Use for:
- long-running downloads
- persistent notifications
- playlist downloads

---

# YouTube Extraction

## yt-dlp

Use for:
- metadata extraction
- playlist extraction
- stream URLs
- audio extraction

Do NOT build custom YouTube parsers.

---

# Media Conversion

## ffmpeg

Use for:
- audio extraction
- format conversion
- metadata embedding

---

# Optional VPS Stack

## Recommended Option
- PocketBase

## Alternative
- Node.js + PostgreSQL

---

# Suggested Libraries

| Purpose | Library |
|---|---|
| Media playback | ExoPlayer |
| Database | Room |
| DI | Hilt |
| Background jobs | WorkManager |
| HTTP | Retrofit |
| Serialization | Kotlinx Serialization |
| Image loading | Coil |
| Logging | Timber |
| Navigation | Jetpack Navigation |

---

# 3. Architecture Overview

# High-Level Architecture

```text
UI Layer
    ↓
ViewModels
    ↓
Repositories
    ↓
Local DB + Media Services + Sync Services
```

---

# Architectural Pattern

## MVVM + Repository Pattern

---

# Main Modules

```text
app/
 ├── ui/
 ├── player/
 ├── downloads/
 ├── subscriptions/
 ├── library/
 ├── sync/
 ├── export/
 ├── database/
 ├── workers/
 ├── services/
 ├── media/
 └── settings/
```

---

# Core Components

## UI Layer
Responsible for:
- screens
- navigation
- state rendering

---

## Playback Engine
Responsible for:
- video playback
- audio playback
- resume position
- playback notifications

---

## Download Engine
Responsible for:
- yt-dlp execution
- queue management
- retries
- progress tracking

---

## Database Layer
Responsible for:
- metadata
- history
- organization
- subscriptions

---

## Sync Engine
Responsible for:
- metadata sync
- conflict resolution
- export/import

---

## Storage Layer
Responsible for:
- media files
- thumbnails
- cleanup
- relinking

---

# Media Storage Structure

```text
/MediaNest
    /audio
    /video
    /thumbs
    /temp
```

---

# Important Rule

Media files are stored in filesystem.

Metadata is stored in SQLite.

DO NOT store media blobs inside SQLite.

---

# 4. Step-by-Step Implementation Plan

# Phase 1 — Project Foundation

## Step 1
Create Android project using:
- Kotlin
- Jetpack Compose

Setup:
- Hilt
- Room
- Navigation
- Retrofit
- ExoPlayer

---

## Step 2
Create database schema:
- videos
- downloads
- history
- subscriptions
- folders
- playlists

---

## Step 3
Implement basic navigation:
- Home
- Downloads
- Library
- Player
- Settings

---

# Phase 2 — Media Extraction

## Step 4
Integrate yt-dlp.

Features:
- fetch metadata
- fetch playlists
- fetch channel uploads

Input:
- YouTube URL

Output:
- title
- duration
- thumbnail
- available streams

---

## Step 5
Implement media selection UI.

Allow:
- video quality selection
- audio-only selection

---

# Phase 3 — Playback

## Step 6
Integrate ExoPlayer.

Features:
- play video
- play audio
- seek
- playback speed
- subtitles
- background playback

---

## Step 7
Implement playback persistence.

Store:
- playback position
- completion state
- timestamp

Restore automatically on reopen.

---

# Phase 4 — Download System

## Step 8
Build download queue system.

States:
- queued
- downloading
- paused
- completed
- failed

---

## Step 9
Implement foreground download service.

Features:
- persistent notifications
- pause/resume
- retries

---

## Step 10
Integrate ffmpeg.

Support:
- audio extraction
- mp3/m4a conversion

---

# Phase 5 — Organization

## Step 11
Build folder system.

Features:
- create folders
- nested folders
- move media
- virtual organization

---

## Step 12
Build tags and favorites system.

---

## Step 13
Implement search.

Search:
- title
- uploader
- tags
- folders

---

# Phase 6 — Subscriptions

## Step 14
Build subscription system.

Support:
- channels
- playlists

---

## Step 15
Implement scheduled upload checks.

Use:
- WorkManager

---

## Step 16
Implement auto-download rules.

Options:
- notify only
- auto-download video
- auto-download audio

---

# Phase 7 — Export / Import

## Step 17
Build export system.

Generate:
```text
medianest_backup.zip
```

Include:
- settings
- subscriptions
- folders
- history
- database snapshot

---

## Step 18
Build import system.

Features:
- restore metadata
- rescan media
- relink files

---

## Step 19
Implement library repair tool.

Features:
- find missing files
- relink moved media
- remove dead entries

---

# Phase 8 — VPS Sync (Optional)

## Step 20
Create lightweight sync API.

Support:
- push changes
- fetch changes
- authentication

---

## Step 21
Implement sync engine.

Features:
- periodic sync
- manual sync
- conflict resolution

---

## Step 22
Implement device registration.

Each device gets:
```text
device_id
```

---

# 5. Data Models / Schemas

# videos

```sql
videos
--------
id (UUID)
youtube_id
title
description
uploader
duration
thumbnail_url
local_path
media_type
created_at
updated_at
```

---

# history

```sql
history
--------
id (UUID)
video_id
position_ms
completed
last_watched_at
updated_at
device_id
```

---

# downloads

```sql
downloads
--------
id (UUID)
video_id
status
progress
download_type
quality
created_at
updated_at
```

---

# folders

```sql
folders
--------
id (UUID)
name
parent_id
created_at
updated_at
```

---

# video_folder_map

```sql
video_folder_map
----------------
video_id
folder_id
```

---

# subscriptions

```sql
subscriptions
--------------
id (UUID)
source_type
source_id
title
auto_download
audio_only
last_checked_at
created_at
```

---

# playlists

```sql
playlists
----------
id (UUID)
youtube_playlist_id
title
description
created_at
updated_at
```

---

# settings

```sql
settings
---------
key
value
updated_at
```

---

# devices

```sql
devices
--------
id (UUID)
device_name
last_sync_at
created_at
```

---

# 6. Edge Cases & Error Handling

# Download Failures

Possible issues:
- network interruption
- throttling
- expired stream URLs

Handling:
- retry with exponential backoff
- refresh metadata
- resume partial downloads

---

# Android Background Restrictions

Issue:
- OS kills background processes

Handling:
- foreground service
- WorkManager
- persistent notifications

---

# Storage Permission Failures

Handling:
- SAF fallback
- user-selected media directory
- graceful permission prompts

---

# Missing Media Files

Possible causes:
- user manually deletes files
- SD card removed

Handling:
- repair scan
- mark unavailable
- relink tool

---

# Sync Conflicts

Issue:
multiple devices modify same record.

Handling:
- timestamp-based conflict resolution
- newest update wins

---

# Playlist Changes

Issue:
videos removed/reordered on YouTube.

Handling:
- sync latest playlist metadata
- preserve downloaded local media

---

# yt-dlp Breakage

Issue:
YouTube changes internals.

Handling:
- version updater
- fallback extraction
- update notifications

---

# Corrupt Database

Handling:
- automatic backups
- export snapshots
- recovery import

---

# Duplicate Downloads

Handling:
- deduplicate using youtube_id
- hash checking optional later

---

# File Path Changes

Handling:
- relative paths
- library repair scanner

---

# 7. Testing Plan

# Unit Tests

Test:
- repositories
- database logic
- sync merge logic
- playback persistence

---

# Integration Tests

Test:
- yt-dlp integration
- ffmpeg conversion
- download queue
- playlist imports

---

# UI Tests

Test:
- navigation
- playback controls
- folder management
- export/import flow

---

# Device Tests

Test on:
- Android 10+
- low-storage devices
- battery saver mode
- offline mode

---

# Sync Tests

Test:
- multiple devices
- offline modifications
- merge conflicts
- duplicate records

---

# Stress Tests

Test:
- large playlists
- 1000+ media items
- interrupted downloads
- background app kills

---

# Export/Import Tests

Test:
- full restore
- partial restore
- missing media
- version mismatch

---

# Manual Testing Checklist

| Feature | Verify |
|---|---|
| Playback resume | YES |
| Audio extraction | YES |
| Playlist download | YES |
| Auto-download | YES |
| Folder organization | YES |
| Export/import | YES |
| Sync merge | YES |
| Offline playback | YES |

---

# 8. Open Questions

# 1. yt-dlp Integration Method

Need decision:
- bundled binary
- external install
- dynamically downloaded binary

---

# 2. Storage Strategy

Need decision:
- internal app storage
- SAF-selected folders
- SD card support

---

# 3. Thumbnail Strategy

Need decision:
- cache thumbnails locally
- regenerate on demand
- optional download

---

# 4. Sync Authentication

Need decision:
- token auth
- username/password
- OAuth

---

# 5. Backup Format

Need decision:
- JSON export only
- SQLite snapshot
- hybrid approach

---

# 6. VPS Sync Frequency

Need decision:
- realtime
- periodic
- manual-only

---

# 7. Desktop Support

Need decision later:
- separate desktop app
- Kotlin Multiplatform
- web companion

---

# 8. Media Deduplication

Need decision:
- allow duplicate downloads
- detect duplicates
- shared media references

---

# Estimated Timeline

# Solo Beginner-to-Intermediate Developer

| Milestone | Estimate |
|---|---|
| Basic MVP | 2–3 months |
| Stable Daily Use | 4–6 months |
| Fully Polished System | 8–12 months |

---

# Recommended Initial MVP Scope

Build ONLY:

1. URL input
2. Metadata fetch
3. Playback
4. Download manager
5. Audio-only mode
6. Resume playback
7. Local history
8. Folder organization

DO NOT build initially:
- VPS sync
- cloud storage
- desktop support
- advanced transcoding
- social features

---

# Final Architectural Recommendation

## Prioritize:
- reliability
- offline support
- media organization
- playback experience

## Avoid:
- overengineering
- cloud-first design
- realtime complexity

The product should feel like:
# a personal offline media operating system

NOT:
# a public streaming platform.
