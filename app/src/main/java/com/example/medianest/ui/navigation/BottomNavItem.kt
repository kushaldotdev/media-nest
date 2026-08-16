package com.example.medianest.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : BottomNavItem("home", "Home", Icons.Default.Home)
    data object Downloads : BottomNavItem("downloads", "Downloads", Icons.Default.Download)
    data object Collections : BottomNavItem("collections", "Collections", Icons.Default.Collections)
    data object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)
}
