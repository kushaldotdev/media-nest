# Implementation Plan: Phase 4 — Download System

## System / Contract Summary
- **Package**: `com.example.medianest`
- **Storage**: `context.filesDir/MediaNest/video/` and `MediaNest/audio/` — app-internal, no SAF, no user-visible directory
- **Concurrent downloads**: Default 2, user-configurable via DataStore (Downloads screen header chip)
- **Auto-download**: Video-only this phase
- **Audio-from-video**: Deferred to separate ffmpeg phase (Phase 5)
- **Expired URLs**: Auto re-extract + retry up to 3 times, then show error
- **HTTP client**: OkHttp (already transitive via Coil — add explicit dep)
- **Content-Length**: Read from HTTP response headers for progress (StreamSource.contentLength is 0 in current extraction)
- **DB version**: 2 currently → bump to 3 for DownloadEntity changes
- **Current DownloadEntity**: `id(autoGen)`, `videoId`, `filePath`, `format`, `quality`, `fileSizeBytes`, `downloadedAt`, `lastPlayedAt` — needs `status`, `progress`, `errorMessage`, `url`, `retryCount`

---

## Phase Order

1. **4.1** — Update `DownloadEntity` with status enum + new fields. Bump DB to v3 with destructive migration.
2. **4.2** — Update `DownloadDao` with status-based queries. Create `DownloadRepository` + `DownloadModule` Hilt provider.
3. **4.3** — Create `DownloadService` (Foreground Service) — HTTP download with OkHttp, progress callbacks, pause/resume, retry with re-extract.
4. **4.4** — Create `DownloadPreferences` (DataStore) for max concurrent count. Create `DownloadsViewModel` + wire `DownloadsScreen` with queue list, progress bars, pause/resume/retry.
5. **4.5** — Wire "Download" button in `VideoDetailScreen` — select stream → start download. Update `AppNavigation` if needed.
6. **4.6** — Add `FOREGROUND_SERVICE_DATA_SYNC` permission + notification channel in `MediaNestApp.kt`. Update `AndroidManifest.xml`. Add OkHttp dep.

---

## Steps

### Step 4.1: Update DownloadEntity

**What**: Add `status` enum, `progress` float, `errorMessage`, `url`, `retryCount` fields. Make `videoId + format + quality` unique (one download per stream variant). Bump DB version.

**Where**:
- `app/src/main/java/com/example/medianest/data/local/entity/DownloadEntity.kt`
- `app/src/main/java/com/example/medianest/data/local/AppDatabase.kt`

**How** — replace `DownloadEntity.kt`:

```kotlin
package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("videoId"), Index("videoId", "format", "quality", unique = true)]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val url: String,
    val format: String,
    val quality: String,
    val filePath: String = "",
    val fileSizeBytes: Long = 0,
    val downloadedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val retryCount: Int = 0
)
```

`AppDatabase.kt` — bump version to 3:
```kotlin
    version = 3,
```

**Why**: Without status fields, we can't track queue state, progress, or retries. Unique index on `videoId + format + quality` prevents duplicate downloads of the same stream.

