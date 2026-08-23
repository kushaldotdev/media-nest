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
        val trimmed = rawDate.trim()

        // 1. Numeric timestamp
        trimmed.toLongOrNull()?.let { num ->
            return if (num > 1_000_000_000_000L) {
                Date(num)
            } else if (num > 1_000_000_000L) {
                Date(num * 1000L)
            } else null
        }

        // 2. Relative time strings like "now", "just now", "yesterday", "9 months ago", "2w", etc.
        if (trimmed.equals("just now", ignoreCase = true) || trimmed.equals("now", ignoreCase = true)) {
            return Date()
        }
        if (trimmed.equals("yesterday", ignoreCase = true)) {
            return Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
        }
        val agoRegex = Regex("""(\d+)\s*(year|yr|month|mo|week|wk|day|d|hour|hr|minute|min|second|sec)s?(?:\s*ago)?""", RegexOption.IGNORE_CASE)
        val agoMatch = agoRegex.find(trimmed)
        if (agoMatch != null && (trimmed.contains("ago", ignoreCase = true) || trimmed.matches(Regex("""^\d+\s*[a-zA-Z]+$""")))) {
            val count = agoMatch.groupValues[1].toLongOrNull() ?: return null
            val unit = agoMatch.groupValues[2].lowercase()
            val millis = when {
                unit.startsWith("y") -> count * 365L * 24 * 60 * 60 * 1000L
                unit.startsWith("mo") -> count * 30L * 24 * 60 * 60 * 1000L
                unit.startsWith("w") -> count * 7L * 24 * 60 * 60 * 1000L
                unit.startsWith("d") -> count * 24L * 60 * 60 * 1000L
                unit.startsWith("h") -> count * 60 * 60 * 1000L
                unit.startsWith("m") -> count * 60 * 1000L
                unit.startsWith("s") -> count * 1000L
                else -> null
            }
            if (millis != null) {
                return Date(System.currentTimeMillis() - millis)
            }
        }

        // 3. ISO 8601 formats
        if (trimmed.contains("T")) {
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss"
            )
            for (p in patterns) {
                try {
                    val sdf = SimpleDateFormat(p, Locale.US)
                    val d = sdf.parse(trimmed)
                    if (d != null) return d
                } catch (e: Exception) {
                    // try next
                }
            }
            try {
                val dateTimeStr = if (trimmed.length >= 19) trimmed.substring(0, 19) else trimmed
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                val d = sdf.parse(dateTimeStr)
                if (d != null) return d
            } catch (e: Exception) {
                // fallback
            }
        }

        // 4. Standard Date formats
        val formats = listOf(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "MMM dd, yyyy",
            "MMMM dd, yyyy",
            "dd MMM yyyy",
            "dd MMMM yyyy",
            "MMM d, yyyy",
            "d MMM yyyy"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed
            } catch (e: Exception) {
                // continue
            }
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
        val trimmed = rawDate.trim()
        val date = parseUploadDate(trimmed) ?: return trimmed
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

    fun upgradeAvatarUrl(url: String?): String? {
        if (url.isNullOrBlank()) return url
        var upgraded = url
        if (upgraded.contains("yt3.ggpht.com") || upgraded.contains("googleusercontent.com")) {
            upgraded = upgraded.replace(Regex("=s\\d+(?:-[^-?&]+)*"), "=s800")
        }
        return upgraded
    }

    fun upgradeBannerUrl(url: String?): String? {
        if (url.isNullOrBlank()) return url
        var upgraded = url
        if (upgraded.contains("yt3.ggpht.com") || upgraded.contains("googleusercontent.com")) {
            upgraded = upgraded.replace(Regex("=w\\d+(?:-[^-?&]+)*"), "=w2120")
            upgraded = upgraded.replace(Regex("=s\\d+(?:-[^-?&]+)*"), "=s1600")
        }
        return upgraded
    }

    fun upgradePlaylistThumbnail(url: String?, firstVideoThumbnail: String? = null): String? {
        val target = if (!url.isNullOrBlank()) url else firstVideoThumbnail
        if (target.isNullOrBlank()) return null
        var upgraded = target
        if (upgraded.contains("i.ytimg.com") || upgraded.contains("youtube.com")) {
            upgraded = upgraded.replace("/default.jpg", "/hqdefault.jpg")
            upgraded = upgraded.replace("/mqdefault.jpg", "/hqdefault.jpg")
        }
        if (upgraded.contains("yt3.ggpht.com") || upgraded.contains("googleusercontent.com")) {
            upgraded = upgraded.replace(Regex("=s\\d+(?:-[^-?&]+)*"), "=s800")
            upgraded = upgraded.replace(Regex("=w\\d+(?:-[^-?&]+)*"), "=w1280")
        }
        return upgraded
    }
}
