package com.example.medianest.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.example.medianest.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.data.model.ChannelInfo
import com.example.medianest.data.model.ExtractedPlaylistInfo
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.components.MediaNestChip
import com.example.medianest.ui.components.MediaNestSnackbarHost
import com.example.medianest.ui.components.MediaNestSortBottomSheet
import com.example.medianest.ui.components.MediaNestSortOption
import com.example.medianest.ui.components.MediaNestTopAppBar
import com.example.medianest.ui.components.QuickDownloadMenu
import com.example.medianest.ui.components.UnifiedVideoCard
import com.example.medianest.ui.components.UnifiedVideoRow
import com.example.medianest.ui.components.VideoCardConfig
import com.example.medianest.ui.components.WatchCountDialog
import com.example.medianest.ui.components.YoutubeSubscribeButton
import com.example.medianest.ui.components.EndOfListIndicator
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.viewmodel.HomeUiState
import com.example.medianest.ui.viewmodel.HomeViewModel
import com.example.medianest.ui.viewmodel.SortCategory
import com.example.medianest.ui.viewmodel.SortDirection
import com.example.medianest.ui.viewmodel.ViewMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineSubscriptionScreen(
    sourceType: String,
    sourceId: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onPlayFromList: (List<ExtractedVideoInfo>, startIndex: Int) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = hiltViewModel()
) {
    BackHandler(enabled = true, onBack = onBack)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val showShorts by viewModel.showShorts.collectAsStateWithLifecycle()
    val favoriteVideoIds by viewModel.favoriteVideoIds.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val videoFolderMap by viewModel.videoFolderMap.collectAsStateWithLifecycle()
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val fetchingStreamsFor by viewModel.fetchingStreamsFor.collectAsStateWithLifecycle()
    val fetchedStreams by viewModel.fetchedStreams.collectAsStateWithLifecycle()
    val playbackHistory by viewModel.playbackHistory.collectAsStateWithLifecycle()
    val watchCounts by viewModel.watchCounts.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val showBulkQualityDialog by viewModel.showBulkQualityDialog.collectAsStateWithLifecycle()
    val bulkFetchProgress by viewModel.bulkFetchProgress.collectAsStateWithLifecycle()
    val bulkDownloadConfirmation by viewModel.bulkDownloadConfirmation.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    var videoToMove by remember { mutableStateOf<ExtractedVideoInfo?>(null) }
    var expandedDownloadVideoId by remember { mutableStateOf<String?>(null) }

    var showWatchCountDialog by remember { mutableStateOf(false) }
    var watchCountTargetVideoId by remember { mutableStateOf<String?>(null) }
    var watchCountTargetTitle by remember { mutableStateOf("") }
    var watchCountTargetInitialCount by remember { mutableStateOf(0) }

    var sortCategory by remember { mutableStateOf(SortCategory.DATE) }
    var sortDirection by remember { mutableStateOf(SortDirection.DESC) }
    var showSortSheet by remember { mutableStateOf(false) }

    val url = remember(sourceType, sourceId) {
        val trimmed = sourceId.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (sourceType == "playlist") {
            val cleanId = if (trimmed.contains("list=")) {
                trimmed.substringAfter("list=").substringBefore("&")
            } else {
                trimmed
            }
            "https://www.youtube.com/playlist?list=$cleanId"
        } else {
            // channel
            if (trimmed.startsWith("UC")) {
                "https://www.youtube.com/channel/$trimmed/videos"
            } else if (trimmed.startsWith("@")) {
                "https://www.youtube.com/$trimmed/videos"
            } else {
                val cleanId = trimmed.removePrefix("/").removePrefix("channel/").removePrefix("c/")
                "https://www.youtube.com/channel/$cleanId/videos"
            }
        }
    }

    LaunchedEffect(url) {
        viewModel.onUrlSubmitted(url)
    }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val shouldLoadMoreGrid by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 3
        }
    }
    val shouldLoadMoreList by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMoreGrid, shouldLoadMoreList) {
        if (shouldLoadMoreGrid || shouldLoadMoreList) {
            viewModel.loadNextPage()
        }
    }

    Scaffold(
        snackbarHost = { MediaNestSnackbarHost(snackbarHostState) },
        topBar = {
            MediaNestTopAppBar(
                title = "",
                onNavigateBack = onBack,
                showBottomDivider = true,
                actions = {
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            painter = painterResource(if (viewMode == ViewMode.GRID) R.drawable.ic_mn_list else R.drawable.ic_mn_grid),
                            contentDescription = if (viewMode == ViewMode.GRID) "Switch to list view" else "Switch to grid view",
                            tint = MediaNestColors.TextPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading, is HomeUiState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is HomeUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.onUrlSubmitted(url) }
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }

                is HomeUiState.PlaylistResult -> {
                    val isSaved = subscriptions.any { it.sourceType == "playlist" && it.sourceId == state.playlist.playlistId }
                    val playlistVideos = state.playlist.videos
                    val filteredVideos = if (showShorts) {
                        playlistVideos
                    } else {
                        playlistVideos.filter { !it.isShort }
                    }
                    val sortedVideos = remember(filteredVideos, sortCategory, sortDirection) {
                        val sorted = when (sortCategory) {
                            SortCategory.DATE -> filteredVideos.sortedBy { it.uploadDate }.let { if (sortDirection == SortDirection.ASC) it else it.reversed() }
                            SortCategory.NAME -> filteredVideos.sortedBy { it.title.lowercase() }.let { if (sortDirection == SortDirection.ASC) it else it.reversed() }
                            SortCategory.DURATION -> filteredVideos.sortedBy { it.durationSeconds }.let { if (sortDirection == SortDirection.ASC) it else it.reversed() }
                            else -> filteredVideos
                        }
                        sorted
                    }

                    ExtractedVideoListContent(
                        videos = sortedVideos,
                        viewMode = viewMode,
                        gridState = gridState,
                        listState = listState,
                        headerContent = {
                            PlaylistHeader(
                                playlist = state.playlist,
                                isSaved = isSaved,
                                onToggleSave = {
                                    if (isSaved) {
                                        viewModel.unsubscribe(state.playlist.playlistId)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Removed from Playlist")
                                        }
                                    } else {
                                        viewModel.subscribe(
                                            "playlist",
                                            state.playlist.playlistId,
                                            state.playlist.name,
                                            state.playlist.thumbnailUrl
                                        )
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Added to Playlist")
                                        }
                                    }
                                },
                                onDownloadAll = {
                                    viewModel.setBulkQualityDialogVisible(true)
                                },
                                showShorts = showShorts,
                                onToggleShorts = { viewModel.toggleShorts(it) }
                            )
                            if (filteredVideos.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val sortLabel = when (sortCategory) { SortCategory.DATE -> "Date"; SortCategory.NAME -> "Name"; SortCategory.DURATION -> "Duration"; else -> "Date" }
                                    val sortDirectionSymbol = if (sortDirection == SortDirection.ASC) "↑" else "↓"
                                    MediaNestChip(
                                        label = "$sortLabel $sortDirectionSymbol",
                                        selected = false,
                                        onClick = { showSortSheet = true },
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
                        },
                        playbackHistory = playbackHistory,
                        favoriteVideoIds = favoriteVideoIds,
                        allDownloads = allDownloads,
                        watchCounts = watchCounts,
                        videoFolderMap = videoFolderMap,
                        expandedDownloadVideoId = expandedDownloadVideoId,
                        fetchingStreamsFor = fetchingStreamsFor,
                        fetchedStreams = fetchedStreams,
                        isFetchingNextPage = state.isFetchingNextPage,
                        hasMore = state.hasMore,
                        onVideoClick = { video ->
                            onVideoClick(video.videoId)
                        },
                        onPlayClick = { video ->
                            onPlayFromList(sortedVideos, sortedVideos.indexOfFirst { it.videoId == video.videoId }.coerceAtLeast(0))
                        },
                        onFavoriteToggle = { video, fav ->
                            viewModel.toggleFavorite(video, fav)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(if (fav) "Added to favorites" else "Removed from favorites")
                            }
                        },
                        onMoveToFolder = { video ->
                            videoToMove = video
                            showMoveToFolderDialog = true
                        },
                        onDownloadClick = { id ->
                            expandedDownloadVideoId = id
                            viewModel.fetchStreamsFor(id)
                        },
                        onMarkWatched = { video ->
                            val currentCount = watchCounts[video.videoId] ?: 0
                            watchCountTargetVideoId = video.videoId
                            watchCountTargetTitle = video.title
                            watchCountTargetInitialCount = currentCount
                            showWatchCountDialog = true
                        },
                        onDismissDownloadMenu = { expandedDownloadVideoId = null },
                        onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                        onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                        onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                    )

                    if (showSortSheet) {
                        MediaNestSortBottomSheet(
                            onDismissRequest = { showSortSheet = false },
                            selectedSortBy = when (sortCategory) {
                                SortCategory.DATE -> "DATE"
                                SortCategory.NAME -> "TITLE"
                                SortCategory.DURATION -> "DURATION"
                                else -> "DATE"
                            },
                            isAscending = sortDirection == SortDirection.ASC,
                            onSortSelected = { sortBy, isAscending ->
                                sortCategory = when (sortBy.uppercase()) {
                                    "TITLE", "NAME" -> SortCategory.NAME
                                    "DURATION" -> SortCategory.DURATION
                                    else -> SortCategory.DATE
                                }
                                sortDirection = if (isAscending) SortDirection.ASC else SortDirection.DESC
                                showSortSheet = false
                            },
                            options = MediaNestSortOption.DefaultMediaOptions
                        )
                    }
                }

                is HomeUiState.ChannelResult -> {
                    val isSubscribed = subscriptions.any { sub ->
                        sub.sourceType == "channel" && (
                            sub.sourceId == state.channel.channelId ||
                            sub.sourceId == state.channel.url ||
                            sub.sourceId.contains(state.channel.channelId) ||
                            sub.name.equals(state.channel.name, ignoreCase = true) ||
                            (state.channel.url.isNotBlank() && state.channel.url.contains(
                                sub.sourceId.removePrefix("https://").removePrefix("www.youtube.com/").removePrefix("@").removePrefix("channel/").removePrefix("c/").trim()
                            ))
                        )
                    }
                    val channelUploads = state.channel.uploads
                    val filteredVideos = if (showShorts) {
                        channelUploads
                    } else {
                        channelUploads.filter { !it.isShort }
                    }
                    val sortedVideos = remember(filteredVideos, sortCategory, sortDirection) {
                        val sorted = when (sortCategory) {
                            SortCategory.DATE -> filteredVideos.sortedBy { it.uploadDate }.let { if (sortDirection == SortDirection.ASC) it else it.reversed() }
                            SortCategory.NAME -> filteredVideos.sortedBy { it.title.lowercase() }.let { if (sortDirection == SortDirection.ASC) it else it.reversed() }
                            SortCategory.DURATION -> filteredVideos.sortedBy { it.durationSeconds }.let { if (sortDirection == SortDirection.ASC) it else it.reversed() }
                            else -> filteredVideos
                        }
                        sorted
                    }

                    ExtractedVideoListContent(
                        videos = sortedVideos,
                        viewMode = viewMode,
                        gridState = gridState,
                        listState = listState,
                        headerContent = {
                            ChannelHeader(
                                channel = state.channel,
                                isSubscribed = isSubscribed,
                                onToggleSubscribe = {
                                    if (isSubscribed) {
                                        val matchedSub = subscriptions.firstOrNull { sub ->
                                            sub.sourceType == "channel" && (
                                                sub.sourceId == state.channel.channelId ||
                                                sub.sourceId == state.channel.url ||
                                                sub.sourceId.contains(state.channel.channelId) ||
                                                sub.name.equals(state.channel.name, ignoreCase = true) ||
                                                (state.channel.url.isNotBlank() && state.channel.url.contains(
                                                    sub.sourceId.removePrefix("https://").removePrefix("www.youtube.com/").removePrefix("@").removePrefix("channel/").removePrefix("c/").trim()
                                                ))
                                            )
                                        }
                                        val subId = matchedSub?.sourceId ?: state.channel.channelId
                                        viewModel.unsubscribe(subId)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Unsubscribed from Channel")
                                        }
                                    } else {
                                        viewModel.subscribe(
                                            "channel",
                                            state.channel.channelId,
                                            state.channel.name,
                                            state.channel.avatarUrl
                                        )
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Subscribed to Channel")
                                        }
                                    }
                                },
                                onDownloadAll = {
                                    viewModel.setBulkQualityDialogVisible(true)
                                },
                                showShorts = showShorts,
                                onToggleShorts = { viewModel.toggleShorts(it) }
                            )
                            if (filteredVideos.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val sortLabel = when (sortCategory) { SortCategory.DATE -> "Date"; SortCategory.NAME -> "Name"; SortCategory.DURATION -> "Duration"; else -> "Date" }
                                    val sortDirectionSymbol = if (sortDirection == SortDirection.ASC) "↑" else "↓"
                                    MediaNestChip(
                                        label = "$sortLabel $sortDirectionSymbol",
                                        selected = false,
                                        onClick = { showSortSheet = true },
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
                        },
                        playbackHistory = playbackHistory,
                        favoriteVideoIds = favoriteVideoIds,
                        allDownloads = allDownloads,
                        watchCounts = watchCounts,
                        videoFolderMap = videoFolderMap,
                        expandedDownloadVideoId = expandedDownloadVideoId,
                        fetchingStreamsFor = fetchingStreamsFor,
                        fetchedStreams = fetchedStreams,
                        isFetchingNextPage = state.isFetchingNextPage,
                        hasMore = state.hasMore,
                        onVideoClick = { video ->
                            onVideoClick(video.videoId)
                        },
                        onPlayClick = { video ->
                            onPlayFromList(sortedVideos, sortedVideos.indexOfFirst { it.videoId == video.videoId }.coerceAtLeast(0))
                        },
                        onFavoriteToggle = { video, fav ->
                            viewModel.toggleFavorite(video, fav)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(if (fav) "Added to favorites" else "Removed from favorites")
                            }
                        },
                        onMoveToFolder = { video ->
                            videoToMove = video
                            showMoveToFolderDialog = true
                        },
                        onDownloadClick = { id ->
                            expandedDownloadVideoId = id
                            viewModel.fetchStreamsFor(id)
                        },
                        onMarkWatched = { video ->
                            val currentCount = watchCounts[video.videoId] ?: 0
                            watchCountTargetVideoId = video.videoId
                            watchCountTargetTitle = video.title
                            watchCountTargetInitialCount = currentCount
                            showWatchCountDialog = true
                        },
                        onDismissDownloadMenu = { expandedDownloadVideoId = null },
                        onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                        onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                        onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                    )

                    if (showSortSheet) {
                        MediaNestSortBottomSheet(
                            onDismissRequest = { showSortSheet = false },
                            selectedSortBy = when (sortCategory) {
                                SortCategory.DATE -> "DATE"
                                SortCategory.NAME -> "TITLE"
                                SortCategory.DURATION -> "DURATION"
                                else -> "DATE"
                            },
                            isAscending = sortDirection == SortDirection.ASC,
                            onSortSelected = { sortBy, isAscending ->
                                sortCategory = when (sortBy.uppercase()) {
                                    "TITLE", "NAME" -> SortCategory.NAME
                                    "DURATION" -> SortCategory.DURATION
                                    else -> SortCategory.DATE
                                }
                                sortDirection = if (isAscending) SortDirection.ASC else SortDirection.DESC
                                showSortSheet = false
                            },
                            options = MediaNestSortOption.DefaultMediaOptions
                        )
                    }
                }

                is HomeUiState.Success -> {
                    viewModel.cacheResult(state.video)
                    val singleList = listOf(state.video)

                    ExtractedVideoListContent(
                        videos = singleList,
                        viewMode = viewMode,
                        gridState = gridState,
                        listState = listState,
                        headerContent = {},
                        playbackHistory = playbackHistory,
                        favoriteVideoIds = favoriteVideoIds,
                        allDownloads = allDownloads,
                        watchCounts = watchCounts,
                        videoFolderMap = videoFolderMap,
                        expandedDownloadVideoId = expandedDownloadVideoId,
                        fetchingStreamsFor = fetchingStreamsFor,
                        fetchedStreams = fetchedStreams,
                        isFetchingNextPage = false,
                        hasMore = false,
                        onVideoClick = { video -> onVideoClick(video.videoId) },
                        onPlayClick = { video -> onPlayFromList(singleList, 0) },
                        onFavoriteToggle = { video, fav ->
                            viewModel.toggleFavorite(video, fav)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(if (fav) "Added to favorites" else "Removed from favorites")
                            }
                        },
                        onMoveToFolder = { video ->
                            videoToMove = video
                            showMoveToFolderDialog = true
                        },
                        onDownloadClick = { id ->
                            expandedDownloadVideoId = id
                            viewModel.fetchStreamsFor(id)
                        },
                        onMarkWatched = { video ->
                            val currentCount = watchCounts[video.videoId] ?: 0
                            watchCountTargetVideoId = video.videoId
                            watchCountTargetTitle = video.title
                            watchCountTargetInitialCount = currentCount
                            showWatchCountDialog = true
                        },
                        onDismissDownloadMenu = { expandedDownloadVideoId = null },
                        onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                        onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                        onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                    )
                }
            }
        }
    }

    if (showBulkQualityDialog) {
        val qualities = listOf("1080p", "720p", "480p", "360p", "Audio")
        var selectedQuality by remember { mutableStateOf("720p") }
        AlertDialog(
            onDismissRequest = { viewModel.setBulkQualityDialogVisible(false) },
            title = { Text("Download All by Resolution") },
            text = {
                Column {
                    Text("Select target resolution/format:")
                    Spacer(Modifier.height(8.dp))
                    qualities.forEach { quality ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedQuality = quality }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = (selectedQuality == quality),
                                onClick = { selectedQuality = quality }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when (quality) {
                                    "1080p" -> "1080p (Highest video quality)"
                                    "720p" -> "720p (High definition)"
                                    "480p" -> "480p (Standard definition)"
                                    "360p" -> "360p (Low data usage)"
                                    "Audio" -> "Audio Only (M4A/WebM)"
                                    else -> quality
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val videos = when (val state = uiState) {
                        is HomeUiState.PlaylistResult -> if (showShorts) state.playlist.videos else state.playlist.videos.filter { !it.isShort }
                        is HomeUiState.ChannelResult -> if (showShorts) state.channel.uploads else state.channel.uploads.filter { !it.isShort }
                        else -> emptyList()
                    }
                    viewModel.startBulkFetch(videos, selectedQuality)
                }) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setBulkQualityDialogVisible(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    bulkFetchProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Fetching Video Metadata") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Retrieving stream details to calculate total size and check disk space.")
                    Spacer(Modifier.height(16.dp))
                    Text("Progress: ${progress.current} of ${progress.total} videos")
                    Spacer(Modifier.height(8.dp))
                    if (progress.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = progress.currentTitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelBulkFetch() }) {
                    Text("Cancel")
                }
            }
        )
    }

    bulkDownloadConfirmation?.let { confirmation ->
        val hasSpace = confirmation.usableSpace > confirmation.totalSize
        val formattedSize = Formatter.formatShortFileSize(context, confirmation.totalSize)
        val formattedSpace = Formatter.formatShortFileSize(context, confirmation.usableSpace)

        AlertDialog(
            onDismissRequest = { viewModel.dismissBulkConfirmation() },
            title = { Text("Confirm Bulk Download") },
            text = {
                Column {
                    Text("Quality: ${confirmation.quality}")
                    Spacer(Modifier.height(4.dp))
                    Text("Total Videos: ${confirmation.totalVideoCount}")
                    Spacer(Modifier.height(4.dp))
                    Text("Downloadable Videos: ${confirmation.videoCount}")
                    if (confirmation.unavailableVideoCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("No ${confirmation.quality} stream: ${confirmation.unavailableVideoCount}")
                    }
                    if (confirmation.failedVideoCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Failed to fetch metadata: ${confirmation.failedVideoCount}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Total Download Size: $formattedSize")
                    Spacer(Modifier.height(4.dp))
                    Text("Available Disk Space: $formattedSpace")
                    Spacer(Modifier.height(12.dp))

                    if (hasSpace) {
                        Text(
                            text = "Storage check: Sufficient space available.",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        val neededBytes = confirmation.totalSize - confirmation.usableSpace
                        val formattedNeeded = Formatter.formatShortFileSize(context, neededBytes)
                        Text(
                            text = "WARNING: Insufficient storage space!\nYou need an additional $formattedNeeded to complete downloads.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmBulkDownload(confirmation.jobId)
                }) {
                    Text(if (hasSpace) "Download" else "Download Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBulkConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showMoveToFolderDialog && videoToMove != null) {
        AlertDialog(
            onDismissRequest = {
                showMoveToFolderDialog = false
                videoToMove = null
            },
            title = { Text("Move to Folder") },
            text = {
                LazyColumn {
                    items(folders) { folder ->
                        TextButton(
                            onClick = {
                                videoToMove?.let { vid ->
                                    viewModel.moveVideoToFolder(vid, folder.id)
                                }
                                showMoveToFolderDialog = false
                                videoToMove = null
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Moved to ${folder.name}")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(folder.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showMoveToFolderDialog = false
                    videoToMove = null
                }) { Text("Cancel") }
            }
        )
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

@Composable
private fun ExtractedVideoListContent(
    videos: List<ExtractedVideoInfo>,
    viewMode: ViewMode,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    headerContent: @Composable () -> Unit,
    playbackHistory: List<com.example.medianest.data.local.entity.HistoryEntity>,
    favoriteVideoIds: Set<String>,
    allDownloads: List<com.example.medianest.data.local.entity.DownloadEntity>,
    watchCounts: Map<String, Int>,
    videoFolderMap: Map<String, List<com.example.medianest.data.local.entity.FolderEntity>>,
    expandedDownloadVideoId: String?,
    fetchingStreamsFor: String?,
    fetchedStreams: ExtractedVideoInfo?,
    isFetchingNextPage: Boolean,
    hasMore: Boolean,
    onVideoClick: (ExtractedVideoInfo) -> Unit,
    onPlayClick: ((ExtractedVideoInfo) -> Unit)? = null,
    onFavoriteToggle: (ExtractedVideoInfo, Boolean) -> Unit,
    onMoveToFolder: (ExtractedVideoInfo) -> Unit,
    onDownloadClick: (String) -> Unit,
    onMarkWatched: (ExtractedVideoInfo) -> Unit,
    onDismissDownloadMenu: () -> Unit,
    onEnqueueDownload: (ExtractedVideoInfo, com.example.medianest.data.model.StreamSource) -> Unit,
    onDeleteDownload: (com.example.medianest.data.local.entity.DownloadEntity) -> Unit,
    onExtractAudio: (com.example.medianest.data.local.entity.DownloadEntity) -> Unit
) {
    if (viewMode == ViewMode.GRID) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                headerContent()
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Videos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            if (videos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No videos found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = videos,
                    key = { _, video -> video.videoId }
                ) { index, video ->
                    val history = playbackHistory.find { it.videoId == video.videoId }
                    val positionMillis = history?.positionMillis ?: 0L
                    val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                        ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    val isFavorite = favoriteVideoIds.contains(video.videoId)
                    val isDownloaded = allDownloads.any { it.videoId == video.videoId && it.status == DownloadStatus.COMPLETED }

                    UnifiedVideoCard(
                        title = video.title,
                        serialNumber = index + 1,
                        channelName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl,
                        durationSeconds = video.durationSeconds,
                        uploadDate = video.uploadDate,
                        isFavorite = isFavorite,
                        isDownloaded = isDownloaded,
                        playbackProgressFraction = progressFraction,
                        watchCount = watchCounts[video.videoId] ?: 0,
                        folders = videoFolderMap[video.videoId] ?: emptyList(),
                        mediaType = "VIDEO",
                        config = VideoCardConfig(
                            showFavoriteButton = true,
                            showPlayButton = true,
                            showMoveToFolderButton = true,
                            showDownloadButton = true,
                            showFolderBadges = true,
                            showPlaybackProgress = true,
                            showDownloadedBadge = true,
                            showMarkWatchedButton = true,
                            showMediaTypeBadge = true
                        ),
                        onClick = { onVideoClick(video) },
                        onPlayClick = onPlayClick?.let { cb -> { cb(video) } },
                        onFavoriteToggle = { onFavoriteToggle(video, !isFavorite) },
                        onMoveToFolder = { onMoveToFolder(video) },
                        onDownloadClick = { onDownloadClick(video.videoId) },
                        onMarkWatched = { onMarkWatched(video) },
                        downloadMenuContent = {
                            QuickDownloadMenu(
                                isExpanded = expandedDownloadVideoId == video.videoId,
                                onDismiss = onDismissDownloadMenu,
                                isFetching = fetchingStreamsFor == video.videoId,
                                fetchedStreams = fetchedStreams,
                                allDownloads = allDownloads,
                                videoId = video.videoId,
                                onEnqueueDownload = onEnqueueDownload,
                                onDeleteDownload = onDeleteDownload,
                                onExtractAudio = onExtractAudio
                            )
                        }
                    )
                }
            }
            if (isFetchingNextPage) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (!hasMore && videos.isNotEmpty()) {
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                headerContent()
            }
            item {
                Text(
                    text = "Videos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            if (videos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No videos found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = videos,
                    key = { _, video -> video.videoId }
                ) { index, video ->
                    val history = playbackHistory.find { it.videoId == video.videoId }
                    val positionMillis = history?.positionMillis ?: 0L
                    val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                        ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    val isFavorite = favoriteVideoIds.contains(video.videoId)
                    val isDownloaded = allDownloads.any { it.videoId == video.videoId && it.status == DownloadStatus.COMPLETED }

                    UnifiedVideoRow(
                        title = video.title,
                        serialNumber = index + 1,
                        channelName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl,
                        durationSeconds = video.durationSeconds,
                        uploadDate = video.uploadDate,
                        isFavorite = isFavorite,
                        isDownloaded = isDownloaded,
                        playbackProgressFraction = progressFraction,
                        watchCount = watchCounts[video.videoId] ?: 0,
                        folders = videoFolderMap[video.videoId] ?: emptyList(),
                        mediaType = "VIDEO",
                        config = VideoCardConfig(
                            showFavoriteButton = true,
                            showPlayButton = true,
                            showMoveToFolderButton = true,
                            showDownloadButton = true,
                            showFolderBadges = true,
                            showPlaybackProgress = true,
                            showDownloadedBadge = true,
                            showMarkWatchedButton = true,
                            showMediaTypeBadge = true
                        ),
                        onClick = { onVideoClick(video) },
                        onPlayClick = onPlayClick?.let { cb -> { cb(video) } },
                        onFavoriteToggle = { onFavoriteToggle(video, !isFavorite) },
                        onMoveToFolder = { onMoveToFolder(video) },
                        onDownloadClick = { onDownloadClick(video.videoId) },
                        onMarkWatched = { onMarkWatched(video) },
                        downloadMenuContent = {
                            QuickDownloadMenu(
                                isExpanded = expandedDownloadVideoId == video.videoId,
                                onDismiss = onDismissDownloadMenu,
                                isFetching = fetchingStreamsFor == video.videoId,
                                fetchedStreams = fetchedStreams,
                                allDownloads = allDownloads,
                                videoId = video.videoId,
                                onEnqueueDownload = onEnqueueDownload,
                                onDeleteDownload = onDeleteDownload,
                                onExtractAudio = onExtractAudio
                            )
                        }
                    )
                }
            }
            if (isFetchingNextPage) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (!hasMore && videos.isNotEmpty()) {
                item {
                    EndOfListIndicator()
                }
            }
        }
    }
}

@Composable
fun PlaylistHeader(
    playlist: ExtractedPlaylistInfo,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onDownloadAll: () -> Unit,
    showShorts: Boolean,
    onToggleShorts: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (!playlist.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = "Playlist",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Videos: ${playlist.videoCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!playlist.uploaderName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playlist.uploaderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSaved) {
                    OutlinedButton(
                        onClick = onToggleSave,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_mn_check), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Saved",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Button(
                        onClick = onToggleSave,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_mn_playlist), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add to Playlist",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Button(
                    onClick = onDownloadAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(painter = painterResource(R.drawable.ic_mn_download), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Download All",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Shorts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = showShorts,
                    onCheckedChange = onToggleShorts
                )
            }
        }
    }
}

@Composable
fun ChannelHeader(
    channel: ChannelInfo,
    isSubscribed: Boolean,
    onToggleSubscribe: () -> Unit,
    onDownloadAll: () -> Unit,
    showShorts: Boolean,
    onToggleShorts: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            val bannerUrl = channel.avatarUrl
            if (!bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = "Channel Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = channel.avatarUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Channel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Videos: ${channel.videoCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!channel.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = channel.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                YoutubeSubscribeButton(
                    isSubscribed = isSubscribed,
                    onClick = onToggleSubscribe,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onDownloadAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(painter = painterResource(R.drawable.ic_mn_download), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Download All",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Shorts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = showShorts,
                    onCheckedChange = onToggleShorts
                )
            }
        }
    }
}
