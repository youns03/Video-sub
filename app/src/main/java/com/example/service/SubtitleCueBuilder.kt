package com.example.service

import com.example.data.model.SubtitleSegment
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Builds subtitle cues from Whisper word timestamps; never stretches a short transcript across a long VAD span. */
object SubtitleCueBuilder {
    const val LEAD_IN_MS = 100L
    const val TAIL_MS = 220L
    const val MAX_CHARS_PER_SECOND = 20.0
    const val MAX_CHARS_PER_LINE = 42
    const val MAX_LINES = 2
    const val MAX_CUE_DURATION_MS = 6_000L
    const val MIN_GAP_MS = 80L
    private const val SENTENCE_PAUSE_MS = 250L
    private data class Cue(val start: Long, val end: Long, val text: String)

    private val whitespace = Regex("\\s+")
    private val nonSpeechMarkup = Regex("\\[[^\\]]*]|\\([^)]*\\)|[♪♫]|\\*[^*]*\\*")
    private val blockedPhrases = listOf(
        "amara.org", "sous-titres réalisés par",
        "sous-titrage société radio-canada", "sous-titrage st'"
    )
    private val sentenceEnd = Regex("[.!?…]+[»”\\\"']?$")
    private val clauseEnd = Regex("[,;:،؛]$")
    private val frenchConnectors = setOf("et", "mais", "que", "qui", "parce", "car", "donc", "quand", "lorsque")
    private val cannotEndWith = setOf(
        "le", "la", "les", "un", "une", "des", "du", "de", "d'", "à", "au", "aux", "en", "dans", "sur", "pour", "par",
        "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles", "me", "te", "se", "ne", "n'",
        "a", "as", "avons", "avez", "ont", "suis", "es", "est", "sommes", "êtes", "sont", "peut", "doit"
    )

    fun normalize(text: String): String = text.replace('\u00A0', ' ').replace(whitespace, " ").trim()

    fun build(raw: List<RecognizedUtterance>, offsetMs: Long, durationMs: Long): List<SubtitleSegment> {
        if (durationMs <= 0L) return emptyList()
        val alignedWords = ArrayList<TimedWord>()
        val coarseCues = ArrayList<Cue>()

        for (utterance in raw.sortedBy { it.startMs }) {
            val text = normalize(utterance.text)
            val start = (utterance.startMs + offsetMs).coerceAtLeast(0L)
            val end = (utterance.endMs + offsetMs).coerceAtMost(durationMs)
            if (text.isEmpty() || start >= durationMs || end <= start || isLikelyHallucination(text, end - start)) continue

            val words = utterance.words.mapNotNull { word ->
                val wordText = normalize(word.text)
                val wordStart = (word.startMs + offsetMs).coerceIn(start, end)
                val wordEnd = (word.endMs + offsetMs).coerceIn(wordStart, end)
                if (wordText.isBlank() || wordEnd <= wordStart) null
                else TimedWord(wordStart, wordEnd, wordText)
            }
            if (words.isEmpty()) {
                // Keep a short recognizer interval intact, but never export a long unaligned
                // transcript as one stretched cue. The backend must retry it on smaller windows.
                val coarseStart = max(start - LEAD_IN_MS, 0L)
                val coarseEnd = min(end + TAIL_MS, durationMs)
                require(coarseEnd - coarseStart <= MAX_CUE_DURATION_MS) {
                    "تعذر تحديد توقيت الكلمات بدقة لمقطع طويل؛ أعد التفريغ بنوافذ أصغر."
                }
                coarseCues += Cue(coarseStart, coarseEnd, text)
            } else {
                alignedWords += words
            }
        }

        val deduplicatedWords = removeOverlapDuplicates(alignedWords.sortedBy { it.startMs })
        val alignedCues = buildCuesFromWords(deduplicatedWords, durationMs)
        val deduplicatedCoarseCues = removeCoarseBoundaryDuplicates(coarseCues.sortedBy { it.start })
        val ordered = (deduplicatedCoarseCues + alignedCues).sortedBy { it.start }
        val nonOverlapping = enforceNonOverlap(ordered, durationMs)
        return nonOverlapping.mapIndexedNotNull { index, cue ->
            if (cue.end <= cue.start || cue.text.isBlank()) null
            else SubtitleSegment(
                id = index + 1,
                startTimeMs = cue.start,
                endTimeMs = cue.end,
                frenchText = cue.text,
                arabicText = ""
            )
        }
    }

