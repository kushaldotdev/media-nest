package com.example.medianest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.components.EndOfListIndicator
import androidx.compose.material.icons.filled.CheckCircle
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
import com.example.medianest.data.local.entity.VideoEntity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestSemanticColors
import com.example.medianest.ui.viewmodel.DownloadsViewModel

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

private fun compactDuration(ms: Long): String {
    val totalSecs = (ms / 1000L).coerceAtLeast(0L)
    if (totalSecs <= 0L) return "0s"
    return when {
        totalSecs < 60 -> "${totalSecs}s"
        totalSecs < 3600 -> {
            "%dm%02ds".format(totalSecs / 60, totalSecs % 60)
        }
        else -> "%dh%02dm".format(totalSecs / 3600, (totalSecs % 3600) / 60)
    }
}

private fun buildMetaLine(msg: String, format: String): String {
    if (msg.isBlank()) return ""
    val parts = msg.split("|")
    val items = mutableListOf<String>()
    when {
        msg.startsWith("downloading_video") || msg.startsWith("downloading_audio") -> {
            appendSpeed(items, parts, 4)
            appendTiming(items, parts, 5)
        }
        msg.startsWith("merging") -> {
            appendSpeed(items, parts, 4)
            appendTiming(items, parts, 2)
        }
        msg.startsWith("downloading|") -> {
            appendSpeed(items, parts, 1)
            appendTiming(items, parts, 2)
        }
        format == "audio_extracted" && msg.startsWith("extracting|") -> {
            appendTiming(items, parts, 1)
        }
    }
    return items.joinToString(" · ")
}

private fun appendSpeed(items: MutableList<String>, parts: List<String>, index: Int) {
    val speed = parts.getOrNull(index)?.trim().orEmpty()
    if (speed.isEmpty()) return
    val rawBytes = speed.toLongOrNull()
    val formatted = if (rawBytes != null && rawBytes > 0L) {
        val mbps = rawBytes / (1024f * 1024f)
        if (mbps >= 1f) "%.1f MB/s".format(mbps) else "%.0f KB/s".format(rawBytes / 1024f)
    } else {
        speed
    }
    items.add(formatted)
}

private fun appendTiming(items: MutableList<String>, parts: List<String>, offset: Int) {
    val elapsedMs = parts.getOrNull(offset)?.toLongOrNull() ?: return
    val remainingMs = parts.getOrNull(offset + 1)?.toLongOrNull() ?: return
    if (elapsedMs >= 0L) items.add(compactDuration(elapsedMs))
    if (remainingMs > 0L) items.add("${compactDuration(remainingMs)} left")
}

/**
 * Resolves download resolution falling back to configured [defaultResolution] or [DownloadPreferences.DEFAULT_RESOLUTION].
 */
fun resolveDownloadResolution(
    explicitResolution: String? = null,
    defaultResolution: String? = null
): String {
    return explicitResolution?.takeIf { it.isNotBlank() }
        ?: defaultResolution?.takeIf { it.isNotBlank() }
        ?: DownloadPreferences.DEFAULT_RESOLUTION
}

/**
 * Sort categories available in the Downloads screen per design-2 specification.
 */
enum class DownloadSortCategory(val label: String, val defaultMode: String) {
    DATE("Date", "DATE_DESC"),
    PROGRESS("Progress", "PROGRESS_DESC"),
    SIZE("Size", "SIZE_DESC"),
    STATUS("Status", "STATUS_ASC")
}

fun getSortCategory(mode: String): DownloadSortCategory = when {
    mode.startsWith("DATE") -> DownloadSortCategory.DATE
    mode.startsWith("PROGRESS") -> DownloadSortCategory.PROGRESS
    mode.startsWith("SIZE") -> DownloadSortCategory.SIZE
    mode.startsWith("STATUS") -> DownloadSortCategory.STATUS
    else -> DownloadSortCategory.DATE
}

fun isSortAscending(mode: String): Boolean = mode.endsWith("_ASC")

