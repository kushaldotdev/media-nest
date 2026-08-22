package com.example.medianest.ui.components

import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.medianest.R
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.utils.UiUtils

/**
 * CompositionLocal providing global preference for displaying full vs truncated video titles.
 */
val LocalFullTitles = staticCompositionLocalOf { false }

/**
 * Configuration options for displaying optional features on the unified video card.
 */
data class VideoCardConfig(
    val showFavoriteButton: Boolean = false,
    val showPlayButton: Boolean = false,
    val showMoveToFolderButton: Boolean = false,
    val showRemoveFromFolderButton: Boolean = false,
    val showDownloadButton: Boolean = false,
    val showSelectionCheckbox: Boolean = false,
    val showFolderBadges: Boolean = false,
    val showPlaybackProgress: Boolean = false,
    val showDownloadedBadge: Boolean = false,
    val showMarkWatchedButton: Boolean = false,
    val showMediaTypeBadge: Boolean = false,
    val showMoreButton: Boolean = true,
    val fullTitles: Boolean? = null
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
                        painter = painterResource(R.drawable.ic_mn_folder),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = firstFolder.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 70.dp)
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
                                        painter = painterResource(R.drawable.ic_mn_folder),
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    watchCount: Int = 0,
    folders: List<FolderEntity> = emptyList(),
    config: VideoCardConfig = VideoCardConfig(),
    onClick: () -> Unit = {},
    onPlayClick: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    onMoveToFolder: () -> Unit = {},
    onRemoveFromFolder: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onMarkWatched: () -> Unit = {},
    onSelectionToggle: () -> Unit = {},
    downloadMenuContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    serialNumber: Int? = null,
    mediaType: String = "VIDEO"
) {
    val globalFullTitles = LocalFullTitles.current
    val initialExpanded = config.fullTitles ?: globalFullTitles
    var isTitleExpanded by remember(initialExpanded) { mutableStateOf(initialExpanded) }
    var showActionSheet by remember { mutableStateOf(false) }

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

                // Green Downloaded badge - TOP RIGHT corner on thumbnail
                if (config.showDownloadedBadge && isDownloaded) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_check_circle),
                        contentDescription = "Downloaded",
                        tint = MediaNestColors.Success,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .background(MediaNestColors.PlayerSurface.copy(alpha = 0.6f), RoundedCornerShape(50))
                    )
                }

                // Media type badge - TOP LEFT corner on thumbnail
                if (config.showMediaTypeBadge) {
                    val isAudio = mediaType.equals("AUDIO", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                color = if (isAudio) MediaNestColors.AccentDeep.copy(alpha = 0.85f) else MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(if (isAudio) R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                            contentDescription = if (isAudio) "Audio" else "Video",
                            tint = MediaNestColors.TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Eye icon with view count - BOTTOM LEFT corner on thumbnail
                if (watchCount > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(
                                color = MediaNestColors.PlayerSurface.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_eye),
                            contentDescription = "Watch count",
                            tint = MediaNestColors.TextPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = watchCount.toString(),
                            color = MediaNestColors.TextPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Duration badge - BOTTOM RIGHT corner
                if (durationSeconds > 0) {
                    Text(
                        text = UiUtils.formatDuration(durationSeconds),
                        color = MediaNestColors.TextPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(
                                color = MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
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
                            .background(MediaNestColors.ProgressTrack.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(playbackProgressFraction.coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(MediaNestColors.YouTubeRed)
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
                        val displayTitle = if (serialNumber != null && title.isNotEmpty()) "$serialNumber. $title" else title
                        Text(
                            text = displayTitle,
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

                // Action 3-dot button (if selection mode is disabled)
                if (!config.showSelectionCheckbox && config.showMoreButton) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onPlayClick != null && config.showPlayButton) {
                            IconButton(
                                onClick = onPlayClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_play),
                                    contentDescription = "Play",
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (isFavorite || config.showFavoriteButton) {
                            IconButton(
                                onClick = onFavoriteToggle,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (isFavorite) R.drawable.ic_mn_heart_filled else R.drawable.ic_mn_heart),
                                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorite) MediaNestColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Box {
                            IconButton(
                                onClick = { showActionSheet = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_more),
                                    contentDescription = "More actions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            downloadMenuContent?.invoke()
                        }
                    }
                }
            }
        }
    }

    if (showActionSheet) {
        val actions = remember(isFavorite) {
            listOf(
                VideoActionItem(
                    id = "play",
                    label = "Play Video",
                    iconRes = R.drawable.ic_mn_play
                ),
                VideoActionItem(
                    id = "folder",
                    label = "Add to / Move to Folder",
                    iconRes = R.drawable.ic_mn_folder
                ),
                VideoActionItem(
                    id = "download",
                    label = "Download / Quality Options",
                    iconRes = R.drawable.ic_mn_download
                ),
                VideoActionItem(
                    id = "favorite",
                    label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                    iconRes = if (isFavorite) R.drawable.ic_mn_heart_filled else R.drawable.ic_mn_heart,
                    active = isFavorite
                ),
                VideoActionItem(
                    id = "share",
                    label = "Share URL / File",
                    iconRes = R.drawable.ic_mn_share
                ),
                VideoActionItem(
                    id = "delete",
                    label = "Delete / Remove Video",
                    iconRes = R.drawable.ic_mn_trash,
                    destructive = true
                )
            )
        }

        VideoActionBottomSheet(
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            actions = actions,
            onAction = { actionId ->
                showActionSheet = false
                when (actionId) {
                    "play" -> (onPlayClick ?: onClick).invoke()
                    "folder" -> onMoveToFolder()
                    "download" -> onDownloadClick()
                    "favorite" -> onFavoriteToggle()
                    "share" -> { /* share = no-op */ }
                    "delete" -> onRemoveFromFolder()
                }
            },
            onDismiss = { showActionSheet = false }
        )
    }
}

/**
 * Universal video row (List layout representation) with glassmorphism.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    watchCount: Int = 0,
    folders: List<FolderEntity> = emptyList(),
    config: VideoCardConfig = VideoCardConfig(),
    onClick: () -> Unit = {},
    onPlayClick: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    onMoveToFolder: () -> Unit = {},
    onRemoveFromFolder: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onMarkWatched: () -> Unit = {},
    onSelectionToggle: () -> Unit = {},
    downloadMenuContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    serialNumber: Int? = null,
    mediaType: String = "VIDEO"
) {
    val globalFullTitles = LocalFullTitles.current
    val initialExpanded = config.fullTitles ?: globalFullTitles
    var isTitleExpanded by remember(initialExpanded) { mutableStateOf(initialExpanded) }
    var showActionSheet by remember { mutableStateOf(false) }

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
            verticalAlignment = Alignment.Top
        ) {
            // Left Column: Thumbnail Box + Folder Badges
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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

                    // Green Downloaded badge - TOP RIGHT corner on thumbnail
                    if (config.showDownloadedBadge && isDownloaded) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_check_circle),
                            contentDescription = "Downloaded",
                            tint = MediaNestColors.Success,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(16.dp)
                                .background(MediaNestColors.PlayerSurface.copy(alpha = 0.6f), RoundedCornerShape(50))
                        )
                    }

                    // Media type badge - TOP LEFT corner on thumbnail
                    if (config.showMediaTypeBadge) {
                        val isAudio = mediaType.equals("AUDIO", ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .background(
                                    color = if (isAudio) MediaNestColors.AccentDeep.copy(alpha = 0.85f) else MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(if (isAudio) R.drawable.ic_mn_music else R.drawable.ic_mn_video),
                                contentDescription = if (isAudio) "Audio" else "Video",
                                tint = MediaNestColors.TextPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Eye icon with view count - BOTTOM LEFT corner on thumbnail
                    if (watchCount > 0) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .background(
                                    color = MediaNestColors.PlayerSurface.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_eye),
                                contentDescription = "Watch count",
                                tint = MediaNestColors.TextPrimary,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = watchCount.toString(),
                                color = MediaNestColors.TextPrimary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // Duration badge - BOTTOM RIGHT corner
                    if (durationSeconds > 0) {
                        Text(
                            text = UiUtils.formatDuration(durationSeconds),
                            color = MediaNestColors.TextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(
                                    color = MediaNestColors.PlayerSurface.copy(alpha = 0.7f),
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
                                .background(MediaNestColors.ProgressTrack.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(playbackProgressFraction.coerceIn(0f, 1f))
                                    .height(2.dp)
                                    .background(MediaNestColors.YouTubeRed)
                            )
                        }
                    }
                }

                // Folder badges placed under the thumbnail
                if (config.showFolderBadges) {
                    FolderBadges(folders)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Info and controls column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize()
            ) {

                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Title (Tap to expand/collapse)
                        val displayTitle = if (serialNumber != null && title.isNotEmpty()) "$serialNumber. $title" else title
                        Text(
                            text = displayTitle,
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

                // Action 3-dot button (if selection mode is disabled)
                if (!config.showSelectionCheckbox && config.showMoreButton) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onPlayClick != null && config.showPlayButton) {
                            IconButton(
                                onClick = onPlayClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_play),
                                    contentDescription = "Play",
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (isFavorite || config.showFavoriteButton) {
                            IconButton(
                                onClick = onFavoriteToggle,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (isFavorite) R.drawable.ic_mn_heart_filled else R.drawable.ic_mn_heart),
                                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorite) MediaNestColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Box {
                            IconButton(
                                onClick = { showActionSheet = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_more),
                                    contentDescription = "More actions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            downloadMenuContent?.invoke()
                        }
                    }
                }
            }
        }
    }

    if (showActionSheet) {
        val actions = remember(isFavorite) {
            listOf(
                VideoActionItem(
                    id = "play",
                    label = "Play Video",
                    iconRes = R.drawable.ic_mn_play
                ),
                VideoActionItem(
                    id = "folder",
                    label = "Add to / Move to Folder",
                    iconRes = R.drawable.ic_mn_folder
                ),
                VideoActionItem(
                    id = "download",
                    label = "Download / Quality Options",
                    iconRes = R.drawable.ic_mn_download
                ),
                VideoActionItem(
                    id = "favorite",
                    label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                    iconRes = if (isFavorite) R.drawable.ic_mn_heart_filled else R.drawable.ic_mn_heart,
                    active = isFavorite
                ),
                VideoActionItem(
                    id = "share",
                    label = "Share URL / File",
                    iconRes = R.drawable.ic_mn_share
                ),
                VideoActionItem(
                    id = "delete",
                    label = "Delete / Remove Video",
                    iconRes = R.drawable.ic_mn_trash,
                    destructive = true
                )
            )
        }

        VideoActionBottomSheet(
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            actions = actions,
            onAction = { actionId ->
                showActionSheet = false
                when (actionId) {
                    "play" -> (onPlayClick ?: onClick).invoke()
                    "folder" -> onMoveToFolder()
                    "download" -> onDownloadClick()
                    "favorite" -> onFavoriteToggle()
                    "share" -> { /* share = no-op */ }
                    "delete" -> onRemoveFromFolder()
                }
            },
            onDismiss = { showActionSheet = false }
        )
    }
}

