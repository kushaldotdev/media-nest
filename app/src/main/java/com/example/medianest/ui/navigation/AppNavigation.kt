package com.example.medianest.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.ui.screens.DownloadsScreen
import com.example.medianest.ui.screens.HomeScreen
import com.example.medianest.ui.screens.LibraryScreen
import com.example.medianest.ui.screens.NotificationsScreen
import com.example.medianest.ui.screens.PlayerQueueItem
import com.example.medianest.ui.screens.PlayerScreen
import com.example.medianest.ui.screens.SettingsScreen
import com.example.medianest.ui.screens.SubscriptionsScreen
import com.example.medianest.ui.screens.VideoDetailScreen
import com.example.medianest.ui.screens.StatisticsScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.medianest.ui.viewmodel.HomeViewModel
import com.example.medianest.ui.viewmodel.PlayerViewModel
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

private fun Context.findActivity(): ComponentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is ComponentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

object NavigationRoutes {
    const val PLAYER_ONLINE = "player/{videoId}?streamIndex={streamIndex}"
    const val PLAYER_OFFLINE = "downloads/player/{videoId}?downloadId={downloadId}"
    const val VIDEO_DETAIL = "videoDetail/{videoId}"
    const val STATISTICS = "statistics"
    const val NOTIFICATIONS = "notifications"
}


