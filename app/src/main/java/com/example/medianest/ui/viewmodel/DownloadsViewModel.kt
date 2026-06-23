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
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.service.AudioExtractor
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
    private val audioExtractor: AudioExtractor
) : ViewModel() {

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _playingVideoId = MutableStateFlow<String?>(null)
    val playingVideoId: StateFlow<String?> = _playingVideoId

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    val downloads: StateFlow<List<DownloadEntity>> = downloadRepository.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun deleteDownload(download: DownloadEntity, deleteFile: Boolean) {
        viewModelScope.launch {
            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                DownloadService.cancel(context, download.id)
            } else {
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
                        val dir = if (download.format == "audio" || download.format == "audio_extracted") "audio" else "video"
                        val outputDir = File(context.filesDir, "MediaNest/$dir")
                        val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
                        if (tmpFile.exists()) {
                            tmpFile.delete()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DownloadsViewModel", "Failed to delete tmp file", e)
                    }
                }
                downloadRepository.delete(download)
            }
        }
    }

    fun retryDownload(download: DownloadEntity) {
        viewModelScope.launch {
            try {
                val dir = if (download.format == "audio" || download.format == "audio_extracted") "audio" else "video"
                val outputDir = File(context.filesDir, "MediaNest/$dir")
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
                        val dir = if (download.format == "audio" || download.format == "audio_extracted") "audio" else "video"
                        val outputDir = File(context.filesDir, "MediaNest/$dir")
                        val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
                        if (tmpFile.exists()) {
                            tmpFile.delete()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DownloadsViewModel", "Failed to delete tmp file on delete all", e)
                    }
                }
                downloadRepository.delete(download)
            }
        }
    }

    fun extractAudio(download: DownloadEntity) {
        if (download.filePath.isEmpty() || download.status != DownloadStatus.COMPLETED) return
        if (_extractingVideoId.value == download.videoId) return

        viewModelScope.launch {
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
