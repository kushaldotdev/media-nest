package com.example.medianest.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object MediaNestShapes {
    val Card = RoundedCornerShape(14.dp)
    val Hero = RoundedCornerShape(16.dp)
    val Control = RoundedCornerShape(20.dp)

    val shapes = Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp)
    )
}

val Shapes = MediaNestShapes.shapes