fun toggleSortMode(category: DownloadSortCategory, currentMode: String): String {
    val currentCat = getSortCategory(currentMode)
    return if (currentCat == category) {
        val currentIsAsc = isSortAscending(currentMode)
        val newDir = if (currentIsAsc) "DESC" else "ASC"
        "${category.name}_$newDir"
    } else {
        category.defaultMode
    }
}

/**
 * Sorts the download list according to [sortMode] preference.
 * Supported modes:
 * - DATE_DESC: Newest downloads first (default)
 * - DATE_ASC: Oldest downloads first
 * - PROGRESS_DESC: Highest download progress percentage first
 * - PROGRESS_ASC: Lowest download progress percentage first
 * - SIZE_DESC: Largest file size first
 * - SIZE_ASC: Smallest file size first
 * - STATUS_ASC: Active downloading first, followed by queued, paused, failed, canceled, completed
 * - STATUS_DESC: Completed downloads first, followed by canceled, failed, paused, queued, downloading
 */
fun sortDownloads(
    downloads: List<DownloadEntity>,
    sortMode: String = DownloadPreferences.DEFAULT_SORT_MODE
): List<DownloadEntity> {
    val category = getSortCategory(sortMode)
    val isAsc = isSortAscending(sortMode)
    return when (category) {
        DownloadSortCategory.DATE -> {
            if (isAsc) {
                downloads.sortedWith(
                    compareBy<DownloadEntity> { it.downloadedAt }
                        .thenBy { it.id }
                )
            } else {
                downloads.sortedWith(
                    compareByDescending<DownloadEntity> { it.downloadedAt }
                        .thenByDescending { it.id }
                )
            }
        }
        DownloadSortCategory.PROGRESS -> {
            if (isAsc) {
                downloads.sortedWith(
                    compareBy<DownloadEntity> { it.progress }
                        .thenBy { it.downloadedAt }
                        .thenBy { it.id }
                )
            } else {
                downloads.sortedWith(
                    compareByDescending<DownloadEntity> { it.progress }
                        .thenByDescending { it.downloadedAt }
                        .thenByDescending { it.id }
                )
            }
        }
        DownloadSortCategory.SIZE -> {
            if (isAsc) {
                downloads.sortedWith(
                    compareBy<DownloadEntity> { it.fileSizeBytes }
                        .thenBy { it.downloadedAt }
                        .thenBy { it.id }
                )
            } else {
                downloads.sortedWith(
                    compareByDescending<DownloadEntity> { it.fileSizeBytes }
                        .thenByDescending { it.downloadedAt }
                        .thenByDescending { it.id }
                )
            }
        }
        DownloadSortCategory.STATUS -> {
            fun statusRank(status: DownloadStatus): Int = when (status) {
                DownloadStatus.DOWNLOADING -> 0
                DownloadStatus.QUEUED -> 1
                DownloadStatus.PAUSED -> 2
                DownloadStatus.FAILED -> 3
                DownloadStatus.CANCELED -> 4
                DownloadStatus.COMPLETED -> 5
            }
            if (isAsc) {
                downloads.sortedWith(
                    compareBy<DownloadEntity> { statusRank(it.status) }
                        .thenByDescending { it.downloadedAt }
                        .thenByDescending { it.id }
                )
            } else {
                downloads.sortedWith(
                    compareByDescending<DownloadEntity> { statusRank(it.status) }
                        .thenByDescending { it.downloadedAt }
                        .thenByDescending { it.id }
                )
            }
        }
    }
}

/**
 * Applies in-memory custom queue order to downloads list, falling back to [sortMode] for new items.
 */
