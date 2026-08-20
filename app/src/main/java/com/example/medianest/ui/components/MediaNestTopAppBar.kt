package com.example.medianest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.example.medianest.R
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
 * Sticky MediaNest Top App Bar designed per Design 2.0 specs.
 *
 * Characteristics:
 * - Background: [MediaNestColors.Background] (#120B0E)
 * - 1px bottom border divider: [MediaNestColors.Border] (#4B2B33)
 * - Title: [MediaNestColors.TextPrimary] (#FFF7F8), SemiBold
 * - Subtitle: [MediaNestColors.TextSecondary] (#C8AEB4), Normal
 * - Built-in back button navigation icon support with rounded pill ripple
 * - Action slot with badge counter support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaNestTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    containerColor: Color = MediaNestColors.Background,
    borderColor: Color = MediaNestColors.Border,
    showBottomDivider: Boolean = true,
    height: Dp = 60.dp,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .windowInsetsPadding(windowInsets)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Navigation Icon / Back Button
            if (navigationIcon != null) {
                navigationIcon()
            } else if (onNavigateBack != null) {
                MediaNestBackButton(onClick = onNavigateBack)
            }

            // Title & Subtitle or Custom Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (navigationIcon != null || onNavigateBack != null) 4.dp else 8.dp,
                        end = 8.dp
                    )
            ) {
                if (titleContent != null) {
                    titleContent()
                } else {
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MediaNestColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!subtitle.isNullOrBlank()) {
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = MediaNestColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Action Icons Slot
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }

        // 1px Border Bottom Divider
        if (showBottomDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(borderColor)
            )
        }
    }
}

/**
 * Standard back button for MediaNest TopAppBar.
 */
@Composable
fun MediaNestBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
    tint: Color = MediaNestColors.TextPrimary
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mn_back),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Action icon button for TopAppBar with optional notification / item badge count.
 */
@Composable
fun MediaNestAppBarAction(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null,
    tint: Color = MediaNestColors.TextPrimary,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 20.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = if (enabled) tint else MediaNestColors.TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )

        if (badgeCount != null && badgeCount > 0) {
            MediaNestAppBarBadge(
                count = badgeCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-2).dp)
            )
        }
    }
}

/**
 * Badge counter pill for TopAppBar actions.
 */
@Composable
fun MediaNestAppBarBadge(
    count: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MediaNestColors.Destructive,
    textColor: Color = Color.White
) {
    val displayCount = if (count > 99) "99+" else count.toString()

    Box(
        modifier = modifier
            .widthIn(min = 18.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(backgroundColor)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayCount,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

// -----------------------------------------------------------------------------
// Previews
// -----------------------------------------------------------------------------

@Preview(name = "Standard Top App Bar", showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun MediaNestTopAppBarPreview() {
    MediaNestTheme {
        MediaNestTopAppBar(
            title = "Library",
            subtitle = "142 offline items",
            onNavigateBack = {},
            actions = {
                MediaNestAppBarAction(
                    painter = painterResource(R.drawable.ic_mn_search),
                    contentDescription = "Search",
                    onClick = {}
                )
                MediaNestAppBarAction(
                    painter = painterResource(R.drawable.ic_mn_bell),
                    contentDescription = "Notifications",
                    badgeCount = 3,
                    onClick = {}
                )
                MediaNestAppBarAction(
                    painter = painterResource(R.drawable.ic_mn_more),
                    contentDescription = "More",
                    onClick = {}
                )
            }
        )
    }
}

@Preview(name = "Root Top App Bar (No Back)", showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun MediaNestTopAppBarRootPreview() {
    MediaNestTheme {
        MediaNestTopAppBar(
            title = "MediaNest",
            subtitle = null,
            actions = {
                MediaNestAppBarAction(
                    painter = painterResource(R.drawable.ic_mn_search),
                    contentDescription = "Search",
                    onClick = {}
                )
            }
        )
    }
}