    private fun buildCuesFromWords(words: List<TimedWord>, durationMs: Long): List<Cue> {
        if (words.isEmpty()) return emptyList()
        val cues = ArrayList<Cue>()
        var current = ArrayList<TimedWord>()

        fun emit() {
            if (current.isEmpty()) return
            val text = joinWords(current.map { it.text })
            val start = max(current.first().startMs - LEAD_IN_MS, 0L)
            val end = min(current.last().endMs + TAIL_MS, durationMs)
            if (end > start && text.isNotBlank()) cues += Cue(start, end, text)
            current = ArrayList()
        }

        for (word in words) {
            val previous = current.lastOrNull()
            if (previous != null && shouldBreakBefore(current, previous, word)) emit()
            current += word

            val text = joinWords(current.map { it.text })
            val elapsed = current.last().endMs - current.first().startMs
            val tooLarge = exceedsDisplayLimits(text, elapsed)
            if (tooLarge && current.size > 1) {
                val last = current.removeAt(current.lastIndex)
                emit()
                current += last
                if (exceedsDisplayLimits(last.text, last.endMs - last.startMs)) emit()
            } else if (endsSentence(word.text)) {
                emit()
            }
        }
        emit()
        return cues
    }

    private fun shouldBreakBefore(current: List<TimedWord>, previous: TimedWord, next: TimedWord): Boolean {
        val gap = next.startMs - previous.endMs
        val previousWord = previous.text.lowercase(Locale.FRENCH).trimEnd('.', ',', ';', ':', '?', '!')
        val nextWord = next.text.lowercase(Locale.FRENCH).trimStart('’', '\'').trimEnd('.', ',', ';', ':', '?', '!')
        val protectedPair = previousWord in cannotEndWith ||
            (nextWord == "pas" && current.takeLast(3).any { it.text.lowercase(Locale.FRENCH).trimEnd('.', ',', ';', ':', '?', '!') == "ne" })
        if (gap >= SENTENCE_PAUSE_MS && !protectedPair) return true
        if (clauseEnd.containsMatchIn(previous.text) && current.size >= 4 && !protectedPair) return true
        if (nextWord in frenchConnectors && current.size >= 4 && joinWords(current.map { it.text }).length >= 28) return true
        // Prefer to keep grammatical pairs together when limits allow it.
        return false
    }

    private fun exceedsDisplayLimits(text: String, durationMs: Long): Boolean {
        if (durationMs + LEAD_IN_MS + TAIL_MS > MAX_CUE_DURATION_MS) return true
        val lines = wrapForMeasurement(text)
        if (lines.size > MAX_LINES) return true
        if (lines.any { it.length > MAX_CHARS_PER_LINE } && text.trim().split(whitespace).size > 1) return true
        // A short speech interval must not be split merely because that would raise CPS further.
        // High reading speed is surfaced by SubtitleQualityAnalyzer for review.
        return false
    }

