package com.example.medianest.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.SubscriptionDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.local.entity.VideoFolderJoin
import com.example.medianest.data.mapper.toVideoEntity
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.preferences.CollectionsPreferences
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.data.repository.VideoRepository
import com.example.medianest.service.AudioExtractor
import com.example.medianest.ui.utils.UiUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class LibraryTab(val label: String) {
    HISTORY("History"),
    WATCHED("Watched"),
    FOLDERS("Folders"),
    FAVORITES("Favorites"),
    PLAYLISTS("Playlists"),
    SUBSCRIPTIONS("Channels")
}

enum class SortCategory(val label: String) {
    DATE_PUBLISHED("Published Date"),
    LAST_WATCHED("Last Watched"),
    DATE_ADDED("Date Added"),
    NAME("Name"),
    DURATION("Duration"),
    SIZE("Size")
}

enum class SortDirection {
    ASC, DESC
}

enum class MediaTypeFilter(val label: String) {
    ALL("All"),
    VIDEO("Videos"),
    AUDIO("Audio")
}

enum class ViewMode { GRID, LIST }

data class LibraryUiState(
    val searchQuery: String = "",
    val currentTab: LibraryTab = LibraryTab.HISTORY,
    val mediaTypeFilter: MediaTypeFilter = MediaTypeFilter.ALL,
    val sortCategory: SortCategory = SortCategory.DATE_PUBLISHED,
    val sortDirection: SortDirection = SortDirection.DESC,
    val selectedFolder: FolderEntity? = null,
    val folderStack: List<FolderEntity> = emptyList(),
    val viewMode: ViewMode = ViewMode.GRID,
    val isSelectionMode: Boolean = false,
    val selectedVideoIds: Set<String> = emptySet()
)

