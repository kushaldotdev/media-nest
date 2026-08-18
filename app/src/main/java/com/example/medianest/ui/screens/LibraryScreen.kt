package com.example.medianest.ui.screens

import android.text.format.Formatter
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.R
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.repository.FolderRepository
import com.example.medianest.data.repository.FolderTreeNode
import com.example.medianest.data.repository.flattenWithDepth
import com.example.medianest.ui.components.*
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestShapes
import com.example.medianest.ui.utils.UiUtils
import com.example.medianest.ui.viewmodel.FolderStats
import com.example.medianest.ui.viewmodel.LibraryTab
import com.example.medianest.ui.viewmodel.LibraryViewModel
import com.example.medianest.ui.viewmodel.MediaTypeFilter
import com.example.medianest.ui.viewmodel.SortCategory
import com.example.medianest.ui.viewmodel.SortDirection
import com.example.medianest.ui.viewmodel.ViewMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

val LocalMoveToFolder = androidx.compose.runtime.staticCompositionLocalOf<(String) -> Unit> { {} }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryFolderEntryPoint {
    fun folderRepository(): FolderRepository
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoClick: (String) -> Unit,
    onSubscriptionClick: (String, String) -> Unit = { _, _ -> },
    onNavigateToStatistics: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    var selectedSubscription by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (selectedSubscription != null) {
        InlineSubscriptionScreen(
            sourceType = selectedSubscription!!.first,
            sourceId = selectedSubscription!!.second,
            onBack = { selectedSubscription = null },
            onVideoClick = onVideoClick
        )
        return
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val favoriteVideos by viewModel.favoriteVideos.collectAsStateWithLifecycle()
    val watchedVideos by viewModel.watchedVideos.collectAsStateWithLifecycle()
    val folderVideos by viewModel.folderVideos.collectAsStateWithLifecycle()
    val rootFolders by viewModel.rootFolders.collectAsStateWithLifecycle()
    val childFolders by viewModel.childFolders.collectAsStateWithLifecycle()
    val videoFolderMap by viewModel.videoFolderMap.collectAsStateWithLifecycle()
    val folderStatsMap by viewModel.folderStatsMap.collectAsStateWithLifecycle()
    val playbackHistory by viewModel.playbackHistory.collectAsStateWithLifecycle()
    val historyLimit by viewModel.historyLimit.collectAsStateWithLifecycle()
    val favoritesLimit by viewModel.favoritesLimit.collectAsStateWithLifecycle()
    val watchedLimit by viewModel.watchedLimit.collectAsStateWithLifecycle()
    val folderVideosLimit by viewModel.folderVideosLimit.collectAsStateWithLifecycle()

    // Live counts for tabs
    val historyStats by viewModel.historyStats.collectAsStateWithLifecycle()
    val favoritesCount by viewModel.favoritesCount.collectAsStateWithLifecycle()
    val watchedCount by viewModel.watchedCount.collectAsStateWithLifecycle()
    val playlistsCount by viewModel.playlistsCount.collectAsStateWithLifecycle()
    val channelsCount by viewModel.channelsCount.collectAsStateWithLifecycle()

    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val fetchingStreamsFor by viewModel.fetchingStreamsFor.collectAsStateWithLifecycle()
    val fetchedStreams by viewModel.fetchedStreams.collectAsStateWithLifecycle()
    var expandedDownloadVideoId by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Move-to-Folder tree resolution
    val folderRepository = remember(context) {
        try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                LibraryFolderEntryPoint::class.java
            ).folderRepository()
        } catch (e: Exception) {
            null
        }
    }
    val folderTree by produceState<List<FolderTreeNode>>(initialValue = emptyList(), folderRepository) {
        folderRepository?.getAllFoldersTreeFlow()?.collect { value = it }
    }
    val flattenedFolders = remember(folderTree, rootFolders) {
        if (folderTree.isNotEmpty()) {
            folderTree.flattenWithDepth()
        } else {
            rootFolders.map { it to 0 }
        }
    }
    val allFoldersMap = remember(folderTree, rootFolders) {
        val map = mutableMapOf<Long, FolderEntity>()
        fun addNode(node: FolderTreeNode) {
            map[node.folder.id] = node.folder
            node.children.forEach { addNode(it) }
        }
        folderTree.forEach { addNode(it) }
        if (map.isEmpty()) {
            rootFolders.forEach { map[it.id] = it }
        }
        map
    }

    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    var singleVideoToMove by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var deleteDownloadsWithFolder by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var renameFolderName by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var deleteDownloadsWithBatch by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }

    var showWatchCountDialog by remember { mutableStateOf(false) }
    var watchCountTargetVideoId by remember { mutableStateOf<String?>(null) }
    var watchCountTargetTitle by remember { mutableStateOf("") }
    var watchCountTargetInitialCount by remember { mutableStateOf(0) }

    // Intercept back navigation
    BackHandler(enabled = uiState.isSelectionMode || uiState.selectedFolder != null || uiState.searchQuery.isNotEmpty()) {
        when {
            uiState.isSelectionMode -> viewModel.clearSelection()
            uiState.selectedFolder != null -> viewModel.navigateBackFromFolder()
            uiState.searchQuery.isNotEmpty() -> viewModel.setSearchQuery("")
        }
    }

    // Determine current video items for selection actions
    val currentVideosList = remember(uiState.currentTab, videos, favoriteVideos, watchedVideos, folderVideos) {
        when (uiState.currentTab) {
            LibraryTab.HISTORY -> videos
            LibraryTab.FAVORITES -> favoriteVideos
            LibraryTab.WATCHED -> watchedVideos
            LibraryTab.FOLDERS -> folderVideos
            else -> emptyList()
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalMoveToFolder provides { videoId ->
            singleVideoToMove = videoId
            showMoveToFolderDialog = true
        }
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    snackbar = { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MediaNestColors.Raised,
                            contentColor = MediaNestColors.TextPrimary,
                            actionColor = MediaNestColors.Accent,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                )
            },
            containerColor = MediaNestColors.Background,
            topBar = {
                MediaNestTopAppBar(
                    title = when {
                        uiState.isSelectionMode -> "${uiState.selectedVideoIds.size} Selected"
                        uiState.currentTab == LibraryTab.FOLDERS && uiState.selectedFolder != null -> uiState.selectedFolder!!.name
                        else -> "Collections"
                    },
                    subtitle = when {
                        uiState.isSelectionMode -> "Batch selection active"
                        uiState.currentTab == LibraryTab.FOLDERS && uiState.selectedFolder != null -> "Folder collection"
                        else -> null
                    },
                    navigationIcon = when {
                        uiState.isSelectionMode -> {
                            {
                                MediaNestIconButton(
                                    onClick = { viewModel.clearSelection() },
                                    contentDescription = "Cancel selection"
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_close),
                                        contentDescription = "Cancel selection",
                                        tint = MediaNestColors.TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        uiState.currentTab == LibraryTab.FOLDERS && uiState.selectedFolder != null -> {
                            {
                                MediaNestIconButton(
                                    onClick = { viewModel.navigateBackFromFolder() },
                                    contentDescription = "Back to folders"
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_back),
                                        contentDescription = "Back",
                                        tint = MediaNestColors.TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        else -> null
                    },
                    actions = {
                        if (!uiState.isSelectionMode) {
                            MediaNestIconButton(
                                onClick = onNavigateToStatistics,
                                contentDescription = "Statistics"
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_chart),
                                    contentDescription = "Statistics",
                                    tint = MediaNestColors.TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            MediaNestIconButton(
                                onClick = { viewModel.toggleViewMode() },
                                contentDescription = if (uiState.viewMode == ViewMode.GRID) "Switch to list view" else "Switch to grid view"
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (uiState.viewMode == ViewMode.GRID) R.drawable.ic_mn_list else R.drawable.ic_mn_grid
                                    ),
                                    contentDescription = null,
                                    tint = MediaNestColors.TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (uiState.isSelectionMode) {
                    BatchSelectionBar(
                        selectedCount = uiState.selectedVideoIds.size,
                        allVideoIds = currentVideosList.map { it.id },
                        selectedVideoIds = uiState.selectedVideoIds,
                        onSelectAll = { viewModel.selectAll(currentVideosList.map { it.id }) },
                        onClearSelection = { viewModel.clearSelection() },
                        onMove = { showMoveToFolderDialog = true },
                        onShare = { viewModel.shareSelectedVideos(context) },
                        onDelete = { showBatchDeleteDialog = true }
                    )
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
                // Collection Sub-Tabs Row with PillTabRow
                val tabs = listOf(
                    LibraryTab.HISTORY,
                    LibraryTab.WATCHED,
                    LibraryTab.FOLDERS,
                    LibraryTab.FAVORITES,
                    LibraryTab.PLAYLISTS,
                    LibraryTab.SUBSCRIPTIONS
                )

                PillTabRow(
                    items = tabs,
                    selected = uiState.currentTab,
                    onSelect = { tab -> viewModel.setTab(tab) },
                    label = { it.label },
                    iconRes = { tab ->
                        when (tab) {
                            LibraryTab.HISTORY -> R.drawable.ic_mn_history
                            LibraryTab.WATCHED -> R.drawable.ic_mn_watched
                            LibraryTab.FOLDERS -> R.drawable.ic_mn_folder
                            LibraryTab.FAVORITES -> R.drawable.ic_mn_heart
                            LibraryTab.PLAYLISTS -> R.drawable.ic_mn_playlist
                            LibraryTab.SUBSCRIPTIONS -> R.drawable.ic_mn_channel
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                )

                // Search Bar
                LibrarySearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholder = "Search ${uiState.currentTab.label.lowercase()}...",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                // Secondary Filter Controls Bar (Media Type & Sort for media tabs)
                val showSecondaryFilters = uiState.currentTab in listOf(
                    LibraryTab.HISTORY,
                    LibraryTab.FAVORITES,
                    LibraryTab.WATCHED
                ) || (uiState.currentTab == LibraryTab.FOLDERS && (uiState.selectedFolder != null || folderVideos.isNotEmpty()))

                if (showSecondaryFilters) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Media Type Segmented Pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                MediaTypeFilter.ALL,
                                MediaTypeFilter.VIDEO,
                                MediaTypeFilter.AUDIO
                            ).forEach { filter ->
                                val isFilterSelected = uiState.mediaTypeFilter == filter
                                MediaNestChip(
                                    label = filter.label,
                                    selected = isFilterSelected,
                                    onClick = { viewModel.setMediaTypeFilter(filter) },
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = when (filter) {
                                        MediaTypeFilter.VIDEO -> ({
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_video),
                                                contentDescription = null,
                                                tint = if (isFilterSelected) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        })
                                        MediaTypeFilter.AUDIO -> ({
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_music),
                                                contentDescription = null,
                                                tint = if (isFilterSelected) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        })
                                        else -> null
                                    }
                                )
                            }
                        }

                        // Sort Button
                        val sortLabel = when (uiState.sortCategory) {
                            SortCategory.DATE -> "Date"
                            SortCategory.NAME -> "Name"
                            SortCategory.DURATION -> "Duration"
                            SortCategory.SIZE -> "Size"
                        }
                        val sortDirectionSymbol = if (uiState.sortDirection == SortDirection.ASC) "↑" else "↓"

                        MediaNestChip(
                            label = "$sortLabel $sortDirectionSymbol",
                            selected = false,
                            onClick = { showSortBottomSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_sort),
                                    contentDescription = "Sort",
                                    tint = MediaNestColors.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }

                // Scoped Summary Metadata Line
                LibraryStatsLine(
                    tab = uiState.currentTab,
                    historyCount = historyStats.first,
                    historyWatchTimeMs = historyStats.second,
                    watchedCount = watchedCount,
                    favoritesCount = favoritesCount,
                    rootFoldersCount = rootFolders.size,
                    childFoldersCount = childFolders.size,
                    folderVideosCount = folderVideos.size,
                    selectedFolder = uiState.selectedFolder,
                    playlistsCount = playlistsCount,
                    channelsCount = channelsCount
                )

                // Active Folder Breadcrumb Navigation
                if (uiState.currentTab == LibraryTab.FOLDERS && uiState.selectedFolder != null) {
                    FolderBreadcrumbs(
                        stack = uiState.folderStack,
                        onCrumbClick = { index -> viewModel.navigateToFolderCrumb(index) },
                        onNavigateBack = { viewModel.navigateBackFromFolder() },
                        onCreateSubfolder = { showCreateFolderDialog = true }
                    )
                }

                // Main Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (uiState.currentTab) {
                        LibraryTab.HISTORY -> {
                            if (videos.isEmpty()) {
                                EmptyState(
                                    title = if (uiState.searchQuery.isNotEmpty()) "No Results Found" else "No Watch History",
                                    message = if (uiState.searchQuery.isNotEmpty()) "No history items matched \"${uiState.searchQuery}\"" else "Videos you stream or play will appear here for fast replay.",
                                    iconContent = {
                                        Icon(
                                            painter = painterResource(if (uiState.searchQuery.isNotEmpty()) R.drawable.ic_mn_search else R.drawable.ic_mn_history),
                                            contentDescription = null,
                                            tint = MediaNestColors.TextSecondary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    },
                                    actionText = if (uiState.searchQuery.isNotEmpty()) "Clear Search" else null,
                                    onActionClick = if (uiState.searchQuery.isNotEmpty()) { { viewModel.setSearchQuery("") } } else null
                                )
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
                                    showContinueWatching = true,
                                    isEndReached = videos.isNotEmpty() && videos.size < historyLimit,
                                    onVideoClick = onVideoClick,
                                    onVideoLongClick = { videoId ->
                                        if (!uiState.isSelectionMode) {
                                            viewModel.toggleSelectionMode()
                                        }
                                        viewModel.toggleVideoSelection(videoId)
                                    },
                                    onToggleSelection = { viewModel.toggleVideoSelection(it) },
                                    onFavoriteToggle = { video ->
                                        viewModel.toggleFavorite(video.id, video.favorite)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(if (video.favorite) "Removed from Favorites" else "Added to Favorites")
                                        }
                                    },
                                    onDownloadIconClick = { videoId ->
                                        expandedDownloadVideoId = videoId
                                        viewModel.fetchStreamsFor(videoId)
                                    },
                                    onDismissDownloadMenu = { expandedDownloadVideoId = null },
                                    onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                                    onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                                    onExtractAudio = { entity ->
                                        viewModel.extractAudio(entity)
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Audio extraction queued") }
                                    },
                                    onLoadMore = viewModel::loadMoreHistory,
                                    onMarkWatched = { videoId ->
                                        val video = videos.find { it.id == videoId }
                                        if (video != null) {
                                            watchCountTargetVideoId = videoId
                                            watchCountTargetTitle = video.title
                                            watchCountTargetInitialCount = video.watchCount
                                            showWatchCountDialog = true
                                        }
                                    }
                                )
                            }
                        }

                        LibraryTab.FAVORITES -> {
                            if (favoriteVideos.isEmpty()) {
                                EmptyState(
                                    title = if (uiState.searchQuery.isNotEmpty()) "No Results Found" else "No Favorites Yet",
                                    message = if (uiState.searchQuery.isNotEmpty()) "No favorite items matched \"${uiState.searchQuery}\"" else "Tap the heart icon on any media item to save it here.",
                                    iconContent = {
                                        Icon(
                                            painter = painterResource(if (uiState.searchQuery.isNotEmpty()) R.drawable.ic_mn_search else R.drawable.ic_mn_heart),
                                            contentDescription = null,
                                            tint = MediaNestColors.TextSecondary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    },
                                    actionText = if (uiState.searchQuery.isNotEmpty()) "Clear Search" else null,
                                    onActionClick = if (uiState.searchQuery.isNotEmpty()) { { viewModel.setSearchQuery("") } } else null
                                )
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
                                    isEndReached = favoriteVideos.isNotEmpty() && favoriteVideos.size < favoritesLimit,
                                    onVideoClick = onVideoClick,
                                    onVideoLongClick = { videoId ->
                                        if (!uiState.isSelectionMode) {
                                            viewModel.toggleSelectionMode()
                                        }
                                        viewModel.toggleVideoSelection(videoId)
                                    },
                                    onToggleSelection = { viewModel.toggleVideoSelection(it) },
                                    onFavoriteToggle = { video ->
                                        viewModel.toggleFavorite(video.id, video.favorite)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(if (video.favorite) "Removed from Favorites" else "Added to Favorites")
                                        }
                                    },
                                    onDownloadIconClick = { videoId ->
                                        expandedDownloadVideoId = videoId
                                        viewModel.fetchStreamsFor(videoId)
                                    },
                                    onDismissDownloadMenu = { expandedDownloadVideoId = null },
                                    onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                                    onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                                    onExtractAudio = { entity ->
                                        viewModel.extractAudio(entity)
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Audio extraction queued") }
                                    },
                                    onLoadMore = viewModel::loadMoreFavorites,
                                    onMarkWatched = { videoId ->
                                        val video = favoriteVideos.find { it.id == videoId }
                                        if (video != null) {
                                            watchCountTargetVideoId = videoId
                                            watchCountTargetTitle = video.title
                                            watchCountTargetInitialCount = video.watchCount
                                            showWatchCountDialog = true
                                        }
                                    }
                                )
                            }
                        }

                        LibraryTab.WATCHED -> {
                            if (watchedVideos.isEmpty()) {
                                EmptyState(
                                    title = if (uiState.searchQuery.isNotEmpty()) "No Results Found" else "No Watched Videos",
                                    message = if (uiState.searchQuery.isNotEmpty()) "No watched items matched \"${uiState.searchQuery}\"" else "Completed and marked videos will be archived here.",
                                    iconContent = {
                                        Icon(
                                            painter = painterResource(if (uiState.searchQuery.isNotEmpty()) R.drawable.ic_mn_search else R.drawable.ic_mn_watched),
                                            contentDescription = null,
                                            tint = MediaNestColors.TextSecondary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    },
                                    actionText = if (uiState.searchQuery.isNotEmpty()) "Clear Search" else null,
                                    onActionClick = if (uiState.searchQuery.isNotEmpty()) { { viewModel.setSearchQuery("") } } else null
                                )
                            } else {
                                VideoListLayout(
                                    videos = watchedVideos,
                                    videoFolderMap = videoFolderMap,
                                    viewMode = uiState.viewMode,
                                    isSelectionMode = uiState.isSelectionMode,
                                    selectedIds = uiState.selectedVideoIds,
                                    expandedDownloadVideoId = expandedDownloadVideoId,
                                    fetchingStreamsFor = fetchingStreamsFor,
                                    fetchedStreams = fetchedStreams,
                                    allDownloads = allDownloads,
                                    playbackHistory = playbackHistory,
                                    isEndReached = watchedVideos.isNotEmpty() && watchedVideos.size < watchedLimit,
                                    onVideoClick = onVideoClick,
                                    onVideoLongClick = { videoId ->
                                        if (!uiState.isSelectionMode) {
                                            viewModel.toggleSelectionMode()
                                        }
                                        viewModel.toggleVideoSelection(videoId)
                                    },
                                    onToggleSelection = { viewModel.toggleVideoSelection(it) },
                                    onFavoriteToggle = { video ->
                                        viewModel.toggleFavorite(video.id, video.favorite)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(if (video.favorite) "Removed from Favorites" else "Added to Favorites")
                                        }
                                    },
                                    onDownloadIconClick = { videoId ->
                                        expandedDownloadVideoId = videoId
                                        viewModel.fetchStreamsFor(videoId)
                                    },
                                    onDismissDownloadMenu = { expandedDownloadVideoId = null },
                                    onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                                    onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                                    onExtractAudio = { entity ->
                                        viewModel.extractAudio(entity)
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Audio extraction queued") }
                                    },
                                    onLoadMore = viewModel::loadMoreWatched,
                                    onMarkWatched = { videoId ->
                                        val video = watchedVideos.find { it.id == videoId }
                                        if (video != null) {
                                            watchCountTargetVideoId = videoId
                                            watchCountTargetTitle = video.title
                                            watchCountTargetInitialCount = video.watchCount
                                            showWatchCountDialog = true
                                        }
                                    }
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
                                isEndReached = folderVideos.isNotEmpty() && folderVideos.size < folderVideosLimit,
                                onFolderClick = { viewModel.selectFolder(it) },
                                onCreateFolderClick = { showCreateFolderDialog = true },
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
                                onVideoLongClick = { videoId ->
                                    if (!uiState.isSelectionMode) {
                                        viewModel.toggleSelectionMode()
                                    }
                                    viewModel.toggleVideoSelection(videoId)
                                },
                                onToggleSelection = { viewModel.toggleVideoSelection(it) },
                                onFavoriteToggle = { video ->
                                    viewModel.toggleFavorite(video.id, video.favorite)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(if (video.favorite) "Removed from Favorites" else "Added to Favorites")
                                    }
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
                                onExtractAudio = { entity ->
                                    viewModel.extractAudio(entity)
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Audio extraction queued") }
                                },
                                onLoadMoreVideos = viewModel::loadMoreFolderVideos,
                                onMarkWatched = { videoId ->
                                    val video = folderVideos.find { it.id == videoId }
                                    if (video != null) {
                                        watchCountTargetVideoId = videoId
                                        watchCountTargetTitle = video.title
                                        watchCountTargetInitialCount = video.watchCount
                                        showWatchCountDialog = true
                                    }
                                }
                            )
                        }

                        LibraryTab.PLAYLISTS -> {
                            SubscriptionsScreen(
                                sourceType = "playlist",
                                searchQuery = uiState.searchQuery,
                                viewMode = uiState.viewMode,
                                onSubscriptionClick = { type, id ->
                                    selectedSubscription = Pair(type, id)
                                    onSubscriptionClick(type, id)
                                }
                            )
                        }

                        LibraryTab.SUBSCRIPTIONS -> {
                            SubscriptionsScreen(
                                sourceType = "channel",
                                searchQuery = uiState.searchQuery,
                                viewMode = uiState.viewMode,
                                onSubscriptionClick = { type, id ->
                                    selectedSubscription = Pair(type, id)
                                    onSubscriptionClick(type, id)
                                }
                            )
                        }
                    }
                }
            }

            // Move To Folder Dialog
            if (showMoveToFolderDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showMoveToFolderDialog = false
                        singleVideoToMove = null
                    },
                    containerColor = MediaNestColors.Raised,
                    titleContentColor = MediaNestColors.TextPrimary,
                    textContentColor = MediaNestColors.TextSecondary,
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_move),
                                contentDescription = null,
                                tint = MediaNestColors.Accent,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Move to Folder",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MediaNestColors.TextPrimary
                                )
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                        ) {
                            if (flattenedFolders.isEmpty()) {
                                Text(
                                    text = "No folders available. Create a folder first.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MediaNestColors.TextSecondary,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                fun getFolderPath(folder: FolderEntity): String {
                                    val parts = mutableListOf<String>()
                                    var curr: FolderEntity? = folder
                                    val visited = mutableSetOf<Long>()
                                    while (curr != null && visited.add(curr.id)) {
                                        parts.add(0, curr.name)
                                        curr = curr.parentId?.let { allFoldersMap[it] }
                                    }
                                    return parts.joinToString(" / ")
                                }

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(flattenedFolders, key = { it.first.id }) { (folder, depth) ->
                                        val stats = folderStatsMap[folder.id]
                                        val itemCount = stats?.itemCount ?: 0
                                        val fullPath = getFolderPath(folder)
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(MediaNestShapes.Card)
                                                .clickable {
                                                    if (singleVideoToMove != null) {
                                                        viewModel.moveVideoToFolder(singleVideoToMove!!, folder.id)
                                                        singleVideoToMove = null
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Moved to ${folder.name}")
                                                        }
                                                    } else {
                                                        val count = uiState.selectedVideoIds.size
                                                        viewModel.moveSelectedToFolder(folder.id)
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Moved $count items to ${folder.name}")
                                                        }
                                                    }
                                                    showMoveToFolderDialog = false
                                                },
                                            shape = MediaNestShapes.Card,
                                            color = MediaNestColors.Card,
                                            border = BorderStroke(1.dp, MediaNestColors.Border)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        start = 12.dp + (depth * 16).dp,
                                                        end = 12.dp,
                                                        top = 10.dp,
                                                        bottom = 10.dp
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_mn_folder),
                                                    contentDescription = null,
                                                    tint = MediaNestColors.Accent,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = folder.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Medium,
                                                            color = MediaNestColors.TextPrimary
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (depth > 0) {
                                                        Text(
                                                            text = fullPath,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 11.sp,
                                                                color = MediaNestColors.TextSecondary
                                                            ),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "$itemCount ${if (itemCount == 1) "item" else "items"}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MediaNestColors.TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        MediaNestButton(
                            text = "Cancel",
                            onClick = {
                                showMoveToFolderDialog = false
                                singleVideoToMove = null
                            },
                            variant = MediaNestButtonVariant.Ghost,
                            size = MediaNestButtonSize.Small
                        )
                    }
                )
            }

            // Delete Folder Dialog
            folderToDelete?.let { folder ->
                AlertDialog(
                    onDismissRequest = { folderToDelete = null },
                    containerColor = MediaNestColors.Raised,
                    titleContentColor = MediaNestColors.TextPrimary,
                    textContentColor = MediaNestColors.TextSecondary,
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_trash),
                                contentDescription = null,
                                tint = MediaNestColors.Destructive,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Delete Folder?",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MediaNestColors.TextPrimary
                                )
                            )
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Are you sure you want to delete folder '${folder.name}'? This cannot be undone.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MediaNestColors.TextSecondary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { deleteDownloadsWithFolder = !deleteDownloadsWithFolder }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = deleteDownloadsWithFolder,
                                    onCheckedChange = { deleteDownloadsWithFolder = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MediaNestColors.Destructive,
                                        uncheckedColor = MediaNestColors.Border,
                                        checkmarkColor = MediaNestColors.TextPrimary
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Delete downloaded videos in this folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediaNestColors.TextPrimary
                                )
                            }
                        }
                    },
                    confirmButton = {
                        MediaNestButton(
                            text = "Delete",
                            onClick = {
                                viewModel.deleteFolder(folder, deleteDownloadsWithFolder)
                                folderToDelete = null
                                coroutineScope.launch { snackbarHostState.showSnackbar("Folder deleted") }
                            },
                            variant = MediaNestButtonVariant.DangerSolid,
                            size = MediaNestButtonSize.Small
                        )
                    },
                    dismissButton = {
                        MediaNestButton(
                            text = "Cancel",
                            onClick = { folderToDelete = null },
                            variant = MediaNestButtonVariant.Ghost,
                            size = MediaNestButtonSize.Small
                        )
                    }
                )
            }

            // Rename Folder Dialog
            folderToRename?.let { folder ->
                AlertDialog(
                    onDismissRequest = { folderToRename = null },
                    containerColor = MediaNestColors.Raised,
                    titleContentColor = MediaNestColors.TextPrimary,
                    textContentColor = MediaNestColors.TextSecondary,
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_edit),
                                contentDescription = null,
                                tint = MediaNestColors.Accent,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Rename Folder",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MediaNestColors.TextPrimary
                                )
                            )
                        }
                    },
                    text = {
                        OutlinedTextField(
                            value = renameFolderName,
                            onValueChange = { renameFolderName = it },
                            placeholder = { Text("New folder name", color = MediaNestColors.TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MediaNestColors.Accent,
                                unfocusedBorderColor = MediaNestColors.Border,
                                focusedTextColor = MediaNestColors.TextPrimary,
                                unfocusedTextColor = MediaNestColors.TextPrimary,
                                cursorColor = MediaNestColors.Accent,
                                focusedContainerColor = MediaNestColors.Card,
                                unfocusedContainerColor = MediaNestColors.Card
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        MediaNestButton(
                            text = "Rename",
                            onClick = {
                                if (renameFolderName.isNotBlank()) {
                                    viewModel.renameFolder(folder.id, renameFolderName.trim())
                                    folderToRename = null
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Folder renamed") }
                                }
                            },
                            variant = MediaNestButtonVariant.Primary,
                            size = MediaNestButtonSize.Small,
                            enabled = renameFolderName.isNotBlank()
                        )
                    },
                    dismissButton = {
                        MediaNestButton(
                            text = "Cancel",
                            onClick = { folderToRename = null },
                            variant = MediaNestButtonVariant.Ghost,
                            size = MediaNestButtonSize.Small
                        )
                    }
                )
            }

            // Create Folder Dialog
            if (showCreateFolderDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showCreateFolderDialog = false
                        newFolderName = ""
                    },
                    containerColor = MediaNestColors.Raised,
                    titleContentColor = MediaNestColors.TextPrimary,
                    textContentColor = MediaNestColors.TextSecondary,
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_folder_add),
                                contentDescription = null,
                                tint = MediaNestColors.Accent,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (uiState.selectedFolder != null) "New Subfolder" else "New Folder",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MediaNestColors.TextPrimary
                                )
                            )
                        }
                    },
                    text = {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            placeholder = { Text("Folder name", color = MediaNestColors.TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MediaNestColors.Accent,
                                unfocusedBorderColor = MediaNestColors.Border,
                                focusedTextColor = MediaNestColors.TextPrimary,
                                unfocusedTextColor = MediaNestColors.TextPrimary,
                                cursorColor = MediaNestColors.Accent,
                                focusedContainerColor = MediaNestColors.Card,
                                unfocusedContainerColor = MediaNestColors.Card
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        MediaNestButton(
                            text = "Create",
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    viewModel.createFolder(newFolderName.trim(), uiState.selectedFolder?.id)
                                    showCreateFolderDialog = false
                                    newFolderName = ""
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Folder created") }
                                }
                            },
                            variant = MediaNestButtonVariant.Primary,
                            size = MediaNestButtonSize.Small,
                            enabled = newFolderName.isNotBlank()
                        )
                    },
                    dismissButton = {
                        MediaNestButton(
                            text = "Cancel",
                            onClick = {
                                showCreateFolderDialog = false
                                newFolderName = ""
                            },
                            variant = MediaNestButtonVariant.Ghost,
                            size = MediaNestButtonSize.Small
                        )
                    }
                )
            }

            // Batch Delete Dialog
            if (showBatchDeleteDialog) {
                val count = uiState.selectedVideoIds.size
                AlertDialog(
                    onDismissRequest = { showBatchDeleteDialog = false },
                    containerColor = MediaNestColors.Raised,
                    titleContentColor = MediaNestColors.TextPrimary,
                    textContentColor = MediaNestColors.TextSecondary,
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_trash),
                                contentDescription = null,
                                tint = MediaNestColors.Destructive,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Delete $count ${if (count == 1) "Video" else "Videos"}?",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MediaNestColors.TextPrimary
                                )
                            )
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Are you sure you want to remove the selected videos from your library?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MediaNestColors.TextSecondary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { deleteDownloadsWithBatch = !deleteDownloadsWithBatch }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = deleteDownloadsWithBatch,
                                    onCheckedChange = { deleteDownloadsWithBatch = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MediaNestColors.Destructive,
                                        uncheckedColor = MediaNestColors.Border,
                                        checkmarkColor = MediaNestColors.TextPrimary
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Delete downloaded files from storage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediaNestColors.TextPrimary
                                )
                            }
                        }
                    },
                    confirmButton = {
                        MediaNestButton(
                            text = "Delete",
                            onClick = {
                                viewModel.deleteSelectedVideos(deleteDownloadsWithBatch)
                                showBatchDeleteDialog = false
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Deleted $count ${if (count == 1) "video" else "videos"}")
                                }
                            },
                            variant = MediaNestButtonVariant.DangerSolid,
                            size = MediaNestButtonSize.Small
                        )
                    },
                    dismissButton = {
                        MediaNestButton(
                            text = "Cancel",
                            onClick = { showBatchDeleteDialog = false },
                            variant = MediaNestButtonVariant.Ghost,
                            size = MediaNestButtonSize.Small
                        )
                    }
                )
            }

            // Sort Bottom Sheet
            if (showSortBottomSheet) {
                MediaNestSortBottomSheet(
                    onDismissRequest = { showSortBottomSheet = false },
                    selectedSortBy = when (uiState.sortCategory) {
                        SortCategory.DATE -> "DATE"
                        SortCategory.NAME -> "TITLE"
                        SortCategory.DURATION -> "DURATION"
                        SortCategory.SIZE -> "SIZE"
                    },
                    isAscending = uiState.sortDirection == SortDirection.ASC,
                    onSortSelected = { sortBy, isAscending ->
                        val cat = when (sortBy.uppercase()) {
                            "TITLE", "NAME" -> SortCategory.NAME
                            "DURATION" -> SortCategory.DURATION
                            "SIZE" -> SortCategory.SIZE
                            else -> SortCategory.DATE
                        }
                        val dir = if (isAscending) SortDirection.ASC else SortDirection.DESC
                        viewModel.setSort(cat, dir)
                    },
                    options = MediaNestSortOption.DefaultMediaOptions
                )
            }
        }
    }

    if (showWatchCountDialog) {
        WatchCountDialog(
            videoTitle = watchCountTargetTitle,
            initialCount = watchCountTargetInitialCount,
            onDismiss = { showWatchCountDialog = false },
            onConfirm = { newCount ->
                watchCountTargetVideoId?.let { videoId ->
                    viewModel.updateWatchCount(videoId, newCount)
                }
            }
        )
    }
}

