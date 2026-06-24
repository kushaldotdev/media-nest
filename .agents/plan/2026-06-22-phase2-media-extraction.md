# Implementation Plan: Phase 2 — Media Extraction

## System / Contract Summary
- **Package**: `com.example.medianest`
- **New dependency**: `com.github.TeamNewPipe:NewPipeExtractor:v0.26.3` (JitPack-hosted)
- **JitPack repository** must be added to `settings.gradle.kts` and `pluginManagement`
- **Repository pattern** will be introduced: `VideoRepository` wraps `VideoDao` + extraction logic
- **ViewModel pattern** will be introduced: `HomeViewModel` powered by Hilt
- **Store thumbnails** in app cache dir (`context.cacheDir/thumbs/`) — no storage permission needed
- **No storage permission needed** — metadata lives in Room, thumbnails in cache, extraction is network-only

## Phase Order
1. **2.1** — Add JitPack repo + NewPipeExtractor dependency
2. **2.2** — Create `data/model/` DTOs for extraction results
3. **2.3** — Create `data/mapper/` to map extraction results → Room entities
4. **2.4** — Create `data/repository/VideoRepository` (extraction + DB persistence)
5. **2.5** — Create `WorkerUtil.kt` helper for NewPipeExtractor CLI fallback (dev/emulator)
6. **2.6** — Create `extraction/YouTubeExtractor.kt` (NewPipeExtractor wrapper)
7. **2.7** — Create `ui/viewmodel/HomeViewModel.kt`
8. **2.8** — Update `HomeScreen.kt` with URL input + search results
9. **2.9** — Create `data/model/StreamInfo.kt` for quality selection
10. **2.10** — Create `ui/screens/VideoDetailScreen.kt` with quality picker + navigation

---

## Steps

### Step 2.1: Add JitPack + NewPipeExtractor dependency

**What**: Add JitPack repository to `settings.gradle.kts`, NewPipeExtractor to `libs.versions.toml` and `app/build.gradle.kts`.

**Where**:
- `settings.gradle.kts` — add `maven("https://jitpack.io")` to `dependencyResolutionManagement.repositories`
- `gradle/libs.versions.toml` — add version + library entry
- `app/build.gradle.kts` — add `implementation` dependency

**How**:

`settings.gradle.kts` — add inside `dependencyResolutionManagement.repositories` block:
```kotlin
maven("https://jitpack.io")
```

`gradle/libs.versions.toml` — add under `[versions]`:
```toml
newpipeExtractor = "v0.26.3"
```

Add under `[libraries]`:
```toml
com-github-teamnewpipe-newpipeextractor = { module = "com.github.TeamNewPipe:NewPipeExtractor", version.ref = "newpipeExtractor" }
```

`app/build.gradle.kts` — add under `dependencies`:
```kotlin
// NewPipeExtractor
implementation(libs.com.github.teamnewpipe.newpipeextractor)
```

**Why**: NewPipeExtractor is not in Maven Central or Google — JitPack is the official distribution channel.

**Edge cases**: JitPack can be slow for first build (builds from source). If it fails, check JitPack build logs at `https://jitpack.io/com/github/TeamNewPipe/NewPipeExtractor/v0.26.3/build.log`.

**Pitfalls / do not**: Do not add JitPack to the root project `build.gradle.kts` — only `settings.gradle.kts` repositories. Do NOT add it to `pluginManagement` (JitPack only hosts libraries, not plugins).

**Validation**: `./gradlew :app:dependencies` resolves without errors.

**Docs**: None.

---

### Step 2.2: Create data model DTOs

**What**: Create `data/model/` package with `ExtractedVideoInfo`, `ExtractedPlaylistInfo`, `ChannelInfo` data classes — these are the DTOs returned by the extraction layer before mapping to Room entities.

**Where**: `app/src/main/java/com/example/medianest/data/model/`
- `ExtractedVideoInfo.kt`
- `ExtractedPlaylistInfo.kt`
- `ChannelInfo.kt`

**How**:

