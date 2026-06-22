# Implementation Plan: Phase 3 — Playback

## System / Contract Summary
- **Package**: `com.example.medianest`
- **Existing deps**: `media3-exoplayer`, `media3-ui`, `media3-session` at `1.6.1`
- **Add dep**: `media3-exoplayer-hls` for HLS/DASH YouTube streams
- **Existing History**: `HistoryEntity(positionMillis, playedAt)` — needs upsert, no `completed` flag
- **DataStore** already wired — used for global playback speed
- **Player architecture**: Composable `AndroidView` wrapping ExoPlayer inside Navigation (not separate Activity)
- **Shared player**: Single `PlayerScreen` handles both video (ExoPlayer visible) and audio (thumbnail-as-album-art, player controls visible)
- **Global speed**: Stored in DataStore preferences, applies to all videos
- **Background playback**: Media3 `MediaSessionService` pattern with persistent notification
- **Stream sources**: Passed as `videoId` + `streamIndex` via nav argument, resolved from cache

## Phase Order
1. **3.1** — Add `media3-exoplayer-hls` dependency + DataStore settings for global playback speed
2. **3.2** — Create `PlayerUiState` data class + `PlayerViewModel` (player lifecycle, position tracking, speed control, MediaSession connection)
3. **3.3** — Create `PlayerScreen` composable with ExoPlayer `AndroidView`, controls overlay, audio-mode fallback UI
4. **3.4** — Update `HistoryDao` with upsert (`INSERT OR REPLACE`), wire periodic position saves from PlayerViewModel
5. **3.5** — Create `PlaybackService` (MediaSessionService) for background playback + notification channel
6. **3.6** — Wire navigation: add `player/{videoId}?streamIndex={n}` route, update `VideoDetailScreen.onPlay` to navigate, hide bottom nav on player routes
7. **3.7** — Add playback speed selector to player controls
8. **3.8** — Handle playback errors with error state + retry

---

## Steps

### Step 3.1: Add HLS dependency + DataStore speed settings

**What**: Add `media3-exoplayer-hls` to libs catalog and build file. Create a simple `SettingsRepository` (or use DataStore directly) for persisting global playback speed.

**Where**:
- `gradle/libs.versions.toml` — add HLS library entry
- `app/build.gradle.kts` — add `implementation` for HLS
- `app/src/main/java/com/example/medianest/data/preferences/PlaybackPreferences.kt` — DataStore helper for speed

**How**:

`gradle/libs.versions.toml` — add under `[libraries]`:
```toml
androidx-media3-exoplayer-hls = { module = "androidx.media3:media3-exoplayer-hls", version.ref = "media3" }
```

`app/build.gradle.kts` — add under `dependencies`:
```kotlin
implementation(libs.androidx.media3.exoplayer.hls)
```

`PlaybackPreferences.kt`:
```kotlin
package com.example.medianest.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playbackStore: DataStore<Preferences> by preferencesDataStore(name = "playback_prefs")

class PlaybackPreferences(private val context: Context) {
    companion object {
        private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        const val DEFAULT_SPEED = 1.0f
    }

    val playbackSpeed: Flow<Float> = context.playbackStore.data.map { prefs ->
        prefs[PLAYBACK_SPEED] ?: DEFAULT_SPEED
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.playbackStore.edit { prefs ->
            prefs[PLAYBACK_SPEED] = speed
        }
    }
}
```

Hilt provider for `PlaybackPreferences` — add to a new `di/PlaybackModule.kt`:
```kotlin
package com.example.medianest.di

import android.content.Context
import com.example.medianest.data.preferences.PlaybackPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    @Provides
    @Singleton
    fun providePlaybackPreferences(@ApplicationContext context: Context): PlaybackPreferences =
        PlaybackPreferences(context)
}
```

**Why**: YouTube streams often use HLS/DASH; without this dep, many streams won't play. DataStore for speed avoids Room schema changes.

**Edge cases**: Missing HLS dep → some streams silently fail. Speed range: 0.25x–3.0f (validate in UI).

**Pitfalls / do not**: Do NOT store speed in History — user wants global only. Do not use SharedPreferences (DataStore is already a dep).

**Validation**: `./gradlew :app:assembleDebug` succeeds.

**Docs**: None.

---

### Step 3.2: Create PlayerViewModel

