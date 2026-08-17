package com.example.medianest.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestTheme

/**
 * Data item representing a destination in the MediaNest bottom navigation bar.
 */
data class MediaNestNavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badgeCount: Int? = null
)

/**
 * The 4 main bottom navigation destinations for MediaNest per Design 2.0.
 */
object MediaNestBottomNavDefaults {
    val Destinations: List<MediaNestNavigationItem> = listOf(
        MediaNestNavigationItem(
            route = "home",
            label = "Home",
            icon = Icons.Outlined.Home,
            selectedIcon = Icons.Filled.Home
        ),
        MediaNestNavigationItem(
            route = "library",
            label = "Library",
            icon = Icons.Outlined.VideoLibrary,
            selectedIcon = Icons.Filled.VideoLibrary
        ),
        MediaNestNavigationItem(
            route = "downloads",
            label = "Downloads",
            icon = Icons.Outlined.Download,
            selectedIcon = Icons.Filled.Download
        ),
        MediaNestNavigationItem(
            route = "settings",
            label = "Settings",
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Filled.Settings
        )
    )
}

/**
 * Bottom Navigation Bar designed strictly against MediaNest Design 2.0 specifications.
 *
 * Characteristics:
 * - Background: [MediaNestColors.NavigationBar] (#241417)
 * - Top border: [MediaNestColors.Border] (#4B2B33)
 * - Active pill indicator: [MediaNestColors.NavigationActive] (#682B38)
 * - Active icon & text: [MediaNestColors.Accent] (#FFB1B6)
 * - Inactive icon & text: [MediaNestColors.TextSecondary] (#C8AEB4)
 * - 4 main destinations: Home, Library, Downloads, Settings
 */
@Composable
fun MediaNestBottomNavigation(
    currentRoute: String?,
    onNavigate: (route: String) -> Unit,
    modifier: Modifier = Modifier,
    items: List<MediaNestNavigationItem> = MediaNestBottomNavDefaults.Destinations,
    badgeCounts: Map<String, Int> = emptyMap(),
    barHeight: Dp = 64.dp,
    windowInsets: WindowInsets = WindowInsets.navigationBars
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MediaNestColors.NavigationBar)
            .windowInsetsPadding(windowInsets)
    ) {
        // Top 1px border divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MediaNestColors.Border)
        )

        // Navigation items row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = when {
                    currentRoute == null -> false
                    currentRoute == item.route -> true
                    // Support matching routes with query arguments (e.g. "library" vs "collections")
                    currentRoute.substringBefore("?").substringBefore("/") == item.route -> true
                    item.route == "library" && currentRoute.startsWith("collections") -> true
                    else -> false
                }

                val badge = badgeCounts[item.route] ?: item.badgeCount

                MediaNestBottomNavigationItemView(
                    item = item,
                    selected = isSelected,
                    badgeCount = badge,
                    onClick = {
                        if (!isSelected) {
                            onNavigate(item.route)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual Navigation Item composable with active pill indicator and top active bar.
 */
@Composable
fun MediaNestBottomNavigationItemView(
    item: MediaNestNavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) MediaNestColors.Accent else MediaNestColors.TextSecondary,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "navIconColor"
    )

    val labelColor by animateColorAsState(
        targetValue = if (selected) MediaNestColors.Accent else MediaNestColors.TextSecondary,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "navLabelColor"
    )

    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "navPillAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "navIconScale"
    )

    Column(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, radius = 28.dp),
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Top active indicator bar (Design 2.0 CSS .mn-nav__item__indicator)
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(3.dp)
                .graphicsLayer { alpha = pillAlpha }
                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                .background(MediaNestColors.Accent)
        )

        Spacer(Modifier.height(3.dp))

        // Icon Container with Pill Indicator
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MediaNestColors.NavigationActive.copy(alpha = pillAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )

            // Badge Counter Pill
            if (badgeCount != null && badgeCount > 0) {
                val displayCount = if (badgeCount > 99) "99+" else badgeCount.toString()
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-3).dp)
                        .widthIn(min = 16.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MediaNestColors.Destructive)
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayCount,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 9.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        // Text Label
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.2.sp
            ),
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -----------------------------------------------------------------------------
// Previews
// -----------------------------------------------------------------------------

@Preview(name = "Bottom Navigation - Home Selected", showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun MediaNestBottomNavigationHomePreview() {
    MediaNestTheme {
        MediaNestBottomNavigation(
            currentRoute = "home",
            onNavigate = {},
            badgeCounts = mapOf("downloads" to 2)
        )
    }
}

@Preview(name = "Bottom Navigation - Downloads Selected", showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun MediaNestBottomNavigationDownloadsPreview() {
    MediaNestTheme {
        MediaNestBottomNavigation(
            currentRoute = "downloads",
            onNavigate = {},
            badgeCounts = mapOf("downloads" to 4)
        )
    }
}