@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Home.route,
        modifier = modifier
    ) {
        composable(
            route = NavigationRoutes.PLAYER_ONLINE,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType },
                navArgument("streamIndex") { type = NavType.IntType; defaultValue = 0 }
            ),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(450))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(450))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(450))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(450))
            }
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val streamIndex = backStackEntry.arguments?.getInt("streamIndex") ?: 0
            val context = LocalContext.current
            val activity = context.findActivity() ?: error("Activity not found")
            val playerViewModel: PlayerViewModel = hiltViewModel(activity)
            val queue by playerViewModel.queue.collectAsStateWithLifecycle()
            val ctxTitle by playerViewModel.queueContextTitle.collectAsStateWithLifecycle()
            val ctxType by playerViewModel.queueContextType.collectAsStateWithLifecycle()
            PlayerScreen(
                videoId = videoId,
                streamIndex = streamIndex,
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                queue = queue,
                contextTitle = ctxTitle,
                contextType = ctxType
            )
        }
        composable(
            route = BottomNavItem.Home.route + "?url={url}",
            arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null }),
            exitTransition = {
                if (targetState.destination.route?.contains("player") == true) {
                    fadeOut(animationSpec = tween(450), targetAlpha = 0.9f)
                } else {
                    null
                }
            },
            popEnterTransition = {
                if (initialState.destination.route?.contains("player") == true) {
                    fadeIn(animationSpec = tween(450), initialAlpha = 0.9f)
                } else {
                    null
                }
            }
        ) { backStackEntry ->
            val homeViewModel: HomeViewModel = hiltViewModel()
            val urlToLoad = backStackEntry.arguments?.getString("url")
            
            LaunchedEffect(urlToLoad) {
                if (!urlToLoad.isNullOrEmpty()) {
                    homeViewModel.onUrlSubmitted(java.net.URLDecoder.decode(urlToLoad, "UTF-8"))
                }
            }

            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                HomeScreen(
                    onVideoSelected = { videoId ->
                        navController.navigate("videoDetail/$videoId")
                    },
                    onSubscribe = { sourceType, sourceId, name, thumbnailUrl ->
                        homeViewModel.subscribe(sourceType, sourceId, name, thumbnailUrl)
                    },
                    onNavigateToNotifications = { navController.navigate(NavigationRoutes.NOTIFICATIONS) }
                )
            }
        }
        composable(
            route = NavigationRoutes.VIDEO_DETAIL,
            arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
            exitTransition = {
                if (targetState.destination.route?.contains("player") == true) {
                    fadeOut(animationSpec = tween(450), targetAlpha = 0.9f)
                } else {
                    null
                }
            },
            popEnterTransition = {
                if (initialState.destination.route?.contains("player") == true) {
                    fadeIn(animationSpec = tween(450), initialAlpha = 0.9f)
                } else {
                    null
                }
            }
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val detailViewModel: com.example.medianest.ui.viewmodel.VideoDetailViewModel = hiltViewModel()

            LaunchedEffect(videoId) {
                detailViewModel.loadVideoInfo(videoId)
                detailViewModel.loadFavorite(videoId)
            }

            val videoInfo by detailViewModel.videoInfo.collectAsStateWithLifecycle()
            val downloads by detailViewModel.videoDownloads.collectAsStateWithLifecycle()

            LaunchedEffect(videoInfo) {
                val info = videoInfo ?: return@LaunchedEffect
                detailViewModel.initSubscription(info.channelId ?: "", info.channelName, info.thumbnailUrl)
            }

            val isFavorite by detailViewModel.isFavorite.collectAsStateWithLifecycle()
            val isSubscribed by detailViewModel.isSubscribed.collectAsStateWithLifecycle()

            val videoHistory by detailViewModel.videoHistory.collectAsStateWithLifecycle()
            val watchSessions by detailViewModel.watchSessions.collectAsStateWithLifecycle()
            val localVideo by detailViewModel.localVideo.collectAsStateWithLifecycle()
            val isFetchingOnline by detailViewModel.isFetchingOnline.collectAsStateWithLifecycle()

            val info = videoInfo
            if (info != null) {
                Box(modifier = Modifier.padding(bottom = 80.dp)) {
                    VideoDetailScreen(
                        videoInfo = info,
                        localVideo = localVideo,
                        downloads = downloads,
                        isFavorite = isFavorite,
                        isSubscribed = isSubscribed,
                        videoHistory = videoHistory,
                        watchSessions = watchSessions,
                        isFetchingOnline = isFetchingOnline,
                        onSubscribe = { detailViewModel.toggleSubscription() },
                        onToggleFavorite = { detailViewModel.toggleFavorite() },
                        onRefresh = { detailViewModel.loadVideoInfo(videoId, forceRefresh = true) },
                        onPlay = { stream ->
                            val streamIndex = info.streamSources.indexOf(stream)
                            navController.navigate("player/$videoId?streamIndex=$streamIndex")
                        },
                        onPlayDownload = { download ->
                            navController.navigate("downloads/player/${download.videoId}?downloadId=${download.id}")
                        },
                        onDeleteDownload = { download, deleteFile -> detailViewModel.deleteDownload(download, deleteFile) },
                        onDownload = { stream ->
                            detailViewModel.enqueueDownload(info, stream)
                        },
                        onBack = { navController.popBackStack() },
                        onResetWatchPosition = { detailViewModel.resetPlaybackPosition() },
                        onMarkWatched = { count -> detailViewModel.updateWatchCount(count) }
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        composable(
            route = BottomNavItem.Downloads.route,
            exitTransition = {
                if (targetState.destination.route?.contains("player") == true) {
                    fadeOut(animationSpec = tween(450), targetAlpha = 0.9f)
                } else {
                    null
                }
            },
            popEnterTransition = {
                if (initialState.destination.route?.contains("player") == true) {
                    fadeIn(animationSpec = tween(450), initialAlpha = 0.9f)
                } else {
                    null
                }
            }
        ) {
            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                DownloadsScreen(
                    onPlayDownload = { download ->
                        navController.navigate("downloads/player/${download.videoId}?downloadId=${download.id}")
                    },
                    onVideoClick = { videoId ->
                        navController.navigate("videoDetail/$videoId")
                    },
                    onNavigateToNotifications = { navController.navigate(NavigationRoutes.NOTIFICATIONS) }
                )
            }
        }
        composable(
            route = NavigationRoutes.PLAYER_OFFLINE,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType },
                navArgument("downloadId") { type = NavType.LongType; defaultValue = -1L }
            ),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(450))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(450))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(450))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(450))
            }
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val downloadIdArg = backStackEntry.arguments?.getLong("downloadId") ?: -1L
            val downloadId = if (downloadIdArg == -1L) null else downloadIdArg
            val context = LocalContext.current
            val activity = context.findActivity() ?: error("Activity not found")
            val playerViewModel: PlayerViewModel = hiltViewModel(activity)
            val queue by playerViewModel.queue.collectAsStateWithLifecycle()
            val ctxTitle by playerViewModel.queueContextTitle.collectAsStateWithLifecycle()
            val ctxType by playerViewModel.queueContextType.collectAsStateWithLifecycle()
            PlayerScreen(
                videoId = videoId,
                streamIndex = 0,
                downloadId = downloadId,
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                queue = queue,
                contextTitle = ctxTitle,
                contextType = ctxType
            )
        }
        composable(
            route = BottomNavItem.Collections.route,
            exitTransition = {
                if (targetState.destination.route?.contains("player") == true) {
                    fadeOut(animationSpec = tween(450), targetAlpha = 0.9f)
                } else {
                    null
                }
            },
            popEnterTransition = {
                if (initialState.destination.route?.contains("player") == true) {
                    fadeIn(animationSpec = tween(450), initialAlpha = 0.9f)
                } else {
                    null
                }
            }
        ) {
            val context = LocalContext.current
            val activity = context.findActivity() ?: return@composable
            val playerViewModel: PlayerViewModel = hiltViewModel(activity)

            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                LibraryScreen(
                    onVideoClick = { videoId ->
                        navController.navigate("videoDetail/$videoId")
                    },
                    onSubscriptionClick = { _, _ -> },
                    onNavigateToStatistics = {
                        navController.navigate(NavigationRoutes.STATISTICS)
                    },
                    onNavigateToNotifications = { navController.navigate(NavigationRoutes.NOTIFICATIONS) },
                    onPlayFromList = { videos, startIndex ->
                        if (videos.isEmpty()) return@LibraryScreen
                        val startIdx = startIndex.coerceIn(0, videos.size - 1)
                        val start = videos[startIdx]
                        playerViewModel.setQueue(
                            videos.map { PlayerQueueItem(id = it.videoId, title = it.title, channelName = it.channelName, durationSeconds = it.durationSeconds, thumbnailUrl = it.thumbnailUrl) },
                            startVideoId = start.videoId,
                            contextTitle = null,
                            contextType = "playlist"
                        )
                        navController.navigate("player/${start.videoId}?streamIndex=0")
                    }
                )
            }
        }
        composable(
            route = BottomNavItem.Settings.route,
            exitTransition = {
                if (targetState.destination.route?.contains("player") == true) {
                    fadeOut(animationSpec = tween(450), targetAlpha = 0.9f)
                } else {
                    null
                }
            },
            popEnterTransition = {
                if (initialState.destination.route?.contains("player") == true) {
                    fadeIn(animationSpec = tween(450), initialAlpha = 0.9f)
                } else {
                    null
                }
            }
        ) { 
            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                SettingsScreen(
                    onNavigateToStatistics = { navController.navigate(NavigationRoutes.STATISTICS) },
                    onNavigateToNotifications = { navController.navigate(NavigationRoutes.NOTIFICATIONS) }
                ) 
            }
        }
        composable(
            route = NavigationRoutes.STATISTICS,
            exitTransition = {
                if (targetState.destination.route?.contains("player") == true) {
                    fadeOut(animationSpec = tween(450), targetAlpha = 0.9f)
                } else {
                    null
                }
            },
            popEnterTransition = {
                if (initialState.destination.route?.contains("player") == true) {
                    fadeIn(animationSpec = tween(450), initialAlpha = 0.9f)
                } else {
                    null
                }
            }
        ) {
            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                StatisticsScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(
            route = NavigationRoutes.NOTIFICATIONS,
            exitTransition = {
                if (targetState.destination.route?.contains("player") == true) {
                    fadeOut(animationSpec = tween(450), targetAlpha = 0.9f)
                } else {
                    null
                }
            },
            popEnterTransition = {
                if (initialState.destination.route?.contains("player") == true) {
                    fadeIn(animationSpec = tween(450), initialAlpha = 0.9f)
                } else {
                    null
                }
            }
        ) {
            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToVideo = { videoId -> navController.navigate("videoDetail/$videoId") },
                    onNavigateToDownloads = { navController.navigate(BottomNavItem.Downloads.route) }
                )
            }
        }
    }
}
