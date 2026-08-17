package com.example.medianest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.medianest.R
import com.example.medianest.ui.components.EndOfListIndicator
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.components.MediaNestButton
import com.example.medianest.ui.components.MediaNestButtonSize
import com.example.medianest.ui.components.MediaNestButtonVariant
import com.example.medianest.ui.components.MediaNestTopAppBar
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestShapes
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
            MediaNestTopAppBar(
                title = "App Statistics",
                subtitle = "Library & storage insights",
                onNavigateBack = onBack
            )
        },
        containerColor = MediaNestColors.Background
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MediaNestColors.Background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MediaNestColors.Accent,
                    strokeWidth = 3.dp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // =====================================================================
                // HERO STORAGE & OVERVIEW METER BANNER
                // =====================================================================
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MediaNestColors.AccentDeep),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_chart),
                                            contentDescription = null,
                                            tint = MediaNestColors.Accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "Overview & Storage",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                letterSpacing = (-0.2).sp
                                            ),
                                            color = MediaNestColors.TextPrimary
                                        )
                                        Text(
                                            "Total Disk Footprint",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MediaNestColors.TextSecondary
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MediaNestColors.Raised,
                                    border = BorderStroke(1.dp, MediaNestColors.Border)
                                ) {
                                    Text(
                                        text = formatBytes(uiState.totalDownloadBytes),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = MediaNestColors.Accent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Storage Multi-segment Meter Bar
                            StorageBreakdownBar(
                                videoBytes = uiState.videoBytes,
                                audioBytes = uiState.audioBytes,
                                totalBytes = uiState.totalDownloadBytes
                            )

                            // Storage Legend Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StorageLegendItem(
                                    color = MediaNestColors.Accent,
                                    label = "Video",
                                    value = formatBytes(uiState.videoBytes)
                                )
                                StorageLegendItem(
                                    color = MediaNestColors.ProgressAudio,
                                    label = "Audio",
                                    value = formatBytes(uiState.audioBytes)
                                )
                                StorageLegendItem(
                                    color = MediaNestColors.Success,
                                    label = "On Disk",
                                    value = formatBytes(uiState.completedBytes)
                                )
                            }
                        }
                    }
                }

                // =====================================================================
                // 1. OVERALL ENGAGEMENT & LIBRARY OVERVIEW
                // =====================================================================
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatSectionHeader(
                            iconRes = R.drawable.ic_mn_video,
                            title = "Library Overview",
                            badge = "${uiState.totalTracked} tracked items"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Tracked Media",
                                value = uiState.totalTracked.toString(),
                                iconRes = R.drawable.ic_mn_video,
                                subtitle = "library items",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Library Ratio",
                                value = "${uiState.videoRatioPct}% / ${uiState.audioRatioPct}%",
                                iconRes = R.drawable.ic_mn_music,
                                subtitle = "${uiState.videoTrackCount} video • ${uiState.audioTrackCount} audio",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Completion",
                                value = "${uiState.completionPct}%",
                                iconRes = R.drawable.ic_mn_check_circle,
                                subtitle = "${uiState.watchedVideos} of ${uiState.totalTracked} watched",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Total Plays",
                                value = uiState.totalPlayCount.toString(),
                                iconRes = R.drawable.ic_mn_play,
                                subtitle = "playback sessions",
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
                                iconRes = R.drawable.ic_mn_heart,
                                subtitle = "favorited items",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Subscriptions",
                                value = uiState.subCount.toString(),
                                iconRes = R.drawable.ic_mn_bell,
                                subtitle = "${uiState.autoDownloadCount} auto-syncing",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // =====================================================================
                // 2. ENGAGEMENT & WATCH METRICS
                // =====================================================================
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatSectionHeader(
                            iconRes = R.drawable.ic_mn_history,
                            title = "Watch Metrics",
                            badge = "${uiState.sessionsThisWeek} sessions this week"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Total Watch Time",
                                value = formatWatchTime(uiState.totalWatchTimeMillis),
                                iconRes = R.drawable.ic_mn_history,
                                subtitle = "cumulative watch time",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Watch This Week",
                                value = formatWatchTime(uiState.weekWatchTimeMillis),
                                iconRes = R.drawable.ic_mn_speed,
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
                                iconRes = R.drawable.ic_mn_play,
                                subtitle = "per playback session",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Longest Session",
                                value = formatWatchTime(uiState.longestSessionMillis),
                                iconRes = R.drawable.ic_mn_star,
                                subtitle = "single longest session",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // =====================================================================
                // 3. STORAGE DETAILS & BREAKDOWN
                // =====================================================================
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatSectionHeader(
                            iconRes = R.drawable.ic_mn_download,
                            title = "Storage Details",
                            badge = "${formatBytes(uiState.totalDownloadBytes)} on disk"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Total Downloads",
                                value = uiState.totalDownloadsCount.toString(),
                                iconRes = R.drawable.ic_mn_download,
                                subtitle = formatBytes(uiState.totalDownloadBytes),
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Average File Size",
                                value = formatBytes(uiState.avgFileSizeBytes),
                                iconRes = R.drawable.ic_mn_file,
                                subtitle = "per download",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Video Storage",
                                value = formatBytes(uiState.videoBytes),
                                iconRes = R.drawable.ic_mn_video,
                                subtitle = "video files",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Audio Storage",
                                value = formatBytes(uiState.audioBytes),
                                iconRes = R.drawable.ic_mn_music,
                                subtitle = "audio files",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        StatCard(
                            title = "Audio Extractions",
                            value = uiState.audioExtractionCount.toString(),
                            iconRes = R.drawable.ic_mn_extract,
                            subtitle = "extracted audio tracks",
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Storage breakdown progress bars card
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
                                    color = MediaNestColors.ProgressAudio
                                )
                                HorizontalDivider(color = MediaNestColors.Border, thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
                                StatTextRow("Completed on disk", formatBytes(uiState.completedBytes))
                                StatTextRow("Total download footprint", formatBytes(uiState.totalDownloadBytes))
                            }
                        }
                    }
                }

                // =====================================================================
                // 4. DOWNLOAD HEALTH
                // =====================================================================
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatSectionHeader(
                            iconRes = R.drawable.ic_mn_check_circle,
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
                                iconRes = R.drawable.ic_mn_check_circle,
                                subtitle = "${uiState.completedDownloads} completed",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Completed",
                                value = uiState.completedDownloads.toString(),
                                iconRes = R.drawable.ic_mn_download_done,
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
                                iconRes = R.drawable.ic_mn_refresh,
                                subtitle = "in progress",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Failed / Canceled",
                                value = (uiState.failedDownloads + uiState.canceledDownloads).toString(),
                                iconRes = R.drawable.ic_mn_warning,
                                subtitle = "unsuccessful",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // =====================================================================
                // 5. LINK EXTRACTION & FOLDERS
                // =====================================================================
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatSectionHeader(
                            iconRes = R.drawable.ic_mn_playlist,
                            title = "Link Extraction & Folders",
                            badge = "${uiState.totalExtractedLinks} extracted links"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Extracted Links",
                                value = uiState.totalExtractedLinks.toString(),
                                iconRes = R.drawable.ic_mn_playlist,
                                subtitle = "in link history",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Single Videos",
                                value = uiState.videoLinksCount.toString(),
                                iconRes = R.drawable.ic_mn_video,
                                subtitle = "video URLs",
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
                                iconRes = R.drawable.ic_mn_playlist,
                                subtitle = "playlist URLs",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Channel Links",
                                value = uiState.channelLinksCount.toString(),
                                iconRes = R.drawable.ic_mn_channel,
                                subtitle = "channel URLs",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        StatCard(
                            title = "Total Folders",
                            value = uiState.totalFolders.toString(),
                            iconRes = R.drawable.ic_mn_folder,
                            subtitle = "organized media folders",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // =====================================================================
                // 6. TOP CONTENT
                // =====================================================================
                item {
                    StatSectionHeader(
                        iconRes = R.drawable.ic_mn_star,
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
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MediaNestColors.Raised),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_star),
                                        contentDescription = null,
                                        tint = MediaNestColors.Accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "No playback history yet",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MediaNestColors.TextPrimary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Play some videos to populate your top content analytics.",
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
                                MediaNestButton(
                                    text = "Show more top content (${uiState.topVideos.size - topVideosLimit} remaining)",
                                    onClick = { topVideosLimit += 10 },
                                    variant = MediaNestButtonVariant.Deep,
                                    size = MediaNestButtonSize.Small
                                )
                            }
                        }
                    } else {
                        item {
                            EndOfListIndicator()
                        }
                    }
                }

                // =====================================================================
                // 7. BREAKDOWN BY RESOLUTION
                // =====================================================================
                if (uiState.resolutionMap.isNotEmpty()) {
                    item {
                        StatSectionHeader(
                            iconRes = R.drawable.ic_mn_sliders,
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
                                        color = if (quality.equals("Audio", ignoreCase = true)) MediaNestColors.ProgressAudio else MediaNestColors.Accent
                                    )
                                }
                            }
                        }
                    }
                }

                // =====================================================================
                // 8. BREAKDOWN BY CHANNEL
                // =====================================================================
                if (uiState.channelMap.isNotEmpty()) {
                    item {
                        StatSectionHeader(
                            iconRes = R.drawable.ic_mn_channel,
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
                                    MediaNestButton(
                                        text = "Show more channels (${sortedChannels.size - channelsLimit} remaining)",
                                        onClick = { channelsLimit += 10 },
                                        variant = MediaNestButtonVariant.Ghost,
                                        size = MediaNestButtonSize.Small,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                } else {
                                    EndOfListIndicator()
                                }
                            }
                        }
                    }
                }

                // =====================================================================
                // 9. BREAKDOWN BY FOLDER
                // =====================================================================
                if (uiState.folderMap.isNotEmpty()) {
                    item {
                        StatSectionHeader(
                            iconRes = R.drawable.ic_mn_folder,
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
                                    MediaNestButton(
                                        text = "Show more folders (${sortedFolders.size - foldersLimit} remaining)",
                                        onClick = { foldersLimit += 10 },
                                        variant = MediaNestButtonVariant.Ghost,
                                        size = MediaNestButtonSize.Small,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
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

// =============================================================================
// Helper Composables & Meters
// =============================================================================

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
                        painter = painterResource(
                            if (video.mediaType.equals("AUDIO", ignoreCase = true)) R.drawable.ic_mn_music else R.drawable.ic_mn_video
                        ),
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
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MediaNestColors.Accent
                )
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = MediaNestColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    video.channelName,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MediaNestColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MediaNestColors.AccentDeep
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_eye),
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MediaNestColors.Accent
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${video.watchCount} plays",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MediaNestColors.Accent
                            )
                        }
                    }
                    if (video.totalWatchTimeMillis > 0L) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MediaNestColors.Raised,
                            border = BorderStroke(1.dp, MediaNestColors.Border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_history),
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MediaNestColors.TextSecondary
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    formatWatchTime(video.totalWatchTimeMillis),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
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
fun StatSectionHeader(
    iconRes: Int,
    title: String,
    badge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MediaNestColors.Raised),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MediaNestColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = MediaNestColors.TextPrimary
            )
        }
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MediaNestColors.Raised,
                border = BorderStroke(1.dp, MediaNestColors.Border)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MediaNestColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    iconRes: Int,
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
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MediaNestColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MediaNestColors.Raised),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MediaNestColors.Accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = MediaNestColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = MediaNestColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatBarRow(
    label: String,
    count: Long,
    total: Long,
    color: Color = MediaNestColors.Accent
) {
    val pct = if (total > 0) ((count * 100) / total).coerceIn(0, 100).toInt() else 0
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MediaNestColors.TextSecondary
            )
            Text(
                "$pct%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MediaNestColors.TextPrimary
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
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MediaNestColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = MediaNestColors.TextPrimary
        )
    }
}

@Composable
private fun StorageBreakdownBar(
    videoBytes: Long,
    audioBytes: Long,
    totalBytes: Long
) {
    val effectiveTotal = totalBytes.coerceAtLeast(1L).toFloat()
    val videoWeight = (videoBytes.toFloat() / effectiveTotal).coerceIn(0f, 1f)
    val audioWeight = (audioBytes.toFloat() / effectiveTotal).coerceIn(0f, 1f)
    val remainingWeight = (1f - videoWeight - audioWeight).coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MediaNestColors.ProgressTrack)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (videoWeight > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(videoWeight)
                        .background(MediaNestColors.Accent)
                )
            }
            if (audioWeight > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(audioWeight)
                        .background(MediaNestColors.ProgressAudio)
                )
            }
            if (remainingWeight > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(remainingWeight)
                        .background(Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun StorageLegendItem(
    color: Color,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = MediaNestColors.TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = MediaNestColors.TextPrimary
            )
        }
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
