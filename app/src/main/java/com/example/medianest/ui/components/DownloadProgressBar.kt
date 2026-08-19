package com.example.medianest.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestTheme

/**
 * Download stages with associated brand colors.
 */
enum class DownloadProgressStage(val defaultColor: Color, val label: String) {
    QUEUED(MediaNestColors.TextSecondary, "Queued"),
    VIDEO(MediaNestColors.ProgressVideo, "Downloading Video"),
    AUDIO(MediaNestColors.ProgressAudio, "Downloading Audio"),
    EXTRACTING(MediaNestColors.ProgressAudio, "Extracting Audio"),
    MERGING(MediaNestColors.ProgressMerge, "Merging Media"),
    COMPLETED(MediaNestColors.Success, "Completed"),
    PAUSED(MediaNestColors.TextSecondary, "Paused"),
    FAILED(MediaNestColors.Destructive, "Failed"),
    CANCELED(MediaNestColors.Border, "Canceled"),
    INDETERMINATE(MediaNestColors.ProgressMerge, "Processing")
}

/**
 * Data segment for multi-segment progress rendering.
 */
data class DownloadProgressSegment(
    val progress: Float,
    val color: Color,
    val label: String? = null
)

/**
 * Multi-segment / state-aware progress bar for MediaNest downloads.
 *
 * Uses brand tokens:
 * - Track: [MediaNestColors.ProgressTrack] (#54333C)
 * - Video Segment: [MediaNestColors.ProgressVideo] (#FFB1B6)
 * - Audio Segment: [MediaNestColors.ProgressAudio] (#FF9800)
 * - Merge Segment: [MediaNestColors.ProgressMerge] (#67D98A)
 *
 * Displays status text, percentage, download speed, and ETA with smooth animated transitions.
 */
