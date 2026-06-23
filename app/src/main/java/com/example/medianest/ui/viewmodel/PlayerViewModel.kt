package com.example.medianest.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.preferences.PlaybackPreferences
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.service.PlaybackService
import com.example.medianest.ui.viewmodel.HomeViewModel.Companion.lastResultCache
import com.google.common.util.concurrent.ListenableFuture
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
    val error: String? = null,
    val historyPositionMs: Long = 0L,
    val isBuffering: Boolean = false,
    val bufferedPositionMs: Long = 0L,
    val videoId: String? = null,
    val isLocal: Boolean = false,
    val streamIndex: Int = 0
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: HistoryDao,
    private val playbackPreferences: PlaybackPreferences,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player

    private var positionTrackingJob: Job? = null
    private var currentVideoId: String? = null
    private var currentStreamIndex: Int = 0
    private var videoInfo: ExtractedVideoInfo? = null

    private var sessionTotalWatchTime: Long = 0L
    private var countedThisSession: Boolean = false

    private var pendingInit: (() -> Unit)? = null
    private var maxSavedPositionMs: Long = 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            if (isPlaying) startPositionTracking() else stopPositionTracking()
        }
        override fun onPlaybackStateChanged(state: Int) {
            val isBuffering = state == Player.STATE_BUFFERING
            _uiState.value = _uiState.value.copy(isBuffering = isBuffering)
            if (state == Player.STATE_READY) {
                val duration = _player.value?.duration ?: 0L
                if (duration > 0) {
                    _uiState.value = _uiState.value.copy(durationMs = duration)
                }
            }
            if (state == Player.STATE_ENDED) saveFinalPosition()
        }
        override fun onPlayerError(error: PlaybackException) {
            _uiState.value = _uiState.value.copy(error = error.localizedMessage ?: "Playback error")
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val controller = future.get()
                    _player.value = controller
                    controller.addListener(playerListener)
                    pendingInit?.invoke()
                    pendingInit = null
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = e.localizedMessage ?: "Failed to connect to playback service")
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun initialize(videoId: String, streamIndex: Int) {
        if (currentVideoId == videoId && currentStreamIndex == streamIndex) return
        currentVideoId = videoId
        currentStreamIndex = streamIndex
        val info = lastResultCache.get(videoId)
        videoInfo = info

        val action = {
            val controller = _player.value
            if (controller != null) {
                viewModelScope.launch {
                    val speed = playbackPreferences.playbackSpeed.first()
                    controller.setPlaybackSpeed(speed)
                    _uiState.value = _uiState.value.copy(currentSpeed = speed)

                    val localDownloads = downloadRepository.getLocalDownloadsForVideo(videoId)
                    val localFile = localDownloads.firstOrNull { it.filePath.isNotEmpty() && java.io.File(it.filePath).exists() }
                    val streamSource = if (localFile == null && info != null && streamIndex < info.streamSources.size) {
                        info.streamSources[streamIndex]
                    } else {
                        null
                    }
                    val uri = if (localFile != null) {
                        android.net.Uri.fromFile(java.io.File(localFile.filePath)).toString()
                    } else if (streamSource != null) {
                        streamSource.url
                    } else {
                        _uiState.value = _uiState.value.copy(error = "No playable source found")
                        return@launch
                    }

                    val audioUri = if (localFile == null && streamSource != null && streamSource.format == "video_only" && info != null) {
                        val isWebmVideo = streamSource.mimeType.contains("webm", ignoreCase = true) || streamSource.codec.contains("webm", ignoreCase = true)
                        val compatibleAudioStreams = info.streamSources
                            .filter { it.format == "audio" }
                            .filter {
                                val mime = it.mimeType.lowercase()
                                val codec = it.codec.lowercase()
                                if (isWebmVideo) {
                                    mime.contains("webm") || mime.contains("ogg") || codec.contains("webm") || codec.contains("opus")
                                } else {
                                    mime.contains("mp4") || mime.contains("m4a") || codec.contains("m4a") || codec.contains("aac")
                                }
                            }
                        val audioStreamsToUse = if (compatibleAudioStreams.isNotEmpty()) compatibleAudioStreams else {
                            info.streamSources.filter { it.format == "audio" }
                        }
                        val audioStream = audioStreamsToUse
                            .maxByOrNull { it.quality.replace("kbps", "").toIntOrNull() ?: 0 }
                        audioStream?.url
                    } else {
                        null
                    }

                    val title = info?.title ?: localFile?.title ?: "Unknown"
                    val channel = info?.channelName ?: ""
                    val thumbnail = info?.thumbnailUrl ?: localFile?.thumbnailUrl

                    val lastPlayback = historyDao.getLatestPlayback(videoId)
                    val startPosition = 0L
                    val savedPosition = lastPlayback?.positionMillis ?: 0L
                    maxSavedPositionMs = savedPosition
                    
                    sessionTotalWatchTime = lastPlayback?.totalWatchTimeMillis ?: 0L
                    countedThisSession = false

                    _uiState.value = _uiState.value.copy(
                        title = title,
                        channelName = channel,
                        thumbnailUrl = thumbnail,
                        isAudioOnly = localFile?.format == "audio" || localFile?.format == "audio_extracted",
                        durationMs = if (localFile != null) 0L else (info?.durationSeconds ?: 0L) * 1000,
                        positionMs = startPosition,
                        historyPositionMs = savedPosition,
                        videoId = videoId,
                        isLocal = localFile != null,
                        streamIndex = streamIndex
                    )

                    val mediaItemBuilder = MediaItem.Builder()
                        .setMediaId(videoId)
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(channel)
                                .build()
                        )

                    if (audioUri != null) {
                        mediaItemBuilder.setRequestMetadata(
                            MediaItem.RequestMetadata.Builder()
                                .setExtras(android.os.Bundle().apply {
                                    putString("audio_url", audioUri)
                                })
                                .build()
                        )
                    }

                    val mediaItem = mediaItemBuilder.build()
                    controller.setMediaItem(mediaItem)
                    controller.seekTo(startPosition)
                    controller.prepare()
                    controller.play()
                }
            }
        }

        if (_player.value != null) {
            action()
        } else {
            pendingInit = action
        }
    }

    fun togglePlayPause() {
        val controller = _player.value ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMs: Long) {
        val controller = _player.value ?: return
        controller.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(positionMs = positionMs)
    }

    fun seekRelative(offsetMs: Long) {
        val controller = _player.value ?: return
        val newPosition = (controller.currentPosition + offsetMs).coerceIn(0L, maxOf(controller.duration, 0L))
        controller.seekTo(newPosition)
        _uiState.value = _uiState.value.copy(positionMs = newPosition)
        savePosition()
    }

    fun setSpeed(speed: Float) {
        val controller = _player.value ?: return
        controller.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(currentSpeed = speed)
        viewModelScope.launch { playbackPreferences.setPlaybackSpeed(speed) }
    }

    private fun startPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val controller = _player.value
                if (controller != null) {
                    if (controller.isPlaying) {
                        sessionTotalWatchTime += 1000L
                    }
                    val pos = controller.currentPosition
                    val duration = controller.duration
                    
                    if (!countedThisSession && duration > 0 && pos >= duration * 0.8) {
                        countedThisSession = true
                        historyDao.insertWatchSession(
                            com.example.medianest.data.local.entity.WatchSessionEntity(
                                videoId = currentVideoId ?: "",
                                watchedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    val buf = controller.bufferedPosition
                    _uiState.value = _uiState.value.copy(
                        positionMs = pos,
                        bufferedPositionMs = buf
                    )
                    savePosition()
                }
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
        val controller = _player.value ?: return
        val pos = controller.currentPosition
        if (pos > maxSavedPositionMs) {
            maxSavedPositionMs = pos
            _uiState.value = _uiState.value.copy(historyPositionMs = pos)
            viewModelScope.launch {
                historyDao.upsert(
                    HistoryEntity(
                        videoId = videoId,
                        positionMillis = pos,
                        playedAt = System.currentTimeMillis(),
                        totalWatchTimeMillis = sessionTotalWatchTime
                    )
                )
            }
        }
    }

    private fun saveFinalPosition() {
        val videoId = currentVideoId ?: return
        val controller = _player.value ?: return
        val duration = controller.duration
        if (duration > maxSavedPositionMs) {
            maxSavedPositionMs = duration
            _uiState.value = _uiState.value.copy(historyPositionMs = duration)
            viewModelScope.launch {
                historyDao.upsert(
                    HistoryEntity(
                        videoId = videoId,
                        positionMillis = duration,
                        playedAt = System.currentTimeMillis(),
                        totalWatchTimeMillis = sessionTotalWatchTime
                    )
                )
            }
        }
    }

    fun retry() {
        val videoId = currentVideoId ?: return
        _uiState.value = _uiState.value.copy(error = null)
        initialize(videoId, currentStreamIndex)
    }

    fun resetError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearHistoryPosition() {
        _uiState.value = _uiState.value.copy(historyPositionMs = 0L)
    }

    fun forceSaveCurrentPosition() {
        val videoId = currentVideoId ?: return
        val controller = _player.value ?: return
        val pos = controller.currentPosition
        maxSavedPositionMs = pos
        _uiState.value = _uiState.value.copy(historyPositionMs = pos)
        viewModelScope.launch {
            historyDao.upsert(
                HistoryEntity(
                    videoId = videoId,
                    positionMillis = pos,
                    playedAt = System.currentTimeMillis(),
                    totalWatchTimeMillis = sessionTotalWatchTime
                )
            )
        }
    }

    fun stopPlayback() {
        val controller = _player.value
        if (controller != null) {
            controller.stop()
            controller.clearMediaItems()
        }
        currentVideoId = null
        currentStreamIndex = 0
        videoInfo = null
        _uiState.value = PlayerUiState() // Reset UI State entirely
    }

    override fun onCleared() {
        super.onCleared()
        positionTrackingJob?.cancel()
        positionTrackingJob = null
        _player.value?.removeListener(playerListener)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        _player.value = null
    }
}
