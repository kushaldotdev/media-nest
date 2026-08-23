package com.example.medianest.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.R
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.preferences.CollectionsPreferences
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.ui.components.DownloadProgressBar
import com.example.medianest.ui.components.DownloadProgressStage
import com.example.medianest.ui.components.EmptyState
import com.example.medianest.ui.components.EndOfListIndicator
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.components.FullTitlesToggle
import com.example.medianest.ui.components.LocalFullTitles
import com.example.medianest.ui.components.LoadingState
import com.example.medianest.ui.components.MediaNestButton
import com.example.medianest.ui.components.MediaNestButtonSize
import com.example.medianest.ui.components.MediaNestButtonVariant
import com.example.medianest.ui.components.MediaNestChip
import com.example.medianest.ui.components.MediaNestFilterRow
import com.example.medianest.ui.components.MediaNestIconButton
import com.example.medianest.ui.components.MediaNestIconButtonSize
import com.example.medianest.ui.components.MediaNestSortBottomSheet
import com.example.medianest.ui.components.MediaNestSortOption
import com.example.medianest.ui.components.MediaNestTopAppBar
import com.example.medianest.ui.components.NotificationBellAction
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestSemanticColors
import com.example.medianest.ui.theme.MediaNestShapes
import com.example.medianest.ui.utils.UiUtils
import com.example.medianest.ui.viewmodel.DownloadsViewModel
import com.example.medianest.ui.viewmodel.PendingRestartConfirmation
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayList

// -----------------------------------------------------------------------------
// Helper Utilities & Sort Functions
// -----------------------------------------------------------------------------

fun compactDuration(ms: Long): String {
    val totalSecs = (ms / 1000L).coerceAtLeast(0L)
    if (totalSecs <= 0L) return "0s"
    return when {
        totalSecs < 60 -> "${totalSecs}s"
        totalSecs < 3600 -> "%dm%02ds".format(totalSecs / 60, totalSecs % 60)
        else -> "%dh%02dm".format(totalSecs / 3600, (totalSecs % 3600) / 60)
    }
}

