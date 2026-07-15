package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.dao.BulkDownloadDao
import com.example.medianest.data.model.ChannelInfo
import com.example.medianest.data.model.ExtractedPlaylistInfo
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.repository.SubscriptionRepository
import com.example.medianest.data.repository.VideoRepository
import com.example.medianest.extraction.YouTubeExtractor
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
import com.example.medianest.data.local.entity.BulkDownloadItemEntity
import com.example.medianest.data.local.entity.BulkDownloadItemStatus
import com.example.medianest.data.local.entity.BulkDownloadJobEntity
import com.example.medianest.data.local.entity.BulkDownloadJobStatus
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.service.AudioExtractor
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class HomeUiState {
    data object Idle : HomeUiState()
    data object Loading : HomeUiState()
    data class Success(val video: ExtractedVideoInfo) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
    data class PlaylistResult(
        val playlist: ExtractedPlaylistInfo,
        val currentPage: YouTubeExtractor.PlaylistPage?,
        val isFetchingNextPage: Boolean = false,
        val hasMore: Boolean = true
    ) : HomeUiState()
    data class ChannelResult(
        val channel: ChannelInfo,
        val currentPage: YouTubeExtractor.ChannelPage?,
        val isFetchingNextPage: Boolean = false,
        val hasMore: Boolean = true
    ) : HomeUiState()
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
    private val bulkDownloadDao: BulkDownloadDao,
    private val audioExtractor: AudioExtractor,
    private val historyDao: HistoryDao,
    private val downloadPreferences: DownloadPreferences,
    private val youTubeExtractor: YouTubeExtractor
) : ViewModel() {

    companion object {
        val lastResultCache = android.util.LruCache<String, ExtractedVideoInfo>(100)
        val lastPlaylistCache = android.util.LruCache<String, ExtractedPlaylistInfo>(50)
        val lastChannelCache = android.util.LruCache<String, ChannelInfo>(50)
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _showShorts = MutableStateFlow(true)
    val showShorts: StateFlow<Boolean> = _showShorts

    val watchCounts: StateFlow<Map<String, Int>> = videoDao.getAllVideos()
        .map { list -> list.associate { it.id to it.watchCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun toggleShorts(show: Boolean) {
        _showShorts.value = show
    }

    fun onUrlSubmitted(inputUrl: String) {
        _linkHistoryLimit.value = 10
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

        val isPlaylist = "youtube.com/playlist" in sanitizedUrl || "list=" in sanitizedUrl
        val isChannel = "/channel/" in sanitizedUrl || "/c/" in sanitizedUrl || "/@" in sanitizedUrl || sanitizedUrl.contains("youtube.com/@")
        val videoId = if (!isPlaylist && !isChannel) {
            when {
                "v=" in sanitizedUrl -> sanitizedUrl.substringAfter("v=").substringBefore("&")
                "youtu.be/" in sanitizedUrl -> sanitizedUrl.substringAfter("youtu.be/").substringBefore("?")
                else -> sanitizedUrl.substringAfterLast("/").substringBefore("?")
            }
        } else null

        viewModelScope.launch {
            val localVideo = videoId?.let { videoDao.getVideoById(it) }
            if (localVideo != null) {
                val allDownloads = downloadRepository.getDownloadsForVideoFlow(localVideo.id).first()
                val mockSources = allDownloads.map { download ->
                    val quality = if (download.format == "audio") {
                        download.quality
                    } else {
                        download.quality.substringBefore(" (")
                    }
                    val codec = if (download.format == "audio") {
                        ""
                    } else {
                        download.quality.substringAfter(" (", "").substringBefore(")")
                    }
                    StreamSource(
                        url = download.url,
                        format = download.format,
                        quality = quality,
                        mimeType = "",
                        codec = codec,
                        contentLength = download.fileSizeBytes
                    )
                }
                val fallbackInfo = ExtractedVideoInfo(
                    videoId = localVideo.id,
                    title = localVideo.title,
                    channelName = localVideo.channelName,
                    channelId = localVideo.channelId,
                    durationSeconds = localVideo.durationSeconds,
                    thumbnailUrl = localVideo.thumbnailUrl,
                    description = localVideo.description,
                    uploadDate = localVideo.uploadDate,
                    streamSources = mockSources,
                    isOfflineFallback = true
                )
                lastResultCache.put(localVideo.id, fallbackInfo)
                _uiState.value = HomeUiState.Success(fallbackInfo)
                saveLinkToHistory(url, HomeUiState.Success(fallbackInfo))

                launch {
                    try {
                        val video = repository.searchAndSave(sanitizedUrl)
                        lastResultCache.put(video.videoId, video)
                        _uiState.value = HomeUiState.Success(video)
                    } catch (e: Exception) {
                        android.util.Log.w("HomeViewModel", "Background search and save failed offline", e)
                    }
                }
                return@launch
            }

            if (isPlaylist) {
                val listId = sanitizedUrl.substringAfter("list=").substringBefore("&")
                val playlistUrl = "https://www.youtube.com/playlist?list=$listId"
                val cachedPlaylist = lastPlaylistCache.get(listId)
                if (cachedPlaylist != null) {
                    val cachedState = HomeUiState.PlaylistResult(
                        playlist = cachedPlaylist,
                        currentPage = null,
                        isFetchingNextPage = false,
                        hasMore = false
                    )
                    _uiState.value = cachedState
                    saveLinkToHistory(url, cachedState)
                    launch {
                        try {
                            val (playlist, page) = repository.extractPlaylistFirstPage(playlistUrl)
                            if (playlist.videos.isNotEmpty()) {
                                lastPlaylistCache.put(listId, playlist)
                                _uiState.value = HomeUiState.PlaylistResult(
                                    playlist = playlist,
                                    currentPage = page,
                                    isFetchingNextPage = false,
                                    hasMore = page.nextPage != null
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("HomeViewModel", "Background playlist update failed offline", e)
                        }
                    }
                    return@launch
                }
            }

            if (isChannel) {
                val cleanChannelUrl = sanitizedUrl.removeSuffix("/videos").removeSuffix("/streams").removeSuffix("/shorts").removeSuffix("/playlists").removeSuffix("/")
                val cachedChannel = lastChannelCache.get(cleanChannelUrl)
                if (cachedChannel != null) {
                    val cachedState = HomeUiState.ChannelResult(
                        channel = cachedChannel,
                        currentPage = null,
                        isFetchingNextPage = false,
                        hasMore = false
                    )
                    _uiState.value = cachedState
                    saveLinkToHistory(url, cachedState)
                    launch {
                        try {
                            val (channel, page) = repository.extractChannelFirstPage(sanitizedUrl)
                            lastChannelCache.put(cleanChannelUrl, channel)
                            _uiState.value = HomeUiState.ChannelResult(
                                channel = channel,
                                currentPage = page,
                                isFetchingNextPage = false,
                                hasMore = page.nextPage != null
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("HomeViewModel", "Background channel update failed offline", e)
                        }
                    }
                    return@launch
                }
            }

            _uiState.value = HomeUiState.Loading
            runCatching {
                when {
                    "youtube.com/playlist" in sanitizedUrl || "list=" in sanitizedUrl -> {
                        val listId = sanitizedUrl.substringAfter("list=").substringBefore("&")
                        val playlistUrl = "https://www.youtube.com/playlist?list=$listId"
                        
                        try {
                            val (playlist, page) = repository.extractPlaylistFirstPage(playlistUrl)
                            if (playlist.videos.isNotEmpty()) {
                                lastPlaylistCache.put(listId, playlist)
                                HomeUiState.PlaylistResult(
                                    playlist = playlist,
                                    currentPage = page,
                                    isFetchingNextPage = false,
                                    hasMore = page.nextPage != null
                                )
                            } else {
                                val video = repository.searchAndSave(sanitizedUrl)
                                HomeUiState.Success(video)
                            }
                        } catch (e: Exception) {
                            if ("v=" in sanitizedUrl) {
                                val vId = sanitizedUrl.substringAfter("v=").substringBefore("&")
                                val cleanVideoUrl = "https://www.youtube.com/watch?v=$vId"
                                val video = repository.searchAndSave(cleanVideoUrl)
                                HomeUiState.Success(video)
                            } else {
                                HomeUiState.Error("Playlist failed: ${e.message}")
                            }
                        }
                    }
                    "/channel/" in sanitizedUrl || "/c/" in sanitizedUrl || "/@" in sanitizedUrl || sanitizedUrl.contains("youtube.com/@") -> {
                        val cleanChannelUrl = sanitizedUrl.removeSuffix("/videos").removeSuffix("/streams").removeSuffix("/shorts").removeSuffix("/playlists").removeSuffix("/")
                        try {
                            val (channel, page) = repository.extractChannelFirstPage(sanitizedUrl)
                            lastChannelCache.put(cleanChannelUrl, channel)
                            HomeUiState.ChannelResult(
                                channel = channel,
                                currentPage = page,
                                isFetchingNextPage = false,
                                  hasMore = page.nextPage != null
                            )
                        } catch (e: Exception) {
                            val channelId = youTubeExtractor.extractChannelIdFromUrl(cleanChannelUrl) ?: cleanChannelUrl.substringAfterLast("/")
                            val localVideos = repository.getVideosByChannel(channelId)
                            val localSub = subscriptionRepository.getAllSubscriptionsOnce().firstOrNull { sub ->
                                sub.sourceType == "channel" && sub.sourceId == channelId
                            }
                            if (localVideos.isNotEmpty() || localSub != null) {
                                val channelName = localSub?.name ?: localVideos.firstOrNull()?.channelName ?: "Channel"
                                val thumbnailUrl = localSub?.thumbnailUrl ?: localVideos.firstOrNull()?.thumbnailUrl
                                val mockChannel = ChannelInfo(
                                    channelId = channelId,
                                    url = sanitizedUrl,
                                    name = channelName,
                                    avatarUrl = thumbnailUrl,
                                    subscriberCount = -1L,
                                    description = "Offline Fallback",
                                    videoCount = localVideos.size,
                                    uploads = localVideos.map { local ->
                                        ExtractedVideoInfo(
                                            videoId = local.id,
                                            title = local.title,
                                            channelName = local.channelName,
                                            channelId = local.channelId,
                                            durationSeconds = local.durationSeconds,
                                            thumbnailUrl = local.thumbnailUrl,
                                            description = local.description,
                                            uploadDate = local.uploadDate,
                                            streamSources = emptyList()
                                        )
                                    }
                                )
                                lastChannelCache.put(cleanChannelUrl, mockChannel)
                                HomeUiState.ChannelResult(
                                    channel = mockChannel,
                                    currentPage = null,
                                    isFetchingNextPage = false,
                                    hasMore = false
                                )
                            } else {
                                throw e
                            }
                        }
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

    private val _linkHistoryLimit = MutableStateFlow(10)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val linkHistory: StateFlow<List<LinkHistoryEntity>> = _linkHistoryLimit.flatMapLatest { limit ->
        linkHistoryDao.getLinkHistoryPaged(limit)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadMoreLinkHistory() {
        _linkHistoryLimit.value += 10
    }

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

    data class BulkFetchProgress(
        val jobId: Long,
        val current: Int,
        val total: Int,
        val currentTitle: String
    )

    data class BulkDownloadConfirmation(
        val jobId: Long,
        val quality: String,
        val totalVideoCount: Int,
        val videoCount: Int,
        val unavailableVideoCount: Int,
        val failedVideoCount: Int,
        val totalSize: Long,
        val usableSpace: Long
    )

    private val _showBulkQualityDialog = MutableStateFlow(false)
    val showBulkQualityDialog: StateFlow<Boolean> = _showBulkQualityDialog

    private val _bulkFetchProgress = MutableStateFlow<BulkFetchProgress?>(null)
    val bulkFetchProgress: StateFlow<BulkFetchProgress?> = _bulkFetchProgress

    private val _bulkDownloadConfirmation = MutableStateFlow<BulkDownloadConfirmation?>(null)
    val bulkDownloadConfirmation: StateFlow<BulkDownloadConfirmation?> = _bulkDownloadConfirmation

    private val _suppressedBulkJobId = MutableStateFlow<Long?>(null)
    private var bulkFetchStartJob: Job? = null

    init {
        viewModelScope.launch {
            bulkDownloadDao.observeLatestActiveJob().collect { job ->
                when (job?.status) {
                    BulkDownloadJobStatus.PENDING,
                    BulkDownloadJobStatus.RUNNING -> {
                        _bulkFetchProgress.value = BulkFetchProgress(
                            jobId = job.id,
                            current = job.processedVideos,
                            total = job.totalVideos,
                            currentTitle = job.currentTitle.ifBlank { "Preparing downloads" }
                        )
                        _bulkDownloadConfirmation.value = null
                        _suppressedBulkJobId.value = null
                    }
                    BulkDownloadJobStatus.READY -> {
                        _bulkFetchProgress.value = null
                        if (_suppressedBulkJobId.value != job.id) {
                            _bulkDownloadConfirmation.value = BulkDownloadConfirmation(
                                jobId = job.id,
                                quality = job.quality,
                                totalVideoCount = job.totalVideos,
                                videoCount = job.downloadableVideos,
                                unavailableVideoCount = job.unavailableVideos,
                                failedVideoCount = job.failedVideos,
                                totalSize = job.totalSizeBytes,
                                usableSpace = job.usableSpaceBytes
                            )
                        }
                    }
                    BulkDownloadJobStatus.FAILED,
                    BulkDownloadJobStatus.CANCELLED -> {
                        _bulkFetchProgress.value = null
                        _bulkDownloadConfirmation.value = null
                        _suppressedBulkJobId.value = job.id
                        if (job.status == BulkDownloadJobStatus.FAILED) {
                            android.widget.Toast.makeText(
                                context,
                                job.errorMessage ?: "Bulk download preparation failed",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    BulkDownloadJobStatus.CONFIRMED,
                    null -> {
                        _bulkFetchProgress.value = null
                        _bulkDownloadConfirmation.value = null
                    }
                }
            }
        }
    }

    fun setBulkQualityDialogVisible(visible: Boolean) {
        _showBulkQualityDialog.value = visible
    }

    fun dismissBulkConfirmation() {
        _bulkDownloadConfirmation.value?.let { _suppressedBulkJobId.value = it.jobId }
        _bulkDownloadConfirmation.value = null
    }

    fun startBulkFetch(videos: List<ExtractedVideoInfo>, targetQuality: String) {
        val state = _uiState.value
        val sourceType = when (state) {
            is HomeUiState.PlaylistResult -> "playlist"
            is HomeUiState.ChannelResult -> "channel"
            else -> return
        }
        if (videos.isEmpty()) {
            android.widget.Toast.makeText(context, "No videos selected for bulk download", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val (sourceId, sourceUrl, sourceName) = when (state) {
            is HomeUiState.PlaylistResult -> Triple(
                state.playlist.playlistId,
                "https://www.youtube.com/playlist?list=${state.playlist.playlistId}",
                state.playlist.name
            )
            is HomeUiState.ChannelResult -> Triple(
                if (state.channel.channelId.isNotBlank()) state.channel.channelId else state.channel.url,
                state.channel.url,
                state.channel.name
            )
            else -> return
        }

        _showBulkQualityDialog.value = false
        _bulkDownloadConfirmation.value = null
        _suppressedBulkJobId.value = null
        _bulkFetchProgress.value = BulkFetchProgress(0, 0, videos.size, "Preparing downloads")

        bulkFetchStartJob?.cancel()
        bulkFetchStartJob = viewModelScope.launch(Dispatchers.IO) {
            val jobId = bulkDownloadDao.replaceActiveJobWithItems(
                BulkDownloadJobEntity(
                    sourceType = sourceType,
                    sourceId = sourceId,
                    sourceUrl = sourceUrl,
                    sourceName = sourceName,
                    quality = targetQuality,
                    totalVideos = videos.size
                ),
                videos.mapIndexed { index, video ->
                    BulkDownloadItemEntity(
                        jobId = 0,
                        videoId = video.videoId,
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        channelName = video.channelName,
                        channelId = video.channelId,
                        quality = targetQuality,
                        displayOrder = index
                    )
                }
            )

            _bulkFetchProgress.value = BulkFetchProgress(jobId, 0, videos.size, "Preparing downloads")
            WorkScheduler.enqueueBulkDownloadPreparation(context, jobId)
        }
    }

    fun cancelBulkFetch() {
        bulkFetchStartJob?.cancel()
        bulkFetchStartJob = null
        val jobId = (_bulkFetchProgress.value?.jobId ?: _bulkDownloadConfirmation.value?.jobId)
            ?.takeIf { it > 0L }
        viewModelScope.launch(Dispatchers.IO) {
            if (jobId != null) {
                bulkDownloadDao.updateJobStatus(jobId, BulkDownloadJobStatus.CANCELLED)
            } else {
                bulkDownloadDao.cancelActiveJobs()
            }
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork(WorkScheduler.BULK_DOWNLOAD_PREP_WORK_NAME)
        }
        _bulkFetchProgress.value = null
        _bulkDownloadConfirmation.value = null
    }

    fun confirmBulkDownload(jobId: Long) {
        _bulkDownloadConfirmation.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val items = bulkDownloadDao.getItemsOnce(jobId)
            var enqueuedCount = 0

            for (item in items.filter { it.status == BulkDownloadItemStatus.READY }) {
                val dbQuality = if (item.format == "audio") item.quality else "${item.quality} (${item.codec})"
                val existing = downloadRepository.getDownload(item.videoId, item.format, dbQuality)
                if (existing != null) continue

                val entity = DownloadEntity(
                    videoId = item.videoId,
                    url = item.url,
                    videoUrl = "https://www.youtube.com/watch?v=${item.videoId}",
                    format = item.format,
                    quality = dbQuality,
                    status = DownloadStatus.QUEUED,
                    title = item.title,
                    thumbnailUrl = item.thumbnailUrl
                )
                downloadRepository.insert(entity)
                enqueuedCount++
            }

            bulkDownloadDao.markJobConfirmed(jobId)
            bulkDownloadDao.pruneFinishedJobs()

            if (enqueuedCount > 0) {
                try {
                    context.startForegroundService(android.content.Intent(context, com.example.medianest.service.DownloadService::class.java))
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Enqueued $enqueuedCount downloads", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to start downloader: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "All selected videos are already in the queue or downloaded", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState is HomeUiState.ChannelResult) {
            if (currentState.isFetchingNextPage || !currentState.hasMore || currentState.currentPage?.nextPage == null) return
            
            _uiState.value = currentState.copy(isFetchingNextPage = true)
            viewModelScope.launch {
                runCatching {
                    repository.extractChannelNextPage(currentState.currentPage)
                }.onSuccess { nextPageData ->
                    val updatedState = _uiState.value as? HomeUiState.ChannelResult
                    if (updatedState != null) {
                        val newVideos = nextPageData.videos
                        val existingIds = updatedState.channel.uploads.map { it.videoId }.toSet()
                        val uniqueNewVideos = newVideos.filter { it.videoId !in existingIds }
                        
                        val updatedUploads = updatedState.channel.uploads + uniqueNewVideos
                        val updatedChannel = updatedState.channel.copy(
                            uploads = updatedUploads,
                            videoCount = updatedUploads.size
                        )
                        
                        _uiState.value = updatedState.copy(
                            channel = updatedChannel,
                            currentPage = nextPageData,
                            isFetchingNextPage = false,
                            hasMore = nextPageData.nextPage != null && nextPageData.videos.isNotEmpty()
                        )
                    }
                }.onFailure {
                    val updatedState = _uiState.value as? HomeUiState.ChannelResult
                    if (updatedState != null) {
                        _uiState.value = updatedState.copy(isFetchingNextPage = false)
                    }
                }
            }
        } else if (currentState is HomeUiState.PlaylistResult) {
            if (currentState.isFetchingNextPage || !currentState.hasMore || currentState.currentPage?.nextPage == null) return
            
            _uiState.value = currentState.copy(isFetchingNextPage = true)
            viewModelScope.launch {
                runCatching {
                    repository.extractPlaylistNextPage(currentState.currentPage)
                }.onSuccess { nextPageData ->
                    val updatedState = _uiState.value as? HomeUiState.PlaylistResult
                    if (updatedState != null) {
                        val newVideos = nextPageData.videos
                        val existingIds = updatedState.playlist.videos.map { it.videoId }.toSet()
                        val uniqueNewVideos = newVideos.filter { it.videoId !in existingIds }
                        
                        val updatedVideos = updatedState.playlist.videos + uniqueNewVideos
                        val updatedPlaylist = updatedState.playlist.copy(
                            videos = updatedVideos,
                            videoCount = if (updatedState.playlist.videoCount > updatedVideos.size) updatedState.playlist.videoCount else updatedVideos.size
                        )
                        
                        _uiState.value = updatedState.copy(
                            playlist = updatedPlaylist,
                            currentPage = nextPageData,
                            isFetchingNextPage = false,
                            hasMore = nextPageData.nextPage != null && nextPageData.videos.isNotEmpty()
                        )
                    }
                }.onFailure {
                    val updatedState = _uiState.value as? HomeUiState.PlaylistResult
                    if (updatedState != null) {
                        _uiState.value = updatedState.copy(isFetchingNextPage = false)
                    }
                }
            }
        }
    }

    fun updateWatchCount(videoId: String, newCount: Int) {
        viewModelScope.launch {
            var video = videoDao.getVideoById(videoId)
            if (video == null) {
                val cached = lastResultCache.get(videoId)
                if (cached != null) {
                    val entity = cached.toVideoEntity()
                    videoDao.insert(entity)
                    video = entity
                } else {
                    try {
                        val url = "https://www.youtube.com/watch?v=$videoId"
                        repository.searchAndSave(url)
                        video = videoDao.getVideoById(videoId)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
            if (video == null && videoDao.getVideoById(videoId) == null) {
                val fallback = com.example.medianest.data.local.entity.VideoEntity(
                    id = videoId,
                    title = "Video ($videoId)",
                    channelName = "Unknown Channel",
                    channelId = "",
                    durationSeconds = 0L,
                    thumbnailUrl = "",
                    description = "",
                    uploadDate = ""
                )
                videoDao.insert(fallback)
            }
            val existing = videoDao.getVideoById(videoId)
            val oldCount = existing?.watchCount ?: 0
            videoDao.setWatchCount(videoId, newCount)
            if (newCount > oldCount) {
                historyDao.insertWatchSession(
                    com.example.medianest.data.local.entity.WatchSessionEntity(
                        videoId = videoId,
                        watchedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
