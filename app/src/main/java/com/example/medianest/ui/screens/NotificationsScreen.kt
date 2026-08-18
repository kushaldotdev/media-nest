package com.example.medianest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.medianest.R
import com.example.medianest.data.local.entity.AppNotificationEntity
import com.example.medianest.ui.components.EmptyState
import com.example.medianest.ui.components.EndOfListIndicator
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.components.MediaNestButton
import com.example.medianest.ui.components.MediaNestButtonSize
import com.example.medianest.ui.components.MediaNestButtonVariant
import com.example.medianest.ui.components.MediaNestChip
import com.example.medianest.ui.components.MediaNestFilterRow
import com.example.medianest.ui.components.MediaNestTopAppBar
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.utils.UiUtils
import com.example.medianest.ui.viewmodel.NotificationsViewModel
import java.util.Date

private data class FilterOption(val key: String, val label: String)

private val FILTER_OPTIONS = listOf(
    FilterOption("ALL", "All"),
    FilterOption(AppNotificationEntity.TYPE_DOWNLOAD, "Downloads"),
    FilterOption(AppNotificationEntity.TYPE_SUBSCRIPTION, "Subscriptions"),
    FilterOption(AppNotificationEntity.TYPE_SYSTEM, "System"),
    FilterOption(AppNotificationEntity.TYPE_SYNC, "Sync")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToVideo: ((String) -> Unit)? = null,
    onNavigateToDownloads: (() -> Unit)? = null,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val unreadCount by viewModel.unreadCount.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val notifications by viewModel.filteredNotifications.collectAsState()

    var visibleCount by remember { mutableIntStateOf(10) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Reset pagination batch when filter changes
    LaunchedEffect(selectedFilter) {
        visibleCount = 10
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = MediaNestColors.Raised,
            title = {
                Text(
                    text = "Clear all notifications?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MediaNestColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to clear all notifications? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediaNestColors.TextSecondary
                )
            },
            confirmButton = {
                MediaNestButton(
                    text = "Clear All",
                    variant = MediaNestButtonVariant.Danger,
                    size = MediaNestButtonSize.Small,
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    }
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        color = MediaNestColors.TextSecondary
                    )
                }
            }
        )
    }

    Scaffold(
        topBar = {
            MediaNestTopAppBar(
                title = "Notifications",
                subtitle = if (unreadCount > 0) "$unreadCount unread" else null,
                onNavigateBack = onBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.markAllAsRead() }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_check),
                            contentDescription = "Mark all read",
                            tint = MediaNestColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showClearDialog = true }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_trash),
                            contentDescription = "Clear all",
                            tint = MediaNestColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        },
        containerColor = MediaNestColors.Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Pills Row
            MediaNestFilterRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                FILTER_OPTIONS.forEach { option ->
                    MediaNestChip(
                        label = option.label,
                        selected = selectedFilter == option.key,
                        onClick = { viewModel.setFilter(option.key) }
                    )
                }
            }

            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "No notifications",
                        message = if (selectedFilter == "ALL") {
                            "Download completions, new uploads, sync and other events will appear here."
                        } else {
                            "No ${FILTER_OPTIONS.firstOrNull { it.key == selectedFilter }?.label ?: selectedFilter} notifications found."
                        },
                        iconContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_bell),
                                contentDescription = null,
                                tint = MediaNestColors.TextSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    )
                }
            } else {
                val displayedList = notifications.take(visibleCount)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Showing ${displayedList.size} of ${notifications.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediaNestColors.TextSecondary,
                                fontSize = 12.sp
                            )
                            if (unreadCount > 0) {
                                Text(
                                    text = "$unreadCount unread",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediaNestColors.Accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    items(
                        items = displayedList,
                        key = { it.id }
                    ) { notification ->
                        NotificationCard(
                            notification = notification,
                            onItemClick = {
                                viewModel.markAsRead(notification.id)
                                if (!notification.targetVideoId.isNullOrBlank()) {
                                    onNavigateToVideo?.invoke(notification.targetVideoId)
                                } else if (notification.targetDownloadId != null || notification.type == AppNotificationEntity.TYPE_DOWNLOAD) {
                                    onNavigateToDownloads?.invoke()
                                }
                            }
                        )
                    }

                    if (notifications.size > visibleCount) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                MediaNestButton(
                                    text = "Show more (${notifications.size - visibleCount} remaining)",
                                    variant = MediaNestButtonVariant.Secondary,
                                    size = MediaNestButtonSize.Small,
                                    onClick = { visibleCount += 10 }
                                )
                            }
                        }
                    } else {
                        item {
                            EndOfListIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotificationEntity,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconRes = when (notification.type) {
        AppNotificationEntity.TYPE_DOWNLOAD -> R.drawable.ic_mn_download
        AppNotificationEntity.TYPE_SUBSCRIPTION -> R.drawable.ic_mn_channel
        AppNotificationEntity.TYPE_SYNC -> R.drawable.ic_mn_cloud
        AppNotificationEntity.TYPE_SYSTEM -> R.drawable.ic_mn_info
        else -> R.drawable.ic_mn_info
    }

    val iconTint = when (notification.type) {
        AppNotificationEntity.TYPE_DOWNLOAD -> MediaNestColors.Accent
        AppNotificationEntity.TYPE_SUBSCRIPTION -> MediaNestColors.Accent
        AppNotificationEntity.TYPE_SYNC -> MediaNestColors.Success
        AppNotificationEntity.TYPE_SYSTEM -> MediaNestColors.TextSecondary
        else -> MediaNestColors.TextSecondary
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onItemClick,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Event Type Icon Container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MediaNestColors.Raised)
                    .border(1.dp, MediaNestColors.Border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Notification Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = MediaNestColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(Modifier.size(8.dp))

                    Text(
                        text = UiUtils.formatRelativeTime(Date(notification.timestamp)),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp
                        ),
                        color = MediaNestColors.TextSecondary
                    )
                }

                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = MediaNestColors.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (!notification.targetVideoId.isNullOrBlank() || notification.targetDownloadId != null) {
                    Text(
                        text = "Tap to view →",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MediaNestColors.Accent,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Unread Dot Indicator
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MediaNestColors.Accent)
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }
}
