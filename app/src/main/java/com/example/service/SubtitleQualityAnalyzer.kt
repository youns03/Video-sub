package com.example.service

import com.example.data.model.SubtitleSegment
import java.util.Locale

/** Non-blocking editorial warnings for timing and reading-speed issues in an SRT draft. */
object SubtitleQualityAnalyzer {
    data class Warning(val segmentIndex: Int, val message: String)

    fun analyze(segments: List<SubtitleSegment>): List<Warning> {
        val warnings = ArrayList<Warning>()
        for (index in segments.indices) {
            val segment = segments[index]
            val durationMs = segment.endTimeMs - segment.startTimeMs
            val arabic = segment.arabicText.trim()
            if (durationMs <= 0L) {
                warnings += Warning(index, "المقطع ${index + 1}: مدة التوقيت غير صالحة.")
                continue
            }

            val charsPerSecond = arabic.length * 1000.0 / durationMs
            if (durationMs > SubtitleCueBuilder.MAX_CUE_DURATION_MS) {
                warnings += Warning(index, "المقطع ${index + 1}: مدته تتجاوز 6 ثوانٍ.")
            }
            if (durationMs > 5_000L && arabic.isNotEmpty() && charsPerSecond < 8.0) {
                warnings += Warning(index, "المقطع ${index + 1}: نص قليل ظاهر مدة طويلة؛ راجع التوقيت أو التفريغ.")
            }
            if (charsPerSecond > SubtitleCueBuilder.MAX_CHARS_PER_SECOND) {
                warnings += Warning(index, "المقطع ${index + 1}: سرعة القراءة تتجاوز 20 حرفًا في الثانية.")
            }

            val next = segments.getOrNull(index + 1)
            if (next != null && segment.endTimeMs > next.startTimeMs) {
                warnings += Warning(index, "المقطعان ${index + 1} و${index + 2}: يتداخل توقيت العرض بينهما.")
            }
            if (next != null && !endsWithPunctuation(segment.arabicText) &&
                next.startTimeMs - segment.endTimeMs < 250L) {
                warnings += Warning(index, "المقطع ${index + 1}: ينتهي بلا ترقيم ولا توقف واضح قبل التالي.")
            }
            if (next != null && lastWord(segment.frenchText).isNotEmpty() &&
                lastWord(segment.frenchText) == firstWord(next.frenchText)) {
                warnings += Warning(index, "المقطعان ${index + 1} و${index + 2}: كلمة مكررة عند الحد بينهما.")
            }
        }
        return warnings.distinctBy { it.segmentIndex to it.message }
    }

    private fun endsWithPunctuation(text: String): Boolean =
        text.trim().lastOrNull()?.let { it in setOf('.', '!', '?', '؟', '…', '؛', '،', ':') } == true

    private fun firstWord(text: String): String =
        text.trim().split(Regex("\\s+")).firstOrNull()?.let(::normalizeWord).orEmpty()

    private fun lastWord(text: String): String =
        text.trim().split(Regex("\\s+")).lastOrNull()?.let(::normalizeWord).orEmpty()

    private fun normalizeWord(word: String): String = word.lowercase(Locale.FRENCH)
        .trim('.', ',', ';', ':', '?', '!', '…', '؟', '،', '؛', '’', '\'')
}