@Composable
fun QuickDownloadMenu(
    isExpanded: Boolean,
    onDismiss: () -> Unit,
    isFetching: Boolean,
    fetchedStreams: ExtractedVideoInfo?,
    allDownloads: List<DownloadEntity>,
    videoId: String,
    onEnqueueDownload: (ExtractedVideoInfo, StreamSource) -> Unit,
    onDeleteDownload: (DownloadEntity) -> Unit,
    onExtractAudio: (DownloadEntity) -> Unit
) {
    val context = LocalContext.current
    DropdownMenu(
        expanded = isExpanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.5f).dp)
    ) {
        if (isFetching) {
            DropdownMenuItem(
                text = { Text("Loading formats...") },
                leadingIcon = { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) },
                onClick = { }
            )
        } else if (fetchedStreams != null) {
            val streams = fetchedStreams.streamSources
            val downloadedEntities = allDownloads.filter { it.videoId == videoId }

            if (downloadedEntities.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Downloaded Formats", style = MaterialTheme.typography.titleSmall) },
                    onClick = { }
                )
                downloadedEntities.forEach { entity ->
                    DropdownMenuItem(
                        text = { Text("Delete ${if (entity.format == "audio" || entity.format == "audio_extracted") "Audio" else entity.quality}") },
                        leadingIcon = { Icon(painter = painterResource(R.drawable.ic_mn_trash), contentDescription = "Delete") },
                        onClick = { onDeleteDownload(entity); onDismiss() }
                    )
                    if ((entity.format == "video" || entity.format == "video_only") &&
                        !downloadedEntities.any { it.format == "audio_extracted" }) {
                        DropdownMenuItem(
                            text = { Text("Extract Audio from ${entity.quality}") },
                            leadingIcon = { Icon(painter = painterResource(R.drawable.ic_mn_extract), contentDescription = "Extract") },
                            onClick = { onExtractAudio(entity); onDismiss() }
                        )
                    }
                }
                HorizontalDivider()
            }

            val videoStreams = streams.filter { it.format == "video" || it.format == "video_only" }
            val bestAudioStream = streams.filter { it.format == "audio" }
                .maxByOrNull { it.quality.replace("kbps", "").toIntOrNull() ?: 0 }
            val bestAudioLength = bestAudioStream?.contentLength

            val groupedVideos = videoStreams.groupBy { it.quality }
            val sortedResolutions = groupedVideos.keys.sortedByDescending { 
                it.substringBefore("p").toIntOrNull() ?: 0 
            }
            
            if (sortedResolutions.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Available Videos", style = MaterialTheme.typography.titleSmall) },
                    onClick = { }
                )
                sortedResolutions.forEach { resolution ->
                    DropdownMenuItem(
                        text = { Text(resolution, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) },
                        onClick = { }
                    )
                    val streamsInResolution = groupedVideos[resolution] ?: emptyList()
                    streamsInResolution.forEach { stream ->
                        val dbQuality = if (stream.format == "audio") stream.quality else "${stream.quality} (${stream.codec})"
                        val isDownloaded = downloadedEntities.any { it.format == stream.format && it.quality == dbQuality }
                        if (!isDownloaded) {
                            DropdownMenuItem(
                                text = {
                                    val sizeStr = stream.contentLength?.let { videoLen ->
                                        val videoSize = Formatter.formatShortFileSize(context, videoLen)
                                        if (stream.format == "video_only" && bestAudioLength != null && bestAudioLength > 0) {
                                            val audioSize = Formatter.formatShortFileSize(context, bestAudioLength)
                                            " • $videoSize + $audioSize"
                                        } else {
                                            " • $videoSize"
                                        }
                                    } ?: ""
                                    val typeLabel = "Video"
                                    val codecLabel = if (stream.codec.isNotEmpty()) " (${stream.codec.uppercase()})" else ""
                                    Text("$typeLabel$codecLabel$sizeStr")
                                },
                                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_mn_download), contentDescription = "Download") },
                                onClick = { onEnqueueDownload(fetchedStreams, stream); onDismiss() }
                            )
                        }
                    }
                }
            }
        }
    }
}
