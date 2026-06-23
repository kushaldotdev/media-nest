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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.medianest.ui.viewmodel.HomeViewModel

object NavigationRoutes {
    const val PLAYER_ONLINE = "player/{videoId}?streamIndex={streamIndex}"
    const val PLAYER_OFFLINE = "downloads/player/{videoId}"
    const val VIDEO_DETAIL = "videoDetail/{videoId}"
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
            PlayerScreen(
                videoId = videoId,
                streamIndex = streamIndex,
                onBack = { navController.popBackStack() }
            )
        }
        composable(BottomNavItem.Home.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()
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

            LaunchedEffect(videoInfo) {
                val info = videoInfo ?: return@LaunchedEffect
                detailViewModel.initSubscription(info.channelId ?: "", info.channelName, info.thumbnailUrl)
                detailViewModel.checkSubscription()
            }

            val isFavorite by detailViewModel.isFavorite.collectAsState()
            val isSubscribed by detailViewModel.isSubscribed.collectAsState()

            val info = videoInfo
            if (info != null) {
                VideoDetailScreen(
                    videoInfo = info,
                    isFavorite = isFavorite,
                    isSubscribed = isSubscribed,
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
                }
            )
        }
        composable(
            route = NavigationRoutes.PLAYER_OFFLINE,
            arguments = listOf(navArgument("videoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            PlayerScreen(
                videoId = videoId,
                streamIndex = 0,
                onBack = { navController.popBackStack() }
            )
        }
        composable(BottomNavItem.Library.route) {
            LibraryScreen(
                onVideoClick = { videoId ->
                    navController.navigate("videoDetail/$videoId")
                }
            )
        }
        composable(BottomNavItem.Settings.route) { SettingsScreen() }
        composable(BottomNavItem.Subscriptions.route) {
            SubscriptionsScreen()
        }
    }
}
