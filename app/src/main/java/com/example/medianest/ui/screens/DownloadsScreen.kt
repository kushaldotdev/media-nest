package com.example.medianest.ui.screens

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

@Composable
fun DownloadsScreen(
    onPlayDownload: (DownloadEntity) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialogFor by remember { mutableStateOf<DownloadEntity?>(null) }

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
                        onDeleteClick = { showDeleteDialogFor = it }
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
}
@Composable
private fun DownloadItem(
    download: DownloadEntity,
    onPlayDownload: (DownloadEntity) -> Unit,
    viewModel: DownloadsViewModel,
    onDeleteClick: (DownloadEntity) -> Unit
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
                                        if (download.fileSizeBytes > 0L) {
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
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when (download.status) {
                                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
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
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons at the bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (download.status) {
                    DownloadStatus.QUEUED -> {
                        TextButton(onClick = { onDeleteClick(download) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    DownloadStatus.DOWNLOADING -> {
                        TextButton(onClick = { viewModel.pauseDownload(download.id) }) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onDeleteClick(download) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        TextButton(onClick = { viewModel.resumeDownload(download.id) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onDeleteClick(download) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                    DownloadStatus.FAILED -> {
                        TextButton(onClick = { viewModel.retryDownload(download) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onDeleteClick(download) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        if (download.filePath.isNotEmpty()) {
                            TextButton(onClick = { onPlayDownload(download) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Play")
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
                                Text("Extract Audio")
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        TextButton(onClick = { onDeleteClick(download) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
