package com.example.medianest.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.preferences.PlaybackPreferences
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.service.PlayerResolver
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
    private val playbackPreferences: PlaybackPreferences,
    private val downloadRepository: DownloadRepository
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
                override fun onPlayerError(error: PlaybackException) {
                    _uiState.value = _uiState.value.copy(error = error.localizedMessage ?: "Playback error")
                }
            })
            PlayerResolver.player = this
        }
    }

    private var positionTrackingJob: Job? = null
    private var currentVideoId: String? = null
    private var currentStreamIndex: Int = 0
    private var videoInfo: ExtractedVideoInfo? = null

    fun initialize(videoId: String, streamIndex: Int) {
        currentVideoId = videoId
        currentStreamIndex = streamIndex
        val info = lastResultCache[videoId]
        videoInfo = info

        viewModelScope.launch {
            val speed = playbackPreferences.playbackSpeed.first()
            player.setPlaybackSpeed(speed)
            _uiState.value = _uiState.value.copy(currentSpeed = speed)

            val localDownloads = downloadRepository.getLocalDownloadsForVideo(videoId)
            val localFile = localDownloads.firstOrNull { it.filePath.isNotEmpty() }
            val uri = if (localFile != null) {
                android.net.Uri.fromFile(java.io.File(localFile.filePath)).toString()
            } else if (info != null && streamIndex < info.streamSources.size) {
                info.streamSources[streamIndex].url
            } else {
                _uiState.value = _uiState.value.copy(error = "No playable source found")
                return@launch
            }

            val title = info?.title ?: localFile?.title ?: "Unknown"
            val channel = info?.channelName ?: ""
            val thumbnail = info?.thumbnailUrl ?: localFile?.thumbnailUrl

            _uiState.value = _uiState.value.copy(
                title = title,
                channelName = channel,
                thumbnailUrl = thumbnail,
                isAudioOnly = localFile?.format == "audio" || localFile?.format == "audio_extracted",
                durationMs = if (localFile != null) 0L else (info?.durationSeconds ?: 0L) * 1000
            )

            val lastPlayback = historyDao.getLatestPlayback(videoId)
            val startPosition = lastPlayback?.positionMillis ?: 0L

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(channel)
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

    fun retry() {
        val videoId = currentVideoId ?: return
        _uiState.value = _uiState.value.copy(error = null)
        initialize(videoId, currentStreamIndex)
    }

    fun resetError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