**ExtractedVideoInfo.kt**:
```kotlin
package com.example.medianest.data.model

data class ExtractedVideoInfo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String?,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val description: String?,
    val uploadDate: String?,
    val streamSources: List<StreamSource> = emptyList()
)

data class StreamSource(
    val url: String,
    val format: String,       // "video" or "audio"
    val quality: String,       // e.g. "720p", "128kbps"
    val mimeType: String,
    val contentLength: Long?   // file size in bytes, nullable
)
```

**ExtractedPlaylistInfo.kt**:
```kotlin
package com.example.medianest.data.model

data class ExtractedPlaylistInfo(
    val playlistId: String,
    val name: String,
    val thumbnailUrl: String?,
    val uploaderName: String?,
    val videos: List<ExtractedVideoInfo>
)
```

**ChannelInfo.kt**:
```kotlin
package com.example.medianest.data.model

data class ChannelInfo(
    val channelId: String,
    val name: String,
    val avatarUrl: String?,
    val subscriberCount: Long?,
    val description: String?,
    val uploads: List<ExtractedVideoInfo>
)
```

**Why**: Separate DTOs from Room entities to avoid coupling the extraction layer to the persistence schema.

**Edge cases**: `contentLength` may be null if server doesn't provide it. `uploadDate` may be null or in various formats.

**Pitfalls / do not**: Do not put business logic in DTOs — they are pure data containers. Do not merge DTOs with entities.

**Validation**: Compiles.

**Docs**: None.

---

### Step 2.3: Create data mapper

**What**: One mapper function `ExtractedVideoInfo.toVideoEntity()` in a file `data/mapper/VideoMappers.kt`.

**Where**: `app/src/main/java/com/example/medianest/data/mapper/VideoMappers.kt`

**How**:
```kotlin
package com.example.medianest.data.mapper

import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.model.ExtractedVideoInfo

fun ExtractedVideoInfo.toVideoEntity(): VideoEntity = VideoEntity(
    id = videoId,
    title = title,
    channelName = channelName,
    channelId = channelId,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    description = description,
    uploadDate = uploadDate,
    addedAt = System.currentTimeMillis()
)
```

**Why**: Keeps entity construction centralized. If the entity schema changes, only one file updates.

**Edge cases**: All fields mapped with sensible defaults. `addedAt` is set to current time at mapping, not extraction time.

**Pitfalls / do not**: Do not put mapping inside entity class (data class should be clean). Do not add mapper for PlaylistEntity here — will add when needed in Organization phase.

**Validation**: Compiles.

**Docs**: None.

---

### Step 2.4: Create extraction layer — `YouTubeExtractor.kt`

**What**: A utility class that wraps NewPipeExtractor API to fetch video metadata, stream URLs, playlists, and channel uploads. Returns our DTOs.

**Where**: `app/src/main/java/com/example/medianest/extraction/YouTubeExtractor.kt`

