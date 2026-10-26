package com.example.service

import android.content.Context
import kotlin.math.abs

object ArabicTranslator {
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun translateFrenchToArabic(frenchText: String): String {
        val context = appContext ?: throw IllegalStateException("لم تتم تهيئة إعدادات DeepL بعد.")
        return DeepLTranslator.translateOne(context, frenchText)
    }

    /** Normalize Arabic punctuation and wrap long text at a balanced linguistic boundary. */
    fun formatArabicSubtitleText(text: String): String {
        val normalized = text
            .replace("?", "؟")
            .replace(",", "،")
            .replace(";", "؛")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.length <= MAX_LINE_CHARS) return normalized

        val words = normalized.split(' ').filter(String::isNotBlank)
        if (words.size < 2) return normalized
        val candidates = ArrayList<Pair<String, String>>()
        val left = StringBuilder()
        for (index in 0 until words.lastIndex) {
            if (left.isNotEmpty()) left.append(' ')
            left.append(words[index])
            val right = words.drop(index + 1).joinToString(" ")
            candidates += left.toString() to right
        }

        val feasible = candidates.filter { (first, second) ->
            first.length <= MAX_LINE_CHARS && second.length <= MAX_LINE_CHARS
        }
        val pool = feasible.ifEmpty { candidates }
        val best = pool.minByOrNull { (first, second) ->
            val balancePenalty = abs(first.length - second.length)
            val punctuationBonus = if (first.lastOrNull()?.let { it in PREFERRED_BREAKS } == true) -12 else 0
            val overLimitPenalty = (first.length - MAX_LINE_CHARS).coerceAtLeast(0) * 4 +
                (second.length - MAX_LINE_CHARS).coerceAtLeast(0) * 4
            balancePenalty + punctuationBonus + overLimitPenalty
        } ?: return normalized
        return "${best.first}\n${best.second}"
    }

    private const val MAX_LINE_CHARS = 42
    private val PREFERRED_BREAKS = setOf('،', '؛', '؟', '.', '!', ':')
}
