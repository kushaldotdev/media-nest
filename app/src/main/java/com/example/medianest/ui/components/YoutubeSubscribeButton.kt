package com.example.medianest.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun YoutubeSubscribeButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    val backgroundColor = if (isSubscribed) {
        if (isDark) Color(0xFF272727) else Color(0xFFF2F2F2)
    } else {
        if (isDark) Color(0xFFF1F1F1) else Color(0xFF0F0F0F)
    }

    val contentColor = if (isSubscribed) {
        if (isDark) Color(0xFFF1F1F1) else Color(0xFF0F0F0F)
    } else {
        if (isDark) Color(0xFF0F0F0F) else Color(0xFFFFFFFF)
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        Text(
            text = if (isSubscribed) "Subscribed" else "Subscribe",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold
            )
        )
    }
}
