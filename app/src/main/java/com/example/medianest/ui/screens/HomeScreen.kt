package com.example.medianest.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.Formatter
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.components.QuickDownloadMenu
import com.example.medianest.ui.components.UnifiedVideoCard
import com.example.medianest.ui.components.UnifiedVideoRow
import com.example.medianest.ui.components.VideoCardConfig
import com.example.medianest.ui.components.WatchCountDialog
import com.example.medianest.ui.components.YoutubeSubscribeButton
import com.example.medianest.ui.components.EndOfListIndicator
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.utils.UiUtils
import com.example.medianest.ui.viewmodel.HomeUiState
import com.example.medianest.ui.viewmodel.HomeViewModel

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
    val watchCounts by viewModel.watchCounts.collectAsStateWithLifecycle()
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
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var videoToMove by remember { mutableStateOf<ExtractedVideoInfo?>(null) }
    var expandedDownloadVideoId by remember { mutableStateOf<String?>(null) }

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
                    if (linkHistory.isNotEmpty()) {
                        viewModel.loadMoreLinkHistory()
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (uiState !is HomeUiState.Loading) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        viewModel.onUrlSubmitted(urlInput.trim())
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Extract URL"
                    )
                },
                text = { Text("Extract") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
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
                            watchCount = watchCounts[state.video.videoId] ?: 0,
                            onSelectQuality = { onVideoSelected(state.video.videoId) },
                            onFavoriteToggle = { video, fav -> 
                                viewModel.toggleFavorite(video, fav)
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
                            Text(
                                "Videos: ${state.playlist.videoCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            val isSaved = subscriptions.any { it.sourceId == state.playlist.playlistId }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isSaved) {
                                    OutlinedButton(
                                        onClick = { 
                                            viewModel.unsubscribe(state.playlist.playlistId)
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Removed from Playlist") }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Saved to Playlist")
                                    }
                                } else {
                                    Button(
                                        onClick = { 
                                            viewModel.subscribe("playlist", state.playlist.playlistId, state.playlist.name, state.playlist.thumbnailUrl)
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Added to Playlist") }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Add to Playlist")
                                    }
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
                    itemsIndexed(filteredVideos) { index, video ->
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
                            onClick = { onVideoSelected(video.videoId) },
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
                    } else if (!state.hasMore && (if (showShorts) state.playlist.videos else state.playlist.videos.filter { !it.isShort }).isNotEmpty()) {
                        item {
                            EndOfListIndicator()
                        }
                    }
                }
                is HomeUiState.ChannelResult -> {
                    item {
                        Column {
                            Text(
                                "Channel: ${state.channel.name}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Videos: ${state.channel.videoCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                              val isSubscribed = subscriptions.any { sub ->
                                  sub.sourceType == "channel" && (
                                      sub.sourceId == state.channel.channelId ||
                                      sub.sourceId == state.channel.url ||
                                      sub.sourceId.contains(state.channel.channelId) ||
                                      sub.name.equals(state.channel.name, ignoreCase = true) ||
                                      (state.channel.url.contains(sub.sourceId.removePrefix("https://").removePrefix("www.youtube.com/").removePrefix("@").removePrefix("channel/").removePrefix("c/").trim()))
                                  )
                              }
                              Row(
                                  modifier = Modifier.fillMaxWidth(),
                                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                              ) {
                                  YoutubeSubscribeButton(
                                      isSubscribed = isSubscribed,
                                      onClick = {
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
                                      modifier = Modifier.weight(1f)
                                  )
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
                            watchCount = watchCounts[video.videoId] ?: 0,
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
                            }
                        )
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
                    } else if (!state.hasMore && (if (showShorts) state.channel.uploads else state.channel.uploads.filter { !it.isShort }).isNotEmpty()) {
                        item {
                            EndOfListIndicator()
                        }
                    }
                }
            }

            if (uiState !is HomeUiState.Loading && linkHistory.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(
                            onClick = { showClearHistoryDialog = true }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MediaNestColors.Destructive
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Clear all",
                                style = MaterialTheme.typography.labelMedium,
                                color = MediaNestColors.Destructive
                            )
                        }
                    }
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
                        },
                        onReExtract = {
                            urlInput = item.url
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            viewModel.onUrlSubmitted(item.url)
                        }
                    )
                }
                item {
                    EndOfListIndicator()
                }
            }

            item {
                Spacer(Modifier.height(72.dp))
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear link history") },
            text = { Text("Remove all link history entries? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllLinkHistory()
                        showClearHistoryDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Link history cleared")
                        }
                    }
                ) {
                    Text("Clear all", color = MediaNestColors.Destructive)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
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
fun HistoryItemRow(
    item: com.example.medianest.data.local.entity.LinkHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onReExtract: (() -> Unit)? = null
) {
    val (typeIcon, typeLabel) = when (item.linkType.uppercase()) {
        "VIDEO" -> Pair(Icons.Default.PlayCircle, "Video")
        "PLAYLIST" -> Pair(Icons.AutoMirrored.Filled.PlaylistPlay, "Playlist")
        "CHANNEL" -> Pair(Icons.Default.AccountCircle, "Channel")
        else -> Pair(Icons.AutoMirrored.Filled.HelpOutline, "Link")
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = typeLabel,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$typeLabel · ${item.url}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onReExtract != null) {
                IconButton(
                    onClick = onReExtract
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Re-extract link",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
    watchCount: Int = 0,
    onSelectQuality: () -> Unit,
    onFavoriteToggle: ((ExtractedVideoInfo, Boolean) -> Unit)? = null,
    onMarkWatched: () -> Unit = {}
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
        watchCount = watchCount,
        config = VideoCardConfig(
            showFavoriteButton = onFavoriteToggle != null,
            showFolderBadges = folders.isNotEmpty(),
            showPlaybackProgress = playbackProgressFraction > 0f,
            showMarkWatchedButton = true
        ),
        onClick = onSelectQuality,
        onFavoriteToggle = {
            onFavoriteToggle?.invoke(video, !isFavorite)
        },
        onMarkWatched = onMarkWatched
    )
}

@Composable
fun VideoListItem(
    video: ExtractedVideoInfo,
    isFavorite: Boolean,
    folders: List<com.example.medianest.data.local.entity.FolderEntity> = emptyList(),
    playbackProgressFraction: Float = 0f,
    watchCount: Int = 0,
    onClick: () -> Unit,
    showChannelName: Boolean = true,
    onFavoriteToggle: ((ExtractedVideoInfo, Boolean) -> Unit)? = null,
    onMoveToFolder: ((ExtractedVideoInfo) -> Unit)? = null,
    onDownloadClick: ((String) -> Unit)? = null,
    downloadMenuContent: (@Composable () -> Unit)? = null,
    serialNumber: Int? = null,
    onMarkWatched: () -> Unit = {}
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
        watchCount = watchCount,
        folders = folders,
        config = VideoCardConfig(
            showFavoriteButton = onFavoriteToggle != null,
            showMoveToFolderButton = onMoveToFolder != null,
            showDownloadButton = onDownloadClick != null,
            showPlaybackProgress = playbackProgressFraction > 0f,
            showDownloadedBadge = false,
            showFolderBadges = folders.isNotEmpty(),
            showMarkWatchedButton = true
        ),
        onClick = onClick,
        onFavoriteToggle = {
            onFavoriteToggle?.invoke(video, !isFavorite)
        },
        onMoveToFolder = { onMoveToFolder?.invoke(video) },
        onDownloadClick = { onDownloadClick?.invoke(video.videoId) },
        downloadMenuContent = downloadMenuContent,
        serialNumber = serialNumber,
        onMarkWatched = onMarkWatched
    )
}
