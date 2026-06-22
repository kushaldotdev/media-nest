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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.ui.viewmodel.DownloadsViewModel

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                    DownloadItem(download = download, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun DownloadItem(download: DownloadEntity, viewModel: DownloadsViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = download.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(60.dp, 40.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(download.title.ifEmpty { download.quality }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    when (download.status) {
                        DownloadStatus.QUEUED -> "Queued"
                        DownloadStatus.DOWNLOADING -> "${(download.progress * 100).toInt()}%"
                        DownloadStatus.PAUSED -> "Paused"
                        DownloadStatus.COMPLETED -> "Completed"
                        DownloadStatus.FAILED -> download.errorMessage ?: "Failed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (download.status) {
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            when (download.status) {
                DownloadStatus.DOWNLOADING -> {
                    IconButton(onClick = { viewModel.pauseDownload(download.id) }) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    }
                }
                DownloadStatus.PAUSED -> {
                    IconButton(onClick = { viewModel.resumeDownload(download.id) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                }
                DownloadStatus.FAILED -> {
                    IconButton(onClick = { viewModel.retryDownload(download) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Retry")
                    }
                }
                DownloadStatus.COMPLETED -> {
                    IconButton(onClick = { viewModel.cancelDownload(download.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
                else -> {}
            }
        }
    }
}
