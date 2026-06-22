package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.model.ChannelInfo
import com.example.medianest.data.model.ExtractedPlaylistInfo
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeUiState {
    data object Idle : HomeUiState()
    data object Loading : HomeUiState()
    data class Success(val video: ExtractedVideoInfo) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
    data class PlaylistResult(val playlist: ExtractedPlaylistInfo) : HomeUiState()
    data class ChannelResult(val channel: ChannelInfo) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val videoDao: VideoDao
) : ViewModel() {

    companion object {
        val lastResultCache = mutableMapOf<String, ExtractedVideoInfo>()
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState

    fun onUrlSubmitted(url: String) {
        if (url.isBlank()) {
            _uiState.value = HomeUiState.Error("Please enter a URL")
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            runCatching {
                when {
                    "youtube.com/playlist" in url || "list=" in url -> {
                        val playlist = repository.extractPlaylist(url)
                        if (playlist.videos.isNotEmpty()) {
                            _uiState.value = HomeUiState.PlaylistResult(playlist)
                            return@launch
                        }
                        val video = repository.searchAndSave(url)
                        HomeUiState.Success(video)
                    }
                    "/channel/" in url || "/c/" in url || "/@" in url -> {
                        val channel = repository.extractChannel(url)
                        HomeUiState.ChannelResult(channel)
                    }
                    else -> {
                        val video = repository.searchAndSave(url)
                        HomeUiState.Success(video)
                    }
                }
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { e ->
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to extract video")
            }
        }
    }

    fun cacheResult(video: ExtractedVideoInfo) {
        lastResultCache[video.videoId] = video
    }

    fun getCachedResult(videoId: String): ExtractedVideoInfo? = lastResultCache[videoId]

    fun toggleFavorite(videoId: String, favorite: Boolean) {
        viewModelScope.launch {
            videoDao.setFavorite(videoId, favorite)
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Idle
    }
}
