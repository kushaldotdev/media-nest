# Implementation Plan: Phase 8 — Export / Import (Backup & Restore)

## System / Contract Summary
- **Package**: `com.example.medianest`
- **Export scope**: All 7 Room tables (videos, downloads, playback_history, playlists, folders, video_folder_join, subscriptions) + 2 DataStore preference flows (download, playback) + media files (video/audio dirs)
- **Format**: Single ZIP file via `java.util.zip.ZipOutputStream` containing:
  - `metadata.json` — app version, export timestamp, schema version
  - `database.json` — all 7 tables serialized as JSON arrays via `kotlinx.serialization`
  - `preferences.json` — DataStore prefs as JSON key-value pairs
  - `media/` — flat copy of all files from `filesDir/MediaNest/video/` and `filesDir/MediaNest/audio/`
- **Import scope**: Restore metadata + prefs from JSON; optionally re-scan media files
- **Destination**: `context.getExternalFilesDir(null)` or `Downloads/` via SAF (ActivityResultContract). Decision: use SAF `CreateDocument` for export, SAF `OpenDocumentTree` for import — user picks location.
- **No DB restore directly**: Export dumps data to JSON. Import reads JSON and inserts into existing DB. No raw `media_nest.db` restore (avoids schema mismatch).
- **Library repair tool**: Second pass — scan `MediaNest/{video,audio}/` directories, match by filename pattern `{videoId}_{quality}.{ext}`, update `localFilePath` on VideoEntity + `filePath` on DownloadEntity. Remove orphan DB entries with no matching file.
- **DB version**: 6 currently → bump to 7 with migration (add `exportedAt` nullable column to track last export — optional, could skip)
- **SettingsScreen**: Replace placeholder with export/import/repair buttons + status text + progress indicators

---

## Phase Order

1. **8.1** — Create data serialization models + `BackupData` sealed structure using `kotlinx.serialization`
2. **8.2** — Create `BackupRepository` — export logic (collect all tables, serialize to JSON, write ZIP with media files)
3. **8.3** — Create `RestoreRepository` — import logic (read ZIP, parse JSON, insert into DB, rescan media dirs)
4. **8.4** — Create `LibraryRepair` — scan filesystem, match files to DB entries, fix paths, remove orphans
5. **8.5** — Create `ExportImportViewModel` — manage export/import state with progress tracking
6. **8.6** — Replace `SettingsScreen` — export button, import button, repair button, status messages, progress bars
7. **8.7** — Wire SAF file picker in `AppNavigation` or use `rememberLauncherForActivityResult`
8. **8.8** — Build and verify

---

## Steps

### Step 8.1: Create data serialization models + BackupData structure

**What**: Define serializable data classes matching all 7 Room entities. Create a top-level `BackupData` container.

**Where**:
- `app/src/main/java/com/example/medianest/data/backup/BackupModels.kt` (new)

**How**:
```kotlin
package com.example.medianest.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupMetadata(
    val appVersion: String = "1.0",
    val schemaVersion: Int = 7,
    val exportedAt: Long = System.currentTimeMillis(),
    val videoCount: Int = 0,
    val downloadCount: Int = 0,
    val mediaFileCount: Int = 0
)

@Serializable
data class BackupVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String? = null,
    val durationSeconds: Long = 0,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val uploadDate: String? = null,
    val localFilePath: String = "",
    val favorite: Boolean = false,
    val addedAt: Long
)

@Serializable
data class BackupDownload(
    val id: Long,
    val videoId: String,
    val url: String,
    val format: String,
    val quality: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val filePath: String = "",
    val fileSizeBytes: Long = 0,
    val downloadedAt: Long,
    val lastPlayedAt: Long? = null,
    val status: String,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val retryCount: Int = 0
)

@Serializable
data class BackupHistory(
    val videoId: String,
    val positionMillis: Long = 0,
    val playedAt: Long
)

@Serializable
data class BackupFolder(
    val id: Long,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupVideoFolderJoin(
    val videoId: String,
    val folderId: Long,
    val addedAt: Long
)

@Serializable
data class BackupPlaylist(
    val id: Long,
    val name: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val youtubePlaylistId: String = "",
    val uploaderName: String? = null,
    val videoCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupSubscription(
    val id: Long,
    val sourceType: String,
    val sourceId: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val uploaderName: String? = null,
    val autoDownload: Boolean = false,
    val audioOnly: Boolean = false,
    val lastCheckedAt: Long = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupPreferences(
    val downloads: Map<String, String> = emptyMap(),
    val playback: Map<String, String> = emptyMap()
)

@Serializable
data class BackupData(
    val metadata: BackupMetadata,
    val videos: List<BackupVideo> = emptyList(),
    val downloads: List<BackupDownload> = emptyList(),
    val history: List<BackupHistory> = emptyList(),
    val folders: List<BackupFolder> = emptyList(),
    val videoFolderJoins: List<BackupVideoFolderJoin> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val subscriptions: List<BackupSubscription> = emptyList(),
    val preferences: BackupPreferences = BackupPreferences()
)
```