    private fun wrapForMeasurement(text: String): List<String> {
        val words = normalize(text).split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val lines = ArrayList<String>()
        var line = StringBuilder()
        for (word in words) {
            val required = if (line.isEmpty()) word.length else line.length + 1 + word.length
            if (required > MAX_CHARS_PER_LINE && line.isNotEmpty()) {
                lines += line.toString()
                line = StringBuilder(word)
            } else {
                if (line.isNotEmpty()) line.append(' ')
                line.append(word)
            }
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    private fun removeOverlapDuplicates(words: List<TimedWord>): List<TimedWord> {
        val result = ArrayList<TimedWord>(words.size)
        for (word in words) {
            val previous = result.lastOrNull()
            if (previous != null && normalizedWord(previous.text) == normalizedWord(word.text) &&
                word.startMs < previous.endMs - 30L) {
                // Adjacent chunk overlap can decode a boundary word twice. Keep the earlier copy.
                continue
            }
            result += word
        }
        return result
    }

    private fun removeCoarseBoundaryDuplicates(cues: List<Cue>): List<Cue> {
        val result = ArrayList<Cue>(cues.size)
        for (cue in cues) {
            val previous = result.lastOrNull()
            val audioWindowsOverlap = previous != null &&
                cue.start + LEAD_IN_MS < previous.end - TAIL_MS
            if (!audioWindowsOverlap) {
                result += cue
                continue
            }
            val previousWords = normalize(previous.text).split(' ')
            var currentWords = normalize(cue.text).split(' ')
            val maxOverlap = min(4, min(previousWords.size, currentWords.size))
            var duplicateCount = 0
            for (count in maxOverlap downTo 1) {
                val suffix = previousWords.takeLast(count).map(::normalizedWord)
                val prefix = currentWords.take(count).map(::normalizedWord)
                if (suffix == prefix) {
                    duplicateCount = count
                    break
                }
            }
            if (duplicateCount == 0) {
                val firstCurrentWord = currentWords.firstOrNull()?.let(::normalizedWord).orEmpty()
                if (firstCurrentWord.length >= 4 &&
                    previousWords.takeLast(3).map(::normalizedWord).contains(firstCurrentWord)) {
                    duplicateCount = 1
                }
            }
            if (duplicateCount > 0) currentWords = currentWords.drop(duplicateCount)
            val remaining = normalize(currentWords.joinToString(" "))
            if (remaining.isNotBlank()) result += cue.copy(text = remaining)
        }
        return result
    }

    private fun normalizedWord(text: String): String =
        text.lowercase(Locale.FRENCH).trim().trim('.', ',', ';', ':', '?', '!', '…', '’', '\'')

    private fun enforceNonOverlap(cues: List<Cue>, durationMs: Long): List<Cue> {
        if (cues.isEmpty()) return emptyList()
        val result = ArrayList<Cue>(cues.size)
        for (index in cues.indices) {
            val cue = cues[index]
            val previousEnd = result.lastOrNull()?.end
            val nextStart = cues.getOrNull(index + 1)?.start
            val start = if (previousEnd == null) cue.start else max(cue.start, previousEnd + MIN_GAP_MS)
            val endLimit = nextStart?.minus(MIN_GAP_MS) ?: durationMs
            val end = min(cue.end, endLimit).coerceAtMost(durationMs)
            if (end > start) result += cue.copy(start = start, end = end)
        }
        return result
    }

    private fun joinWords(words: List<String>): String {
        val result = StringBuilder()
        for (wordValue in words) {
            val word = wordValue.trim()
            if (word.isEmpty()) continue
            val punctuation = word.firstOrNull()?.let {
                it in setOf('.', ',', ';', ':', '?', '!', '…', ')', ']', '»', '”', '،', '؛')
            } == true
            if (result.isNotEmpty() && !punctuation) result.append(' ')
            result.append(word)
        }
        return normalize(result.toString())
    }

    private fun endsSentence(text: String): Boolean = sentenceEnd.containsMatchIn(text.trim())

    fun isLikelyHallucination(text: String, durationMs: Long): Boolean {
        val clean = normalize(text)
        if (clean.isEmpty()) return true
        val lower = clean.lowercase(Locale.FRENCH).replace('’', '\'')
        if (blockedPhrases.any(lower::contains)) return true
        if (nonSpeechMarkup.replace(clean, "").none { it.isLetterOrDigit() }) return true
        if (durationMs > 0L && clean.length * 1000.0 / durationMs > 35.0) return true
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
