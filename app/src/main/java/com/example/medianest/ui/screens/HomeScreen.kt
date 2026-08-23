package com.example.medianest.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.scale
import com.example.medianest.ui.components.mediaNestSwitchColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.medianest.R
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.local.entity.LinkHistoryEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.model.ExtractedPlaylistInfo
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.data.preferences.CollectionsPreferences
import com.example.medianest.ui.viewmodel.ViewMode
import com.example.medianest.ui.components.EndOfListIndicator
import com.example.medianest.ui.components.FullTitlesToggle
import com.example.medianest.ui.components.LoadingState
import com.example.medianest.ui.components.LocalFullTitles
import com.example.medianest.ui.components.MediaNestSnackbarHost
import com.example.medianest.ui.components.MediaNestTopAppBar
import com.example.medianest.ui.components.NotificationBellAction
import com.example.medianest.ui.components.QuickDownloadMenu
import com.example.medianest.ui.components.UnifiedVideoCard
import com.example.medianest.ui.components.UnifiedVideoRow
import com.example.medianest.ui.components.VideoCardConfig
import com.example.medianest.ui.components.WatchCountDialog
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.utils.UiUtils
import com.example.medianest.ui.viewmodel.HomeUiState
import com.example.medianest.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(LocalContext.current as ComponentActivity),
    onVideoSelected: (String) -> Unit = {},
    onSubscribe: (sourceType: String, sourceId: String, name: String, thumbnailUrl: String?) -> Unit = { _, _, _, _ -> },
    onNavigateToNotifications: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val linkHistory by viewModel.linkHistory.collectAsStateWithLifecycle()
    val linkHistoryLimit by viewModel.linkHistoryLimit.collectAsStateWithLifecycle()
    val defaultResolution by viewModel.defaultResolution.collectAsStateWithLifecycle()
    val showShorts by viewModel.showShorts.collectAsStateWithLifecycle()
    val favoriteVideoIds by viewModel.favoriteVideoIds.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val videoFolderMap by viewModel.videoFolderMap.collectAsStateWithLifecycle()
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val fetchingStreamsFor by viewModel.fetchingStreamsFor.collectAsStateWithLifecycle()
    val fetchedStreams by viewModel.fetchedStreams.collectAsStateWithLifecycle()
    val playbackHistory by viewModel.playbackHistory.collectAsStateWithLifecycle()
    val watchCounts by viewModel.watchCounts.collectAsStateWithLifecycle()
    val continueWatchingVideos by viewModel.continueWatchingVideos.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    val showBulkQualityDialog by viewModel.showBulkQualityDialog.collectAsStateWithLifecycle()
    val bulkFetchProgress by viewModel.bulkFetchProgress.collectAsStateWithLifecycle()
    val bulkDownloadConfirmation by viewModel.bulkDownloadConfirmation.collectAsStateWithLifecycle()

    var urlInput by remember { mutableStateOf("") }
    var selectedFormatFilter by remember { mutableStateOf("All") } // "All", "Videos", "Audio"
    var expandedDownloadVideoId by remember { mutableStateOf<String?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val collectionsPreferences = remember(context) { CollectionsPreferences(context.applicationContext) }
    val globalFullTitles by collectionsPreferences.fullTitles.collectAsStateWithLifecycle(
        initialValue = CollectionsPreferences.DEFAULT_FULL_TITLES
    )
    var fullTitlesHome by remember(globalFullTitles) { mutableStateOf(globalFullTitles) }

    // Bottom sheet & dialog states
    var activeVideoForSheet by remember { mutableStateOf<ExtractedVideoInfo?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showQuickDownloadSheet by remember { mutableStateOf(false) }
    var showMoveToFolderSheet by remember { mutableStateOf(false) }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var historyItemToDelete by remember { mutableStateOf<LinkHistoryEntity?>(null) }

    var showWatchCountDialog by remember { mutableStateOf(false) }
    var watchCountTargetVideoId by remember { mutableStateOf<String?>(null) }
    var watchCountTargetTitle by remember { mutableStateOf("") }
    var watchCountTargetInitialCount by remember { mutableStateOf(0) }

    val listState = rememberLazyListState()
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= totalItems - 5 && totalItems > 0
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            when (uiState) {
                is HomeUiState.ChannelResult -> viewModel.loadNextPage()
                is HomeUiState.PlaylistResult -> viewModel.loadNextPage()
                else -> {
                    if (!linkHistory.isNullOrEmpty()) {
                        viewModel.loadMoreLinkHistory()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                viewMode = viewMode,
                onToggleViewMode = { viewModel.toggleViewMode() },
                onNotificationsClick = onNavigateToNotifications
            )
        },
        containerColor = MediaNestColors.Background,
        contentColor = MediaNestColors.TextPrimary,
        snackbarHost = { MediaNestSnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        val currentHistory = linkHistory
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Continue Watching Section (horizontal LazyRow carousel of ~220dp cards)
            val currentContinueWatching = continueWatchingVideos
            if (!currentContinueWatching.isNullOrEmpty() && uiState is HomeUiState.Idle) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        SectionHeader(
                            title = "Continue watching",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(currentContinueWatching, key = { it.first.id }) { (vEntity, hEntity) ->
                                val totalSec = vEntity.durationSeconds
                                val posSec = hEntity.positionMillis / 1000L
                                val progressFraction = if (totalSec > 0) (posSec.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f) else 0f
                                val isAudio = vEntity.mediaType.equals("AUDIO", ignoreCase = true)

                                ContinueWatchingCard(
                                    video = vEntity,
                                    history = hEntity,
                                    isAudio = isAudio,
                                    progressFraction = progressFraction,
                                    posSec = posSec,
                                    onClick = { onVideoSelected(vEntity.id) }
                                )
                            }
                            item { EndOfListIndicator() }
                        }
                    }
                }
            }

            // 3. Hero URL Extraction Panel (Offline-first, Search/Paste pill)
            item {
                HeroExtractionPanel(
                    urlValue = urlInput,
                    onUrlChange = { urlInput = it },
                    onExtractClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        viewModel.onUrlSubmitted(urlInput.trim())
                    },
                    isLoading = uiState is HomeUiState.Loading,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fullTitles = fullTitlesHome,
                    onFullTitlesChange = { fullTitlesHome = it },
                    showClear = urlInput.isNotEmpty() || uiState !is HomeUiState.Idle,
                    onClearAll = {
                        urlInput = ""
                        viewModel.resetState()
                    }
                )
            }

            // 4. Format filter pills
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    FormatFilterPills(
                        selectedFilter = selectedFormatFilter,
                        onFilterSelected = { selectedFormatFilter = it }
                    )
                }
            }

            // 5. Extraction Results or Loading / Error
            when (val state = uiState) {
                is HomeUiState.Idle -> {
                    // Idle state: No active extraction search result to display
                }

                is HomeUiState.Loading -> {
                    item {
                        ExtractionLoadingView(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }

                is HomeUiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MediaNestColors.Raised,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Destructive.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_warning),
                                    contentDescription = "Error",
                                    tint = MediaNestColors.Destructive,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Extraction Error",
                                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = state.message,
                                        style = TextStyle(fontSize = 13.sp, color = MediaNestColors.TextSecondary)
                                    )
                                }
                                TextButton(onClick = { viewModel.resetState() }) {
                                    Text("Dismiss", color = MediaNestColors.Accent)
                                }
                            }
                        }
                    }
                }

                is HomeUiState.Success -> {
                    viewModel.cacheResult(state.video)
                    item {
                        val history = playbackHistory.find { it.videoId == state.video.videoId }
                        val positionMillis = history?.positionMillis ?: 0L
                        val progressFraction = if (state.video.durationSeconds > 0 && positionMillis > 0) {
                            ((positionMillis.toFloat() / 1000f) / state.video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val isFavorite = favoriteVideoIds.contains(state.video.videoId)
                        val isDownloaded = allDownloads.any { it.videoId == state.video.videoId && it.status == DownloadStatus.COMPLETED }

                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SectionHeader(
                                title = "Extracted Video",
                                actionText = "Play Video",
                                onActionClick = { onVideoSelected(state.video.videoId) }
                            )
                            Spacer(Modifier.height(8.dp))
                            if (viewMode == ViewMode.GRID) {
                                UnifiedVideoCard(
                                    title = state.video.title,
                                    serialNumber = 1,
                                    channelName = state.video.channelName,
                                    thumbnailUrl = state.video.thumbnailUrl,
                                    durationSeconds = state.video.durationSeconds,
                                    uploadDate = state.video.uploadDate,
                                    isFavorite = isFavorite,
                                    isDownloaded = isDownloaded,
                                    playbackProgressFraction = progressFraction,
                                    watchCount = watchCounts[state.video.videoId] ?: 0,
                                    folders = videoFolderMap[state.video.videoId] ?: emptyList(),
                                    mediaType = "VIDEO",
                                    config = VideoCardConfig(
                                        showFavoriteButton = true,
                                        showMoveToFolderButton = true,
                                        showDownloadButton = true,
                                        showFolderBadges = true,
                                        showPlaybackProgress = true,
                                        showDownloadedBadge = true,
                                        showMarkWatchedButton = true,
                                        showMediaTypeBadge = true,
                                        fullTitles = fullTitlesHome
                                    ),
                                    onClick = { onVideoSelected(state.video.videoId) },
                                    onFavoriteToggle = {
                                        viewModel.toggleFavorite(state.video, !isFavorite)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(if (!isFavorite) "Added to favorites" else "Removed from favorites")
                                        }
                                    },
                                    onMoveToFolder = {
                                        activeVideoForSheet = state.video
                                        showMoveToFolderSheet = true
                                    },
                                    onDownloadClick = {
                                        expandedDownloadVideoId = state.video.videoId
                                        viewModel.fetchStreamsFor(state.video.videoId)
                                    },
                                    onMarkWatched = {
                                        val currentCount = watchCounts[state.video.videoId] ?: 0
                                        watchCountTargetVideoId = state.video.videoId
                                        watchCountTargetTitle = state.video.title
                                        watchCountTargetInitialCount = currentCount
                                        showWatchCountDialog = true
                                    },
                                    downloadMenuContent = {
                                        QuickDownloadMenu(
                                            isExpanded = expandedDownloadVideoId == state.video.videoId,
                                            onDismiss = { expandedDownloadVideoId = null },
                                            isFetching = fetchingStreamsFor == state.video.videoId,
                                            fetchedStreams = fetchedStreams,
                                            allDownloads = allDownloads,
                                            videoId = state.video.videoId,
                                            onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                                            onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                                            onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                                        )
                                    }
                                )
                            } else {
                                UnifiedVideoRow(
                                    title = state.video.title,
                                    serialNumber = 1,
                                    channelName = state.video.channelName,
                                    thumbnailUrl = state.video.thumbnailUrl,
                                    durationSeconds = state.video.durationSeconds,
                                    uploadDate = state.video.uploadDate,
                                    isFavorite = isFavorite,
                                    isDownloaded = isDownloaded,
                                    playbackProgressFraction = progressFraction,
                                    watchCount = watchCounts[state.video.videoId] ?: 0,
                                    folders = videoFolderMap[state.video.videoId] ?: emptyList(),
                                    mediaType = "VIDEO",
                                    config = VideoCardConfig(
                                        showFavoriteButton = true,
                                        showMoveToFolderButton = true,
                                        showDownloadButton = true,
                                        showFolderBadges = true,
                                        showPlaybackProgress = true,
                                        showDownloadedBadge = true,
                                        showMarkWatchedButton = true,
                                        showMediaTypeBadge = true,
                                        fullTitles = fullTitlesHome
                                    ),
                                    onClick = { onVideoSelected(state.video.videoId) },
                                    onFavoriteToggle = {
                                        viewModel.toggleFavorite(state.video, !isFavorite)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(if (!isFavorite) "Added to favorites" else "Removed from favorites")
                                        }
                                    },
                                    onMoveToFolder = {
                                        activeVideoForSheet = state.video
                                        showMoveToFolderSheet = true
                                    },
                                    onDownloadClick = {
                                        expandedDownloadVideoId = state.video.videoId
                                        viewModel.fetchStreamsFor(state.video.videoId)
                                    },
                                    onMarkWatched = {
                                        val currentCount = watchCounts[state.video.videoId] ?: 0
                                        watchCountTargetVideoId = state.video.videoId
                                        watchCountTargetTitle = state.video.title
                                        watchCountTargetInitialCount = currentCount
                                        showWatchCountDialog = true
                                    },
                                    downloadMenuContent = {
                                        QuickDownloadMenu(
                                            isExpanded = expandedDownloadVideoId == state.video.videoId,
                                            onDismiss = { expandedDownloadVideoId = null },
                                            isFetching = fetchingStreamsFor == state.video.videoId,
                                            fetchedStreams = fetchedStreams,
                                            allDownloads = allDownloads,
                                            videoId = state.video.videoId,
                                            onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                                            onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                                            onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                is HomeUiState.PlaylistResult -> {
                    item {
                        val isSaved = subscriptions.any { it.sourceId == state.playlist.playlistId }
                        PlaylistResultHeader(
                            playlist = state.playlist,
                            isSaved = isSaved,
                            showShorts = showShorts,
                            hasMore = state.hasMore,
                            onToggleSave = {
                                if (isSaved) {
                                    viewModel.unsubscribe(state.playlist.playlistId)
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Removed from Playlist") }
                                } else {
                                    viewModel.subscribe("playlist", state.playlist.playlistId, state.playlist.name, state.playlist.thumbnailUrl)
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Added to Playlist") }
                                }
                            },
                            onDownloadAll = { viewModel.setBulkQualityDialogVisible(true) },
                            onToggleShorts = { viewModel.toggleShorts(it) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    val baseVideos = if (showShorts) state.playlist.videos else state.playlist.videos.filter { !it.isShort }
                    val filteredVideos = when (selectedFormatFilter) {
                        "Videos" -> baseVideos
                        "Audio" -> emptyList()
                        else -> baseVideos
                    }

                    if (viewMode == ViewMode.GRID) {
                        val chunkedVideos = filteredVideos.chunked(2)
                        itemsIndexed(chunkedVideos, key = { chunkIdx, _ -> "playlist_chunk_$chunkIdx" }) { chunkIdx, chunk ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                chunk.forEachIndexed { i, video ->
                                    val index = chunkIdx * 2 + i
                                    val history = playbackHistory.find { it.videoId == video.videoId }
                                    val positionMillis = history?.positionMillis ?: 0L
                                    val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                                        ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    val isFavorite = favoriteVideoIds.contains(video.videoId)
                                    val isDownloaded = allDownloads.any { it.videoId == video.videoId && it.status == DownloadStatus.COMPLETED }

                                    Box(modifier = Modifier.weight(1f)) {
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
                                                showMoveToFolderButton = true,
                                                showDownloadButton = true,
                                                showFolderBadges = true,
                                                showPlaybackProgress = true,
                                                showDownloadedBadge = true,
                                                showMarkWatchedButton = true,
                                                showMediaTypeBadge = true,
                                                fullTitles = fullTitlesHome
                                            ),
                                            onClick = { onVideoSelected(video.videoId) },
                                            onFavoriteToggle = {
                                                viewModel.toggleFavorite(video, !isFavorite)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(if (!isFavorite) "Added to favorites" else "Removed from favorites")
                                                }
                                            },
                                            onMoveToFolder = {
                                                activeVideoForSheet = video
                                                showMoveToFolderSheet = true
                                            },
                                            onDownloadClick = {
                                                expandedDownloadVideoId = video.videoId
                                                viewModel.fetchStreamsFor(video.videoId)
                                            },
                                            onMarkWatched = {
                                                val currentCount = watchCounts[video.videoId] ?: 0
                                                watchCountTargetVideoId = video.videoId
                                                watchCountTargetTitle = video.title
                                                watchCountTargetInitialCount = currentCount
                                                showWatchCountDialog = true
                                            },
                                            downloadMenuContent = {
                                                QuickDownloadMenu(
                                                    isExpanded = expandedDownloadVideoId == video.videoId,
                                                    onDismiss = { expandedDownloadVideoId = null },
                                                    isFetching = fetchingStreamsFor == video.videoId,
                                                    fetchedStreams = fetchedStreams,
                                                    allDownloads = allDownloads,
                                                    videoId = video.videoId,
                                                    onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                                                    onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                                                    onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                                                )
                                            }
                                        )
                                    }
                                }
                                if (chunk.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        itemsIndexed(filteredVideos, key = { _, v -> "playlist_${v.videoId}" }) { index, video ->
                            val history = playbackHistory.find { it.videoId == video.videoId }
                            val positionMillis = history?.positionMillis ?: 0L
                            val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                                ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val isFavorite = favoriteVideoIds.contains(video.videoId)
                            val isDownloaded = allDownloads.any { it.videoId == video.videoId && it.status == DownloadStatus.COMPLETED }

                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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
                                        showMoveToFolderButton = true,
                                        showDownloadButton = true,
                                        showFolderBadges = true,
                                        showPlaybackProgress = true,
                                        showDownloadedBadge = true,
                                        showMarkWatchedButton = true,
                                        showMediaTypeBadge = true,
                                        fullTitles = fullTitlesHome
                                    ),
                                    onClick = { onVideoSelected(video.videoId) },
                                    onFavoriteToggle = {
                                        viewModel.toggleFavorite(video, !isFavorite)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(if (!isFavorite) "Added to favorites" else "Removed from favorites")
                                        }
                                    },
                                    onMoveToFolder = {
                                        activeVideoForSheet = video
                                        showMoveToFolderSheet = true
                                    },
                                    onDownloadClick = {
                                        expandedDownloadVideoId = video.videoId
                                        viewModel.fetchStreamsFor(video.videoId)
                                    },
                                    onMarkWatched = {
                                        val currentCount = watchCounts[video.videoId] ?: 0
                                        watchCountTargetVideoId = video.videoId
                                        watchCountTargetTitle = video.title
                                        watchCountTargetInitialCount = currentCount
                                        showWatchCountDialog = true
                                    },
                                    downloadMenuContent = {
                                        QuickDownloadMenu(
                                            isExpanded = expandedDownloadVideoId == video.videoId,
                                            onDismiss = { expandedDownloadVideoId = null },
                                            isFetching = fetchingStreamsFor == video.videoId,
                                            fetchedStreams = fetchedStreams,
                                            allDownloads = allDownloads,
                                            videoId = video.videoId,
                                            onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                                            onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                                            onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (state.isFetchingNextPage) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MediaNestColors.Accent, modifier = Modifier.size(32.dp))
                            }
                        }
                    } else if (!state.hasMore && filteredVideos.isNotEmpty()) {
                        item {
                            EndOfListIndicator()
                        }
                    }
                }

                is HomeUiState.ChannelResult -> {
                    item {
                        val isSubscribed = subscriptions.any { sub ->
                            sub.sourceType == "channel" && (
                                sub.sourceId == state.channel.channelId ||
                                sub.sourceId == state.channel.url ||
                                sub.sourceId.contains(state.channel.channelId) ||
                                sub.name.equals(state.channel.name, ignoreCase = true) ||
                                (state.channel.url.contains(sub.sourceId.removePrefix("https://").removePrefix("www.youtube.com/").removePrefix("@").removePrefix("channel/").removePrefix("c/").trim()))
                            )
                        }

                        ChannelResultHeader(
                            channel = state.channel,
                            isSubscribed = isSubscribed,
                            showShorts = showShorts,
                            hasMore = state.hasMore,
                            onToggleSubscribe = {
                                if (isSubscribed) {
                                    val matchedSub = subscriptions.firstOrNull { sub ->
                                        sub.sourceType == "channel" && (
                                            sub.sourceId == state.channel.channelId ||
                                            sub.sourceId == state.channel.url ||
                                            sub.sourceId.contains(state.channel.channelId) ||
                                            sub.name.equals(state.channel.name, ignoreCase = true) ||
                                            (state.channel.url.contains(sub.sourceId.removePrefix("https://").removePrefix("www.youtube.com/").removePrefix("@").removePrefix("channel/").removePrefix("c/").trim()))
                                        )
                                    }
                                    val subId = matchedSub?.sourceId ?: state.channel.channelId
                                    viewModel.unsubscribe(subId)
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Unsubscribed from Channel") }
                                } else {
                                    viewModel.subscribe("channel", state.channel.channelId, state.channel.name, state.channel.avatarUrl)
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Subscribed to Channel") }
                                }
                            },
                            onDownloadAll = { viewModel.setBulkQualityDialogVisible(true) },
                            onToggleShorts = { viewModel.toggleShorts(it) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    val baseUploads = if (showShorts) state.channel.uploads else state.channel.uploads.filter { !it.isShort }
                    val filteredUploads = when (selectedFormatFilter) {
                        "Videos" -> baseUploads
                        "Audio" -> emptyList()
                        else -> baseUploads
                    }

                    if (viewMode == ViewMode.GRID) {
                        val chunkedUploads = filteredUploads.chunked(2)
                        itemsIndexed(chunkedUploads, key = { chunkIdx, _ -> "channel_chunk_$chunkIdx" }) { chunkIdx, chunk ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                chunk.forEachIndexed { i, video ->
                                    val index = chunkIdx * 2 + i
                                    val history = playbackHistory.find { it.videoId == video.videoId }
                                    val positionMillis = history?.positionMillis ?: 0L
                                    val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                                        ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    val isFavorite = favoriteVideoIds.contains(video.videoId)
                                    val isDownloaded = allDownloads.any { it.videoId == video.videoId && it.status == DownloadStatus.COMPLETED }

                                    Box(modifier = Modifier.weight(1f)) {
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
                                                showMoveToFolderButton = true,
                                                showDownloadButton = true,
                                                showFolderBadges = true,
                                                showPlaybackProgress = true,
                                                showDownloadedBadge = true,
                                                showMarkWatchedButton = true,
                                                showMediaTypeBadge = true,
                                                fullTitles = fullTitlesHome
                                            ),
                                            onClick = { onVideoSelected(video.videoId) },
                                            onFavoriteToggle = {
                                                viewModel.toggleFavorite(video, !isFavorite)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(if (!isFavorite) "Added to favorites" else "Removed from favorites")
                                                }
                                            },
                                            onMoveToFolder = {
                                                activeVideoForSheet = video
                                                showMoveToFolderSheet = true
                                            },
                                            onDownloadClick = {
                                                expandedDownloadVideoId = video.videoId
                                                viewModel.fetchStreamsFor(video.videoId)
                                            },
                                            onMarkWatched = {
                                                val currentCount = watchCounts[video.videoId] ?: 0
                                                watchCountTargetVideoId = video.videoId
                                                watchCountTargetTitle = video.title
                                                watchCountTargetInitialCount = currentCount
                                                showWatchCountDialog = true
                                            },
                                            downloadMenuContent = {
                                                QuickDownloadMenu(
                                                    isExpanded = expandedDownloadVideoId == video.videoId,
                                                    onDismiss = { expandedDownloadVideoId = null },
                                                    isFetching = fetchingStreamsFor == video.videoId,
                                                    fetchedStreams = fetchedStreams,
                                                    allDownloads = allDownloads,
                                                    videoId = video.videoId,
                                                    onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                                                    onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                                                    onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                                                )
                                            }
                                        )
                                    }
                                }
                                if (chunk.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        itemsIndexed(filteredUploads, key = { _, v -> "channel_${v.videoId}" }) { index, video ->
                            val history = playbackHistory.find { it.videoId == video.videoId }
                            val positionMillis = history?.positionMillis ?: 0L
                            val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                                ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val isFavorite = favoriteVideoIds.contains(video.videoId)
                            val isDownloaded = allDownloads.any { it.videoId == video.videoId && it.status == DownloadStatus.COMPLETED }

                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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
                                        showMoveToFolderButton = true,
                                        showDownloadButton = true,
                                        showFolderBadges = true,
                                        showPlaybackProgress = true,
                                        showDownloadedBadge = true,
                                        showMarkWatchedButton = true,
                                        showMediaTypeBadge = true,
                                        fullTitles = fullTitlesHome
                                    ),
                                    onClick = { onVideoSelected(video.videoId) },
                                    onFavoriteToggle = {
                                        viewModel.toggleFavorite(video, !isFavorite)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(if (!isFavorite) "Added to favorites" else "Removed from favorites")
                                        }
                                    },
                                    onMoveToFolder = {
                                        activeVideoForSheet = video
                                        showMoveToFolderSheet = true
                                    },
                                    onDownloadClick = {
                                        expandedDownloadVideoId = video.videoId
                                        viewModel.fetchStreamsFor(video.videoId)
                                    },
                                    onMarkWatched = {
                                        val currentCount = watchCounts[video.videoId] ?: 0
                                        watchCountTargetVideoId = video.videoId
                                        watchCountTargetTitle = video.title
                                        watchCountTargetInitialCount = currentCount
                                        showWatchCountDialog = true
                                    },
                                    downloadMenuContent = {
                                        QuickDownloadMenu(
                                            isExpanded = expandedDownloadVideoId == video.videoId,
                                            onDismiss = { expandedDownloadVideoId = null },
                                            isFetching = fetchingStreamsFor == video.videoId,
                                            fetchedStreams = fetchedStreams,
                                            allDownloads = allDownloads,
                                            videoId = video.videoId,
                                            onEnqueueDownload = { info, stream -> viewModel.enqueueDownload(info, stream) },
                                            onDeleteDownload = { entity -> viewModel.deleteDownload(entity) },
                                            onExtractAudio = { entity -> viewModel.extractAudio(entity) }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (state.isFetchingNextPage) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MediaNestColors.Accent, modifier = Modifier.size(32.dp))
                            }
                        }
                    } else if (!state.hasMore && filteredUploads.isNotEmpty()) {
                        item {
                            EndOfListIndicator()
                        }
                    }
                }
            }

            // 6. Link History / Recent Activity Section
            if (uiState is HomeUiState.Idle) {
                if (currentHistory == null) {
                    item {
                        LoadingState(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                } else {
                    val filteredHistory = when (selectedFormatFilter) {
                        "Videos" -> currentHistory.filter { it.linkType.equals("VIDEO", ignoreCase = true) }
                        "Audio" -> currentHistory.filter { it.linkType.equals("AUDIO", ignoreCase = true) }
                        else -> currentHistory
                    }

                    item {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "History",
                                style = TextStyle(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MediaNestColors.TextPrimary,
                                    letterSpacing = (-0.2).sp
                                )
                            )
                            if (filteredHistory.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showClearHistoryDialog = true }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_trash),
                                        contentDescription = null,
                                        tint = MediaNestColors.Destructive,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Clear all",
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MediaNestColors.Destructive
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (filteredHistory.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MediaNestColors.Card,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_history),
                                        contentDescription = null,
                                        tint = MediaNestColors.Accent.copy(alpha = 0.7f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "No link history",
                                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MediaNestColors.TextPrimary)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Links you extract will appear here for quick re-load.",
                                        style = TextStyle(fontSize = 13.sp, color = MediaNestColors.TextSecondary),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredHistory, key = { "history_${it.url}" }) { item ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                LinkHistoryItemRow(
                                    item = item,
                                    fullTitles = fullTitlesHome,
                                    onClick = {
                                        try {
                                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("YouTube Link", item.url)
                                            clipboardManager.setPrimaryClip(clip)
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Link copied to clipboard") }
                                        } catch (e: Exception) {
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Failed to copy link") }
                                        }
                                    },
                                    onReExtract = {
                                        urlInput = item.url
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        viewModel.onUrlSubmitted(item.url)
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    },
                                    onDelete = { historyItemToDelete = item }
                                )
                            }
                        }

                        if (currentHistory.size < linkHistoryLimit) {
                            item {
                                EndOfListIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Modals, Dialogs & Sheets
    // -------------------------------------------------------------------------

    // 1. Video Action Bottom Sheet (3-dot menu)
    if (showActionSheet && activeVideoForSheet != null) {
        val targetVideo = activeVideoForSheet!!
        val isFav = favoriteVideoIds.contains(targetVideo.videoId)

        ModalBottomSheet(
            onDismissRequest = {
                showActionSheet = false
                activeVideoForSheet = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MediaNestColors.Raised,
            contentColor = MediaNestColors.TextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(MediaNestColors.Border, RoundedCornerShape(2.dp))
                )
            }
        ) {
            VideoActionBottomSheetContent(
                video = targetVideo,
                isFavorite = isFav,
                onPlay = {
                    showActionSheet = false
                    onVideoSelected(targetVideo.videoId)
                },
                onDownload = {
                    showActionSheet = false
                    viewModel.fetchStreamsFor(targetVideo.videoId)
                    showQuickDownloadSheet = true
                },
                onMoveToFolder = {
                    showActionSheet = false
                    showMoveToFolderSheet = true
                },
                onMarkWatched = {
                    showActionSheet = false
                    val currentCount = watchCounts[targetVideo.videoId] ?: 0
                    watchCountTargetVideoId = targetVideo.videoId
                    watchCountTargetTitle = targetVideo.title
                    watchCountTargetInitialCount = currentCount
                    showWatchCountDialog = true
                },
                onToggleFavorite = {
                    showActionSheet = false
                    val nextFav = !isFav
                    viewModel.toggleFavorite(targetVideo, nextFav)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(if (nextFav) "Added to favorites" else "Removed from favorites")
                    }
                }
            )
        }
    }

    // 2. Quick Download Sheet
    if (showQuickDownloadSheet && activeVideoForSheet != null) {
        val targetVideo = activeVideoForSheet!!
        val existingDownloads = allDownloads.filter { it.videoId == targetVideo.videoId }

        ModalBottomSheet(
            onDismissRequest = {
                showQuickDownloadSheet = false
                activeVideoForSheet = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MediaNestColors.Raised,
            contentColor = MediaNestColors.TextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(MediaNestColors.Border, RoundedCornerShape(2.dp))
                )
            }
        ) {
            QuickDownloadSheetContent(
                video = targetVideo,
                existingDownloads = existingDownloads,
                isFetching = fetchingStreamsFor == targetVideo.videoId,
                fetchedVideoInfo = fetchedStreams,
                onEnqueue = { stream ->
                    viewModel.enqueueDownload(targetVideo, stream)
                    showQuickDownloadSheet = false
                    activeVideoForSheet = null
                },
                onDelete = { dl ->
                    viewModel.deleteDownload(dl)
                },
                onExtractAudio = { dl ->
                    viewModel.extractAudio(dl)
                }
            )
        }
    }

    // 3. Move To Folder Sheet
    if (showMoveToFolderSheet && activeVideoForSheet != null) {
        val targetVideo = activeVideoForSheet!!

        ModalBottomSheet(
            onDismissRequest = {
                showMoveToFolderSheet = false
                activeVideoForSheet = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MediaNestColors.Raised,
            contentColor = MediaNestColors.TextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(MediaNestColors.Border, RoundedCornerShape(2.dp))
                )
            }
        ) {
            MoveToFolderSheetContent(
                video = targetVideo,
                folders = folders,
                onSelectFolder = { folder ->
                    viewModel.moveVideoToFolder(targetVideo, folder.id)
                    showMoveToFolderSheet = false
                    activeVideoForSheet = null
                    coroutineScope.launch { snackbarHostState.showSnackbar("Moved to ${folder.name}") }
                }
            )
        }
    }

    // 4. Delete History Item Dialog
    if (historyItemToDelete != null) {
        val itemToDelete = historyItemToDelete
        AlertDialog(
            onDismissRequest = { historyItemToDelete = null },
            containerColor = MediaNestColors.Raised,
            title = {
                Text(
                    text = "Delete history item",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary)
                )
            },
            text = {
                Text(
                    text = "Remove this link from your history?",
                    style = TextStyle(fontSize = 14.sp, color = MediaNestColors.TextSecondary)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let { viewModel.deleteHistoryItem(it.url) }
                        historyItemToDelete = null
                        coroutineScope.launch { snackbarHostState.showSnackbar("History item removed") }
                    }
                ) {
                    Text("Delete", color = MediaNestColors.Destructive, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { historyItemToDelete = null }) {
                    Text("Cancel", color = MediaNestColors.TextSecondary)
                }
            }
        )
    }

    // 5. Clear All History Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = MediaNestColors.Raised,
            title = {
                Text(
                    text = "Clear link history",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary)
                )
            },
            text = {
                Text(
                    text = "Remove all link history entries? This cannot be undone.",
                    style = TextStyle(fontSize = 14.sp, color = MediaNestColors.TextSecondary)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllLinkHistory()
                        showClearHistoryDialog = false
                        coroutineScope.launch { snackbarHostState.showSnackbar("Link history cleared") }
                    }
                ) {
                    Text("Clear all", color = MediaNestColors.Destructive, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = MediaNestColors.TextSecondary)
                }
            }
        )
    }

    // 6. Watch Count Dialog
    if (showWatchCountDialog) {
        WatchCountDialog(
            videoTitle = watchCountTargetTitle,
            initialCount = watchCountTargetInitialCount,
            onDismiss = { showWatchCountDialog = false },
            onConfirm = { newCount ->
                watchCountTargetVideoId?.let { videoId ->
                    viewModel.updateWatchCount(videoId, newCount)
                    coroutineScope.launch { snackbarHostState.showSnackbar("Watch count set to $newCount") }
                }
            }
        )
    }

    // 7. Bulk Quality Selection Dialog
    if (showBulkQualityDialog) {
        val qualities = listOf("1080p", "720p", "480p", "360p", "Audio")
        var selectedQuality by remember(defaultResolution) { mutableStateOf(defaultResolution) }

        AlertDialog(
            onDismissRequest = { viewModel.setBulkQualityDialogVisible(false) },
            containerColor = MediaNestColors.Raised,
            title = {
                Text(
                    text = "Download All by Resolution",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Select target resolution/format:", color = MediaNestColors.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    qualities.forEach { quality ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedQuality = quality }
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = (selectedQuality == quality),
                                onClick = { selectedQuality = quality },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MediaNestColors.Accent,
                                    unselectedColor = MediaNestColors.TextSecondary
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(if (quality == "Audio") R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                                contentDescription = null,
                                tint = if (selectedQuality == quality) MediaNestColors.Accent else MediaNestColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = when (quality) {
                                    "1080p" -> "1080p (Full HD)"
                                    "720p" -> "720p (High Definition)"
                                    "480p" -> "480p (Standard Definition)"
                                    "360p" -> "360p (Low Data Usage)"
                                    "Audio" -> "Audio Only (Opus/M4A 128kbps)"
                                    else -> quality
                                },
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedQuality == quality) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selectedQuality == quality) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val videos = when (val state = uiState) {
                            is HomeUiState.PlaylistResult -> if (showShorts) state.playlist.videos else state.playlist.videos.filter { !it.isShort }
                            is HomeUiState.ChannelResult -> if (showShorts) state.channel.uploads else state.channel.uploads.filter { !it.isShort }
                            else -> emptyList()
                        }
                        viewModel.startBulkFetch(videos, selectedQuality)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MediaNestColors.Accent, contentColor = MediaNestColors.OnAccent),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Next", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setBulkQualityDialogVisible(false) }) {
                    Text("Cancel", color = MediaNestColors.TextSecondary)
                }
            }
        )
    }

    // 8. Bulk Fetch Progress Dialog
    bulkFetchProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = MediaNestColors.Raised,
            title = {
                Text(
                    text = "Fetching Video Metadata",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary)
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Retrieving stream details to calculate size and check disk space.",
                        style = TextStyle(fontSize = 13.sp, color = MediaNestColors.TextSecondary),
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Progress:", style = TextStyle(fontSize = 13.sp, color = MediaNestColors.TextSecondary))
                        Text(
                            "${progress.current} of ${progress.total} videos",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MediaNestColors.Accent)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (progress.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total.toFloat() },
                            color = MediaNestColors.YouTubeRed,
                            trackColor = MediaNestColors.ProgressTrack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else {
                        CircularProgressIndicator(color = MediaNestColors.Accent, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = progress.currentTitle,
                        style = TextStyle(fontSize = 12.sp, color = MediaNestColors.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelBulkFetch() }) {
                    Text("Cancel", color = MediaNestColors.Destructive)
                }
            }
        )
    }

    // 9. Bulk Download Confirmation Dialog
    bulkDownloadConfirmation?.let { confirmation ->
        val hasSpace = confirmation.usableSpace > confirmation.totalSize
        val formattedSize = Formatter.formatShortFileSize(context, confirmation.totalSize)
        val formattedSpace = Formatter.formatShortFileSize(context, confirmation.usableSpace)

        AlertDialog(
            onDismissRequest = { viewModel.dismissBulkConfirmation() },
            containerColor = MediaNestColors.Raised,
            title = {
                Text(
                    text = "Confirm Bulk Download",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Quality:", color = MediaNestColors.TextSecondary, fontSize = 13.sp)
                        Text(confirmation.quality, color = MediaNestColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Total Videos:", color = MediaNestColors.TextSecondary, fontSize = 13.sp)
                        Text("${confirmation.totalVideoCount}", color = MediaNestColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Total Download Size:", color = MediaNestColors.TextSecondary, fontSize = 13.sp)
                        Text(formattedSize, color = MediaNestColors.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Available Space:", color = MediaNestColors.TextSecondary, fontSize = 13.sp)
                        Text(formattedSpace, color = MediaNestColors.TextPrimary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (hasSpace) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_check_circle),
                                contentDescription = null,
                                tint = MediaNestColors.Success,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Storage check: Sufficient space available.",
                                color = MediaNestColors.Success,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        val neededBytes = confirmation.totalSize - confirmation.usableSpace
                        val formattedNeeded = Formatter.formatShortFileSize(context, neededBytes)
                        Text(
                            text = "Warning: Insufficient storage! Extra $formattedNeeded needed.",
                            color = MediaNestColors.Destructive,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmBulkDownload(confirmation.jobId) },
                    colors = ButtonDefaults.buttonColors(containerColor = MediaNestColors.Accent, contentColor = MediaNestColors.OnAccent),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(if (hasSpace) "Download" else "Download Anyway", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBulkConfirmation() }) {
                    Text("Cancel", color = MediaNestColors.TextSecondary)
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------
// Component Implementations
// -----------------------------------------------------------------------------

@Composable
fun HomeTopBar(
    viewMode: ViewMode = ViewMode.GRID,
    onToggleViewMode: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    MediaNestTopAppBar(
        title = "MediaNest",
        subtitle = "Offline Media Hub",
        modifier = modifier,
        titleContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = "MediaNest Icon",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column {
                    Text(
                        text = "MediaNest",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MediaNestColors.TextPrimary,
                            letterSpacing = (-0.3).sp
                        )
                    )
                    Text(
                        text = "Offline Media Hub",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MediaNestColors.TextSecondary
                        )
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onToggleViewMode) {
                Icon(
                    painter = painterResource(if (viewMode == ViewMode.GRID) R.drawable.ic_mn_list else R.drawable.ic_mn_grid),
                    contentDescription = if (viewMode == ViewMode.GRID) "Switch to list view" else "Switch to grid view",
                    tint = MediaNestColors.TextPrimary
                )
            }
            NotificationBellAction(onClick = onNotificationsClick)
        }
    )
}

@Composable
fun ContinueWatchingCard(
    video: VideoEntity,
    history: HistoryEntity,
    isAudio: Boolean,
    progressFraction: Float,
    posSec: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MediaNestColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Border),
        modifier = modifier
            .width(220.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // 16:9 Thumbnail Preview with Type Badge, Centered Play Overlay & YouTube Red Progress Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MediaNestColors.PlayerSurface)
            ) {
                SubcomposeAsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MediaNestColors.Accent,
                                strokeWidth = 3.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MediaNestColors.ThumbnailPlaceholder)
                        )
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark vignette overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        )
                )

                // Top-Left Media Type Badge (ic_mn_video or ic_mn_music)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MediaNestColors.PlayerSurface.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        painter = painterResource(if (isAudio) R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                        contentDescription = if (isAudio) "Audio" else "Video",
                        tint = MediaNestColors.TextPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }

                // Centered Play Button Overlay
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                        .background(MediaNestColors.PlayerSurface.copy(alpha = 0.8f), CircleShape)
                        .border(1.dp, MediaNestColors.GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_play),
                        contentDescription = "Resume",
                        tint = MediaNestColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Bottom-Right Position Badge
                if (posSec > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 6.dp, end = 6.dp)
                            .background(MediaNestColors.PlayerSurface.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = UiUtils.formatDuration(posSec),
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = MediaNestColors.TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                // Bottom 3dp YouTube Red Progress Strip
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(MediaNestColors.ProgressTrack)
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

            Spacer(Modifier.height(8.dp))

            // Video Title
            Text(
                text = video.title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MediaNestColors.TextPrimary,
                    lineHeight = 17.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            // Channel / Meta line
            Text(
                text = if (posSec > 0) "Left off at ${UiUtils.formatDuration(posSec)}" else video.channelName,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = MediaNestColors.TextSecondary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HeroExtractionPanel(
    urlValue: String,
    onUrlChange: (String) -> Unit,
    onExtractClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    fullTitles: Boolean = false,
    onFullTitlesChange: ((Boolean) -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
    showClear: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MediaNestColors.Raised,
        border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Border),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MediaNestColors.AccentDeep.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        radius = 600f
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "OFFLINE-FIRST",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                                color = MediaNestColors.Accent
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "MediaNest",
                            style = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MediaNestColors.TextPrimary,
                                letterSpacing = (-0.4).sp
                            )
                        )
                    }

                    if (onFullTitlesChange != null) {
                        FullTitlesToggle(
                            checked = fullTitles,
                            onCheckedChange = onFullTitlesChange
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Download, organize and play your YouTube library — even offline.",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = MediaNestColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Quick-Paste & Search Pill
                QuickPasteSearchPill(
                    value = urlValue,
                    onValueChange = onUrlChange,
                    onExtractClick = onExtractClick,
                    isLoading = isLoading
                )

                if (showClear && onClearAll != null) {
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onClearAll() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_close),
                                contentDescription = null,
                                tint = MediaNestColors.Accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Clear",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MediaNestColors.Accent
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickPasteSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    onExtractClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MediaNestColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Border),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mn_youtube),
                contentDescription = null,
                tint = MediaNestColors.YouTubeRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Search or paste URL...",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = MediaNestColors.TextSecondary.copy(alpha = 0.75f)
                        )
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MediaNestColors.TextPrimary,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(MediaNestColors.Accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { onExtractClick() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_close),
                        contentDescription = "Clear",
                        tint = MediaNestColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // PASTE Button
            IconButton(
                onClick = {
                    try {
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = clipboardManager?.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!text.isNullOrEmpty()) {
                                onValueChange(text)
                            }
                        }
                    } catch (_: Exception) {
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MediaNestColors.Accent,
                    contentColor = MediaNestColors.OnAccent
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mn_paste),
                    contentDescription = "Paste",
                    tint = MediaNestColors.OnAccent,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // EXTRACT Button
            IconButton(
                onClick = onExtractClick,
                enabled = !isLoading,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MediaNestColors.Accent,
                    contentColor = MediaNestColors.OnAccent,
                    disabledContainerColor = MediaNestColors.Accent.copy(alpha = 0.4f),
                    disabledContentColor = MediaNestColors.OnAccent.copy(alpha = 0.6f)
                ),
                modifier = Modifier.size(36.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MediaNestColors.OnAccent,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_extract),
                        contentDescription = "Extract",
                        tint = MediaNestColors.OnAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FormatFilterPills(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        Triple("All", "All", R.drawable.ic_mn_list),
        Triple("Videos", "Videos", R.drawable.ic_mn_video),
        Triple("Audio", "Audio", R.drawable.ic_mn_music)
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (id, label, iconRes) ->
            val isSelected = selectedFilter == id
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MediaNestColors.AccentDeep else MediaNestColors.Card,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) MediaNestColors.AccentDeep else MediaNestColors.Border
                ),
                modifier = Modifier
                    .height(36.dp)
                    .clickable { onFilterSelected(id) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (isSelected) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = label,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    actionIconRes: Int? = null,
    actionColor: Color = MediaNestColors.Accent,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MediaNestColors.TextPrimary,
                letterSpacing = (-0.2).sp
            )
        )

        if (actionText != null && onActionClick != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onActionClick() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (actionIconRes != null) {
                    Icon(
                        painter = painterResource(actionIconRes),
                        contentDescription = null,
                        tint = actionColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = actionText,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = actionColor
                    )
                )
                if (actionIconRes == null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_chevron_right),
                        contentDescription = null,
                        tint = actionColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistResultHeader(
    playlist: ExtractedPlaylistInfo,
    isSaved: Boolean,
    showShorts: Boolean,
    onToggleSave: () -> Unit,
    onDownloadAll: () -> Unit,
    onToggleShorts: (Boolean) -> Unit,
    hasMore: Boolean = false,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MediaNestColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Border),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Cover Image
            val coverUrl = UiUtils.upgradePlaylistThumbnail(playlist.thumbnailUrl, playlist.videos.firstOrNull()?.thumbnailUrl)
            if (!coverUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = coverUrl,
                    contentDescription = playlist.name,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MediaNestColors.Accent,
                                strokeWidth = 3.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MediaNestColors.ThumbnailPlaceholder)
                        )
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MediaNestColors.PlayerSurface)
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = "PLAYLIST",
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MediaNestColors.Accent)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = playlist.name,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary)
            )
            Spacer(Modifier.height(2.dp))
            val videoCountText = if (hasMore) {
                if (playlist.videoCount > playlist.videos.size) {
                    "Videos: ${playlist.videos.size} loaded of ${playlist.videoCount} • Scroll to load more"
                } else {
                    "Videos: ${playlist.videos.size} loaded • Scroll to load more"
                }
            } else {
                "Videos: ${if (playlist.videos.isNotEmpty()) playlist.videos.size else playlist.videoCount}"
            } + if (!playlist.uploaderName.isNullOrBlank()) " · ${playlist.uploaderName}" else ""

            Text(
                text = videoCountText,
                style = TextStyle(fontSize = 13.sp, color = MediaNestColors.TextSecondary)
            )

            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    style = TextStyle(fontSize = 12.sp, color = MediaNestColors.TextSecondary.copy(alpha = 0.8f)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSaved) MediaNestColors.Raised else MediaNestColors.Accent,
                        contentColor = if (isSaved) MediaNestColors.TextPrimary else MediaNestColors.OnAccent
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(
                        painter = painterResource(if (isSaved) R.drawable.ic_mn_check else R.drawable.ic_mn_playlist),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isSaved) "Saved" else "Add to Playlist",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onDownloadAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MediaNestColors.AccentDeep,
                        contentColor = MediaNestColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_download),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Download All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Show Shorts Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Shorts",
                    style = TextStyle(fontSize = 13.sp, color = MediaNestColors.TextSecondary)
                )
                Switch(
                    checked = showShorts,
                    onCheckedChange = onToggleShorts,
                    colors = mediaNestSwitchColors(),
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
    }
}

