package com.example.medianest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.ui.viewmodel.DownloadsViewModel

import androidx.compose.runtime.LaunchedEffect

@Composable
fun DownloadsScreen(
    onPlayDownload: (DownloadEntity) -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playingVideoId by viewModel.playingVideoId.collectAsStateWithLifecycle()
    val playingUri by viewModel.playingUri.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    var showDeleteDialogFor by remember { mutableStateOf<DownloadEntity?>(null) }
    var showRestartDialogFor by remember { mutableStateOf<DownloadEntity?>(null) }
    var pendingDialogId by remember { mutableStateOf<Long?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        com.example.medianest.ui.viewmodel.PendingRestartConfirmation.pendingDownloadId.collect { id ->
            pendingDialogId = id
        }
    }

    LaunchedEffect(downloads, pendingDialogId) {
        val id = pendingDialogId
        if (id != null && downloads.isNotEmpty()) {
            val download = downloads.find { it.id == id }
            if (download != null) {
                showRestartDialogFor = download
                pendingDialogId = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Downloads", style = MaterialTheme.typography.titleLarge)
            var expanded by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { expanded = true }) {
                    Text("Max: ${uiState.maxConcurrent}")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    (1..5).forEach { n ->
                        DropdownMenuItem(
                            text = { Text("$n concurrent") },
                            onClick = {
                                viewModel.setMaxConcurrent(n)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.pauseAllDownloads() }) {
                Text("Pause All")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { viewModel.resumeAllDownloads() }) {
                Text("Resume All")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { showDeleteAllDialog = true }) {
                Text("Delete All")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloads yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(downloads, key = { it.id }) { download ->
                    DownloadItem(
                        download = download,
                        onPlayDownload = onPlayDownload,
                        onVideoClick = onVideoClick,
                        viewModel = viewModel,
                        onDeleteClick = { showDeleteDialogFor = it },
                        onRestartClick = { showRestartDialogFor = it },
                        playingVideoId = playingVideoId,
                        playingUri = playingUri,
                        isPlaying = isPlaying
                    )
                }
            }
        }
    }

    if (showDeleteDialogFor != null) {
        val download = showDeleteDialogFor!!
        val isActive = download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED
        
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = {
                Text(text = if (isActive) "Cancel Download" else "Delete Download")
            },
            text = {
                Text(
                    text = if (isActive) {
                        "Are you sure you want to cancel downloading \"${download.title}\"?"
                    } else {
                        "Choose how you want to delete \"${download.title}\"."
                    }
                )
            },
            confirmButton = {
                if (isActive) {
                    TextButton(
                        onClick = {
                            viewModel.deleteDownload(download, deleteFile = true)
                            showDeleteDialogFor = null
                        }
                    ) {
                        Text("Cancel Download")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.deleteDownload(download, deleteFile = false)
                                showDeleteDialogFor = null
                            }
                        ) {
                            Text("List Only")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                viewModel.deleteDownload(download, deleteFile = true)
                                showDeleteDialogFor = null
                            }
                        ) {
                            Text("Delete File & List")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialogFor = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRestartDialogFor != null) {
        val download = showRestartDialogFor!!
        AlertDialog(
            onDismissRequest = { showRestartDialogFor = null },
            title = {
                Text("Restart Download")
            },
            text = {
                Text("Are you sure you want to restart downloading \"${download.title}\"? This will delete any partially downloaded files and start from scratch.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.retryDownload(download)
                        showRestartDialogFor = null
                    }
                ) {
                    Text("Restart")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestartDialogFor = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = {
                Text("Delete All Downloads")
            },
            text = {
                Text("Choose how you want to delete all downloads. This will cancel all active downloads and remove them from the list.")
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            viewModel.deleteAllDownloads(deleteFiles = false)
                            showDeleteAllDialog = false
                        }
                    ) {
                        Text("List Only")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            viewModel.deleteAllDownloads(deleteFiles = true)
                            showDeleteAllDialog = false
                        }
                    ) {
                        Text("Delete Files & List")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
@Composable
private fun DownloadItem(
    download: DownloadEntity,
    onPlayDownload: (DownloadEntity) -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: DownloadsViewModel,
    onDeleteClick: (DownloadEntity) -> Unit,
    onRestartClick: (DownloadEntity) -> Unit,
    playingVideoId: String?,
    playingUri: String?,
    isPlaying: Boolean
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val formatLabel = when (download.format) {
        "video" -> "Video"
        "video_only" -> "Video"
        "audio" -> "Audio"
        "audio_extracted" -> "Extracted Audio"
        else -> download.format.replaceFirstChar { it.uppercase() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Clickable Metadata Row (navigates to Video Details Screen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVideoClick(download.videoId) }
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 16:9 Thumbnail
                Box(
                    modifier = Modifier
                        .size(110.dp, 62.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    AsyncImage(
                        model = download.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Title, Format Badge, and Quality text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.title.ifEmpty { download.quality },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = formatLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = download.quality,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Status and Speed info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (download.status == DownloadStatus.COMPLETED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        val sizeText = if (download.fileSizeBytes > 0L) {
                            "%.1f MB".format(download.fileSizeBytes / (1024f * 1024f))
                        } else {
                            "Done"
                        }
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    val statusText = when (download.status) {
                        DownloadStatus.QUEUED -> "Queued"
                        DownloadStatus.DOWNLOADING -> {
                            val msg = download.errorMessage ?: ""
                            if (msg.startsWith("downloading_audio")) {
                                val speedPart = msg.substringAfter("|", "")
                                if (speedPart.isNotEmpty()) {
                                    "Downloading audio ($speedPart)..."
                                } else {
                                    "Downloading audio..."
                                }
                            } else if (msg == "merging") {
                                "Merging video & audio..."
                            } else if (download.fileSizeBytes > 0L) {
                                val downloadedMb = (download.progress * download.fileSizeBytes) / (1024f * 1024f)
                                val totalMb = download.fileSizeBytes / (1024f * 1024f)
                                val pct = (download.progress * 100).toInt()
                                if (msg.isNotEmpty() && !msg.startsWith("downloading_audio") && msg != "merging") {
                                    "%.1fMB / %.1fMB (%d%%) • %s".format(downloadedMb, totalMb, pct, msg)
                                } else {
                                    "%.1fMB / %.1fMB (%d%%)".format(downloadedMb, totalMb, pct)
                                }
                            } else {
                                "${(download.progress * 100).toInt()}%"
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            if (download.fileSizeBytes > 0L) {
                                "Paused — %.1fMB / %.1fMB".format(
                                    (download.progress * download.fileSizeBytes) / (1024f * 1024f),
                                    download.fileSizeBytes / (1024f * 1024f)
                                )
                            } else {
                                "Paused"
                            }
                        }
                        DownloadStatus.FAILED -> download.errorMessage ?: "Failed"
                        DownloadStatus.CANCELED -> "Canceled"
                        DownloadStatus.COMPLETED -> ""
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (download.status) {
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                            DownloadStatus.CANCELED -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Progress Bar
            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                Spacer(Modifier.height(4.dp))
                if (download.errorMessage == "merging") {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(4.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (download.status) {
                    DownloadStatus.QUEUED -> {
                        Button(
                            onClick = { viewModel.pauseDownload(download.id) },
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { onDeleteClick(download) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DownloadStatus.DOWNLOADING -> {
                        Button(
                            onClick = { viewModel.pauseDownload(download.id) },
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { onDeleteClick(download) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        Button(
                            onClick = { viewModel.resumeDownload(download.id) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onRestartClick(download) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restart", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { onDeleteClick(download) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DownloadStatus.FAILED -> {
                        Button(
                            onClick = { onRestartClick(download) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { onDeleteClick(download) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DownloadStatus.CANCELED -> {
                        Button(
                            onClick = { onRestartClick(download) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restart", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { onDeleteClick(download) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        if (download.filePath.isNotEmpty()) {
                            val isCurrentPlaying = playingVideoId == download.videoId && 
                                (playingUri == null || download.filePath.isEmpty() || 
                                 playingUri == android.net.Uri.fromFile(java.io.File(download.filePath)).toString())
                            val showPause = isCurrentPlaying && isPlaying
                            Button(
                                onClick = {
                                    if (isCurrentPlaying) {
                                        viewModel.togglePlayPause()
                                    } else {
                                        onPlayDownload(download)
                                    }
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (showPause) "Pause" else "Play", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (download.format != "audio" && download.format != "audio_extracted") {
                            val isExtracting = uiState.extractingVideoId == download.videoId
                            OutlinedButton(
                                onClick = { viewModel.extractAudio(download) },
                                enabled = !isExtracting,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                if (isExtracting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text("Extract Audio", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        TextButton(
                            onClick = { onDeleteClick(download) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
