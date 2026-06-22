package com.example.medianest.ui.navigation

import androidx.compose.runtime.Composable
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
import com.example.medianest.ui.screens.SettingsScreen
import com.example.medianest.ui.screens.VideoDetailScreen
import com.example.medianest.ui.viewmodel.HomeViewModel

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Home.route,
        modifier = modifier
    ) {
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
                VideoDetailScreen(
                    videoInfo = videoInfo,
                    onPlay = { /* Phase 3 — playback */ },
                    onBack = { navController.popBackStack() }
                )
            } else {
                navController.popBackStack()
            }
        }
        composable(BottomNavItem.Downloads.route) { DownloadsScreen() }
        composable(BottomNavItem.Library.route) { LibraryScreen() }
        composable(BottomNavItem.Settings.route) { SettingsScreen() }
    }
}