@Composable
fun ChannelResultHeader(
    channel: com.example.medianest.data.model.ChannelInfo,
    isSubscribed: Boolean,
    showShorts: Boolean,
    onToggleSubscribe: () -> Unit,
    onDownloadAll: () -> Unit,
    onToggleShorts: (Boolean) -> Unit,
    hasMore: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MediaNestColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Border),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Channel Banner (only if real banner exists)
            val bannerUrl = channel.bannerUrl?.takeIf { it.isNotBlank() && it != channel.avatarUrl }
            if (!bannerUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = UiUtils.upgradeBannerUrl(bannerUrl),
                    contentDescription = "Channel Banner",
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MediaNestColors.Accent,
                                strokeWidth = 3.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MediaNestColors.ThumbnailPlaceholder)
                        )
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MediaNestColors.PlayerSurface)
                )
                Spacer(Modifier.height(12.dp))
            }

            // Channel Avatar & Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AsyncImage(
                    model = UiUtils.upgradeAvatarUrl(channel.avatarUrl),
                    contentDescription = channel.name,
                    placeholder = ColorPainter(MediaNestColors.ThumbnailPlaceholder),
                    error = ColorPainter(MediaNestColors.ThumbnailPlaceholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MediaNestColors.PlayerSurface)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CHANNEL",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MediaNestColors.Accent)
                    )
                    Text(
                        text = channel.name,
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary)
                    )
                    val videoCountText = if (hasMore) {
                        "Videos: ${channel.uploads.size} loaded • Scroll to load more"
                    } else {
                        "Videos: ${channel.uploads.size}"
                    }
                    Text(
                        text = videoCountText,
                        style = TextStyle(fontSize = 12.sp, color = MediaNestColors.TextSecondary)
                    )
                }
            }

            if (!channel.description.isNullOrBlank() && channel.description != "Offline Fallback") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = channel.description,
                    style = TextStyle(fontSize = 12.sp, color = MediaNestColors.TextSecondary.copy(alpha = 0.8f)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleSubscribe,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSubscribed) MediaNestColors.Raised else MediaNestColors.YouTubeRed,
                        contentColor = MediaNestColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(
                        painter = painterResource(if (isSubscribed) R.drawable.ic_mn_check else R.drawable.ic_mn_youtube),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isSubscribed) "Subscribed" else "Subscribe",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onDownloadAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MediaNestColors.AccentDeep,
                        contentColor = MediaNestColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_download),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Download All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Show Shorts Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Shorts",
                    style = TextStyle(fontSize = 13.sp, color = MediaNestColors.TextSecondary)
                )
                Switch(
                    checked = showShorts,
                    onCheckedChange = onToggleShorts,
                    colors = mediaNestSwitchColors(),
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
    }
}