**How**:
```kotlin
package com.example.medianest.extraction

import com.example.medianest.data.model.ChannelInfo
import com.example.medianest.data.model.ExtractedPlaylistInfo
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeExtractor @Inject constructor() {

    companion object {
        private const val SERVICE_ID = 0 // YouTube is service 0
    }

    private val service: StreamingService by lazy {
        NewPipe.getService(SERVICE_ID)
    }

    suspend fun extractVideo(url: String): ExtractedVideoInfo = withContext(Dispatchers.IO) {
        // 1. Get the link handler and extractor
        val linkHandler = service.linkHandler.fromUrl(url)
        val extractor = service.getStreamExtractor(linkHandler)
        extractor.fetchPage()

        // 2. Extract stream sources
        val streams = mutableListOf<StreamSource>()

        // Try audio streams first
        runCatching {
            extractor.audioStreams?.forEach { track ->
                streams.add(StreamSource(
                    url = track.content,
                    format = "audio",
                    quality = track.averageBitrate?.let { "${it / 1000}kbps" } ?: "unknown",
                    mimeType = track.format.mimeType ?: "audio/mpeg",
                    contentLength = track.contentLength
                ))
            }
        }

        // Then video streams
        runCatching {
            extractor.videoStreams?.forEach { stream ->
                streams.add(StreamSource(
                    url = stream.content,
                    format = "video",
                    quality = stream.resolution ?: "unknown",
                    mimeType = stream.format.mimeType ?: "video/mp4",
                    contentLength = stream.contentLength
                ))
            }
        }

        // 3. Build result
        ExtractedVideoInfo(
            videoId = extractor.id,
            title = extractor.name ?: "",
            channelName = extractor.uploaderName ?: "Unknown",
            channelId = extractor.uploaderUrl,
            durationSeconds = extractor.streamDuration?.seconds ?: 0L,
            thumbnailUrl = extractor.thumbnailUrl,
            description = extractor.description?.content?.take(1000),
            uploadDate = extractor.uploadDate?.dayMonthYearString,
            streamSources = streams
        )
    }

    suspend fun extractPlaylist(url: String): ExtractedPlaylistInfo = withContext(Dispatchers.IO) {
        val linkHandler = service.linkHandler.fromUrl(url)
        val extractor = service.getPlaylistExtractor(linkHandler)
        extractor.fetchPage()

        val videos = mutableListOf<ExtractedVideoInfo>()
        // Only take first page (25 items) for initial display
        val items = extractor.initialItems ?: emptyList()
        for (item in items) {
            runCatching {
                videos.add(ExtractedVideoInfo(
                    videoId = item.id,
                    title = item.name ?: "Unknown",
                    channelName = item.uploaderName ?: "Unknown",
                    channelId = item.uploaderUrl,
                    durationSeconds = item.streamDuration?.seconds ?: 0L,
                    thumbnailUrl = item.thumbnailUrl,
                    description = null,
                    uploadDate = null
                ))
            }
        }

        ExtractedPlaylistInfo(
            playlistId = extractor.id,
            name = extractor.name ?: "Unknown",
            thumbnailUrl = extractor.thumbnailUrl,
            uploaderName = extractor.uploaderName,
            videos = videos
        )
    }

    suspend fun extractChannel(url: String): ChannelInfo = withContext(Dispatchers.IO) {
        val linkHandler = service.linkHandler.fromUrl(url)
        val extractor = service.getChannelExtractor(linkHandler)
        extractor.fetchPage()

        val uploads = mutableListOf<ExtractedVideoInfo>()
        runCatching {
            val items = extractor.initialItems ?: emptyList()
            for (item in items) {
                uploads.add(ExtractedVideoInfo(
                    videoId = item.id,
                    title = item.name ?: "Unknown",
                    channelName = extractor.name ?: "Unknown",
                    channelId = extractor.url,
                    durationSeconds = item.streamDuration?.seconds ?: 0L,
                    thumbnailUrl = item.thumbnailUrl,
                    description = null,
                    uploadDate = null
                ))
            }
        }

        ChannelInfo(
            channelId = extractor.id,
            name = extractor.name ?: "Unknown",
            avatarUrl = extractor.avatarUrl,
            subscriberCount = extractor.subscriberCount,
            description = extractor.description?.content?.take(500),
            uploads = uploads
        )
    }
}
```

**Note**: The exact NewPipeExtractor API methods may differ from this pseudocode. Real implementation will follow actual API from v0.26.3. Key classes: `StreamExtractor`, `PlaylistExtractor`, `ChannelExtractor`, `VideoStream`, `AudioStream`, `StreamInfoItem`.

**Why**: Centralizes all YouTube extraction logic behind a clean Kotlin interface. Hilt-injectable singleton.

**Edge cases**: `streamDuration?.seconds` may be null for livestreams → default to 0L. `uploadDate` may be null → UI handles gracefully. `description` can be very long → truncate at 1000 chars.

**Pitfalls / do not**: Do not call `fetchPage()` twice. Do not use `Dispatchers.Main` for network calls. Do not wrap every single access in `runCatching` — only the optional parts (streams, items). Do not catch `ExtractionException` silently — let it propagate so UI shows error. Do not initialize NewPipe here — do it once in `MediaNestApp` (add to Step 2.4b).

