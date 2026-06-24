package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.mapper.toVideoEntity
import com.example.medianest.data.repository.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val videoRepository: com.example.medianest.data.repository.VideoRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val historyDao: com.example.medianest.data.local.dao.HistoryDao
) : ViewModel() {
    private val _videoDownloads = MutableStateFlow<List<DownloadEntity>>(emptyList())
    val videoDownloads: StateFlow<List<DownloadEntity>> = _videoDownloads

    private val _localVideo = MutableStateFlow<VideoEntity?>(null)
    val localVideo: StateFlow<VideoEntity?> = _localVideo

    private var downloadsJob: kotlinx.coroutines.Job? = null
    private var localVideoJob: kotlinx.coroutines.Job? = null
    private var currentVideoId: String = ""

    private val _videoInfo = MutableStateFlow<ExtractedVideoInfo?>(null)
    val videoInfo: StateFlow<ExtractedVideoInfo?> = _videoInfo

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

    private val _videoHistory = MutableStateFlow<com.example.medianest.data.local.entity.HistoryEntity?>(null)
    val videoHistory: StateFlow<com.example.medianest.data.local.entity.HistoryEntity?> = _videoHistory

    private val _watchSessions = MutableStateFlow<List<com.example.medianest.data.local.entity.WatchSessionEntity>>(emptyList())
    val watchSessions: StateFlow<List<com.example.medianest.data.local.entity.WatchSessionEntity>> = _watchSessions

    fun loadVideoInfo(videoId: String) {
        if (currentVideoId != videoId) {
            currentVideoId = videoId
            downloadsJob?.cancel()
            downloadsJob = viewModelScope.launch {
                downloadRepository.getDownloadsForVideoFlow(videoId).collect {
                    _videoDownloads.value = it
                }
            }
            localVideoJob?.cancel()
            localVideoJob = viewModelScope.launch {
                videoRepository.getVideoByIdFlow(videoId).collect {
                    _localVideo.value = it
                }
            }
            viewModelScope.launch {
                _videoHistory.value = historyDao.getLatestPlayback(videoId)
            }
            viewModelScope.launch {
                historyDao.getWatchSessions(videoId).collect {
                    _watchSessions.value = it
                }
            }
        }
        val cached = HomeViewModel.lastResultCache.get(videoId)
        if (cached != null && cached.streamSources.isNotEmpty()) {
            _videoInfo.value = cached
            if (cached.streamSources.any { it.contentLength == null || it.contentLength <= 0L }) {
                resolveStreamSizes(videoId)
            }
            return
        }
        
        viewModelScope.launch {
            try {
                val videoUrl = "https://www.youtube.com/watch?v=$videoId"
                val extracted = videoRepository.searchAndSave(videoUrl)
                HomeViewModel.lastResultCache.put(videoId, extracted)
                _videoInfo.value = extracted
                resolveStreamSizes(videoId)
            } catch (e: Exception) {
                // If it fails, fallback to cached info without streams if available
                if (cached != null) {
                    _videoInfo.value = cached
                }
            }
        }
    }

    private fun resolveStreamSizes(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentInfo = _videoInfo.value ?: return@launch
            val updatedSources = currentInfo.streamSources.map { source ->
                if (source.contentLength == null || source.contentLength <= 0L) {
                    val size = getStreamSize(source.url)
                    source.copy(contentLength = size)
                } else {
                    source
                }
            }
            val newInfo = currentInfo.copy(streamSources = updatedSources)
            _videoInfo.value = newInfo
            HomeViewModel.lastResultCache.put(videoId, newInfo)
        }
    }

    private fun getStreamSize(url: String): Long {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connect()
            if (connection.responseCode in 200..299) {
                val len = connection.contentLengthLong
                if (len > 0) return len
            }
        } catch (e: Exception) {
            // Ignore resolving failure
        } finally {
            connection?.disconnect()
        }
        return 0L
    }

    private var currentChannelId: String = ""
    private var currentChannelName: String = ""
    private var currentThumbnailUrl: String? = null

    fun initSubscription(channelId: String, channelName: String, thumbnailUrl: String?) {
        currentChannelId = channelId
        currentChannelName = channelName
        currentThumbnailUrl = thumbnailUrl
    }

    fun checkSubscription() {
        if (currentChannelId.isEmpty()) return
        viewModelScope.launch {
            _isSubscribed.value = subscriptionRepository.isSubscribed("channel", currentChannelId)
        }
    }

    fun toggleSubscription() {
        if (currentChannelId.isEmpty()) return
        viewModelScope.launch {
            if (_isSubscribed.value) {
                val sub = subscriptionRepository.getBySource("channel", currentChannelId)
                if (sub != null) subscriptionRepository.unsubscribe(sub.id)
                _isSubscribed.value = false
            } else {
                subscriptionRepository.subscribe("channel", currentChannelId, currentChannelName, currentThumbnailUrl)
                _isSubscribed.value = true
            }
        }
    }

    fun loadFavorite(videoId: String) {
        currentVideoId = videoId
        viewModelScope.launch {
            val video = videoRepository.getVideoById(videoId)
            _isFavorite.value = video?.favorite ?: false
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val newValue = !_isFavorite.value
            videoRepository.setFavorite(currentVideoId, newValue)
            _isFavorite.value = newValue
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
                context.startForegroundService(Intent(context, DownloadService::class.java))
                android.widget.Toast.makeText(context, "Download started: ${videoInfo.title}", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to start downloader service: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