fun applyQueueOrder(
    downloads: List<DownloadEntity>,
    queueOrder: List<Long>,
    sortMode: String
): List<DownloadEntity> {
    if (queueOrder.isEmpty()) {
        return sortDownloads(downloads, sortMode)
    }
    val downloadMap = downloads.associateBy { it.id }
    val ordered = queueOrder.mapNotNull { downloadMap[it] }
    val remaining = downloads.filterNot { it.id in queueOrder.toSet() }
    val sortedRemaining = sortDownloads(remaining, sortMode)
    return ordered + sortedRemaining
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onPlayDownload: (DownloadEntity) -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
    downloadPreferences: DownloadPreferences? = null
) {
    val context = LocalContext.current
    val prefs = downloadPreferences ?: remember(context) { DownloadPreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    val sortMode by prefs.sortMode.collectAsStateWithLifecycle(initialValue = DownloadPreferences.DEFAULT_SORT_MODE)
    val defaultResolution by prefs.defaultResolution.collectAsStateWithLifecycle(initialValue = DownloadPreferences.DEFAULT_RESOLUTION)

    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val queueOrder by viewModel.queueOrder.collectAsStateWithLifecycle()
    val sortedDownloads = remember(downloads, sortMode, queueOrder) {
        applyQueueOrder(downloads, queueOrder, sortMode)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playingVideoId by viewModel.playingVideoId.collectAsStateWithLifecycle()
    val playingUri by viewModel.playingUri.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val videosMap by viewModel.videosMap.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val lastWatchedDownload by viewModel.lastWatchedDownload.collectAsStateWithLifecycle()
    val lastWatchedProgress by viewModel.lastWatchedProgress.collectAsStateWithLifecycle()
    val lastWatchedPositionMs by viewModel.lastWatchedPositionMs.collectAsStateWithLifecycle()

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

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshDownloads() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Downloads", style = MaterialTheme.typography.titleLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var sortExpanded by remember { mutableStateOf(false) }
                        val currentCategory = getSortCategory(sortMode)
                        val isAsc = isSortAscending(sortMode)
                        val sortLabel = currentCategory.label
                        val sortIcon = if (isAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                        Box {
                            OutlinedButton(
                                onClick = { sortExpanded = true },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = sortIcon,
                                    contentDescription = "Sort queue: $sortLabel",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(sortLabel, style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = { sortExpanded = false }
                            ) {
                                DownloadSortCategory.values().forEach { category ->
                                    val isActive = currentCategory == category
                                    val categoryIcon = if (isActive) {
                                        if (isAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                                    } else {
                                        if (category == DownloadSortCategory.STATUS) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                                    }
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = categoryIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = category.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        trailingIcon = if (isActive) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            val newMode = toggleSortMode(category, sortMode)
                                            viewModel.clearCustomOrder()
                                            coroutineScope.launch {
                                                prefs.setSortMode(newMode)
                                            }
                                            sortExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { expanded = true },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Max: ${uiState.maxConcurrent}", style = MaterialTheme.typography.labelMedium)
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
                }
                Spacer(Modifier.height(4.dp))
            }

            item {
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
                Spacer(Modifier.height(16.dp))
            }

            // Resume Watching section
            lastWatchedDownload?.let { download ->
                item {
                    Text("Resume Watching", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    GlassCard(
                        onClick = { onPlayDownload(download) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp, 68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                            ) {
                                AsyncImage(
                                    model = download.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MediaNestColors.PlayerSurface.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MediaNestColors.TextPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                if (lastWatchedProgress > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(MediaNestColors.ProgressTrack)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(lastWatchedProgress)
                                                .height(4.dp)
                                                .background(MediaNestColors.YouTubeRed)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val effectiveQuality = download.quality.ifEmpty { resolveDownloadResolution(null, defaultResolution) }
                                Text(
                                    text = download.title.ifEmpty { effectiveQuality },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (lastWatchedPositionMs > 0) {
                                        "Left off at ${com.example.medianest.ui.utils.UiUtils.formatDuration(lastWatchedPositionMs / 1000L)}"
                                    } else {
                                        "Not started"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            if (sortedDownloads.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Download Queue & Files", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${sortedDownloads.size} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (sortedDownloads.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(bottom = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No downloads yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                itemsIndexed(sortedDownloads, key = { _, download -> download.id }) { index, download ->
                    DownloadItem(
                        download = download,
                        index = index,
                        totalCount = sortedDownloads.size,
                        onDragMove = { targetIndex ->
                            viewModel.reorderDownloads(index, targetIndex, sortedDownloads)
                        },
                        hasExtractedAudio = sortedDownloads.any {
                            it.videoId == download.videoId && it.format == "audio_extracted"
                        },
                        videosMap = videosMap,
                        onPlayDownload = onPlayDownload,
                        onVideoClick = onVideoClick,
                        viewModel = viewModel,
                        onDeleteClick = { showDeleteDialogFor = it },
                        onRestartClick = { showRestartDialogFor = it },
                        playingVideoId = playingVideoId,
                        playingUri = playingUri,
                        isPlaying = isPlaying,
                        defaultResolution = defaultResolution
                    )
                }
                item {
                    EndOfListIndicator()
                }
            }
        }
    }

    if (showDeleteDialogFor != null) {
        val download = showDeleteDialogFor!!
        val isActive = download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED
        val effectiveQuality = download.quality.ifEmpty { resolveDownloadResolution(null, defaultResolution) }
        val displayTitle = download.title.ifEmpty { effectiveQuality }
        
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = {
                Text(text = if (isActive) "Cancel Download" else "Delete Download")
            },
            text = {
                Text(
                    text = if (isActive) {
                        "Are you sure you want to cancel downloading \"$displayTitle\"?"
                    } else {
                        "Choose how you want to delete \"$displayTitle\"."
                    }
                )
            },
            confirmButton = {
                if (isActive) {
                    TextButton(
                        onClick = {
                            viewModel.cancelDownload(download.id)
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
        val effectiveQuality = download.quality.ifEmpty { resolveDownloadResolution(null, defaultResolution) }
        val displayTitle = download.title.ifEmpty { effectiveQuality }
        AlertDialog(
            onDismissRequest = { showRestartDialogFor = null },
            title = {
                Text("Restart Download")
            },
            text = {
                Text("Are you sure you want to restart downloading \"$displayTitle\"? This will delete any partially downloaded files and start from scratch.")
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
    index: Int,
    totalCount: Int,
    onDragMove: (targetIndex: Int) -> Unit,
    hasExtractedAudio: Boolean,
    videosMap: Map<String, VideoEntity>,
    onPlayDownload: (DownloadEntity) -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: DownloadsViewModel,
    onDeleteClick: (DownloadEntity) -> Unit,
    onRestartClick: (DownloadEntity) -> Unit,
    playingVideoId: String?,
    playingUri: String?,
    isPlaying: Boolean,
    defaultResolution: String = DownloadPreferences.DEFAULT_RESOLUTION
) {
    var dragAccumulator by remember { mutableStateOf(0f) }
    val dragThresholdPx = 120f
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackHistory by viewModel.playbackHistory.collectAsStateWithLifecycle()

    val formatLabel = when (download.format) {
        "video" -> "Video"
        "video_only" -> "Video"
        "audio" -> "Audio"
        "audio_extracted" -> "Extracted Audio"
        else -> download.format.replaceFirstChar { it.uppercase() }
    }

    val videoEntity = videosMap[download.videoId]
    val durationSeconds = videoEntity?.durationSeconds ?: 0L

    val history = playbackHistory.find { it.videoId == download.videoId }
    val positionMillis = history?.positionMillis ?: 0L
    val progressFraction = if (durationSeconds > 0 && positionMillis > 0) {
        ((positionMillis.toFloat() / 1000f) / durationSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val effectiveQuality = download.quality.ifEmpty { resolveDownloadResolution(null, defaultResolution) }

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
            // Header Row with Drag Handle and Clickable Metadata Row (navigates to Video Details Screen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle with draggable modifier
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                dragAccumulator += delta
                                if (dragAccumulator > dragThresholdPx && index < totalCount - 1) {
                                    onDragMove(index + 1)
                                    dragAccumulator = 0f
                                } else if (dragAccumulator < -dragThresholdPx && index > 0) {
                                    onDragMove(index - 1)
                                    dragAccumulator = 0f
                                }
                            },
                            onDragStopped = {
                                dragAccumulator = 0f
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Reorder Drag Handle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Clickable Metadata Row (navigates to Video Details Screen)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onVideoClick(download.videoId) },
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
                        if (download.status == DownloadStatus.COMPLETED && download.errorMessage != "file_missing") {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .background(
                                        color = MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = MediaNestSemanticColors.Completed,
                                    modifier = Modifier.size(12.dp)
                                )
                                if (download.fileSizeBytes > 0L) {
                                    Text(
                                        text = "%.1f MB".format(download.fileSizeBytes / (1024f * 1024f)),
                                        color = MediaNestColors.TextPrimary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        if (durationSeconds > 0) {
                            Text(
                                text = com.example.medianest.ui.utils.UiUtils.formatDuration(durationSeconds),
                                color = MediaNestColors.TextPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(
                                        color = MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        if (download.status == DownloadStatus.COMPLETED && durationSeconds > 0 && positionMillis > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(MediaNestColors.ProgressTrack)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFraction)
                                        .height(3.dp)
                                        .background(MediaNestColors.YouTubeRed)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // Title, Format Badge, and Quality text
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = download.title.ifEmpty { effectiveQuality },
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
                                text = effectiveQuality,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (download.status == DownloadStatus.COMPLETED && positionMillis > 0) {
                                Text(
                                    text = "Left off at ${com.example.medianest.ui.utils.UiUtils.formatDuration(positionMillis / 1000L)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (download.status != DownloadStatus.COMPLETED || download.errorMessage == "file_missing") {
                // Status and Speed info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when (download.status) {
                        DownloadStatus.QUEUED -> "Queued"
                        DownloadStatus.DOWNLOADING -> {
                            val msg = download.errorMessage ?: ""
                            val meta = buildMetaLine(msg, download.format)
                            if (msg.startsWith("downloading_video")) {
                                val parts = msg.split("|")
                                val vDownloaded = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                                val vTotal = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                                val aTotal = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                                
                                val totalSize = vTotal + aTotal
                                val downloadedMb = vDownloaded / (1024f * 1024f)
                                val totalMb = totalSize / (1024f * 1024f)
                                val pct = (download.progress * 100).toInt()
                                
                                listOf("%.1fMB / %.1fMB (%d%%)".format(downloadedMb, totalMb, pct), meta).joinToString("\n")
                            } else if (msg.startsWith("downloading_audio")) {
                                val parts = msg.split("|")
                                if (parts.size >= 4) {
                                    val aDownloaded = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                                    val aTotal = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                                    val vTotal = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                                    
                                    val totalDownloaded = vTotal + aDownloaded
                                    val totalSize = vTotal + aTotal
                                    val downloadedMb = totalDownloaded / (1024f * 1024f)
                                    val totalMb = totalSize / (1024f * 1024f)
                                    val pct = (download.progress * 100).toInt()
                                    
                                listOf("Downloading audio: %.1fMB / %.1fMB (%d%%)".format(downloadedMb, totalMb, pct), meta).joinToString("\n")
                                } else {
                                    val speedPart = msg.substringAfter("|", "")
                                    if (speedPart.isNotEmpty()) {
                                        "Downloading audio ($speedPart)..."
                                    } else {
                                        "Downloading audio..."
                                    }
                                }
                            } else if (msg.startsWith("merging")) {
                                val parts = msg.split("|")
                                val pctPart = parts.getOrNull(1) ?: ""
                                val pct = pctPart.toIntOrNull()
                                if (pct != null && pct >= 0) {
                                    listOf("Merging video & audio ($pct%)", meta).joinToString("\n")
                                } else {
                                    "Merging video & audio..."
                                }
                            } else if (download.format == "audio_extracted" && msg.startsWith("extracting|")) {
                                val pct = (download.progress * 100).toInt()
                                listOf("Extracting audio: $pct%", meta).joinToString("\n")
                            } else if (msg.startsWith("downloading|")) {
                                val pctLine = "${(download.progress * 100).toInt()}%"
                                listOf(pctLine, meta).joinToString("\n")
                            } else if (download.fileSizeBytes > 0L) {
                                val downloadedMb = (download.progress * download.fileSizeBytes) / (1024f * 1024f)
                                val totalMb = download.fileSizeBytes / (1024f * 1024f)
                                val pct = (download.progress * 100).toInt()
                                if (msg.isNotEmpty() && !msg.startsWith("downloading_audio") && !msg.startsWith("merging")) {
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
                        DownloadStatus.COMPLETED -> if (download.errorMessage == "file_missing") "Source missing" else ""
                    }
                    Text(
                        text = statusText,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = when (download.status) {
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                            DownloadStatus.CANCELED -> MaterialTheme.colorScheme.outline
                            DownloadStatus.COMPLETED -> if (download.errorMessage == "file_missing") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Progress Bar
            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                Spacer(Modifier.height(4.dp))
                val msg = download.errorMessage ?: ""
                val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                val videoColor = MaterialTheme.colorScheme.primary
                val audioColor = MediaNestColors.AudioDownload
                val mergeColor = MediaNestColors.Success

                if (msg == "merging") {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    ) {
                        val width = size.width
                        val height = size.height

                        // Draw background track
                        drawRect(
                            color = trackColor,
                            size = size
                        )

                        if (msg.startsWith("merging")) {
                            val pctPart = msg.split("|").getOrNull(1) ?: ""
                            val pct = pctPart.toFloatOrNull() ?: 0f
                            val filledWidth = (pct / 100f) * width
                            drawRect(
                                color = mergeColor,
                                size = androidx.compose.ui.geometry.Size(filledWidth, height)
                            )
                        } else if (download.format == "video_only" && (msg.startsWith("downloading_video") || msg.startsWith("downloading_audio"))) {
                            if (msg.startsWith("downloading_audio")) {
                                // Video portion stays blue; only the audio segment is orange
                                val parts = msg.split("|")
                                val videoSize = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                                val audioDownloaded = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                                val audioTotal = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                                val totalSize = videoSize + audioTotal
                                if (totalSize > 0L) {
                                    val videoEnd = (videoSize.toFloat() / totalSize) * width
                                    val audioEnd = ((videoSize + audioDownloaded).toFloat() / totalSize) * width
                                    // Blue: the already-downloaded video portion (0 .. videoEnd)
                                    drawRect(
                                        color = videoColor,
                                        size = androidx.compose.ui.geometry.Size(videoEnd, height)
                                    )
                                    // Orange: the audio portion being downloaded (videoEnd .. audioEnd)
                                    if (audioEnd > videoEnd) {
                                        drawRect(
                                            color = audioColor,
                                            topLeft = androidx.compose.ui.geometry.Offset(videoEnd, 0f),
                                            size = androidx.compose.ui.geometry.Size(audioEnd - videoEnd, height)
                                        )
                                    }
                                }
                            } else {
                                drawRect(
                                    color = videoColor,
                                    size = androidx.compose.ui.geometry.Size(download.progress * width, height)
                                )
                            }
                        } else {
                            // Pure audio or standard download format
                            val barColor = if (download.format == "audio" || download.format == "audio_extracted") audioColor else videoColor
                            drawRect(
                                color = barColor,
                                size = androidx.compose.ui.geometry.Size(download.progress * width, height)
                            )
                        }
                    }
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
                horizontalArrangement = if (download.status == DownloadStatus.COMPLETED && download.errorMessage != "file_missing") Arrangement.SpaceBetween else Arrangement.End,
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
                        if (download.errorMessage == "file_missing") {
                            if (download.format != "audio_extracted") {
                                Button(
                                    onClick = { onRestartClick(download) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Redownload", style = MaterialTheme.typography.labelMedium)
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
                        } else {
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
                            }
                            if (download.format != "audio" && download.format != "audio_extracted" && !hasExtractedAudio) {
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
}
