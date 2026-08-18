package com.example.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medianest.R
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestTheme

/**
 * Single-category sort trigger pill button per design-2 specification §5.6.
 * Displays sort direction arrow icon and active category label.
 *
 * @param categoryLabel Label of currently active sort category (e.g., "Date", "Name", "Duration").
 * @param ascending True if sorting ascending (arrow up), false for descending (arrow down).
 * @param onClick Callback invoked when the pill is tapped to toggle sort order or open sort sheet.
 * @param modifier Root layout modifier.
 */
@Composable
fun SortPill(
    categoryLabel: String,
    ascending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = shape,
        color = MediaNestColors.Card,
        contentColor = MediaNestColors.TextPrimary,
        border = BorderStroke(1.dp, MediaNestColors.Border),
        modifier = modifier
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (ascending) R.drawable.ic_mn_arrow_up else R.drawable.ic_mn_arrow_down
                ),
                contentDescription = if (ascending) "Sort ascending" else "Sort descending",
                modifier = Modifier.size(16.dp),
                tint = MediaNestColors.Accent
            )
            Text(
                text = categoryLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MediaNestColors.TextPrimary
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun SortPillDescendingPreview() {
    MediaNestTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SortPill(
                categoryLabel = "Date",
                ascending = false,
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun SortPillAscendingPreview() {
    MediaNestTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SortPill(
                categoryLabel = "Duration",
                ascending = true,
                onClick = {}
            )
        }
    }
}
