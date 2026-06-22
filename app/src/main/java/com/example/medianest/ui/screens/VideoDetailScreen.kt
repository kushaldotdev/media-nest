package com.example.medianest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailScreen(
    videoInfo: ExtractedVideoInfo,
    onPlay: (StreamSource) -> Unit,
    onDownload: (StreamSource) -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    isFavorite: Boolean = false,
    onSubscribe: () -> Unit = {},
    isSubscribed: Boolean = false
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(videoInfo.title, maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    IconToggleButton(
                        checked = isSubscribed,
                        onCheckedChange = { onSubscribe() }
                    ) {
                        Icon(
                            imageVector = if (isSubscribed) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions,
                            contentDescription = if (isSubscribed) "Unsubscribe" else "Subscribe",
                            tint = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconToggleButton(
                        checked = isFavorite,
                        onCheckedChange = { onToggleFavorite() }
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AsyncImage(
                model = videoInfo.thumbnailUrl,
                contentDescription = videoInfo.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(videoInfo.title, style = MaterialTheme.typography.titleLarge)
            Text(videoInfo.channelName, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            Text("Available streams:", style = MaterialTheme.typography.titleSmall)

            val videoStreams = videoInfo.streamSources.filter { it.format == "video" }
            val audioStreams = videoInfo.streamSources.filter { it.format == "audio" }

            if (videoStreams.isNotEmpty()) {
                Text("Video", style = MaterialTheme.typography.labelLarge)
                videoStreams.forEach { stream ->
                    StreamQualityRow(stream, onPlay, onDownload)
                }
            }

            if (audioStreams.isNotEmpty()) {
                Text("Audio Only", style = MaterialTheme.typography.labelLarge)
                audioStreams.forEach { stream ->
                    StreamQualityRow(stream, onPlay, onDownload)
                }
            }

            if (videoStreams.isEmpty() && audioStreams.isEmpty()) {
                Text(
                    "No streams available",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StreamQualityRow(
    stream: StreamSource,
    onPlay: (StreamSource) -> Unit,
    onDownload: (StreamSource) -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = { onPlay(stream) }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stream.quality)
                Text(
                    stream.contentLength?.let { "${it / 1024 / 1024}MB" } ?: "Unknown size",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = { onDownload(stream) }) {
                Text("Download")
            }
        }
    }
}
