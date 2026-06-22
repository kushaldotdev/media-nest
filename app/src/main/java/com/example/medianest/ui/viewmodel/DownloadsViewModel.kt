package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context

data class DownloadsUiState(
    val maxConcurrent: Int = DownloadPreferences.DEFAULT_MAX,
    val activeCount: Int = 0
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val downloadPreferences: DownloadPreferences
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = downloadRepository.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState

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
    }

    fun pauseDownload(downloadId: Long) {
        DownloadService.pause(context, downloadId)
    }

    fun resumeDownload(downloadId: Long) {
        DownloadService.resume(context, downloadId)
    }

    fun cancelDownload(downloadId: Long) {
        DownloadService.cancel(context, downloadId)
    }

    fun retryDownload(download: DownloadEntity) {
        viewModelScope.launch {
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
}
