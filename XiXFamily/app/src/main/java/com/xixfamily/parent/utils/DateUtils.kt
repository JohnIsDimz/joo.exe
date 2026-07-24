package com.xixfamily.parent.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val displayFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayMonthFormat = SimpleDateFormat("MMM dd", Locale.US)

    fun formatTimestamp(isoTimestamp: String): String {
        return try {
            val date = isoFormat.parse(isoTimestamp)
            if (date != null) displayFormat.format(date) else isoTimestamp
        } catch (e: Exception) {
            isoTimestamp
        }
    }

    fun formatTime(isoTimestamp: String): String {
        return try {
            val date = isoFormat.parse(isoTimestamp)
            if (date != null) timeFormat.format(date) else isoTimestamp
        } catch (e: Exception) {
            isoTimestamp
        }
    }

    fun formatDateOnly(isoTimestamp: String): String {
        return try {
            val date = isoFormat.parse(isoTimestamp)
            if (date != null) dayMonthFormat.format(date) else isoTimestamp
        } catch (e: Exception) {
            isoTimestamp
        }
    }

    fun getRelativeTime(isoTimestamp: String): String {
        return try {
            val date = isoFormat.parse(isoTimestamp) ?: return isoTimestamp
            val now = Date()
            val diff = now.time - date.time

            when {
                diff < 60_000 -> "just now"
                diff < 3_600_000 -> "${diff / 60_000}m ago"
                diff < 86_400_000 -> "${diff / 3_600_000}h ago"
                diff < 604_800_000 -> "${diff / 86_400_000}d ago"
                else -> formatDateOnly(isoTimestamp)
            }
        } catch (e: Exception) {
            isoTimestamp
        }
    }

    fun getTodayDate(): String {
        return dateOnlyFormat.format(Date())
    }

    fun formatDuration(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) {
            "${hours}h ${mins}m"
        } else {
            "${mins}m"
        }
    }
}
