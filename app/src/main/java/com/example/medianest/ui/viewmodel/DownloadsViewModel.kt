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
import kotlinx.coroutines.flow.combine
import com.example.medianest.service.DownloadService
import com.example.medianest.service.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val downloads: StateFlow<List<DownloadEntity>> = downloadRepository.getAllDownloads()
        .map { list ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                list.map { download ->
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
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videosMap: StateFlow<Map<String, VideoEntity>> = videoRepository.getAllVideos()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val playbackHistory: StateFlow<List<HistoryEntity>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastWatchedVideo: StateFlow<VideoEntity?> = combine(
        videoRepository.getAllVideos(),
        downloads
    ) { allVideos, downloadsList ->
        val completedIds = downloadsList.filter { it.status == DownloadStatus.COMPLETED }.map { it.videoId }.toSet()
        allVideos.filter { it.id in completedIds && it.lastPlayedAt != null }
            .maxByOrNull { it.lastPlayedAt!! }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastWatchedDownload: StateFlow<DownloadEntity?> = combine(
        lastWatchedVideo,
        downloads
    ) { video, downloadsList ->
        if (video == null) null
        else downloadsList.find { it.videoId == video.id && it.status == DownloadStatus.COMPLETED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastWatchedProgress: StateFlow<Float> = combine(
        lastWatchedVideo,
        playbackHistory
    ) { video, historyList ->
        if (video == null) return@combine 0f
        val history = historyList.find { it.videoId == video.id }
        val positionMillis = history?.positionMillis ?: 0L
        if (video.durationSeconds > 0 && positionMillis > 0) {
            ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val lastWatchedPositionMs: StateFlow<Long> = combine(
        lastWatchedVideo,
        playbackHistory
    ) { video, historyList ->
        if (video == null) return@combine 0L
        val history = historyList.find { it.videoId == video.id }
        history?.positionMillis ?: 0L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

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
            downloadRepository.updateStatusOnly(downloadId, DownloadStatus.CANCELED)
        }
        DownloadService.cancel(context, downloadId)
    }

    private suspend fun getOutputDir(format: String): File {
        val dir = if (format == "audio" || format == "audio_extracted") "audio" else "video"
        val customFolder = downloadPreferences.downloadFolder.first()
        return if (customFolder.isNotEmpty()) {
            File(File(customFolder), dir)
        } else {
            File(context.filesDir, "MediaNest/$dir")
        }
    }

    fun deleteDownload(download: DownloadEntity, deleteFile: Boolean) {
        viewModelScope.launch {
            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                DownloadService.cancel(context, download.id)
            }
            
            if (deleteFile) {
                if (download.filePath.isNotEmpty()) {
                    try {
                        val file = File(download.filePath)
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DownloadsViewModel", "Failed to delete completed file", e)
                    }
                }
                try {
                    val outputDir = getOutputDir(download.format)
                    val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
                    if (tmpFile.exists()) {
                        tmpFile.delete()
                    }
                    val audioFile = File(outputDir, "${download.videoId}_${download.quality}_audio.tmp")
                    if (audioFile.exists()) {
                        audioFile.delete()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DownloadsViewModel", "Failed to delete tmp file", e)
                }
            }
            
            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                kotlinx.coroutines.delay(500)
            }
            
            downloadRepository.delete(download)
            
            val remaining = downloadRepository.getLocalDownloadsForVideo(download.videoId)
            if (remaining.isEmpty()) {
                val video = videoRepository.getVideoById(download.videoId)
                if (video != null) {
                    videoRepository.updateVideo(video.copy(localFilePath = ""))
                }
            }
        }
    }

    fun retryDownload(download: DownloadEntity) {
        viewModelScope.launch {
            try {
                val outputDir = getOutputDir(download.format)
                val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadsViewModel", "Failed to delete tmp file on restart", e)
            }

            val reset = download.copy(
                status = DownloadStatus.QUEUED,
                progress = 0f,
                errorMessage = null,
                retryCount = 0
            )
            downloadRepository.update(reset)
            DownloadService.resume(context, download.id)
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
            val intent = Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL_ALL
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                android.util.Log.e("DownloadsViewModel", "Failed to cancel all on delete all", e)
            }

            val all = downloadRepository.getAllDownloadsOnce()
            all.forEach { download ->
                if (deleteFiles) {
                    if (download.filePath.isNotEmpty()) {
                        try {
                            val file = File(download.filePath)
                            if (file.exists()) {
                                file.delete()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DownloadsViewModel", "Failed to delete file on delete all", e)
                        }
                    }
                    try {
                        val outputDir = getOutputDir(download.format)
                        val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
                        if (tmpFile.exists()) {
                            tmpFile.delete()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DownloadsViewModel", "Failed to delete tmp file on delete all", e)
                    }
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
        if (download.filePath.isEmpty() || download.status != DownloadStatus.COMPLETED) return
        if (_extractingVideoId.value == download.videoId) return
        android.widget.Toast.makeText(context, "Audio extraction started", android.widget.Toast.LENGTH_SHORT).show()

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            val existing = downloadRepository.getAudioExtraction(download.videoId)
            if (existing != null) return@launch

            _extractingVideoId.value = download.videoId

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
                val result = audioExtractor.extractAudio(
                    download.filePath,
                    download.videoId,
                    download.quality
                )

                if (result.success) {
                    downloadRepository.markCompleted(insertId, File(result.outputPath).length(), result.outputPath)
                } else {
                    downloadRepository.markFailed(
                        insertId,
                        result.errorMessage ?: "Extraction failed",
                        0
                    )
                }
            } catch (e: Throwable) {
                downloadRepository.markFailed(
                    insertId,
                    e.message ?: "Extraction failed: ${e.javaClass.simpleName}",
                    0
                )
            } finally {
                _extractingVideoId.value = ""
            }
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
}
