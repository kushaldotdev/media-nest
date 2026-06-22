package com.example.medianest.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : BottomNavItem("home", "Home", Icons.Default.Home)
    data object Downloads : BottomNavItem("downloads", "Downloads", Icons.Default.Download)
    data object Library : BottomNavItem("library", "Library", Icons.Default.LibraryMusic)
    data object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)
}
