package com.example.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medianest.ui.theme.MediaNestColors

/**
 * Visual variants for MediaNest buttons following Design 2.0 specifications.
 */
enum class MediaNestButtonVariant {
    /** High-emphasis primary action (Accent background, OnAccent text). */
    Primary,

    /** Secondary outlined action (Transparent background, Border stroke, Accent text). */
    Secondary,

    /** Deep accent action (AccentDeep background, TextPrimary text). */
    Deep,

    /** Ghost/subtle action (Transparent background, TextSecondary text). */
    Ghost,

    /** Outlined destructive action (Transparent background, Destructive border and text). */
    Danger,

    /** Solid destructive action (Destructive background, White text). */
    DangerSolid
}

/**
 * Sizing options for MediaNest buttons.
 */
enum class MediaNestButtonSize(
    val minHeight: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val textStyle: TextStyle,
    val defaultShape: Shape,
    val iconSize: Dp,
    val iconSpacing: Dp
) {
    /** Standard button size: min-height 40dp, 16dp horizontal padding. */
    Standard(
        minHeight = 40.dp,
        horizontalPadding = 16.dp,
        verticalPadding = 0.dp,
        textStyle = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp
        ),
        defaultShape = RoundedCornerShape(20.dp),
        iconSize = 18.dp,
        iconSpacing = 8.dp
    ),

    /** Small button size: min-height 32dp, 12dp horizontal padding, pill shape. */
    Small(
        minHeight = 32.dp,
        horizontalPadding = 12.dp,
        verticalPadding = 0.dp,
        textStyle = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp
        ),
        defaultShape = CircleShape,
        iconSize = 16.dp,
        iconSpacing = 6.dp
    ),

    /** Extra-small button size: min-height 28dp, 10dp horizontal padding, pill shape. */
    ExtraSmall(
        minHeight = 28.dp,
        horizontalPadding = 10.dp,
        verticalPadding = 0.dp,
        textStyle = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp
        ),
        defaultShape = CircleShape,
        iconSize = 14.dp,
        iconSpacing = 4.dp
    )
}

/**
 * Visual sizing options for MediaNest icon buttons.
 * All sizes maintain at least a 48dp accessible touch target.
 */
enum class MediaNestIconButtonSize(
    val visualSize: Dp,
    val iconSize: Dp
) {
    /** Standard icon button (40dp visual target, 20dp icon). */
    Standard(visualSize = 40.dp, iconSize = 20.dp),

    /** Small icon button (32dp visual target, 16dp icon). */
    Small(visualSize = 32.dp, iconSize = 16.dp),

    /** Extra-small icon button (24dp visual target, 14dp icon). */
    ExtraSmall(visualSize = 24.dp, iconSize = 14.dp)
}

/**
 * Primary MediaNest button component supporting all Design 2.0 variants and sizes.
 */
@Composable
fun MediaNestButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MediaNestButtonVariant = MediaNestButtonVariant.Primary,
    size: MediaNestButtonSize = MediaNestButtonSize.Standard,
    enabled: Boolean = true,
    shape: Shape? = null,
    contentPadding: PaddingValues? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    fullWidth: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val containerColor = when (variant) {
        MediaNestButtonVariant.Primary -> MediaNestColors.Accent
        MediaNestButtonVariant.Secondary -> Color.Transparent
        MediaNestButtonVariant.Deep -> MediaNestColors.AccentDeep
        MediaNestButtonVariant.Ghost -> Color.Transparent
        MediaNestButtonVariant.Danger -> Color.Transparent
        MediaNestButtonVariant.DangerSolid -> MediaNestColors.Destructive
    }

    val contentColor = when (variant) {
        MediaNestButtonVariant.Primary -> MediaNestColors.OnAccent
        MediaNestButtonVariant.Secondary -> MediaNestColors.Accent
        MediaNestButtonVariant.Deep -> MediaNestColors.TextPrimary
        MediaNestButtonVariant.Ghost -> MediaNestColors.TextSecondary
        MediaNestButtonVariant.Danger -> MediaNestColors.Destructive
        MediaNestButtonVariant.DangerSolid -> Color.White
    }

    val disabledContainerColor = when (variant) {
        MediaNestButtonVariant.Primary -> MediaNestColors.Accent.copy(alpha = 0.45f)
        MediaNestButtonVariant.Secondary -> Color.Transparent
        MediaNestButtonVariant.Deep -> MediaNestColors.AccentDeep.copy(alpha = 0.45f)
        MediaNestButtonVariant.Ghost -> Color.Transparent
        MediaNestButtonVariant.Danger -> Color.Transparent
        MediaNestButtonVariant.DangerSolid -> MediaNestColors.Destructive.copy(alpha = 0.45f)
    }

    val disabledContentColor = when (variant) {
        MediaNestButtonVariant.Primary -> MediaNestColors.OnAccent.copy(alpha = 0.45f)
        MediaNestButtonVariant.Secondary -> MediaNestColors.Accent.copy(alpha = 0.45f)
        MediaNestButtonVariant.Deep -> MediaNestColors.TextPrimary.copy(alpha = 0.45f)
        MediaNestButtonVariant.Ghost -> MediaNestColors.TextSecondary.copy(alpha = 0.45f)
        MediaNestButtonVariant.Danger -> MediaNestColors.Destructive.copy(alpha = 0.45f)
        MediaNestButtonVariant.DangerSolid -> Color.White.copy(alpha = 0.45f)
    }

    val border: BorderStroke? = when (variant) {
        MediaNestButtonVariant.Secondary -> {
            val borderColor = if (enabled) MediaNestColors.Border else MediaNestColors.Border.copy(alpha = 0.45f)
            BorderStroke(1.5.dp, borderColor)
        }
        MediaNestButtonVariant.Danger -> {
            val borderColor = if (enabled) MediaNestColors.Destructive else MediaNestColors.Destructive.copy(alpha = 0.45f)
            BorderStroke(1.5.dp, borderColor)
        }
        else -> null
    }

    val buttonShape = shape ?: size.defaultShape
    val padding = contentPadding ?: PaddingValues(
        horizontal = size.horizontalPadding,
        vertical = size.verticalPadding
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = size.minHeight)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier),
        enabled = enabled,
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        ),
        elevation = null,
        border = border,
        contentPadding = padding
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(size.iconSpacing))
            }

            content()

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(size.iconSpacing))
                trailingIcon()
            }
        }
    }
}

