package com.example.medianest.ui.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiUtils {
    fun formatDuration(seconds: Long): String {
        if (seconds <= 0L) return "0s"
        val totalMins = seconds / 60
        val totalHours = totalMins / 60
        val totalDays = totalHours / 24

        val years = totalDays / 365
        val months = (totalDays % 365) / 30
        val weeks = ((totalDays % 365) % 30) / 7
        val days = ((totalDays % 365) % 30) % 7
        val hours = totalHours % 24
        val mins = totalMins % 60
        val secs = seconds % 60

        val parts = mutableListOf<String>()
        if (years > 0) parts.add("${years}y")
        if (months > 0) parts.add("${months}mo")
        if (weeks > 0) parts.add("${weeks}w")
        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (mins > 0) parts.add("${mins}m")
        if (secs > 0 || parts.isEmpty()) parts.add("${secs}s")

        return parts.joinToString(" ")
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
        val weeks = ((diffDay % 365) % 30) / 7
        val days = ((diffDay % 365) % 30) % 7
        val hours = diffHour % 24
        val minutes = diffMin % 60
        val seconds = diffSec % 60

        if (abbreviated) {
            val sb = StringBuilder()
            if (years > 0) sb.append("${years}y ")
            if (months > 0) sb.append("${months}mo ")
            if (weeks > 0) sb.append("${weeks}w ")
            if (days > 0) sb.append("${days}d ")
            if (hours > 0) sb.append("${hours}h ")
            if (minutes > 0) sb.append("${minutes}m ")
            if (seconds > 0) sb.append("${seconds}s ")
            
            val result = sb.toString().trim()
            if (result.isNotEmpty()) return result
            
            return "just now"
        } else {
            val parts = mutableListOf<String>()
            if (years > 0) {
                parts.add(if (years == 1L) "1 year" else "$years years")
            }
            if (months > 0) {
                parts.add(if (months == 1L) "1 month" else "$months months")
            }
            if (weeks > 0) {
                parts.add(if (weeks == 1L) "1 week" else "$weeks weeks")
            }
            if (days > 0) {
                parts.add(if (days == 1L) "1 day" else "$days days")
            }
            if (hours > 0) {
                parts.add(if (hours == 1L) "1 hour" else "$hours hours")
            }
            if (minutes > 0) {
                parts.add(if (minutes == 1L) "1 minute" else "$minutes minutes")
            }
            if (seconds > 0) {
                parts.add(if (seconds == 1L) "1 second" else "$seconds seconds")
            }

            if (parts.isNotEmpty()) {
                return parts.joinToString(", ") + " ago"
            }
            return "just now"
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
