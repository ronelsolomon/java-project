package com.example.emotiondetector.util

import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for date and time operations
 */
@Singleton
class DateTimeUtils @Inject constructor() {
    companion object {
        // Common date/time patterns
        const val PATTERN_ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" // 2023-05-15T14:30:45.123Z
        const val PATTERN_DATE = "yyyy-MM-dd" // 2023-05-15
        const val PATTERN_TIME = "HH:mm:ss" // 14:30:45
        const val PATTERN_DATE_TIME = "yyyy-MM-dd HH:mm:ss" // 2023-05-15 14:30:45
        const val PATTERN_FILE_TIMESTAMP = "yyyyMMdd_HHmmss" // 20230515_143045
        const val PATTERN_READABLE_DATE = "MMM d, yyyy" // May 15, 2023
        const val PATTERN_READABLE_TIME = "h:mm a" // 2:30 PM
        const val PATTERN_READABLE_DATE_TIME = "MMM d, yyyy h:mm a" // May 15, 2023 2:30 PM
        
        // Time constants in milliseconds
        const val SECOND_MILLIS = 1000L
        const val MINUTE_MILLIS = 60 * SECOND_MILLIS
        const val HOUR_MILLIS = 60 * MINUTE_MILLIS
        const val DAY_MILLIS = 24 * HOUR_MILLIS
        const val WEEK_MILLIS = 7 * DAY_MILLIS
    }
    
    private val defaultLocale = Locale.getDefault()
    
    /**
     * Get current timestamp in milliseconds
     */
    fun currentTimeMillis(): Long = System.currentTimeMillis()
    
    /**
     * Format a timestamp to a string using the specified pattern
     */
    fun formatTimestamp(
        timestamp: Long,
        pattern: String = PATTERN_ISO_8601,
        locale: Locale = defaultLocale
    ): String {
        return SimpleDateFormat(pattern, locale).format(Date(timestamp))
    }
    
    /**
     * Parse a date string to a timestamp using the specified pattern
     */
    fun parseTimestamp(
        dateString: String,
        pattern: String = PATTERN_ISO_8601,
        locale: Locale = defaultLocale
    ): Long? {
        return try {
            SimpleDateFormat(pattern, locale).parse(dateString)?.time
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get a human-readable relative time string (e.g., "2 hours ago")
     */
    fun getRelativeTimeSpanString(
        timestamp: Long,
        now: Long = currentTimeMillis(),
        minResolution: Long = 0,
        flags: Int = 0
    ): String {
        val diff = now - timestamp
        
        return when {
            diff < MINUTE_MILLIS -> "Just now"
            diff < 2 * MINUTE_MILLIS -> "A minute ago"
            diff < 50 * MINUTE_MILLIS -> "${diff / MINUTE_MILLIS} minutes ago"
            diff < 90 * MINUTE_MILLIS -> "An hour ago"
            diff < 24 * HOUR_MILLIS -> "${diff / HOUR_MILLIS} hours ago"
            diff < 48 * HOUR_MILLIS -> "Yesterday"
            diff < 7 * DAY_MILLIS -> "${diff / DAY_MILLIS} days ago"
            diff < 2 * WEEK_MILLIS -> "Last week"
            diff < 4 * WEEK_MILLIS -> "${diff / WEEK_MILLIS} weeks ago"
            diff < 30 * DAY_MILLIS -> "${diff / (7 * DAY_MILLIS)} weeks ago"
            diff < 12 * 4 * WEEK_MILLIS -> "${diff / (30 * DAY_MILLIS)} months ago"
            else -> formatTimestamp(timestamp, PATTERN_READABLE_DATE)
        }
    }
    
    /**
     * Get the start of the day (00:00:00) for the given timestamp
     */
    fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
    
    /**
     * Get the end of the day (23:59:59.999) for the given timestamp
     */
    fun getEndOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return calendar.timeInMillis
    }
    
    /**
     * Add days to a timestamp
     */
    fun addDays(timestamp: Long, days: Int): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            add(Calendar.DAY_OF_YEAR, days)
        }
        return calendar.timeInMillis
    }
    
    /**
     * Calculate the difference in days between two timestamps
     */
    fun getDaysDifference(from: Long, to: Long = currentTimeMillis()): Int {
        val diff = getStartOfDay(to) - getStartOfDay(from)
        return (diff / DAY_MILLIS).toInt()
    }
    
    /**
     * Format a duration in milliseconds to a human-readable string (e.g., "2h 30m")
     */
    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60)) % 24
        val days = durationMs / (1000 * 60 * 60 * 24)
        
        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
    
    /**
     * Check if two timestamps are on the same day
     */
    fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    /**
     * Get the current time in ISO 8601 format
     */
    fun getCurrentIso8601(): String {
        return formatTimestamp(currentTimeMillis(), PATTERN_ISO_8601)
    }
    
    /**
     * Get the current time in a human-readable format
     */
    fun getCurrentReadableTime(): String {
        return formatTimestamp(currentTimeMillis(), PATTERN_READABLE_TIME)
    }
    
    /**
     * Get the current date in a human-readable format
     */
    fun getCurrentReadableDate(): String {
        return formatTimestamp(currentTimeMillis(), PATTERN_READABLE_DATE)
    }
    
    /**
     * Get the current date and time in a human-readable format
     */
    fun getCurrentReadableDateTime(): String {
        return formatTimestamp(currentTimeMillis(), PATTERN_READABLE_DATE_TIME)
    }
    
    /**
     * Get a timestamp for a specific date and time
     */
    fun getTimestamp(
        year: Int,
        month: Int, // 0-11
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0
    ): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
    
    /**
     * Get the age in years from a birth date timestamp
     */
    fun getAge(birthDate: Long): Int {
        val birthCalendar = Calendar.getInstance().apply { timeInMillis = birthDate }
        val now = Calendar.getInstance()
        
        var age = now.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
        
        // Adjust age if birthday hasn't occurred yet this year
        if (now.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        
        return age
    }
}
