package com.example.medianest.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.medianest.ui.navigation.AppNavigation
import com.example.medianest.ui.navigation.BottomNavItem
import com.example.medianest.ui.navigation.NavigationRoutes
import com.example.medianest.ui.theme.MediaNestTheme
import com.example.medianest.ui.viewmodel.PendingRestartConfirmation
import com.example.medianest.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun android.content.Context.findActivity(): androidx.activity.ComponentActivity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is androidx.activity.ComponentActivity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun MainScreen() {
    MediaNestTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        val context = LocalContext.current
        val activity = context.findActivity() ?: error("Activity not found")
        val playerViewModel: PlayerViewModel = hiltViewModel(activity)
        val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            launch {
                PendingRestartConfirmation.pendingDownloadId.collect { id ->
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute != BottomNavItem.Downloads.route) {
                        navController.navigate(BottomNavItem.Downloads.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                }
            }
            launch {
                PendingRestartConfirmation.navigateToDownloads.collect {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute != BottomNavItem.Downloads.route) {
                        navController.navigate(BottomNavItem.Downloads.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                }
            }
        }

        val showBottomBar = navBackStackEntry?.destination?.route?.let { route ->
            route != NavigationRoutes.PLAYER_ONLINE && route != NavigationRoutes.PLAYER_OFFLINE
        } ?: true

        val currentRoute = navBackStackEntry?.destination?.route
        val showMiniPlayer = playerUiState.videoId != null &&
                currentRoute != NavigationRoutes.PLAYER_ONLINE &&
                currentRoute != NavigationRoutes.PLAYER_OFFLINE

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val maxHeightPx = with(density) { maxHeight.toPx() }

            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            LaunchedEffect(playerUiState.videoId) {
                offsetX = 0f
                offsetY = 0f
            }

            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar {
                            listOf(
                                BottomNavItem.Home,
                                BottomNavItem.Downloads,
                                BottomNavItem.Library,
                                BottomNavItem.Settings
                            ).forEach { item ->
                                NavigationBarItem(
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false) },
                                    alwaysShowLabel = false,
                                    selected = currentDestination?.hierarchy?.any { it.route?.substringBefore("?") == item.route } == true,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = false
                                            }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                AppNavigation(
                    navController = navController,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            AnimatedVisibility(
                visible = showMiniPlayer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                var miniPlayerWidth by remember { mutableStateOf(0) }
                var miniPlayerHeight by remember { mutableStateOf(0) }
                val bottomPadding = if (showBottomBar) 88.dp else 16.dp

                MiniPlayer(
                    title = playerUiState.title,
                    channelName = playerUiState.channelName,
                    thumbnailUrl = playerUiState.thumbnailUrl,
                    isPlaying = playerUiState.isPlaying,
                    positionMs = playerUiState.positionMs,
                    durationMs = playerUiState.durationMs,
                    bufferedPositionMs = playerUiState.bufferedPositionMs,
                    onTogglePlay = { playerViewModel.togglePlayPause() },
                    onClose = { playerViewModel.stopPlayback() },
                    onClick = {
                        val route = if (playerUiState.isLocal) {
                            "downloads/player/${playerUiState.videoId}"
                        } else {
                            "player/${playerUiState.videoId}?streamIndex=${playerUiState.streamIndex}"
                        }
                        navController.navigate(route)
                    },
                    modifier = Modifier
                        .padding(bottom = bottomPadding, start = 16.dp, end = 16.dp)
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .onGloballyPositioned { coordinates ->
                            miniPlayerWidth = coordinates.size.width
                            miniPlayerHeight = coordinates.size.height
                        }
                        .pointerInput(playerUiState.videoId) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y

                                val defaultX = (maxWidthPx - miniPlayerWidth) / 2f
                                val bottomPaddingPx = with(density) { bottomPadding.toPx() }
                                val defaultY = maxHeightPx - miniPlayerHeight - bottomPaddingPx

                                offsetX = offsetX.coerceIn(-defaultX, maxWidthPx - miniPlayerWidth - defaultX)
                                offsetY = offsetY.coerceIn(-defaultY, maxHeightPx - miniPlayerHeight - defaultY)
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    title: String,
    channelName: String,
    thumbnailUrl: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .widthIn(max = 500.dp)
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.height(72.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }

            val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
            val bufferProgress = if (durationMs > 0) bufferedPositionMs.toFloat() / durationMs.toFloat() else 0f

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
            ) {
                LinearProgressIndicator(
                    progress = { bufferProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    trackColor = Color.Transparent
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
