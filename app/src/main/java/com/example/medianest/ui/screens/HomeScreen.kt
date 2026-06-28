package com.example.medianest.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.layout.heightIn
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.example.medianest.data.model.ExtractedVideoInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.example.medianest.ui.utils.UiUtils
import com.example.medianest.ui.viewmodel.HomeUiState
import com.example.medianest.ui.viewmodel.HomeViewModel
import com.example.medianest.ui.components.UnifiedVideoRow
import com.example.medianest.ui.components.UnifiedVideoCard
import com.example.medianest.ui.components.VideoCardConfig
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.components.QuickDownloadMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onVideoSelected: (String) -> Unit = {},
    onSubscribe: (sourceType: String, sourceId: String, name: String, thumbnailUrl: String?) -> Unit = { _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val linkHistory by viewModel.linkHistory.collectAsStateWithLifecycle()
    val showShorts by viewModel.showShorts.collectAsStateWithLifecycle()
    val favoriteVideoIds by viewModel.favoriteVideoIds.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val videoFolderMap by viewModel.videoFolderMap.collectAsStateWithLifecycle()
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val fetchingStreamsFor by viewModel.fetchingStreamsFor.collectAsStateWithLifecycle()
    val fetchedStreams by viewModel.fetchedStreams.collectAsStateWithLifecycle()
    val playbackHistory by viewModel.playbackHistory.collectAsStateWithLifecycle()
    val showBulkQualityDialog by viewModel.showBulkQualityDialog.collectAsStateWithLifecycle()
    val bulkFetchProgress by viewModel.bulkFetchProgress.collectAsStateWithLifecycle()
    val bulkDownloadConfirmation by viewModel.bulkDownloadConfirmation.collectAsStateWithLifecycle()
    var urlInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    var videoToMove by remember { mutableStateOf<ExtractedVideoInfo?>(null) }
    var expandedDownloadVideoId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Paste YouTube URL") },
                        singleLine = true,
                        trailingIcon = {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { urlInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear text"
                                    )
                                }
                            }
                        }
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            viewModel.onUrlSubmitted(urlInput.trim())
                        },
                        enabled = uiState !is HomeUiState.Loading
                    ) {
                        Text("Extract")
                    }
                }
            }
            


            when (val state = uiState) {
                is HomeUiState.Idle -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Enter a YouTube URL to get started",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is HomeUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is HomeUiState.Error -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = state.message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
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
                        VideoResultCard(
                            video = state.video,
                            isFavorite = favoriteVideoIds.contains(state.video.videoId),
                            folders = videoFolderMap[state.video.videoId] ?: emptyList(),
                            playbackProgressFraction = progressFraction,
                            onSelectQuality = { onVideoSelected(state.video.videoId) },
                            onFavoriteToggle = { video, fav -> 
                                viewModel.toggleFavorite(video, fav)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (fav) "Added to favorites" else "Removed from favorites"
                                    )
                                }
                            }
                        )
                    }
                }
                is HomeUiState.PlaylistResult -> {
                    item {
                        Column {
                            Text(
                                "Playlist: ${state.playlist.name}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            val isSaved = subscriptions.any { it.sourceId == state.playlist.playlistId }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { 
                                        if (isSaved) {
                                            viewModel.unsubscribe(state.playlist.playlistId)
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Removed from Playlist") }
                                        } else {
                                            viewModel.subscribe("playlist", state.playlist.playlistId, state.playlist.name, state.playlist.thumbnailUrl)
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Added to Playlist") }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = if (isSaved) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                        contentColor = if (isSaved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(if (isSaved) "Saved to Playlist" else "Add to Playlist")
                                }
                                Button(
                                    onClick = { 
                                        viewModel.setBulkQualityDialogVisible(true)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Download All")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show Shorts", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Switch(
                                    checked = showShorts,
                                    onCheckedChange = { viewModel.toggleShorts(it) }
                                )
                            }
                        }
                    }
                    val filteredVideos = if (showShorts) state.playlist.videos else state.playlist.videos.filter { !it.isShort }
                    items(filteredVideos) { video ->
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
                            onClick = { onVideoSelected(video.videoId) },
                            onFavoriteToggle = { videoObj, fav -> 
                                viewModel.toggleFavorite(videoObj, fav)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (fav) "Added to favorites" else "Removed from favorites"
                                    )
                                }
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
                            }
                        )
                    }
                }
                is HomeUiState.ChannelResult -> {
                    item {
                        Column {
                            Text(
                                "Channel: ${state.channel.name}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            val isSubscribed = subscriptions.any { it.sourceId == state.channel.url || it.sourceId == state.channel.channelId }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { 
                                        if (isSubscribed) {
                                            val subId = subscriptions.firstOrNull { it.sourceId == state.channel.url || it.sourceId == state.channel.channelId }?.sourceId ?: state.channel.url
                                            viewModel.unsubscribe(subId)
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Unsubscribed from Channel") }
                                        } else {
                                            viewModel.subscribe("channel", state.channel.url, state.channel.name, state.channel.avatarUrl)
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Subscribed to Channel") }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                        contentColor = if (isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(if (isSubscribed) "Subscribed" else "Subscribe to Channel")
                                }
                                Button(
                                    onClick = { 
                                        viewModel.setBulkQualityDialogVisible(true)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Download All")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show Shorts", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Switch(
                                    checked = showShorts,
                                    onCheckedChange = { viewModel.toggleShorts(it) }
                                )
                            }
                        }
                    }
                    val filteredUploads = if (showShorts) state.channel.uploads else state.channel.uploads.filter { !it.isShort }
                    items(filteredUploads) { video ->
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
                            onClick = { onVideoSelected(video.videoId) },
                            showChannelName = false,
                            onFavoriteToggle = { videoObj, fav -> 
                                viewModel.toggleFavorite(videoObj, fav)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (fav) "Added to favorites" else "Removed from favorites"
                                    )
                                }
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
                            }
                        )
                    }
                }
            }

            if (uiState !is HomeUiState.Loading && linkHistory.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(linkHistory, key = { it.url }) { item ->
                    HistoryItemRow(
                        item = item,
                        onClick = {
                            try {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("YouTube Link", item.url)
                                clipboardManager.setPrimaryClip(clip)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Link copied to clipboard")
                                }
                            } catch (e: Exception) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Failed to copy link")
                                }
                            }
                        },
                        onDelete = {
                            viewModel.deleteHistoryItem(item.url)
                        }
                    )
                }
            }
        }
    }

    if (showMoveToFolderDialog && videoToMove != null) {
        AlertDialog(
            onDismissRequest = { 
                showMoveToFolderDialog = false
                videoToMove = null
            },
            title = { Text("Move to Folder") },
            text = {
                if (folders.isEmpty()) {
                    Text("No folders found. Create one in the Library tab first.")
                } else {
                    LazyColumn {
                        items(folders) { folder ->
                            TextButton(
                                onClick = {
                                    videoToMove?.let { video ->
                                        viewModel.moveVideoToFolder(video, folder.id)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Moved to ${folder.name}")
                                        }
                                    }
                                    showMoveToFolderDialog = false
                                    videoToMove = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(folder.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showMoveToFolderDialog = false
                    videoToMove = null
                }) { Text("Cancel") }
            }
        )
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
            onDismissRequest = {}, // Force user to cancel or wait
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
                    Text("Total Videos: ${confirmation.videoCount}")
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
                    viewModel.confirmBulkDownload(confirmation.videosToDownload)
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
}

@Composable
fun HistoryItemRow(
    item: com.example.medianest.data.local.entity.LinkHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete history item",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun VideoResultCard(
    video: ExtractedVideoInfo,
    isFavorite: Boolean,
    folders: List<com.example.medianest.data.local.entity.FolderEntity> = emptyList(),
    playbackProgressFraction: Float = 0f,
    onSelectQuality: () -> Unit,
    onFavoriteToggle: ((ExtractedVideoInfo, Boolean) -> Unit)? = null
) {
    UnifiedVideoCard(
        title = video.title,
        channelName = video.channelName,
        thumbnailUrl = video.thumbnailUrl,
        durationSeconds = video.durationSeconds,
        uploadDate = video.uploadDate,
        isFavorite = isFavorite,
        folders = folders,
        playbackProgressFraction = playbackProgressFraction,
        config = VideoCardConfig(
            showFavoriteButton = onFavoriteToggle != null,
            showFolderBadges = folders.isNotEmpty(),
            showPlaybackProgress = playbackProgressFraction > 0f
        ),
        onClick = onSelectQuality,
        onFavoriteToggle = {
            onFavoriteToggle?.invoke(video, !isFavorite)
        }
    )
}

@Composable
fun VideoListItem(
    video: ExtractedVideoInfo,
    isFavorite: Boolean,
    folders: List<com.example.medianest.data.local.entity.FolderEntity> = emptyList(),
    playbackProgressFraction: Float = 0f,
    onClick: () -> Unit,
    showChannelName: Boolean = true,
    onFavoriteToggle: ((ExtractedVideoInfo, Boolean) -> Unit)? = null,
    onMoveToFolder: ((ExtractedVideoInfo) -> Unit)? = null,
    onDownloadClick: ((String) -> Unit)? = null,
    downloadMenuContent: (@Composable () -> Unit)? = null
) {
    UnifiedVideoRow(
        title = video.title,
        channelName = if (showChannelName) video.channelName else "",
        thumbnailUrl = video.thumbnailUrl,
        durationSeconds = video.durationSeconds,
        uploadDate = video.uploadDate,
        isFavorite = isFavorite,
        isDownloaded = false, // Not tracked on Home screen
        playbackProgressFraction = playbackProgressFraction,
        folders = folders,
        config = VideoCardConfig(
            showFavoriteButton = onFavoriteToggle != null,
            showMoveToFolderButton = onMoveToFolder != null,
            showDownloadButton = onDownloadClick != null,
            showPlaybackProgress = playbackProgressFraction > 0f,
            showDownloadedBadge = false,
            showFolderBadges = folders.isNotEmpty()
        ),
        onClick = onClick,
        onFavoriteToggle = {
            onFavoriteToggle?.invoke(video, !isFavorite)
        },
        onMoveToFolder = { onMoveToFolder?.invoke(video) },
        onDownloadClick = { onDownloadClick?.invoke(video.videoId) },
        downloadMenuContent = downloadMenuContent
    )
}
