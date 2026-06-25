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
    var urlInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
                        VideoResultCard(
                            video = state.video,
                            onSelectQuality = { onVideoSelected(state.video.videoId) },
                            onFavoriteToggle = { videoId, fav -> viewModel.toggleFavorite(videoId, fav) }
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
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = if (isSaved) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isSaved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(if (isSaved) "Saved to Playlist" else "Add to Playlist")
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
                        VideoListItem(
                            video = video,
                            onClick = { onVideoSelected(video.videoId) },
                            onFavoriteToggle = { id, current -> viewModel.toggleFavorite(id, current) },
                            onMoveToFolder = { /* TODO: Move to folder from home screen not supported yet */ },
                            onDownloadClick = { /* TODO: Download from home screen not supported yet */ }
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
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(if (isSubscribed) "Subscribed" else "Subscribe to Channel")
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
                        VideoListItem(
                            video = video,
                            onClick = { onVideoSelected(video.videoId) },
                            showChannelName = false,
                            onFavoriteToggle = { id, current -> viewModel.toggleFavorite(id, current) },
                            onMoveToFolder = { /* TODO: Move to folder from home screen not supported yet */ },
                            onDownloadClick = { /* TODO: Download from home screen not supported yet */ }
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
    onSelectQuality: () -> Unit,
    onFavoriteToggle: ((String, Boolean) -> Unit)? = null
) {
    var isFavorited by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UnifiedVideoCard(
            title = video.title,
            channelName = video.channelName,
            thumbnailUrl = video.thumbnailUrl,
            durationSeconds = video.durationSeconds,
            uploadDate = video.uploadDate,
            isFavorite = isFavorited,
            config = VideoCardConfig(showFavoriteButton = onFavoriteToggle != null),
            onFavoriteToggle = {
                val newFav = !isFavorited
                isFavorited = newFav
                onFavoriteToggle?.invoke(video.videoId, newFav)
            }
        )
        Button(
            onClick = onSelectQuality,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Quality")
        }
    }
}

@Composable
fun VideoListItem(
    video: ExtractedVideoInfo,
    onClick: () -> Unit,
    showChannelName: Boolean = true,
    onFavoriteToggle: ((String, Boolean) -> Unit)? = null,
    onMoveToFolder: ((String) -> Unit)? = null,
    onDownloadClick: ((String) -> Unit)? = null
) {
    var isFavorited by remember { mutableStateOf(false) }

    UnifiedVideoRow(
        title = video.title,
        channelName = if (showChannelName) video.channelName else "",
        thumbnailUrl = video.thumbnailUrl,
        durationSeconds = video.durationSeconds,
        uploadDate = video.uploadDate,
        isFavorite = isFavorited,
        isDownloaded = false, // Not tracked on Home screen
        playbackProgressFraction = 0f, // Not tracked on Home screen
        config = VideoCardConfig(
            showFavoriteButton = onFavoriteToggle != null,
            showMoveToFolderButton = onMoveToFolder != null,
            showDownloadButton = onDownloadClick != null,
            showPlaybackProgress = false,
            showDownloadedBadge = false
        ),
        onClick = onClick,
        onFavoriteToggle = {
            val newFav = !isFavorited
            isFavorited = newFav
            onFavoriteToggle?.invoke(video.videoId, newFav)
        },
        onMoveToFolder = { onMoveToFolder?.invoke(video.videoId) },
        onDownloadClick = { onDownloadClick?.invoke(video.videoId) }
    )
}
