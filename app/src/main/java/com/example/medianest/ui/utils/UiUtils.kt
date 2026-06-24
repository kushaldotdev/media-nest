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

    fun stripHtml(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
            } else {
                @Suppress("DEPRECATION")
                android.text.Html.fromHtml(html).toString().trim()
            }
        } catch (e: Exception) {
            html
        }
    }

    fun formatReleaseDate(rawDate: String?): String? {
        if (rawDate.isNullOrBlank()) return null
        if (rawDate.contains("T")) {
            try {
                val odt = java.time.OffsetDateTime.parse(rawDate)
                val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
                return odt.format(formatter)
            } catch (e: Exception) {
                // fallback
            }
        }
        try {
            val ld = java.time.LocalDate.parse(rawDate)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
            return ld.format(formatter)
        } catch (e: Exception) {
            return rawDate
        }
    }
}
