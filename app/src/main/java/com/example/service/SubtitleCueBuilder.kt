package com.example.service

import com.example.data.model.SubtitleSegment
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Validates, cleans and times speech results before any subtitle is exported. */
object SubtitleCueBuilder {
    const val LEAD_IN_MS = 150L
    const val TAIL_MS = 200L
    const val MAX_CHARS_PER_SECOND = 35.0
    private const val MAX_LINES = 2
    private data class Cue(val start: Long, val end: Long, val text: String)

    private val whitespace = Regex("\\s+")
    private val nonSpeechMarkup = Regex("\\[[^\\]]*]|\\([^)]*\\)|[♪♫]|\\*[^*]*\\*")
    private val blockedPhrases = listOf(
        "amara.org", "sous-titres réalisés par",
        "sous-titrage société radio-canada", "sous-titrage st'"
    )

    fun normalize(text: String): String = text.replace('\u00A0', ' ').replace(whitespace, " ").trim()

    fun build(raw: List<RecognizedUtterance>, offsetMs: Long, durationMs: Long): List<SubtitleSegment> {
        if (durationMs <= 0) return emptyList()
        val cleaned = ArrayList<Cue>()
        for (u in raw.sortedBy { it.startMs }) {
            val text = normalize(u.text)
            val start = (u.startMs + offsetMs).coerceAtLeast(0L)
            val end = (u.endMs + offsetMs).coerceAtMost(durationMs)
            if (text.isEmpty() || start >= durationMs || end <= start) continue
            if (isLikelyHallucination(text, end - start)) continue
            cleaned += Cue(start, end, text)
        }

        val ordered = ArrayList<Cue>()
        for (cue in cleaned) {
            val previousEnd = ordered.lastOrNull()?.end ?: 0L
            val start = max(cue.start, previousEnd)
            if (cue.end > start) ordered += Cue(start, cue.end, cue.text)
        }

        val pieces = ordered.flatMap(::split)
        val starts = LongArray(pieces.size) { i ->
            val c = pieces[i]
            if (i == 0) max(c.start - LEAD_IN_MS, 0L)
            else min(c.start, max(c.start - LEAD_IN_MS, pieces[i - 1].end + FrenchSTTEngine.PAUSE_BETWEEN_CUES_MS))
        }
        val result = ArrayList<SubtitleSegment>(pieces.size)
        for (i in pieces.indices) {
            val cue = pieces[i]
            val limit = if (i == pieces.lastIndex) durationMs
                else max(cue.end, starts[i + 1] - FrenchSTTEngine.PAUSE_BETWEEN_CUES_MS)
            var end = max(cue.end, min(cue.end + TAIL_MS, limit))
            if (end - starts[i] < FrenchSTTEngine.MIN_DURATION_MS) {
                end = max(end, min(starts[i] + FrenchSTTEngine.MIN_DURATION_MS, limit))
            }
            if (end <= starts[i]) continue
            result += SubtitleSegment(
                id = result.size + 1, startTimeMs = starts[i], endTimeMs = end,
                frenchText = cue.text, arabicText = ""
            )
        }
        return result
    }

    private fun split(cue: Cue): List<Cue> {
        val maxChars = FrenchSTTEngine.MAX_CHARS_PER_LINE * MAX_LINES
        val duration = cue.end - cue.start
        val parts = max(
            ceil(cue.text.length / maxChars.toDouble()).toInt(),
            ceil(duration / FrenchSTTEngine.MAX_DURATION_MS.toDouble()).toInt()
        ).coerceAtLeast(1)
        val words = cue.text.split(' ')
        if (parts == 1 || words.size < parts) return listOf(cue)

        val groups = ArrayList<String>()
        val target = cue.text.length.toDouble() / parts
        val current = StringBuilder()
        for ((index, word) in words.withIndex()) {
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
            val remainingWords = words.size - index - 1
            val remainingGroups = parts - groups.size - 1
            if (groups.size < parts - 1 && current.length >= target && remainingWords >= remainingGroups) {
                groups += current.toString()
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) groups += current.toString()
        val total = groups.sumOf { it.length }.coerceAtLeast(1)
        var cursor = cue.start
        var consumed = 0
        return groups.mapIndexed { index, group ->
            consumed += group.length
            val end = if (index == groups.lastIndex) cue.end else cue.start + duration * consumed / total
            Cue(cursor, end, group).also { cursor = end }
        }
    }

    fun isLikelyHallucination(text: String, durationMs: Long): Boolean {
        val clean = normalize(text)
        if (clean.isEmpty()) return true
        val lower = clean.lowercase(Locale.FRENCH).replace('’', '\'')
        if (blockedPhrases.any(lower::contains)) return true
        if (nonSpeechMarkup.replace(clean, "").none { it.isLetterOrDigit() }) return true
        if (durationMs > 0 && clean.length * 1000.0 / durationMs > MAX_CHARS_PER_SECOND) return true
        val tokens = lower.split(' ').map { it.trim { c -> !c.isLetterOrDigit() } }.filter(String::isNotEmpty)
        for (n in 1..4) {
            val minRepeats = if (n == 1) 6 else 4
            var i = 0
            while (i + n * minRepeats <= tokens.size) {
                var j = i + n
                var repeats = 1
                while (j + n <= tokens.size && tokens.subList(j, j + n) == tokens.subList(i, i + n)) {
                    repeats++
                    j += n
                }
                if (repeats >= minRepeats) return true
                i++
            }
        }
        return false
    }
}
