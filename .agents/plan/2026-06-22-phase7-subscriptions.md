# Implementation Plan: Phase 7 — Subscriptions (Channels + Playlists + Auto-Download)

## System / Contract Summary
- **Package**: `com.example.medianest`
- **SubscriptionEntity**: New entity storing subscribed channels/playlists (sourceType, sourceId, name, autoDownload, audioOnly, lastCheckedAt)
- **PlaylistEntity**: Already exists (basic). Add `youtubePlaylistId` + unique index. Add `video_count` cached.
- **Auto-download rules**: Per-subscription boolean flags. WorkManager periodic check (configurable interval, default 6h).
- **DB version**: 5 currently → bump to 6 with explicit migration (NOT destructive).
- **Migration**: `CREATE TABLE subscriptions`, `ALTER TABLE playlists ADD COLUMN youtubePlaylistId`, `ALTER TABLE playlists ADD COLUMN uploaderName`, `ALTER TABLE playlists ADD COLUMN videoCount`
- **Subscriptions tab**: New bottom nav item replacing? No — keep 4 tabs, add Subscriptions screen accessible from Library or as a top-level tab in Library. Decision: add "Subscriptions" as a 4th bottom nav item. 5 items max on bottom nav.
- **WorkManager**: PeriodicWorkRequest for subscription check. Runs extraction for each subscribed channel/playlist, compares with existing videos, shows notification for new uploads or auto-downloads.
- **No download of all subscription videos at once** — auto-download per-video based on flags.

---

## Phase Order

1. **7.1** — Create `SubscriptionEntity`. Bump DB v5→v6 with migration. Extend `PlaylistEntity` with youtubePlaylistId + uploaderName + videoCount.
2. **7.2** — Create `SubscriptionDao` + extend `PlaylistDao`. Add queries for subscription CRUD and playlist dedup.
3. **7.3** — Create `SubscriptionRepository` — subscribe, unsubscribe, check for updates.
4. **7.4** — Create `SubscriptionWorker` (WorkManager) — periodic check, new-upload detection, notification + auto-download.
5. **7.5** — Create `SubscriptionsViewModel` + `SubscriptionsScreen` — list subscribed channels/playlists, subscribe button on detail screens, unsubscribe, auto-download toggle.
6. **7.6** — Add Subscribe button to `HomeScreen` (on channel/playlist results) and `VideoDetailScreen`.
7. **7.7** — Add `Subscriptions` to bottom nav. Wire in `AppNavigation.kt`.
8. **7.8** — Add "Add to Playlist" action to video context (Library screen long-press or menu).
9. **7.9** — Build and verify.

---

## Steps

### Step 7.1: Create SubscriptionEntity + DB v5→v6 migration + extend PlaylistEntity

**What**: New entity for subscriptions. Extend playlists table. Bump DB version 5→6 with explicit migration.

**Where**:
- `app/src/main/java/com/example/medianest/data/local/entity/SubscriptionEntity.kt` (new)
- `app/src/main/java/com/example/medianest/data/local/entity/PlaylistEntity.kt` (extend)
- `app/src/main/java/com/example/medianest/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/medianest/di/DatabaseModule.kt`

**SubscriptionEntity.kt**:
```kotlin
package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,  // "channel" or "playlist"
    val sourceId: String,    // channel URL or playlist URL/ID
    val name: String,
    val thumbnailUrl: String? = null,
    val uploaderName: String? = null,
    val autoDownload: Boolean = false,
    val audioOnly: Boolean = false,
    val lastCheckedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

**PlaylistEntity.kt** — add fields:
```kotlin
    val youtubePlaylistId: String = "",
    val uploaderName: String? = null,
    val videoCount: Int = 0,