@Composable
fun HomeMediaRow(
    video: ExtractedVideoInfo,
    serialNumber: Int? = null,
    isFavorite: Boolean = false,
    playbackProgressFraction: Float = 0f,
    watchCount: Int = 0,
    isExpanded: Boolean? = null,
    onTitleToggle: () -> Unit = {},
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onMoreClick: () -> Unit,
    fullTitles: Boolean = LocalFullTitles.current,
    modifier: Modifier = Modifier
) {
    var isTitleExpanded by remember(fullTitles, isExpanded) { mutableStateOf(isExpanded ?: fullTitles) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MediaNestColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Border),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 16:9 Thumbnail with 12dp radius, duration badge, progress strip
            Box(
                modifier = Modifier
                    .width(116.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MediaNestColors.PlayerSurface)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    placeholder = ColorPainter(MediaNestColors.ThumbnailPlaceholder),
                    error = ColorPainter(MediaNestColors.ThumbnailPlaceholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-Left Media Type Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(MediaNestColors.PlayerSurface.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(3.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_video),
                        contentDescription = null,
                        tint = MediaNestColors.TextPrimary,
                        modifier = Modifier.size(10.dp)
                    )
                }

                // Bottom-Left Watch Count Badge
                if (watchCount > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(MediaNestColors.PlayerSurface.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_eye),
                            contentDescription = null,
                            tint = MediaNestColors.TextPrimary,
                            modifier = Modifier.size(9.dp)
                        )
                        Text(
                            text = "$watchCount",
                            style = TextStyle(fontSize = 9.sp, color = MediaNestColors.TextPrimary, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Bottom-Right Duration Badge
                if (video.durationSeconds > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(MediaNestColors.PlayerSurface.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = UiUtils.formatDuration(video.durationSeconds),
                            style = TextStyle(fontSize = 9.sp, color = MediaNestColors.TextPrimary, fontWeight = FontWeight.Medium)
                        )
                    }
                }

                // Bottom Edge YouTube Red Progress Strip
                if (playbackProgressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.BottomCenter)
                            .background(MediaNestColors.ProgressTrack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(playbackProgressFraction.coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(MediaNestColors.YouTubeRed)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Body Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize()
            ) {
                val displayTitle = if (serialNumber != null) "$serialNumber. ${video.title}" else video.title

                Text(
                    text = displayTitle,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MediaNestColors.TextPrimary,
                        lineHeight = 19.sp
                    ),
                    maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isTitleExpanded = !isTitleExpanded
                        onTitleToggle()
                    }
                )

                if (!video.description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = video.description,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = MediaNestColors.TextSecondary
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (video.channelName.isNotBlank()) {
                        Text(
                            text = video.channelName,
                            style = TextStyle(fontSize = 12.sp, color = MediaNestColors.TextSecondary),
                            maxLines = if (isTitleExpanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (!video.uploadDate.isNullOrBlank()) {
                        val formattedDate = UiUtils.formatReleaseDate(video.uploadDate)
                        if (!formattedDate.isNullOrBlank()) {
                            Text(
                                text = "· $formattedDate",
                                style = TextStyle(fontSize = 11.sp, color = MediaNestColors.TextSecondary.copy(alpha = 0.7f)),
                                maxLines = if (isTitleExpanded) Int.MAX_VALUE else 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // Actions: Favorite & 3-dot More
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_heart),
                        contentDescription = "Favorite",
                        tint = if (isFavorite) MediaNestColors.Accent else MediaNestColors.TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_more),
                        contentDescription = "More",
                        tint = MediaNestColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LinkHistoryItemRow(
    item: LinkHistoryEntity,
    onClick: () -> Unit,
    onReExtract: () -> Unit,
    onDelete: () -> Unit,
    fullTitles: Boolean = LocalFullTitles.current,
    modifier: Modifier = Modifier
) {
    var isTitleExpanded by remember(fullTitles) { mutableStateOf(fullTitles) }

    val (typeIconRes, typeLabel) = when (item.linkType.uppercase()) {
        "VIDEO" -> Pair(R.drawable.ic_mn_video, "Video")
        "PLAYLIST" -> Pair(R.drawable.ic_mn_playlist, "Playlist")
        "CHANNEL" -> Pair(R.drawable.ic_mn_channel, "Channel")
        else -> Pair(R.drawable.ic_mn_file, "Link")
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MediaNestColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Border),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MediaNestColors.Raised, RoundedCornerShape(10.dp))
                    .border(1.dp, MediaNestColors.Border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(typeIconRes),
                    contentDescription = typeLabel,
                    tint = MediaNestColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MediaNestColors.TextPrimary),
                    maxLines = if (isTitleExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { isTitleExpanded = !isTitleExpanded }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$typeLabel · ${item.url}",
                    style = TextStyle(fontSize = 11.sp, color = MediaNestColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onReExtract,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mn_play),
                    contentDescription = "Load",
                    tint = MediaNestColors.Accent,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mn_trash),
                    contentDescription = "Delete",
                    tint = MediaNestColors.Destructive,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ExtractionLoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Extracting…",
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MediaNestColors.Accent),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        CircularProgressIndicator(
            color = MediaNestColors.Accent,
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp
        )
    }
}

@Composable
fun VideoActionBottomSheetContent(
    video: ExtractedVideoInfo,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onMoveToFolder: () -> Unit,
    onMarkWatched: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        // Video Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                placeholder = ColorPainter(MediaNestColors.ThumbnailPlaceholder),
                error = ColorPainter(MediaNestColors.ThumbnailPlaceholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 64.dp, height = 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MediaNestColors.PlayerSurface)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.channelName,
                    style = TextStyle(fontSize = 12.sp, color = MediaNestColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MediaNestColors.Border))
        Spacer(Modifier.height(8.dp))

        // Action Rows
        ActionRowItem(iconRes = R.drawable.ic_mn_play, title = "Play Video", onClick = onPlay)
        ActionRowItem(iconRes = R.drawable.ic_mn_download, title = "Download / Formats", onClick = onDownload)
        ActionRowItem(iconRes = R.drawable.ic_mn_folder, title = "Move to Folder", onClick = onMoveToFolder)
        ActionRowItem(iconRes = R.drawable.ic_mn_eye, title = "Mark as Watched", onClick = onMarkWatched)
        ActionRowItem(
            iconRes = if (isFavorite) R.drawable.ic_mn_heart_filled else R.drawable.ic_mn_heart,
            title = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
            iconTint = if (isFavorite) MediaNestColors.Accent else MediaNestColors.TextPrimary,
            onClick = onToggleFavorite
        )
    }
}

@Composable
fun ActionRowItem(
    iconRes: Int,
    title: String,
    iconTint: Color = MediaNestColors.TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MediaNestColors.TextPrimary)
        )
    }
}