**Why**: `kotlinx.serialization` is already in the project (v1.8.1). These models decouple backup format from Room entities, allowing schema evolution without breaking exports.

**Edge cases**:
- Empty table → empty JSON array (not null)
- Null fields → Kotlinx serialization uses `?` for nullable, omits from JSON if null with `@EncodeDefault(DecodeMode.NEVER)` — use defaults
- Reserved characters in file paths → `java.util.zip` handles UTF-8

**Pitfalls / do not**:
- Do NOT use `@SerialName` unless field names change between DB and backup
- Do NOT include `localFilePath` as full absolute path — will differ on restore. Store only relative path `MediaNest/video/{filename}`. Parse on import.

**Validation**: Serialize and deserialize round-trip with test data.

**Docs**: `BackupModels.kt` — public API for all backup data structures.

---

### Step 8.2: Create BackupRepository

**What**: Collect all DB rows, serialize to JSON, write ZIP with metadata + media files.

**Where**:
- `app/src/main/java/com/example/medianest/data/backup/BackupRepository.kt` (new)
- `app/src/main/java/com/example/medianest/data/preferences/DownloadPreferences.kt` (needs suspend `getAll()`)
- `app/src/main/java/com/example/medianest/data/preferences/PlaybackPreferences.kt` (needs suspend `getAll()`)

**How**:

