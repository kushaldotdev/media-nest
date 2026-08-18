package com.example.medianest.ui.navigation

import androidx.annotation.DrawableRes
import com.example.medianest.R

sealed class BottomNavItem(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int
) {
    data object Home : BottomNavItem("home", "Home", R.drawable.ic_mn_home)
    data object Downloads : BottomNavItem("downloads", "Downloads", R.drawable.ic_mn_download)
    data object Collections : BottomNavItem("collections", "Collections", R.drawable.ic_mn_library)
    data object Settings : BottomNavItem("settings", "Settings", R.drawable.ic_mn_settings)
}
