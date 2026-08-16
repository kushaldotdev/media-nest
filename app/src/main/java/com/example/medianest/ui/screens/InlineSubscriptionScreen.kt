package com.example.medianest.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.data.model.ChannelInfo
import com.example.medianest.data.model.ExtractedPlaylistInfo
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.components.QuickDownloadMenu
import com.example.medianest.ui.components.WatchCountDialog
import com.example.medianest.ui.components.YoutubeSubscribeButton
import com.example.medianest.ui.viewmodel.HomeUiState
import com.example.medianest.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineSubscriptionScreen(
    sourceType: String,
    sourceId: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
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

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadNextPage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (sourceType == "playlist") "Back to Playlists" else "Back to Channels",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                    val filteredVideos = if (showShorts) {
                        state.playlist.videos
                    } else {
                        state.playlist.videos.filter { !it.isShort }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
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
                        }

                        item {
                            Text(
                                text = "Videos",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        if (filteredVideos.isEmpty()) {
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
                                items = filteredVideos,
                                key = { _, video -> video.videoId }
                            ) { index, video ->
                                val history = playbackHistory.find { it.videoId == video.videoId }
                                val positionMillis = history?.positionMillis ?: 0L
                                val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                                    ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                                } else 0f

                                VideoListItem(
                                    video = video,
                                    isFavorite = favoriteVideoIds.contains(video.videoId),
                                    folders = videoFolderMap[video.videoId] ?: emptyList(),
                                    playbackProgressFraction = progressFraction,
                                    watchCount = watchCounts[video.videoId] ?: 0,
                                    onClick = { onVideoClick(video.videoId) },
                                    onFavoriteToggle = { videoObj, fav ->
                                        viewModel.toggleFavorite(videoObj, fav)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (fav) "Added to favorites" else "Removed from favorites"
                                            )
                                        }
                                    },
                                    onMarkWatched = {
                                        val currentCount = watchCounts[video.videoId] ?: 0
                                        watchCountTargetVideoId = video.videoId
                                        watchCountTargetTitle = video.title
                                        watchCountTargetInitialCount = currentCount
                                        showWatchCountDialog = true
                                    },
                                    onMoveToFolder = {
                                        videoToMove = video
                                        showMoveToFolderDialog = true
                                    },
                                    onDownloadClick = { id ->
                                        expandedDownloadVideoId = id
                                        viewModel.fetchStreamsFor(id)
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
                                    },
                                    serialNumber = index + 1
                                )
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
                                    CircularProgressIndicator()
                                }
                            }
                        }
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
                    val filteredVideos = if (showShorts) {
                        state.channel.uploads
                    } else {
                        state.channel.uploads.filter { !it.isShort }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
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
                        }

                        item {
                            Text(
                                text = "Videos",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        if (filteredVideos.isEmpty()) {
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
                                items = filteredVideos,
                                key = { _, video -> video.videoId }
                            ) { index, video ->
                                val history = playbackHistory.find { it.videoId == video.videoId }
                                val positionMillis = history?.positionMillis ?: 0L
                                val progressFraction = if (video.durationSeconds > 0 && positionMillis > 0) {
                                    ((positionMillis.toFloat() / 1000f) / video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                                } else 0f

                                VideoListItem(
                                    video = video,
                                    isFavorite = favoriteVideoIds.contains(video.videoId),
                                    folders = videoFolderMap[video.videoId] ?: emptyList(),
                                    playbackProgressFraction = progressFraction,
                                    watchCount = watchCounts[video.videoId] ?: 0,
                                    onClick = { onVideoClick(video.videoId) },
                                    onFavoriteToggle = { videoObj, fav ->
                                        viewModel.toggleFavorite(videoObj, fav)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (fav) "Added to favorites" else "Removed from favorites"
                                            )
                                        }
                                    },
                                    onMarkWatched = {
                                        val currentCount = watchCounts[video.videoId] ?: 0
                                        watchCountTargetVideoId = video.videoId
                                        watchCountTargetTitle = video.title
                                        watchCountTargetInitialCount = currentCount
                                        showWatchCountDialog = true
                                    },
                                    onMoveToFolder = {
                                        videoToMove = video
                                        showMoveToFolderDialog = true
                                    },
                                    onDownloadClick = { id ->
                                        expandedDownloadVideoId = id
                                        viewModel.fetchStreamsFor(id)
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
                                    },
                                    serialNumber = index + 1
                                )
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
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }

                is HomeUiState.Success -> {
                    viewModel.cacheResult(state.video)
                    val history = playbackHistory.find { it.videoId == state.video.videoId }
                    val positionMillis = history?.positionMillis ?: 0L
                    val progressFraction = if (state.video.durationSeconds > 0 && positionMillis > 0) {
                        ((positionMillis.toFloat() / 1000f) / state.video.durationSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            VideoListItem(
                                video = state.video,
                                isFavorite = favoriteVideoIds.contains(state.video.videoId),
                                folders = videoFolderMap[state.video.videoId] ?: emptyList(),
                                playbackProgressFraction = progressFraction,
                                watchCount = watchCounts[state.video.videoId] ?: 0,
                                onClick = { onVideoClick(state.video.videoId) },
                                onFavoriteToggle = { videoObj, fav ->
                                    viewModel.toggleFavorite(videoObj, fav)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (fav) "Added to favorites" else "Removed from favorites"
                                        )
                                    }
                                },
                                onMarkWatched = {
                                    val currentCount = watchCounts[state.video.videoId] ?: 0
                                    watchCountTargetVideoId = state.video.videoId
                                    watchCountTargetTitle = state.video.title
                                    watchCountTargetInitialCount = currentCount
                                    showWatchCountDialog = true
                                },
                                onMoveToFolder = {
                                    videoToMove = state.video
                                    showMoveToFolderDialog = true
                                },
                                onDownloadClick = { id ->
                                    expandedDownloadVideoId = id
                                    viewModel.fetchStreamsFor(id)
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
                                },
                                serialNumber = 1
                            )
                        }
                    }
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Saved to Playlist", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Button(
                        onClick = onToggleSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add to Playlist", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Button(
                    onClick = onDownloadAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download All", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download All", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