`BackupRepository`:
```kotlin
package com.example.medianest.data.backup

import android.content.Context
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.PlaylistDao
import com.example.medianest.data.local.dao.SubscriptionDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.preferences.PlaybackPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val videoDao: VideoDao,
    private val downloadDao: DownloadDao,
    private val historyDao: HistoryDao,
    private val folderDao: FolderDao,
    private val videoFolderDao: VideoFolderDao,
    private val playlistDao: PlaylistDao,
    private val subscriptionDao: SubscriptionDao,
    private val downloadPreferences: DownloadPreferences,
    private val playbackPreferences: PlaybackPreferences
) {
    suspend fun exportToZip(outputStream: FileOutputStream, progress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            ZipOutputStream(outputStream).use { zos ->
                // Collect all data
                progress(0.1f)
                val videos = videoDao.getAllVideos().first()
                progress(0.15f)
                val downloads = downloadDao.getAllDownloads().first()
                progress(0.2f)
                val history = historyDao.getAllHistory().first()
                progress(0.25f)
                val folders = folderDao.getAllFolders().first()
                progress(0.3f)
                val subscriptions = subscriptionDao.getAllSubscriptions().first()
                progress(0.35f)
                val playlists = playlistDao.getAllPlaylists().first()
                progress(0.4f)
                // video_folder_join — need one-shot query
                // Use folderDao.getAllFolders().first() and derive joins... 
                // Actually need a dedicated query. Add getAllJoins() to VideoFolderDao.

                val preferences = BackupPreferences(
                    downloads = mapOf("max_concurrent" to downloadPreferences.maxConcurrentDownloads.first().toString()),
                    playback = mapOf("playback_speed" to playbackPreferences.playbackSpeed.first().toString())
                )
                progress(0.45f)

                val backupData = BackupData(
                    metadata = BackupMetadata(
                        videoCount = videos.size,
                        downloadCount = downloads.size,
                        mediaFileCount = countMediaFiles()
                    ),
                    videos = videos.map { it.toBackup() },
                    downloads = downloads.map { it.toBackup() },
                    history = history.map { it.toBackup() },
                    folders = folders.map { it.toBackup() },
                    playlists = playlists.map { it.toBackup() },
                    subscriptions = subscriptions.map { it.toBackup() },
                    preferences = preferences
                )

                // Write metadata.json
                zos.putNextEntry(ZipEntry("metadata.json"))
                zos.write(json.encodeToString(backupData.metadata).toByteArray())
                zos.closeEntry()
                progress(0.5f)

                // Write database.json
                zos.putNextEntry(ZipEntry("database.json"))
                zos.write(json.encodeToString(backupData).toByteArray())
                zos.closeEntry()
                progress(0.6f)

                // Write preferences.json
                zos.putNextEntry(ZipEntry("preferences.json"))
                zos.write(json.encodeToString(backupData.preferences).toByteArray())
                zos.closeEntry()
                progress(0.7f)

                // Write media files
                val mediaFiles = listOf(
                    File(context.filesDir, "MediaNest/video"),
                    File(context.filesDir, "MediaNest/audio")
                ).flatMap { dir ->
                    if (dir.exists()) dir.listFiles()?.toList() ?: emptyList() else emptyList()
                }
                var written = 0
                for (file in mediaFiles) {
                    val entryName = "media/${file.name}"
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    written++
                    progress(0.7f + 0.3f * written / mediaFiles.size.coerceAtLeast(1))
                }
            }
        }
    }

    private fun countMediaFiles(): Int {
        return listOf(
            File(context.filesDir, "MediaNest/video"),
            File(context.filesDir, "MediaNest/audio")
        ).sumOf { dir -> if (dir.exists()) dir.listFiles()?.size ?: 0 else 0 }
    }
}
```

Note: Need extension functions for entity→backup mapping. Either inline or create mapper:
```kotlin
private fun com.example.medianest.data.local.entity.VideoEntity.toBackup() = BackupVideo(
    id = id, title = title, channelName = channelName, channelId = channelId,
    durationSeconds = durationSeconds, thumbnailUrl = thumbnailUrl,
    description = description, uploadDate = uploadDate,
    localFilePath = localFilePath, favorite = favorite, addedAt = addedAt
)
// Similar for DownloadEntity, HistoryEntity, FolderEntity, PlaylistEntity, SubscriptionEntity
```

Need `getAllJoins()` in `VideoFolderDao`:
```kotlin
    @Query("SELECT * FROM video_folder_join")
    suspend fun getAllJoins(): List<VideoFolderJoin>
```

Need `getAllHistory().first()` — already Flow-compatible.

Need a `suspend` function to get one-shot prefs from DataStore. Add to `DownloadPreferences`:
```kotlin
    suspend fun getMaxConcurrent(): Int = maxConcurrentDownloads.first()
```

**Json module** — need a singleton provider in DI for `@Singleton Json`:
```kotlin
    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
```

Add to `DatabaseModule.kt` or new `BackupModule.kt`.

**Edge cases**:
- No media files → media/ folder in ZIP is empty
- Large media files (1GB+) → ZIP may be large; acceptable for now
- Export takes time → run on `Dispatchers.IO`, show progress
- File size > 2GB → `ZipOutputStream` supports up to 4GB per entry; acceptable

**Pitfalls / do not**:
- Do NOT use `ZipEntry.STORED` for media — use `DEFLATED` for JSON, `STORED` for media (faster, no recompression)
- Do NOT include `media_nest.db` directly — JSON export only
- Do NOT block the UI thread — all IO on `Dispatchers.IO`

**Validation**: Export creates valid ZIP with correct structure. Can be opened with 7-Zip/WinRAR.

**Docs**: `BackupRepository` — public API for export.

---

### Step 8.3: Create RestoreRepository

