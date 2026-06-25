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

    fun parseUploadDate(rawDate: String?): Date? {
        if (rawDate.isNullOrBlank()) return null
        if (rawDate.contains("T")) {
            try {
                val dateTimeStr = if (rawDate.length >= 19) rawDate.substring(0, 19) else rawDate
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                return sdf.parse(dateTimeStr)
            } catch (e: Exception) {
                // fallback
            }
        }
        try {
            val dateStr = if (rawDate.length >= 10) rawDate.substring(0, 10) else rawDate
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.parse(dateStr)
        } catch (e: Exception) {
            // fallback
        }
        return null
    }

    fun formatRelativeTime(date: Date, abbreviated: Boolean = true): String {
        val diffMs = System.currentTimeMillis() - date.time
        if (diffMs < 0) {
            return "just now"
        }
        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val diffHour = diffMin / 60
        val diffDay = diffHour / 24

        val years = diffDay / 365
        val months = (diffDay % 365) / 30
        val days = (diffDay % 365) % 30
        val hours = diffHour % 24

        if (abbreviated) {
            val sb = StringBuilder()
            if (years > 0) sb.append("${years}y ")
            if (months > 0) sb.append("${months}mo ")
            if (days > 0) sb.append("${days}d ")
            if (hours > 0) sb.append("${hours}h ")
            
            val result = sb.toString().trim()
            if (result.isNotEmpty()) return result
            
            return if (diffMin > 0) "${diffMin}m" else "just now"
        } else {
            val parts = mutableListOf<String>()
            if (years > 0) {
                parts.add(if (years == 1L) "1 year" else "$years years")
            }
            if (months > 0) {
                parts.add(if (months == 1L) "1 month" else "$months months")
            }
            if (days > 0) {
                parts.add(if (days == 1L) "1 day" else "$days days")
            }
            if (hours > 0) {
                parts.add(if (hours == 1L) "1 hour" else "$hours hours")
            }

            if (parts.isNotEmpty()) {
                return parts.joinToString(", ") + " ago"
            }
            return if (diffMin > 0) {
                if (diffMin == 1L) "1 minute ago" else "$diffMin minutes ago"
            } else {
                "just now"
            }
        }
    }

    fun formatReleaseDate(rawDate: String?): String? {
        if (rawDate.isNullOrBlank()) return null
        if (rawDate.contains("ago", ignoreCase = true) || rawDate.contains("now", ignoreCase = true)) {
            return rawDate
        }
        val date = parseUploadDate(rawDate) ?: return rawDate
        return formatRelativeTime(date, abbreviated = true)
    }

    fun formatRelativeDateExact(rawDate: String?): String? {
        if (rawDate.isNullOrBlank()) return null
        val date = parseUploadDate(rawDate) ?: return rawDate
        return formatRelativeTime(date, abbreviated = false)
    }

    fun formatAbsoluteReleaseDate(rawDate: String?): String? {
        if (rawDate.isNullOrBlank()) return null
        val date = parseUploadDate(rawDate) ?: return rawDate
        return if (rawDate.contains("T")) {
            val formatter = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.US)
            formatter.format(date)
        } else {
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            formatter.format(date)
        }
    }
}