@Composable
fun QuickDownloadSheetContent(
    video: ExtractedVideoInfo,
    existingDownloads: List<DownloadEntity>,
    isFetching: Boolean,
    fetchedVideoInfo: ExtractedVideoInfo?,
    onEnqueue: (StreamSource) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    onExtractAudio: (DownloadEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Download Options",
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = video.title,
            style = TextStyle(fontSize = 12.sp, color = MediaNestColors.TextSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(14.dp))

        // Existing Downloads Section
        if (existingDownloads.isNotEmpty()) {
            Text(
                text = "DOWNLOADED FORMATS",
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MediaNestColors.Accent)
            )
            Spacer(Modifier.height(8.dp))
            existingDownloads.forEach { dl ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = dl.quality,
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MediaNestColors.TextPrimary)
                        )
                        Text(
                            text = "Status: ${dl.status}",
                            style = TextStyle(fontSize = 12.sp, color = if (dl.status == DownloadStatus.COMPLETED) MediaNestColors.Success else MediaNestColors.Accent)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (dl.format == "video" && dl.status == DownloadStatus.COMPLETED) {
                            IconButton(onClick = { onExtractAudio(dl) }) {
                                Icon(painter = painterResource(R.drawable.ic_mn_music), contentDescription = "Extract Audio", tint = MediaNestColors.Accent)
                            }
                        }
                        IconButton(onClick = { onDelete(dl) }) {
                            Icon(painter = painterResource(R.drawable.ic_mn_trash), contentDescription = "Delete", tint = MediaNestColors.Destructive)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MediaNestColors.Border))
            Spacer(Modifier.height(12.dp))
        }

        // Available Streams Section
        Text(
            text = "AVAILABLE QUALITIES",
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MediaNestColors.Accent)
        )
        Spacer(Modifier.height(8.dp))

        if (isFetching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MediaNestColors.Accent, modifier = Modifier.size(32.dp))
            }
        } else {
            val sources = fetchedVideoInfo?.streamSources ?: video.streamSources
            if (sources.isEmpty()) {
                // Fallback default stream list
                val defaultQualities = listOf("1080p", "720p", "480p", "360p", "Audio")
                defaultQualities.forEach { q ->
                    val isAudio = q == "Audio"
                    val mockSource = StreamSource(
                        url = "https://www.youtube.com/watch?v=${video.videoId}",
                        format = if (isAudio) "audio" else "video",
                        quality = if (isAudio) "128kbps (opus)" else q,
                        mimeType = "",
                        codec = if (isAudio) "opus" else "vp9",
                        contentLength = null
                    )
                    StreamRowButton(
                        source = mockSource,
                        durationSec = video.durationSeconds,
                        onClick = { onEnqueue(mockSource) }
                    )
                }
            } else {
                sources.forEach { stream ->
                    StreamRowButton(
                        source = stream,
                        durationSec = video.durationSeconds,
                        onClick = { onEnqueue(stream) }
                    )
                }
            }
        }
    }
}