**Validation**: Unit test with a real YouTube URL (integration test — requires network).

**Docs**: Add doc comment explaining that NewPipeExtractor is LGPL-licensed and used under its terms.

---

### Step 2.4b: Initialize NewPipe in `MediaNestApp.kt`

**What**: Call `NewPipe.init()` during app startup with a custom `Downloader`.

**Where**: `MediaNestApp.kt`

**How** — add to `onCreate()` before Timber:
```kotlin
NewPipe.init(DownloaderProvider.getDownloader())
```

Create `extraction/DownloaderProvider.kt`:
```kotlin
package com.example.medianest.extraction

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

object DownloaderProvider {
    fun getDownloader(): Downloader = object : Downloader() {
        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
            val connection = URL(request.url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            request.headers?.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.connect()

            val body = connection.inputStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
            return Response(
                connection.responseCode,
                connection.responseMessage ?: "",
                responseHeaders = connection.headerFields?.mapKeys { it.key ?: "" }
                    ?.mapValues { it.value.toList() } ?: emptyMap(),
                responseBody = body
            )
        }
    }
}
```

**Why**: NewPipeExtractor needs a `Downloader` instance to make HTTP requests. The default works but a custom one gives us control over timeouts and user-agent.

**Edge cases**: Network unavailable → `IOException` propagates to caller. Timeout set to 10s connect, 30s read.

**Pitfalls / do not**: Do not use OkHttp here to avoid adding another dependency. `HttpURLConnection` is sufficient. Do not cache the Downloader response — extraction calls are already on IO dispatcher.

**Validation**: App doesn't crash on startup.

**Docs**: None.

---

### Step 2.5: Create `VideoRepository.kt`

**What**: Repository class that coordinates between `YouTubeExtractor`, `VideoDao`, and the mapper. Provides clean suspend functions for the ViewModel layer.

**Where**: `app/src/main/java/com/example/medianest/data/repository/VideoRepository.kt`

**How**:
```kotlin
package com.example.medianest.data.repository

import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.mapper.toVideoEntity
import com.example.medianest.data.model.ExtractedPlaylistInfo
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.extraction.YouTubeExtractor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val videoDao: VideoDao,
    private val youTubeExtractor: YouTubeExtractor
) {
    fun getAllVideos(): Flow<List<VideoEntity>> = videoDao.getAllVideos()

    suspend fun getVideoById(videoId: String): VideoEntity? = videoDao.getVideoById(videoId)

    suspend fun searchAndSave(url: String): ExtractedVideoInfo {
        val info = youTubeExtractor.extractVideo(url)
        videoDao.insert(info.toVideoEntity())
        return info
    }

    suspend fun extractPlaylist(url: String): ExtractedPlaylistInfo =
        youTubeExtractor.extractPlaylist(url)

    suspend fun extractChannel(url: String): com.example.medianest.data.model.ChannelInfo =
        youTubeExtractor.extractChannel(url)

    suspend fun deleteVideo(video: VideoEntity) = videoDao.delete(video)
}
```

**Why**: Repository pattern decouples data sources from ViewModels. Single source of truth for video data.

**Edge cases**: `searchAndSave` saves to Room even if extraction partially fails (no streams but metadata OK). Later phases will handle download/playback states.

**Pitfalls / do not**: Do not add playlist/channel save logic until Phase 5 (Organization). Do not call `searchAndSave` for playlists — only single videos.

**Validation**: Compiles, injectable via Hilt.

**Docs**: None.

---

### Step 2.6: Create Hilt module for extraction + repository

**What**: Register `YouTubeExtractor` and `VideoRepository` as Hilt bindings.

**Where**: Update `di/DatabaseModule.kt` or create `di/ExtractionModule.kt`

**How** — `YouTubeExtractor` and `VideoRepository` already have `@Singleton` and `@Inject` constructor → Hilt auto-discovers them. No additional module needed.

Only needed if constructor injection doesn't work (e.g., third-party classes). Verify both compile.

