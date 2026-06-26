package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.model.ChannelInfo
import com.example.medianest.data.model.ExtractedPlaylistInfo
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.repository.SubscriptionRepository
import com.example.medianest.data.repository.VideoRepository
import com.example.medianest.data.local.dao.LinkHistoryDao
import com.example.medianest.data.local.entity.LinkHistoryEntity
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.VideoFolderJoin
import com.example.medianest.data.mapper.toVideoEntity
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.service.AudioExtractor
import com.example.medianest.data.model.StreamSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeUiState {
    data object Idle : HomeUiState()
    data object Loading : HomeUiState()
    data class Success(val video: ExtractedVideoInfo) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
    data class PlaylistResult(val playlist: ExtractedPlaylistInfo) : HomeUiState()
    data class ChannelResult(val channel: ChannelInfo) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val repository: VideoRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val linkHistoryDao: LinkHistoryDao,
    private val folderDao: FolderDao,
    private val videoFolderDao: VideoFolderDao,
    private val videoDao: VideoDao,
    private val downloadRepository: DownloadRepository,
    private val audioExtractor: AudioExtractor,
    private val historyDao: HistoryDao
) : ViewModel() {

    companion object {
        val lastResultCache = android.util.LruCache<String, ExtractedVideoInfo>(50)
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _showShorts = MutableStateFlow(true)
    val showShorts: StateFlow<Boolean> = _showShorts

    fun toggleShorts(show: Boolean) {
        _showShorts.value = show
    }

    fun onUrlSubmitted(inputUrl: String) {
        val url = inputUrl.trim()
        if (url.isBlank()) {
            _uiState.value = HomeUiState.Error("Please enter a URL")
            return
        }

        val sanitizedUrl = when {
            // Handle @channel inputs
            url.startsWith("@") -> "https://www.youtube.com/$url"
            
            // Handle youtu.be short links (strip ?si= but keep the ID)
            url.contains("youtu.be/") -> {
                val idPart = url.substringAfter("youtu.be/").substringBefore("?")
                "https://www.youtube.com/watch?v=$idPart"
            }
            
            // Handle standard youtube.com links (clean up si= parameter)
            url.contains("youtube.com/") -> {
                var cleanUrl = url
                if (!cleanUrl.startsWith("http")) cleanUrl = "https://$cleanUrl"
                // Remove si= parameter if it exists (using simple string replace for safety)
                cleanUrl = cleanUrl.replace(Regex("[?&]si=[^&]*"), "")
                // If we ended up with a dangling ? or &, clean it
                cleanUrl.replace(Regex("[?&]$"), "")
            }
            
            // Handle bare IDs (e.g. 5AJ1Lhi-J84?si=...)
            else -> {
                val cleanId = url.substringAfterLast("/").substringBefore("?")
                "https://www.youtube.com/watch?v=$cleanId"
            }
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            runCatching {
                when {
                    "youtube.com/playlist" in sanitizedUrl || "list=" in sanitizedUrl -> {
                        val listId = sanitizedUrl.substringAfter("list=").substringBefore("&")
                        val playlistUrl = "https://www.youtube.com/playlist?list=$listId"
                        
                        try {
                            val playlist = repository.extractPlaylist(playlistUrl)
                            if (playlist.videos.isNotEmpty()) {
                                HomeUiState.PlaylistResult(playlist)
                            } else {
                                val video = repository.searchAndSave(sanitizedUrl)
                                HomeUiState.Success(video)
                            }
                        } catch (e: Exception) {
                            // If playlist extraction fails (e.g. YouTube Mixes or v1/next errors), fallback to single video
                            // NewPipe rejects URLs with &list= for stream extraction, so we must clean it:
                            if ("v=" in sanitizedUrl) {
                                val videoId = sanitizedUrl.substringAfter("v=").substringBefore("&")
                                val cleanVideoUrl = "https://www.youtube.com/watch?v=$videoId"
                                val video = repository.searchAndSave(cleanVideoUrl)
                                HomeUiState.Success(video)
                            } else {
                                HomeUiState.Error("Playlist failed: ${e.message}")
                            }
                        }
                    }
                    "/channel/" in sanitizedUrl || "/c/" in sanitizedUrl || "/@" in sanitizedUrl || sanitizedUrl.contains("youtube.com/@") -> {
                        val channel = repository.extractChannel(sanitizedUrl)
                        HomeUiState.ChannelResult(channel)
                    }
                    else -> {
                        val video = repository.searchAndSave(sanitizedUrl)
                        HomeUiState.Success(video)
                    }
                }
            }.onSuccess { state ->
                _uiState.value = state
                saveLinkToHistory(url, state)
            }.onFailure { e ->
                _uiState.value = HomeUiState.Error("${e.message ?: "Failed to extract"} \nURL: $sanitizedUrl")
            }
        }
    }

    fun cacheResult(video: ExtractedVideoInfo) {
        lastResultCache.put(video.videoId, video)
    }

    fun getCachedResult(videoId: String): ExtractedVideoInfo? = lastResultCache.get(videoId)

    fun toggleFavorite(video: ExtractedVideoInfo, favorite: Boolean) {
        viewModelScope.launch {
            val existing = repository.getVideoById(video.videoId)
            if (existing == null) {
                repository.insertVideo(video.toVideoEntity().copy(favorite = favorite))
            } else {
                repository.setFavorite(video.videoId, favorite)
            }
        }
    }

    val favoriteVideoIds: StateFlow<Set<String>> = repository.getAllVideos()
        .map { list -> list.filter { it.favorite }.map { it.id }.toSet() }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptySet())

    val folders: StateFlow<List<FolderEntity>> = folderDao.getAllFolders()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    val videoFolderMap: StateFlow<Map<String, List<FolderEntity>>> = combine(
        folderDao.getAllFolders(),
        videoFolderDao.getAllJoinsFlow()
    ) { folders, joins ->
        val folderMap = folders.associateBy { it.id }
        joins.groupBy({ it.videoId }, { folderMap[it.folderId] })
            .mapValues { (_, list) -> list.filterNotNull() }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyMap())

    fun moveVideoToFolder(video: ExtractedVideoInfo, folderId: Long) {
        viewModelScope.launch {
            val existing = repository.getVideoById(video.videoId)
            if (existing == null) {
                repository.insertVideo(video.toVideoEntity())
            }
            videoFolderDao.addVideoToFolder(VideoFolderJoin(video.videoId, folderId))
        }
    }

    private val _fetchingStreamsFor = MutableStateFlow<String?>(null)
    val fetchingStreamsFor: StateFlow<String?> = _fetchingStreamsFor

    private val _fetchedStreams = MutableStateFlow<ExtractedVideoInfo?>(null)
    val fetchedStreams: StateFlow<ExtractedVideoInfo?> = _fetchedStreams

    val allDownloads: StateFlow<List<DownloadEntity>> = downloadRepository.getAllDownloads()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun fetchStreamsFor(videoId: String) {
        _fetchingStreamsFor.value = videoId
        _fetchedStreams.value = null
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cached = lastResultCache.get(videoId)
                if (cached != null && cached.streamSources.isNotEmpty()) {
                    _fetchedStreams.value = cached
                } else {
                    val info = repository.searchAndSave("https://www.youtube.com/watch?v=$videoId")
                    lastResultCache.put(videoId, info)
                    if (videoId == _fetchingStreamsFor.value) {
                        _fetchedStreams.value = info
                    }
                }
            } catch (e: Exception) {
                // handle error
            } finally {
                if (videoId == _fetchingStreamsFor.value) {
                    _fetchingStreamsFor.value = null
                }
            }
        }
    }

    fun enqueueDownload(videoInfo: ExtractedVideoInfo, stream: StreamSource) {
        viewModelScope.launch {
            val dbQuality = if (stream.format == "audio") stream.quality else "${stream.quality} (${stream.codec})"
            val existing = downloadRepository.getDownload(videoInfo.videoId, stream.format, dbQuality)
            if (existing != null) {
                android.widget.Toast.makeText(context, "Download already exists in queue", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            val video = repository.getVideoById(videoInfo.videoId)
            if (video == null) {
                repository.insertVideo(videoInfo.toVideoEntity())
            }

            val entity = DownloadEntity(
                videoId = videoInfo.videoId,
                url = stream.url,
                videoUrl = "https://www.youtube.com/watch?v=${videoInfo.videoId}",
                format = stream.format,
                quality = dbQuality,
                status = DownloadStatus.QUEUED,
                title = videoInfo.title,
                thumbnailUrl = videoInfo.thumbnailUrl
            )
            downloadRepository.insert(entity)
            try {
                context.startForegroundService(android.content.Intent(context, com.example.medianest.service.DownloadService::class.java))
                android.widget.Toast.makeText(context, "Download started: ${videoInfo.title}", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to start downloader service: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                com.example.medianest.service.DownloadService.cancel(context, download.id)
            } else {
                if (download.filePath.isNotEmpty()) {
                    try {
                        val file = java.io.File(download.filePath)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {}
                }
                downloadRepository.delete(download)
                
                val remaining = downloadRepository.getLocalDownloadsForVideo(download.videoId)
                if (remaining.isEmpty()) {
                    val video = repository.getVideoById(download.videoId)
                    if (video != null) {
                        repository.updateVideo(video.copy(localFilePath = ""))
                    }
                }
            }
        }
    }

    fun extractAudio(download: DownloadEntity) {
        if (download.filePath.isEmpty() || download.status != DownloadStatus.COMPLETED) return
        android.widget.Toast.makeText(context, "Audio extraction started", android.widget.Toast.LENGTH_SHORT).show()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            val existing = downloadRepository.getAudioExtraction(download.videoId)
            if (existing != null) return@launch

            val extractionEntity = DownloadEntity(
                videoId = download.videoId,
                url = "",
                format = "audio_extracted",
                quality = "${download.quality}_audio",
                title = download.title,
                thumbnailUrl = download.thumbnailUrl,
                status = DownloadStatus.DOWNLOADING,
                progress = 0f
            )
            val insertId = downloadRepository.insert(extractionEntity)

            try {
                val result = audioExtractor.extractAudio(download.filePath, download.videoId, download.quality)
                if (result.success) {
                    downloadRepository.markCompleted(insertId, java.io.File(result.outputPath).length(), result.outputPath)
                } else {
                    downloadRepository.markFailed(insertId, result.errorMessage ?: "Extraction failed", 0)
                }
            } catch (e: Throwable) {
                downloadRepository.markFailed(insertId, e.message ?: "Extraction failed", 0)
            }
        }
    }

    val subscriptions: StateFlow<List<com.example.medianest.data.local.entity.SubscriptionEntity>> = subscriptionRepository.getAllSubscriptions()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    val linkHistory: StateFlow<List<LinkHistoryEntity>> = linkHistoryDao.getAllLinkHistory()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackHistory: StateFlow<List<HistoryEntity>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private fun saveLinkToHistory(url: String, state: HomeUiState) {
        val title = when (state) {
            is HomeUiState.Success -> state.video.title
            is HomeUiState.PlaylistResult -> state.playlist.name
            is HomeUiState.ChannelResult -> state.channel.name
            else -> return
        }
        viewModelScope.launch {
            linkHistoryDao.insertWithLimit(LinkHistoryEntity(url = url, title = title))
        }
    }

    fun deleteHistoryItem(url: String) {
        viewModelScope.launch {
            linkHistoryDao.deleteByUrl(url)
        }
    }

    fun subscribe(sourceType: String, sourceId: String, name: String, thumbnailUrl: String?) {
        viewModelScope.launch {
            subscriptionRepository.subscribe(sourceType, sourceId, name, thumbnailUrl)
        }
    }

    fun unsubscribe(sourceId: String) {
        viewModelScope.launch {
            subscriptionRepository.unsubscribeBySourceId(sourceId)
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Idle
    }
}