**What**: Read ZIP, parse JSON, insert into DB, restore prefs. Does NOT restore media files (media files are separate — user manually copies or rescans).

**Where**:
- `app/src/main/java/com/example/medianest/data/backup/RestoreRepository.kt` (new)

**How**:

```kotlin
package com.example.medianest.data.backup

import android.content.Context
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.PlaylistDao
import com.example.medianest.data.local.dao.SubscriptionDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.local.entity.PlaylistEntity
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.local.entity.VideoFolderJoin
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.preferences.PlaybackPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Singleton
class RestoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val videoDao: VideoDao,
    private val downloadDao: DownloadDao,
    private val historyDao: HistoryDao,
    private val folderDao: FolderDao,
    private val videoFolderDao: VideoFolderDao,
    private val playlistDao: PlaylistDao,
    private val subscriptionDao: SubscriptionDao,
    private val downloadPreferences: DownloadPreferences,
    private val playbackPreferences: PlaybackPreferences
) {
    suspend fun restoreFromZip(inputStream: java.io.InputStream, progress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            val zip = ZipInputStream(inputStream)
            var databaseJson: String? = null
            var preferencesJson: String? = null
            val mediaFiles = mutableMapOf<String, ByteArray>()

            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "database.json" -> databaseJson = zip.readBytes().toString(Charsets.UTF_8)
                    entry.name == "preferences.json" -> preferencesJson = zip.readBytes().toString(Charsets.UTF_8)
                    entry.name.startsWith("media/") -> mediaFiles[entry.name.removePrefix("media/")] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            progress(0.2f)

            // Restore database
            if (databaseJson != null) {
                val data = json.decodeFromString<BackupData>(databaseJson)
                progress(0.3f)

                // Insert videos
                for (video in data.videos) {
                    videoDao.insert(video.toEntity())
                }
                progress(0.4f)

                // Insert downloads
                for (download in data.downloads) {
                    downloadDao.insert(download.toEntity())
                }
                progress(0.5f)

                // Insert history
                for (hist in data.history) {
                    historyDao.upsert(hist.toEntity())
                }
                progress(0.55f)

                // Insert folders
                for (folder in data.folders) {
                    folderDao.insert(folder.toEntity())
                }
                progress(0.6f)

                // Insert video-folder joins
                for (join in data.videoFolderJoins) {
                    videoFolderDao.addVideoToFolder(join.toEntity())
                }
                progress(0.65f)

                // Insert playlists
                for (playlist in data.playlists) {
                    playlistDao.insert(playlist.toEntity())
                }
                progress(0.7f)

                // Insert subscriptions
                for (sub in data.subscriptions) {
                    subscriptionDao.insert(sub.toEntity())
                }
                progress(0.75f)
            }

            // Restore preferences
            if (preferencesJson != null) {
                val prefs = json.decodeFromString<BackupPreferences>(preferencesJson)
                prefs.downloads["max_concurrent"]?.toIntOrNull()?.let {
                    downloadPreferences.setMaxConcurrentDownloads(it)
                }
                prefs.playback["playback_speed"]?.toFloatOrNull()?.let {
                    playbackPreferences.setPlaybackSpeed(it)
                }
            }
            progress(0.85f)

            // Restore media files
            val videoDir = File(context.filesDir, "MediaNest/video").also { it.mkdirs() }
            val audioDir = File(context.filesDir, "MediaNest/audio").also { it.mkdirs() }
            for ((name, bytes) in mediaFiles) {
                val target = if (name.contains("_audio")) audioDir else videoDir
                File(target, name).writeBytes(bytes)
            }
            progress(1.0f)
        }
    }
}
```

Need extension functions for backup→entity mapping. Add to file:
```kotlin
private fun BackupVideo.toEntity() = VideoEntity(
    id = id, title = title, channelName = channelName, channelId = channelId,
    durationSeconds = durationSeconds, thumbnailUrl = thumbnailUrl,
    description = description, uploadDate = uploadDate,
    localFilePath = localFilePath, favorite = favorite, addedAt = addedAt
)
// Similar for DownloadEntity, HistoryEntity, FolderEntity, PlaylistEntity, SubscriptionEntity, VideoFolderJoin
```