/**
 * Text-based overload for [MediaNestButton].
 */
@Composable
fun MediaNestButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MediaNestButtonVariant = MediaNestButtonVariant.Primary,
    size: MediaNestButtonSize = MediaNestButtonSize.Standard,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape? = null,
    contentPadding: PaddingValues? = null,
    fullWidth: Boolean = false
) {
    MediaNestButton(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        size = size,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        fullWidth = fullWidth
    ) {
        Text(
            text = text,
            style = size.textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Icon button for MediaNest with 48dp minimum accessible touch target and ripple effect.
 *
 * @param onClick Invoked when button is pressed.
 * @param modifier Custom modifier.
 * @param size Visual size of the button (Standard: 40dp, Small: 32dp, ExtraSmall: 24dp).
 * @param enabled Whether the button responds to interaction.
 * @param tint Color tint applied to the icon.
 * @param containerColor Optional background fill for the button surface.
 * @param badgeText Optional badge count / text displayed at the top-right corner.
 * @param showBadge Whether to display an uncounted indicator badge.
 * @param contentDescription Accessibility description for the action.
 * @param icon Composable icon content.
 */
@Composable
fun MediaNestIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: MediaNestIconButtonSize = MediaNestIconButtonSize.Standard,
    enabled: Boolean = true,
    tint: Color = MediaNestColors.TextSecondary,
    containerColor: Color = Color.Transparent,
    badgeText: String? = null,
    showBadge: Boolean = false,
    contentDescription: String? = null,
    icon: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val effectiveTint = if (enabled) tint else tint.copy(alpha = 0.45f)
    val effectiveContainer = if (enabled) containerColor else containerColor.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = (size.visualSize / 2) + 4.dp),
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size.visualSize)
                .clip(CircleShape)
                .background(effectiveContainer),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides effectiveTint) {
                icon()
            }
        }

        // Badge indicator
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(MediaNestColors.Destructive)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        } else if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 0.dp, y = 0.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MediaNestColors.Destructive)
            )
        }
    }
}

/**
 * ImageVector overload for [MediaNestIconButton].
 */
@Composable
fun MediaNestIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: MediaNestIconButtonSize = MediaNestIconButtonSize.Standard,
    enabled: Boolean = true,
    tint: Color = MediaNestColors.TextSecondary,
    containerColor: Color = Color.Transparent,
    badgeText: String? = null,
    showBadge: Boolean = false
) {
    MediaNestIconButton(
        onClick = onClick,
        modifier = modifier,
        size = size,
        enabled = enabled,
        tint = tint,
        containerColor = containerColor,
        badgeText = badgeText,
        showBadge = showBadge,
        contentDescription = contentDescription
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(size.iconSize)
        )
    }
}

/**
 * MediaNest Floating Action Button with pill radius, 52dp height, Accent background, and OnAccent content.
 */
@Composable
fun MediaNestFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MediaNestColors.Accent,
    contentColor: Color = MediaNestColors.OnAccent,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(
        defaultElevation = 6.dp,
        pressedElevation = 8.dp
    )
) {
    if (text != null) {
        // Extended Pill FAB (height: 52dp, padding: 0 22px)
        ExtendedFloatingActionButton(
            onClick = onClick,
            modifier = modifier
                .height(52.dp),
            shape = CircleShape,
            containerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.45f),
            contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.45f),
            elevation = elevation,
            icon = {
                if (icon != null) {
                    icon()
                }
            },
            text = {
                Text(
                    text = text,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.1.sp
                    )
                )
            }
        )
    } else {
        // Standard Pill FAB (height: 52dp)
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier
                .size(52.dp),
            shape = CircleShape,
            containerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.45f),
            contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.45f),
            elevation = elevation
        ) {
            if (icon != null) {
                icon()
            }
        }
    }
}

/**
 * Convenience ImageVector overload for [MediaNestFloatingActionButton].
 */
@Composable
fun MediaNestFloatingActionButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    enabled: Boolean = true,
    containerColor: Color = MediaNestColors.Accent,
    contentColor: Color = MediaNestColors.OnAccent
) {
    MediaNestFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        text = text,
        icon = {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
        },
        enabled = enabled,
        containerColor = containerColor,
        contentColor = contentColor
    )
}