**What**: ViewModel that owns the `ExoPlayer` instance, manages playback lifecycle, reports position, applies speed, connects to `PlaybackPreferences` for speed persistence.

**Where**: `app/src/main/java/com/example/medianest/ui/viewmodel/PlayerViewModel.kt`

**How**:

```kotlin
package com.example.medianest.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.preferences.PlaybackPreferences
import com.example.medianest.ui.viewmodel.HomeViewModel.Companion.lastResultCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val currentSpeed: Float = 1.0f,
    val isAudioOnly: Boolean = false,
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String? = null,
    val error: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: HistoryDao,
    private val playbackPreferences: PlaybackPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                    if (isPlaying) startPositionTracking() else stopPositionTracking()
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) saveFinalPosition()
                }
                override fun onPlayerError(error: Player.PlayerError) {
                    _uiState.value = _uiState.value.copy(error = error.message ?: "Playback error")
                }
            })
        }
    }

    private var positionTrackingJob: Job? = null
    private var currentVideoId: String? = null
    private var currentStream: StreamSource? = null

    fun initialize(videoId: String, streamIndex: Int) {
        currentVideoId = videoId
        val videoInfo = lastResultCache[videoId] ?: return
        val streams = videoInfo.streamSources
        if (streamIndex >= streams.size) {
            _uiState.value = _uiState.value.copy(error = "Stream not found")
            return
        }
        currentStream = streams[streamIndex]

        _uiState.value = _uiState.value.copy(
            title = videoInfo.title,
            channelName = videoInfo.channelName,
            thumbnailUrl = videoInfo.thumbnailUrl,
            isAudioOnly = currentStream?.format != "video",
            durationMs = videoInfo.durationSeconds * 1000
        )

        viewModelScope.launch {
            // Apply global speed
            val speed = playbackPreferences.playbackSpeed.first()
            player.setPlaybackSpeed(speed)
            _uiState.value = _uiState.value.copy(currentSpeed = speed)

            // Restore position from history
            val lastPlayback = historyDao.getLatestPlayback(videoId)
            val startPosition = lastPlayback?.positionMillis ?: 0L

            // Prepare media
            val mediaItem = MediaItem.Builder()
                .setUri(currentStream?.url ?: return@launch)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(videoInfo.title)
                        .setArtist(videoInfo.channelName)
                        .build()
                )
                .build()
            player.setMediaItem(mediaItem)
            player.seekTo(startPosition)
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(currentSpeed = speed)
        viewModelScope.launch { playbackPreferences.setPlaybackSpeed(speed) }
    }

    private fun startPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                savePosition()
            }
        }
    }

    private fun stopPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = null
        savePosition()
    }

    private fun savePosition() {
        val videoId = currentVideoId ?: return
        val pos = player.currentPosition
        viewModelScope.launch {
            historyDao.upsert(
                HistoryEntity(
                    videoId = videoId,
                    positionMillis = pos,
                    playedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun saveFinalPosition() {
        val videoId = currentVideoId ?: return
        viewModelScope.launch {
            historyDao.upsert(
                HistoryEntity(
                    videoId = videoId,
                    positionMillis = player.duration,
                    playedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun resetError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
```

**Edge cases**: `streamIndex` out of bounds → error state. No cached video → error state. Zero duration → position tracking still works. Player error mid-playback → error state with retry.

**Pitfalls / do not**: Do NOT call `player.release()` before saving final position — `onCleared()` saves via `stopPositionTracking()`. Do not create multiple ExoPlayer instances — ViewModel scoped to composable lifecycle. Do not use `remember` for player — ViewModel is the owner.

**Validation**: Compiles.

**Docs**: None.

---

### Step 3.3: Create PlayerScreen composable

**What**: Full-screen player composable that wraps ExoPlayer via `AndroidView`. Shows player controls overlay (play/pause, seek, speed). In audio mode, shows large centered thumbnail as album art.

**Where**: `app/src/main/java/com/example/medianest/ui/screens/PlayerScreen.kt`

**How**:

