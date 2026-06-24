package com.example.medianest.ui.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiUtils {
    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    fun formatAbsoluteDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.US)
        return sdf.format(Date(timestamp))
    }
}
