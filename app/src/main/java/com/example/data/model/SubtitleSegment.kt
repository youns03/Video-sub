package com.example.data.model

import java.util.Locale

/**
 * Represents a single subtitle segment with precise start and end timestamps in milliseconds,
 * original French speech, translated Arabic text, and optional speaker identification.
 */
data class SubtitleSegment(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val frenchText: String,
    val arabicText: String,
    val speaker: String? = null
) {
    val durationMs: Long
        get() = (endTimeMs - startTimeMs).coerceAtLeast(0)

    val formattedStartTime: String
        get() = formatTimestamp(startTimeMs)

    val formattedEndTime: String
        get() = formatTimestamp(endTimeMs)

    val formattedTimeRange: String
        get() = "$formattedStartTime --> $formattedEndTime"

    /**
     * Converts this segment to standard SRT block format:
     * 1
     * 00:00:01,200 --> 00:00:04,500
     * النص المترجم بالعربية
     */
    fun toSrtBlock(sequenceNumber: Int, preferArabic: Boolean = true): String {
        val text = if (preferArabic) arabicText else frenchText
        return "$sequenceNumber\n$formattedStartTime --> $formattedEndTime\n$text\n"
    }

    /**
     * Converts to WebVTT format
     */
    fun toVttBlock(preferArabic: Boolean = true): String {
        val vttStart = formattedStartTime.replace(',', '.')
        val vttEnd = formattedEndTime.replace(',', '.')
        val text = if (preferArabic) arabicText else frenchText
        return "$vttStart --> $vttEnd\n$text\n"
    }

    companion object {
        fun formatTimestamp(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val ms = millis % 1000
            return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, ms)
        }

        fun parseTimestamp(timeStr: String): Long {
            return try {
                val cleaned = timeStr.trim().replace('.', ',')
                val parts = cleaned.split(":")
                if (parts.size == 3) {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val secParts = parts[2].split(",")
                    val seconds = secParts[0].toLong()
                    val millis = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                    (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
                } else {
                    0L
                }
            } catch (e: Exception) {
                0L
            }
        }
    }
}
