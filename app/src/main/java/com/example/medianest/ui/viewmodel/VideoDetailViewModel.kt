package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val videoRepository: com.example.medianest.data.repository.VideoRepository,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {
    private var currentVideoId: String = ""

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

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
            val existing = downloadRepository.getDownload(videoInfo.videoId, stream.format, stream.quality)
            if (existing != null) return@launch

            val entity = DownloadEntity(
                videoId = videoInfo.videoId,
                url = stream.url,
                format = stream.format,
                quality = stream.quality,
                status = DownloadStatus.QUEUED,
                title = videoInfo.title,
                thumbnailUrl = videoInfo.thumbnailUrl
            )
            downloadRepository.insert(entity)
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }
    }
}
