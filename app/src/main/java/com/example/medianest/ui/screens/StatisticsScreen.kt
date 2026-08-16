package com.example.medianest.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.medianest.ui.components.EndOfListIndicator
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.viewmodel.StatisticsUiState
import com.example.medianest.ui.viewmodel.StatisticsViewModel
import com.example.medianest.ui.viewmodel.TopVideoStat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var topVideosLimit by remember { mutableStateOf(10) }
    var channelsLimit by remember { mutableStateOf(10) }
    var foldersLimit by remember { mutableStateOf(10) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Statistics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MediaNestColors.Accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Banner
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MediaNestColors.AccentDeep),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MediaNestColors.Accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "App Statistics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Library usage at a glance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediaNestColors.TextSecondary
                            )
                        }
                    }
                }

                // 1. Overall Engagement & Library Overview
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatSectionHeader(
                            icon = Icons.Default.BarChart,
                            title = "Overall Engagement",
                            badge = "${uiState.totalTracked} tracked videos"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Tracked Videos",
                                value = uiState.totalTracked.toString(),
                                icon = Icons.Default.VideoLibrary,
                                subtitle = "library items",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Library Ratio",
                                value = "${uiState.videoRatioPct}% / ${uiState.audioRatioPct}%",
                                icon = Icons.Default.Audiotrack,
                                subtitle = "${uiState.videoTrackCount} video · ${uiState.audioTrackCount} audio",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Library Completion",
                                value = "${uiState.completionPct}%",
                                icon = Icons.Default.CheckCircle,
                                subtitle = "${uiState.watchedVideos} of ${uiState.totalTracked} watched",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Total Plays",
                                value = uiState.totalPlayCount.toString(),
                                icon = Icons.Default.PlayArrow,
                                subtitle = "sessions recorded",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Favorites",
                                value = uiState.favorites.toString(),
                                icon = Icons.Default.Favorite,
                                subtitle = "favorited videos",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Subscriptions",
                                value = uiState.subCount.toString(),
                                icon = Icons.Default.Notifications,
                                subtitle = "${uiState.autoDownloadCount} auto-syncing",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. Engagement & Watch Metrics
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatSectionHeader(
                            icon = Icons.Default.History,
                            title = "Engagement & Watch Metrics",
                            badge = "${uiState.sessionsThisWeek} sessions this week"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Total Watch Time",
                                value = formatWatchTime(uiState.totalWatchTimeMillis),
                                icon = Icons.Default.History,
                                subtitle = "cumulative watch time",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Watch This Week",
                                value = formatWatchTime(uiState.weekWatchTimeMillis),
                                icon = Icons.Default.AccessTime,
                                subtitle = "${uiState.sessionsThisWeek} sessions",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Average Session",
                                value = formatWatchTime(uiState.avgSessionMillis),
                                icon = Icons.Default.PlayArrow,
                                subtitle = "per playback session",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Longest Session",
                                value = formatWatchTime(uiState.longestSessionMillis),
                                icon = Icons.Default.Star,
                                subtitle = "single longest session",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. Storage Details
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatSectionHeader(
                            icon = Icons.Default.Storage,
                            title = "Storage Details",
                            badge = "${formatBytes(uiState.totalDownloadBytes)} on disk"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Total Storage",
                                value = formatBytes(uiState.totalDownloadBytes),
                                icon = Icons.Default.Storage,
                                subtitle = "${uiState.totalDownloadsCount} downloads",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Video Storage",
                                value = formatBytes(uiState.videoBytes),
                                icon = Icons.Default.Videocam,
                                subtitle = "video files",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Audio Storage",
                                value = formatBytes(uiState.audioBytes),
                                icon = Icons.Default.Audiotrack,
                                subtitle = "audio files",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Average File Size",
                                value = formatBytes(uiState.avgFileSizeBytes),
                                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                                subtitle = "per download",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        StatCard(
                            title = "Audio Extractions",
                            value = uiState.audioExtractionCount.toString(),
                            icon = Icons.Default.Extension,
                            subtitle = "extracted tracks",
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Storage breakdown progress bars
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val totalBytes = uiState.totalDownloadBytes.coerceAtLeast(1L)
                                StatBarRow(
                                    label = "Video Storage",
                                    count = uiState.videoBytes,
                                    total = totalBytes,
                                    color = MediaNestColors.Accent
                                )
                                StatBarRow(
                                    label = "Audio Storage",
                                    count = uiState.audioBytes,
                                    total = totalBytes,
                                    color = MediaNestColors.Success
                                )
                                Spacer(Modifier.height(4.dp))
                                StatTextRow("Completed on disk", formatBytes(uiState.completedBytes))
                                StatTextRow("Total download footprint", formatBytes(uiState.totalDownloadBytes))
                            }
                        }
                    }
                }

                // 4. Download Health
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatSectionHeader(
                            icon = Icons.Default.CheckCircle,
                            title = "Download Health",
                            badge = "${uiState.downloadSuccessRate}% success rate"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Success Rate",
                                value = "${uiState.downloadSuccessRate}%",
                                icon = Icons.Default.CheckCircle,
                                subtitle = "${uiState.completedDownloads} completed",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Completed",
                                value = uiState.completedDownloads.toString(),
                                icon = Icons.Default.FileDownload,
                                subtitle = formatBytes(uiState.completedBytes),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Active / Queued",
                                value = uiState.activeDownloads.toString(),
                                icon = Icons.Default.Sync,
                                subtitle = "in progress",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Failed / Canceled",
                                value = (uiState.failedDownloads + uiState.canceledDownloads).toString(),
                                icon = Icons.Default.Warning,
                                subtitle = "unsuccessful",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 5. Link Extraction Stats
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatSectionHeader(
                            icon = Icons.Default.Link,
                            title = "Link Extraction",
                            badge = "${uiState.totalExtractedLinks} total extracted links"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Extracted Links",
                                value = uiState.totalExtractedLinks.toString(),
                                icon = Icons.Default.Link,
                                subtitle = "in link history",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Video Links",
                                value = uiState.videoLinksCount.toString(),
                                icon = Icons.Default.PlayCircle,
                                subtitle = "single videos",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Playlist Links",
                                value = uiState.playlistLinksCount.toString(),
                                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                subtitle = "playlists",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Channel Links",
                                value = uiState.channelLinksCount.toString(),
                                icon = Icons.Default.AccountCircle,
                                subtitle = "channels",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 6. Subscriptions & Folders
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatSectionHeader(
                            icon = Icons.Default.Folder,
                            title = "Subscriptions & Folders",
                            badge = "${uiState.subCount} subs · ${uiState.totalFolders} folders"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Subscriptions",
                                value = uiState.subCount.toString(),
                                icon = Icons.Default.Notifications,
                                subtitle = "channels & playlists",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Auto-Downloads",
                                value = uiState.autoDownloadCount.toString(),
                                icon = Icons.Default.Sync,
                                subtitle = "auto-sync enabled",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        StatCard(
                            title = "Total Folders",
                            value = uiState.totalFolders.toString(),
                            icon = Icons.Default.Folder,
                            subtitle = "organized folders",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 7. Top Content (Dynamic list with 10-item batching + EndOfListIndicator)
                item {
                    StatSectionHeader(
                        icon = Icons.Default.Star,
                        title = "Top Content",
                        badge = if (uiState.topVideos.isNotEmpty()) "${uiState.topVideos.size} most played" else null
                    )
                }
                if (uiState.topVideos.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "No plays yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Play some videos to populate your top content.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediaNestColors.TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    val displayedTopVideos = uiState.topVideos.take(topVideosLimit)
                    items(displayedTopVideos, key = { it.id }) { video ->
                        val rank = uiState.topVideos.indexOf(video) + 1
                        TopVideoCard(video = video, rank = rank)
                    }
                    if (uiState.topVideos.size > topVideosLimit) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = { topVideosLimit += 10 },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MediaNestColors.Raised,
                                        contentColor = MediaNestColors.Accent
                                    )
                                ) {
                                    Text("Show more top content (${uiState.topVideos.size - topVideosLimit} remaining)")
                                }
                            }
                        }
                    } else {
                        item {
                            EndOfListIndicator()
                        }
                    }
                }

                // 8. Breakdown by Resolution (inherently small list: ~5 buckets)
                if (uiState.resolutionMap.isNotEmpty()) {
                    item {
                        StatSectionHeader(
                            icon = Icons.Default.HighQuality,
                            title = "By Resolution",
                            badge = "${uiState.resolutionMap.size} qualities"
                        )
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val total = uiState.resolutionMap.values.sum().coerceAtLeast(1)
                                uiState.resolutionMap.entries.sortedByDescending { it.value }.forEach { (quality, count) ->
                                    StatBarRow(
                                        label = quality,
                                        count = count.toLong(),
                                        total = total.toLong(),
                                        color = if (quality.equals("Audio", ignoreCase = true)) MediaNestColors.Success else MediaNestColors.Accent
                                    )
                                }
                            }
                        }
                    }
                }

                // 9. Breakdown by Channel (Dynamic breakdown with 10-item batching + EndOfListIndicator)
                if (uiState.channelMap.isNotEmpty()) {
                    item {
                        StatSectionHeader(
                            icon = Icons.Default.AccountCircle,
                            title = "By Channel",
                            badge = "${uiState.channelMap.size} channels"
                        )
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val sortedChannels = remember(uiState.channelMap) {
                                    uiState.channelMap.entries.sortedByDescending { it.value }
                                }
                                sortedChannels.take(channelsLimit).forEach { (channel, count) ->
                                    StatTextRow(channel, "$count videos")
                                }
                                if (sortedChannels.size > channelsLimit) {
                                    TextButton(
                                        onClick = { channelsLimit += 10 },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text(
                                            "Show more channels (${sortedChannels.size - channelsLimit} remaining)",
                                            color = MediaNestColors.Accent
                                        )
                                    }
                                } else {
                                    EndOfListIndicator()
                                }
                            }
                        }
                    }
                }

                // 10. Breakdown by Folder (Dynamic breakdown with 10-item batching + EndOfListIndicator)
                if (uiState.folderMap.isNotEmpty()) {
                    item {
                        StatSectionHeader(
                            icon = Icons.Default.Folder,
                            title = "By Folder",
                            badge = "${uiState.folderMap.size} folders"
                        )
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val sortedFolders = remember(uiState.folderMap) {
                                    uiState.folderMap.entries.sortedByDescending { it.value }
                                }
                                sortedFolders.take(foldersLimit).forEach { (folderName, count) ->
                                    StatTextRow(folderName, "$count items")
                                }
                                if (sortedFolders.size > foldersLimit) {
                                    TextButton(
                                        onClick = { foldersLimit += 10 },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text(
                                            "Show more folders (${sortedFolders.size - foldersLimit} remaining)",
                                            color = MediaNestColors.Accent
                                        )
                                    }
                                } else {
                                    EndOfListIndicator()
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun TopVideoCard(video: TopVideoStat, rank: Int) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MediaNestColors.Raised)
            ) {
                if (!video.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MediaNestColors.Background.copy(alpha = 0.85f)
                ) {
                    Icon(
                        if (video.mediaType.equals("AUDIO", ignoreCase = true)) Icons.Default.Audiotrack else Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier
                            .size(12.dp)
                            .padding(1.dp),
                        tint = MediaNestColors.Accent
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "#$rank",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MediaNestColors.Accent
                )
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MediaNestColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MediaNestColors.AccentDeep
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MediaNestColors.Accent
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${video.watchCount} plays",
                                style = MaterialTheme.typography.labelSmall,
                                color = MediaNestColors.Accent
                            )
                        }
                    }
                    if (video.totalWatchTimeMillis > 0L) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MediaNestColors.Raised
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MediaNestColors.TextSecondary
                            )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    formatWatchTime(video.totalWatchTimeMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MediaNestColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatSectionHeader(icon: ImageVector, title: String, badge: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MediaNestColors.Accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = MediaNestColors.TextSecondary
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MediaNestColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MediaNestColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MediaNestColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatBarRow(label: String, count: Long, total: Long, color: Color = MediaNestColors.Accent) {
    val pct = if (total > 0) ((count * 100) / total).coerceIn(0, 100).toInt() else 0
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MediaNestColors.TextSecondary
            )
            Text(
                "$pct%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (pct / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MediaNestColors.ProgressTrack
        )
    }
}

@Composable
fun StatTextRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MediaNestColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatWatchTime(millis: Long): String {
    if (millis <= 0L) return "0s"
    val seconds = millis / 1000
    if (seconds < 60) return "${seconds}s"
    val mins = seconds / 60
    val hours = mins / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${mins % 60}m"
        else -> "${mins}m ${seconds % 60}s"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
