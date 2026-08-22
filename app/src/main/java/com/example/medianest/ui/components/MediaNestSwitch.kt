package com.example.medianest.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier.scale(0.8f),
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

@Composable
fun FullTitlesToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String = "Full titles",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MediaNestColors.TextSecondary)
        Spacer(Modifier.width(4.dp))
        MediaNestSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