**Edge cases**: Status transition validation (can't go COMPLETED → QUEUED). Existing downloads in DB from v1 schema get wiped by destructive migration.

**Pitfalls / do not**: Do NOT add `autoGenerate = true` alongside `@Upsert` — reading by `videoId + format + quality` should use a DAO query, not upsert on PK.

**Validation**: Compiles. New fields accessible in DAO.

**Docs**: None.

---

### Step 4.2: Update DownloadDao + create DownloadRepository + DownloadModule

**What**: Add status-based queries. Create `DownloadRepository` with queue management, suspend/resume, retry logic. Create `DownloadModule` for Hilt DI.

**Where**:
- `app/src/main/java/com/example/medianest/data/local/dao/DownloadDao.kt`
- `app/src/main/java/com/example/medianest/data/repository/DownloadRepository.kt`
- `app/src/main/java/com/example/medianest/di/DownloadModule.kt`

**How**:

`DownloadDao.kt` — add queries:
```kotlin
@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    suspend fun getDownloadByVideoId(videoId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE videoId = :videoId AND format = :format AND quality = :quality LIMIT 1")
    suspend fun getDownload(videoId: String, format: String, quality: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY downloadedAt ASC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' OR status = 'DOWNLOADING'")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DOWNLOADING'")
    suspend fun getActiveDownloadCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Delete
    suspend fun delete(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, progress: Float)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage, retryCount = :retryCount WHERE id = :id")
    suspend fun markFailed(id: Long, status: DownloadStatus, errorMessage: String, retryCount: Int)

    @Query("UPDATE downloads SET status = 'COMPLETED', progress = 1.0, fileSizeBytes = :fileSize WHERE id = :id")
    suspend fun markCompleted(id: Long, fileSize: Long)
}
```

`DownloadRepository.kt`:
```kotlin
package com.example.medianest.data.repository

import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao
) {
    fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    fun getActiveDownloads(): Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()

    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadEntity>> =
        downloadDao.getDownloadsByStatus(status)

    suspend fun getDownload(videoId: String, format: String, quality: String): DownloadEntity? =
        downloadDao.getDownload(videoId, format, quality)

    suspend fun getActiveDownloadCount(): Int = downloadDao.getActiveDownloadCount()

    suspend fun insert(download: DownloadEntity): Long = downloadDao.insert(download)

    suspend fun update(download: DownloadEntity) = downloadDao.update(download)

    suspend fun delete(download: DownloadEntity) = downloadDao.delete(download)

    suspend fun updateStatus(id: Long, status: DownloadStatus, progress: Float) =
        downloadDao.updateStatus(id, status, progress)

    suspend fun markFailed(id: Long, errorMessage: String, retryCount: Int) =
        downloadDao.markFailed(id, DownloadStatus.FAILED, errorMessage, retryCount)

    suspend fun markCompleted(id: Long, fileSize: Long) =
        downloadDao.markCompleted(id, fileSize)
}
```

`DownloadModule.kt`:
```kotlin
package com.example.medianest.di

import android.content.Context
import com.example.medianest.data.preferences.DownloadPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    fun provideDownloadPreferences(@ApplicationContext context: Context): DownloadPreferences =
        DownloadPreferences(context)
}
```

Also wire `DownloadDao` provider in `DatabaseModule.kt` (already exists — verify).

**Why**: Repository abstracts queue logic. Module provides DataStore prefs for concurrent limit.

**Edge cases**: `getActiveDownloadCount()` counts `DOWNLOADING` only (not `QUEUED`).

**Pitfalls / do not**: Do not expose `DownloadDao` directly to ViewModels — always go through repository.

**Validation**: Compiles.

**Docs**: None.

---

### Step 4.3: Create DownloadService (Foreground Service)

**What**: Foreground service that manages the download queue. Reads from DB `QUEUED` entries, picks up to `maxConcurrent` items, downloads via OkHttp with chunked streaming, updates progress in DB, handles pause/resume/retry.

**Where**:
- `app/src/main/java/com/example/medianest/service/DownloadService.kt`
- `app/src/main/java/com/example/medianest/service/DownloadWorker.kt` (optional — inline in service for simplicity)

**How**:

```kotlin
package com.example.medianest.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.medianest.MainActivity
import com.example.medianest.R
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.extraction.YouTubeExtractor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "com.example.medianest.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.example.medianest.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.example.medianest.CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun pause(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(intent)
        }

        fun resume(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(intent)
        }

        fun cancel(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(intent)
        }
    }

    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var preferences: DownloadPreferences
    @Inject lateinit var extractor: YouTubeExtractor
    @Inject lateinit var okHttpClient: OkHttpClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<Long, Job>()
    private val activeSemaphores = mutableMapOf<Long, Semaphore>()
    private var maxConcurrent = 2
    private val globalThrottle = Semaphore(maxConcurrent)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            maxConcurrent = preferences.maxConcurrentDownloads.first()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                if (id != -1L) pauseDownload(id)
            }
            ACTION_RESUME -> {
                val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                if (id != -1L) resumeDownload(id)
            }
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                if (id != -1L) cancelDownload(id)
            }
            else -> startForeground(NOTIFICATION_ID, buildNotification(0, 0))
        }
        processQueue()
        return START_STICKY
    }

    private fun processQueue() {
        serviceScope.launch {
            val queue = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
            val active = repository.getActiveDownloadCount()
            val limit = maxConcurrent
            val slots = (limit - active).coerceAtLeast(0)
            queue.take(slots).forEach { enqueueDownload(it) }
        }
    }

    private fun enqueueDownload(download: DownloadEntity) {
        val job = serviceScope.launch {
            globalThrottle.acquire()
            try {
                downloadFile(download)
            } finally {
                globalThrottle.release()
                processQueue()
            }
        }
        activeJobs[download.id] = job
    }

    private suspend fun downloadFile(download: DownloadEntity) {
        repository.updateStatus(download.id, DownloadStatus.DOWNLOADING, 0f)
        updateNotification(download.id)

        val dir = if (download.format == "audio") "audio" else "video"
        val outputDir = File(filesDir, "MediaNest/$dir")
        outputDir.mkdirs()

        var currentUrl = download.url
        var retries = 0
        val maxRetries = 3

        while (retries <= maxRetries) {
            try {
                val request = Request.Builder().url(currentUrl).build()
                val response = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    if ((response.code == 403 || response.code == 410) && retries < maxRetries) {
                        // URL expired — re-extract
                        retries++
                        repository.update(download.copy(retryCount = retries))
                        val freshInfo = extractor.extractVideo(download.url)
                        val matchingStream = freshInfo.streamSources.find {
                            it.format == download.format && it.quality == download.quality
                        }
                        if (matchingStream != null) {
                            currentUrl = matchingStream.url
                            continue
                        }
                    }
                    repository.markFailed(download.id, "HTTP ${response.code}", retries)
                    return
                }

                val contentLength = response.body?.contentLength() ?: -1L
                val ext = download.mimeType.split("/").lastOrNull()?.split(";")?.first() ?: "mp4"
                val fileName = "${download.videoId}_${download.quality}.$ext"
                val outputFile = File(outputDir, fileName)

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var lastProgressUpdate = 0L

                        while (true) {
                            if (isPaused(download.id)) {
                                repository.updateStatus(download.id, DownloadStatus.PAUSED, 
                                    if (contentLength > 0) bytesRead.toFloat() / contentLength else 0f)
                                return
                            }
                            if (isCancelled(download.id)) {
                                outputFile.delete()
                                repository.delete(download)
                                return
                            }

                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read

                            if (contentLength > 0 && bytesRead - lastProgressUpdate > 65536) {
                                val progress = bytesRead.toFloat() / contentLength
                                repository.updateStatus(download.id, DownloadStatus.DOWNLOADING, progress)
                                updateNotification(download.id, bytesRead, contentLength)
                                lastProgressUpdate = bytesRead
                            }
                        }

                        repository.markCompleted(download.id, bytesRead)
                        repository.update(download.copy(filePath = outputFile.absolutePath, fileSizeBytes = bytesRead))
                        updateNotification(download.id, bytesRead, contentLength)
                    }
                }
                return
            } catch (e: CancellationException) {
                return
            } catch (e: Exception) {
                if (retries < maxRetries) {
                    retries++
                    repository.update(download.copy(retryCount = retries))
                    continue
                }
                repository.markFailed(download.id, e.message ?: "Download failed", retries)
                return
            }
        }
    }

    private fun isPaused(id: Long): Boolean {
        val entity = runCatching { repository.getDownload(id) }.getOrNull() ?: return false
        return entity.status == DownloadStatus.PAUSED
    }

    private fun isCancelled(id: Long): Boolean {
        return !activeJobs.containsKey(id)
    }

    private fun pauseDownload(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        serviceScope.launch {
            repository.updateStatus(id, DownloadStatus.PAUSED, 0f)
            processQueue()
        }
    }

    private fun resumeDownload(id: Long) {
        serviceScope.launch {
            val download = repository.getDownloadById(id) ?: return@launch
            if (download.status == DownloadStatus.PAUSED) {
                repository.updateStatus(id, DownloadStatus.QUEUED, download.progress)
                processQueue()
            }
        }
    }

    private fun cancelDownload(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        serviceScope.launch {
            val download = repository.getDownloadById(id) ?: return@launch
            if (!download.filePath.isNullOrEmpty()) {
                File(download.filePath).delete()
            }
            repository.delete(download)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Downloads")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun buildNotification(progress: Int, max: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading...")
            .setContentText("$progress / $max files")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(max, progress, false)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun updateNotification(downloadId: Long, bytesDownloaded: Long = 0, totalBytes: Long = 0) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .apply {
                if (totalBytes > 0) {
                    setProgress(totalBytes.toInt(), bytesDownloaded.toInt(), false)
                    setContentText("${bytesDownloaded / 1024}KB / ${totalBytes / 1024}KB")
                } else {
                    setProgress(0, 0, true)
                }
            }
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
```

Note: Need to add `getDownload(id: Long)` and `getDownloadById(id: Long)` to `DownloadDao`:
```kotlin
@Query("SELECT * FROM downloads WHERE id = :id")
suspend fun getDownloadById(id: Long): DownloadEntity?
```

Also add to `DownloadRepository`:
```kotlin
suspend fun getDownloadById(id: Long): DownloadEntity? = downloadDao.getDownloadById(id)
```

OkHttp client provider — add to a new module or existing `PlaybackModule`:
```kotlin
@Provides
@Singleton
fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
    .build()
```

**Why**: Foreground service ensures OS doesn't kill download. OkHttp handles chunked downloads. Semaphore limits concurrency. File stored at `filesDir/MediaNest/{video,audio}/{videoId}_{quality}.{ext}`.

**Edge cases**: 
- App killed → service restarts with `START_STICKY`, reads QUEUED entries from DB
- Network drops during download → IOException → retry up to 3 times with re-extract
- Paused download → partial file kept in place, NOT deleted. Resume re-downloads from start (no Range header for now)
- Download completes → notification updates to completed (briefly), then removed by ViewModel polling

**Pitfalls / do not**: 
- Do NOT write partial file to final path until download completes — write to `.tmp` then rename
- Do NOT delete paused downloads
- Do NOT block MainThread — all IO on `Dispatchers.IO`
- Do NOT use `@AndroidEntryPoint` on a Service without proper Hilt setup (already done for MainActivity)
- Must add `foregroundServiceType="dataSync"` in manifest

**Validation**: Start download → notification appears → file appears in `filesDir/MediaNest/` → DB row updated to COMPLETED.

**Docs**: None.

---

### Step 4.4: Create DownloadPreferences + DownloadsViewModel + update DownloadsScreen

**What**: DataStore for max concurrent downloads. ViewModel managing download queue state. Downloads screen with queue list, progress bars, pause/resume/retry, concurrent limit chip.

**Where**:
- `app/src/main/java/com/example/medianest/data/preferences/DownloadPreferences.kt`
- `app/src/main/java/com/example/medianest/ui/viewmodel/DownloadsViewModel.kt`
- `app/src/main/java/com/example/medianest/ui/screens/DownloadsScreen.kt`

**How**:

`DownloadPreferences.kt`:
```kotlin
package com.example.medianest.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.downloadStore: DataStore<Preferences> by preferencesDataStore(name = "download_prefs")

class DownloadPreferences(private val context: Context) {
    companion object {
        private val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        const val DEFAULT_MAX = 2
    }

    val maxConcurrentDownloads: Flow<Int> = context.downloadStore.data.map { prefs ->
        prefs[MAX_CONCURRENT] ?: DEFAULT_MAX
    }

    suspend fun setMaxConcurrentDownloads(max: Int) {
        context.downloadStore.edit { prefs ->
            prefs[MAX_CONCURRENT] = max.coerceIn(1, 5)
        }
    }
}
```

`DownloadsViewModel.kt`:
```kotlin
package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context

data class DownloadsUiState(
    val maxConcurrent: Int = DownloadPreferences.DEFAULT_MAX,
    val activeCount: Int = 0
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val downloadPreferences: DownloadPreferences
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = downloadRepository.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState

    init {
        viewModelScope.launch {
            downloadPreferences.maxConcurrentDownloads.collect { max ->
                _uiState.value = _uiState.value.copy(maxConcurrent = max)
            }
        }
        viewModelScope.launch {
            downloadRepository.getActiveDownloads().collect { active ->
                val downloading = active.count { it.status == DownloadStatus.DOWNLOADING }
                _uiState.value = _uiState.value.copy(activeCount = downloading)
            }
        }
    }

    fun pauseDownload(downloadId: Long) {
        DownloadService.pause(context, downloadId)
    }

    fun resumeDownload(downloadId: Long) {
        DownloadService.resume(context, downloadId)
    }

    fun cancelDownload(downloadId: Long) {
        DownloadService.cancel(context, downloadId)
    }

    fun retryDownload(download: DownloadEntity) {
        viewModelScope.launch {
            val reset = download.copy(
                status = DownloadStatus.QUEUED,
                progress = 0f,
                errorMessage = null,
                retryCount = 0
            )
            downloadRepository.update(reset)
            DownloadService.resume(context, download.id)  // triggers processing
        }
    }

    fun setMaxConcurrent(max: Int) {
        viewModelScope.launch {
            downloadPreferences.setMaxConcurrentDownloads(max)
        }
    }
}
```

`DownloadsScreen.kt` — fully replace:
```kotlin
package com.example.medianest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.ui.viewmodel.DownloadsViewModel

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header with concurrent limit chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Downloads", style = MaterialTheme.typography.titleLarge)
            var expanded by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { expanded = true }) {
                    Text("Max: ${uiState.maxConcurrent}")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    (1..5).forEach { n ->
                        DropdownMenuItem(
                            text = { Text("$n concurrent") },
                            onClick = {
                                viewModel.setMaxConcurrent(n)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloads yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(downloads, key = { it.id }) { download ->
                    DownloadItem(download = download, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun DownloadItem(download: DownloadEntity, viewModel: DownloadsViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail placeholder
            AsyncImage(
                model = download.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(60.dp, 40.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(download.quality, style = MaterialTheme.typography.bodyMedium)
                Text(
                    when (download.status) {
                        DownloadStatus.QUEUED -> "Queued"
                        DownloadStatus.DOWNLOADING -> {
                            "${(download.progress * 100).toInt()}%"
                        }
                        DownloadStatus.PAUSED -> "Paused"
                        DownloadStatus.COMPLETED -> "Completed"
                        DownloadStatus.FAILED -> {
                            download.errorMessage ?: "Failed"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (download.status) {
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            when (download.status) {
                DownloadStatus.DOWNLOADING -> {
                    IconButton(onClick = { viewModel.pauseDownload(download.id) }) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    }
                }
                DownloadStatus.PAUSED -> {
                    IconButton(onClick = { viewModel.resumeDownload(download.id) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                }
                DownloadStatus.FAILED -> {
                    IconButton(onClick = { viewModel.retryDownload(download) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Retry")
                    }
                }
                DownloadStatus.COMPLETED -> {
                    IconButton(onClick = { viewModel.cancelDownload(download.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
                else -> {}
            }
        }
    }
}
```

**Edge cases**: 
- Empty downloads list → placeholder text
- Progress at 0f → indeterminate LinearProgressIndicator (Compose handles natively with `progress = { 0f }`)
- Failed with null errorMessage → show "Failed"
- Concurrent limit changed mid-download → takes effect on next queue processing

**Pitfalls / do not**: 
- Do NOT use `with` or `requireContext()` in ViewModel — use `@ApplicationContext`
- Do NOT hold Activity reference in ViewModel — leaks
- DownloadEntity does NOT have `thumbnailUrl` field currently — need to add a lookup. Alternative: show by videoId query or skip thumbnail.

**Issue**: `DownloadEntity` has no `thumbnailUrl`. Need to show it in the downloads screen. Options:
1. Add `thumbnailUrl` to `DownloadEntity` (simplest, redundant with VideoEntity)
2. Look up from `VideoDao` by `videoId` (cleaner, extra query)

**Recommendation**: Option 1 — add `thumbnailUrl` and `title` to DownloadEntity for offline display. The redundancy is acceptable for a local-first app and avoids N+1 queries.

**Update DownloadEntity** to add:
```kotlin
val title: String = "",
val thumbnailUrl: String? = null,
```

**Edge cases**: Backfill missing titles/thumbnails — insert with empty string, show placeholder.

**Pitfalls / do not**: Do NOT make nullable `title` — use empty string default.

**Validation**: Compiles. Downloads screen shows items with progress bars and action buttons.

**Docs**: None.

---

### Step 4.5: Wire Download button in VideoDetailScreen

**What**: Add "Download" button next to each stream source in VideoDetailScreen. When tapped, insert a QUEUED DownloadEntity row and start the DownloadService.

**Where**:
- `app/src/main/java/com/example/medianest/ui/screens/VideoDetailScreen.kt`

**How** — update `StreamQualityRow` to accept `onDownload` callback:

```kotlin
@Composable
private fun StreamQualityRow(
    stream: StreamSource,
    onPlay: (StreamSource) -> Unit,
    onDownload: (StreamSource) -> Unit  // new
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = { onPlay(stream) }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stream.quality)
                Text(
                    stream.contentLength?.let { "${it / 1024 / 1024}MB" } ?: "Unknown size",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = { onDownload(stream) }) {
                Text("Download")
            }
        }
    }
}
```

Update `VideoDetailScreen` signature to include `onDownload`:
```kotlin
@Composable
fun VideoDetailScreen(
    videoInfo: ExtractedVideoInfo,
    onPlay: (StreamSource) -> Unit,
    onDownload: (StreamSource) -> Unit,
    onBack: () -> Unit
)
```

Update call sites in `VideoDetailScreen`:
```kotlin
StreamQualityRow(stream, onPlay, onDownload)
```

Update `AppNavigation.kt` — pass `onDownload` to `VideoDetailScreen`:
```kotlin
VideoDetailScreen(
    videoInfo = videoInfo,
    onPlay = { stream -> ... },
    onDownload = { stream ->
        viewModel.enqueueDownload(videoInfo, stream)
    },
    onBack = { ... }
)
```

`enqueueDownload` can be on HomeViewModel or a separate mechanism. Since we need to inject DownloadRepository, best to create a small composable-level handler using a Hilt ViewModel scoped to the navigation entry. Simplest approach: inject via `hiltViewModel()` at the composable level in AppNavigation — but AppNavigation is not a ViewModel target.

**Better approach**: Add a `DownloadHelper` or use `LocalContext.current` to call `DownloadService` directly from `VideoDetailScreen`. Since the screen already has `hiltViewModel` capabilities via the `HomeViewModel`'s cache, use a dedicated composable-level LaunchedEffect. Or simplest: use `LocalContext.current` to start the service from the composable.

**Simplest correct approach**: Inject `DownloadRepository` via `hiltViewModel()` on `VideoDetailScreen` directly. But `VideoDetailScreen` currently has no ViewModel. Create a small ViewModel or use `remember` + `LocalContext`.

**Recommendation**: Keep it simple. `VideoDetailScreen` gets a `viewModel: VideoDetailViewModel = hiltViewModel()` with `DownloadRepository` injected, exposing an `enqueueDownload(videoInfo, stream)` function.

`VideoDetailViewModel.kt`:
```kotlin
package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    fun enqueueDownload(videoInfo: ExtractedVideoInfo, stream: StreamSource) {
        viewModelScope.launch {
            val existing = downloadRepository.getDownload(videoInfo.videoId, stream.format, stream.quality)
            if (existing != null) return@launch

            val entity = DownloadEntity(
                videoId = videoInfo.videoId,
                url = stream.url,
                format = stream.format,
                quality = stream.quality,
                status = DownloadStatus.QUEUED,
                title = videoInfo.title,
                thumbnailUrl = videoInfo.thumbnailUrl
            )
            downloadRepository.insert(entity)
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }
    }
}
```

**Edge cases**: 
- Already downloaded → no-op (check `existing` before insert)
- Already queued → no-op
- Download of same video+format+quality in progress → no-op (unique index prevents)

**Pitfalls / do not**: Do NOT allow duplicate downloads of the same stream. Do NOT start service if already running.

**Validation**: Extract video → tap "Download" on a stream → QUEUED entry appears in Downloads tab → download progresses → completes.

**Docs**: None.

---

### Step 4.6: Permissions, notification channel, OkHttp dep, manifest updates

**What**: Add `FOREGROUND_SERVICE_DATA_SYNC`, notification channel at app startup, OkHttp explicit dep.

**Where**:
- `app/build.gradle.kts` — add OkHttp dep
- `AndroidManifest.xml` — add `FOREGROUND_SERVICE_DATA_SYNC` permission + `foregroundServiceType="dataSync"` on DownloadService
- `app/src/main/java/com/example/medianest/MediaNestApp.kt` — create download notification channel
- `app/src/main/java/com/example/medianest/di/NetworkModule.kt` — OkHttp provider

**How**:

`app/build.gradle.kts` — add:
```kotlin
    // OkHttp
    implementation(libs.okhttp)
```

`gradle/libs.versions.toml` — add:
```toml
okhttp = "4.12.0"
```
And under `[libraries]`:
```toml
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
```

`AndroidManifest.xml` — add:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

Update DownloadService declaration:
```xml
<service
    android:name=".service.DownloadService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

`MediaNestApp.kt` — add channel creation:
```kotlin
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

// In onCreate() after existing code:
val downloadChannel = NotificationChannelCompat.Builder("downloads", NotificationManagerCompat.IMPORTANCE_LOW)
    .setName("Downloads")
    .build()
NotificationManagerCompat.from(this).createNotificationChannel(downloadChannel)
```

`NetworkModule.kt` — OkHttp singleton:
```kotlin
package com.example.medianest.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}
```

**Edge cases**: 
- `FOREGROUND_SERVICE_DATA_SYNC` requires API 34+ — use `maxSdkVersion 34` or just add and rely on manifest merging. Actually it's API 34+ only. Set `android:maxSdkVersion="34"` to avoid issues on older APIs? No — it's fine, Android ignores unknown permissions on older APIs.
- On API < 34, `foregroundServiceType` with `dataSync` is ignored.

**Pitfalls / do not**: Do NOT create the same notification channel twice (it's idempotent). Do NOT forget to add the service to manifest.

**Validation**: `./gradlew :app:assembleDebug` succeeds.

**Docs**: None.

---

## Beginner Implementation Guide

1. Update `DownloadEntity` with status enum + title/thumbnailUrl fields → bump DB to v3
2. Update `DownloadDao` with status queries + `getDownloadById`
3. Create `DownloadRepository` + `DownloadModule`
4. Create `DownloadService` — foreground service with OkHttp download
5. Create `DownloadPreferences` for concurrent limit
6. Create `DownloadsViewModel` + rewrite `DownloadsScreen`
7. Create `VideoDetailViewModel` + add Download button to `VideoDetailScreen`
8. Add OkHttp dep + NetworkModule
9. Update manifest with permissions + service
10. Add notification channel in MediaNestApp
11. Wire `onDownload` in `AppNavigation`
12. Build and verify

## Final Verification Checklist
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] Extract a video → video detail shows "Download" button per stream
- [ ] Tap Download → QUEUED entry appears in Downloads tab
- [ ] Download notification appears
- [ ] Progress bar updates during download
- [ ] File saved to `filesDir/MediaNest/video/` or `audio/`
- [ ] Pause → progress saved → Resume → download restarts
- [ ] Cancel → file deleted, DB entry removed
- [ ] Failed download (bad network) → error state → Retry works
- [ ] URL expires → auto re-extract + retry (up to 3 times)
- [ ] Concurrent limit chip works (1–5)
- [ ] Multiple downloads queue up (first 2 download, rest QUEUED)
- [ ] Duplicate stream download prevented (no-op)
- [ ] Completed download shows "Completed" with delete button
- [ ] Back button from detail returns to home
- [ ] Downloads tab shows all items with status

## Stop Conditions
- `DownloadService` doesn't start → verify `foregroundServiceType="dataSync"` + `FOREGROUND_SERVICE_DATA_SYNC` permission
- OkHttp request blocked → verify `INTERNET` permission (already set)
- Progress bar stays at 0 → verify `contentLength` > 0 from response headers; if not, show indeterminate
- `@AndroidEntryPoint` on service fails → verify Hilt is configured for services (need `hilt.androidx.lifecycle.ViewModel` dep already present)
- `NotificationChannelCompat` not found → verify `androidx.core:core-ktx` version (already at 1.10.1)
