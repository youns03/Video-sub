package com.example.service

import android.content.Context

object ArabicTranslator {
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun translateFrenchToArabic(frenchText: String): String {
        val context = appContext ?: throw IllegalStateException("لم تتم تهيئة إعدادات DeepL بعد.")
        return DeepLTranslator.translateOne(context, frenchText)
    }

    fun formatArabicSubtitleText(text: String): String {
        var formatted = text
            .replace("?", "؟")
            .replace(",", "،")
            .replace(";", "؛")
            .trim()
        if (formatted.length > 44 && !formatted.contains("\n")) {
            val words = formatted.split(" ")
            val mid = words.size / 2
            formatted = "${words.subList(0, mid).joinToString(" ")}\n${words.subList(mid, words.size).joinToString(" ")}"
        }
        return formatted
    }
}