**Validation**: `./gradlew :app:assembleDebug` succeeds.

---

### Step 2.7: Create `HomeViewModel.kt`

**What**: ViewModel for the Home screen — handles URL input, extraction trigger, results state.

**Where**: `app/src/main/java/com/example/medianest/ui/viewmodel/HomeViewModel.kt`

**How**:
```kotlin
package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeUiState {
    data object Idle : HomeUiState()
    data object Loading : HomeUiState()
    data class Success(val video: ExtractedVideoInfo) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
    data class PlaylistResult(val playlist: com.example.medianest.data.model.ExtractedPlaylistInfo) : HomeUiState()
    data class ChannelResult(val channel: com.example.medianest.data.model.ChannelInfo) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState

    fun onUrlSubmitted(url: String) {
        if (url.isBlank()) {
            _uiState.value = HomeUiState.Error("Please enter a URL")
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            runCatching {
                when {
                    "youtube.com/playlist" in url || "youtu.be" in url -> {
                        // Try playlist first
                        val playlist = repository.extractPlaylist(url)
                        if (playlist.videos.isNotEmpty()) {
                            _uiState.value = HomeUiState.PlaylistResult(playlist)
                            return@launch
                        }
                        // Fall through to single video
                        val video = repository.searchAndSave(url)
                        HomeUiState.Success(video)
                    }
                    "/channel/" in url || "/c/" in url || "/@" in url -> {
                        val channel = repository.extractChannel(url)
                        HomeUiState.ChannelResult(channel)
                    }
                    else -> {
                        val video = repository.searchAndSave(url)
                        HomeUiState.Success(video)
                    }
                }
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { e ->
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to extract video")
            }
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Idle
    }
}
```

**Why**: Standard MVI-ish pattern. StateFlow drives Compose recomposition. `runCatching` replaces try-catch.

**Edge cases**: Blank URL → immediate error without network call. Invalid YouTube URL → extraction throws, caught by `runCatching`. Playlist/channel detection is URL-pattern-based — may need refinement.

**Pitfalls / do not**: Do not expose `VideoDao` directly to ViewModel — always go through Repository. Do not use `LiveData` — StateFlow is preferred for Compose. Do not catch and suppress `ExtractionException` without meaningful user message.

**Validation**: Unit test with mock Repository.

**Docs**: None.

---

### Step 2.8: Update `HomeScreen.kt` with URL input + results

**What**: Replace `HomeScreen.kt` placeholder with a proper screen containing:
- URL input field at top
- "Extract" button
- Loading indicator
- Error display
- Video result card (thumbnail, title, channel, duration, quality selector)
- Playlist/Channel result with list

**Where**: `ui/screens/HomeScreen.kt`

