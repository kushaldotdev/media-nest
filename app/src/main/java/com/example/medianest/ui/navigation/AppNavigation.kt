package com.example.medianest.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.medianest.ui.screens.DownloadsScreen
import com.example.medianest.ui.screens.HomeScreen
import com.example.medianest.ui.screens.LibraryScreen
import com.example.medianest.ui.screens.SettingsScreen

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Home.route,
        modifier = modifier
    ) {
        composable(BottomNavItem.Home.route) { HomeScreen() }
        composable(BottomNavItem.Downloads.route) { DownloadsScreen() }
        composable(BottomNavItem.Library.route) { LibraryScreen() }
        composable(BottomNavItem.Settings.route) { SettingsScreen() }
    }
}
