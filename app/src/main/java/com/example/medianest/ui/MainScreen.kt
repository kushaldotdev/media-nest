package com.example.medianest.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.medianest.ui.navigation.AppNavigation
import com.example.medianest.ui.navigation.BottomNavItem
import com.example.medianest.ui.theme.MediaNestTheme

import androidx.compose.runtime.LaunchedEffect
import com.example.medianest.ui.viewmodel.PendingRestartConfirmation
import com.example.medianest.ui.navigation.NavigationRoutes

import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    MediaNestTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

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
    }
}