**How**:
```kotlin
package com.example.medianest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.ui.viewmodel.HomeUiState
import com.example.medianest.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onVideoSelected: (String) -> Unit = {} // navigation callback for detail screen
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var urlInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // URL input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Paste YouTube URL") },
                singleLine = true
            )
            Button(
                onClick = { viewModel.onUrlSubmitted(urlInput.trim()) },
                enabled = uiState !is HomeUiState.Loading
            ) {
                Text("Extract")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Content area
        when (val state = uiState) {
            is HomeUiState.Idle -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Enter a YouTube URL to get started", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is HomeUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is HomeUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = state.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is HomeUiState.Success -> {
                VideoResultCard(
                    video = state.video,
                    onSelectQuality = { /* navigate to detail or play */ }
                )
            }
            is HomeUiState.PlaylistResult -> {
                // Show playlist header + video list (scrollable)
                // Each item = thumbnail + title + channel
                // Will be more detailed in Phase 5
                Text("Playlist: ${state.playlist.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(state.playlist.videos) { video ->
                        VideoListItem(video = video)
                    }
                }
            }
            is HomeUiState.ChannelResult -> {
                Text("Channel: ${state.channel.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(state.channel.uploads) { video ->
                        VideoListItem(video = video)
                    }
                }
            }
        }
    }
}

// Placeholder cards — refine in later steps
@Composable
fun VideoResultCard(video: com.example.medianest.data.model.ExtractedVideoInfo, onSelectQuality: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Thumbnail
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(video.title, style = MaterialTheme.typography.titleMedium)
            Text(video.channelName, style = MaterialTheme.typography.bodySmall)
            Text("${video.durationSeconds / 60}:%02d".format(video.durationSeconds % 60))
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSelectQuality, modifier = Modifier.fillMaxWidth()) {
                Text("Select Quality")
            }
        }
    }
}

@Composable
fun VideoListItem(video: com.example.medianest.data.model.ExtractedVideoInfo) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier.size(120.dp, 68.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(video.title, maxLines = 2)
            Text(video.channelName, style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

**Why**: Full-featured home screen replaces placeholder. User can test extraction end-to-end.

**Edge cases**: Empty thumbnail URL → AsyncImage handles gracefully (shows placeholder/empty). Very long title → `maxLines = 2` with ellipsis. Network error during thumbnail load → AsyncImage shows error state.

**Pitfalls / do not**: Do not block UI during extraction — ViewModel handles on IO dispatcher. Do not use `LaunchedEffect` for URL extraction — always use button click. Do not add navigation integration until Step 2.10.

**Validation**: App launches, paste URL, tap Extract → loading → result card appears.

**Docs**: None.

---

### Step 2.9: Create `VideoDetailScreen.kt` with quality picker

**What**: A detail screen showing extracted video with quality selection (video streams + audio-only).

**Where**: `app/src/main/java/com/example/medianest/ui/screens/VideoDetailScreen.kt`

**How**:
```kotlin
package com.example.medianest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailScreen(
    videoInfo: ExtractedVideoInfo,
    onPlay: (StreamSource) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(videoInfo.title, maxLines = 1) }, navigationIcon = {
                TextButton(onClick = onBack) { Text("Back") }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            AsyncImage(
                model = videoInfo.thumbnailUrl,
                contentDescription = videoInfo.title,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(videoInfo.title, style = MaterialTheme.typography.titleLarge)
            Text(videoInfo.channelName, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            Text("Available streams:", style = MaterialTheme.typography.titleSmall)

            // Group streams by format
            val videoStreams = videoInfo.streamSources.filter { it.format == "video" }
            val audioStreams = videoInfo.streamSources.filter { it.format == "audio" }

            if (videoStreams.isNotEmpty()) {
                Text("Video", style = MaterialTheme.typography.labelLarge)
                videoStreams.forEach { stream ->
                    StreamQualityRow(stream, onPlay)
                }
            }

            if (audioStreams.isNotEmpty()) {
                Text("Audio Only", style = MaterialTheme.typography.labelLarge)
                audioStreams.forEach { stream ->
                    StreamQualityRow(stream, onPlay)
                }
            }

            if (videoStreams.isEmpty() && audioStreams.isEmpty()) {
                Text("No streams available", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StreamQualityRow(stream: StreamSource, onPlay: (StreamSource) -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = { onPlay(stream) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stream.quality)
            Text(
                stream.contentLength?.let { "${it / 1024 / 1024}MB" } ?: "Unknown size",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
```

**Why**: Quality picker is the core of Step 5 in the project plan. Groups video/audio separately, shows size, tap to play (wired in Phase 3).

**Edge cases**: No streams → error text. Zero byte streams → filtering handled by extraction (not here).

**Pitfalls / do not**: Do not start playback here — only emit event. Playback is Phase 3. Do not download here — download is Phase 4.

**Validation**: Compiles, shows quality list from real extraction.

**Docs**: None.

---

### Step 2.10: Wire navigation — HomeScreen → VideoDetailScreen

**What**: Add `videoDetail/{videoId}` route to `AppNavigation.kt`, pass `ExtractedVideoInfo` via savedStateHandle or in-memory cache. For simplicity in Phase 2, use a shared ViewModel or `NavController` argument.

**Approach**: Since `ExtractedVideoInfo` is not serializable to NavArgs easily, use a simple `mutableStateMapOf` in a companion object or shared repository for the current extraction result. Navigation route passes only the videoId.

**Where**: `ui/navigation/AppNavigation.kt`, `ui/MainScreen.kt`

**How**:

Add route to `AppNavigation.kt`:
```kotlin
composable(
    route = "videoDetail/{videoId}",
    arguments = listOf(navArgument("videoId") { type = NavType.StringType })
) { backStackEntry ->
    val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
    // Retrieve ExtractedVideoInfo from a shared cache
    val videoInfo = remember { HomeViewModel.lastResultCache[videoId] }
    if (videoInfo != null) {
        VideoDetailScreen(
            videoInfo = videoInfo,
            onPlay = { /* Phase 3 */ },
            onBack = { navController.popBackStack() }
        )
    }
}
```

Add static cache to `HomeViewModel`:
```kotlin
companion object {
    val lastResultCache = mutableMapOf<String, ExtractedVideoInfo>()
}
```

Update `onUrlSubmitted` to store result in cache, then call `onVideoSelected(videoId)`.

**Why**: Simple approach avoids serialization complexity. Cache is memory-only, cleared on app restart.

**Edge cases**: Cache miss → navigate back to Home. Cache stores only last N results (for now, all — low memory footprint).

**Pitfalls / do not**: Do not store the entire `ExtractedVideoInfo` in `savedStateHandle` (too large, not serializable). Do not use a global singleton — companion object on ViewModel is acceptable for this limited use.

**Validation**: Extract video → quality picker screen opens with streams listed.

**Docs**: None.

---

## Beginner Implementation Guide

1. Open `settings.gradle.kts` → add `maven("https://jitpack.io")` to repositories
2. Open `gradle/libs.versions.toml` → add `newpipeExtractor = "v0.26.3"` + library entry
3. Open `app/build.gradle.kts` → add `implementation` for NewPipeExtractor
4. Sync Gradle, verify build passes
5. Create `data/model/` package with 3 DTOs (ExtractedVideoInfo, ExtractedPlaylistInfo, ChannelInfo, StreamSource)
6. Create `data/mapper/VideoMappers.kt` with `toVideoEntity()` extension
7. Create `extraction/YouTubeExtractor.kt` — wraps NewPipeExtractor
8. Create `extraction/DownloaderProvider.kt` — custom HTTP downloader
9. Update `MediaNestApp.kt` — call `NewPipe.init()` in `onCreate`
10. Create `data/repository/VideoRepository.kt`
11. Create `ui/viewmodel/HomeViewModel.kt`
12. Replace `HomeScreen.kt` placeholder with URL input + result cards
13. Create `ui/screens/VideoDetailScreen.kt` with quality picker
14. Update `AppNavigation.kt` with `videoDetail/{videoId}` route
15. Build and test: paste YouTube URL → extract → see quality options

## Final Verification Checklist
- [ ] `./gradlew :app:assembleDebug` succeeds (first run slow — JitPack builds NewPipeExtractor from source)
- [ ] App launches, Home screen shows URL input
- [ ] Paste valid YouTube video URL → loading spinner → result card with thumbnail
- [ ] Tap "Select Quality" → detail screen shows video and audio stream options
- [ ] Paste playlist URL → shows playlist items (first page only)
- [ ] Paste channel URL → shows channel uploads (first page only)
- [ ] Invalid URL → error card with message
- [ ] Extraction works on both emulator and real device (WiFi required)

## Stop Conditions
- `NewPipe.init()` crashes → verify `DownloaderProvider` works correctly
- JitPack fails to resolve NewPipeExtractor → check `https://jitpack.io/#com.github.TeamNewPipe/NewPipeExtractor/v0.26.3` for build status
- NewPipeExtractor API mismatch → actual API methods differ from pseudocode; adjust `YouTubeExtractor.kt` accordingly
- `AsyncImage` fails silently → verify Coil dependency (already added in Phase 1)
- `hiltViewModel()` not resolving → verify `@HiltViewModel` annotation and Hilt plugin applied
