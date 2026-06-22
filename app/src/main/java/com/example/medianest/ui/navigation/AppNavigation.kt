package com.example.medianest.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Home.route,
        modifier = modifier
    ) {
        composable(
            route = "player/{videoId}?streamIndex={streamIndex}",
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
            HomeScreen(
                onVideoSelected = { videoId ->
                    navController.navigate("videoDetail/$videoId")
                }
            )
        }
        composable(
            route = "videoDetail/{videoId}",
            arguments = listOf(navArgument("videoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val videoInfo = remember { HomeViewModel.lastResultCache[videoId] }
            if (videoInfo != null) {
                val detailViewModel: com.example.medianest.ui.viewmodel.VideoDetailViewModel = hiltViewModel()
                LaunchedEffect(videoId) { detailViewModel.loadFavorite(videoId) }
                LaunchedEffect(videoInfo) {
                    detailViewModel.initSubscription(videoInfo.channelId ?: "", videoInfo.channelName, videoInfo.thumbnailUrl)
                    detailViewModel.checkSubscription()
                }
                val isFavorite by detailViewModel.isFavorite.collectAsState()
                val isSubscribed by detailViewModel.isSubscribed.collectAsState()
                VideoDetailScreen(
                    videoInfo = videoInfo,
                    isFavorite = isFavorite,
                    isSubscribed = isSubscribed,
                    onSubscribe = { detailViewModel.toggleSubscription() },
                    onToggleFavorite = { detailViewModel.toggleFavorite() },
                    onPlay = { stream ->
                        val streamIndex = videoInfo.streamSources.indexOf(stream)
                        navController.navigate("player/$videoId?streamIndex=$streamIndex")
                    },
                    onDownload = { stream ->
                        detailViewModel.enqueueDownload(videoInfo, stream)
                    },
                    onBack = { navController.popBackStack() }
                )
            } else {
                navController.popBackStack()
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
            route = "downloads/player/{videoId}",
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