@Composable
fun DownloadProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    stage: DownloadProgressStage = DownloadProgressStage.VIDEO,
    videoProgress: Float? = null,
    audioProgress: Float? = null,
    mergeProgress: Float? = null,
    statusText: String? = null,
    percentage: Int? = null,
    downloadSpeed: String? = null,
    eta: String? = null,
    elapsed: String? = null,
    isIndeterminate: Boolean = false,
    showMetadata: Boolean = true,
    barHeight: Dp = 6.dp,
    trackColor: Color = MediaNestColors.ProgressTrack
) {
    val effectivePercentage = percentage ?: (progress.coerceIn(0f, 1f) * 100).toInt()
    val effectiveStatusText = statusText ?: stage.label

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showMetadata) {
            // Header Row: Status text and Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = effectiveStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (stage) {
                        DownloadProgressStage.FAILED -> MediaNestColors.Destructive
                        DownloadProgressStage.COMPLETED -> MediaNestColors.Success
                        else -> MediaNestColors.TextPrimary
                    },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (!isIndeterminate && stage != DownloadProgressStage.FAILED && stage != DownloadProgressStage.CANCELED) {
                    Text(
                        text = "$effectivePercentage%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = when (stage) {
                            DownloadProgressStage.COMPLETED -> MediaNestColors.Success
                            DownloadProgressStage.MERGING -> MediaNestColors.ProgressMerge
                            DownloadProgressStage.AUDIO, DownloadProgressStage.EXTRACTING -> MediaNestColors.ProgressAudio
                            else -> MediaNestColors.Accent
                        }
                    )
                }
            }
        }

        // Progress Bar Track
        if (isIndeterminate || stage == DownloadProgressStage.INDETERMINATE) {
            IndeterminateProgressBar(
                modifier = Modifier.fillMaxWidth(),
                barHeight = barHeight,
                color = when (stage) {
                    DownloadProgressStage.MERGING -> MediaNestColors.ProgressMerge
                    DownloadProgressStage.AUDIO, DownloadProgressStage.EXTRACTING -> MediaNestColors.ProgressAudio
                    else -> MediaNestColors.Accent
                },
                trackColor = trackColor
            )
        } else if (videoProgress != null && audioProgress != null) {
            DualSegmentProgressBar(
                videoProgress = videoProgress,
                audioProgress = audioProgress,
                barHeight = barHeight,
                trackColor = trackColor
            )
        } else {
            val targetProgress = progress.coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = targetProgress,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "singleProgress"
            )

            val fillColor = when (stage) {
                DownloadProgressStage.VIDEO -> MediaNestColors.ProgressVideo
                DownloadProgressStage.AUDIO, DownloadProgressStage.EXTRACTING -> MediaNestColors.ProgressAudio
                DownloadProgressStage.MERGING -> MediaNestColors.ProgressMerge
                DownloadProgressStage.COMPLETED -> MediaNestColors.Success
                DownloadProgressStage.FAILED -> MediaNestColors.Destructive
                DownloadProgressStage.PAUSED -> MediaNestColors.TextSecondary
                DownloadProgressStage.QUEUED -> MediaNestColors.TextSecondary.copy(alpha = 0.5f)
                DownloadProgressStage.CANCELED -> MediaNestColors.Border
                DownloadProgressStage.INDETERMINATE -> MediaNestColors.ProgressMerge
            }

            SingleSegmentProgressBar(
                progress = animatedProgress,
                barHeight = barHeight,
                fillColor = fillColor,
                trackColor = trackColor
            )
        }

        // Footer Row: Speed and ETA metadata
        if (showMetadata && (downloadSpeed != null || eta != null || elapsed != null)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val startInfo = listOfNotNull(elapsed, downloadSpeed).joinToString(" · ")
                if (startInfo.isNotEmpty()) {
                    Text(
                        text = startInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MediaNestColors.TextSecondary
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (eta != null) {
                    Text(
                        text = if (eta.startsWith("ETA", ignoreCase = true)) eta else "ETA $eta",
                        style = MaterialTheme.typography.labelSmall,
                        color = MediaNestColors.TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Single-segment progress track with rounded capsule ends.
 */
@Composable
fun SingleSegmentProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    barHeight: Dp = 6.dp,
    fillColor: Color = MediaNestColors.Accent,
    trackColor: Color = MediaNestColors.ProgressTrack
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
    ) {
        val width = size.width
        val height = size.height
        val cornerRadius = CornerRadius(height / 2, height / 2)

        // Track background
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = cornerRadius
        )

        // Progress fill
        if (progress > 0f) {
            val fillWidth = (width * progress.coerceIn(0f, 1f))
            drawRoundRect(
                color = fillColor,
                size = Size(fillWidth, height),
                cornerRadius = cornerRadius
            )
        }
    }
}

/**
 * Dual-segment progress bar for downloads split between video and audio streams.
 */
@Composable
fun DualSegmentProgressBar(
    videoProgress: Float,
    audioProgress: Float,
    modifier: Modifier = Modifier,
    videoWeight: Float = 0.8f,
    audioWeight: Float = 0.2f,
    barHeight: Dp = 6.dp,
    trackColor: Color = MediaNestColors.ProgressTrack,
    videoColor: Color = MediaNestColors.ProgressVideo,
    audioColor: Color = MediaNestColors.ProgressAudio
) {
    val animatedVideo by animateFloatAsState(
        targetValue = videoProgress.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "dualVideoProgress"
    )
    val animatedAudio by animateFloatAsState(
        targetValue = audioProgress.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "dualAudioProgress"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
    ) {
        val width = size.width
        val height = size.height
        val cornerRadius = CornerRadius(height / 2, height / 2)

        // Track background
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = cornerRadius
        )

        val totalWeight = videoWeight + audioWeight
        val videoMaxWidth = (videoWeight / totalWeight) * width
        val audioMaxWidth = (audioWeight / totalWeight) * width

        val currentVideoWidth = videoMaxWidth * animatedVideo
        val currentAudioWidth = audioMaxWidth * animatedAudio

        // Video segment (starts at 0)
        if (currentVideoWidth > 0f) {
            drawRoundRect(
                color = videoColor,
                size = Size(currentVideoWidth, height),
                cornerRadius = cornerRadius
            )
        }

        // Audio segment (starts after video allocated portion)
        if (currentAudioWidth > 0f) {
            val audioStart = videoMaxWidth
            drawRoundRect(
                color = audioColor,
                topLeft = Offset(audioStart, 0f),
                size = Size(currentAudioWidth, height),
                cornerRadius = cornerRadius
            )
        }
    }
}

/**
 * Multi-segment progress bar with arbitrary ordered segments.
 */
@Composable
fun MultiSegmentProgressBar(
    segments: List<DownloadProgressSegment>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 6.dp,
    trackColor: Color = MediaNestColors.ProgressTrack
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
    ) {
        val width = size.width
        val height = size.height
        val cornerRadius = CornerRadius(height / 2, height / 2)

        // Track
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = cornerRadius
        )

        var accumulatedStart = 0f
        segments.forEach { segment ->
            val segWidth = (segment.progress.coerceIn(0f, 1f) * width)
            if (segWidth > 0f) {
                drawRoundRect(
                    color = segment.color,
                    topLeft = Offset(accumulatedStart, 0f),
                    size = Size(segWidth, height),
                    cornerRadius = cornerRadius
                )
                accumulatedStart += segWidth
            }
        }
    }
}