```kotlin
package com.example.medianest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoId: String,
    streamIndex: Int,
    viewModel: PlayerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(videoId, streamIndex) {
        viewModel.initialize(videoId, streamIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Video or audio area
            if (state.isAudioOnly) {
                // Audio mode — large centered thumbnail as album art
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = state.thumbnailUrl,
                        contentDescription = state.title,
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .aspectRatio(1f)
                    )
                }
            } else {
                // Video mode — ExoPlayer SurfaceView
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { viewModel.player.surfaceView },
                        modifier = Modifier.fillMaxSize()
                    )
                    // Tap to toggle controls
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { viewModel.togglePlayPause() }
                    )
                }
            }

            // Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Seek bar
                Slider(
                    value = state.positionMs.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..maxOf(state.durationMs, 1L).toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                // Time labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(state.positionMs))
                    Text(formatDuration(state.durationMs))
                }

                Spacer(Modifier.height(8.dp))

                // Play/pause + speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* Previous — future */ }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    IconButton(onClick = { /* Next — future */ }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }

                // Speed display — full speed selector is Step 3.7
                Text(
                    "Speed: ${state.currentSpeed}x",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        // Error overlay
        state.error?.let { errorMsg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { viewModel.resetError() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMsg, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap to dismiss", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
```

**Edge cases**: State.error → full-screen overlay with dismiss. Audio mode → ExoPlayer surface hidden, thumbnail acts as album art. Zero-duration stream → Slider with single value.

**Pitfalls / do not**: Do not use `DisposableEffect` for player lifecycle — ViewModel's `onCleared()` handles release. Do not use `PlayerView` from the View-based API — wrap via `AndroidView`. `player.surfaceView` is a `SurfaceView` that is managed by ExoPlayer.

**Validation**: Compiles, navigation works.

**Docs**: None.

---

### Step 3.4: Update HistoryDao with upsert

**What**: Add `upsert` method to `HistoryDao` using `INSERT OR REPLACE` (need `@Upsert` from Room 2.7 or use `@Insert(onConflict = REPLACE)`).

**Where**: `app/src/main/java/com/example/medianest/data/local/dao/HistoryDao.kt`

**How** — replace existing file:

```kotlin
package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.medianest.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE videoId = :videoId ORDER BY playedAt DESC LIMIT 1")
    suspend fun getLatestPlayback(videoId: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: HistoryEntity)

    @Query("DELETE FROM playback_history WHERE playedAt < :beforeTimestamp")
    suspend fun deleteOldEntries(beforeTimestamp: Long)
}
```

Key changes: `insert` → `upsert`, added `@Insert(onConflict = OnConflictStrategy.REPLACE)`. The HistoryEntity must have a unique constraint on `videoId` for REPLACE to work correctly. Currently the PK is `id(autoGenerate)` — need to change to composite or unique index.

Add unique index to `HistoryEntity`:
```kotlin
@Entity(
    tableName = "playback_history",
    foreignKeys = [...],
    indices = [Index("videoId")]
)
```
The current schema has `id` as auto-generated PK but no unique on `videoId`. With `ON_CONFLICT_REPLACE`, Room needs a conflict target. Since `id` is the only PK and auto-generated, `REPLACE` will always insert a new row (different auto-generated PK). 

**Fix**: Change the entity so `videoId` is unique, or use `@Upsert` annotation. Room 2.7.2 supports `@Upsert` natively. Simplest approach: use `@Upsert` which was added in Room 2.7.0-alpha03.

Change to:
```kotlin
@Dao
interface HistoryDao {
    // ... existing queries unchanged ...

    @Upsert
    suspend fun upsert(history: HistoryEntity)
}
```

`@Upsert` uses `videoId` unique index to determine insert vs update. No entity change needed.

**Why**: Without upsert, each position save creates a new row — infinite growth. `@Upsert` handles insert-or-update based on the unique index on `videoId`.

**Edge cases**: Multiple rapid saves → last one wins. Concurrent saves → Room serializes.

**Pitfalls / do not**: Do not use raw `@Insert(onConflict = REPLACE)` with auto-generated PK — it inserts new rows because PK differs every time. Use `@Upsert` instead.

**Validation**: Compiles. Tracked positions appear in DB after playback.

**Docs**: None.

---

### Step 3.5: Create PlaybackService (MediaSessionService)

**What**: A `MediaSessionService` that allows background playback, lock screen controls, and notification. Connects to the ExoPlayer instance.

**Where**: `app/src/main/java/com/example/medianest/service/PlaybackService.kt`

**How**:

```kotlin
package com.example.medianest.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerResolver.player ?: return
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()
        player.setAudioAttributes(audioAttributes, true)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
```

