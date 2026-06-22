package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.local.entity.VideoFolderJoin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab { ALL, FOLDERS, FAVORITES }

data class LibraryUiState(
    val searchQuery: String = "",
    val currentTab: LibraryTab = LibraryTab.ALL,
    val selectedFolder: FolderEntity? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val videoDao: VideoDao,
    private val folderDao: FolderDao,
    private val videoFolderDao: VideoFolderDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val videos: StateFlow<List<VideoEntity>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            videoDao.getAllVideosSortedByDate()
        } else {
            videoDao.searchVideos(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteVideos: StateFlow<List<VideoEntity>> = _uiState.flatMapLatest { state ->
        if (state.currentTab == LibraryTab.FAVORITES) {
            videoDao.getFavoriteVideos()
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val folderVideos: StateFlow<List<VideoEntity>> = _uiState.flatMapLatest { state ->
        val folder = state.selectedFolder
        if (folder != null) {
            videoFolderDao.getVideosInFolder(folder.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rootFolders: StateFlow<List<FolderEntity>> = folderDao.getRootFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val childFolders: StateFlow<List<FolderEntity>> = _uiState.flatMapLatest { state ->
        val folder = state.selectedFolder
        if (folder != null) {
            folderDao.getChildFolders(folder.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab, selectedFolder = null)
    }

    fun selectFolder(folder: FolderEntity) {
        _uiState.value = _uiState.value.copy(currentTab = LibraryTab.FOLDERS, selectedFolder = folder)
    }

    fun navigateBackFromFolder() {
        val currentFolder = _uiState.value.selectedFolder
        if (currentFolder?.parentId != null) {
            viewModelScope.launch {
                val parent = folderDao.getFolderById(currentFolder.parentId)
                _uiState.value = _uiState.value.copy(selectedFolder = parent)
            }
        } else {
            _uiState.value = _uiState.value.copy(selectedFolder = null)
        }
    }

    fun toggleFavorite(videoId: String, current: Boolean) {
        viewModelScope.launch {
            videoDao.setFavorite(videoId, !current)
        }
    }

    fun createFolder(name: String, parentId: Long? = null) {
        viewModelScope.launch {
            folderDao.insert(FolderEntity(name = name, parentId = parentId))
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            folderDao.delete(folder)
            if (_uiState.value.selectedFolder?.id == folder.id) {
                _uiState.value = _uiState.value.copy(selectedFolder = null)
            }
        }
    }

    fun renameFolder(id: Long, name: String) {
        viewModelScope.launch {
            folderDao.rename(id, name)
        }
    }

    fun addVideoToFolder(videoId: String, folderId: Long) {
        viewModelScope.launch {
            videoFolderDao.addVideoToFolder(
                VideoFolderJoin(videoId = videoId, folderId = folderId)
            )
        }
    }

    fun removeVideoFromFolder(videoId: String, folderId: Long) {
        viewModelScope.launch {
            videoFolderDao.removeVideoFromFolder(videoId, folderId)
        }
    }
}
