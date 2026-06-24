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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.TextButton
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.ui.utils.UiUtils
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import android.text.method.LinkMovementMethod
import androidx.core.text.HtmlCompat
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailScreen(
    videoInfo: ExtractedVideoInfo,
    localVideo: VideoEntity? = null,
    downloads: List<DownloadEntity> = emptyList(),
    onPlay: (StreamSource) -> Unit,
    onDownload: (StreamSource) -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    isFavorite: Boolean = false,
    onSubscribe: () -> Unit = {},
    isSubscribed: Boolean = false,
    videoHistory: com.example.medianest.data.local.entity.HistoryEntity? = null,
    watchSessions: List<com.example.medianest.data.local.entity.WatchSessionEntity> = emptyList()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(videoInfo.title, maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    IconToggleButton(
                        checked = isFavorite,
                        onCheckedChange = { 
                            onToggleFavorite()
                            coroutineScope.launch { snackbarHostState.showSnackbar(if (isFavorite) "Removed from favorites" else "Added to favorites") }
                        }
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
            Spacer(Modifier.height(16.dp))
            
            // Channel Info Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(videoInfo.channelName, style = MaterialTheme.typography.titleMedium)
                if (isSubscribed) {
                    androidx.compose.material3.OutlinedButton(onClick = {
                        onSubscribe()
                        coroutineScope.launch { snackbarHostState.showSnackbar("Unsubscribed from ${videoInfo.channelName}") }
                    }) {
                        Text("Subscribed")
                    }
                } else {
                    androidx.compose.material3.Button(onClick = {
                        onSubscribe()
                        coroutineScope.launch { snackbarHostState.showSnackbar("Subscribed to ${videoInfo.channelName}") }
                    }) {
                        Text("Subscribe")
                    }
                }
            }

            // Duration, Released, and Downloaded Metadata
            if (videoInfo.durationSeconds > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Duration: ${UiUtils.formatDuration(videoInfo.durationSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val formattedReleaseDate = UiUtils.formatReleaseDate(videoInfo.uploadDate)
            if (!formattedReleaseDate.isNullOrEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Released: $formattedReleaseDate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val completedDownloads = downloads.filter { it.status == DownloadStatus.COMPLETED }
            val overallDownloadTime = if (completedDownloads.isNotEmpty()) {
                completedDownloads.maxOfOrNull { it.downloadedAt }
            } else {
                localVideo?.downloadedAt
            }
            if (overallDownloadTime != null && overallDownloadTime > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Downloaded: ${UiUtils.formatAbsoluteDate(overallDownloadTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Collapsible Description Container
            if (!videoInfo.description.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                var isDescriptionExpanded by remember { mutableStateOf(false) }
                val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                val linkColor = MaterialTheme.colorScheme.primary.toArgb()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        AndroidView(
                            factory = { context ->
                                TextView(context).apply {
                                    movementMethod = LinkMovementMethod.getInstance()
                                    ellipsize = android.text.TextUtils.TruncateAt.END
                                }
                            },
                            update = { textView ->
                                textView.setTextColor(textColor)
                                textView.setLinkTextColor(linkColor)
                                textView.textSize = 14f
                                textView.maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3
                                textView.text = HtmlCompat.fromHtml(
                                    videoInfo.description ?: "",
                                    HtmlCompat.FROM_HTML_MODE_LEGACY
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isDescriptionExpanded) "Show less" else "Show more",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }

            if (videoHistory != null || watchSessions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Your Statistics", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val totalTimeStr = com.example.medianest.ui.screens.formatWatchTime(videoHistory?.totalWatchTimeMillis ?: 0L)
                        Text("Total Watch Time: $totalTimeStr", style = MaterialTheme.typography.bodyLarge)
                        
                        if (watchSessions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp)
                            ) {
                                Text("View Watch History (Watched ${watchSessions.size} times)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            if (expanded) {
                                Spacer(Modifier.height(4.dp))
                                val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault())
                                watchSessions.forEach { session ->
                                    val dateStr = dateFormat.format(java.util.Date(session.watchedAt))
                                    Text("• Watched on: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Available streams:", style = MaterialTheme.typography.titleSmall)

            val videoStreams = videoInfo.streamSources.filter { it.format == "video" || it.format == "video_only" }
            val audioStreams = videoInfo.streamSources.filter { it.format == "audio" }

            val groupedVideos = videoStreams.groupBy { it.quality }
            val sortedResolutions = groupedVideos.keys.sortedByDescending { 
                it.replace("p", "").toIntOrNull() ?: 0 
            }

            val bestAudioStream = audioStreams.maxByOrNull {
                it.quality.replace("kbps", "").toIntOrNull() ?: 0
            }
            val bestAudioLength = bestAudioStream?.contentLength

            if (sortedResolutions.isNotEmpty()) {
                sortedResolutions.forEach { resolution ->
                    Spacer(Modifier.height(8.dp))
                    Text(resolution, style = MaterialTheme.typography.titleMedium)
                    val streamsInResolution = groupedVideos[resolution] ?: emptyList()
                    streamsInResolution.forEach { stream ->
                        StreamQualityRow(
                            stream = stream,
                            downloads = downloads,
                            bestAudioLength = bestAudioLength,
                            onPlay = onPlay,
                            onDownload = onDownload,
                            onShowSnackbar = { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } }
                        )
                    }
                }
            }

            if (audioStreams.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Audio Only", style = MaterialTheme.typography.titleMedium)
                val sortedAudios = audioStreams.sortedByDescending {
                    it.quality.replace("kbps", "").toIntOrNull() ?: 0
                }
                sortedAudios.forEach { stream ->
                    StreamQualityRow(
                        stream = stream,
                        downloads = downloads,
                        bestAudioLength = null,
                        onPlay = onPlay,
                        onDownload = onDownload,
                        onShowSnackbar = { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
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
    downloads: List<DownloadEntity>,
    bestAudioLength: Long?,
    onPlay: (StreamSource) -> Unit,
    onDownload: (StreamSource) -> Unit,
    onShowSnackbar: (String) -> Unit
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
            Column(modifier = Modifier.weight(1f)) {
                val typeLabel = when (stream.format) {
                    "video_only", "video" -> "Video"
                    "audio" -> "Audio Only (${stream.quality})"
                    else -> stream.format
                }
                val label = if (stream.codec.isNotEmpty()) {
                    "${typeLabel} • ${stream.codec.uppercase()}"
                } else {
                    typeLabel
                }
                Text(label)
                val sizeText = when {
                    stream.contentLength != null && stream.contentLength > 0 -> {
                        val videoSize = "%.1f MB".format(stream.contentLength / (1024f * 1024f))
                        if (stream.format == "video_only" && bestAudioLength != null && bestAudioLength > 0) {
                            val audioSize = "%.1f MB".format(bestAudioLength / (1024f * 1024f))
                            "$videoSize + $audioSize"
                        } else {
                            videoSize
                        }
                    }
                    else -> "Resolving size…"
                }
                Text(sizeText, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { onPlay(stream) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
                
                val dbQuality = if (stream.format == "audio") stream.quality else "${stream.quality} (${stream.codec})"
                val downloadState = downloads.find { it.format == stream.format && it.quality == dbQuality }
                if (downloadState != null) {
                    when (downloadState.status) {
                        DownloadStatus.COMPLETED -> {
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Downloaded",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Downloaded",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (downloadState.downloadedAt > 0) {
                                    Text(
                                        text = UiUtils.formatAbsoluteDate(downloadState.downloadedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                            TextButton(onClick = {}, enabled = false) {
                                Text("Downloading")
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            TextButton(onClick = {}, enabled = false) {
                                Text("Paused")
                            }
                        }
                        DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                            TextButton(onClick = { 
                                onDownload(stream) 
                                onShowSnackbar("Added to download queue")
                            }) {
                                Text("Download")
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { 
                        onDownload(stream) 
                        onShowSnackbar("Added to download queue")
                    }) {
                        Text("Download")
                    }
                }
            }
        }
    }
}