`PlayerResolver` — a simple singleton bridge so the ViewModel's ExoPlayer instance can be shared with the service:

```kotlin
package com.example.medianest.service

import androidx.media3.exoplayer.ExoPlayer

object PlayerResolver {
    var player: ExoPlayer? = null
}
```

Update `PlayerViewModel` to set `PlayerResolver.player = player` after creating it.

Register service in `AndroidManifest.xml`:
```xml
<service
    android:name=".service.PlaybackService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

Also add notification permission for Android 13+ (API 33+):
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

**Why**: MediaSessionService is the recommended approach for background playback since Android 11. Enables lock screen controls. Without it, playback stops when app goes to background.

**Edge cases**: App killed by OS → service stops, player released. No media loaded → service stops immediately. Permission denied for notifications on API 33+ → no controls in notification shade but playback continues.

**Pitfalls / do not**: Do NOT create a second ExoPlayer in the service — share via `PlayerResolver`. Do NOT forget `foregroundServiceType="mediaPlayback"` — required for API 34+. Do NOT call `stopSelf()` while media is actively playing.

**Validation**: Play a video/audio → press home → playback continues in background → notification appears with controls.

**Docs**:
- AndroidManifest.xml — add `<service>` element
- `AGENTS.md` — note `POST_NOTIFICATIONS` permission for Android 13+

---

### Step 3.6: Wire navigation — player route + bottom nav hiding

**What**: Add `player/{videoId}?streamIndex={n}` route to `AppNavigation.kt`. Update `VideoDetailScreen.onPlay` to navigate. Hide bottom navigation bar on player screen.

**Where**: `ui/navigation/AppNavigation.kt`, `ui/MainScreen.kt`, `ui/screens/VideoDetailScreen.kt`

**How**:

`AppNavigation.kt` — add player composable:
```kotlin
composable(
    route = "player/{videoId}?streamIndex={streamIndex}",
    arguments = listOf(
        navArgument("videoId") { type = NavType.StringType },
        navArgument("streamIndex") { type = NavType.IntType; defaultValue = 0 }
    )
) { backStackEntry ->
    val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
    val streamIndex = backStackEntry.arguments?.getInt("streamIndex") ?: 0
    PlayerScreen(
        videoId = videoId,
        streamIndex = streamIndex,
        onBack = { navController.popBackStack() }
    )
}
```

Update `VideoDetailScreen.onPlay` call site in `AppNavigation.kt`:
```kotlin
VideoDetailScreen(
    videoInfo = videoInfo,
    onPlay = { stream ->
        val streamIndex = videoInfo.streamSources.indexOf(stream)
        navController.navigate("player/$videoId?streamIndex=$streamIndex")
    },
    onBack = { navController.popBackStack() }
)
```

`MainScreen.kt` — hide bottom bar on player route:
```kotlin
val showBottomBar = navBackStackEntry?.destination?.route?.let { route ->
    !route.startsWith("player/")
} ?: true

