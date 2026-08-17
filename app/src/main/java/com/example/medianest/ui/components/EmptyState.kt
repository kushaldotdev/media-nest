package com.example.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestTheme

/**
 * Variants for [MediaNestButton].
 */
enum class MediaNestButtonVariant {
    /** High emphasis primary action: Accent fill (#FFB1B6) with OnAccent text (#3A0B12). */
    PRIMARY,
    /** Elevated surface action: Raised fill (#382027) with Border outline (#4B2B33) and TextPrimary text. */
    SECONDARY,
    /** Strong branded accent action: AccentDeep fill (#8F1D2C) with TextPrimary text. */
    TONAL,
    /** Outlined action: Transparent background with Border outline (#4B2B33) and TextPrimary text. */
    OUTLINED,
    /** Destructive action: Destructive fill (#EA4A59) with white text. */
    DESTRUCTIVE
}

/**
 * Brand button component matching MediaNest Design 2.0 specs (.mn-btn).
 */
@Composable
fun MediaNestButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MediaNestButtonVariant = MediaNestButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
) {
    val containerColor = when (variant) {
        MediaNestButtonVariant.PRIMARY -> MediaNestColors.Accent
        MediaNestButtonVariant.SECONDARY -> MediaNestColors.Raised
        MediaNestButtonVariant.TONAL -> MediaNestColors.AccentDeep
        MediaNestButtonVariant.OUTLINED -> Color.Transparent
        MediaNestButtonVariant.DESTRUCTIVE -> MediaNestColors.Destructive
    }

    val contentColor = when (variant) {
        MediaNestButtonVariant.PRIMARY -> MediaNestColors.OnAccent
        MediaNestButtonVariant.SECONDARY -> MediaNestColors.TextPrimary
        MediaNestButtonVariant.TONAL -> MediaNestColors.TextPrimary
        MediaNestButtonVariant.OUTLINED -> MediaNestColors.TextPrimary
        MediaNestButtonVariant.DESTRUCTIVE -> Color.White
    }

    val borderStroke = when (variant) {
        MediaNestButtonVariant.SECONDARY -> BorderStroke(1.dp, MediaNestColors.Border)
        MediaNestButtonVariant.OUTLINED -> BorderStroke(1.dp, MediaNestColors.Border)
        else -> null
    }

    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.38f),
            disabledContentColor = contentColor.copy(alpha = 0.38f)
        ),
        border = borderStroke,
        contentPadding = contentPadding,
        modifier = modifier.height(42.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.1.sp
                    ),
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Standard Empty State component per MediaNest Design 2.0 specs (.mn-state).
 *
 * Characteristics:
 * - Icon container: Circular 72dp background with [MediaNestColors.Raised] (#382027)
 *   and 1px border with [MediaNestColors.Border] (#4B2B33)
 * - Icon: 32dp centered icon tinted [MediaNestColors.TextSecondary] or custom tint
 * - Title: [MediaNestColors.TextPrimary] (#FFF7F8), SemiBold, centered
 * - Message: [MediaNestColors.TextSecondary] (#C8AEB4), Normal, centered, max-width ~280dp
 * - Optional Action: [MediaNestButton] or custom action composable slot
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionVariant: MediaNestButtonVariant = MediaNestButtonVariant.PRIMARY,
    actionIcon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
    iconBackgroundColor: Color = MediaNestColors.Raised,
    iconBorderColor: Color = MediaNestColors.Border,
    iconTint: Color = MediaNestColors.TextSecondary,
    iconContainerSize: Dp = 72.dp,
    iconSize: Dp = 32.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Circular Icon Badge
        if (iconContent != null || icon != null) {
            Box(
                modifier = Modifier
                    .size(iconContainerSize)
                    .clip(CircleShape)
                    .background(iconBackgroundColor)
                    .border(1.dp, iconBorderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (iconContent != null) {
                    iconContent()
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
        }

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = (-0.2).sp
            ),
            color = MediaNestColors.TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        // Body Message
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            color = MediaNestColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp)
        )

        // Action Slot / MediaNestButton
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        } else if (actionText != null && onActionClick != null) {
            Spacer(Modifier.height(20.dp))
            MediaNestButton(
                text = actionText,
                onClick = onActionClick,
                variant = actionVariant,
                icon = actionIcon
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Previews
// -----------------------------------------------------------------------------

@Preview(name = "Empty Library State", showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun EmptyLibraryPreview() {
    MediaNestTheme {
        EmptyState(
            title = "Library is Empty",
            message = "No downloaded media or collections found. Start exploring to save content for offline access.",
            icon = Icons.Default.VideoLibrary,
            actionText = "Explore Media",
            onActionClick = {},
            actionVariant = MediaNestButtonVariant.PRIMARY
        )
    }
}

@Preview(name = "Empty Downloads State", showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun EmptyDownloadsPreview() {
    MediaNestTheme {
        EmptyState(
            title = "No Active Downloads",
            message = "Your download queue is clear. Videos and audio will appear here while downloading.",
            icon = Icons.Default.DownloadDone,
            actionText = "Check Library",
            onActionClick = {},
            actionVariant = MediaNestButtonVariant.SECONDARY
        )
    }
}

@Preview(name = "No Search Results State", showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun EmptySearchPreview() {
    MediaNestTheme {
        EmptyState(
            title = "No Results Found",
            message = "We couldn't find anything matching your search. Try different keywords or check spelling.",
            icon = Icons.Default.SearchOff,
            actionText = "Clear Search",
            onActionClick = {},
            actionVariant = MediaNestButtonVariant.OUTLINED
        )
    }
}
