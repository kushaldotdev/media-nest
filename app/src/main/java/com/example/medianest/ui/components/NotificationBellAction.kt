package com.example.medianest.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medianest.R
import com.example.medianest.ui.viewmodel.NotificationsViewModel

/**
 * Reusable TopAppBar action composable displaying a notification bell icon with an unread count badge.
 */
@Composable
fun NotificationBellAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: NotificationsViewModel = hiltViewModel()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()

    MediaNestAppBarAction(
        painter = painterResource(R.drawable.ic_mn_bell),
        contentDescription = "Notifications",
        onClick = onClick,
        badgeCount = unreadCount,
        modifier = modifier
    )
}
