package com.example.service

import kotlin.math.roundToLong

/**
 * Converts Sherpa-ONNX Whisper token start times into word intervals.
 * Sherpa exposes token start times; a word ends at the next word's start, while
 * the final word uses a short bounded estimate inside the decoded audio window.
 */
object WhisperTokenAligner {
    private data class TokenPiece(val text: String, val startMs: Long, val startsWord: Boolean)
    private data class WordStart(val text: String, val startMs: Long)

    fun align(
        tokens: Array<String>,
        timestampsSeconds: FloatArray,
        windowStartMs: Long,
        windowEndMs: Long
    ): List<TimedWord> {
        if (tokens.isEmpty() || tokens.size != timestampsSeconds.size || windowEndMs <= windowStartMs) {
            return emptyList()
        }

        val pieces = ArrayList<TokenPiece>(tokens.size)
        var previousTime = -1L
        for (index in tokens.indices) {
            val raw = tokens[index]
            if (raw.isBlank() || isSpecialToken(raw)) continue
            val seconds = timestampsSeconds[index]
            if (!seconds.isFinite() || seconds < 0f) return emptyList()
            val relativeMs = (seconds * 1000.0).roundToLong()
            val absoluteMs = (windowStartMs + relativeMs).coerceIn(windowStartMs, windowEndMs)
            if (previousTime >= 0L && absoluteMs < previousTime) return emptyList()
            previousTime = absoluteMs

            val decoded = raw
                .replace("Ġ", " ")
                .replace("▁", " ")
            val text = decoded.trim()
            if (text.isEmpty() || text.all { it.isWhitespace() }) continue
            val punctuationOnly = text.all { it in PUNCTUATION }
            val startsWord = decoded.firstOrNull()?.isWhitespace() == true && !punctuationOnly
            pieces += TokenPiece(text, absoluteMs, startsWord)
        }
        if (pieces.isEmpty()) return emptyList()

        val starts = ArrayList<WordStart>()
        val current = StringBuilder()
        var currentStart = -1L
        for (piece in pieces) {
            if (piece.startsWord && current.isNotEmpty()) {
                starts += WordStart(current.toString().trim(), currentStart)
                current.setLength(0)
                currentStart = -1L
            }
            if (current.isEmpty()) currentStart = piece.startMs
            // A punctuation token normally has no leading space and is appended to its word.
            current.append(piece.text)
        }
        if (current.isNotEmpty()) starts += WordStart(current.toString().trim(), currentStart)
        if (starts.isEmpty() || starts.any { it.text.isBlank() }) return emptyList()
        if (starts.size > 1) {
            val alignmentSpan = starts.last().startMs - starts.first().startMs
            val minimumSpan = maxOf(40L, minOf(150L, ((windowEndMs - windowStartMs) * 0.03).roundToLong()))
            if (alignmentSpan < minimumSpan) return emptyList()
        }

        val result = ArrayList<TimedWord>(starts.size)
        for (index in starts.indices) {
            val word = starts[index]
            val nextStart = starts.getOrNull(index + 1)?.startMs
            val proposedEnd = if (nextStart != null && nextStart > word.startMs) {
                minOf(nextStart, word.startMs + MAX_WORD_INTERVAL_MS)
            } else {
                minOf(windowEndMs, word.startMs + LAST_WORD_INTERVAL_MS)
            }
            val end = proposedEnd.coerceAtMost(windowEndMs)
            if (end > word.startMs) result += TimedWord(word.startMs, end, word.text)
        }
        return result
    }

    private fun isSpecialToken(value: String): Boolean =
        value.startsWith("<|") && value.endsWith("|>")

    private const val MAX_WORD_INTERVAL_MS = 900L
    private const val LAST_WORD_INTERVAL_MS = 600L
    private val PUNCTUATION = setOf('.', ',', ';', ':', '?', '!', '…', ')', ']', '»', '”', '،', '؛', '\'')
}
