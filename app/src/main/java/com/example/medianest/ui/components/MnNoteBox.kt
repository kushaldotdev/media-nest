package com.example.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medianest.R
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestTheme

/**
 * Visual styling variants for [MnNoteBox] callout containers per design-2 specification.
 */
enum class NoteBoxVariant {
    STANDARD,
    WARNING,
    SUCCESS
}

/**
 * Structured callout container (.mn-note-box) with an icon, title header, and supporting content.
 *
 * @param title Header title text.
 * @param modifier Modifier for the root container.
 * @param variant Visual variant determining colors and default icon (STANDARD, WARNING, SUCCESS).
 * @param iconPainter Optional custom icon painter. If null, a variant-appropriate icon is used.
 * @param content Composable column content rendered below the header.
 */
@Composable
fun MnNoteBox(
    title: String,
    modifier: Modifier = Modifier,
    variant: NoteBoxVariant = NoteBoxVariant.STANDARD,
    iconPainter: Painter? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundColor = when (variant) {
        NoteBoxVariant.STANDARD -> MediaNestColors.NoteBoxBgStandard
        NoteBoxVariant.WARNING -> MediaNestColors.NoteBoxBgWarning
        NoteBoxVariant.SUCCESS -> MediaNestColors.NoteBoxBgSuccess
    }

    val borderColor = when (variant) {
        NoteBoxVariant.STANDARD -> MediaNestColors.Border
        NoteBoxVariant.WARNING -> MediaNestColors.NoteBoxBorderWarning
        NoteBoxVariant.SUCCESS -> MediaNestColors.NoteBoxBorderSuccess
    }

    val headerColor = when (variant) {
        NoteBoxVariant.STANDARD -> MediaNestColors.Accent
        NoteBoxVariant.WARNING -> MediaNestColors.NoteBoxTextWarning
        NoteBoxVariant.SUCCESS -> MediaNestColors.NoteBoxTextSuccess
    }

    val effectiveIconPainter = iconPainter ?: when (variant) {
        NoteBoxVariant.STANDARD -> painterResource(R.drawable.ic_mn_info)
        NoteBoxVariant.WARNING -> painterResource(R.drawable.ic_mn_warning)
        NoteBoxVariant.SUCCESS -> painterResource(R.drawable.ic_mn_check_circle)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = effectiveIconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = headerColor
                )
                Text(
                    text = title,
                    color = headerColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val contentTextStyle = TextStyle(
                color = MediaNestColors.TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = (10.5 * 1.55).sp
            )

            CompositionLocalProvider(LocalTextStyle provides contentTextStyle) {
                content()
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun MnNoteBoxStandardPreview() {
    MediaNestTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MnNoteBox(
                title = "Library Storage Info",
                variant = NoteBoxVariant.STANDARD
            ) {
                Text("Media files are stored securely in internal app storage. SAF exports can be configured in preferences.")
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun MnNoteBoxWarningPreview() {
    MediaNestTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MnNoteBox(
                title = "Network Metered",
                variant = NoteBoxVariant.WARNING
            ) {
                Text("Background downloading paused while connected to a metered Wi-Fi network.")
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF120B0E)
@Composable
private fun MnNoteBoxSuccessPreview() {
    MediaNestTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MnNoteBox(
                title = "Sync Complete",
                variant = NoteBoxVariant.SUCCESS
            ) {
                Text("All 42 collection items synchronized successfully with your remote instance.")
            }
        }
    }
}