Scaffold(
    bottomBar = {
        if (showBottomBar) {
            NavigationBar { ... }
        }
    }
) { innerPadding -> ... }
```

**Edge cases**: `streamIndex` invalid (out of bounds) → PlayerViewModel handles with error state. No stream sources → streamIndex defaults to 0, will error. Back navigation → pops to previous screen.

**Pitfalls / do not**: Do NOT hide top app bar — user needs back button. Do NOT use `startActivity()` — stay within Navigation. Do not make `streamIndex` a required path param — use query param with default.

**Validation**: Extract video → Select Quality → pick a stream → player screen opens. Bottom nav hidden. Back button returns to detail.

**Docs**: None.

---

### Step 3.7: Add playback speed selector

**What**: Replace the static speed text in `PlayerScreen` with a row of speed chips (0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x) that call `viewModel.setSpeed()`.

**Where**: `ui/screens/PlayerScreen.kt` — replace the speed text line.

**How** — replace:
```kotlin
Text(
    "Speed: ${state.currentSpeed}x",
    style = MaterialTheme.typography.bodySmall,
    modifier = Modifier.align(Alignment.CenterHorizontally)
)
```

With:
```kotlin
// Speed selector
Text("Speed", style = MaterialTheme.typography.labelMedium)
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
        FilterChip(
            selected = state.currentSpeed == speed,
            onClick = { viewModel.setSpeed(speed) },
            label = { Text("${speed}x") }
        )
    }
}
```

Add import for `FilterChip` from Material3.

**Edge cases**: Selecting same chip again → no-op (speed stays same). User sets speed before media loaded → applied immediately, player applies after prepare.

**Pitfalls / do not**: Do not add custom speed text input — keep it simple with presets. Do not change speed while streaming HLS (Media3 handles seamlessly).

**Validation**: Select speed → playback changes. Dismiss app → reopen → speed persists from DataStore.

**Docs**: None.

---

### Step 3.8: Handle playback errors with retry

**What**: Add a retry button to the error overlay in `PlayerScreen`. When tapped, re-initialize the player with the same `videoId`/`streamIndex`.

**Where**: `PlayerViewModel.kt` — add `retry()` function. `PlayerScreen.kt` — update error overlay.

**How**:

`PlayerViewModel.kt` — add:
```kotlin
fun retry() {
    val videoId = currentVideoId ?: return
    val si = currentStream?.let { videoInfo?.streamSources?.indexOf(it) } ?: 0
    _uiState.value = _uiState.value.copy(error = null)
    initialize(videoId, si)
}
```

Need to store `videoInfo` reference:
```kotlin
private var videoInfo: ExtractedVideoInfo? = null
// In initialize():
this.videoInfo = lastResultCache[videoId]
```

`PlayerScreen.kt` — update error overlay:
```kotlin
Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(errorMsg, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Button(onClick = { viewModel.retry() }) {
        Text("Retry")
    }
    Spacer(Modifier.height(4.dp))
    Text("Tap to dismiss", color = Color.White.copy(alpha = 0.7f))
}
```

**Edge cases**: Stream URL expired after initial extraction → retry will fail again. Player error caused by bad network → retry after reconnection works. Continuous errors → user can dismiss and try a different stream.

**Pitfalls / do not**: Do not auto-retry — let user decide. Do not clear error state without user action.

**Validation**: Playback fails → error overlay with Retry button → tap Retry → playback restarts.

**Docs**: None.

---

## Beginner Implementation Guide

1. Open `gradle/libs.versions.toml` → add `androidx-media3-exoplayer-hls` library, then add dep to `app/build.gradle.kts` and sync
2. Create `data/preferences/PlaybackPreferences.kt` with DataStore-backed speed storage
3. Create `di/PlaybackModule.kt` — Hilt provider for PlaybackPreferences
4. Create `ui/viewmodel/PlayerViewModel.kt` — ExoPlayer lifecycle + position tracking + speed
5. Update `data/local/dao/HistoryDao.kt` — change insert to upsert
6. Create `ui/screens/PlayerScreen.kt` — AndroidView ExoPlayer + controls
7. Create `service/PlaybackService.kt` + `service/PlayerResolver.kt` — background playback
8. Register service + notification permission in `AndroidManifest.xml`
9. Update `ui/navigation/AppNavigation.kt` — add player route
10. Wire `VideoDetailScreen.onPlay` to navigate to player
11. Update `ui/MainScreen.kt` — hide bottom nav on player routes
12. Add speed selector chips to PlayerScreen
13. Add retry logic to error overlay

## Final Verification Checklist
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] Extract a video → select quality → player opens
- [ ] Video plays with audio
- [ ] Audio-only streams show thumbnail-as-album-art UI
- [ ] Play/pause toggle works
- [ ] Seek slider works
- [ ] Speed selector changes playback speed
- [ ] Speed persists across app restarts
- [ ] Press home → playback continues in background
- [ ] Notification appears with play/pause controls
- [ ] Lock screen controls work
- [ ] Close app → playback stops, position saved
- [ ] Reopen app → position restored from history
- [ ] Player error → error overlay with Retry button
- [ ] Bottom navigation hidden on player screen
- [ ] Back button returns to video detail

## Stop Conditions
- ExoPlayer `surfaceView` not available → verify `ExoPlayer.Builder` + `SurfaceView` setup
- `@Upsert` not recognized → verify Room version `2.7.2` supports it (it does since `2.7.0-alpha03`)
- Background playback stops immediately → verify `foregroundServiceType="mediaPlayback"` in manifest
- Notification doesn't appear → verify `POST_NOTIFICATIONS` permission on API 33+
- `AndroidView` doesn't show video → verify player is prepared before `factory` lambda returns
- Speed selector doesn't persist → verify DataStore edit completes before VM clears
