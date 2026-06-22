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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

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
