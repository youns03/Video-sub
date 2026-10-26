package com.example.service

import java.io.Closeable

/** A word aligned to the decoded audio timeline, in milliseconds relative to the first audio sample. */
data class TimedWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class RecognizedUtterance(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<TimedWord> = emptyList()
)

/** Streaming contract: mono 16 kHz float PCM in [-1, 1]. Times are relative to the first sample. */
interface SpeechBackend : Closeable {
    fun accept(samples: FloatArray): List<RecognizedUtterance>
    fun finish(): List<RecognizedUtterance>
}

typealias PcmSource = suspend (onChunk: suspend (FloatArray) -> Unit) -> Long
