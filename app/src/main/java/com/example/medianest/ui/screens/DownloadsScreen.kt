package com.example.medianest.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playingVideoId by viewModel.playingVideoId.collectAsStateWithLifecycle()
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
                        viewModel = viewModel,
                        onDeleteClick = { showDeleteDialogFor = it },
                        onRestartClick = { showRestartDialogFor = it },
                        playingVideoId = playingVideoId,
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
    viewModel: DownloadsViewModel,
    onDeleteClick: (DownloadEntity) -> Unit,
    onRestartClick: (DownloadEntity) -> Unit,
    playingVideoId: String?,
    isPlaying: Boolean
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = download.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp, 48.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.title.ifEmpty { download.quality },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val formatLabel = when (download.format) {
                            "video" -> "VIDEO"
                            "video_only" -> "VIDEO ONLY"
                            "audio" -> "AUDIO"
                            "audio_extracted" -> "EXTRACTED AUDIO"
                            else -> download.format.uppercase()
                        }
                        Text(
                            text = "$formatLabel • ${download.quality}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
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
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        } else {
                            Text(
                                text = when (download.status) {
                                    DownloadStatus.QUEUED -> "Queued"
                                    DownloadStatus.DOWNLOADING -> {
                                        if (download.errorMessage == "downloading_audio") {
                                            "Downloading audio..."
                                        } else if (download.errorMessage == "merging") {
                                            "Merging video & audio..."
                                        } else if (download.fileSizeBytes > 0L) {
                                            val downloadedMb = (download.progress * download.fileSizeBytes) / (1024f * 1024f)
                                            val totalMb = download.fileSizeBytes / (1024f * 1024f)
                                            "%.1fMB / %.1fMB (%d%%)".format(downloadedMb, totalMb, (download.progress * 100).toInt())
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
                                    DownloadStatus.CANCELED -> {
                                        if (download.fileSizeBytes > 0L) {
                                            "Canceled — %.1fMB / %.1fMB".format(
                                                (download.progress * download.fileSizeBytes) / (1024f * 1024f),
                                                download.fileSizeBytes / (1024f * 1024f)
                                            )
                                        } else {
                                            "Canceled"
                                        }
                                    }
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when (download.status) {
                                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                                    DownloadStatus.CANCELED -> MaterialTheme.colorScheme.outline
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                Spacer(Modifier.height(8.dp))
                if (download.errorMessage == "merging") {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons at the bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (download.status) {
                    DownloadStatus.QUEUED -> {
                        TextButton(onClick = { viewModel.pauseDownload(download.id) }) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause", maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onDeleteClick(download) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel", maxLines = 1, softWrap = false)
                        }
                    }
                    DownloadStatus.DOWNLOADING -> {
                        TextButton(onClick = { viewModel.pauseDownload(download.id) }) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause", maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onDeleteClick(download) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel", maxLines = 1, softWrap = false)
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        TextButton(onClick = { viewModel.resumeDownload(download.id) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume", maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onRestartClick(download) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restart", maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onDeleteClick(download) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", maxLines = 1, softWrap = false)
                        }
                    }
                    DownloadStatus.FAILED -> {
                        TextButton(onClick = { onRestartClick(download) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry", maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onDeleteClick(download) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", maxLines = 1, softWrap = false)
                        }
                    }
                    DownloadStatus.CANCELED -> {
                        TextButton(onClick = { onRestartClick(download) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restart", maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onDeleteClick(download) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", maxLines = 1, softWrap = false)
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        if (download.filePath.isNotEmpty()) {
                            val isCurrentPlaying = playingVideoId == download.videoId
                            val showPause = isCurrentPlaying && isPlaying
                            TextButton(
                                onClick = {
                                    if (isCurrentPlaying) {
                                        viewModel.togglePlayPause()
                                    } else {
                                        onPlayDownload(download)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (showPause) "Pause" else "Play", maxLines = 1, softWrap = false)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (download.format != "audio" && download.format != "audio_extracted") {
                            val isExtracting = uiState.extractingVideoId == download.videoId
                            TextButton(
                                onClick = { viewModel.extractAudio(download) },
                                enabled = !isExtracting
                            ) {
                                if (isExtracting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text("Extract Audio", maxLines = 1, softWrap = false)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        TextButton(onClick = { onDeleteClick(download) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}
