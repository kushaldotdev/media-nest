package com.example.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.medianest.ui.theme.MediaNestColors

/**
 * A reusable glassmorphism Card wrapper that applies standard background
 * and outline borders to maintain consistent styling across the application.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MediaNestColors.Glass
            ),
            border = BorderStroke(
                1.dp,
                MediaNestColors.GlassBorder
            ),
            onClick = onClick
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MediaNestColors.Glass
            ),
            border = BorderStroke(
                1.dp,
                MediaNestColors.GlassBorder
            )
        ) {
            content()
        }
    }
}
