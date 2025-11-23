package ireader.common.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Utility object for parsing dates from various formats commonly found in web novels.
 * Provides thread-safe date parsing with support for relative dates (e.g., "2 hours ago").
 */
object DateParser {
    
    /**
     * Common date formats used across different sources.
     * Add new formats as needed for different sources.
     */
    private val dateFormats = listOf(
        SimpleDateFormat("MMM dd, yyyy", Locale.US),
        SimpleDateFormat("MMM dd,yyyy", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US),
        SimpleDateFormat("MM/dd/yyyy", Locale.US),
    )
    
    /**
     * Parses a date string that may be in relative format (e.g., "2 hours ago")
     * or absolute format (e.g., "Jan 15, 2024").
     * 
     * @param dateStr The date string to parse
     * @return Unix timestamp in milliseconds, or 0 if parsing fails
     */
    fun parseRelativeOrAbsoluteDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        
        return when {
            "ago" in dateStr.lowercase() -> parseRelativeDate(dateStr)
            else -> parseAbsoluteDate(dateStr)
        }
    }
    
    /**
     * Parses relative date strings like "2 hours ago", "3 days ago", etc.
     * 
     * @param dateStr The relative date string
     * @return Unix timestamp in milliseconds, or 0 if parsing fails
     */
    fun parseRelativeDate(dateStr: String): Long {
        return try {
            val parts = dateStr.lowercase().split(' ')
            if (parts.size < 2) return 0L
            
            val value = parts[0].toIntOrNull() ?: return 0L
            val unit = parts[1]
            
            Calendar.getInstance().apply {
                when {
                    "sec" in unit || "second" in unit -> add(Calendar.SECOND, -value)
                    "min" in unit || "minute" in unit -> add(Calendar.MINUTE, -value)
                    "hour" in unit -> add(Calendar.HOUR_OF_DAY, -value)
                    "day" in unit -> add(Calendar.DATE, -value)
                    "week" in unit -> add(Calendar.DATE, -value * 7)
                    "month" in unit -> add(Calendar.MONTH, -value)
                    "year" in unit -> add(Calendar.YEAR, -value)
                    else -> return 0L
                }
            }.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Parses absolute date strings using common date formats.
     * 
     * @param dateStr The absolute date string
     * @return Unix timestamp in milliseconds, or 0 if parsing fails
     */
    fun parseAbsoluteDate(dateStr: String): Long {
        for (format in dateFormats) {
            try {
                return format.parse(dateStr)?.time ?: continue
            } catch (e: Exception) {
                continue
            }
        }
        return 0L
    }
    
    /**
     * Adds a custom date format to the parser.
     * Useful for sources with unique date formats.
     * 
     * @param pattern The SimpleDateFormat pattern
     * @param locale The locale to use (defaults to US)
     */
    fun addCustomFormat(pattern: String, locale: Locale = Locale.US) {
        (dateFormats as MutableList).add(SimpleDateFormat(pattern, locale))
    }
}
