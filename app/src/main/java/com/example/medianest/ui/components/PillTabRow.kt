package com.example.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
 * Generic horizontally-scrollable pill tab row for sub-tab navigation per design-2 specification.
 *
 * @param items List of tab items.
 * @param selected Currently selected tab item.
 * @param onSelect Callback invoked when a tab item is clicked.
 * @param label Function returning text label for an item.
 * @param iconRes Function returning optional drawable resource ID for an item.
 * @param modifier Root layout modifier.
 * @param contentPadding Padding applied around the horizontal row content.
 */
@Composable
fun <T> PillTabRow(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    iconRes: (T) -> Int? = { null },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val pillBg = if (isSelected) MediaNestColors.AccentDeep else MediaNestColors.Card
            val contentColor = if (isSelected) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary
            val border = if (isSelected) null else BorderStroke(1.dp, MediaNestColors.Border)
            val iconDrawableRes = iconRes(item)
            val shape = RoundedCornerShape(999.dp)
            val interactionSource = remember { MutableInteractionSource() }

            Surface(
                shape = shape,
                color = pillBg,
                contentColor = contentColor,
                border = border,
                modifier = Modifier
                    .clip(shape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        role = Role.Tab,
                        onClick = { onSelect(item) }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (iconDrawableRes != null && iconDrawableRes != 0) {
                        Icon(
                            painter = painterResource(iconDrawableRes),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = contentColor
                        )
                    }
                    Text(
                        text = label(item),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

private enum class SampleTab(val label: String, val iconRes: Int) {
    HISTORY("History", R.drawable.ic_mn_history),
    WATCHED("Watched", R.drawable.ic_mn_watched),
    FOLDERS("Folders", R.drawable.ic_mn_folder),
    FAVORITES("Favorites", R.drawable.ic_mn_heart),
    PLAYLISTS("Playlists", R.drawable.ic_mn_playlist),
    CHANNELS("Channels", R.drawable.ic_mn_channel)
}

@Preview(showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun PillTabRowPreview() {
    MediaNestTheme {
        Box(modifier = Modifier.padding(vertical = 16.dp)) {
            PillTabRow(
                items = SampleTab.entries,
                selected = SampleTab.FOLDERS,
                onSelect = {},
                label = { it.label },
                iconRes = { it.iconRes }
            )
        }
    }
}
