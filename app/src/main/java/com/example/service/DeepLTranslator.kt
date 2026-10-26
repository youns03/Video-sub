package com.example.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object DeepLTranslator {
    private const val MAX_TEXTS_PER_REQUEST = 50
    private const val MAX_CHARS_PER_REQUEST = 25_000
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun translate(context: Context, frenchTexts: List<String>): List<String> = withContext(Dispatchers.IO) {
        val store = DeepLSettingsStore(context)
        val values = frenchTexts.map { it.trim() }
        if (values.any { it.isEmpty() }) throw IOException("لا يمكن ترجمة مقطع فرنسي فارغ.")
        values.chunkedByDeepLLimits().flatMap { batch -> requestBatch(store, batch) }
    }

    suspend fun translateOne(context: Context, frenchText: String): String =
        translate(context, listOf(frenchText)).single()

    suspend fun testConnection(context: Context): String = withContext(Dispatchers.IO) {
        val result = translateOne(context, "Bonjour, ceci est un test de connexion.")
        if (result.isBlank()) throw IOException("استجاب DeepL دون نص مترجم صالح.")
        result
    }

    private fun requestBatch(store: DeepLSettingsStore, texts: List<String>): List<String> {
        val form = FormBody.Builder()
            .add("auth_key", store.apiKey())
            .add("source_lang", "FR")
            .add("target_lang", "AR")
        texts.forEach { form.add("text", it) }
        val request = Request.Builder()
            .url(store.baseUrl() + "/v2/translate")
            .post(form.build())
            .header("User-Agent", "SubVid/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    400, 403 -> "مفتاح DeepL غير صحيح أو غير صالح لهذه الخطة."
                    456 -> "تم تجاوز حد استخدام DeepL أو رصيد الحساب."
                    429 -> "طلبات DeepL كثيرة مؤقتًا؛ أعد المحاولة بعد قليل."
                    in 500..599 -> "خادم DeepL غير متاح حاليًا."
                    else -> "فشل DeepL برمز HTTP ${response.code}."
                }
                throw IOException(message)
            }
            val translations = runCatching { JSONObject(body).getJSONArray("translations") }
                .getOrElse { throw IOException("استجابة DeepL غير صالحة.") }
            if (translations.length() != texts.size) throw IOException("لم يُرجع DeepL ترجمة لكل المقاطع.")
            return (0 until translations.length()).map { index ->
                val translated = translations.getJSONObject(index).optString("text").trim()
                if (translated.isBlank()) throw IOException("أعاد DeepL نصًا مترجمًا فارغًا.")
                ArabicTranslator.formatArabicSubtitleText(translated)
            }
        }
    }

    private fun List<String>.chunkedByDeepLLimits(): List<List<String>> {
        val batches = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var chars = 0
        for (text in this) {
            if (current.isNotEmpty() && (current.size >= MAX_TEXTS_PER_REQUEST || chars + text.length > MAX_CHARS_PER_REQUEST)) {
                batches += current
                current = mutableListOf()
                chars = 0
            }
            current += text
            chars += text.length
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }
}