/**
 * Design 2.0 Pill Search Bar for MediaNest Library.
 */
@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search videos, folders, channels...",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = MediaNestColors.Card,
        border = BorderStroke(1.dp, MediaNestColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mn_search),
                contentDescription = "Search",
                tint = MediaNestColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            color = MediaNestColors.TextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        color = MediaNestColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(MediaNestColors.Accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                MediaNestIconButton(
                    onClick = { onQueryChange("") },
                    size = MediaNestIconButtonSize.ExtraSmall,
                    contentDescription = "Clear search"
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_close),
                        contentDescription = "Clear search",
                        tint = MediaNestColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Bottom Action Bar for multi-item selection actions.
 */
@Composable
private fun BatchSelectionBar(
    selectedCount: Int,
    allVideoIds: List<String>,
    selectedVideoIds: Set<String>,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAllSelected = allVideoIds.isNotEmpty() && selectedVideoIds.containsAll(allVideoIds)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MediaNestColors.Raised,
        border = BorderStroke(1.dp, MediaNestColors.Border),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Count and Select All toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MediaNestColors.TextPrimary
                    )
                )

                MediaNestChip(
                    label = if (isAllSelected) "Deselect" else "Select All",
                    selected = isAllSelected,
                    onClick = {
                        if (isAllSelected) {
                            onClearSelection()
                        } else {
                            onSelectAll()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move Button
                MediaNestButton(
                    text = "Move",
                    onClick = onMove,
                    variant = MediaNestButtonVariant.Secondary,
                    size = MediaNestButtonSize.Small,
                    enabled = selectedCount > 0,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_move),
                            contentDescription = "Move",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )

                // Share Button
                MediaNestIconButton(
                    onClick = onShare,
                    size = MediaNestIconButtonSize.Small,
                    enabled = selectedCount > 0,
                    contentDescription = "Share selected"
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_share),
                        contentDescription = "Share",
                        tint = if (selectedCount > 0) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Delete Button
                MediaNestIconButton(
                    onClick = onDelete,
                    size = MediaNestIconButtonSize.Small,
                    enabled = selectedCount > 0,
                    tint = MediaNestColors.Destructive,
                    contentDescription = "Delete selected"
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_trash),
                        contentDescription = "Delete",
                        tint = if (selectedCount > 0) MediaNestColors.Destructive else MediaNestColors.Destructive.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Close / Cancel Button
                MediaNestIconButton(
                    onClick = onClearSelection,
                    size = MediaNestIconButtonSize.Small,
                    contentDescription = "Cancel selection"
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_close),
                        contentDescription = "Cancel",
                        tint = MediaNestColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Breadcrumbs path bar when navigating through folders.
 */
@Composable
private fun FolderBreadcrumbs(
    stack: List<FolderEntity>,
    onCrumbClick: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onCreateSubfolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaNestIconButton(
            onClick = onNavigateBack,
            size = MediaNestIconButtonSize.Small,
            contentDescription = "Back"
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mn_back),
                contentDescription = "Back",
                tint = MediaNestColors.TextPrimary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.width(6.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Folders",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (stack.isEmpty()) FontWeight.Bold else FontWeight.Medium,
                    color = if (stack.isEmpty()) MediaNestColors.Accent else MediaNestColors.TextSecondary
                ),
                modifier = Modifier.clickable { onCrumbClick(-1) }
            )

            stack.forEachIndexed { index, folder ->
                Icon(
                    painter = painterResource(R.drawable.ic_mn_chevron_right),
                    contentDescription = null,
                    tint = MediaNestColors.TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(12.dp)
                )

                val isLast = index == stack.size - 1
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                        color = if (isLast) MediaNestColors.Accent else MediaNestColors.TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onCrumbClick(index) }
                )
            }
        }

        MediaNestIconButton(
            onClick = onCreateSubfolder,
            size = MediaNestIconButtonSize.Small,
            contentDescription = "New subfolder"
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mn_folder_add),
                contentDescription = "New subfolder",
                tint = MediaNestColors.Accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private data class ContinueWatchingItem(
    val video: VideoEntity,
    val positionMillis: Long,
    val progressFraction: Float
)