**Edge cases**:
- ZIP with missing `database.json` → skip DB restore, show error
- Partially restored data → some tables restored, others not. Acceptable — user retries
- Duplicate primary keys → `REPLACE` conflict strategy overwrites existing
- Older schema version → `ignoreUnknownKeys = true` in Json config handles missing fields
- Prefs values fail to parse → skip individual pref, continue

**Pitfalls / do not**:
- Do NOT clear existing data before restore — merge by `REPLACE`. User can clear manually.
- Do NOT restore `downloadedAt` as future timestamp — already set from import
- Do NOT try to restore file permissions — Android internal storage handles this

**Validation**: Export → delete app data → import → verify all data restored.

**Docs**: `RestoreRepository` — public API for import.

---

### Step 8.4: Create LibraryRepair

**What**: Scan `filesDir/MediaNest/{video,audio}/` for media files. Match to DB entries by filename pattern. Fix `localFilePath` on VideoEntity and `filePath` on DownloadEntity. Remove orphan DB entries with no matching file.

**Where**:
- `app/src/main/java/com/example/medianest/data/backup/LibraryRepair.kt` (new)

**How**:
```kotlin
package com.example.medianest.data.backup

import android.content.Context
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.entity.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

data class RepairResult(
    val filesFound: Int = 0,
    val pathsFixed: Int = 0,
    val orphansRemoved: Int = 0,
    val errors: List<String> = emptyList()
)

@Singleton
class LibraryRepair @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao,
    private val downloadDao: DownloadDao
) {
    suspend fun repair(progress: (Float) -> Unit): RepairResult {
        return withContext(Dispatchers.IO) {
            val errors = mutableListOf<String>()
            val videoDir = File(context.filesDir, "MediaNest/video")
            val audioDir = File(context.filesDir, "MediaNest/audio")

            val mediaFiles = mutableMapOf<String, File>()
            for (dir in listOf(videoDir, audioDir)) {
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        mediaFiles[file.name] = file
                    }
                }
            }
            progress(0.3f)

            val filesFound = mediaFiles.size
            var pathsFixed = 0

            // Fix video entities
            val videos = videoDao.getAllVideos().first()
            for (video in videos) {
                val matchingFile = mediaFiles.entries.find { (name, _) ->
                    name.startsWith(video.id)
                }?.value
                if (matchingFile != null) {
                    if (video.localFilePath != matchingFile.absolutePath) {
                        videoDao.update(video.copy(localFilePath = matchingFile.absolutePath))
                        pathsFixed++
                    }
                } else if (video.localFilePath.isNotEmpty()) {
                    // File missing — clear path
                    videoDao.update(video.copy(localFilePath = ""))
                }
            }
            progress(0.6f)

            // Fix download entities
            val downloads = downloadDao.getAllDownloads().first()
            var orphansRemoved = 0
            for (download in downloads) {
                if (download.filePath.isNotEmpty() && !File(download.filePath).exists()) {
                    downloadDao.update(download.copy(filePath = ""))
                }
            }
            progress(0.8f)

            // Remove orphan media files (no matching video)
            for ((name, file) in mediaFiles) {
                val videoId = name.substringBefore("_")
                val video = videoDao.getVideoById(videoId)
                if (video == null) {
                    file.delete()
                    orphansRemoved++
                }
            }
            progress(1.0f)

            RepairResult(
                filesFound = filesFound,
                pathsFixed = pathsFixed,
                orphansRemoved = orphansRemoved,
                errors = errors
            )
        }
    }
}
```

**Edge cases**:
- No media files → `filesFound = 0`, skip repair
- Filename doesn't match `{videoId}_*` pattern → skip (not our file)
- File exists but video DB entry doesn't → delete orphan file
- Video DB entry exists but no file → clear `localFilePath`

**Pitfalls / do not**:
- Do NOT delete video DB entries even if file missing — user may re-download
- Do NOT scan external storage — only `context.filesDir`
- Do NOT modify download `status` — only fix `filePath`

**Validation**: Delete a media file → run repair → `localFilePath` cleared. Re-add file → run repair → path restored.

