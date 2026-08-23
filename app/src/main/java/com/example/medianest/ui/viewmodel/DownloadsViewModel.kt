package com.example.medianest.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.data.repository.VideoRepository
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.sync.SyncManager
import com.example.medianest.service.AudioExtractor
import kotlinx.coroutines.flow.map
import com.example.medianest.service.DownloadPathResolver
import com.example.medianest.service.DownloadService
import com.example.medianest.service.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DownloadsUiState(
    val maxConcurrent: Int = DownloadPreferences.DEFAULT_MAX,
    val activeCount: Int = 0,
    val extractingVideoId: String? = null
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val downloadPreferences: DownloadPreferences,
    private val audioExtractor: AudioExtractor,
    private val videoRepository: VideoRepository,
    private val videoDao: VideoDao,
    private val historyDao: HistoryDao,
    private val syncManager: SyncManager
) : ViewModel() {

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _playingVideoId = MutableStateFlow<String?>(null)
    val playingVideoId: StateFlow<String?> = _playingVideoId

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playingUri = MutableStateFlow<String?>(null)
    val playingUri: StateFlow<String?> = _playingUri

    private val _queueOrder = MutableStateFlow<List<Long>>(emptyList())
    val queueOrder: StateFlow<List<Long>> = _queueOrder.asStateFlow()

    fun reorderDownloads(fromIndex: Int, toIndex: Int, currentList: List<DownloadEntity>) {
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices || fromIndex == toIndex) return
        val mutable = currentList.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        _queueOrder.value = mutable.map { it.id }
    }

    fun clearCustomOrder() {
        _queueOrder.value = emptyList()
    }

    val downloads: StateFlow<List<DownloadEntity>?> = downloadRepository.getAllDownloads()
        .map { list ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val mapped = list.map { download ->
                    if (download.status == com.example.medianest.data.local.entity.DownloadStatus.COMPLETED) {
                        if (download.filePath.isEmpty() || !java.io.File(download.filePath).exists()) {
                            download.copy(errorMessage = "file_missing")
                        } else {
                            download
                        }
                    } else {
                        download
                    }
                }
                // Reconcile VideoEntity.localFilePath so the Library "Downloaded" badge
                // is cleared when no completed download for the video still has a file on disk.
                mapped.filter { it.status == DownloadStatus.COMPLETED && it.errorMessage == "file_missing" }
                    .distinctBy { it.videoId }
                    .forEach { download ->
                        try {
                            reconcileMissingFile(download.videoId)
                        } catch (e: Exception) {
                            android.util.Log.w("DownloadsViewModel", "Failed to reconcile missing file for ${download.videoId}", e)
                        }
                    }
                mapped
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * Clears [VideoEntity.localFilePath] for [videoId] when no completed download for that
     * video still has a file present on disk. No-op when another download's file still exists.
     */
    private suspend fun reconcileMissingFile(videoId: String) {
        val hasRemainingFile = downloadRepository.getLocalDownloadsForVideo(videoId)
            .any { it.filePath.isNotEmpty() && File(it.filePath).exists() }
        if (hasRemainingFile) return
        val video = videoDao.getVideoById(videoId) ?: return
        // Only clear when the current path is itself gone: if a new download for this
        // video completed concurrently, localFilePath points at an existing file and
        // must not be wiped (narrow stale-snapshot race in the remaining-file check).
        if (video.localFilePath.isNotEmpty() && !File(video.localFilePath).exists()) {
            videoDao.update(video.copy(localFilePath = ""))
        }
    }

    val videosMap: StateFlow<Map<String, VideoEntity>> = videoRepository.getAllVideos()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val playbackHistory: StateFlow<List<HistoryEntity>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshDownloads() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            
            try {
                syncManager.sync()
            } catch (e: Exception) {
                android.util.Log.e("DownloadsViewModel", "Failed to sync during pull-to-refresh", e)
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val allDownloads = downloadRepository.getAllDownloadsOnce()
                    allDownloads.forEach { download ->
                        if (download.status == DownloadStatus.COMPLETED) {
                            val file = if (download.filePath.isNotEmpty()) File(download.filePath) else null
                            if (file == null || !file.exists()) {
                                downloadRepository.update(download.copy(errorMessage = "file_missing"))
                                reconcileMissingFile(download.videoId)
                            } else if (download.errorMessage == "file_missing") {
                                downloadRepository.update(download.copy(errorMessage = null))
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DownloadsViewModel", "Error refreshing downloads on disk", e)
                }
            }
            
            kotlinx.coroutines.delay(800)
            _isRefreshing.value = false
        }
    }

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState

    private val _extractingVideoId = MutableStateFlow<String?>(null)
    val extractingVideoId: StateFlow<String?> = _extractingVideoId

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
        viewModelScope.launch {
            _extractingVideoId.collect { id ->
                _uiState.value = _uiState.value.copy(extractingVideoId = id)
            }
        }

        viewModelScope.launch {
            val activeList = downloadRepository.getActiveDownloads().first()
            if (activeList.isNotEmpty()) {
                try {
                    context.startForegroundService(Intent(context, DownloadService::class.java))
                } catch (e: Exception) {
                    android.util.Log.e("DownloadsViewModel", "Failed to auto-start DownloadService on init", e)
                }
            }
        }

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val controller = future.get()
                    mediaController = controller
                    updatePlaybackState(controller)
                    controller.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            updatePlaybackState(controller)
                        }
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            updatePlaybackState(controller)
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            updatePlaybackState(controller)
                        }
                    })
                } catch (e: Exception) {
                    android.util.Log.e("DownloadsViewModel", "Failed to connect to playback service", e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun updatePlaybackState(player: Player) {
        _isPlaying.value = player.isPlaying
        _playingVideoId.value = player.currentMediaItem?.mediaId
        _playingUri.value = player.currentMediaItem?.localConfiguration?.uri?.toString()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun pauseDownload(downloadId: Long) {
        // Optimistic DB update → Room Flow emits → app UI shows Paused immediately
        viewModelScope.launch {
            downloadRepository.updateStatusOnly(downloadId, DownloadStatus.PAUSED)
        }
        DownloadService.pause(context, downloadId)
    }

    fun resumeDownload(downloadId: Long) {
        viewModelScope.launch {
            downloadRepository.updateStatusOnly(downloadId, DownloadStatus.QUEUED)
        }
        DownloadService.resume(context, downloadId)
    }

    fun cancelDownload(downloadId: Long) {
        viewModelScope.launch {
            // Keep canceled rows visible even if service command is delayed or rejected.
            downloadRepository.updateStatusOnly(downloadId, DownloadStatus.CANCELED)
        }
        DownloadService.cancel(context, downloadId)
    }

    fun deleteDownload(download: DownloadEntity, deleteFile: Boolean) {
        DownloadService.delete(context, download.id, deleteFile)
    }

    fun retryDownload(download: DownloadEntity) {
        viewModelScope.launch {
            DownloadService.restart(context, download.id)
        }
    }

    fun setMaxConcurrent(max: Int) {
        viewModelScope.launch {
            downloadPreferences.setMaxConcurrentDownloads(max)
        }
    }

    fun pauseAllDownloads() {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_ALL
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            android.util.Log.e("DownloadsViewModel", "Failed to start pause all command", e)
        }
    }

    fun resumeAllDownloads() {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_ALL
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            android.util.Log.e("DownloadsViewModel", "Failed to start resume all command", e)
        }
    }

    fun deleteAllDownloads(deleteFiles: Boolean) {
        viewModelScope.launch {
            val all = downloadRepository.getAllDownloadsOnce()
            all.forEach { download ->
                DownloadService.delete(context, download.id, deleteFiles)
            }
        }
    }

    fun extractAudio(download: DownloadEntity) {
        if (download.filePath.isEmpty() || download.status != DownloadStatus.COMPLETED) return
        if (_extractingVideoId.value == download.videoId) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val existing = downloadRepository.getAudioExtraction(download.videoId)
            if (existing != null) return@launch

            _extractingVideoId.value = download.videoId

            val outputRoot = downloadPreferences.downloadFolder.first()
            val extractionEntity = DownloadEntity(
                videoId = download.videoId,
                url = "",
                format = "audio_extracted",
                quality = "${download.quality}_audio",
                title = download.title,
                thumbnailUrl = download.thumbnailUrl,
                status = DownloadStatus.QUEUED,
                progress = 0f,
                downloadUuid = java.util.UUID.randomUUID().toString(),
                outputRoot = outputRoot
            )
            val insertId = downloadRepository.insert(extractionEntity)
            if (insertId <= 0L) {
                _extractingVideoId.value = ""
                return@launch
            }
            DownloadService.extractAudio(context, insertId)
            if (_extractingVideoId.value == download.videoId) _extractingVideoId.value = ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
    }
}

object PendingRestartConfirmation {
    val pendingDownloadId = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val navigateToDownloads = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToUpdates = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
