package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.model.ChannelInfo
import com.example.medianest.data.model.ExtractedPlaylistInfo
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.repository.SubscriptionRepository
import com.example.medianest.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    companion object {
        val lastResultCache = android.util.LruCache<String, ExtractedVideoInfo>(50)
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState

    fun onUrlSubmitted(inputUrl: String) {
        val url = inputUrl.trim()
        if (url.isBlank()) {
            _uiState.value = HomeUiState.Error("Please enter a URL")
            return
        }

        val sanitizedUrl = when {
            // Handle @channel inputs
            url.startsWith("@") -> "https://www.youtube.com/$url"
            
            // Handle youtu.be short links (strip ?si= but keep the ID)
            url.contains("youtu.be/") -> {
                val idPart = url.substringAfter("youtu.be/").substringBefore("?")
                "https://www.youtube.com/watch?v=$idPart"
            }
            
            // Handle standard youtube.com links (clean up si= parameter)
            url.contains("youtube.com/") -> {
                var cleanUrl = url
                if (!cleanUrl.startsWith("http")) cleanUrl = "https://$cleanUrl"
                // Remove si= parameter if it exists (using simple string replace for safety)
                cleanUrl = cleanUrl.replace(Regex("[?&]si=[^&]*"), "")
                // If we ended up with a dangling ? or &, clean it
                cleanUrl.replace(Regex("[?&]$"), "")
            }
            
            // Handle bare IDs (e.g. 5AJ1Lhi-J84?si=...)
            else -> {
                val cleanId = url.substringAfterLast("/").substringBefore("?")
                "https://www.youtube.com/watch?v=$cleanId"
            }
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            runCatching {
                when {
                    "youtube.com/playlist" in sanitizedUrl || "list=" in sanitizedUrl -> {
                        val listId = sanitizedUrl.substringAfter("list=").substringBefore("&")
                        val playlistUrl = "https://www.youtube.com/playlist?list=$listId"
                        
                        try {
                            val playlist = repository.extractPlaylist(playlistUrl)
                            if (playlist.videos.isNotEmpty()) {
                                HomeUiState.PlaylistResult(playlist)
                            } else {
                                val video = repository.searchAndSave(sanitizedUrl)
                                HomeUiState.Success(video)
                            }
                        } catch (e: Exception) {
                            // If playlist extraction fails (e.g. YouTube Mixes or v1/next errors), fallback to single video
                            // NewPipe rejects URLs with &list= for stream extraction, so we must clean it:
                            if ("v=" in sanitizedUrl) {
                                val videoId = sanitizedUrl.substringAfter("v=").substringBefore("&")
                                val cleanVideoUrl = "https://www.youtube.com/watch?v=$videoId"
                                val video = repository.searchAndSave(cleanVideoUrl)
                                HomeUiState.Success(video)
                            } else {
                                HomeUiState.Error("Playlist failed: ${e.message}")
                            }
                        }
                    }
                    "/channel/" in sanitizedUrl || "/c/" in sanitizedUrl || "/@" in sanitizedUrl || sanitizedUrl.contains("youtube.com/@") -> {
                        val channel = repository.extractChannel(sanitizedUrl)
                        HomeUiState.ChannelResult(channel)
                    }
                    else -> {
                        val video = repository.searchAndSave(sanitizedUrl)
                        HomeUiState.Success(video)
                    }
                }
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { e ->
                _uiState.value = HomeUiState.Error("${e.message ?: "Failed to extract"} \nURL: $sanitizedUrl")
            }
        }
    }

    fun cacheResult(video: ExtractedVideoInfo) {
        lastResultCache.put(video.videoId, video)
    }

    fun getCachedResult(videoId: String): ExtractedVideoInfo? = lastResultCache.get(videoId)

    fun toggleFavorite(videoId: String, favorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(videoId, favorite)
        }
    }

    val subscriptions: StateFlow<List<com.example.medianest.data.local.entity.SubscriptionEntity>> = subscriptionRepository.getAllSubscriptions()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun subscribe(sourceType: String, sourceId: String, name: String, thumbnailUrl: String?) {
        viewModelScope.launch {
            subscriptionRepository.subscribe(sourceType, sourceId, name, thumbnailUrl)
        }
    }

    fun unsubscribe(sourceId: String) {
        viewModelScope.launch {
            subscriptionRepository.unsubscribeBySourceId(sourceId)
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Idle
    }
}
