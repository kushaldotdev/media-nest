package com.example.medianest.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.medianest.ui.theme.MediaNestColors

/**
 * Universal Switch colors for MediaNest matching Design & Brand Guidelines:
 * - Checked: Deep Red accent track (MediaNestColors.AccentDeep) with white thumb (Color.White)
 * - Unchecked: Muted dark track (MediaNestColors.ProgressTrack) with white thumb (Color.White) and subtle border (MediaNestColors.Border)
 */
@Composable
fun mediaNestSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedTrackColor = MediaNestColors.AccentDeep,
    checkedThumbColor = Color.White,
    checkedBorderColor = Color.Transparent,
    uncheckedTrackColor = MediaNestColors.ProgressTrack,
    uncheckedThumbColor = Color.White,
    uncheckedBorderColor = MediaNestColors.Border,
    disabledCheckedTrackColor = MediaNestColors.Border.copy(alpha = 0.4f),
    disabledUncheckedTrackColor = MediaNestColors.Border.copy(alpha = 0.4f),
    disabledCheckedThumbColor = MediaNestColors.TextSecondary.copy(alpha = 0.6f),
    disabledUncheckedThumbColor = MediaNestColors.TextSecondary.copy(alpha = 0.6f)
)

/**
 * Universal MediaNest Switch component.
 */
@Composable
fun MediaNestSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = mediaNestSwitchColors()
    )
}