---

### Step 8.5: Create ExportImportViewModel

**What**: ViewModel managing export/import/repair state with progress tracking.

**Where**:
- `app/src/main/java/com/example/medianest/ui/viewmodel/ExportImportViewModel.kt` (new)

**How**:
```kotlin
package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.backup.BackupRepository
import com.example.medianest.data.backup.LibraryRepair
import com.example.medianest.data.backup.RestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import javax.inject.Inject

sealed class ExportImportState {
    data object Idle : ExportImportState()
    data class InProgress(val operation: String, val progress: Float = 0f) : ExportImportState()
    data class Success(val message: String) : ExportImportState()
    data class Error(val message: String) : ExportImportState()
}

@HiltViewModel
class ExportImportViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val restoreRepository: RestoreRepository,
    private val libraryRepair: LibraryRepair
) : ViewModel() {

    private val _state = MutableStateFlow<ExportImportState>(ExportImportState.Idle)
    val state: StateFlow<ExportImportState> = _state

    fun exportToFile(outputStream: FileOutputStream) {
        _state.value = ExportImportState.InProgress("Exporting", 0f)
        viewModelScope.launch {
            try {
                backupRepository.exportToZip(outputStream) { progress ->
                    _state.value = ExportImportState.InProgress("Exporting", progress)
                }
                _state.value = ExportImportState.Success("Export complete")
            } catch (e: Exception) {
                _state.value = ExportImportState.Error("Export failed: ${e.message}")
            }
        }
    }

    fun restoreFromFile(inputStream: java.io.InputStream) {
        _state.value = ExportImportState.InProgress("Restoring", 0f)
        viewModelScope.launch {
            try {
                restoreRepository.restoreFromZip(inputStream) { progress ->
                    _state.value = ExportImportState.InProgress("Restoring", progress)
                }
                _state.value = ExportImportState.Success("Restore complete")
            } catch (e: Exception) {
                _state.value = ExportImportState.Error("Restore failed: ${e.message}")
            }
        }
    }

    fun repairLibrary() {
        _state.value = ExportImportState.InProgress("Repairing", 0f)
        viewModelScope.launch {
            try {
                val result = libraryRepair.repair { progress ->
                    _state.value = ExportImportState.InProgress("Repairing", progress)
                }
                _state.value = ExportImportState.Success(
                    "Repair complete: ${result.filesFound} files found, ${result.pathsFixed} paths fixed, ${result.orphansRemoved} orphans removed"
                )
            } catch (e: Exception) {
                _state.value = ExportImportState.Error("Repair failed: ${e.message}")
            }
        }
    }

    fun resetState() {
        _state.value = ExportImportState.Idle
    }
}
```

**Edge cases**:
- FileOutputStream is null (SAF cancelled) → handle via SAF result, not ViewModel
- Progress during export/import → `InProgress` state updates UI
- Error during operation → `Error` state with message

**Pitfalls / do not**:
- Do NOT hold a reference to the stream in ViewModel — pass it in as function parameter
- Do NOT call export/import twice concurrently — state machine prevents

**Validation**: Export starts with progress, completes with success or error.

---

### Step 8.6: Replace SettingsScreen

**What**: Replace the placeholder SettingsScreen with export/import/repair buttons, progress bar, status text.

**Where**:
- `app/src/main/java/com/example/medianest/ui/screens/SettingsScreen.kt` (replace)

