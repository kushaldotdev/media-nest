package com.example.medianest.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.medianest.R
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestTheme
import com.example.medianest.ui.utils.UiUtils

/**
 * Data item representing a single action entry in [VideoActionBottomSheet].
 *
 * @param id Unique identifier string for the action (e.g., "play", "favorite", "download", "delete").
 * @param label Human-readable label displayed next to the icon.
 * @param iconRes Vector drawable resource ID for the action icon.
 * @param destructive True if the action is destructive (renders with [MediaNestColors.Destructive] red tint).
 * @param active True if the action is in an active/highlighted state (renders with [MediaNestColors.Accent] tint).
 */
data class VideoActionItem(
    val id: String,
    val label: String,
    @DrawableRes val iconRes: Int,
    val destructive: Boolean = false,
    val active: Boolean = false
)

/**
 * Bottom sheet content displaying the video summary header and a list of actionable rows.
 */
@Composable
fun VideoActionBottomSheetContent(
    title: String,
    channelName: String,
    thumbnailUrl: String?,
    durationSeconds: Long,
    actions: List<VideoActionItem>,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 28.dp)
    ) {
        // Video Header Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MediaNestColors.Card),
                contentAlignment = Alignment.Center
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_video),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MediaNestColors.TextSecondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = MediaNestColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                if (channelName.isNotBlank()) {
                    Text(
                        text = channelName,
                        color = MediaNestColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (durationSeconds > 0) {
                    Text(
                        text = UiUtils.formatDuration(durationSeconds),
                        color = MediaNestColors.TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MediaNestColors.Border,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Action Rows List
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            actions.forEach { action ->
                val tint = when {
                    action.destructive -> MediaNestColors.Destructive
                    action.active -> MediaNestColors.Accent
                    else -> MediaNestColors.TextPrimary
                }
                val interactionSource = remember { MutableInteractionSource() }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            role = Role.Button,
                            onClick = { onAction(action.id) }
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(action.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = tint
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = action.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = tint
                    )
                }
            }
        }
    }
}

/**
 * Reusable modal bottom sheet for video actions per design-2 specification §5.8.
 * Provides the canonical action list triggerable from 3-dot menus across cards.
 *
 * @param title Video title displayed in header.
 * @param channelName Channel/author name displayed in header.
 * @param thumbnailUrl URL for video thumbnail image.
 * @param durationSeconds Video length in seconds for duration badge.
 * @param actions List of [VideoActionItem] actions available for the video.
 * @param onAction Callback invoked when an action is selected with its id string.
 * @param onDismiss Callback invoked when sheet is dismissed.
 * @param modifier Root layout modifier.
 * @param sheetState Bottom sheet state controller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionBottomSheet(
    title: String,
    channelName: String,
    thumbnailUrl: String?,
    durationSeconds: Long,
    actions: List<VideoActionItem>,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
        VideoActionBottomSheetContent(
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            actions = actions,
            onAction = onAction
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun VideoActionBottomSheetPreview() {
    MediaNestTheme {
        Surface(
            color = MediaNestColors.Raised,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            VideoActionBottomSheetContent(
                title = "Rick Astley - Never Gonna Give You Up (Official Music Video)",
                channelName = "Rick Astley",
                thumbnailUrl = null,
                durationSeconds = 213,
                actions = listOf(
                    VideoActionItem(id = "play", label = "Play Video", iconRes = R.drawable.ic_mn_play),
                    VideoActionItem(id = "folder", label = "Add to / Move to Folder", iconRes = R.drawable.ic_mn_folder),
                    VideoActionItem(id = "download", label = "Download / Quality Options", iconRes = R.drawable.ic_mn_download),
                    VideoActionItem(id = "favorite", label = "Favorite", iconRes = R.drawable.ic_mn_heart_filled, active = true),
                    VideoActionItem(id = "delete", label = "Delete / Remove Video", iconRes = R.drawable.ic_mn_trash, destructive = true)
                ),
                onAction = {}
            )
        }
    }
}
