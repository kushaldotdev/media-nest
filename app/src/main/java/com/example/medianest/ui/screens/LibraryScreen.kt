package com.example.medianest.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.ui.utils.UiUtils
import com.example.medianest.ui.viewmodel.LibraryTab
import com.example.medianest.ui.viewmodel.LibraryViewModel
import com.example.medianest.ui.viewmodel.ViewMode
import com.example.medianest.ui.viewmodel.FolderStats
import com.example.medianest.ui.components.UnifiedVideoCard
import com.example.medianest.ui.components.UnifiedVideoRow
import com.example.medianest.ui.components.VideoCardConfig
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.components.QuickDownloadMenu
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoClick: (String) -> Unit,
    onSubscriptionClick: (String, String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val favoriteVideos by viewModel.favoriteVideos.collectAsStateWithLifecycle()
    val folderVideos by viewModel.folderVideos.collectAsStateWithLifecycle()
    val rootFolders by viewModel.rootFolders.collectAsStateWithLifecycle()
    val childFolders by viewModel.childFolders.collectAsStateWithLifecycle()
    val videoFolderMap by viewModel.videoFolderMap.collectAsStateWithLifecycle()
    val folderStatsMap by viewModel.folderStatsMap.collectAsStateWithLifecycle()
    val playbackHistory by viewModel.playbackHistory.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    var singleVideoToMove by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var deleteDownloadsWithFolder by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var renameFolderName by remember { mutableStateOf("") }

    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val fetchingStreamsFor by viewModel.fetchingStreamsFor.collectAsStateWithLifecycle()
    val fetchedStreams by viewModel.fetchedStreams.collectAsStateWithLifecycle()
    var expandedDownloadVideoId by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalMoveToFolder provides { videoId ->
            singleVideoToMove = videoId
            showMoveToFolderDialog = true
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isSelectionMode) "${uiState.selectedVideoIds.size} Selected" else "Library") },
                actions = {
                    if (uiState.currentTab != LibraryTab.FOLDERS || uiState.selectedFolder != null) {
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                if (uiState.viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle View"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.isSelectionMode) {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { showMoveToFolderDialog = true },
                            enabled = uiState.selectedVideoIds.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Move")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search videos...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val tabs = listOf(LibraryTab.HISTORY, LibraryTab.FOLDERS, LibraryTab.FAVORITES, LibraryTab.PLAYLISTS, LibraryTab.SUBSCRIPTIONS)
            val tabLabels = listOf("History", "Folders", "Favorites", "Playlists", "Channels")
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(uiState.currentTab),
                edgePadding = 8.dp,
                divider = {},
                indicator = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = uiState.currentTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.setTab(tab) },
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                tabLabels[index],
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            when (uiState.currentTab) {
                LibraryTab.HISTORY -> {
                    if (videos.isEmpty()) {
                        EmptyState("No watch history yet")
                    } else {
                        VideoListLayout(
                            videos = videos,
                            videoFolderMap = videoFolderMap,
                            viewMode = uiState.viewMode,
                            isSelectionMode = uiState.isSelectionMode,
                            selectedIds = uiState.selectedVideoIds,
                            expandedDownloadVideoId = expandedDownloadVideoId,
                            fetchingStreamsFor = fetchingStreamsFor,
                            fetchedStreams = fetchedStreams,
                            allDownloads = allDownloads,
                            playbackHistory = playbackHistory,
                            onVideoClick = onVideoClick,
                            onVideoLongClick = { viewModel.toggleSelectionMode(); viewModel.toggleVideoSelection(it) },
                            onToggleSelection = { viewModel.toggleVideoSelection(it) },
                            onFavoriteToggle = { video -> 
                                viewModel.toggleFavorite(video.id, video.favorite)
                                coroutineScope.launch { snackbarHostState.showSnackbar(if (video.favorite) "Removed from Favorites" else "Added to Favorites") }
                            },
                            onDownloadIconClick = { videoId -> 
                                expandedDownloadVideoId = videoId
                                viewModel.fetchStreamsFor(videoId)
                            },
                            onDismissDownloadMenu = { expandedDownloadVideoId = null },
                            onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                            onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                            onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                        )
                    }
                }
                LibraryTab.FOLDERS -> {
                    FolderContent(
                        folders = rootFolders,
                        childFolders = childFolders,
                        folderVideos = folderVideos,
                        videoFolderMap = videoFolderMap,
                        folderStatsMap = folderStatsMap,
                        selectedFolder = uiState.selectedFolder,
                        searchQuery = uiState.searchQuery,
                        viewMode = uiState.viewMode,
                        isSelectionMode = uiState.isSelectionMode,
                        selectedIds = uiState.selectedVideoIds,
                        expandedDownloadVideoId = expandedDownloadVideoId,
                        fetchingStreamsFor = fetchingStreamsFor,
                        fetchedStreams = fetchedStreams,
                        allDownloads = allDownloads,
                        playbackHistory = playbackHistory,
                        onFolderClick = { viewModel.selectFolder(it) },
                        onCreateFolder = { name -> 
                            viewModel.createFolder(name, uiState.selectedFolder?.id)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Folder created") }
                        },
                        onRenameFolder = { folder ->
                            folderToRename = folder
                            renameFolderName = folder.name
                        },
                        onDeleteFolder = { folder ->
                            folderToDelete = folder
                            deleteDownloadsWithFolder = false
                        },
                        onNavigateBack = { viewModel.navigateBackFromFolder() },
                        onVideoClick = onVideoClick,
                        onVideoLongClick = { viewModel.toggleSelectionMode(); viewModel.toggleVideoSelection(it) },
                        onToggleSelection = { viewModel.toggleVideoSelection(it) },
                        onFavoriteToggle = { video -> 
                            viewModel.toggleFavorite(video.id, video.favorite)
                            coroutineScope.launch { snackbarHostState.showSnackbar(if (video.favorite) "Removed from Favorites" else "Added to Favorites") }
                        },
                        onRemoveFromFolder = { videoId, folderId ->
                            viewModel.removeVideoFromFolder(videoId, folderId)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Removed from folder") }
                        },
                        onDownloadIconClick = { videoId -> 
                            expandedDownloadVideoId = videoId
                            viewModel.fetchStreamsFor(videoId)
                        },
                        onDismissDownloadMenu = { expandedDownloadVideoId = null },
                        onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                        onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                        onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                    )
                }
                LibraryTab.FAVORITES -> {
                    if (favoriteVideos.isEmpty()) {
                        EmptyState("No favorite videos")
                    } else {
                        VideoListLayout(
                            videos = favoriteVideos,
                            videoFolderMap = videoFolderMap,
                            viewMode = uiState.viewMode,
                            isSelectionMode = uiState.isSelectionMode,
                            selectedIds = uiState.selectedVideoIds,
                            expandedDownloadVideoId = expandedDownloadVideoId,
                            fetchingStreamsFor = fetchingStreamsFor,
                            fetchedStreams = fetchedStreams,
                            allDownloads = allDownloads,
                            playbackHistory = playbackHistory,
                            onVideoClick = onVideoClick,
                            onVideoLongClick = { viewModel.toggleSelectionMode(); viewModel.toggleVideoSelection(it) },
                            onToggleSelection = { viewModel.toggleVideoSelection(it) },
                            onFavoriteToggle = { video -> 
                                viewModel.toggleFavorite(video.id, video.favorite)
                                coroutineScope.launch { snackbarHostState.showSnackbar(if (video.favorite) "Removed from Favorites" else "Added to Favorites") }
                            },
                            onDownloadIconClick = { videoId -> 
                                expandedDownloadVideoId = videoId
                                viewModel.fetchStreamsFor(videoId)
                            },
                            onDismissDownloadMenu = { expandedDownloadVideoId = null },
                            onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                            onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                            onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                        )
                    }
                }
                LibraryTab.PLAYLISTS -> {
                    SubscriptionsScreen(
                        sourceType = "playlist",
                        searchQuery = uiState.searchQuery,
                        viewMode = uiState.viewMode,
                        onSubscriptionClick = onSubscriptionClick
                    )
                }
                LibraryTab.SUBSCRIPTIONS -> {
                    SubscriptionsScreen(
                        sourceType = "channel",
                        searchQuery = uiState.searchQuery,
                        viewMode = uiState.viewMode,
                        onSubscriptionClick = onSubscriptionClick
                    )
                }
                }
            }



            if (showMoveToFolderDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showMoveToFolderDialog = false
                    singleVideoToMove = null
                },
                title = { Text("Move to Folder") },
                text = {
                    LazyColumn {
                        items(rootFolders) { folder ->
                            TextButton(
                                onClick = {
                                    if (singleVideoToMove != null) {
                                        viewModel.moveVideoToFolder(singleVideoToMove!!, folder.id)
                                        singleVideoToMove = null
                                    } else {
                                        viewModel.moveSelectedToFolder(folder.id)
                                    }
                                    showMoveToFolderDialog = false
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Moved to ${folder.name}") }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(folder.name)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        showMoveToFolderDialog = false
                        singleVideoToMove = null
                    }) { Text("Cancel") }
                }
            )
        }

        folderToDelete?.let { folder ->
            AlertDialog(
                onDismissRequest = { folderToDelete = null },
                title = { Text("Delete Folder?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Are you sure you want to delete folder '${folder.name}'? This cannot be undone.")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { deleteDownloadsWithFolder = !deleteDownloadsWithFolder }
                        ) {
                            Checkbox(
                                checked = deleteDownloadsWithFolder,
                                onCheckedChange = { deleteDownloadsWithFolder = it }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Delete downloaded videos in this folder", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteFolder(folder, deleteDownloadsWithFolder)
                            folderToDelete = null
                            coroutineScope.launch { snackbarHostState.showSnackbar("Folder deleted") }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { folderToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        folderToRename?.let { folder ->
            AlertDialog(
                onDismissRequest = { folderToRename = null },
                title = { Text("Rename Folder") },
                text = {
                    OutlinedTextField(
                        value = renameFolderName,
                        onValueChange = { renameFolderName = it },
                        placeholder = { Text("New folder name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renameFolderName.isNotBlank()) {
                                viewModel.renameFolder(folder.id, renameFolderName.trim())
                                folderToRename = null
                                coroutineScope.launch { snackbarHostState.showSnackbar("Folder renamed") }
                            }
                        }
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { folderToRename = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    }
}

@Composable
private fun VideoListLayout(
    videos: List<VideoEntity>,
    videoFolderMap: Map<String, List<FolderEntity>>,
    viewMode: ViewMode,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onVideoClick: (String) -> Unit,
    onVideoLongClick: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit,
    expandedDownloadVideoId: String?,
    fetchingStreamsFor: String?,
    fetchedStreams: com.example.medianest.data.model.ExtractedVideoInfo?,
    allDownloads: List<com.example.medianest.data.local.entity.DownloadEntity>,
    playbackHistory: List<com.example.medianest.data.local.entity.HistoryEntity>,
    onDownloadIconClick: (String) -> Unit,
    onDismissDownloadMenu: () -> Unit,
    onEnqueueDownload: (com.example.medianest.data.model.ExtractedVideoInfo, com.example.medianest.data.model.StreamSource) -> Unit,
    onDeleteDownload: (com.example.medianest.data.local.entity.DownloadEntity) -> Unit,
    onExtractAudio: (com.example.medianest.data.local.entity.DownloadEntity) -> Unit
) {
    val onMoveToFolderClick = LocalMoveToFolder.current
    val context = LocalContext.current

    if (viewMode == ViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos, key = { it.id }) { video ->
                val history = playbackHistory.find { it.videoId == video.id }
                val positionMillis = history?.positionMillis ?: 0L
                val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                    ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                } else 0f

                UnifiedVideoCard(
                    title = video.title,
                    channelName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl,
                    durationSeconds = video.durationSeconds,
                    uploadDate = video.uploadDate,
                    isFavorite = video.favorite,
                    isDownloaded = video.localFilePath.isNotEmpty(),
                    isSelected = selectedIds.contains(video.id),
                    playbackProgressFraction = progressFraction,
                    folders = videoFolderMap[video.id] ?: emptyList(),
                    config = VideoCardConfig(
                        showFavoriteButton = !isSelectionMode,
                        showMoveToFolderButton = !isSelectionMode,
                        showDownloadButton = !isSelectionMode,
                        showSelectionCheckbox = isSelectionMode,
                        showFolderBadges = true,
                        showPlaybackProgress = true,
                        showDownloadedBadge = true
                    ),
                    onClick = { if (isSelectionMode) onToggleSelection(video.id) else onVideoClick(video.id) },
                    onLongClick = { onVideoLongClick(video.id) },
                    onFavoriteToggle = { onFavoriteToggle(video) },
                    onMoveToFolder = { onMoveToFolderClick(video.id) },
                    onDownloadClick = { onDownloadIconClick(video.id) },
                    onSelectionToggle = { onToggleSelection(video.id) },
                    downloadMenuContent = {
                        QuickDownloadMenu(
                            isExpanded = expandedDownloadVideoId == video.id,
                            onDismiss = onDismissDownloadMenu,
                            isFetching = fetchingStreamsFor == video.id,
                            fetchedStreams = fetchedStreams,
                            allDownloads = allDownloads,
                            videoId = video.id,
                            onEnqueueDownload = onEnqueueDownload,
                            onDeleteDownload = onDeleteDownload,
                            onExtractAudio = onExtractAudio
                        )
                    }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos, key = { it.id }) { video ->
                val history = playbackHistory.find { it.videoId == video.id }
                val positionMillis = history?.positionMillis ?: 0L
                val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                    ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                } else 0f

                UnifiedVideoRow(
                    title = video.title,
                    channelName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl,
                    durationSeconds = video.durationSeconds,
                    uploadDate = video.uploadDate,
                    isFavorite = video.favorite,
                    isDownloaded = video.localFilePath.isNotEmpty(),
                    isSelected = selectedIds.contains(video.id),
                    playbackProgressFraction = progressFraction,
                    folders = videoFolderMap[video.id] ?: emptyList(),
                    config = VideoCardConfig(
                        showFavoriteButton = !isSelectionMode,
                        showMoveToFolderButton = !isSelectionMode,
                        showDownloadButton = !isSelectionMode,
                        showSelectionCheckbox = isSelectionMode,
                        showFolderBadges = true,
                        showPlaybackProgress = true,
                        showDownloadedBadge = true
                    ),
                    onClick = { if (isSelectionMode) onToggleSelection(video.id) else onVideoClick(video.id) },
                    onLongClick = { onVideoLongClick(video.id) },
                    onFavoriteToggle = { onFavoriteToggle(video) },
                    onMoveToFolder = { onMoveToFolderClick(video.id) },
                    onDownloadClick = { onDownloadIconClick(video.id) },
                    onSelectionToggle = { onToggleSelection(video.id) },
                    downloadMenuContent = {
                        QuickDownloadMenu(
                            isExpanded = expandedDownloadVideoId == video.id,
                            onDismiss = onDismissDownloadMenu,
                            isFetching = fetchingStreamsFor == video.id,
                            fetchedStreams = fetchedStreams,
                            allDownloads = allDownloads,
                            videoId = video.id,
                            onEnqueueDownload = onEnqueueDownload,
                            onDeleteDownload = onDeleteDownload,
                            onExtractAudio = {
                                onExtractAudio(it)
                                android.widget.Toast.makeText(context, "Audio extraction started", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                )
            }
        }
    }
}



@Composable
private fun FolderContent(
    folders: List<FolderEntity>,
    childFolders: List<FolderEntity>,
    folderVideos: List<VideoEntity>,
    videoFolderMap: Map<String, List<FolderEntity>>,
    folderStatsMap: Map<Long, FolderStats>,
    selectedFolder: FolderEntity?,
    searchQuery: String,
    viewMode: ViewMode,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onFolderClick: (FolderEntity) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onNavigateBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onVideoLongClick: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit,
    onRemoveFromFolder: (String, Long) -> Unit,
    expandedDownloadVideoId: String?,
    fetchingStreamsFor: String?,
    fetchedStreams: com.example.medianest.data.model.ExtractedVideoInfo?,
    allDownloads: List<com.example.medianest.data.local.entity.DownloadEntity>,
    playbackHistory: List<com.example.medianest.data.local.entity.HistoryEntity>,
    onDownloadIconClick: (String) -> Unit,
    onDismissDownloadMenu: () -> Unit,
    onEnqueueDownload: (com.example.medianest.data.model.ExtractedVideoInfo, com.example.medianest.data.model.StreamSource) -> Unit,
    onDeleteDownload: (com.example.medianest.data.local.entity.DownloadEntity) -> Unit,
    onExtractAudio: (com.example.medianest.data.local.entity.DownloadEntity) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    var singleVideoToMove by remember { mutableStateOf<String?>(null) }
    var expandedDownloadVideoId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedFolder != null) {
                TextButton(onClick = onNavigateBack) { Text("< Back") }
                Text(selectedFolder.name, style = MaterialTheme.typography.titleMedium)
            } else {
                Text("Folders", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "Create folder")
            }
        }

        if (selectedFolder == null && searchQuery.isEmpty()) {
            if (folders.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No folders yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(folders, key = { it.id }) { folder ->
                        FolderRow(folder = folder, stats = folderStatsMap[folder.id], onClick = { onFolderClick(folder) }, onRename = { onRenameFolder(folder) }, onDelete = { onDeleteFolder(folder) })
                    }
                }
            }
        } else {
            val currentFolders = if (selectedFolder == null) folders else childFolders
            val currentVideos = folderVideos

            if (currentFolders.isEmpty() && currentVideos.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isNotEmpty()) "No results found" else "Folder is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentFolders.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Folders" else "Subfolders",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(currentFolders, key = { "folder_${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { folder ->
                            FolderRow(folder = folder, stats = folderStatsMap[folder.id], onClick = { onFolderClick(folder) }, onRename = { onRenameFolder(folder) }, onDelete = { onDeleteFolder(folder) })
                        }
                    }
                    if (currentVideos.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Videos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        if (viewMode == ViewMode.GRID) {
                            items(currentVideos, key = { "video_${it.id}" }) { video ->
                                val history = playbackHistory.find { it.videoId == video.id }
                                val positionMillis = history?.positionMillis ?: 0L
                                val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                                    ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                val onMoveToFolderClick = LocalMoveToFolder.current

                                UnifiedVideoCard(
                                    title = video.title,
                                    channelName = video.channelName,
                                    thumbnailUrl = video.thumbnailUrl,
                                    durationSeconds = video.durationSeconds,
                                    uploadDate = video.uploadDate,
                                    isFavorite = video.favorite,
                                    isDownloaded = video.localFilePath.isNotEmpty(),
                                    isSelected = selectedIds.contains(video.id),
                                    playbackProgressFraction = progressFraction,
                                    folders = videoFolderMap[video.id] ?: emptyList(),
                                    config = VideoCardConfig(
                                        showFavoriteButton = !isSelectionMode,
                                        showMoveToFolderButton = !isSelectionMode,
                                        showRemoveFromFolderButton = !isSelectionMode && selectedFolder != null,
                                        showDownloadButton = !isSelectionMode,
                                        showSelectionCheckbox = isSelectionMode,
                                        showFolderBadges = true,
                                        showPlaybackProgress = true,
                                        showDownloadedBadge = true
                                    ),
                                    onClick = { if (isSelectionMode) onToggleSelection(video.id) else onVideoClick(video.id) },
                                    onLongClick = { onVideoLongClick(video.id) },
                                    onFavoriteToggle = { onFavoriteToggle(video) },
                                    onMoveToFolder = { onMoveToFolderClick(video.id) },
                                    onRemoveFromFolder = { selectedFolder?.let { onRemoveFromFolder(video.id, it.id) } },
                                    onDownloadClick = { onDownloadIconClick(video.id) },
                                    onSelectionToggle = { onToggleSelection(video.id) },
                                    downloadMenuContent = {
                                        QuickDownloadMenu(
                                            isExpanded = expandedDownloadVideoId == video.id,
                                            onDismiss = onDismissDownloadMenu,
                                            isFetching = fetchingStreamsFor == video.id,
                                            fetchedStreams = fetchedStreams,
                                            allDownloads = allDownloads,
                                            videoId = video.id,
                                            onEnqueueDownload = onEnqueueDownload,
                                            onDeleteDownload = onDeleteDownload,
                                            onExtractAudio = onExtractAudio
                                        )
                                    }
                                )
                            }
                        } else {
                            items(currentVideos, key = { "video_${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { video ->
                                val history = playbackHistory.find { it.videoId == video.id }
                                val positionMillis = history?.positionMillis ?: 0L
                                val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                                    ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                val onMoveToFolderClick = LocalMoveToFolder.current
                                val context = LocalContext.current

                                UnifiedVideoRow(
                                    title = video.title,
                                    channelName = video.channelName,
                                    thumbnailUrl = video.thumbnailUrl,
                                    durationSeconds = video.durationSeconds,
                                    uploadDate = video.uploadDate,
                                    isFavorite = video.favorite,
                                    isDownloaded = video.localFilePath.isNotEmpty(),
                                    isSelected = selectedIds.contains(video.id),
                                    playbackProgressFraction = progressFraction,
                                    folders = videoFolderMap[video.id] ?: emptyList(),
                                    config = VideoCardConfig(
                                        showFavoriteButton = !isSelectionMode,
                                        showMoveToFolderButton = !isSelectionMode,
                                        showRemoveFromFolderButton = !isSelectionMode && selectedFolder != null,
                                        showDownloadButton = !isSelectionMode,
                                        showSelectionCheckbox = isSelectionMode,
                                        showFolderBadges = true,
                                        showPlaybackProgress = true,
                                        showDownloadedBadge = true
                                    ),
                                    onClick = { if (isSelectionMode) onToggleSelection(video.id) else onVideoClick(video.id) },
                                    onLongClick = { onVideoLongClick(video.id) },
                                    onFavoriteToggle = { onFavoriteToggle(video) },
                                    onMoveToFolder = { onMoveToFolderClick(video.id) },
                                    onRemoveFromFolder = { selectedFolder?.let { onRemoveFromFolder(video.id, it.id) } },
                                    onDownloadClick = { onDownloadIconClick(video.id) },
                                    onSelectionToggle = { onToggleSelection(video.id) },
                                    downloadMenuContent = {
                                        QuickDownloadMenu(
                                            isExpanded = expandedDownloadVideoId == video.id,
                                            onDismiss = onDismissDownloadMenu,
                                            isFetching = fetchingStreamsFor == video.id,
                                            fetchedStreams = fetchedStreams,
                                            allDownloads = allDownloads,
                                            videoId = video.id,
                                            onEnqueueDownload = onEnqueueDownload,
                                            onDeleteDownload = onDeleteDownload,
                                            onExtractAudio = {
                                                onExtractAudio(it)
                                                android.widget.Toast.makeText(context, "Audio extraction started", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newFolderName = "" },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, placeholder = { Text("Folder name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { if (newFolderName.isNotBlank()) { onCreateFolder(newFolderName.trim()); showCreateDialog = false; newFolderName = "" } }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newFolderName = "" }) { Text("Cancel") }
            }
        )
    }
}



@Composable
private fun FolderRow(
    folder: FolderEntity,
    stats: FolderStats?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    GlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                val itemCount = stats?.itemCount ?: 0
                val sizeBytes = stats?.totalSizeBytes ?: 0L
                val sizeText = Formatter.formatShortFileSize(context, sizeBytes)
                Text(
                    text = "$itemCount ${if (itemCount == 1) "item" else "items"} • $sizeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRename) { Icon(Icons.Default.Edit, contentDescription = "Rename folder", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete folder", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

val LocalMoveToFolder = androidx.compose.runtime.staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