data class FolderStats(
    val itemCount: Int,
    val totalSizeBytes: Long
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao,
    private val folderDao: FolderDao,
    private val videoFolderDao: VideoFolderDao,
    private val historyDao: HistoryDao,
    private val subscriptionDao: SubscriptionDao,
    private val downloadRepository: DownloadRepository,
    private val videoRepository: VideoRepository,
    private val audioExtractor: AudioExtractor,
    private val downloadPreferences: DownloadPreferences,
    private val collectionsPreferences: CollectionsPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")

    private val _historyLimit = MutableStateFlow(10)
    val historyLimit: StateFlow<Int> = _historyLimit
    private val _favoritesLimit = MutableStateFlow(10)
    val favoritesLimit: StateFlow<Int> = _favoritesLimit
    private val _folderVideosLimit = MutableStateFlow(10)
    val folderVideosLimit: StateFlow<Int> = _folderVideosLimit
    private val _watchedLimit = MutableStateFlow(10)
    val watchedLimit: StateFlow<Int> = _watchedLimit

    fun loadMoreHistory() {
        _historyLimit.value += 10
    }

    fun loadMoreFavorites() {
        _favoritesLimit.value += 10
    }

    fun loadMoreFolderVideos() {
        _folderVideosLimit.value += 10
    }

    fun loadMoreWatched() {
        _watchedLimit.value += 10
    }

    private fun sortAndFilterVideos(
        list: List<VideoEntity>,
        query: String,
        mediaType: MediaTypeFilter,
        sortCat: SortCategory,
        sortDir: SortDirection
    ): List<VideoEntity> {
        var filtered = list
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            filtered = filtered.filter {
                it.title.lowercase().contains(q) ||
                it.channelName.lowercase().contains(q)
            }
        }
        if (mediaType == MediaTypeFilter.VIDEO) {
            filtered = filtered.filter { !it.mediaType.equals("AUDIO", ignoreCase = true) }
        } else if (mediaType == MediaTypeFilter.AUDIO) {
            filtered = filtered.filter { it.mediaType.equals("AUDIO", ignoreCase = true) }
        }

        val comparator: Comparator<VideoEntity> = when (sortCat) {
            SortCategory.DATE_PUBLISHED -> compareBy { UiUtils.parseUploadDate(it.uploadDate)?.time ?: 0L }
            SortCategory.LAST_WATCHED -> compareBy { it.lastPlayedAt ?: 0L }
            SortCategory.DATE_ADDED -> compareBy { it.addedAt }
            SortCategory.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortCategory.DURATION -> compareBy { it.durationSeconds }
            SortCategory.SIZE -> compareBy { video ->
                if (video.localFilePath.isNotEmpty()) {
                    try {
                        val f = File(video.localFilePath)
                        if (f.exists()) f.length() else (video.durationSeconds * 2500000L) / 8L
                    } catch (e: Exception) {
                        (video.durationSeconds * 2500000L) / 8L
                    }
                } else {
                    (video.durationSeconds * 2500000L) / 8L
                }
            }
        }

        return if (sortDir == SortDirection.ASC) {
            filtered.sortedWith(comparator)
        } else {
            filtered.sortedWith(comparator.reversed())
        }
    }

    val playbackHistory: StateFlow<List<HistoryEntity>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val videos: StateFlow<List<VideoEntity>?> = combine(
        videoDao.getWatchHistoryVideos(),
        _searchQuery,
        _uiState.map { Triple(it.mediaTypeFilter, it.sortCategory, it.sortDirection) }.distinctUntilChanged(),
        _historyLimit
    ) { rawVideos, query, (mediaType, sortCat, sortDir), limit ->
        val sorted = sortAndFilterVideos(rawVideos, query, mediaType, sortCat, sortDir)
        sorted.take(limit)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteVideos: StateFlow<List<VideoEntity>?> = combine(
        videoDao.getFavoriteVideos(),
        _searchQuery,
        _uiState.map { Triple(it.mediaTypeFilter, it.sortCategory, it.sortDirection) }.distinctUntilChanged(),
        _favoritesLimit
    ) { rawVideos, query, (mediaType, sortCat, sortDir), limit ->
        val sorted = sortAndFilterVideos(rawVideos, query, mediaType, sortCat, sortDir)
        sorted.take(limit)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchedVideos: StateFlow<List<VideoEntity>?> = combine(
        videoDao.getWatchedVideos(),
        _searchQuery,
        _uiState.map { Triple(it.mediaTypeFilter, it.sortCategory, it.sortDirection) }.distinctUntilChanged(),
        _watchedLimit
    ) { rawVideos, query, (mediaType, sortCat, sortDir), limit ->
        val sorted = sortAndFilterVideos(rawVideos, query, mediaType, sortCat, sortDir)
        sorted.take(limit)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val folderVideos: StateFlow<List<VideoEntity>?> = combine(
        _uiState.map { it.selectedFolder }.distinctUntilChanged(),
        _searchQuery,
        _uiState.map { Triple(it.mediaTypeFilter, it.sortCategory, it.sortDirection) }.distinctUntilChanged(),
        _folderVideosLimit
    ) { selectedFolder, query, (mediaType, sortCat, sortDir), limit ->
        selectedFolder to Triple(query, Triple(mediaType, sortCat, sortDir), limit)
    }.flatMapLatest { (selectedFolder, params) ->
        val (query, filters, limit) = params
        val (mediaType, sortCat, sortDir) = filters
        if (selectedFolder != null) {
            videoFolderDao.getVideosInFolder(selectedFolder.id).map { raw ->
                val sorted = sortAndFilterVideos(raw, query, mediaType, sortCat, sortDir)
                sorted.take(limit)
            }
        } else {
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                videoFolderDao.searchVideosInAnyFolder(query).map { raw ->
                    val sorted = sortAndFilterVideos(raw, query, mediaType, sortCat, sortDir)
                    sorted.take(limit)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rootFolders: StateFlow<List<FolderEntity>?> = combine(
        folderDao.getRootFolders(),
        _searchQuery,
        _uiState.map { it.sortCategory to it.sortDirection }.distinctUntilChanged()
    ) { folders, query, (sortCat, sortDir) ->
        var list = folders
        if (query.isNotBlank()) {
            list = list.filter { it.name.contains(query, ignoreCase = true) }
        }
        val comparator: Comparator<FolderEntity> = when (sortCat) {
            SortCategory.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortCategory.DATE_ADDED, SortCategory.DATE_PUBLISHED, SortCategory.LAST_WATCHED -> compareBy { it.createdAt }
            else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        if (sortDir == SortDirection.ASC) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val childFolders: StateFlow<List<FolderEntity>> = combine(
        _uiState.map { it.selectedFolder }.distinctUntilChanged(),
        _searchQuery,
        _uiState.map { it.sortCategory to it.sortDirection }.distinctUntilChanged()
    ) { selectedFolder, query, (sortCat, sortDir) ->
        selectedFolder to Pair(query, sortCat to sortDir)
    }.flatMapLatest { (selectedFolder, params) ->
        val (query, sortParams) = params
        val (sortCat, sortDir) = sortParams
        if (selectedFolder != null) {
            folderDao.getChildFolders(selectedFolder.id).map { raw ->
                var list = raw
                if (query.isNotBlank()) {
                    list = list.filter { it.name.contains(query, ignoreCase = true) }
                }
                val comparator: Comparator<FolderEntity> = when (sortCat) {
                    SortCategory.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    SortCategory.DATE_ADDED, SortCategory.DATE_PUBLISHED, SortCategory.LAST_WATCHED -> compareBy { it.createdAt }
                    else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                }
                if (sortDir == SortDirection.ASC) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoFolderMap: StateFlow<Map<String, List<FolderEntity>>> = combine(
        folderDao.getAllFolders(),
        videoFolderDao.getAllJoinsFlow()
    ) { folders, joins ->
        val folderMap = folders.associateBy { it.id }
        joins.groupBy({ it.videoId }, { folderMap[it.folderId] })
            .mapValues { (_, list) -> list.filterNotNull() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val folderStatsMap: StateFlow<Map<Long, FolderStats>> = combine(
        folderDao.getAllFolders(),
        videoFolderDao.getAllJoinsFlow(),
        videoDao.getAllVideos()
    ) { folders, joins, videos ->
        val videoMap = videos.associateBy { it.id }
        val joinsByFolder = joins.groupBy { it.folderId }
        val childrenMap = folders.groupBy { it.parentId }

        val stats = mutableMapOf<Long, FolderStats>()

        fun calculateStats(folderId: Long): FolderStats {
            stats[folderId]?.let { return it }

            var count = 0
            var size = 0L

            val folderJoins = joinsByFolder[folderId] ?: emptyList()
            for (join in folderJoins) {
                val video = videoMap[join.videoId]
                if (video != null) {
                    count++
                    if (video.localFilePath.isNotEmpty()) {
                        try {
                            val file = File(video.localFilePath)
                            if (file.exists()) {
                                size += file.length()
                            }
                        } catch (e: Exception) {}
                    }
                }
            }

            val subfolders = childrenMap[folderId] ?: emptyList()
            for (sub in subfolders) {
                val subStats = calculateStats(sub.id)
                count += subStats.itemCount
                size += subStats.totalSizeBytes
            }

            val result = FolderStats(count, size)
            stats[folderId] = result
            return result
        }

        folders.forEach { folder ->
            calculateStats(folder.id)
        }

        stats
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allDownloads: StateFlow<List<DownloadEntity>> = downloadRepository.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Statistics summary metadata flows
    val historyStats: StateFlow<Pair<Int, Long>> = historyDao.getAllHistory()
        .map { list ->
            val count = list.size
            val totalTime = list.sumOf { it.totalWatchTimeMillis }
            count to totalTime
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0L)

    val watchedCount: StateFlow<Int> = videoDao.getWatchedVideos()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoritesCount: StateFlow<Int> = videoDao.getFavoriteVideos()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val playlistsCount: StateFlow<Int> = subscriptionDao.getByType("playlist")
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val channelsCount: StateFlow<Int> = subscriptionDao.getByType("channel")
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _fetchingStreamsFor = MutableStateFlow<String?>(null)
    val fetchingStreamsFor: StateFlow<String?> = _fetchingStreamsFor

    private val _fetchedStreams = MutableStateFlow<ExtractedVideoInfo?>(null)
    val fetchedStreams: StateFlow<ExtractedVideoInfo?> = _fetchedStreams

    init {
        viewModelScope.launch {
            collectionsPreferences.viewMode.collect { modeStr ->
                try {
                    _uiState.value = _uiState.value.copy(viewMode = ViewMode.valueOf(modeStr))
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(viewMode = ViewMode.GRID)
                }
            }
        }
    }

    fun fetchStreamsFor(videoId: String) {
        if (_fetchedStreams.value?.videoId == videoId) return
        viewModelScope.launch {
            _fetchingStreamsFor.value = videoId
            try {
                val cached = com.example.medianest.ui.viewmodel.HomeViewModel.lastResultCache.get(videoId)
                if (cached != null && cached.streamSources.isNotEmpty()) {
                    _fetchedStreams.value = cached
                } else {
                    val info = videoRepository.searchAndSave("https://www.youtube.com/watch?v=$videoId")
                    com.example.medianest.ui.viewmodel.HomeViewModel.lastResultCache.put(videoId, info)
                    _fetchedStreams.value = info
                }
            } catch (e: Exception) {
                // handle error or just hide spinner
            } finally {
                _fetchingStreamsFor.value = null
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

            val video = videoRepository.getVideoById(videoInfo.videoId)
            if (video == null) {
                videoRepository.insertVideo(videoInfo.toVideoEntity())
            }

            val downloadFolder = downloadPreferences.downloadFolder.first()
            val entity = DownloadEntity(
                videoId = videoInfo.videoId,
                url = stream.url,
                videoUrl = "https://www.youtube.com/watch?v=${videoInfo.videoId}",
                format = stream.format,
                quality = dbQuality,
                status = com.example.medianest.data.local.entity.DownloadStatus.QUEUED,
                title = videoInfo.title,
                thumbnailUrl = videoInfo.thumbnailUrl,
                downloadUuid = java.util.UUID.randomUUID().toString(),
                outputRoot = downloadFolder
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
        com.example.medianest.service.DownloadService.delete(context, download.id, deleteFiles = true)
    }

    fun extractAudio(download: DownloadEntity) {
        if (download.filePath.isEmpty() || download.status != com.example.medianest.data.local.entity.DownloadStatus.COMPLETED) return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = downloadRepository.getAudioExtraction(download.videoId)
            if (existing != null) return@launch

            val downloadFolder = downloadPreferences.downloadFolder.first()
            val extractionEntity = DownloadEntity(
                videoId = download.videoId,
                url = "",
                format = "audio_extracted",
                quality = "${download.quality}_audio",
                title = download.title,
                thumbnailUrl = download.thumbnailUrl,
                status = com.example.medianest.data.local.entity.DownloadStatus.QUEUED,
                progress = 0f,
                downloadUuid = java.util.UUID.randomUUID().toString(),
                outputRoot = downloadFolder
            )
            val insertId = downloadRepository.insert(extractionEntity)
            if (insertId <= 0L) return@launch
            com.example.medianest.service.DownloadService.extractAudio(context, insertId)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setMediaTypeFilter(filter: MediaTypeFilter) {
        _uiState.value = _uiState.value.copy(mediaTypeFilter = filter)
    }

    fun setSort(category: SortCategory, direction: SortDirection) {
        _uiState.value = _uiState.value.copy(sortCategory = category, sortDirection = direction)
    }

    fun toggleSort(category: SortCategory) {
        val currentCat = _uiState.value.sortCategory
        val currentDir = _uiState.value.sortDirection
        if (currentCat == category) {
            val newDir = if (currentDir == SortDirection.DESC) SortDirection.ASC else SortDirection.DESC
            _uiState.value = _uiState.value.copy(sortDirection = newDir)
        } else {
            val defaultDir = if (category == SortCategory.NAME) SortDirection.ASC else SortDirection.DESC
            _uiState.value = _uiState.value.copy(sortCategory = category, sortDirection = defaultDir)
        }
    }

    fun setTab(tab: LibraryTab) {
        _historyLimit.value = 10
        _favoritesLimit.value = 10
        _folderVideosLimit.value = 10
        _watchedLimit.value = 10
        val newSortCat = when (tab) {
            LibraryTab.HISTORY -> if (_uiState.value.sortCategory == SortCategory.SIZE) SortCategory.LAST_WATCHED else _uiState.value.sortCategory
            LibraryTab.PLAYLISTS, LibraryTab.SUBSCRIPTIONS -> if (_uiState.value.sortCategory == SortCategory.SIZE) SortCategory.DATE_ADDED else _uiState.value.sortCategory
            else -> _uiState.value.sortCategory
        }
        _uiState.value = _uiState.value.copy(
            currentTab = tab,
            sortCategory = newSortCat,
            selectedFolder = null,
            folderStack = emptyList(),
            isSelectionMode = false,
            selectedVideoIds = emptySet()
        )
    }

    fun updateWatchCount(videoId: String, newCount: Int) {
        viewModelScope.launch {
            var existing = videoDao.getVideoById(videoId)
            if (existing == null) {
                val fallback = VideoEntity(
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
                existing = fallback
            }
            val oldCount = existing.watchCount
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

    fun selectFolder(folder: FolderEntity) {
        _folderVideosLimit.value = 10
        val newStack = _uiState.value.folderStack + folder
        _uiState.value = _uiState.value.copy(
            currentTab = LibraryTab.FOLDERS,
            selectedFolder = folder,
            folderStack = newStack
        )
    }

    fun navigateBackFromFolder() {
        _folderVideosLimit.value = 10
        val currentStack = _uiState.value.folderStack
        if (currentStack.isNotEmpty()) {
            val newStack = currentStack.dropLast(1)
            val parent = newStack.lastOrNull()
            _uiState.value = _uiState.value.copy(selectedFolder = parent, folderStack = newStack)
        } else {
            _uiState.value = _uiState.value.copy(selectedFolder = null, folderStack = emptyList())
        }
    }

    fun navigateToFolderCrumb(index: Int) {
        _folderVideosLimit.value = 10
        val currentStack = _uiState.value.folderStack
        if (index < 0 || index >= currentStack.size) {
            _uiState.value = _uiState.value.copy(selectedFolder = null, folderStack = emptyList())
        } else {
            val newStack = currentStack.take(index + 1)
            _uiState.value = _uiState.value.copy(selectedFolder = newStack.last(), folderStack = newStack)
        }
    }

    fun toggleFavorite(videoId: String, current: Boolean) {
        viewModelScope.launch {
            videoDao.setFavorite(videoId, !current)
        }
    }

    fun createFolder(name: String, parentId: Long? = null) {
        viewModelScope.launch {
            folderDao.insert(FolderEntity(name = name.trim(), parentId = parentId))
        }
    }

    private suspend fun getVideosInFolderRecursive(folderId: Long): List<VideoEntity> {
        val videosList = mutableListOf<VideoEntity>()
        suspend fun recurse(fId: Long) {
            val directVideos = videoFolderDao.getVideosInFolder(fId).first()
            videosList.addAll(directVideos)
            val children = folderDao.getChildFolders(fId).first()
            children.forEach { child ->
                recurse(child.id)
            }
        }
        recurse(folderId)
        return videosList.distinctBy { it.id }
    }

    fun deleteFolder(folder: FolderEntity, deleteDownloads: Boolean) {
        viewModelScope.launch {
            if (deleteDownloads) {
                try {
                    val videosInFolder = getVideosInFolderRecursive(folder.id)
                    videosInFolder.forEach { video ->
                        if (video.localFilePath.isNotEmpty()) {
                            try {
                                val file = File(video.localFilePath)
                                if (file.exists()) file.delete()
                            } catch (e: Exception) {}
                            videoDao.update(video.copy(localFilePath = ""))
                        }
                        val downloads = downloadRepository.getLocalDownloadsForVideo(video.id)
                        downloads.forEach { download ->
                            downloadRepository.delete(download)
                        }
                    }
                } catch (e: Exception) {}
            }
            folderDao.delete(folder)
            val currentStack = _uiState.value.folderStack.filter { it.id != folder.id }
            val currentSelected = if (_uiState.value.selectedFolder?.id == folder.id) {
                currentStack.lastOrNull()
            } else {
                _uiState.value.selectedFolder
            }
            _uiState.value = _uiState.value.copy(selectedFolder = currentSelected, folderStack = currentStack)
        }
    }

    fun renameFolder(id: Long, name: String) {
        viewModelScope.launch {
            folderDao.rename(id, name.trim())
            // Update in stack if present
            val updatedStack = _uiState.value.folderStack.map { if (it.id == id) it.copy(name = name.trim()) else it }
            val updatedSelected = if (_uiState.value.selectedFolder?.id == id) _uiState.value.selectedFolder?.copy(name = name.trim()) else _uiState.value.selectedFolder
            _uiState.value = _uiState.value.copy(selectedFolder = updatedSelected, folderStack = updatedStack)
        }
    }

    fun addVideoToFolder(videoId: String, folderId: Long) {
        viewModelScope.launch {
            videoFolderDao.addVideoToFolder(
                VideoFolderJoin(videoId = videoId, folderId = folderId)
            )
        }
    }

    fun removeVideoFromFolder(videoId: String, folderId: Long) {
        viewModelScope.launch {
            videoFolderDao.removeVideoFromFolder(videoId, folderId)
        }
    }

    fun toggleViewMode() {
        val newMode = if (_uiState.value.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
        _uiState.value = _uiState.value.copy(viewMode = newMode)
        viewModelScope.launch {
            collectionsPreferences.setViewMode(newMode.name)
        }
    }

    fun toggleSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = !_uiState.value.isSelectionMode,
            selectedVideoIds = emptySet()
        )
    }

    fun toggleVideoSelection(videoId: String) {
        val currentSelected = _uiState.value.selectedVideoIds.toMutableSet()
        if (currentSelected.contains(videoId)) {
            currentSelected.remove(videoId)
        } else {
            currentSelected.add(videoId)
        }
        val isNowSelection = currentSelected.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            selectedVideoIds = currentSelected,
            isSelectionMode = if (currentSelected.isEmpty()) false else _uiState.value.isSelectionMode || isNowSelection
        )
    }

    fun selectAll(videoIds: List<String>) {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = true,
            selectedVideoIds = videoIds.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(isSelectionMode = false, selectedVideoIds = emptySet())
    }

    fun moveSelectedToFolder(folderId: Long) {
        val videoIds = _uiState.value.selectedVideoIds.toList()
        if (videoIds.isEmpty()) return
        viewModelScope.launch {
            videoIds.forEach { videoId ->
                videoFolderDao.addVideoToFolder(VideoFolderJoin(videoId, folderId))
            }
            _uiState.value = _uiState.value.copy(selectedVideoIds = emptySet(), isSelectionMode = false)
        }
    }

    fun moveVideoToFolder(videoId: String, folderId: Long) {
        viewModelScope.launch {
            videoFolderDao.addVideoToFolder(VideoFolderJoin(videoId, folderId))
        }
    }

    fun deleteSelectedVideos(deleteDownloads: Boolean) {
        val videoIds = _uiState.value.selectedVideoIds.toList()
        if (videoIds.isEmpty()) return
        viewModelScope.launch {
            videoIds.forEach { vid ->
                deleteSingleVideo(vid, deleteDownloads)
            }
            _uiState.value = _uiState.value.copy(selectedVideoIds = emptySet(), isSelectionMode = false)
        }
    }

    fun deleteSingleVideo(videoId: String, deleteDownload: Boolean) {
        viewModelScope.launch {
            if (deleteDownload) {
                try {
                    val video = videoDao.getVideoById(videoId)
                    if (video?.localFilePath?.isNotEmpty() == true) {
                        val file = File(video.localFilePath)
                        if (file.exists()) file.delete()
                    }
                    val downloads = downloadRepository.getLocalDownloadsForVideo(videoId)
                    downloads.forEach { download ->
                        downloadRepository.delete(download)
                    }
                } catch (e: Exception) {}
            }
            videoDao.deleteById(videoId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            videoDao.clearAllLastPlayed()
            historyDao.clearAllHistory()
            historyDao.clearAllWatchSessions()
            videoDao.deleteOrphanHistoryVideos()
        }
    }
}
