package com.example.medianest.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestShapes

/**
 * MediaNest filter chip with pill shape, dark red styling, and active/inactive states.
 *
 * Active state: AccentDeep (#8F1D2C) background, TextPrimary (#FFF7F8) text.
 * Inactive state: Transparent background with Border (#4B2B33) stroke, TextSecondary (#C8AEB4) text.
 */
@Composable
fun MediaNestChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    content: @Composable RowScope.() -> Unit
) {
    val targetBackgroundColor = when {
        !enabled -> if (selected) MediaNestColors.AccentDeep.copy(alpha = 0.45f) else Color.Transparent
        selected -> MediaNestColors.AccentDeep
        else -> Color.Transparent
    }

    val targetContentColor = when {
        !enabled -> if (selected) MediaNestColors.TextPrimary.copy(alpha = 0.45f) else MediaNestColors.TextSecondary.copy(alpha = 0.45f)
        selected -> MediaNestColors.TextPrimary
        else -> MediaNestColors.TextSecondary
    }

    val targetBorderColor = when {
        !enabled -> if (selected) Color.Transparent else MediaNestColors.Border.copy(alpha = 0.45f)
        selected -> Color.Transparent
        else -> MediaNestColors.Border
    }

    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(durationMillis = 180),
        label = "chipBackground"
    )

    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(durationMillis = 180),
        label = "chipContent"
    )

    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 180),
        label = "chipBorder"
    )

    val border = if (selected) null else BorderStroke(1.dp, borderColor)
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick
            ),
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        border = border
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    }
}

/**
 * Text-based overload for [MediaNestChip].
 */
@Composable
fun MediaNestChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    leadingIcon: (@Composable () -> Unit)? = if (icon != null) {
        {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    } else null,
    trailingIcon: (@Composable () -> Unit)? = null,
    badgeText: String? = null,
    shape: Shape = CircleShape
) {
    MediaNestChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        }

        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) MediaNestColors.Accent else MediaNestColors.Raised)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = if (selected) MediaNestColors.OnAccent else MediaNestColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (trailingIcon != null) {
            trailingIcon()
        }
    }
}

/**
 * Horizontal scrollable row container for [MediaNestChip] items.
 */
@Composable
fun MediaNestFilterRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    horizontalSpacing: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/**
 * Segmented control component following Design 2.0 specifications (.mn-segment).
 *
 * Container: NavigationBar (#241417) background with 1dp Border (#4B2B33), 20dp rounded shape, 4dp padding.
 * Active item: AccentDeep (#8F1D2C) background with TextPrimary (#FFF7F8) text, 16dp rounded shape.
 * Inactive item: Transparent background with TextSecondary (#C8AEB4) text.
 *
 * @param items List of elements to display.
 * @param selectedIndex Currently active item index.
 * @param onItemSelected Invoked when a segment item is clicked.
 * @param itemLabel Mapping from item to display label text.
 * @param modifier Custom modifier.
 * @param itemIcon Optional leading icon mapping for each item.
 */
@Composable
fun <T> MediaNestSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    itemIcon: ((T) -> ImageVector)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MediaNestShapes.Control,
        color = MediaNestColors.NavigationBar,
        border = BorderStroke(1.dp, MediaNestColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MediaNestColors.AccentDeep else Color.Transparent,
                    animationSpec = tween(durationMillis = 180),
                    label = "segmentItemBg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary,
                    animationSpec = tween(durationMillis = 180),
                    label = "segmentItemText"
                )
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 38.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(backgroundColor)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            role = Role.Tab,
                            onClick = { onItemSelected(index) }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val icon = itemIcon?.invoke(item)
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Text(
                            text = itemLabel(item),
                            color = contentColor,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * String-list overload for [MediaNestSegmentedControl].
 */
@Composable
fun MediaNestSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    MediaNestSegmentedControl(
        items = items,
        selectedIndex = selectedIndex,
        onItemSelected = onItemSelected,
        itemLabel = { it },
        modifier = modifier
    )
}
