package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ArabicTranslator {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Translates French text into natural, contextual Arabic suitable for video subtitles.
     */
    suspend fun translateFrenchToArabic(frenchText: String): String = withContext(Dispatchers.IO) {
        val trimmed = frenchText.trim()
        if (trimmed.isEmpty()) return@withContext ""

        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://api.mymemory.translated.net/get?q=$encodedQuery&langpair=fr|ar"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SubVidApp/1.0")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw java.io.IOException("خدمة الترجمة أعادت HTTP ${response.code}.")
                }
                val responseBody = response.body?.string()
                    ?: throw java.io.IOException("خدمة الترجمة أعادت استجابة فارغة.")
                val json = JSONObject(responseBody)
                val responseStatus = json.optInt("responseStatus", 200)
                val responseData = json.optJSONObject("responseData")
                val translatedText = responseData?.optString("translatedText").orEmpty().trim()

                if (responseStatus != 200 ||
                    translatedText.isBlank() ||
                    translatedText.contains("MYMEMORY WARNING", ignoreCase = true) ||
                    translatedText.equals(trimmed, ignoreCase = true)
                ) {
                    throw java.io.IOException("لم تُرجع خدمة الترجمة نتيجة عربية موثوقة؛ لم نستخدم ترجمة تخمينية.")
                }
                formatArabicSubtitleText(translatedText)
            }
        } catch (error: Exception) {
            if (error is java.io.IOException) throw error
            throw java.io.IOException("تعذر الاتصال بخدمة الترجمة. تحقق من الإنترنت ثم أعد المحاولة.", error)
        }
    }

    /**
     * Formats Arabic subtitle text:
     * - Fixes Arabic punctuation (؟ ،)
     * - Breaks long lines (> 42 chars) into balanced 2-line subtitles
     */
    fun formatArabicSubtitleText(text: String): String {
        var formatted = text
            .replace("?", "؟")
            .replace(",", "،")
            .replace(";", "؛")
            .trim()

        // If line is too long for mobile screen, split cleanly at word boundary
        if (formatted.length > 44 && !formatted.contains("\n")) {
            val words = formatted.split(" ")
            val mid = words.size / 2
            val line1 = words.subList(0, mid).joinToString(" ")
            val line2 = words.subList(mid, words.size).joinToString(" ")
            formatted = "$line1\n$line2"
        }

        return formatted
    }
}
