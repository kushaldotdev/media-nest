package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.entity.MostViewedVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatisticsUiState(
    val totalDownloadedFiles: Int = 0,
    val totalDatabaseEntries: Int = 0,
    val totalWatchTimeMillis: Long = 0L,
    val mostViewedVideo: MostViewedVideo? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val videoDao: VideoDao,
    private val downloadDao: DownloadDao,
    private val historyDao: HistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                val totalEntries = videoDao.getTotalVideoCount()
                val totalDownloads = videoDao.getDownloadedVideoCount()
                
                val totalWatchTime = historyDao.getTotalWatchTime() ?: 0L
                val mostViewed = historyDao.getMostViewedVideo()
                
                _uiState.value = StatisticsUiState(
                    totalDownloadedFiles = totalDownloads,
                    totalDatabaseEntries = totalEntries,
                    totalWatchTimeMillis = totalWatchTime,
                    mostViewedVideo = mostViewed,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