fun buildMetaLine(msg: String, format: String): String {
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024f
    val mb = kb / 1024f
    val gb = mb / 1024f
    return when {
        gb >= 1f -> "%.2f GB".format(gb)
        mb >= 1f -> "%.1f MB".format(mb)
        kb >= 1f -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}

private fun formatSpeed(rawSpeed: String?): String? {
    if (rawSpeed.isNullOrBlank()) return null
    val rawBytes = rawSpeed.trim().toLongOrNull()
    return if (rawBytes != null && rawBytes > 0L) {
        val mbps = rawBytes / (1024f * 1024f)
        if (mbps >= 1f) "%.1f MB/s".format(mbps) else "%.0f KB/s".format(rawBytes / 1024f)
    } else {
        rawSpeed.trim().takeIf { it.isNotBlank() }
    }
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
 * Sort categories available in the Downloads screen per Design 2.0 specification.
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

enum class CompletedMediaFilter(val label: String) {
    ALL("All"),
    VIDEO("Video"),
    AUDIO("Audio")
}

data class ParsedProgressInfo(
    val stage: DownloadProgressStage,
    val videoProgress: Float?,
    val audioProgress: Float?,
    val speedText: String?,
    val etaText: String?,
    val statusText: String,
    val percentage: Int,
    val elapsedText: String? = null
)

fun parseDownloadProgress(download: DownloadEntity): ParsedProgressInfo {
    val msg = download.errorMessage ?: ""
    val pct = (download.progress.coerceIn(0f, 1f) * 100).toInt()

    when (download.status) {
        DownloadStatus.QUEUED -> {
            return ParsedProgressInfo(
                stage = DownloadProgressStage.QUEUED,
                videoProgress = null,
                audioProgress = null,
                speedText = null,
                etaText = null,
                statusText = "Waiting in queue...",
                percentage = 0,
                elapsedText = null
            )
        }
        DownloadStatus.PAUSED -> {
            val statusText = if (download.fileSizeBytes > 0L) {
                "Paused · %.1f MB / %.1f MB".format(
                    (download.progress * download.fileSizeBytes) / (1024f * 1024f),
                    download.fileSizeBytes / (1024f * 1024f)
                )
            } else {
                "Paused"
            }
            return ParsedProgressInfo(
                stage = DownloadProgressStage.PAUSED,
                videoProgress = null,
                audioProgress = null,
                speedText = null,
                etaText = null,
                statusText = statusText,
                percentage = pct,
                elapsedText = null
            )
        }
        DownloadStatus.FAILED -> {
            return ParsedProgressInfo(
                stage = DownloadProgressStage.FAILED,
                videoProgress = null,
                audioProgress = null,
                speedText = null,
                etaText = null,
                statusText = if (msg.isNotBlank()) msg else "Download failed",
                percentage = pct,
                elapsedText = null
            )
        }
        DownloadStatus.CANCELED -> {
            return ParsedProgressInfo(
                stage = DownloadProgressStage.CANCELED,
                videoProgress = null,
                audioProgress = null,
                speedText = null,
                etaText = null,
                statusText = "Canceled",
                percentage = pct,
                elapsedText = null
            )
        }
        DownloadStatus.COMPLETED -> {
            if (msg == "file_missing") {
                return ParsedProgressInfo(
                    stage = DownloadProgressStage.FAILED,
                    videoProgress = null,
                    audioProgress = null,
                    speedText = null,
                    etaText = null,
                    statusText = "Source file missing",
                    percentage = 100,
                    elapsedText = null
                )
            }
            return ParsedProgressInfo(
                stage = DownloadProgressStage.COMPLETED,
                videoProgress = null,
                audioProgress = null,
                speedText = null,
                etaText = null,
                statusText = "Completed · %.1f MB".format(download.fileSizeBytes / (1024f * 1024f)),
                percentage = 100,
                elapsedText = null
            )
        }
        DownloadStatus.DOWNLOADING -> {
            val parts = msg.split("|")

            if (msg.startsWith("downloading_video")) {
                val vDownloaded = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                val vTotal = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                val aTotal = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                val speedRaw = parts.getOrNull(4)
                val elapsedMs = parts.getOrNull(5)?.toLongOrNull()
                val remainingMs = parts.getOrNull(6)?.toLongOrNull()

                val totalSize = vTotal + aTotal
                val downloadedMb = vDownloaded / (1024f * 1024f)
                val totalMb = if (totalSize > 0) totalSize / (1024f * 1024f) else (download.fileSizeBytes / (1024f * 1024f))

                val speedText = formatSpeed(speedRaw)
                val elapsedText = elapsedMs?.takeIf { it >= 0 }?.let { compactDuration(it) }
                val etaText = remainingMs?.takeIf { it > 0 }?.let { "${compactDuration(it)} left" }
                val statusText = "Downloading Video · %.1f MB / %.1f MB".format(downloadedMb, totalMb)

                return ParsedProgressInfo(
                    stage = DownloadProgressStage.VIDEO,
                    videoProgress = if (vTotal > 0) (vDownloaded.toFloat() / vTotal.toFloat()).coerceIn(0f, 1f) else download.progress,
                    audioProgress = 0f,
                    speedText = speedText,
                    etaText = etaText,
                    statusText = statusText,
                    percentage = pct,
                    elapsedText = elapsedText
                )
            } else if (msg.startsWith("downloading_audio")) {
                val aDownloaded = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                val aTotal = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                val vTotal = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                val speedRaw = parts.getOrNull(4)
                val elapsedMs = parts.getOrNull(5)?.toLongOrNull()
                val remainingMs = parts.getOrNull(6)?.toLongOrNull()

                val totalSize = vTotal + aTotal
                val totalDownloaded = vTotal + aDownloaded
                val downloadedMb = totalDownloaded / (1024f * 1024f)
                val totalMb = if (totalSize > 0) totalSize / (1024f * 1024f) else (download.fileSizeBytes / (1024f * 1024f))

                val speedText = formatSpeed(speedRaw)
                val elapsedText = elapsedMs?.takeIf { it >= 0 }?.let { compactDuration(it) }
                val etaText = remainingMs?.takeIf { it > 0 }?.let { "${compactDuration(it)} left" }
                val statusText = "Downloading Audio · %.1f MB / %.1f MB".format(downloadedMb, totalMb)

                val isDual = download.format == "video_only" || vTotal > 0
                return ParsedProgressInfo(
                    stage = DownloadProgressStage.AUDIO,
                    videoProgress = if (isDual) 1.0f else null,
                    audioProgress = if (isDual && aTotal > 0) (aDownloaded.toFloat() / aTotal.toFloat()).coerceIn(0f, 1f) else null,
                    speedText = speedText,
                    etaText = etaText,
                    statusText = statusText,
                    percentage = pct,
                    elapsedText = elapsedText
                )
            } else if (msg.startsWith("merging")) {
                val pctPart = parts.getOrNull(1)?.toIntOrNull()
                val elapsedMs = parts.getOrNull(2)?.toLongOrNull()
                val remainingMs = parts.getOrNull(3)?.toLongOrNull()
                val speedRaw = parts.getOrNull(4)
                val speedText = formatSpeed(speedRaw)
                val elapsedText = elapsedMs?.takeIf { it >= 0 }?.let { compactDuration(it) }
                val etaText = remainingMs?.takeIf { it > 0 }?.let { "${compactDuration(it)} left" }
                val statusText = if (pctPart != null) "Merging Video & Audio ($pctPart%)" else "Merging Video & Audio..."

                return ParsedProgressInfo(
                    stage = DownloadProgressStage.MERGING,
                    videoProgress = null,
                    audioProgress = null,
                    speedText = speedText,
                    etaText = etaText,
                    statusText = statusText,
                    percentage = pctPart ?: pct,
                    elapsedText = elapsedText
                )
            } else if (download.format == "audio_extracted" || msg.startsWith("extracting")) {
                val elapsedMs = parts.getOrNull(1)?.toLongOrNull()
                val remainingMs = parts.getOrNull(2)?.toLongOrNull()
                val elapsedText = elapsedMs?.takeIf { it >= 0 }?.let { compactDuration(it) }
                val etaText = remainingMs?.takeIf { it > 0 }?.let { "${compactDuration(it)} left" }
                return ParsedProgressInfo(
                    stage = DownloadProgressStage.EXTRACTING,
                    videoProgress = null,
                    audioProgress = null,
                    speedText = null,
                    etaText = etaText,
                    statusText = "Extracting Audio ($pct%)",
                    percentage = pct,
                    elapsedText = elapsedText
                )
            } else if (msg.startsWith("downloading|")) {
                val speedRaw = parts.getOrNull(1)
                val elapsedMs = parts.getOrNull(2)?.toLongOrNull()
                val remainingMs = parts.getOrNull(3)?.toLongOrNull()
                val speedText = formatSpeed(speedRaw)
                val elapsedText = elapsedMs?.takeIf { it >= 0 }?.let { compactDuration(it) }
                val etaText = remainingMs?.takeIf { it > 0 }?.let { "${compactDuration(it)} left" }
                val downloadedMb = (download.progress * download.fileSizeBytes) / (1024f * 1024f)
                val totalMb = download.fileSizeBytes / (1024f * 1024f)
                val statusText = if (download.fileSizeBytes > 0) "%.1f MB / %.1f MB".format(downloadedMb, totalMb) else "Downloading..."

                val stage = if (download.format == "audio" || download.format == "audio_extracted") DownloadProgressStage.AUDIO else DownloadProgressStage.VIDEO
                return ParsedProgressInfo(
                    stage = stage,
                    videoProgress = null,
                    audioProgress = null,
                    speedText = speedText,
                    etaText = etaText,
                    statusText = statusText,
                    percentage = pct,
                    elapsedText = elapsedText
                )
            } else {
                val downloadedMb = (download.progress * download.fileSizeBytes) / (1024f * 1024f)
                val totalMb = download.fileSizeBytes / (1024f * 1024f)
                val statusText = if (download.fileSizeBytes > 0) "%.1f MB / %.1f MB".format(downloadedMb, totalMb) else "Downloading ($pct%)"
                val stage = if (download.format == "audio" || download.format == "audio_extracted") DownloadProgressStage.AUDIO else DownloadProgressStage.VIDEO
                return ParsedProgressInfo(
                    stage = stage,
                    videoProgress = null,
                    audioProgress = null,
                    speedText = null,
                    etaText = null,
                    statusText = statusText,
                    percentage = pct,
                    elapsedText = null
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Downloads Screen Implementation (Design 2.0)
// -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onPlayDownload: (DownloadEntity) -> Unit,
    onVideoClick: (String) -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel(LocalContext.current as ComponentActivity),
    downloadPreferences: DownloadPreferences? = null
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val prefs = downloadPreferences ?: remember(context) { DownloadPreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    val sortMode by prefs.sortMode.collectAsStateWithLifecycle(initialValue = DownloadPreferences.DEFAULT_SORT_MODE)
    val defaultResolution by prefs.defaultResolution.collectAsStateWithLifecycle(initialValue = DownloadPreferences.DEFAULT_RESOLUTION)
    val collectionsPreferences = remember(context) { CollectionsPreferences(context) }
    val globalFullTitles by collectionsPreferences.fullTitles.collectAsStateWithLifecycle(
        initialValue = CollectionsPreferences.DEFAULT_FULL_TITLES
    )
    var fullTitles by remember(globalFullTitles) { mutableStateOf(globalFullTitles) }

    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val queueOrder by viewModel.queueOrder.collectAsStateWithLifecycle()
    val sortedDownloads = remember(downloads, sortMode, queueOrder) {
        applyQueueOrder(downloads ?: emptyList(), queueOrder, sortMode)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playingVideoId by viewModel.playingVideoId.collectAsStateWithLifecycle()
    val playingUri by viewModel.playingUri.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val videosMap by viewModel.videosMap.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    // Dialog and interactive states
    var showDeleteDialogFor by remember { mutableStateOf<DownloadEntity?>(null) }
    var showRestartDialogFor by remember { mutableStateOf<DownloadEntity?>(null) }
    var pendingDialogId by remember { mutableStateOf<Long?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMaxConcurrentDialog by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }

    // Segmented filtering and batch selection
    var completedFilter by remember { mutableStateOf(CompletedMediaFilter.ALL) }
    var isBatchMode by remember { mutableStateOf(false) }
    var selectedBatchIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Separate Active vs Completed downloads
    val activeDownloads = remember(sortedDownloads) {
        sortedDownloads.filter { it.status != DownloadStatus.COMPLETED || it.errorMessage == "file_missing" }
    }
    val completedDownloads = remember(sortedDownloads) {
        sortedDownloads.filter { it.status == DownloadStatus.COMPLETED && it.errorMessage != "file_missing" }
    }

    val completedVideoCount = remember(completedDownloads) {
        completedDownloads.count { !it.format.contains("audio") }
    }
    val completedAudioCount = remember(completedDownloads) {
        completedDownloads.count { it.format.contains("audio") }
    }

    val filteredCompleted = remember(completedDownloads, completedFilter) {
        when (completedFilter) {
            CompletedMediaFilter.ALL -> completedDownloads
            CompletedMediaFilter.VIDEO -> completedDownloads.filter { !it.format.contains("audio") }
            CompletedMediaFilter.AUDIO -> completedDownloads.filter { it.format.contains("audio") }
        }
    }

    LaunchedEffect(Unit) {
        PendingRestartConfirmation.pendingDownloadId.collect { id ->
            pendingDialogId = id
        }
    }

    LaunchedEffect(downloads, pendingDialogId) {
        val id = pendingDialogId
        val currentDownloads = downloads
        if (id != null && !currentDownloads.isNullOrEmpty()) {
            val download = currentDownloads.find { it.id == id }
            if (download != null) {
                showRestartDialogFor = download
                pendingDialogId = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MediaNestColors.Background)
    ) {
        MediaNestTopAppBar(
            title = "Downloads",
            subtitle = "Offline downloads & queue",
            actions = {
                NotificationBellAction(onClick = onNavigateToNotifications)
            }
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshDownloads() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val currentDlList = downloads
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
            ) {
                // -----------------------------------------------------------------
                // Top Header & Controls Toolbar
                // -----------------------------------------------------------------
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sort Bottom Sheet Trigger
                                val currentCategory = getSortCategory(sortMode)
                                val isAsc = isSortAscending(sortMode)
                                MediaNestButton(
                                    text = currentCategory.label,
                                    onClick = { showSortBottomSheet = true },
                                    variant = MediaNestButtonVariant.Secondary,
                                    size = MediaNestButtonSize.Small,
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(
                                                if (isAsc) R.drawable.ic_mn_arrow_up else R.drawable.ic_mn_arrow_down
                                            ),
                                            contentDescription = "Sort",
                                            tint = MediaNestColors.Accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )

                                // Max Concurrent Trigger
                                MediaNestButton(
                                    text = "Max: ${uiState.maxConcurrent}",
                                    onClick = { showMaxConcurrentDialog = true },
                                    variant = MediaNestButtonVariant.Secondary,
                                    size = MediaNestButtonSize.Small,
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_sliders),
                                            contentDescription = "Max concurrent",
                                            tint = MediaNestColors.Accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }

                            FullTitlesToggle(
                                checked = fullTitles,
                                onCheckedChange = { fullTitles = it }
                            )
                        }

                        // Secondary Action Controls Row (Pause All / Resume All)
                        if (!downloads.isNullOrEmpty()) {
                            val hasPauseable = activeDownloads.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
                            val hasResumeable = activeDownloads.any { it.status == DownloadStatus.PAUSED }
                            if (hasPauseable || hasResumeable) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hasPauseable) {
                                        MediaNestButton(
                                            text = "Pause All",
                                            onClick = { viewModel.pauseAllDownloads() },
                                            variant = MediaNestButtonVariant.Ghost,
                                            size = MediaNestButtonSize.ExtraSmall,
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_mn_pause),
                                                    contentDescription = null,
                                                    tint = MediaNestColors.TextSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        )
                                    }

                                    if (hasResumeable) {
                                        MediaNestButton(
                                            text = "Resume All",
                                            onClick = { viewModel.resumeAllDownloads() },
                                            variant = MediaNestButtonVariant.Ghost,
                                            size = MediaNestButtonSize.ExtraSmall,
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_mn_play),
                                                    contentDescription = null,
                                                    tint = MediaNestColors.Accent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            if (currentDlList == null) {
                item {
                    LoadingState()
                }
            } else if (currentDlList.isEmpty()) {
                item {
                    EmptyState(
                        title = "No downloads yet",
                        message = "Downloads will appear here. Extract a video or playlist from the Home tab.",
                        iconContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_download),
                                contentDescription = null,
                                tint = MediaNestColors.TextSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        modifier = Modifier.padding(top = 40.dp)
                    )
                }
            } else {
                // -----------------------------------------------------------------
                // Download Storage & Status Overview Card
                // -----------------------------------------------------------------
                item {
                    DownloadStatsCard(downloads = currentDlList)
                }
            }

            // -----------------------------------------------------------------
            // Active Downloads Section
            // -----------------------------------------------------------------
            if (activeDownloads.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Active Queue",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MediaNestColors.TextPrimary
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MediaNestColors.AccentDeep)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${activeDownloads.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MediaNestColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                itemsIndexed(activeDownloads, key = { _, dl -> dl.id }) { index, download ->
                    ActiveDownloadCard(
                        download = download,
                        index = index,
                        totalCount = activeDownloads.size,
                        onDragMove = { targetIndex ->
                            viewModel.reorderDownloads(index, targetIndex, activeDownloads)
                        },
                        videosMap = videosMap,
                        onVideoClick = onVideoClick,
                        onDeleteClick = { showDeleteDialogFor = it },
                        onRestartClick = { showRestartDialogFor = it },
                        viewModel = viewModel,
                        defaultResolution = defaultResolution,
                        fullTitles = fullTitles
                    )
                }
                item { EndOfListIndicator() }
            }

            // -----------------------------------------------------------------
            // Completed Downloads Section & Filters
            // -----------------------------------------------------------------
            if (completedDownloads.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (activeDownloads.isNotEmpty()) 8.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Completed Downloads",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MediaNestColors.TextPrimary
                                    )
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MediaNestColors.Raised)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${completedDownloads.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MediaNestColors.TextSecondary
                                    )
                                }
                            }

                            // Batch Select Toggle
                            MediaNestIconButton(
                                onClick = {
                                    isBatchMode = !isBatchMode
                                    if (!isBatchMode) selectedBatchIds = emptySet()
                                },
                                size = MediaNestIconButtonSize.Small,
                                tint = if (isBatchMode) MediaNestColors.Accent else MediaNestColors.TextSecondary,
                                containerColor = if (isBatchMode) MediaNestColors.Raised else Color.Transparent
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_checkbox),
                                    contentDescription = "Batch Select Mode",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Segmented Filter Bar: All / Video / Audio
                        MediaNestFilterRow(
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                        ) {
                            MediaNestChip(
                                label = "All",
                                badgeText = "${completedDownloads.size}",
                                selected = completedFilter == CompletedMediaFilter.ALL,
                                onClick = { completedFilter = CompletedMediaFilter.ALL }
                            )

                            MediaNestChip(
                                label = "Video",
                                badgeText = "$completedVideoCount",
                                selected = completedFilter == CompletedMediaFilter.VIDEO,
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_video),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                onClick = { completedFilter = CompletedMediaFilter.VIDEO }
                            )

                            MediaNestChip(
                                label = "Audio",
                                badgeText = "$completedAudioCount",
                                selected = completedFilter == CompletedMediaFilter.AUDIO,
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_music),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                onClick = { completedFilter = CompletedMediaFilter.AUDIO }
                            )
                        }

                        // Batch Selection Action Toolbar
                        AnimatedVisibility(
                            visible = isBatchMode,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MediaNestShapes.Card,
                                color = MediaNestColors.Raised,
                                border = BorderStroke(1.dp, MediaNestColors.Border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val allSelected = selectedBatchIds.size == filteredCompleted.size && filteredCompleted.isNotEmpty()
                                        MediaNestButton(
                                            text = if (allSelected) "Deselect All" else "Select All",
                                            onClick = {
                                                selectedBatchIds = if (allSelected) emptySet() else filteredCompleted.map { it.id }.toSet()
                                            },
                                            variant = MediaNestButtonVariant.Secondary,
                                            size = MediaNestButtonSize.ExtraSmall
                                        )

                                        Text(
                                            text = "${selectedBatchIds.size} selected",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MediaNestColors.TextPrimary
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        MediaNestButton(
                                            text = "Delete",
                                            onClick = { showBatchDeleteDialog = true },
                                            variant = MediaNestButtonVariant.DangerSolid,
                                            size = MediaNestButtonSize.ExtraSmall,
                                            enabled = selectedBatchIds.isNotEmpty(),
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_mn_trash),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (filteredCompleted.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No ${completedFilter.label.lowercase()} downloads",
                            message = "No completed items found for this category.",
                            iconContent = {
                                Icon(
                                    painter = painterResource(
                                        if (completedFilter == CompletedMediaFilter.AUDIO) R.drawable.ic_mn_music else R.drawable.ic_mn_video
                                    ),
                                    contentDescription = null,
                                    tint = MediaNestColors.TextSecondary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        )
                    }
                } else {
                    items(filteredCompleted, key = { it.id }) { download ->
                        CompletedDownloadCard(
                            download = download,
                            videosMap = videosMap,
                            onPlayDownload = onPlayDownload,
                            onVideoClick = onVideoClick,
                            onDeleteClick = { showDeleteDialogFor = it },
                            hasExtractedAudio = sortedDownloads.any {
                                it.videoId == download.videoId && it.format == "audio_extracted"
                            },
                            viewModel = viewModel,
                            isBatchMode = isBatchMode,
                            isSelected = selectedBatchIds.contains(download.id),
                            onToggleSelect = { id ->
                                selectedBatchIds = if (selectedBatchIds.contains(id)) {
                                    selectedBatchIds - id
                                } else {
                                    selectedBatchIds + id
                                }
                            },
                            playingVideoId = playingVideoId,
                            playingUri = playingUri,
                            isPlaying = isPlaying,
                            defaultResolution = defaultResolution,
                            fullTitles = fullTitles
                        )
                    }
                }
            }

            if (downloads?.isNotEmpty() == true) {
                item {
                    EndOfListIndicator()
                }
            }
        }
    }
    }

    // -------------------------------------------------------------------------
    // Dialogs & Sheets
    // -------------------------------------------------------------------------

    // Sort Bottom Sheet
    if (showSortBottomSheet) {
        val currentCategory = getSortCategory(sortMode)
        val isAsc = isSortAscending(sortMode)
        val downloadSortOptions = remember {
            listOf(
                MediaNestSortOption(id = "DATE", label = "Date Added", description = "Recently added or downloaded"),
                MediaNestSortOption(id = "PROGRESS", label = "Progress", description = "Download completion percentage"),
                MediaNestSortOption(id = "SIZE", label = "File Size", description = "Total media storage size"),
                MediaNestSortOption(id = "STATUS", label = "Download Status", description = "Active, queued, paused, completed")
            )
        }

        MediaNestSortBottomSheet(
            onDismissRequest = { showSortBottomSheet = false },
            selectedSortBy = currentCategory.name,
            isAscending = isAsc,
            options = downloadSortOptions,
            fullTitles = fullTitles,
            onFullTitlesChange = { fullTitles = it },
            onSortSelected = { newCat, newAsc ->
                val dir = if (newAsc) "ASC" else "DESC"
                val newMode = "${newCat}_$dir"
                viewModel.clearCustomOrder()
                coroutineScope.launch {
                    prefs.setSortMode(newMode)
                }
            }
        )
    }

    // Max Concurrent Downloads Dialog
    if (showMaxConcurrentDialog) {
        AlertDialog(
            onDismissRequest = { showMaxConcurrentDialog = false },
            containerColor = MediaNestColors.Raised,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = MediaNestShapes.Hero,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_sliders),
                        contentDescription = null,
                        tint = MediaNestColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Max Concurrent Downloads",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Select maximum number of downloads running simultaneously:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MediaNestColors.TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    (1..5).forEach { count ->
                        val isSelected = uiState.maxConcurrent == count
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MediaNestShapes.Card)
                                .clickable {
                                    viewModel.setMaxConcurrent(count)
                                    showMaxConcurrentDialog = false
                                },
                            shape = MediaNestShapes.Card,
                            color = if (isSelected) MediaNestColors.AccentDeep else MediaNestColors.Card,
                            border = if (isSelected) null else BorderStroke(1.dp, MediaNestColors.Border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$count concurrent download${if (count > 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary
                                    )
                                )
                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_check),
                                        contentDescription = "Selected",
                                        tint = MediaNestColors.Accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                MediaNestButton(
                    text = "Close",
                    onClick = { showMaxConcurrentDialog = false },
                    variant = MediaNestButtonVariant.Ghost,
                    size = MediaNestButtonSize.Small
                )
            }
        )
    }

    // Delete Single Download Dialog
    if (showDeleteDialogFor != null) {
        val download = showDeleteDialogFor!!
        val isActive = download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED
        val effectiveQuality = download.quality.ifEmpty { resolveDownloadResolution(null, defaultResolution) }
        val displayTitle = download.title.ifEmpty { effectiveQuality }

        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            containerColor = MediaNestColors.Raised,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = MediaNestShapes.Hero,
            title = {
                Text(
                    text = if (isActive) "Cancel Download" else "Delete Download",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Text(
                    text = if (isActive) {
                        "Are you sure you want to cancel downloading \"$displayTitle\"?"
                    } else {
                        "Choose how you want to delete \"$displayTitle\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediaNestColors.TextSecondary
                )
            },
            confirmButton = {
                if (isActive) {
                    MediaNestButton(
                        text = "Cancel Download",
                        onClick = {
                            viewModel.cancelDownload(download.id)
                            showDeleteDialogFor = null
                        },
                        variant = MediaNestButtonVariant.DangerSolid,
                        size = MediaNestButtonSize.Small
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MediaNestButton(
                            text = "List Only",
                            onClick = {
                                viewModel.deleteDownload(download, deleteFile = false)
                                showDeleteDialogFor = null
                            },
                            variant = MediaNestButtonVariant.Secondary,
                            size = MediaNestButtonSize.Small
                        )
                        MediaNestButton(
                            text = "Delete File & List",
                            onClick = {
                                viewModel.deleteDownload(download, deleteFile = true)
                                showDeleteDialogFor = null
                            },
                            variant = MediaNestButtonVariant.DangerSolid,
                            size = MediaNestButtonSize.Small
                        )
                    }
                }
            },
            dismissButton = {
                MediaNestButton(
                    text = "Keep",
                    onClick = { showDeleteDialogFor = null },
                    variant = MediaNestButtonVariant.Ghost,
                    size = MediaNestButtonSize.Small
                )
            }
        )
    }

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteDialog) {
        val count = selectedBatchIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            containerColor = MediaNestColors.Raised,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = MediaNestShapes.Hero,
            title = {
                Text(
                    text = "Delete $count Download${if (count > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Text(
                    text = "Choose whether to remove the selected entries from the list only, or also delete the downloaded files from storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediaNestColors.TextSecondary
                )
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MediaNestButton(
                        text = "List Only",
                        onClick = {
                            val targets = completedDownloads.filter { it.id in selectedBatchIds }
                            targets.forEach { dl -> viewModel.deleteDownload(dl, deleteFile = false) }
                            selectedBatchIds = emptySet()
                            isBatchMode = false
                            showBatchDeleteDialog = false
                        },
                        variant = MediaNestButtonVariant.Secondary,
                        size = MediaNestButtonSize.Small
                    )
                    MediaNestButton(
                        text = "Delete Files & List",
                        onClick = {
                            val targets = completedDownloads.filter { it.id in selectedBatchIds }
                            targets.forEach { dl -> viewModel.deleteDownload(dl, deleteFile = true) }
                            selectedBatchIds = emptySet()
                            isBatchMode = false
                            showBatchDeleteDialog = false
                        },
                        variant = MediaNestButtonVariant.DangerSolid,
                        size = MediaNestButtonSize.Small
                    )
                }
            },
            dismissButton = {
                MediaNestButton(
                    text = "Cancel",
                    onClick = { showBatchDeleteDialog = false },
                    variant = MediaNestButtonVariant.Ghost,
                    size = MediaNestButtonSize.Small
                )
            }
        )
    }

    // Restart Confirmation Dialog
    if (showRestartDialogFor != null) {
        val download = showRestartDialogFor!!
        val effectiveQuality = download.quality.ifEmpty { resolveDownloadResolution(null, defaultResolution) }
        val displayTitle = download.title.ifEmpty { effectiveQuality }
        AlertDialog(
            onDismissRequest = { showRestartDialogFor = null },
            containerColor = MediaNestColors.Raised,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = MediaNestShapes.Hero,
            title = {
                Text(
                    text = "Restart Download",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to restart downloading \"$displayTitle\"? This will start downloading from scratch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediaNestColors.TextSecondary
                )
            },
            confirmButton = {
                MediaNestButton(
                    text = "Restart",
                    onClick = {
                        viewModel.retryDownload(download)
                        showRestartDialogFor = null
                    },
                    variant = MediaNestButtonVariant.Primary,
                    size = MediaNestButtonSize.Small
                )
            },
            dismissButton = {
                MediaNestButton(
                    text = "Cancel",
                    onClick = { showRestartDialogFor = null },
                    variant = MediaNestButtonVariant.Ghost,
                    size = MediaNestButtonSize.Small
                )
            }
        )
    }
}