```

**AppDatabase.kt**:
- Add `SubscriptionEntity::class` to entities array
- Change `version = 5` → `version = 6`
- Add `abstract fun subscriptionDao(): SubscriptionDao`

**DatabaseModule.kt** — add `MIGRATION_5_6`:
```kotlin
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS subscriptions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceType TEXT NOT NULL,
                    sourceId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    thumbnailUrl TEXT,
                    uploaderName TEXT,
                    autoDownload INTEGER NOT NULL DEFAULT 0,
                    audioOnly INTEGER NOT NULL DEFAULT 0,
                    lastCheckedAt INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """)
            db.execSQL("ALTER TABLE playlists ADD COLUMN youtubePlaylistId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE playlists ADD COLUMN uploaderName TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE playlists ADD COLUMN videoCount INTEGER NOT NULL DEFAULT 0")
        }
    }
```

Wire into builder:
```kotlin
        .addMigrations(MIGRATION_4_5)
        .addMigrations(MIGRATION_5_6)
```

Add provider:
```kotlin
    @Provides
    fun provideSubscriptionDao(database: AppDatabase): SubscriptionDao = database.subscriptionDao()
```

**Edge cases**:
- v5 DB upgrades → all existing playlists get empty youtubePlaylistId
- Subscriptions with same sourceId → unique constraint? No — allow duplicates but query by sourceId
- Empty subscriptions list → Flow emits empty list

**Pitfalls / do not**:
- Do NOT add unique index on sourceId — multiple users may subscribe to same source in multi-profile future
- Do NOT make migration destructive — ALTER TABLE only
- Do NOT forget `NOT NULL DEFAULT ''` for new non-nullable TEXT columns

**Validation**: App upgrades v5→v6 without data loss. Fresh install creates v6 schema.

---

### Step 7.2: Create SubscriptionDao + extend PlaylistDao

**What**: DAO for subscription CRUD. Extend PlaylistDao with youtubePlaylistId query.

**Where**:
- `app/src/main/java/com/example/medianest/data/local/dao/SubscriptionDao.kt` (new)
- `app/src/main/java/com/example/medianest/data/local/dao/PlaylistDao.kt` (extend)
- `app/src/main/java/com/example/medianest/di/DatabaseModule.kt` — wire provider

**SubscriptionDao.kt**:
```kotlin
package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.medianest.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY name ASC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Long): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE sourceType = :sourceType AND sourceId = :sourceId LIMIT 1")
    suspend fun getBySource(sourceType: String, sourceId: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE sourceType = :sourceType ORDER BY name ASC")
    fun getByType(sourceType: String): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)

    @Query("UPDATE subscriptions SET lastCheckedAt = :timestamp WHERE id = :id")
    suspend fun updateLastChecked(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE subscriptions SET autoDownload = :autoDownload, audioOnly = :audioOnly WHERE id = :id")
    suspend fun updateAutoDownload(id: Long, autoDownload: Boolean, audioOnly: Boolean)
}
```

**PlaylistDao.kt** — add:
```kotlin
    @Query("SELECT * FROM playlists WHERE youtubePlaylistId = :youtubePlaylistId LIMIT 1")
    suspend fun getByYoutubePlaylistId(youtubePlaylistId: String): PlaylistEntity?
```

**DatabaseModule.kt** — no changes needed (provider added in step 7.1).

**Edge cases**:
- Subscribe to already-subscribed source → `getBySource` check before insert
- Unsubscribe → cascade to any join rows? No join rows for subscriptions (independent entity)

**Pitfalls / do not**:
- Do NOT auto-delete playlists on unsubscribe — user may have manually saved playlist data
- Do NOT use `REPLACE` for subscription insert — use `IGNORE` or check first

**Validation**: Insert subscription → query returns it. Update autoDownload → reflected in DB.

---

### Step 7.3: Create SubscriptionRepository

**What**: Repository wrapping SubscriptionDao + PlaylistDao + YouTubeExtractor for subscribe/unsubscribe/refresh logic.

**Where**:
- `app/src/main/java/com/example/medianest/data/repository/SubscriptionRepository.kt` (new)

**How**:
```kotlin
package com.example.medianest.data.repository

import com.example.medianest.data.local.dao.PlaylistDao
import com.example.medianest.data.local.dao.SubscriptionDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.entity.PlaylistEntity
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.mapper.toVideoEntity
import com.example.medianest.extraction.YouTubeExtractor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val playlistDao: PlaylistDao,
    private val videoDao: VideoDao,
    private val youTubeExtractor: YouTubeExtractor
) {
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>> = subscriptionDao.getAllSubscriptions()

    fun getChannels(): Flow<List<SubscriptionEntity>> = subscriptionDao.getByType("channel")

    fun getPlaylistSubscriptions(): Flow<List<SubscriptionEntity>> = subscriptionDao.getByType("playlist")

    suspend fun getById(id: Long): SubscriptionEntity? = subscriptionDao.getById(id)

    suspend fun isSubscribed(sourceType: String, sourceId: String): Boolean =
        subscriptionDao.getBySource(sourceType, sourceId) != null

    suspend fun subscribe(
        sourceType: String,
        sourceId: String,
        name: String,
        thumbnailUrl: String? = null,
        uploaderName: String? = null
    ): Long {
        val existing = subscriptionDao.getBySource(sourceType, sourceId)
        if (existing != null) return existing.id

        return subscriptionDao.insert(
            SubscriptionEntity(
                sourceType = sourceType,
                sourceId = sourceId,
                name = name,
                thumbnailUrl = thumbnailUrl,
                uploaderName = uploaderName
            )
        )
    }

    suspend fun unsubscribe(id: Long) {
        subscriptionDao.delete(subscriptionDao.getById(id) ?: return)
    }

    suspend fun updateAutoDownload(id: Long, autoDownload: Boolean, audioOnly: Boolean) {
        subscriptionDao.updateAutoDownload(id, autoDownload, audioOnly)
    }

    suspend fun checkForUpdates(subscription: SubscriptionEntity): List<VideoEntity> {
        return if (subscription.sourceType == "channel") {
            checkChannel(subscription)
        } else {
            checkPlaylist(subscription)
        }
    }

    private suspend fun checkChannel(subscription: SubscriptionEntity): List<VideoEntity> {
        val channel = youTubeExtractor.extractChannel(subscription.sourceId)
        val newVideos = mutableListOf<VideoEntity>()
        for (video in channel.uploads) {
            val existing = videoDao.getVideoById(video.videoId)
            if (existing == null) {
                val entity = video.toVideoEntity()
                videoDao.insert(entity)
                newVideos.add(entity)
            }
        }
        subscriptionDao.updateLastChecked(subscription.id)
        return newVideos
    }

    private suspend fun checkPlaylist(subscription: SubscriptionEntity): List<VideoEntity> {
        val playlist = youTubeExtractor.extractPlaylist(subscription.sourceId)
        val newVideos = mutableListOf<VideoEntity>()

        // Upsert playlist metadata
        val existingPlaylist = playlistDao.getByYoutubePlaylistId(playlist.playlistId)
        if (existingPlaylist == null) {
            playlistDao.insert(
                PlaylistEntity(
                    name = playlist.name,
                    thumbnailUrl = playlist.thumbnailUrl,
                    youtubePlaylistId = playlist.playlistId,
                    uploaderName = playlist.uploaderName,
                    videoCount = playlist.videos.size
                )
            )
        }

        for (video in playlist.videos) {
            val existing = videoDao.getVideoById(video.videoId)
            if (existing == null) {
                val entity = video.toVideoEntity()
                videoDao.insert(entity)
                newVideos.add(entity)
            }
        }
        subscriptionDao.updateLastChecked(subscription.id)
        return newVideos
    }
}
```

**Edge cases**:
- Channel with no new uploads → empty list returned
- Playlist removed by YouTube → extraction throws, caught in Worker
- Video already in DB by ID → skip (dedup via `videoDao.getVideoById`)
- Very large channel (1000+ videos) → extraction may be slow; acceptable for now

**Pitfalls / do not**:
- Do NOT extract stream URLs for all videos during check — only metadata (video ID, title, etc.)
- Do NOT download videos automatically here — that's the Worker's job
- Do NOT block UI thread — repository functions are `suspend`

**Validation**: Subscribe to channel → checkForUpdates returns new videos since last extraction.

---

### Step 7.4: Create SubscriptionWorker (WorkManager)

**What**: Periodic WorkManager worker that checks all subscriptions for new uploads. Shows notification for new items. Optionally enqueues downloads based on auto-download flags.

**Where**:
- `app/src/main/java/com/example/medianest/worker/SubscriptionWorker.kt` (new)
- `app/src/main/java/com/example/medianest/di/WorkerModule.kt` (new, Hilt worker factory)
- Need to register periodic work request in Application class or a Hilt EagerSingleton

**SubscriptionWorker.kt**:
```kotlin
package com.example.medianest.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.data.repository.SubscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SubscriptionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val subscriptionRepository: SubscriptionRepository,
    private val downloadRepository: DownloadRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val subscriptions = subscriptionRepository.getAllSubscriptions().let {
            // Collect one-shot (not Flow) — use a suspend getAll
            emptyList<SubscriptionEntity>() // TODO: replace with DB query
        }

        var hasNew = false
        for (sub in subscriptions) {
            try {
                val newVideos = subscriptionRepository.checkForUpdates(sub)
                if (newVideos.isNotEmpty()) {
                    hasNew = true
                    if (sub.autoDownload) {
                        // TODO: auto-download best stream for each new video
                    }
                }
            } catch (_: Exception) {
                // Log and continue to next subscription
            }
        }

        return if (hasNew) Result.success() else Result.success()
    }
}
```

**Note**: The `getAllSubscriptions()` needs a suspend variant for one-shot use. Add to SubscriptionDao:
```kotlin
    @Query("SELECT * FROM subscriptions ORDER BY name ASC")
    suspend fun getAllSubscriptionsOnce(): List<SubscriptionEntity>
```

For auto-download, we need a mechanism to download the best stream. This is complex (needs stream extraction). For Phase 7, auto-download can just extract and enqueue the first video stream found. Streamline with:
```kotlin
    // In the worker, when autoDownload is true:
    val info = extractor.extractVideo(video.id)
    val bestStream = info.streamSources
        .filter { it.format == "video" }
        .maxByOrNull { parseQuality(it.quality) }
    if (bestStream != null) {
        downloadRepository.insert(
            DownloadEntity(
                videoId = video.id,
                url = bestStream.url,
                format = if (sub.audioOnly) "audio" else "video",
                quality = bestStream.quality,
                status = DownloadStatus.QUEUED,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl
            )
        )
    }
```

**WorkerModule.kt** (Hilt):
```kotlin
package com.example.medianest.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
// No explicit binding needed — @HiltWorker handles it
```

**Scheduling** (in Application or Hilt entry point):
```kotlin
    val request = PeriodicWorkRequestBuilder<SubscriptionWorker>(
        6, TimeUnit.HOURS
    ).setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    ).build()
    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        "subscription_check",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
```

**Edge cases**:
- No network → WorkManager defers due to constraint
- Subscription check fails for one → continue to next (no cascade failure)
- Multiple workers running → `ExistingPeriodicWorkPolicy.KEEP` prevents duplicates
- First run after install → checks all subscriptions immediately (if constraints met)

**Pitfalls / do not**:
- Do NOT make the worker run more than once per hour — be respectful of battery/network
- Do NOT block on slow extractions — each subscription check is independent
- Do NOT download auto-download items without network constraint

**Validation**: Schedule worker → runs on next constraint met → new videos detected and logged.

---

### Step 7.5: Create SubscriptionsViewModel + SubscriptionsScreen

**What**: ViewModel + screen for managing subscriptions. List subscribed channels/playlists, toggle auto-download, unsubscribe.

**Where**:
- `app/src/main/java/com/example/medianest/ui/viewmodel/SubscriptionsViewModel.kt` (new)
- `app/src/main/java/com/example/medianest/ui/screens/SubscriptionsScreen.kt` (new)

**SubscriptionsViewModel.kt**:
```kotlin
package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    val subscriptions = subscriptionRepository.getAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unsubscribe(id: Long) {
        viewModelScope.launch {
            subscriptionRepository.unsubscribe(id)
        }
    }

    fun updateAutoDownload(id: Long, autoDownload: Boolean, audioOnly: Boolean) {
        viewModelScope.launch {
            subscriptionRepository.updateAutoDownload(id, autoDownload, audioOnly)
        }
    }
}
```

**SubscriptionsScreen.kt**:
- LazyColumn with subscription cards (icon + name + source type badge + auto-download toggle)
- Swipe to delete or icon button to unsubscribe
- Empty state: "No subscriptions yet. Subscribe to channels or playlists from search results."
- Each item shows: thumbnail/avatar, name, source type ("Channel" / "Playlist"), last checked timestamp
- Auto-download toggle (switch) per item

**Edge cases**:
- Empty list → show instructional empty state
- Very long subscription names → ellipsize (maxLines=1)
- Unsubscribe → confirm dialog (optional, Phase 7 skip for now)

**Pitfalls / do not**:
- Do NOT show download progress here — that's in DownloadsScreen
- Do NOT allow subscribing to arbitrary URLs — only channel/playlist URLs

**Validation**: Open Subscriptions tab → shows subscribed items. Toggle auto-download → persisted.

---

### Step 7.6: Add Subscribe button to HomeScreen + VideoDetailScreen

**What**: Add "Subscribe" button on channel/playlist results in HomeScreen. Add subscribe option to VideoDetailScreen (subscribe to the video's channel).

**Where**:
- `app/src/main/java/com/example/medianest/ui/screens/HomeScreen.kt` (modify)
- `app/src/main/java/com/example/medianest/ui/screens/VideoDetailScreen.kt` (modify)
- `app/src/main/java/com/example/medianest/ui/viewmodel/HomeViewModel.kt` (modify)
- `app/src/main/java/com/example/medianest/ui/viewmodel/VideoDetailViewModel.kt` (modify)

**HomeScreen changes**:
- On `HomeUiState.PlaylistResult` — add a "Subscribe to Playlist" button at the top of the playlist section
- On `HomeUiState.ChannelResult` — add a "Subscribe to Channel" button at the top
- Add `onSubscribe` callback to HomeScreen: `onSubscribe: (sourceType: String, sourceId: String, name: String, thumbnailUrl: String?) -> Unit`
- HomeViewModel: inject `SubscriptionRepository`, add `subscribe(sourceType, sourceId, name, thumbnailUrl)` function

**VideoDetailScreen changes**:
- Add "Subscribe" icon button in TopAppBar actions (alongside favorite)
- If already subscribed, show "Subscribed" state (filled icon)
- Add `onSubscribe: (sourceType: String, sourceId: String, name: String, thumbnailUrl: String?) -> Unit` param
- VideoDetailViewModel: inject `SubscriptionRepository`, add `isSubscribed: StateFlow<Boolean>` + `checkSubscription(channelId)` + `toggleSubscription()` functions

**Edge cases**:
- Channel/playlist already subscribed → button shows "Subscribed" (disabled or checkmark)
- Video has no channelId → hide subscribe button (fallback to channelName URL, but YouTube extraction gives channel ID)
- Unsubscribe from detail screen → toggle off

**Pitfalls / do not**:
- Do NOT subscribe to individual videos — only channels and playlists
- Do NOT show subscribe button while loading/details not fully resolved

**Validation**: Subscribe from channel result → appears in Subscriptions screen. Subscribe from video detail → same.

---

### Step 7.7: Add Subscriptions to bottom nav + wire AppNavigation

**What**: Add Subscriptions as 5th bottom nav item. Wire navigation.

**Where**:
- `app/src/main/java/com/example/medianest/ui/navigation/BottomNavItem.kt`
- `app/src/main/java/com/example/medianest/ui/navigation/AppNavigation.kt`

**BottomNavItem.kt** — add:
```kotlin
    data object Subscriptions : BottomNavItem("subscriptions", "Subscriptions", Icons.Default.Subscriptions)
```

Add import: `import androidx.compose.material.icons.filled.Subscriptions`

**AppNavigation.kt** — add route:
```kotlin
        composable(BottomNavItem.Subscriptions.route) {
            SubscriptionsScreen(
                onSubscribe = { sourceType, sourceId, name, thumbnailUrl ->
                    // Handled internally by SubscriptionsViewModel
                }
            )
        }
```

Wire callback from HomeScreen to navigate to subscriptions:
- `HomeScreen` needs `navController` access or `onNavigateToSubscriptions` callback
- Simplest: add `LaunchedEffect` in HomeScreen that navigates after subscribe

**Edge cases**: 5 bottom nav items on small screens — text labels may truncate. Acceptable.

**Pitfalls / do not**:
- Do NOT add subscriptions as a tab inside Library — it deserves its own bottom nav slot given its complexity
- Do NOT forget `Icons.Default.Subscriptions` requires `material-icons-extended` (already in deps)

**Validation**: Bottom nav shows Subscriptions tab. Tapping shows subscribed items.

---

### Step 7.8: Add "Add to Playlist" action to Library screen

**What**: Allow users to add videos to playlists from Library screen.

**Where**:
- `app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt`

**How**:
- On video card long-press (or menu icon), show bottom sheet with playlist selection
- List all playlists, tap to add video
- Show toast/snackbar confirmation

**Edge cases**: No playlists created → show empty state or "Create playlist" option in sheet

**Pitfalls / do not**: Do NOT auto-create playlists — user must create them manually or via subscribe

---

### Step 7.9: Build and verify

**What**: Compile and verify build succeeds.

**How**:
```bash
./gradlew :app:assembleDebug
```

**Validation**: BUILD SUCCESSFUL.

---

## Beginner Implementation Guide (execution order)

1. Create `SubscriptionEntity.kt`
2. Create `SubscriptionDao.kt`
3. Extend `PlaylistEntity.kt` with youtubePlaylistId + uploaderName + videoCount
4. Extend `PlaylistDao.kt` with `getByYoutubePlaylistId`
5. Update `AppDatabase.kt` — add entity, version 6, abstract DAO
6. Create `MIGRATION_5_6` in `DatabaseModule.kt`, wire into builder, add provider
7. Create `SubscriptionRepository.kt`
8. Create `SubscriptionsViewModel.kt`
9. Create `SubscriptionsScreen.kt`
10. Create `SubscriptionWorker.kt` + auto-download logic
11. Add `getAllSubscriptionsOnce()` to SubscriptionDao
12. Update `HomeScreen.kt` + `HomeViewModel.kt` — subscribe button on channel/playlist
13. Update `VideoDetailScreen.kt` + `VideoDetailViewModel.kt` — subscribe button in actions
14. Add `Subscriptions` to `BottomNavItem.kt`
15. Wire in `AppNavigation.kt`
16. Add "Add to Playlist" to LibraryScreen
17. Build `./gradlew :app:assembleDebug`

---

## Final Verification Checklist

- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] App with v5 DB upgrades to v6 without data loss
- [ ] Fresh install creates v6 schema
- [ ] Subscribe to channel from HomeScreen channel result → appears in Subscriptions
- [ ] Subscribe to playlist from HomeScreen playlist result → appears in Subscriptions
- [ ] Subscribe to channel from VideoDetailScreen → appears in Subscriptions
- [ ] Unsubscribe → removed from list
- [ ] Auto-download toggle persisted and reflected
- [ ] Subscriptions tab shows list with correct icons
- [ ] Empty state shown when no subscriptions
- [ ] VideoDetailScreen shows subscribed/unsubscribed state correctly
- [ ] SubscriptionWorker compiles and can be scheduled
- [ ] Bottom nav has 5 items with Subscriptions
- [ ] PlaylistEntity has youtubePlaylistId field in DB
- [ ] "Add to Playlist" works on LibraryScreen

---

## Stop Conditions

- `MIGRATION_5_6` fails → verify v5 schema columns (videos has favorite, playlists has only basic fields)
- `SubscriptionWorker` Hilt injection fails → verify `@HiltWorker` and `@AssistedInject` annotations are correct
- `Icons.Default.Subscriptions` not found → verify `material-icons-extended` dependency (should already exist)
- Bottom nav overflow → 5 items is the max recommended; verify on real device
- Channel extraction returns empty uploads → YouTubeExtractor may not fully parse channel uploads (known NewPipeExtractor limitation). Accept for now — plan for Phase 7 post-MVP refinement.