/**
 * Indeterminate progress bar with smooth animated shimmer gradient.
 */
@Composable
fun IndeterminateProgressBar(
    modifier: Modifier = Modifier,
    barHeight: Dp = 6.dp,
    color: Color = MediaNestColors.ProgressMerge,
    trackColor: Color = MediaNestColors.ProgressTrack
) {
    val infiniteTransition = rememberInfiniteTransition(label = "indeterminateProgress")
    val offsetFraction by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
    ) {
        val width = size.width
        val height = size.height
        val cornerRadius = CornerRadius(height / 2, height / 2)

        // Track background
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = cornerRadius
        )

        // Shimmer gradient
        val shimmerWidth = width * 0.5f
        val startX = offsetFraction * width
        val brush = Brush.linearGradient(
            colors = listOf(
                color.copy(alpha = 0.1f),
                color,
                color.copy(alpha = 0.1f)
            ),
            start = Offset(startX, 0f),
            end = Offset(startX + shimmerWidth, 0f)
        )

        drawRoundRect(
            brush = brush,
            size = size,
            cornerRadius = cornerRadius
        )
    }
}

// -----------------------------------------------------------------------------
// Previews
// -----------------------------------------------------------------------------

@Preview(name = "Video Download in Progress", showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun DownloadProgressBarVideoPreview() {
    MediaNestTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DownloadProgressBar(
                progress = 0.65f,
                stage = DownloadProgressStage.VIDEO,
                statusText = "Downloading video • 1080p",
                downloadSpeed = "4.8 MB/s",
                eta = "00:32"
            )
        }
    }
}

@Preview(name = "Audio Download", showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun DownloadProgressBarAudioPreview() {
    MediaNestTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DownloadProgressBar(
                progress = 0.42f,
                stage = DownloadProgressStage.AUDIO,
                statusText = "Downloading audio stream",
                downloadSpeed = "1.2 MB/s",
                eta = "00:08"
            )
        }
    }
}

@Preview(name = "Merging Media", showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun DownloadProgressBarMergingPreview() {
    MediaNestTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DownloadProgressBar(
                progress = 0.88f,
                stage = DownloadProgressStage.MERGING,
                statusText = "Muxing audio and video streams...",
                downloadSpeed = "FFmpeg muxer",
                eta = "00:04"
            )
        }
    }
}

@Preview(name = "Dual Segment Video + Audio", showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun DownloadProgressBarDualPreview() {
    MediaNestTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DownloadProgressBar(
                progress = 0.75f,
                videoProgress = 1.0f,
                audioProgress = 0.5f,
                stage = DownloadProgressStage.AUDIO,
                statusText = "Video complete • Audio 50%",
                downloadSpeed = "3.1 MB/s",
                eta = "00:12"
            )
        }
    }
}