// -----------------------------------------------------------------------------
// Component: Download Stats Card
// -----------------------------------------------------------------------------

@Composable
private fun DownloadStatsCard(
    downloads: List<DownloadEntity>,
    modifier: Modifier = Modifier
) {
    val totalCount = downloads.size
    val totalBytes = remember(downloads) { downloads.sumOf { it.fileSizeBytes } }
    val completedBytes = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED && it.errorMessage != "file_missing" }
            .sumOf { it.fileSizeBytes }
    }
    val downloadingCount = remember(downloads) { downloads.count { it.status == DownloadStatus.DOWNLOADING } }
    val queuedCount = remember(downloads) { downloads.count { it.status == DownloadStatus.QUEUED } }
    val pausedCount = remember(downloads) { downloads.count { it.status == DownloadStatus.PAUSED } }
    val failedCount = remember(downloads) { downloads.count { it.status == DownloadStatus.FAILED || it.errorMessage == "file_missing" } }
    val completedCount = remember(downloads) { downloads.count { it.status == DownloadStatus.COMPLETED && it.errorMessage != "file_missing" } }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = MediaNestShapes.Card
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Chart icon + Title + Total size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MediaNestColors.Raised),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_chart),
                            contentDescription = null,
                            tint = MediaNestColors.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Storage & Status",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MediaNestColors.TextPrimary
                        )
                    )
                }

                Text(
                    text = "$totalCount items · ${formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MediaNestColors.TextSecondary
                )
            }

            // Status Pills Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (downloadingCount > 0) {
                    StatusStatPill(
                        label = "Downloading",
                        count = downloadingCount,
                        backgroundColor = MediaNestColors.AccentDeep,
                        contentColor = MediaNestColors.TextPrimary
                    )
                }
                if (queuedCount > 0) {
                    StatusStatPill(
                        label = "Queued",
                        count = queuedCount,
                        backgroundColor = MediaNestColors.Raised,
                        contentColor = MediaNestColors.TextSecondary
                    )
                }
                if (pausedCount > 0) {
                    StatusStatPill(
                        label = "Paused",
                        count = pausedCount,
                        backgroundColor = MediaNestColors.Raised,
                        contentColor = MediaNestColors.TextSecondary
                    )
                }
                if (failedCount > 0) {
                    StatusStatPill(
                        label = "Failed",
                        count = failedCount,
                        backgroundColor = MediaNestColors.Destructive.copy(alpha = 0.2f),
                        contentColor = MediaNestColors.Destructive
                    )
                }
                if (completedCount > 0) {
                    StatusStatPill(
                        label = "Completed",
                        count = completedCount,
                        backgroundColor = MediaNestColors.Success.copy(alpha = 0.2f),
                        contentColor = MediaNestColors.Success
                    )
                }
            }

            // Completed storage usage footer
            if (totalBytes > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Completed Storage",
                        style = MaterialTheme.typography.labelSmall,
                        color = MediaNestColors.TextSecondary
                    )
                    Text(
                        text = formatBytes(completedBytes),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MediaNestColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusStatPill(
    label: String,
    count: Int,
    backgroundColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$label · $count",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

// -----------------------------------------------------------------------------
// Component: Active Download Card
// -----------------------------------------------------------------------------

@Composable
private fun ActiveDownloadCard(
    download: DownloadEntity,
    index: Int,
    totalCount: Int,
    onDragMove: (targetIndex: Int) -> Unit,
    videosMap: Map<String, VideoEntity>,
    onVideoClick: (String) -> Unit,
    onDeleteClick: (DownloadEntity) -> Unit,
    onRestartClick: (DownloadEntity) -> Unit,
    viewModel: DownloadsViewModel,
    defaultResolution: String = DownloadPreferences.DEFAULT_RESOLUTION,
    fullTitles: Boolean = LocalFullTitles.current
) {
    var isTitleExpanded by remember(fullTitles) { mutableStateOf(fullTitles) }
    var dragAccumulator by remember { mutableStateOf(0f) }
    val dragThresholdPx = 120f
    val videoEntity = videosMap[download.videoId]
    val durationSeconds = videoEntity?.durationSeconds ?: 0L
    val effectiveQuality = download.quality.ifEmpty { resolveDownloadResolution(null, defaultResolution) }
    val isAudio = download.format.contains("audio")

    val parsedInfo = remember(download) { parseDownloadProgress(download) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MediaNestShapes.Card
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Drag Handle + Thumbnail + Title/Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
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
                                    dragAccumulator -= dragThresholdPx
                                } else if (dragAccumulator < -dragThresholdPx && index > 0) {
                                    onDragMove(index - 1)
                                    dragAccumulator += dragThresholdPx
                                }
                            },
                            onDragStopped = { dragAccumulator = 0f }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_grip),
                        contentDescription = "Reorder Drag Handle",
                        tint = MediaNestColors.TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Clickable Content Area
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onVideoClick(download.videoId) },
                    verticalAlignment = Alignment.Top
                ) {
                    // 16:9 Thumbnail with Badges
                    Box(
                        modifier = Modifier
                            .size(112.dp, 64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MediaNestColors.Raised)
                    ) {
                        AsyncImage(
                            model = download.thumbnailUrl,
                            contentDescription = null,
                            placeholder = ColorPainter(MediaNestColors.ThumbnailPlaceholder),
                            error = ColorPainter(MediaNestColors.ThumbnailPlaceholder),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Format Badge top-left
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MediaNestColors.GlassStrong)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (isAudio) R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                                contentDescription = null,
                                tint = if (isAudio) MediaNestColors.ProgressAudio else MediaNestColors.Accent,
                                modifier = Modifier.size(10.dp)
                            )
                        }

                        // Status Icon Badge top-right
                        val statusIcon = when (download.status) {
                            DownloadStatus.DOWNLOADING -> R.drawable.ic_mn_download
                            DownloadStatus.PAUSED -> R.drawable.ic_mn_pause
                            DownloadStatus.FAILED -> R.drawable.ic_mn_warning
                            DownloadStatus.CANCELED -> R.drawable.ic_mn_close
                            else -> null
                        }
                        if (statusIcon != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(MediaNestColors.GlassStrong)
                                    .padding(3.dp)
                            ) {
                                Icon(
                                    painter = painterResource(statusIcon),
                                    contentDescription = null,
                                    tint = when (download.status) {
                                        DownloadStatus.DOWNLOADING -> MediaNestColors.Accent
                                        DownloadStatus.FAILED -> MediaNestColors.Destructive
                                        else -> MediaNestColors.TextSecondary
                                    },
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }

                        // Duration Badge bottom-right
                        if (durationSeconds > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MediaNestColors.PlayerSurface.copy(alpha = 0.75f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = UiUtils.formatDuration(durationSeconds),
                                    color = MediaNestColors.TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Title & Quality Information
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = download.title.ifEmpty { effectiveQuality },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MediaNestColors.TextPrimary
                            ),
                            maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { isTitleExpanded = !isTitleExpanded }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MediaNestColors.Raised,
                                border = BorderStroke(1.dp, MediaNestColors.Border)
                            ) {
                                Text(
                                    text = effectiveQuality,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MediaNestColors.Accent,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }

                            if (download.errorMessage == "file_missing") {
                                Text(
                                    text = "Source missing",
                                    fontSize = 11.sp,
                                    color = MediaNestColors.Destructive,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Progress Bar & Metadata Row
            DownloadProgressBar(
                progress = download.progress,
                stage = parsedInfo.stage,
                videoProgress = parsedInfo.videoProgress,
                audioProgress = parsedInfo.audioProgress,
                statusText = parsedInfo.statusText,
                percentage = parsedInfo.percentage,
                downloadSpeed = parsedInfo.speedText,
                eta = parsedInfo.etaText,
                elapsed = parsedInfo.elapsedText,
                isIndeterminate = (download.errorMessage == "merging"),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = MediaNestColors.Border
            )

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (download.status) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                        MediaNestButton(
                            text = "Pause",
                            onClick = { viewModel.pauseDownload(download.id) },
                            variant = MediaNestButtonVariant.Secondary,
                            size = MediaNestButtonSize.Small,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_pause),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        MediaNestButton(
                            text = "Cancel",
                            onClick = { onDeleteClick(download) },
                            variant = MediaNestButtonVariant.Danger,
                            size = MediaNestButtonSize.Small,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_close),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }

                    DownloadStatus.PAUSED -> {
                        MediaNestButton(
                            text = "Resume",
                            onClick = { viewModel.resumeDownload(download.id) },
                            variant = MediaNestButtonVariant.Primary,
                            size = MediaNestButtonSize.Small,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_play),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        MediaNestButton(
                            text = "Restart",
                            onClick = { onRestartClick(download) },
                            variant = MediaNestButtonVariant.Secondary,
                            size = MediaNestButtonSize.Small,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_refresh),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        MediaNestButton(
                            text = "Delete",
                            onClick = { onDeleteClick(download) },
                            variant = MediaNestButtonVariant.Danger,
                            size = MediaNestButtonSize.Small,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_trash),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }

                    DownloadStatus.FAILED -> {
                        MediaNestButton(
                            text = "Retry",
                            onClick = { onRestartClick(download) },
                            variant = MediaNestButtonVariant.Primary,
                            size = MediaNestButtonSize.Small,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_refresh),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        MediaNestButton(
                            text = "Delete",
                            onClick = { onDeleteClick(download) },
                            variant = MediaNestButtonVariant.Danger,
                            size = MediaNestButtonSize.Small,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_trash),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }

                    DownloadStatus.CANCELED -> {
                        MediaNestButton(
                            text = "Restart",
                            onClick = { onRestartClick(download) },
                            variant = MediaNestButtonVariant.Primary,
                            size = MediaNestButtonSize.Small,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_refresh),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        MediaNestButton(
                            text = "Delete",
                            onClick = { onDeleteClick(download) },
                            variant = MediaNestButtonVariant.Danger,
                            size = MediaNestButtonSize.Small,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_trash),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }

                    DownloadStatus.COMPLETED -> {
                        if (download.errorMessage == "file_missing") {
                            MediaNestButton(
                                text = "Redownload",
                                onClick = { onRestartClick(download) },
                                variant = MediaNestButtonVariant.Primary,
                                size = MediaNestButtonSize.Small,
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_refresh),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            MediaNestButton(
                                text = "Delete",
                                onClick = { onDeleteClick(download) },
                                variant = MediaNestButtonVariant.Danger,
                                size = MediaNestButtonSize.Small,
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_trash),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component: Completed Download Card
// -----------------------------------------------------------------------------

@Composable
private fun CompletedDownloadCard(
    download: DownloadEntity,
    videosMap: Map<String, VideoEntity>,
    onPlayDownload: (DownloadEntity) -> Unit,
    onVideoClick: (String) -> Unit,
    onDeleteClick: (DownloadEntity) -> Unit,
    hasExtractedAudio: Boolean,
    viewModel: DownloadsViewModel,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: (Long) -> Unit,
    playingVideoId: String?,
    playingUri: String?,
    isPlaying: Boolean,
    defaultResolution: String = DownloadPreferences.DEFAULT_RESOLUTION,
    fullTitles: Boolean = LocalFullTitles.current
) {
    var isTitleExpanded by remember(fullTitles) { mutableStateOf(fullTitles) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackHistory by viewModel.playbackHistory.collectAsStateWithLifecycle()

    val videoEntity = videosMap[download.videoId]
    val durationSeconds = videoEntity?.durationSeconds ?: 0L

    val history = playbackHistory.find { it.videoId == download.videoId }
    val positionMillis = history?.positionMillis ?: 0L
    val progressFraction = if (durationSeconds > 0 && positionMillis > 0) {
        ((positionMillis.toFloat() / 1000f) / durationSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val effectiveQuality = download.quality.ifEmpty { resolveDownloadResolution(null, defaultResolution) }
    val isAudio = download.format.contains("audio")

    val isCurrentPlaying = playingVideoId == download.videoId &&
        (playingUri == null || download.filePath.isEmpty() ||
            playingUri == Uri.fromFile(File(download.filePath)).toString())
    val showPause = isCurrentPlaying && isPlaying

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (isBatchMode) {
            { onToggleSelect(download.id) }
        } else null,
        shape = MediaNestShapes.Card
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Upper Content Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Batch Checkbox
                if (isBatchMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) MediaNestColors.Accent else MediaNestColors.Raised)
                            .border(
                                1.dp,
                                if (isSelected) MediaNestColors.Accent else MediaNestColors.Border,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onToggleSelect(download.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_check),
                                contentDescription = "Selected",
                                tint = MediaNestColors.OnAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }

                // Clickable Content Area
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isBatchMode) { onVideoClick(download.videoId) },
                    verticalAlignment = Alignment.Top
                ) {
                    // 16:9 Thumbnail with Badges
                    Box(
                        modifier = Modifier
                            .size(112.dp, 64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MediaNestColors.Raised)
                    ) {
                        AsyncImage(
                            model = download.thumbnailUrl,
                            contentDescription = null,
                            placeholder = ColorPainter(MediaNestColors.ThumbnailPlaceholder),
                            error = ColorPainter(MediaNestColors.ThumbnailPlaceholder),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Format Badge top-left
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MediaNestColors.GlassStrong)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (isAudio) R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                                contentDescription = null,
                                tint = if (isAudio) MediaNestColors.ProgressAudio else MediaNestColors.Accent,
                                modifier = Modifier.size(10.dp)
                            )
                        }

                        // Completed Checkmark Badge top-right
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(MediaNestColors.GlassStrong)
                                .padding(3.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_check_circle),
                                contentDescription = "Completed",
                                tint = MediaNestColors.Success,
                                modifier = Modifier.size(10.dp)
                            )
                        }

                        // Duration Badge bottom-right
                        if (durationSeconds > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MediaNestColors.PlayerSurface.copy(alpha = 0.75f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = UiUtils.formatDuration(durationSeconds),
                                    color = MediaNestColors.TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Watch Progress Bar
                        if (durationSeconds > 0 && positionMillis > 0) {
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

                    Spacer(Modifier.width(10.dp))

                    // Title & Metadata
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = download.title.ifEmpty { effectiveQuality },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MediaNestColors.TextPrimary
                            ),
                            maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { isTitleExpanded = !isTitleExpanded }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MediaNestColors.Raised,
                                border = BorderStroke(1.dp, MediaNestColors.Border)
                            ) {
                                Text(
                                    text = effectiveQuality,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MediaNestColors.Accent,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }

                            if (download.fileSizeBytes > 0L) {
                                Text(
                                    text = formatBytes(download.fileSizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MediaNestColors.TextSecondary
                                )
                            }
                        }

                        if (positionMillis > 0) {
                            Text(
                                text = "Left off at ${UiUtils.formatDuration(positionMillis / 1000L)}",
                                fontSize = 11.sp,
                                color = MediaNestColors.Accent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = MediaNestColors.Border
            )

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Action Button
                if (download.filePath.isNotEmpty()) {
                    MediaNestButton(
                        text = if (showPause) "Pause" else "Play",
                        onClick = {
                            if (isCurrentPlaying) {
                                viewModel.togglePlayPause()
                            } else {
                                onPlayDownload(download)
                            }
                        },
                        variant = MediaNestButtonVariant.Primary,
                        size = MediaNestButtonSize.Small,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(
                                    if (showPause) R.drawable.ic_mn_pause else R.drawable.ic_mn_play
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Extract Audio Button (for video files without extracted audio)
                    if (!isAudio && !hasExtractedAudio) {
                        val isExtracting = uiState.extractingVideoId == download.videoId
                        MediaNestButton(
                            text = "Extract Audio",
                            onClick = { viewModel.extractAudio(download) },
                            enabled = !isExtracting,
                            variant = MediaNestButtonVariant.Secondary,
                            size = MediaNestButtonSize.Small,
                            leadingIcon = {
                                if (isExtracting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MediaNestColors.Accent
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_music),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }

                    // Delete Button
                    MediaNestIconButton(
                        onClick = { onDeleteClick(download) },
                        size = MediaNestIconButtonSize.Small,
                        tint = MediaNestColors.Destructive
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_trash),
                            contentDescription = "Delete",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
