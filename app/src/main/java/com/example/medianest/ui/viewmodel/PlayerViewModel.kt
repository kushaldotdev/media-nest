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
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.preferences.PlaybackPreferences
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.service.PlaybackService
import kotlin.comparisons.compareBy
import com.example.medianest.ui.viewmodel.HomeViewModel.Companion.lastResultCache
import com.example.medianest.extraction.YouTubeExtractor
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.example.medianest.data.mapper.toVideoEntity
import com.example.medianest.ui.screens.PlayerQueueItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val streamIndex: Int = 0,
    val downloadId: Long? = null,
    val watchCount: Int = 0,
    val videoQuality: String? = null,
    val availableStreams: List<StreamSource> = emptyList(),
    val completedDownloadQualities: List<String> = emptyList(),
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: HistoryDao,
    private val videoDao: VideoDao,
    private val playbackPreferences: PlaybackPreferences,
    private val downloadRepository: DownloadRepository,
    private val youTubeExtractor: YouTubeExtractor
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player

    private val _queue = MutableStateFlow<List<PlayerQueueItem>>(emptyList())
    val queue: StateFlow<List<PlayerQueueItem>> = _queue.asStateFlow()

    private val _queueContextTitle = MutableStateFlow<String?>(null)
    val queueContextTitle: StateFlow<String?> = _queueContextTitle.asStateFlow()

    private val _queueContextType = MutableStateFlow<String?>(null)
    val queueContextType: StateFlow<String?> = _queueContextType.asStateFlow()

    private var positionTrackingJob: Job? = null
    private var currentVideoId: String? = null
    private var currentStreamIndex: Int = 0
    private var currentDownloadId: Long? = null
    private var videoInfo: ExtractedVideoInfo? = null

    private var sessionTotalWatchTime: Long = 0L
    private var countedThisSession: Boolean = false
    private var autoMarkWatched: Boolean = true

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
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _uiState.value = _uiState.value.copy(
                videoWidth = videoSize.width,
                videoHeight = videoSize.height
            )
        }
        override fun onPlayerError(error: PlaybackException) {
            _uiState.value = _uiState.value.copy(error = error.localizedMessage ?: "Playback error")
        }
    }

    init {
        viewModelScope.launch {
            playbackPreferences.autoMarkWatched.collect { enabled ->
                autoMarkWatched = enabled
            }
        }

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

    fun setQueue(items: List<PlayerQueueItem>, startVideoId: String, contextTitle: String? = null, contextType: String? = null) {
        val indexed = items.mapIndexed { idx, itm ->
            if (itm.originalIndex == null) itm.copy(originalIndex = idx + 1) else itm
        }
        _queue.value = indexed
        _queueContextTitle.value = contextTitle
        _queueContextType.value = contextType
        val start = indexed.firstOrNull { it.id == startVideoId } ?: indexed.firstOrNull()
        if (start != null) initialize(start.id, start.streamIndex, start.downloadId)
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _queueContextTitle.value = null
        _queueContextType.value = null
    }

    fun initialize(videoId: String, streamIndex: Int, downloadId: Long? = null) {
        if (currentVideoId == videoId && currentStreamIndex == streamIndex && currentDownloadId == downloadId) return
        currentVideoId = videoId
        currentStreamIndex = streamIndex
        currentDownloadId = downloadId
        var info = lastResultCache.get(videoId)
        videoInfo = info

        if (info == null) {
            viewModelScope.launch {
                try {
                    val extracted = youTubeExtractor.extractVideo("https://www.youtube.com/watch?v=$videoId")
                    videoInfo = extracted
                    lastResultCache.put(videoId, extracted)
                    val currentState = _uiState.value
                    val newTitle = if (currentState.title.isEmpty() || currentState.title == "Unknown") extracted.title else currentState.title
                    val newChannel = if (currentState.channelName.isEmpty()) extracted.channelName else currentState.channelName
                    val newThumbnail = if (currentState.thumbnailUrl.isNullOrEmpty()) extracted.thumbnailUrl else currentState.thumbnailUrl
                    val newDuration = if (currentState.durationMs == 0L) extracted.durationSeconds * 1000 else currentState.durationMs

                    _uiState.value = _uiState.value.copy(
                        title = newTitle,
                        channelName = newChannel,
                        thumbnailUrl = newThumbnail,
                        durationMs = newDuration,
                        availableStreams = extracted.streamSources
                    )

                    val controller = _player.value
                    if (controller != null && !currentState.isLocal && (controller.currentMediaItem == null || currentState.error != null)) {
                        _uiState.value = _uiState.value.copy(error = null)
                        changeStreamQuality(streamIndex)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val action = {
            val controller = _player.value
            if (controller != null) {
                viewModelScope.launch {
                    val speed = playbackPreferences.playbackSpeed.first()
                    controller.setPlaybackSpeed(speed)
                    _uiState.value = _uiState.value.copy(currentSpeed = speed)

                    val localVideo = videoDao.getVideoById(videoId)
                    val localDownloads = downloadRepository.getLocalDownloadsForVideo(videoId)
                    val localFile = if (downloadId != null) {
                        localDownloads.firstOrNull { it.id == downloadId && it.filePath.isNotEmpty() && java.io.File(it.filePath).exists() }
                    } else if (info != null && streamIndex < info.streamSources.size) {
                        val streamSource = info.streamSources[streamIndex]
                        val streamQuality = if (streamSource.format == "audio") {
                            streamSource.quality
                        } else if (!streamSource.codec.isNullOrEmpty()) {
                            "${streamSource.quality} (${streamSource.codec})"
                        } else {
                            streamSource.quality
                        }
                        val streamRes = streamSource.quality.takeWhile { it.isDigit() }
                        localDownloads.firstOrNull { 
                            it.format == streamSource.format && 
                            it.filePath.isNotEmpty() && 
                            java.io.File(it.filePath).exists() &&
                            (it.quality == streamQuality || it.quality.takeWhile { c -> c.isDigit() } == streamRes)
                        }
                    } else {
                        localDownloads
                            .sortedWith(
                                compareBy<DownloadEntity> { it.format == "audio" || it.format == "audio_extracted" }
                                    .thenByDescending { getQualityValue(it) }
                                    .thenBy { it.id }
                            )
                            .firstOrNull { it.filePath.isNotEmpty() && java.io.File(it.filePath).exists() }
                    }

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

                    val title = info?.title ?: localFile?.title ?: localVideo?.title ?: "Unknown"
                    val channel = info?.channelName ?: localVideo?.channelName ?: ""
                    val thumbnail = info?.thumbnailUrl ?: localFile?.thumbnailUrl ?: localVideo?.thumbnailUrl

                    val lastPlayback = historyDao.getLatestPlayback(videoId)
                    val startPosition = 0L
                    val savedPosition = lastPlayback?.positionMillis ?: 0L
                    maxSavedPositionMs = savedPosition
                    
                    sessionTotalWatchTime = lastPlayback?.totalWatchTimeMillis ?: 0L
                    countedThisSession = false
                    videoDao.updateLastPlayedAt(videoId, System.currentTimeMillis())

                    val durationSeconds = info?.durationSeconds ?: localVideo?.durationSeconds ?: 0L
                    val quality = if (localFile != null) {
                        if (localFile.format == "audio" || localFile.format == "audio_extracted") {
                            localFile.quality
                        } else {
                            localFile.quality.substringBefore(" (")
                        }
                    } else {
                        streamSource?.quality
                    }
                    val completedDownloadQualities = localDownloads
                        .filter { it.status == DownloadStatus.COMPLETED && it.filePath.isNotEmpty() && java.io.File(it.filePath).exists() }
                        .map { it.quality }
                    val fallbackStreams = localDownloads
                        .filter { it.status == DownloadStatus.COMPLETED && it.filePath.isNotEmpty() && java.io.File(it.filePath).exists() }
                        .map { download ->
                            StreamSource(
                                url = android.net.Uri.fromFile(java.io.File(download.filePath)).toString(),
                                format = download.format,
                                quality = download.quality,
                                mimeType = if (download.format.contains("audio")) "audio/*" else "video/*",
                                contentLength = download.fileSizeBytes
                            )
                        }
                    _uiState.value = _uiState.value.copy(
                        title = title,
                        channelName = channel,
                        thumbnailUrl = thumbnail,
                        isAudioOnly = localFile?.format == "audio" || localFile?.format == "audio_extracted",
                        durationMs = if (localFile != null) 0L else durationSeconds * 1000,
                        positionMs = startPosition,
                        historyPositionMs = savedPosition,
                        videoId = videoId,
                        watchCount = localVideo?.watchCount ?: 0,
                        isLocal = localFile != null,
                        streamIndex = streamIndex,
                        downloadId = localFile?.id,
                        videoQuality = quality,
                        availableStreams = info?.streamSources ?: fallbackStreams,
                        completedDownloadQualities = completedDownloadQualities
                    )

                    val mediaMetadataBuilder = MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(channel)

                    if (!thumbnail.isNullOrEmpty()) {
                        val artworkUri = if (thumbnail.startsWith("http") || thumbnail.startsWith("content")) {
                            android.net.Uri.parse(thumbnail)
                        } else {
                            android.net.Uri.fromFile(java.io.File(thumbnail))
                        }
                        mediaMetadataBuilder.setArtworkUri(artworkUri)
                    }

                    val mediaItemBuilder = MediaItem.Builder()
                        .setMediaId(videoId)
                        .setUri(uri)
                        .setMediaMetadata(mediaMetadataBuilder.build())

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
        checkAndMarkWatched(positionMs, controller.duration)
    }

    fun seekRelative(offsetMs: Long) {
        val controller = _player.value ?: return
        val newPosition = (controller.currentPosition + offsetMs).coerceIn(0L, maxOf(controller.duration, 0L))
        controller.seekTo(newPosition)
        _uiState.value = _uiState.value.copy(positionMs = newPosition)
        savePosition()
        checkAndMarkWatched(newPosition, controller.duration)
    }

    fun setSpeed(speed: Float) {
        val controller = _player.value ?: return
        controller.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(currentSpeed = speed)
        viewModelScope.launch { playbackPreferences.setPlaybackSpeed(speed) }
    }

    private suspend fun ensureVideoExists(videoId: String) {
        if (videoDao.getVideoById(videoId) == null) {
            val info = videoInfo ?: lastResultCache.get(videoId)
            if (info != null) {
                videoDao.insert(info.toVideoEntity())
            } else {
                val fallback = com.example.medianest.data.local.entity.VideoEntity(
                    id = videoId,
                    title = _uiState.value.title.ifEmpty { "Video ($videoId)" },
                    channelName = _uiState.value.channelName.ifEmpty { "Unknown Channel" },
                    channelId = "",
                    durationSeconds = _uiState.value.durationMs / 1000,
                    thumbnailUrl = _uiState.value.thumbnailUrl ?: "",
                    description = "",
                    uploadDate = ""
                )
                videoDao.insert(fallback)
            }
        }
    }

    private fun checkAndMarkWatched(pos: Long, duration: Long) {
        val videoId = currentVideoId ?: return
        if (autoMarkWatched && !countedThisSession && duration > 0 && (duration - pos) <= 60000L) {
            countedThisSession = true
            viewModelScope.launch {
                ensureVideoExists(videoId)
                historyDao.insertWatchSession(
                    com.example.medianest.data.local.entity.WatchSessionEntity(
                        videoId = videoId,
                        watchedAt = System.currentTimeMillis()
                    )
                )
                videoDao.incrementWatchCount(videoId)
                val updatedVideo = videoDao.getVideoById(videoId)
                val newCount = updatedVideo?.watchCount ?: 0
                _uiState.value = _uiState.value.copy(watchCount = newCount)
            }
        }
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
                    
                    checkAndMarkWatched(pos, duration)

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
            viewModelScope.launch {
                ensureVideoExists(videoId)
                historyDao.upsert(
                    HistoryEntity(
                        videoId = videoId,
                        positionMillis = pos,
                        playedAt = System.currentTimeMillis(),
                        totalWatchTimeMillis = sessionTotalWatchTime
                    )
                )
                videoDao.updateLastPlayedAt(videoId, System.currentTimeMillis())
            }
        }
    }

    private fun saveFinalPosition() {
        val videoId = currentVideoId ?: return
        val controller = _player.value ?: return
        val duration = controller.duration
        if (duration > maxSavedPositionMs) {
            maxSavedPositionMs = duration
            viewModelScope.launch {
                ensureVideoExists(videoId)
                historyDao.upsert(
                    HistoryEntity(
                        videoId = videoId,
                        positionMillis = duration,
                        playedAt = System.currentTimeMillis(),
                        totalWatchTimeMillis = sessionTotalWatchTime
                    )
                )
                videoDao.updateLastPlayedAt(videoId, System.currentTimeMillis())
            }
        }
    }

    fun retry() {
        val videoId = currentVideoId ?: return
        val streamIndex = currentStreamIndex
        val downloadId = currentDownloadId
        currentVideoId = null
        currentStreamIndex = -1
        currentDownloadId = null
        _uiState.value = _uiState.value.copy(error = null)
        initialize(videoId, if (streamIndex < 0) 0 else streamIndex, downloadId)
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
            ensureVideoExists(videoId)
            historyDao.upsert(
                HistoryEntity(
                    videoId = videoId,
                    positionMillis = pos,
                    playedAt = System.currentTimeMillis(),
                    totalWatchTimeMillis = sessionTotalWatchTime
                )
            )
            videoDao.updateLastPlayedAt(videoId, System.currentTimeMillis())
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

    fun changeStreamQuality(streamIndex: Int) {
        val controller = _player.value ?: return
        val videoId = _uiState.value.videoId ?: currentVideoId ?: return
        val currentPos = controller.currentPosition
        val isPlaying = controller.isPlaying

        _uiState.value = _uiState.value.copy(
            positionMs = currentPos,
            streamIndex = streamIndex
        )
        currentStreamIndex = streamIndex

        viewModelScope.launch {
            val info = videoInfo ?: lastResultCache.get(videoId)
            val uri: String
            val audioUri: String?
            val isLocalNow: Boolean
            val matchingLocalFile: DownloadEntity?
            val quality: String

            if (info == null) {
                val localDownloads = downloadRepository.getLocalDownloadsForVideo(videoId)
                    .filter { it.status == DownloadStatus.COMPLETED && it.filePath.isNotEmpty() && java.io.File(it.filePath).exists() }
                if (streamIndex >= localDownloads.size) return@launch
                val fallbackDownload = localDownloads[streamIndex]
                matchingLocalFile = fallbackDownload
                isLocalNow = true
                uri = android.net.Uri.fromFile(java.io.File(fallbackDownload.filePath)).toString()
                audioUri = null
                quality = fallbackDownload.quality.substringBefore(" (")
            } else {
                if (streamIndex >= info.streamSources.size) return@launch
                val streamSource = info.streamSources[streamIndex]
                val localDownloads = downloadRepository.getLocalDownloadsForVideo(videoId)
                val streamQuality = if (streamSource.format == "audio") {
                    streamSource.quality
                } else if (!streamSource.codec.isNullOrEmpty()) {
                    "${streamSource.quality} (${streamSource.codec})"
                } else {
                    streamSource.quality
                }
                val streamRes = streamSource.quality.takeWhile { it.isDigit() }
                val localMatch = localDownloads.firstOrNull {
                    it.status == DownloadStatus.COMPLETED && it.filePath.isNotEmpty() && java.io.File(it.filePath).exists() &&
                    (it.quality == streamQuality || it.quality.takeWhile { c -> c.isDigit() } == streamRes) &&
                    it.format != "audio"
                }

                matchingLocalFile = localMatch
                isLocalNow = localMatch != null
                uri = if (localMatch != null) {
                    android.net.Uri.fromFile(java.io.File(localMatch.filePath)).toString()
                } else {
                    streamSource.url
                }

                audioUri = if (localMatch == null && streamSource.format == "video_only") {
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

                quality = if (localMatch != null) {
                    localMatch.quality.substringBefore(" (")
                } else {
                    streamSource.quality
                }
            }

            val title = _uiState.value.title
            val channel = _uiState.value.channelName
            val thumbnail = _uiState.value.thumbnailUrl

            val mediaMetadataBuilder = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(channel)

            if (!thumbnail.isNullOrEmpty()) {
                val artworkUri = if (thumbnail.startsWith("http") || thumbnail.startsWith("content")) {
                    android.net.Uri.parse(thumbnail)
                } else {
                    android.net.Uri.fromFile(java.io.File(thumbnail))
                }
                mediaMetadataBuilder.setArtworkUri(artworkUri)
            }

            val mediaItemBuilder = MediaItem.Builder()
                .setMediaId(videoId)
                .setUri(uri)
                .setMediaMetadata(mediaMetadataBuilder.build())

            if (audioUri != null) {
                mediaItemBuilder.setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setExtras(android.os.Bundle().apply {
                            putString("audio_url", audioUri)
                        })
                        .build()
                )
            }

            _uiState.value = _uiState.value.copy(
                videoQuality = quality,
                isLocal = isLocalNow,
                downloadId = matchingLocalFile?.id
            )

            val mediaItem = mediaItemBuilder.build()
            controller.setMediaItem(mediaItem)
            controller.seekTo(currentPos)
            controller.prepare()
            val speed = playbackPreferences.playbackSpeed.first()
            controller.setPlaybackSpeed(speed)
            if (isPlaying) {
                controller.play()
            }
        }
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

    private fun getQualityValue(download: DownloadEntity): Int {
        val digits = download.quality.takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }
}