@Composable
fun StreamRowButton(
    source: StreamSource,
    durationSec: Long,
    onClick: () -> Unit
) {
    val isAudio = source.format.contains("audio", ignoreCase = true)
    val sizeText = source.contentLength?.let {
        Formatter.formatShortFileSize(LocalContext.current, it)
    } ?: run {
        val estBitrate = when (source.quality) {
            "1080p" -> 4500000L
            "720p" -> 2500000L
            "480p" -> 1200000L
            "360p" -> 800000L
            else -> 128000L
        }
        val bytes = (durationSec * estBitrate) / 8
        Formatter.formatShortFileSize(LocalContext.current, bytes)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MediaNestColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(if (isAudio) R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                    contentDescription = null,
                    tint = MediaNestColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isAudio) "Audio Only (${source.quality})" else "${source.quality} (${source.codec.ifBlank { "mp4" }})",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MediaNestColors.TextPrimary)
                )
            }
            Text(
                text = sizeText,
                style = TextStyle(fontSize = 12.sp, color = MediaNestColors.TextSecondary)
            )
        }
    }
}

@Composable
fun MoveToFolderSheetContent(
    video: ExtractedVideoInfo,
    folders: List<FolderEntity>,
    onSelectFolder: (FolderEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Move to Folder",
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = video.title,
            style = TextStyle(fontSize = 12.sp, color = MediaNestColors.TextSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(16.dp))

        if (folders.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MediaNestColors.Card,
                border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_folder),
                        contentDescription = null,
                        tint = MediaNestColors.Accent,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No folders found",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MediaNestColors.TextPrimary)
                    )
                    Text(
                        text = "Create folders in the Collections tab first.",
                        style = TextStyle(fontSize = 12.sp, color = MediaNestColors.TextSecondary)
                    )
                }
            }
        } else {
            folders.forEach { folder ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MediaNestColors.Card,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MediaNestColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectFolder(folder) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_folder),
                                contentDescription = null,
                                tint = MediaNestColors.Accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = folder.name,
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MediaNestColors.TextPrimary)
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_chevron_right),
                            contentDescription = null,
                            tint = MediaNestColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Backwards-Compatible Composables (for InlineSubscriptionScreen.kt and other callers)
