package com.example.medianest.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil.compose.SubcomposeAsyncImage
import com.example.medianest.R
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.ui.components.EndOfListIndicator
import com.example.medianest.ui.components.MediaNestButton
import com.example.medianest.ui.components.MediaNestButtonSize
import com.example.medianest.ui.components.MediaNestButtonVariant
import com.example.medianest.ui.components.MediaNestSnackbarHost
import com.example.medianest.ui.components.MediaNestTopAppBar
import com.example.medianest.ui.components.WatchCountDialog
import com.example.medianest.ui.components.YoutubeSubscribeButton
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestShapes
import com.example.medianest.ui.utils.UiUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailScreen(
    videoInfo: ExtractedVideoInfo,
    localVideo: VideoEntity? = null,
    downloads: List<DownloadEntity> = emptyList(),
    onPlay: (StreamSource) -> Unit,
    onPlayDownload: (DownloadEntity) -> Unit = {},
    onDeleteDownload: (DownloadEntity, Boolean) -> Unit = { _, _ -> },
    onDownload: (StreamSource) -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    isFavorite: Boolean = false,
    onSubscribe: () -> Unit = {},
    isSubscribed: Boolean = false,
    videoHistory: com.example.medianest.data.local.entity.HistoryEntity? = null,
    watchSessions: List<com.example.medianest.data.local.entity.WatchSessionEntity> = emptyList(),
    isFetchingOnline: Boolean = false,
    onRefresh: () -> Unit = {},
    onResetWatchPosition: () -> Unit = {},
    onMarkWatched: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showWatchCountDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showDeleteDownloadDialogFor by remember { mutableStateOf<DownloadEntity?>(null) }
    val completedDownloads = remember(downloads) { downloads.filter { it.status == DownloadStatus.COMPLETED } }
    var downloadedLimit by remember { mutableStateOf(10) }
    var watchSessionsLimit by remember { mutableStateOf(10) }
    var streamsLimit by remember { mutableStateOf(10) }

    val videoStreams = remember(videoInfo.streamSources) {
        videoInfo.streamSources.filter { it.format == "video" || it.format == "video_only" }
    }
    val audioStreams = remember(videoInfo.streamSources) {
        videoInfo.streamSources.filter { it.format == "audio" }
    }

    val groupedVideos = remember(videoStreams) { videoStreams.groupBy { it.quality } }
    val sortedResolutions = remember(groupedVideos) {
        groupedVideos.keys.sortedByDescending {
            it.replace("p", "").toIntOrNull() ?: 0
        }
    }

    val bestAudioStream = remember(audioStreams) {
        audioStreams.maxByOrNull {
            it.quality.replace("kbps", "").toIntOrNull() ?: 0
        }
    }

    val isAudio = remember(localVideo, videoStreams, audioStreams) {
        localVideo?.mediaType?.equals("AUDIO", ignoreCase = true) == true || (videoStreams.isEmpty() && audioStreams.isNotEmpty())
    }

    Scaffold(
        snackbarHost = { MediaNestSnackbarHost(snackbarHostState) },
        containerColor = MediaNestColors.Background,
        contentColor = MediaNestColors.TextPrimary,
        topBar = {
            Column {
                MediaNestTopAppBar(
                    title = "Details",
                    onNavigateBack = onBack,
                    actions = {
                        IconToggleButton(
                            checked = isFavorite,
                            onCheckedChange = {
                                onToggleFavorite()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(if (isFavorite) "Removed from favorites" else "Added to favorites")
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(if (isFavorite) R.drawable.ic_mn_heart_filled else R.drawable.ic_mn_heart),
                                contentDescription = "Favorite",
                                tint = if (isFavorite) MediaNestColors.Accent else MediaNestColors.TextSecondary
                            )
                        }
                    }
                )
                if (isFetchingOnline) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MediaNestColors.Accent,
                        trackColor = MediaNestColors.Raised
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PullToRefreshBox(
                isRefreshing = isFetchingOnline,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .animateContentSize()
                ) {
                    // Media Stage (16:9 Thumbnail Hero)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MediaNestColors.PlayerSurface)
                        ) {
                            SubcomposeAsyncImage(
                                model = videoInfo.thumbnailUrl,
                                contentDescription = videoInfo.title,
                                loading = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = MediaNestColors.Accent,
                                            strokeWidth = 3.dp
                                        )
                                    }
                                },
                                error = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MediaNestColors.ThumbnailPlaceholder)
                                    )
                                },
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Media type badge (ic_mn_video / ic_mn_music) at Top-Start
                            Surface(
                                color = if (isAudio) MediaNestColors.AccentDeep.copy(alpha = 0.85f) else MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
                                contentColor = MediaNestColors.TextPrimary,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(if (isAudio) R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                                        contentDescription = if (isAudio) "Audio" else "Video",
                                        tint = MediaNestColors.TextPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Personal Watch Count badge at Top-End
                            val localWatches = localVideo?.watchCount ?: 0
                            if (localWatches > 0) {
                                Surface(
                                    color = MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
                                    contentColor = MediaNestColors.TextPrimary,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_eye),
                                            contentDescription = null,
                                            tint = MediaNestColors.Accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "$localWatches",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MediaNestColors.TextPrimary
                                        )
                                    }
                                }
                            }

                            // Play overlay button in center
                            IconButton(
                                onClick = {
                                    val completedVideoDownloads = downloads.filter { it.status == DownloadStatus.COMPLETED && it.format != "audio" && it.format != "audio_extracted" }
                                    if (completedVideoDownloads.isNotEmpty()) {
                                        val highestDownloaded = completedVideoDownloads.maxByOrNull { download ->
                                            download.quality.substringBefore("p").toIntOrNull() ?: 0
                                        }
                                        if (highestDownloaded != null) {
                                            onPlayDownload(highestDownloaded)
                                            return@IconButton
                                        }
                                    }
                                    val completedAudioDownloads = downloads.filter { it.status == DownloadStatus.COMPLETED && (it.format == "audio" || it.format == "audio_extracted") }
                                    if (isAudio && completedAudioDownloads.isNotEmpty()) {
                                        onPlayDownload(completedAudioDownloads.first())
                                        return@IconButton
                                    }
                                    val vStreams = videoInfo.streamSources.filter { it.format == "video" || it.format == "video_only" }
                                    val targetStream = vStreams.find { it.format == "video" && it.quality.startsWith("360p") }
                                        ?: vStreams.find { it.format == "video" }
                                        ?: vStreams.maxByOrNull { it.quality.substringBefore("p").toIntOrNull() ?: 0 }
                                        ?: bestAudioStream
                                        ?: videoInfo.streamSources.firstOrNull()
                                    if (targetStream != null) {
                                        onPlay(targetStream)
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("No playable streams available")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(56.dp)
                                    .background(MediaNestColors.PlayerSurface.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_play),
                                    contentDescription = "Play Video",
                                    tint = MediaNestColors.TextPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Playback progress (watched position at Bottom-Start)
                            val positionMillis = videoHistory?.positionMillis ?: 0L
                            if (positionMillis > 0) {
                                val watchedSeconds = positionMillis / 1000L
                                Surface(
                                    color = MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
                                    contentColor = MediaNestColors.TextPrimary,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_history),
                                            contentDescription = null,
                                            tint = MediaNestColors.Accent,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = UiUtils.formatDuration(watchedSeconds),
                                            color = MediaNestColors.TextPrimary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }

                            // Duration at Bottom-End
                            if (videoInfo.durationSeconds > 0) {
                                Surface(
                                    color = MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
                                    contentColor = MediaNestColors.TextPrimary,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = UiUtils.formatDuration(videoInfo.durationSeconds),
                                        color = MediaNestColors.TextPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Red progress strip at the absolute bottom
                            if (videoInfo.durationSeconds > 0 && positionMillis > 0) {
                                val progress = (positionMillis.toFloat() / 1000f) / videoInfo.durationSeconds.toFloat()
                                val coercedProgress = progress.coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(MediaNestColors.ProgressTrack)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(coercedProgress)
                                            .height(3.dp)
                                            .background(MediaNestColors.YouTubeRed)
                                    )
                                }
                            }
                        }
                    }

                    // Title & Media Type Badge / Resolution Pill
                    item {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = videoInfo.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MediaNestColors.TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        // Media Type Badge & Resolution Pill Row
                        val topResolution = if (isAudio) "Audio" else (sortedResolutions.firstOrNull() ?: if (videoInfo.durationSeconds > 0) "HD" else "Auto")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Media Type Pill
                            Surface(
                                color = if (isAudio) MediaNestColors.AccentDeep.copy(alpha = 0.25f) else MediaNestColors.Raised,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isAudio) MediaNestColors.AccentDeep else MediaNestColors.Border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(if (isAudio) R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                                        contentDescription = null,
                                        tint = MediaNestColors.Accent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isAudio) "Audio" else "Video",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MediaNestColors.TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Resolution Pill
                            Surface(
                                color = MediaNestColors.Raised,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MediaNestColors.Border)
                            ) {
                                Text(
                                    text = topResolution,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MediaNestColors.Accent,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // Channel Info Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = videoInfo.channelName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MediaNestColors.TextSecondary
                                ),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            YoutubeSubscribeButton(
                                isSubscribed = isSubscribed,
                                onClick = {
                                    onSubscribe()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (isSubscribed) "Unsubscribed from ${videoInfo.channelName}"
                                            else "Subscribed to ${videoInfo.channelName}"
                                        )
                                    }
                                }
                            )
                        }
                    }

                    // Released and Metadata
                    item {
                        val formattedReleaseDate = UiUtils.formatAbsoluteReleaseDate(videoInfo.uploadDate)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            if (!formattedReleaseDate.isNullOrEmpty()) {
                                Text(
                                    text = "Released: $formattedReleaseDate",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MediaNestColors.TextSecondary
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            val publicViews = videoInfo.viewCount
                            val formattedViews = if (publicViews > 0) {
                                java.text.NumberFormat.getInstance(java.util.Locale.forLanguageTag("en-IN")).format(publicViews) + " views"
                            } else {
                                "—"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_eye),
                                    contentDescription = "Public views",
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Public views: $formattedViews",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MediaNestColors.TextSecondary
                                )
                            }
                        }
                    }

                    // Downloaded Versions List (Card with size, bitrate, Play, Delete)
                    if (completedDownloads.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MediaNestColors.Card
                                ),
                                border = BorderStroke(1.dp, MediaNestColors.Border),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_check_circle),
                                            contentDescription = null,
                                            tint = MediaNestColors.Success,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Downloaded versions (${completedDownloads.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MediaNestColors.Success,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    completedDownloads.take(downloadedLimit).forEachIndexed { index, cdl ->
                                        if (index > 0) {
                                            HorizontalDivider(
                                                color = MediaNestColors.Border.copy(alpha = 0.5f),
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                        val isAudioDl = cdl.format == "audio" || cdl.format == "audio_extracted"
                                        val qLabel = if (isAudioDl) "Audio · ${cdl.quality}" else cdl.quality
                                        val fileSizeStr = Formatter.formatShortFileSize(context, cdl.fileSizeBytes)
                                        val bitrateStr = when {
                                            cdl.quality.contains("kbps", ignoreCase = true) -> cdl.quality.substringAfter("·").trim()
                                            videoInfo.durationSeconds > 0 && cdl.fileSizeBytes > 0 -> {
                                                val bps = (cdl.fileSizeBytes * 8L) / videoInfo.durationSeconds
                                                if (bps >= 1_000_000) "%.1f Mbps".format(bps / 1_000_000f) else "${bps / 1000} kbps"
                                            }
                                            else -> null
                                        }
                                        val downloadDate = if (cdl.downloadedAt > 0) cdl.downloadedAt else (localVideo?.downloadedAt ?: 0L)
                                        val dateStr = if (downloadDate > 0) UiUtils.formatAbsoluteDate(downloadDate) else null

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(MediaNestColors.Raised, RoundedCornerShape(8.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter = painterResource(if (isAudioDl) R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                                                        contentDescription = null,
                                                        tint = MediaNestColors.Accent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = qLabel,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MediaNestColors.TextPrimary
                                                    )
                                                    val metaParts = listOfNotNull(
                                                        fileSizeStr,
                                                        bitrateStr,
                                                        dateStr
                                                    )
                                                    Text(
                                                        text = metaParts.joinToString(" • "),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MediaNestColors.TextSecondary
                                                    )
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = { onPlayDownload(cdl) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_mn_play),
                                                        contentDescription = "Play",
                                                        tint = MediaNestColors.Accent,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        showDeleteDownloadDialogFor = cdl
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_mn_trash),
                                                        contentDescription = "Delete",
                                                        tint = MediaNestColors.Destructive,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (completedDownloads.size > downloadedLimit) {
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(
                                            onClick = { downloadedLimit += 10 },
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        ) {
                                            Text(
                                                "Show more (${completedDownloads.size - downloadedLimit} remaining)",
                                                color = MediaNestColors.Accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons (YouTube & Set Watched)
                    item {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${videoInfo.videoId}"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Unable to open YouTube link")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MediaNestColors.AccentDeep,
                                    contentColor = MediaNestColors.TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_youtube),
                                        contentDescription = "YouTube Logo",
                                        tint = MediaNestColors.TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "YouTube",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MediaNestColors.TextPrimary
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    showWatchCountDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MediaNestColors.Raised,
                                    contentColor = MediaNestColors.TextPrimary
                                ),
                                border = BorderStroke(1.dp, MediaNestColors.Border),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_eye),
                                        contentDescription = "Set as Watched",
                                        tint = MediaNestColors.Accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Set Watched",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MediaNestColors.TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Collapsible Description Container
                    if (!videoInfo.description.isNullOrBlank()) {
                        item {
                            Spacer(Modifier.height(14.dp))
                            var isDescriptionExpanded by remember { mutableStateOf(false) }
                            val textColor = MediaNestColors.TextSecondary.toArgb()
                            val linkColor = MediaNestColors.Accent.toArgb()
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MediaNestColors.Card
                                ),
                                border = BorderStroke(1.dp, MediaNestColors.Border),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Description",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MediaNestColors.TextPrimary
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    AndroidView(
                                        factory = { ctx ->
                                            TextView(ctx).apply {
                                                movementMethod = LinkMovementMethod.getInstance()
                                                ellipsize = android.text.TextUtils.TruncateAt.END
                                            }
                                        },
                                        update = { textView ->
                                            textView.setTextColor(textColor)
                                            textView.setLinkTextColor(linkColor)
                                            textView.textSize = 13.5f
                                            textView.maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3
                                            textView.text = HtmlCompat.fromHtml(
                                                videoInfo.description ?: "",
                                                HtmlCompat.FROM_HTML_MODE_LEGACY
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (isDescriptionExpanded) "Show less" else "Show more",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                            color = MediaNestColors.Accent
                                        )
                                        Icon(
                                            painter = painterResource(if (isDescriptionExpanded) R.drawable.ic_mn_chevron_up else R.drawable.ic_mn_chevron_down),
                                            contentDescription = null,
                                            tint = MediaNestColors.Accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Your Statistics Card
                    if (videoHistory != null || watchSessions.isNotEmpty() || (localVideo?.watchCount ?: 0) > 0) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Your Statistics",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MediaNestColors.TextPrimary
                                )
                            )
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MediaNestColors.Card
                                ),
                                border = BorderStroke(1.dp, MediaNestColors.Border),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    val totalTimeStr = formatWatchTime(videoHistory?.totalWatchTimeMillis ?: 0L)
                                    val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault())

                                    // Watch Count Row (Personal)
                                    val currentWatchCount = localVideo?.watchCount ?: 0
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_eye),
                                            contentDescription = null,
                                            tint = MediaNestColors.Accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Your watch count: ",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MediaNestColors.TextSecondary
                                        )
                                        Text(
                                            "$currentWatchCount ${if (currentWatchCount == 1) "view" else "views"}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MediaNestColors.TextPrimary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))

                                    // Last Watch Position Row
                                    if (videoHistory != null && videoHistory.positionMillis > 0) {
                                        val positionDuration = UiUtils.formatDuration(videoHistory.positionMillis / 1000L)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_history),
                                                contentDescription = null,
                                                tint = MediaNestColors.Accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Last watch position: ",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MediaNestColors.TextSecondary
                                            )
                                            Text(
                                                text = positionDuration,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MediaNestColors.TextPrimary
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                    }

                                    // Total Watch Time Row
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_play),
                                            contentDescription = null,
                                            tint = MediaNestColors.Accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Total Watch Time: ",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MediaNestColors.TextSecondary
                                        )
                                        Text(
                                            totalTimeStr,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MediaNestColors.TextPrimary
                                        )
                                    }

                                    // Last Watched Row
                                    videoHistory?.playedAt?.let { playedAt ->
                                        Spacer(Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_check),
                                                contentDescription = null,
                                                tint = MediaNestColors.Accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Last Watched: ",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MediaNestColors.TextSecondary
                                            )
                                            Text(
                                                dateFormat.format(java.util.Date(playedAt)),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MediaNestColors.TextPrimary
                                            )
                                        }
                                    }

                                    // Average Session and Play Count Row
                                    val sessionCount = if (watchSessions.isNotEmpty()) watchSessions.size else 1
                                    val averageSessionTime = (videoHistory?.totalWatchTimeMillis ?: 0L) / sessionCount
                                    if (averageSessionTime > 0L) {
                                        val averageTimeStr = formatWatchTime(averageSessionTime)
                                        Spacer(Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_speed),
                                                contentDescription = null,
                                                tint = MediaNestColors.Accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Average Session: ",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MediaNestColors.TextSecondary
                                            )
                                            Text(
                                                averageTimeStr,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MediaNestColors.TextPrimary
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_chart),
                                            contentDescription = null,
                                            tint = MediaNestColors.Accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Play Count: ",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MediaNestColors.TextSecondary
                                        )
                                        Text(
                                            "$sessionCount ${if (sessionCount == 1) "play" else "plays"}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = MediaNestColors.TextPrimary
                                        )
                                    }

                                    // First Played At & History Log (Dynamic list with 10-item batching + EndOfListIndicator)
                                    if (watchSessions.isNotEmpty()) {
                                        val oldestSession = watchSessions.minByOrNull { it.watchedAt }
                                        oldestSession?.watchedAt?.let { firstWatched ->
                                            Spacer(Modifier.height(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_mn_check_circle),
                                                    contentDescription = null,
                                                    tint = MediaNestColors.Success,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "First Watched: ",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MediaNestColors.TextSecondary
                                                )
                                                Text(
                                                    dateFormat.format(java.util.Date(firstWatched)),
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                    color = MediaNestColors.TextPrimary
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(12.dp))
                                        HorizontalDivider(color = MediaNestColors.Border)
                                        Spacer(Modifier.height(8.dp))

                                        var expanded by remember { mutableStateOf(false) }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clickable { expanded = !expanded }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                "View Watch History Log (${watchSessions.size})",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MediaNestColors.Accent
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                painter = painterResource(if (expanded) R.drawable.ic_mn_chevron_up else R.drawable.ic_mn_chevron_down),
                                                contentDescription = null,
                                                tint = MediaNestColors.Accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (expanded) {
                                            Spacer(Modifier.height(4.dp))
                                            val sortedSessions = remember(watchSessions) { watchSessions.sortedByDescending { it.watchedAt } }
                                            sortedSessions.take(watchSessionsLimit).forEach { session ->
                                                val dateStr = dateFormat.format(java.util.Date(session.watchedAt))
                                                Text(
                                                    "• Watched on: $dateStr",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MediaNestColors.TextSecondary
                                                )
                                            }
                                            if (sortedSessions.size > watchSessionsLimit) {
                                                TextButton(
                                                    onClick = { watchSessionsLimit += 10 },
                                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                                ) {
                                                    Text(
                                                        "Show more (${sortedSessions.size - watchSessionsLimit} remaining)",
                                                        color = MediaNestColors.Accent
                                                    )
                                                }
                                            } else {
                                                EndOfListIndicator()
                                            }
                                        }
                                    }

                                    if (videoHistory != null && videoHistory.positionMillis > 0) {
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = { showResetConfirm = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MediaNestColors.Raised,
                                                contentColor = MediaNestColors.TextPrimary
                                            ),
                                            border = BorderStroke(1.dp, MediaNestColors.Border),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Reset Last Watch Position")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Available Streams Ladder (Dynamic list with 10-item batching + EndOfListIndicator)
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Available streams:",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MediaNestColors.TextPrimary
                            )
                        )
                    }

                    val bestAudioLength = bestAudioStream?.contentLength
                    val totalStreams = videoStreams.size + audioStreams.size
                    var renderedStreamsCount = 0

                    if (sortedResolutions.isNotEmpty()) {
                        for (resolution in sortedResolutions) {
                            val streamsInResolution = groupedVideos[resolution] ?: emptyList()
                            val availableToRender = (streamsLimit - renderedStreamsCount).coerceAtLeast(0)
                            if (availableToRender > 0) {
                                val toRender = streamsInResolution.take(availableToRender)
                                item(key = "res_header_$resolution") {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = resolution,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MediaNestColors.TextPrimary
                                        )
                                    )
                                }
                                items(toRender, key = { "stream_${it.format}_${it.quality}_${it.codec}_${it.url.hashCode()}" }) { stream ->
                                    StreamQualityRow(
                                        stream = stream,
                                        downloads = downloads,
                                        bestAudioLength = bestAudioLength,
                                        onPlay = onPlay,
                                        onDownload = onDownload,
                                        onShowSnackbar = { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } }
                                    )
                                }
                                renderedStreamsCount += toRender.size
                            }
                        }
                    }

                    val remainingForAudio = (streamsLimit - renderedStreamsCount).coerceAtLeast(0)
                    if (audioStreams.isNotEmpty() && remainingForAudio > 0) {
                        val sortedAudios = audioStreams.sortedByDescending {
                            it.quality.replace("kbps", "").toIntOrNull() ?: 0
                        }
                        val toRenderAudio = sortedAudios.take(remainingForAudio)
                        item(key = "audio_header") {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Audio Only",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MediaNestColors.TextPrimary
                                )
                            )
                        }
                        items(toRenderAudio, key = { "audio_${it.quality}_${it.codec}_${it.url.hashCode()}" }) { stream ->
                            StreamQualityRow(
                                stream = stream,
                                downloads = downloads,
                                bestAudioLength = null,
                                onPlay = onPlay,
                                onDownload = onDownload,
                                onShowSnackbar = { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }
                        renderedStreamsCount += toRenderAudio.size
                    }

                    if (totalStreams > streamsLimit) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = { streamsLimit += 10 },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MediaNestColors.Raised,
                                        contentColor = MediaNestColors.TextPrimary
                                    ),
                                    border = BorderStroke(1.dp, MediaNestColors.Border),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Show more streams (${totalStreams - streamsLimit} remaining)")
                                }
                            }
                        }
                    } else if (totalStreams > 0) {
                        item {
                            EndOfListIndicator()
                        }
                    }

                    if (videoStreams.isEmpty() && audioStreams.isEmpty()) {
                        item {
                            if (isFetchingOnline) {
                                Text(
                                    "Loading streams…",
                                    color = MediaNestColors.TextSecondary
                                )
                            } else {
                                Text(
                                    "No streams available",
                                    color = MediaNestColors.Destructive
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWatchCountDialog) {
        WatchCountDialog(
            videoTitle = videoInfo.title,
            initialCount = localVideo?.watchCount ?: 0,
            onDismiss = { showWatchCountDialog = false },
            onConfirm = { newCount ->
                onMarkWatched(newCount)
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = MediaNestColors.Raised,
            title = {
                Text(
                    text = "Reset watch position?",
                    color = MediaNestColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "This clears your saved playback position for this video.",
                    color = MediaNestColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetWatchPosition()
                        showResetConfirm = false
                    }
                ) {
                    Text(
                        text = "Reset",
                        color = MediaNestColors.Destructive
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirm = false }
                ) {
                    Text(
                        text = "Cancel",
                        color = MediaNestColors.TextSecondary
                    )
                }
            }
        )
    }

    if (showDeleteDownloadDialogFor != null) {
        val download = showDeleteDownloadDialogFor!!
        val isActive = download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED
        val displayTitle = download.title.ifEmpty { download.quality }

        AlertDialog(
            onDismissRequest = { showDeleteDownloadDialogFor = null },
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
                            onDeleteDownload(download, true)
                            showDeleteDownloadDialogFor = null
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
                                onDeleteDownload(download, false)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Removed from downloads list")
                                }
                                showDeleteDownloadDialogFor = null
                            },
                            variant = MediaNestButtonVariant.Secondary,
                            size = MediaNestButtonSize.Small
                        )
                        MediaNestButton(
                            text = "Delete File & List",
                            onClick = {
                                onDeleteDownload(download, true)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Download deleted")
                                }
                                showDeleteDownloadDialogFor = null
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
                    onClick = { showDeleteDownloadDialogFor = null },
                    variant = MediaNestButtonVariant.Ghost,
                    size = MediaNestButtonSize.Small
                )
            }
        )
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
    val isAudio = stream.format == "audio"
    val typeLabel = when (stream.format) {
        "video_only", "video" -> stream.quality
        "audio" -> {
            val langSuffix = if (!stream.language.isNullOrBlank()) " [${stream.language}]" else ""
            "Audio (${stream.quality})$langSuffix"
        }
        else -> stream.format
    }
    val label = if (stream.codec.isNotEmpty()) {
        "${typeLabel} • ${stream.codec.uppercase()}"
    } else {
        typeLabel
    }
    val sizeText = when {
        stream.contentLength != null && stream.contentLength > 0 -> {
            val videoSize = "%.1f MB".format(stream.contentLength / (1024f * 1024f))
            if (stream.format == "video_only" && bestAudioLength != null && bestAudioLength > 0) {
                val audioSize = "%.1f MB".format(bestAudioLength / (1024f * 1024f))
                "~$videoSize + $audioSize"
            } else {
                "~$videoSize"
            }
        }
        else -> "Resolving size…"
    }

    val dbQuality = if (stream.format == "audio") stream.quality else "${stream.quality} (${stream.codec})"
    val downloadState = downloads.find { it.format == stream.format && it.quality == dbQuality }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MediaNestColors.Card
        ),
        border = BorderStroke(1.dp, MediaNestColors.Border),
        shape = RoundedCornerShape(10.dp),
        onClick = { onPlay(stream) }
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(MediaNestColors.Raised, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (isAudio) R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                        contentDescription = null,
                        tint = MediaNestColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MediaNestColors.TextPrimary
                    )
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MediaNestColors.TextSecondary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onPlay(stream) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_play),
                        contentDescription = "Play",
                        tint = MediaNestColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (downloadState != null) {
                    when (downloadState.status) {
                        DownloadStatus.COMPLETED -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_check_circle),
                                    contentDescription = "Downloaded",
                                    tint = MediaNestColors.Success,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Saved",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MediaNestColors.Success,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                            TextButton(onClick = {}, enabled = false) {
                                Text("Downloading", color = MediaNestColors.Accent)
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            TextButton(onClick = {}, enabled = false) {
                                Text("Paused", color = MediaNestColors.TextSecondary)
                            }
                        }
                        DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                            IconButton(
                                onClick = {
                                    onDownload(stream)
                                    onShowSnackbar("Added to download queue")
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_download),
                                    contentDescription = "Download",
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    IconButton(
                        onClick = {
                            onDownload(stream)
                            onShowSnackbar("Added to download queue")
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_download),
                            contentDescription = "Download",
                            tint = MediaNestColors.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
