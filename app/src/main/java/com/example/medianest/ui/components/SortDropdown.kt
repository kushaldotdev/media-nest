package com.example.medianest.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medianest.R
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestShapes

/**
 * Data model for sortable options in MediaNest.
 */
data class MediaNestSortOption(
    val id: String,
    val label: String,
    val iconRes: Int? = null,
    val description: String? = null
) {
    companion object {
        val DATE = MediaNestSortOption(
            id = "DATE",
            label = "Date Added",
            iconRes = R.drawable.ic_mn_history,
            description = "Recently added or uploaded"
        )
        val TITLE = MediaNestSortOption(
            id = "TITLE",
            label = "Title",
            iconRes = R.drawable.ic_mn_sort,
            description = "Alphabetical order by title"
        )
        val DURATION = MediaNestSortOption(
            id = "DURATION",
            label = "Duration",
            iconRes = R.drawable.ic_mn_speed,
            description = "Playback time length"
        )
        val SIZE = MediaNestSortOption(
            id = "SIZE",
            label = "File Size",
            iconRes = R.drawable.ic_mn_sliders,
            description = "Storage space consumed"
        )
        val PROGRESS = MediaNestSortOption(
            id = "PROGRESS",
            label = "Download Progress",
            iconRes = R.drawable.ic_mn_check_circle,
            description = "Completion percentage"
        )
        val STATUS = MediaNestSortOption(
            id = "STATUS",
            label = "Download Status",
            iconRes = R.drawable.ic_mn_check_circle,
            description = "Active, queued, paused, completed"
        )

        /** Default sort options for general media collections. */
        val DefaultMediaOptions = listOf(DATE, TITLE, DURATION, SIZE)

        /** Default sort options for download queue screens. */
        val DefaultDownloadOptions = listOf(DATE, PROGRESS, SIZE, STATUS)

        /** Default sort options for collection tabs. */
        val DefaultCollectionsOptions = listOf(DATE, TITLE, DURATION)
    }
}

/**
 * Sort direction enumeration.
 */
enum class MediaNestSortDirection(val label: String, val isAscending: Boolean) {
    ASCENDING("Ascending (↑)", true),
    DESCENDING("Descending (↓)", false);

    companion object {
        fun fromBoolean(isAscending: Boolean): MediaNestSortDirection =
            if (isAscending) ASCENDING else DESCENDING
    }
}

/**
 * Design 2.0 modal bottom sheet for selecting sort category and direction.
 *
 * Features:
 * - Raised surface (#382027) with top rounded border.
 * - Pill drag handle.
 * - Icon-supported sort category selection list with Accent check/highlighting.
 * - Segmented control for Ascending / Descending order toggle.
 * - High-emphasis Primary Apply button.
 *
 * @param onDismissRequest Called when user dismisses the bottom sheet.
 * @param selectedSortBy Currently selected sort category id (e.g. "DATE", "TITLE").
 * @param isAscending Whether ascending sorting is active.
 * @param onSortSelected Callback receiving new (sortBy, isAscending) values.
 * @param modifier Custom modifier.
 * @param sheetState Bottom sheet state controller.
 * @param title Header title text.
 * @param options List of sort categories displayed to the user.
 * @param showDirectionSection Whether to include the Ascending/Descending direction section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaNestSortBottomSheet(
    onDismissRequest: () -> Unit,
    selectedSortBy: String,
    isAscending: Boolean,
    onSortSelected: (sortBy: String, isAscending: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String = "Sort By",
    options: List<MediaNestSortOption> = MediaNestSortOption.DefaultMediaOptions,
    showDirectionSection: Boolean = true
) {
    var tempSortBy by remember(selectedSortBy) { mutableStateOf(selectedSortBy) }
    var tempIsAscending by remember(isAscending) { mutableStateOf(isAscending) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MediaNestColors.Raised,
        contentColor = MediaNestColors.TextPrimary,
        scrimColor = MediaNestColors.Scrim,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MediaNestColors.Border)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MediaNestColors.TextPrimary
                    )
                )

                MediaNestIconButton(
                    onClick = onDismissRequest,
                    size = MediaNestIconButtonSize.Small,
                    tint = MediaNestColors.TextSecondary,
                    contentDescription = "Close"
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_close),
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MediaNestColors.Border
            )

            // Category section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SORT BY",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MediaNestColors.TextSecondary,
                    letterSpacing = 0.5.sp
                )

                options.forEach { option ->
                    val isSelected = option.id.equals(tempSortBy, ignoreCase = true)
                    SortOptionRow(
                        option = option,
                        isSelected = isSelected,
                        onClick = {
                            tempSortBy = option.id
                        }
                    )
                }
            }

            // Direction section
            if (showDirectionSection) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ORDER",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MediaNestColors.TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    val directionOptions = listOf(
                        MediaNestSortDirection.DESCENDING,
                        MediaNestSortDirection.ASCENDING
                    )
                    val selectedDirectionIndex = if (tempIsAscending) 1 else 0

                    MediaNestSegmentedControl(
                        items = directionOptions,
                        selectedIndex = selectedDirectionIndex,
                        onItemSelected = { index ->
                            tempIsAscending = directionOptions[index].isAscending
                        },
                        itemLabel = { it.label },
                        itemPainter = {
                            if (it.isAscending) painterResource(R.drawable.ic_mn_arrow_up) else painterResource(R.drawable.ic_mn_arrow_down)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaNestButton(
                    text = "Cancel",
                    onClick = onDismissRequest,
                    variant = MediaNestButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )

                MediaNestButton(
                    text = "Apply",
                    onClick = {
                        onSortSelected(tempSortBy, tempIsAscending)
                        onDismissRequest()
                    },
                    variant = MediaNestButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Single sort option row within [MediaNestSortBottomSheet].
 */