// -----------------------------------------------------------------------------

@Composable
fun VideoResultCard(
    video: ExtractedVideoInfo,
    isFavorite: Boolean,
    folders: List<FolderEntity> = emptyList(),
    playbackProgressFraction: Float = 0f,
    watchCount: Int = 0,
    onSelectQuality: () -> Unit,
    onFavoriteToggle: ((ExtractedVideoInfo, Boolean) -> Unit)? = null,
    onMarkWatched: () -> Unit = {},
    fullTitles: Boolean = LocalFullTitles.current
) {
    HomeMediaRow(
        video = video,
        isFavorite = isFavorite,
        playbackProgressFraction = playbackProgressFraction,
        watchCount = watchCount,
        onClick = onSelectQuality,
        onFavoriteToggle = { onFavoriteToggle?.invoke(video, !isFavorite) },
        onMoreClick = onSelectQuality,
        fullTitles = fullTitles
    )
}

@Composable
fun VideoListItem(
    video: ExtractedVideoInfo,
    isFavorite: Boolean,
    folders: List<FolderEntity> = emptyList(),
    playbackProgressFraction: Float = 0f,
    watchCount: Int = 0,
    onClick: () -> Unit,
    showChannelName: Boolean = true,
    onFavoriteToggle: ((ExtractedVideoInfo, Boolean) -> Unit)? = null,
    onMoveToFolder: ((ExtractedVideoInfo) -> Unit)? = null,
    onDownloadClick: ((String) -> Unit)? = null,
    downloadMenuContent: (@Composable () -> Unit)? = null,
    serialNumber: Int? = null,
    onMarkWatched: () -> Unit = {},
    fullTitles: Boolean = LocalFullTitles.current
) {
    HomeMediaRow(
        video = video,
        serialNumber = serialNumber,
        isFavorite = isFavorite,
        playbackProgressFraction = playbackProgressFraction,
        watchCount = watchCount,
        onClick = onClick,
        onFavoriteToggle = { onFavoriteToggle?.invoke(video, !isFavorite) },
        onMoreClick = { onDownloadClick?.invoke(video.videoId) },
        fullTitles = fullTitles
    )
}
