package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.local.entity.VideoFolderJoin
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.preferences.DevicePreferences
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import android.content.Context
import com.example.medianest.service.AudioExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.medianest.data.mapper.toVideoEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab { ALL, FOLDERS, FAVORITES, PLAYLISTS, SUBSCRIPTIONS }

enum class ViewMode { GRID, LIST }

data class LibraryUiState(
    val searchQuery: String = "",
    val currentTab: LibraryTab = LibraryTab.ALL,
    val selectedFolder: FolderEntity? = null,
    val viewMode: ViewMode = ViewMode.GRID,
    val isSelectionMode: Boolean = false,
    val selectedVideoIds: Set<String> = emptySet()
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao,
    private val folderDao: FolderDao,
    private val videoFolderDao: VideoFolderDao,
    private val downloadRepository: DownloadRepository,
    private val videoRepository: VideoRepository,
    private val audioExtractor: AudioExtractor,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val videos: StateFlow<List<VideoEntity>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            videoDao.getAllVideosSortedByDate()
        } else {
            videoDao.searchVideos(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteVideos: StateFlow<List<VideoEntity>> = _uiState.flatMapLatest { state ->
        if (state.currentTab == LibraryTab.FAVORITES) {
            videoDao.getFavoriteVideos()
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val folderVideos: StateFlow<List<VideoEntity>> = _uiState.flatMapLatest { state ->
        val folder = state.selectedFolder
        if (folder != null) {
            videoFolderDao.getVideosInFolder(folder.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rootFolders: StateFlow<List<FolderEntity>> = folderDao.getRootFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val childFolders: StateFlow<List<FolderEntity>> = _uiState.flatMapLatest { state ->
        val folder = state.selectedFolder
        if (folder != null) {
            folderDao.getChildFolders(folder.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDownloads: StateFlow<List<DownloadEntity>> = downloadRepository.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _fetchingStreamsFor = MutableStateFlow<String?>(null)
    val fetchingStreamsFor: StateFlow<String?> = _fetchingStreamsFor

    private val _fetchedStreams = MutableStateFlow<ExtractedVideoInfo?>(null)
    val fetchedStreams: StateFlow<ExtractedVideoInfo?> = _fetchedStreams

    init {
        viewModelScope.launch {
            devicePreferences.libraryViewMode.collect { modeStr ->
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
            val existing = downloadRepository.getDownload(videoInfo.videoId, stream.format, stream.quality)
            if (existing != null) {
                android.widget.Toast.makeText(context, "Download already exists in queue", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            val video = videoRepository.getVideoById(videoInfo.videoId)
            if (video == null) {
                videoRepository.insertVideo(videoInfo.toVideoEntity())
            }

            val entity = DownloadEntity(
                videoId = videoInfo.videoId,
                url = stream.url,
                videoUrl = "https://www.youtube.com/watch?v=${videoInfo.videoId}",
                format = stream.format,
                quality = stream.quality,
                status = com.example.medianest.data.local.entity.DownloadStatus.QUEUED,
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
            if (download.status == com.example.medianest.data.local.entity.DownloadStatus.DOWNLOADING || download.status == com.example.medianest.data.local.entity.DownloadStatus.QUEUED) {
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
                    val video = videoRepository.getVideoById(download.videoId)
                    if (video != null) {
                        videoRepository.insertVideo(video.copy(localFilePath = ""))
                    }
                }
            }
        }
    }

    fun extractAudio(download: DownloadEntity) {
        if (download.filePath.isEmpty() || download.status != com.example.medianest.data.local.entity.DownloadStatus.COMPLETED) return
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
                status = com.example.medianest.data.local.entity.DownloadStatus.DOWNLOADING,
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab, selectedFolder = null)
    }

    fun selectFolder(folder: FolderEntity) {
        _uiState.value = _uiState.value.copy(currentTab = LibraryTab.FOLDERS, selectedFolder = folder)
    }

    fun navigateBackFromFolder() {
        val currentFolder = _uiState.value.selectedFolder
        if (currentFolder?.parentId != null) {
            viewModelScope.launch {
                val parent = folderDao.getFolderById(currentFolder.parentId)
                _uiState.value = _uiState.value.copy(selectedFolder = parent)
            }
        } else {
            _uiState.value = _uiState.value.copy(selectedFolder = null)
        }
    }

    fun toggleFavorite(videoId: String, current: Boolean) {
        viewModelScope.launch {
            videoDao.setFavorite(videoId, !current)
        }
    }

    fun createFolder(name: String, parentId: Long? = null) {
        viewModelScope.launch {
            folderDao.insert(FolderEntity(name = name, parentId = parentId))
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            folderDao.delete(folder)
            if (_uiState.value.selectedFolder?.id == folder.id) {
                _uiState.value = _uiState.value.copy(selectedFolder = null)
            }
        }
    }

    fun renameFolder(id: Long, name: String) {
        viewModelScope.launch {
            folderDao.rename(id, name)
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
            devicePreferences.setLibraryViewMode(newMode.name)
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
        _uiState.value = _uiState.value.copy(selectedVideoIds = currentSelected)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(isSelectionMode = false, selectedVideoIds = emptySet())
    }

    fun moveSelectedToFolder(folderId: Long) {
        val videoIds = _uiState.value.selectedVideoIds.toList()
        if (videoIds.isEmpty()) return
        viewModelScope.launch {
            videoIds.forEach { videoId ->
                videoFolderDao.addVideoToFolder(com.example.medianest.data.local.entity.VideoFolderJoin(videoId, folderId))
            }
            _uiState.value = _uiState.value.copy(selectedVideoIds = emptySet(), isSelectionMode = false)
        }
    }

    fun moveVideoToFolder(videoId: String, folderId: Long) {
        viewModelScope.launch {
            videoFolderDao.addVideoToFolder(com.example.medianest.data.local.entity.VideoFolderJoin(videoId, folderId))
        }
    }
}