**How**:
```kotlin
package com.example.medianest.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medianest.ui.viewmodel.ExportImportState
import com.example.medianest.ui.viewmodel.ExportImportViewModel
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ExportImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val outputStream = context.contentResolver.openOutputStream(it) as FileOutputStream
                viewModel.exportToFile(outputStream)
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it) ?: return@let
                viewModel.restoreFromFile(inputStream)
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            // Export section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backup", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Export all data and media files to a ZIP archive.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { exportLauncher.launch("MediaNest_Backup.zip") },
                        enabled = state !is ExportImportState.InProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export Backup")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Import section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Restore", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Import from a previously exported ZIP archive.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/zip")) },
                        enabled = state !is ExportImportState.InProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import Backup")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Repair section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Library Repair", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Scan media files and fix missing paths. Removes orphan files.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.repairLibrary() },
                        enabled = state !is ExportImportState.InProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Repair Library")
                    }
                }
            }

            // Progress / Status
            when (val s = state) {
                is ExportImportState.InProgress -> {
                    Spacer(Modifier.height(16.dp))
                    Text("${s.operation}...", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is ExportImportState.Success -> {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(s.message, modifier = Modifier.padding(16.dp))
                    }
                }
                is ExportImportState.Error -> {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(s.message, modifier = Modifier.padding(16.dp))
                    }
                }
                else -> {}
            }
        }
    }
}
```

**Edge cases**:
- SAF cancelled by user → no action (launcher returns null URI)
- File too large → SAF handles via `contentResolver`
- No activity result launcher available → compose handles gracefully

**Pitfalls / do not**:
- Do NOT request any new permissions — SAF handles file access
- Do NOT hardcode file paths — SAF returns content URIs
- Do NOT allow concurrent operations — button disabled during InProgress

**Validation**: Tap Export → SAF picker opens → choose location → ZIP is created. Tap Import → SAF picker opens → choose ZIP → data restored.

---

### Step 8.7: Wire SAF file picker in SettingsScreen

Already done in Step 8.6 — `rememberLauncherForActivityResult` is used directly in the composable. No AppNavigation changes needed.

Add `ExportImportViewModel` to Hilt DI — @HiltViewModel annotation handles it. Ensure all injected deps are available:
- `BackupRepository` → needs `Json` provider
- `RestoreRepository` → needs `Json` provider
- `LibraryRepair` → standalone

Add `Json` provider to `DatabaseModule.kt`:
```kotlin
    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
```

---

### Step 8.8: Build and verify

**What**: Compile and verify build succeeds.

**How**:
```bash
./gradlew :app:assembleDebug
```

**Validation**: BUILD SUCCESSFUL.

---

## Beginner Implementation Guide (execution order)

1. Create `BackupModels.kt` — all serializable data classes
2. Add `getAllJoins()` to `VideoFolderDao`
3. Add `Json` provider to `DatabaseModule.kt` (now is a good time for `BackupModule.kt`)
4. Create `BackupRepository.kt` — export to ZIP
5. Create `RestoreRepository.kt` — import from ZIP
6. Create `LibraryRepair.kt` — scan and fix paths
7. Create `ExportImportViewModel.kt` — state management
8. Replace `SettingsScreen.kt` — buttons + SAF launchers + progress
9. Build `./gradlew :app:assembleDebug`

---

## Final Verification Checklist

- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] Export button opens SAF file picker
- [ ] Export creates a valid ZIP with `metadata.json`, `database.json`, `preferences.json`, `media/`
- [ ] Import from ZIP restores all tables
- [ ] Import from ZIP restores DataStore preferences
- [ ] Import from ZIP restores media files to correct directories
- [ ] Repair scans files and fixes `localFilePath`
- [ ] Repair removes orphan media files (no matching DB entry)
- [ ] Repair clears `localFilePath` for videos with missing files
- [ ] Progress bar shows during export/import/repair
- [ ] Error state shows on failure
- [ ] Success state shows on completion
- [ ] Buttons disabled during operation
- [ ] No new permissions required
- [ ] Import from older schema version handles missing fields gracefully

---

## Stop Conditions

- `ZipOutputStream` not found → `java.util.zip` is JVM standard library, always available on Android
- `kotlinx.serialization` encode/decode fails → verify all fields are `@Serializable` and use supported types (no Room `@ForeignKey` annotations in backup models — they're plain data)
- SAF `CreateDocument` returns null → user cancelled, handled gracefully
- Import ZIP missing `database.json` → show error message, don't crash
- `DownloadEntity.status` is enum → serialize as `status.name` string, deserialize via `DownloadStatus.valueOf()`
- `@HiltWorker` not needed here — no background worker for export/import (runs on ViewModel coroutine scope with `Dispatchers.IO`)