@Composable
private fun SortOptionRow(
    option: MediaNestSortOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MediaNestColors.Card else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "sortOptionRowBg"
    )

    val border = if (isSelected) BorderStroke(1.dp, MediaNestColors.Border) else null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MediaNestShapes.Card)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = MediaNestShapes.Card,
        color = backgroundColor,
        border = border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            if (option.iconRes != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MediaNestColors.AccentDeep else MediaNestColors.Raised),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(option.iconRes),
                        contentDescription = null,
                        tint = if (isSelected) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Labels
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) MediaNestColors.Accent else MediaNestColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!option.description.isNullOrEmpty()) {
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MediaNestColors.TextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Selection indicator
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.ic_mn_check),
                    contentDescription = "Selected",
                    tint = MediaNestColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Compact sort trigger button showing active sort label with direction arrow indicator.
 */
@Composable
fun MediaNestSortButton(
    currentSortBy: String,
    isAscending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    options: List<MediaNestSortOption> = MediaNestSortOption.DefaultMediaOptions
) {
    val option = options.find { it.id.equals(currentSortBy, ignoreCase = true) }
    val baseLabel = option?.label ?: currentSortBy
    val directionSymbol = if (isAscending) "↑" else "↓"
    val displayLabel = "$baseLabel $directionSymbol"

    MediaNestChip(
        label = displayLabel,
        selected = true,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_mn_sort),
                contentDescription = "Sort",
                tint = MediaNestColors.TextPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    )
}

/**
 * Dropdown menu alternative for sorting when a bottom sheet is not preferred.
 */
@Composable
fun MediaNestSortDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    selectedSortBy: String,
    isAscending: Boolean,
    onSortSelected: (sortBy: String, isAscending: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    options: List<MediaNestSortOption> = MediaNestSortOption.DefaultMediaOptions
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.background(MediaNestColors.Raised)
    ) {
        options.forEach { option ->
            val isSelected = option.id.equals(selectedSortBy, ignoreCase = true)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = option.label,
                            color = if (isSelected) MediaNestColors.Accent else MediaNestColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Text(
                                text = if (isAscending) "↑" else "↓",
                                color = MediaNestColors.Accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                leadingIcon = option.iconRes?.let { iconRes ->
                    {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = if (isSelected) MediaNestColors.Accent else MediaNestColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_check),
                            contentDescription = "Selected",
                            tint = MediaNestColors.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                onClick = {
                    val newAsc = if (isSelected) !isAscending else false
                    onSortSelected(option.id, newAsc)
                    onDismissRequest()
                }
            )
        }
    }
}
