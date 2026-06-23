package com.example.medianest.ui.screens

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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.example.medianest.data.model.ExtractedVideoInfo
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
    var urlInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Paste YouTube URL") },
                singleLine = true
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

        Spacer(Modifier.height(16.dp))

        when (val state = uiState) {
            is HomeUiState.Idle -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Enter a YouTube URL to get started",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is HomeUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is HomeUiState.Error -> {
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
            is HomeUiState.Success -> {
                viewModel.cacheResult(state.video)
                VideoResultCard(
                    video = state.video,
                    onSelectQuality = { onVideoSelected(state.video.videoId) },
                    onFavoriteToggle = { videoId, fav -> viewModel.toggleFavorite(videoId, fav) }
                )
            }
            is HomeUiState.PlaylistResult -> {
                Text(
                    "Playlist: ${state.playlist.name}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { 
                        onSubscribe("playlist", state.playlist.playlistId, state.playlist.name, state.playlist.thumbnailUrl)
                        coroutineScope.launch { snackbarHostState.showSnackbar("Subscribed to Playlist") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Subscribe to Playlist")
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(state.playlist.videos) { video ->
                        VideoListItem(video = video, onClick = { onVideoSelected(video.videoId) })
                    }
                }
            }
            is HomeUiState.ChannelResult -> {
                Text(
                    "Channel: ${state.channel.name}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { 
                        onSubscribe("channel", state.channel.channelId, state.channel.name, state.channel.avatarUrl)
                        coroutineScope.launch { snackbarHostState.showSnackbar("Subscribed to Channel") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Subscribe to Channel")
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(state.channel.uploads) { video ->
                        VideoListItem(video = video, onClick = { onVideoSelected(video.videoId) })
                    }
                }
            }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(video.title, style = MaterialTheme.typography.titleMedium)
            Text(video.channelName, style = MaterialTheme.typography.bodySmall)
            val minutes = video.durationSeconds / 60
            val seconds = video.durationSeconds % 60
            Text("${minutes}:%02d".format(seconds))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSelectQuality,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select Quality")
                }
                if (onFavoriteToggle != null) {
                    var isFavorited by remember { mutableStateOf(false) }
                    IconToggleButton(
                        checked = isFavorited,
                        onCheckedChange = { checked ->
                            isFavorited = checked
                            onFavoriteToggle(video.videoId, checked)
                        }
                    ) {
                        Icon(
                            imageVector = if (isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Toggle favorite"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoListItem(video: ExtractedVideoInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier.size(120.dp, 68.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(video.title, maxLines = 2)
            Text(
                video.channelName,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