@Composable
private fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mn_history),
                contentDescription = null,
                tint = MediaNestColors.Accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = MediaNestColors.TextPrimary
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            items(items, key = { "cw_${it.video.id}" }) { item ->
                ContinueWatchingCard(
                    video = item.video,
                    positionMillis = item.positionMillis,
                    progressFraction = item.progressFraction,
                    onClick = { onVideoClick(item.video.id) }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    video: VideoEntity,
    positionMillis: Long,
    progressFraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val posSeconds = positionMillis / 1000
    val posLabel = if (posSeconds > 0) "Left off at ${UiUtils.formatDuration(posSeconds)}" else ""
    val isAudio = video.mediaType.equals("AUDIO", ignoreCase = true)

    GlassCard(
        onClick = onClick,
        modifier = modifier
            .width(220.dp)
            .clip(MediaNestShapes.Card),
        shape = MediaNestShapes.Card
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Media type badge - top start
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(
                            color = if (isAudio) MediaNestColors.AccentDeep.copy(alpha = 0.85f) else MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        painter = painterResource(if (isAudio) R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                        contentDescription = if (isAudio) "AUDIO" else "VIDEO",
                        tint = MediaNestColors.TextPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }

                // Play icon in center
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp)
                        .background(
                            color = MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_play),
                        contentDescription = "Play",
                        tint = MediaNestColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Duration badge - bottom right
                if (video.durationSeconds > 0) {
                    Text(
                        text = UiUtils.formatDuration(video.durationSeconds),
                        color = MediaNestColors.TextPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(
                                color = MediaNestColors.PlayerSurface.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Playback progress bar at bottom edge
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(MediaNestColors.ProgressTrack.copy(alpha = 0.4f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(MediaNestColors.YouTubeRed)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = MediaNestColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                val subtitle = buildString {
                    if (video.channelName.isNotEmpty()) {
                        append(video.channelName)
                    }
                    if (posLabel.isNotEmpty()) {
                        if (isNotEmpty()) append(" • ")
                        append(posLabel)
                    }
                }

                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp
                        ),
                        color = MediaNestColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
    fetchedStreams: ExtractedVideoInfo?,
    allDownloads: List<DownloadEntity>,
    playbackHistory: List<HistoryEntity>,
    onDownloadIconClick: (String) -> Unit,
    onDismissDownloadMenu: () -> Unit,
    onEnqueueDownload: (ExtractedVideoInfo, StreamSource) -> Unit,
    onDeleteDownload: (DownloadEntity) -> Unit,
    onExtractAudio: (DownloadEntity) -> Unit,
    onLoadMore: (() -> Unit)? = null,
    onMarkWatched: (String) -> Unit = {},
    showContinueWatching: Boolean = false,
    isEndReached: Boolean = false
) {
    val onMoveToFolderClick = LocalMoveToFolder.current

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val continueWatchingList = remember(videos, playbackHistory, showContinueWatching) {
        if (!showContinueWatching) {
            emptyList()
        } else {
            videos.mapNotNull { video ->
                val history = playbackHistory.find { it.videoId == video.id }
                val positionMillis = history?.positionMillis ?: 0L
                val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                    ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                } else 0f

                if (progressFraction > 0f && progressFraction < 0.95f) {
                    ContinueWatchingItem(video, positionMillis, progressFraction)
                } else {
                    null
                }
            }
        }
    }

    if (onLoadMore != null) {
        val shouldLoadMoreGrid = remember {
            derivedStateOf {
                val layoutInfo = gridState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleItem >= totalItems - 5 && totalItems > 0
            }
        }
        val shouldLoadMoreList = remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleItem >= totalItems - 5 && totalItems > 0
            }
        }
        LaunchedEffect(shouldLoadMoreGrid.value) {
            if (shouldLoadMoreGrid.value) {
                onLoadMore()
            }
        }
        LaunchedEffect(shouldLoadMoreList.value) {
            if (shouldLoadMoreList.value) {
                onLoadMore()
            }
        }
    }

    if (viewMode == ViewMode.GRID) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (continueWatchingList.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueWatchingRow(
                        items = continueWatchingList,
                        onVideoClick = onVideoClick
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "All Videos",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = MediaNestColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }
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
                    isDownloaded = video.localFilePath.isNotEmpty() && File(video.localFilePath).exists(),
                    isSelected = selectedIds.contains(video.id),
                    playbackProgressFraction = progressFraction,
                    watchCount = video.watchCount,
                    folders = videoFolderMap[video.id] ?: emptyList(),
                    mediaType = video.mediaType,
                    config = VideoCardConfig(
                        showFavoriteButton = !isSelectionMode,
                        showMoveToFolderButton = !isSelectionMode,
                        showDownloadButton = !isSelectionMode,
                        showSelectionCheckbox = isSelectionMode,
                        showFolderBadges = true,
                        showPlaybackProgress = true,
                        showDownloadedBadge = true,
                        showMarkWatchedButton = !isSelectionMode,
                        showMediaTypeBadge = true
                    ),
                    onClick = { if (isSelectionMode) onToggleSelection(video.id) else onVideoClick(video.id) },
                    onLongClick = { onVideoLongClick(video.id) },
                    onFavoriteToggle = { onFavoriteToggle(video) },
                    onMoveToFolder = { onMoveToFolderClick(video.id) },
                    onDownloadClick = { onDownloadIconClick(video.id) },
                    onMarkWatched = { onMarkWatched(video.id) },
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
            if (isEndReached) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EndOfListIndicator()
                }
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (continueWatchingList.isNotEmpty()) {
                item {
                    ContinueWatchingRow(
                        items = continueWatchingList,
                        onVideoClick = onVideoClick
                    )
                }
                item {
                    Text(
                        text = "All Videos",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = MediaNestColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }
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
                    isDownloaded = video.localFilePath.isNotEmpty() && File(video.localFilePath).exists(),
                    isSelected = selectedIds.contains(video.id),
                    playbackProgressFraction = progressFraction,
                    watchCount = video.watchCount,
                    folders = videoFolderMap[video.id] ?: emptyList(),
                    mediaType = video.mediaType,
                    config = VideoCardConfig(
                        showFavoriteButton = !isSelectionMode,
                        showMoveToFolderButton = !isSelectionMode,
                        showDownloadButton = !isSelectionMode,
                        showSelectionCheckbox = isSelectionMode,
                        showFolderBadges = true,
                        showPlaybackProgress = true,
                        showDownloadedBadge = true,
                        showMarkWatchedButton = !isSelectionMode,
                        showMediaTypeBadge = true
                    ),
                    onClick = { if (isSelectionMode) onToggleSelection(video.id) else onVideoClick(video.id) },
                    onLongClick = { onVideoLongClick(video.id) },
                    onFavoriteToggle = { onFavoriteToggle(video) },
                    onMoveToFolder = { onMoveToFolderClick(video.id) },
                    onDownloadClick = { onDownloadIconClick(video.id) },
                    onMarkWatched = { onMarkWatched(video.id) },
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
            if (isEndReached) {
                item {
                    EndOfListIndicator()
                }
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
    onCreateFolderClick: () -> Unit,
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
    fetchedStreams: ExtractedVideoInfo?,
    allDownloads: List<DownloadEntity>,
    playbackHistory: List<HistoryEntity>,
    onDownloadIconClick: (String) -> Unit,
    onDismissDownloadMenu: () -> Unit,
    onEnqueueDownload: (ExtractedVideoInfo, StreamSource) -> Unit,
    onDeleteDownload: (DownloadEntity) -> Unit,
    onExtractAudio: (DownloadEntity) -> Unit,
    onLoadMoreVideos: (() -> Unit)? = null,
    onMarkWatched: (String) -> Unit = {},
    isEndReached: Boolean = false
) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    if (onLoadMoreVideos != null) {
        val shouldLoadMore = remember {
            derivedStateOf {
                val layoutInfo = gridState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleItem >= totalItems - 5 && totalItems > 0
            }
        }
        LaunchedEffect(shouldLoadMore.value) {
            if (shouldLoadMore.value) {
                onLoadMoreVideos()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        // Header when at root folder level
        if (selectedFolder == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${folders.size} ${if (folders.size == 1) "Folder" else "Folders"}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MediaNestColors.TextSecondary
                    )
                )

                MediaNestButton(
                    text = "New Folder",
                    onClick = onCreateFolderClick,
                    variant = MediaNestButtonVariant.Primary,
                    size = MediaNestButtonSize.Small,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_folder_add),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                )
            }
        }

        if (selectedFolder == null && searchQuery.isEmpty()) {
            if (folders.isEmpty()) {
                EmptyState(
                    title = "No Folders Yet",
                    message = "Create folders to organize videos into custom offline or online collections.",
                    iconContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_folder),
                            contentDescription = null,
                            tint = MediaNestColors.TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    actionText = "Create Folder",
                    onActionClick = onCreateFolderClick
                )
            } else {
                if (viewMode == ViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folders, key = { it.id }) { folder ->
                            FolderCard(
                                folder = folder,
                                stats = folderStatsMap[folder.id],
                                onClick = { onFolderClick(folder) },
                                onRename = { onRenameFolder(folder) },
                                onDelete = { onDeleteFolder(folder) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folders, key = { it.id }) { folder ->
                            FolderRow(
                                folder = folder,
                                stats = folderStatsMap[folder.id],
                                onClick = { onFolderClick(folder) },
                                onRename = { onRenameFolder(folder) },
                                onDelete = { onDeleteFolder(folder) }
                            )
                        }
                    }
                }
            }
        } else {
            val currentFolders = if (selectedFolder == null) folders else childFolders
            val currentVideos = folderVideos

            if (currentFolders.isEmpty() && currentVideos.isEmpty()) {
                EmptyState(
                    title = if (searchQuery.isNotEmpty()) "No Results Found" else "Folder is Empty",
                    message = if (searchQuery.isNotEmpty()) "No items matching \"$searchQuery\" in this folder." else "Add videos to this folder from the video action menus.",
                    iconContent = {
                        Icon(
                            painter = painterResource(if (searchQuery.isNotEmpty()) R.drawable.ic_mn_search else R.drawable.ic_mn_folder),
                            contentDescription = null,
                            tint = MediaNestColors.TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                )
            } else {
                if (viewMode == ViewMode.GRID) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (currentFolders.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "Folders" else "Subfolders",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MediaNestColors.TextPrimary
                                    ),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(currentFolders, key = { "folder_${it.id}" }) { folder ->
                                FolderCard(
                                    folder = folder,
                                    stats = folderStatsMap[folder.id],
                                    onClick = { onFolderClick(folder) },
                                    onRename = { onRenameFolder(folder) },
                                    onDelete = { onDeleteFolder(folder) }
                                )
                            }
                        }

                        if (currentVideos.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "Videos (${currentVideos.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MediaNestColors.TextPrimary
                                    ),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
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
                                    isDownloaded = video.localFilePath.isNotEmpty() && File(video.localFilePath).exists(),
                                    isSelected = selectedIds.contains(video.id),
                                    playbackProgressFraction = progressFraction,
                                    watchCount = video.watchCount,
                                    folders = videoFolderMap[video.id] ?: emptyList(),
                                    mediaType = video.mediaType,
                                    config = VideoCardConfig(
                                        showFavoriteButton = !isSelectionMode,
                                        showMoveToFolderButton = !isSelectionMode,
                                        showRemoveFromFolderButton = !isSelectionMode && selectedFolder != null,
                                        showDownloadButton = !isSelectionMode,
                                        showSelectionCheckbox = isSelectionMode,
                                        showFolderBadges = true,
                                        showPlaybackProgress = true,
                                        showDownloadedBadge = true,
                                        showMarkWatchedButton = !isSelectionMode,
                                        showMediaTypeBadge = true
                                    ),
                                    onClick = { if (isSelectionMode) onToggleSelection(video.id) else onVideoClick(video.id) },
                                    onLongClick = { onVideoLongClick(video.id) },
                                    onFavoriteToggle = { onFavoriteToggle(video) },
                                    onMoveToFolder = { onMoveToFolderClick(video.id) },
                                    onRemoveFromFolder = { selectedFolder?.let { onRemoveFromFolder(video.id, it.id) } },
                                    onDownloadClick = { onDownloadIconClick(video.id) },
                                    onMarkWatched = { onMarkWatched(video.id) },
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
                        if (isEndReached) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EndOfListIndicator()
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (currentFolders.isNotEmpty()) {
                            item {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "Folders" else "Subfolders",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MediaNestColors.TextPrimary
                                    ),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(currentFolders, key = { "folder_${it.id}" }) { folder ->
                                FolderRow(
                                    folder = folder,
                                    stats = folderStatsMap[folder.id],
                                    onClick = { onFolderClick(folder) },
                                    onRename = { onRenameFolder(folder) },
                                    onDelete = { onDeleteFolder(folder) }
                                )
                            }
                        }

                        if (currentVideos.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Videos (${currentVideos.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MediaNestColors.TextPrimary
                                    ),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(currentVideos, key = { "video_${it.id}" }) { video ->
                                val history = playbackHistory.find { it.videoId == video.id }
                                val positionMillis = history?.positionMillis ?: 0L
                                val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                                    ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                val onMoveToFolderClick = LocalMoveToFolder.current

                                UnifiedVideoRow(
                                    title = video.title,
                                    channelName = video.channelName,
                                    thumbnailUrl = video.thumbnailUrl,
                                    durationSeconds = video.durationSeconds,
                                    uploadDate = video.uploadDate,
                                    isFavorite = video.favorite,
                                    isDownloaded = video.localFilePath.isNotEmpty() && File(video.localFilePath).exists(),
                                    isSelected = selectedIds.contains(video.id),
                                    playbackProgressFraction = progressFraction,
                                    watchCount = video.watchCount,
                                    folders = videoFolderMap[video.id] ?: emptyList(),
                                    mediaType = video.mediaType,
                                    config = VideoCardConfig(
                                        showFavoriteButton = !isSelectionMode,
                                        showMoveToFolderButton = !isSelectionMode,
                                        showRemoveFromFolderButton = !isSelectionMode && selectedFolder != null,
                                        showDownloadButton = !isSelectionMode,
                                        showSelectionCheckbox = isSelectionMode,
                                        showFolderBadges = true,
                                        showPlaybackProgress = true,
                                        showDownloadedBadge = true,
                                        showMarkWatchedButton = !isSelectionMode,
                                        showMediaTypeBadge = true
                                    ),
                                    onClick = { if (isSelectionMode) onToggleSelection(video.id) else onVideoClick(video.id) },
                                    onLongClick = { onVideoLongClick(video.id) },
                                    onFavoriteToggle = { onFavoriteToggle(video) },
                                    onMoveToFolder = { onMoveToFolderClick(video.id) },
                                    onRemoveFromFolder = { selectedFolder?.let { onRemoveFromFolder(video.id, it.id) } },
                                    onDownloadClick = { onDownloadIconClick(video.id) },
                                    onMarkWatched = { onMarkWatched(video.id) },
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
                        if (isEndReached) {
                            item {
                                EndOfListIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Grid-mode Folder Card with Design 2.0 elevated glass surface.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    folder: FolderEntity,
    stats: FolderStats?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(MediaNestShapes.Card)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            ),
        shape = MediaNestShapes.Card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Folder Icon Badge and Overflow Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MediaNestColors.AccentDeep),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_folder),
                        contentDescription = null,
                        tint = MediaNestColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    MediaNestIconButton(
                        onClick = { menuExpanded = true },
                        size = MediaNestIconButtonSize.ExtraSmall,
                        contentDescription = "Folder options"
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_more),
                            contentDescription = "More options",
                            tint = MediaNestColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MediaNestColors.Raised)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = MediaNestColors.TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_edit),
                                    contentDescription = "Rename",
                                    tint = MediaNestColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MediaNestColors.Destructive) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_trash),
                                    contentDescription = "Delete",
                                    tint = MediaNestColors.Destructive,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            // Folder Title
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp
                ),
                color = MediaNestColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Item Count and Size Badge
            val itemCount = stats?.itemCount ?: 0
            val sizeBytes = stats?.totalSizeBytes ?: 0L
            val sizeText = if (sizeBytes > 0L) " • ${Formatter.formatShortFileSize(context, sizeBytes)}" else ""
            val countLabel = "$itemCount ${if (itemCount == 1) "item" else "items"}$sizeText"

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MediaNestColors.Raised)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MediaNestColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * List-mode Folder Row with Design 2.0 elevated surface.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: FolderEntity,
    stats: FolderStats?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(MediaNestShapes.Card)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onRename
            ),
        shape = MediaNestShapes.Card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MediaNestColors.AccentDeep),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mn_folder),
                    contentDescription = null,
                    tint = MediaNestColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = MediaNestColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val itemCount = stats?.itemCount ?: 0
                val sizeBytes = stats?.totalSizeBytes ?: 0L
                val sizeText = if (sizeBytes > 0L) " • ${Formatter.formatShortFileSize(context, sizeBytes)}" else ""
                Text(
                    text = "$itemCount ${if (itemCount == 1) "item" else "items"}$sizeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MediaNestColors.TextSecondary
                )
            }

            MediaNestIconButton(
                onClick = onRename,
                size = MediaNestIconButtonSize.Small,
                contentDescription = "Rename folder"
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mn_edit),
                    contentDescription = "Rename",
                    tint = MediaNestColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            MediaNestIconButton(
                onClick = onDelete,
                size = MediaNestIconButtonSize.Small,
                contentDescription = "Delete folder"
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mn_trash),
                    contentDescription = "Delete",
                    tint = MediaNestColors.Destructive,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LibraryStatsLine(
    tab: LibraryTab,
    historyCount: Int,
    historyWatchTimeMs: Long,
    watchedCount: Int,
    favoritesCount: Int,
    rootFoldersCount: Int,
    childFoldersCount: Int,
    folderVideosCount: Int,
    selectedFolder: FolderEntity?,
    playlistsCount: Int,
    channelsCount: Int,
    modifier: Modifier = Modifier
) {
    val iconRes = when (tab) {
        LibraryTab.HISTORY -> R.drawable.ic_mn_history
        LibraryTab.WATCHED -> R.drawable.ic_mn_watched
        LibraryTab.FOLDERS -> R.drawable.ic_mn_folder
        LibraryTab.FAVORITES -> R.drawable.ic_mn_heart
        LibraryTab.PLAYLISTS -> R.drawable.ic_mn_playlist
        LibraryTab.SUBSCRIPTIONS -> R.drawable.ic_mn_channel
    }

    val label = when (tab) {
        LibraryTab.HISTORY -> {
            val timeStr = UiUtils.formatDuration(historyWatchTimeMs / 1000L)
            "$historyCount ${if (historyCount == 1) "video" else "videos"} · $timeStr watched"
        }
        LibraryTab.WATCHED -> {
            "$watchedCount watched ${if (watchedCount == 1) "video" else "videos"}"
        }
        LibraryTab.FOLDERS -> {
            if (selectedFolder == null) {
                "$rootFoldersCount ${if (rootFoldersCount == 1) "folder" else "folders"}"
            } else {
                val folderStr = if (childFoldersCount > 0) "$childFoldersCount ${if (childFoldersCount == 1) "subfolder" else "subfolders"} · " else ""
                "$folderStr$folderVideosCount ${if (folderVideosCount == 1) "video" else "videos"}"
            }
        }
        LibraryTab.FAVORITES -> {
            "$favoritesCount favorite ${if (favoritesCount == 1) "video" else "videos"}"
        }
        LibraryTab.PLAYLISTS -> {
            "$playlistsCount saved ${if (playlistsCount == 1) "playlist" else "playlists"}"
        }
        LibraryTab.SUBSCRIPTIONS -> {
            "$channelsCount subscribed ${if (channelsCount == 1) "channel" else "channels"}"
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MediaNestColors.Accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MediaNestColors.TextSecondary,
                fontSize = 12.sp
            )
        )
    }
}
