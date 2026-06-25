package com.example.medianest.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.ui.utils.UiUtils

/**
 * Configuration options for displaying optional features on the unified video card.
 */
data class VideoCardConfig(
    val showFavoriteButton: Boolean = false,
    val showMoveToFolderButton: Boolean = false,
    val showDownloadButton: Boolean = false,
    val showSelectionCheckbox: Boolean = false,
    val showFolderBadges: Boolean = false,
    val showPlaybackProgress: Boolean = false,
    val showDownloadedBadge: Boolean = false
)

/**
 * Folder badges display component.
 */
@Composable
private fun FolderBadges(folders: List<FolderEntity>) {
    if (folders.isNotEmpty()) {
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val firstFolder = folders.first()
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = firstFolder.name,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (folders.size > 1) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.clickable { expanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "+${folders.size - 1}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        folders.drop(1).forEach { folder ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                text = {
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                onClick = {
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Universal video card (Grid layout representation) with glassmorphism.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedVideoCard(
    title: String,
    channelName: String,
    thumbnailUrl: String?,
    durationSeconds: Long = 0,
    uploadDate: String? = null,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    isSelected: Boolean = false,
    playbackProgressFraction: Float = 0f,
    folders: List<FolderEntity> = emptyList(),
    config: VideoCardConfig = VideoCardConfig(),
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    onMoveToFolder: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onSelectionToggle: () -> Unit = {},
    downloadMenuContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isTitleExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column {
            // Thumbnail Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Green Downloaded badge - TOP LEFT corner on thumbnail
                if (config.showDownloadedBadge && isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    )
                }

                // Duration badge - BOTTOM RIGHT corner
                if (durationSeconds > 0) {
                    Text(
                        text = UiUtils.formatDuration(durationSeconds),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Playback progress bar (bottom edge of thumbnail)
                if (config.showPlaybackProgress && playbackProgressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(playbackProgressFraction.coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(Color.Red)
                        )
                    }
                }
            }

            // Info Column
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .animateContentSize()
            ) {
                if (config.showFolderBadges) {
                    FolderBadges(folders)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Title (Tap to expand/collapse)
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { isTitleExpanded = !isTitleExpanded }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Metadata
                        val formattedDate = UiUtils.formatReleaseDate(uploadDate)
                        val metadataText = buildString {
                            if (channelName.isNotEmpty()) {
                                append(channelName)
                            }
                            if (!formattedDate.isNullOrEmpty()) {
                                if (isNotEmpty()) append(" • ")
                                append(formattedDate)
                            }
                        }
                        if (metadataText.isNotEmpty()) {
                            Text(
                                text = metadataText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (config.showSelectionCheckbox) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectionToggle() },
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // Action buttons row (if selection mode is disabled)
                if (!config.showSelectionCheckbox &&
                    (config.showFavoriteButton || config.showMoveToFolderButton || config.showDownloadButton)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (config.showFavoriteButton) {
                            IconToggleButton(
                                checked = isFavorite,
                                onCheckedChange = { onFavoriteToggle() }
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (config.showMoveToFolderButton) {
                            IconButton(onClick = onMoveToFolder) {
                                Icon(
                                    imageVector = Icons.Default.DriveFileMove,
                                    contentDescription = "Move to folder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (config.showDownloadButton) {
                            Box {
                                IconButton(onClick = onDownloadClick) {
                                    Icon(
                                        imageVector = if (!isDownloaded) Icons.Default.Download else Icons.Default.DownloadDone,
                                        contentDescription = "Download",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                downloadMenuContent?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Universal video row (List layout representation) with glassmorphism.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedVideoRow(
    title: String,
    channelName: String,
    thumbnailUrl: String?,
    durationSeconds: Long = 0,
    uploadDate: String? = null,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    isSelected: Boolean = false,
    playbackProgressFraction: Float = 0f,
    folders: List<FolderEntity> = emptyList(),
    config: VideoCardConfig = VideoCardConfig(),
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    onMoveToFolder: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onSelectionToggle: () -> Unit = {},
    downloadMenuContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isTitleExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail Box on the Left
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 68.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Green Downloaded badge - TOP LEFT corner on thumbnail
                if (config.showDownloadedBadge && isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    )
                }

                // Duration badge - BOTTOM RIGHT corner
                if (durationSeconds > 0) {
                    Text(
                        text = UiUtils.formatDuration(durationSeconds),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // Playback progress bar (bottom edge of thumbnail)
                if (config.showPlaybackProgress && playbackProgressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(playbackProgressFraction.coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(Color.Red)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Info and controls column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize()
            ) {
                if (config.showFolderBadges) {
                    FolderBadges(folders)
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Title (Tap to expand/collapse)
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { isTitleExpanded = !isTitleExpanded }
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Metadata
                        val formattedDate = UiUtils.formatReleaseDate(uploadDate)
                        val metadataText = buildString {
                            if (channelName.isNotEmpty()) {
                                append(channelName)
                            }
                            if (!formattedDate.isNullOrEmpty()) {
                                if (isNotEmpty()) append(" • ")
                                append(formattedDate)
                            }
                        }
                        if (metadataText.isNotEmpty()) {
                            Text(
                                text = metadataText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (config.showSelectionCheckbox) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectionToggle() },
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // Action buttons row (if selection mode is disabled)
                if (!config.showSelectionCheckbox &&
                    (config.showFavoriteButton || config.showMoveToFolderButton || config.showDownloadButton)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (config.showFavoriteButton) {
                            IconToggleButton(
                                checked = isFavorite,
                                onCheckedChange = { onFavoriteToggle() }
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (config.showMoveToFolderButton) {
                            IconButton(onClick = onMoveToFolder, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.DriveFileMove,
                                    contentDescription = "Move to folder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (config.showDownloadButton) {
                            Box {
                                IconButton(onClick = onDownloadClick, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = if (!isDownloaded) Icons.Default.Download else Icons.Default.DownloadDone,
                                        contentDescription = "Download",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                downloadMenuContent?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }
}
