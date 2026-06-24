package com.example.medianest.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.medianest.ui.screens.DownloadsScreen
import com.example.medianest.ui.screens.HomeScreen
import com.example.medianest.ui.screens.LibraryScreen
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
    const val PLAYER_OFFLINE = "downloads/player/{videoId}"
    const val VIDEO_DETAIL = "videoDetail/{videoId}"
    const val STATISTICS = "statistics"
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
            )
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val streamIndex = backStackEntry.arguments?.getInt("streamIndex") ?: 0
            val context = LocalContext.current
            val activity = context.findActivity() ?: error("Activity not found")
            val playerViewModel: PlayerViewModel = hiltViewModel(activity)
            PlayerScreen(
                videoId = videoId,
                streamIndex = streamIndex,
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = BottomNavItem.Home.route + "?url={url}",
            arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val homeViewModel: HomeViewModel = hiltViewModel()
            val urlToLoad = backStackEntry.arguments?.getString("url")
            
            LaunchedEffect(urlToLoad) {
                if (!urlToLoad.isNullOrEmpty()) {
                    homeViewModel.onUrlSubmitted(java.net.URLDecoder.decode(urlToLoad, "UTF-8"))
                }
            }

            HomeScreen(
                onVideoSelected = { videoId ->
                    navController.navigate("videoDetail/$videoId")
                },
                onSubscribe = { sourceType, sourceId, name, thumbnailUrl ->
                    homeViewModel.subscribe(sourceType, sourceId, name, thumbnailUrl)
                }
            )
        }
        composable(
            route = NavigationRoutes.VIDEO_DETAIL,
            arguments = listOf(navArgument("videoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val detailViewModel: com.example.medianest.ui.viewmodel.VideoDetailViewModel = hiltViewModel()

            LaunchedEffect(videoId) {
                detailViewModel.loadVideoInfo(videoId)
                detailViewModel.loadFavorite(videoId)
            }

            val videoInfo by detailViewModel.videoInfo.collectAsState()
            val downloads by detailViewModel.videoDownloads.collectAsState()

            LaunchedEffect(videoInfo) {
                val info = videoInfo ?: return@LaunchedEffect
                detailViewModel.initSubscription(info.channelId ?: "", info.channelName, info.thumbnailUrl)
                detailViewModel.checkSubscription()
            }

            val isFavorite by detailViewModel.isFavorite.collectAsState()
            val isSubscribed by detailViewModel.isSubscribed.collectAsState()

            val videoHistory by detailViewModel.videoHistory.collectAsState()
            val watchSessions by detailViewModel.watchSessions.collectAsState()
            val localVideo by detailViewModel.localVideo.collectAsState()

            val info = videoInfo
            if (info != null) {
                VideoDetailScreen(
                    videoInfo = info,
                    localVideo = localVideo,
                    downloads = downloads,
                    isFavorite = isFavorite,
                    isSubscribed = isSubscribed,
                    videoHistory = videoHistory,
                    watchSessions = watchSessions,
                    onSubscribe = { detailViewModel.toggleSubscription() },
                    onToggleFavorite = { detailViewModel.toggleFavorite() },
                    onPlay = { stream ->
                        val streamIndex = info.streamSources.indexOf(stream)
                        navController.navigate("player/$videoId?streamIndex=$streamIndex")
                    },
                    onDownload = { stream ->
                        detailViewModel.enqueueDownload(info, stream)
                    },
                    onBack = { navController.popBackStack() }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        composable(BottomNavItem.Downloads.route) {
            DownloadsScreen(
                onPlayDownload = { download ->
                    navController.navigate("downloads/player/${download.videoId}")
                },
                onVideoClick = { videoId ->
                    navController.navigate("videoDetail/$videoId")
                }
            )
        }
        composable(
            route = NavigationRoutes.PLAYER_OFFLINE,
            arguments = listOf(navArgument("videoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val context = LocalContext.current
            val activity = context.findActivity() ?: error("Activity not found")
            val playerViewModel: PlayerViewModel = hiltViewModel(activity)
            PlayerScreen(
                videoId = videoId,
                streamIndex = 0,
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(BottomNavItem.Library.route) {
            LibraryScreen(
                onVideoClick = { videoId ->
                    navController.navigate("videoDetail/$videoId")
                },
                onSubscriptionClick = { type, id ->
                    var url = if (id.startsWith("http")) {
                        id
                    } else if (id.contains("youtube.com")) {
                        if (id.startsWith("//")) "https:$id" else "https://$id"
                    } else if (type == "playlist") {
                        val cleanId = id.substringAfter("list=")
                        "https://www.youtube.com/playlist?list=$cleanId"
                    } else if (id.startsWith("@")) {
                        "https://www.youtube.com/$id"
                    } else {
                        val cleanId = id.removePrefix("/").removePrefix("channel/").removePrefix("c/")
                        "https://www.youtube.com/channel/$cleanId"
                    }
                    if (type != "playlist") {
                        val cleanUrl = url.trim().removeSuffix("/")
                        if (!cleanUrl.endsWith("/videos")) {
                            url = "$cleanUrl/videos"
                        }
                    }
                    navController.navigate(BottomNavItem.Home.route + "?url=${java.net.URLEncoder.encode(url, "UTF-8")}") {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                }
            )
        }
        composable(BottomNavItem.Settings.route) { 
            SettingsScreen(
                onNavigateToStatistics = { navController.navigate(NavigationRoutes.STATISTICS) }
            ) 
        }
        composable(NavigationRoutes.STATISTICS) {
            StatisticsScreen(onBack = { navController.popBackStack() })
        }
    }
}
