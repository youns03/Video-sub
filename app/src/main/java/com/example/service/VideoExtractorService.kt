package com.example.service

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ExtractedVideo(
    val title: String,
    val durationSeconds: Int,
    val mediaUrl: String,
    val httpHeaders: Map<String, String> = emptyMap()
)

object VideoExtractorService {
    private val baseUrl: String
        get() = BuildConfig.EXTRACTOR_BASE_URL.trimEnd('/')

    suspend fun extract(pageUrl: String): ExtractedVideo = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            throw IOException("لم يتم إعداد خادم استخراج Facebook وYouTube في نسخة التطبيق")
        }

        val connection = (URL("$baseUrl/extract").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val payload = JSONObject().put("url", pageUrl).toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            val json = runCatching { JSONObject(responseBody) }.getOrNull()
            if (responseCode !in 200..299) {
                val detail = json?.optString("detail").orEmpty()
                throw IOException(detail.ifBlank { "تعذر استخراج الفيديو من الرابط (HTTP $responseCode)" })
            }
            val response = json ?: throw IOException("استجابة خادم الاستخراج غير صالحة")
            val mediaUrl = response.optString("media_url").orEmpty()
            if (mediaUrl.isBlank()) throw IOException("لم يعثر خادم الاستخراج على ملف فيديو قابل للتنزيل")
            ExtractedVideo(
                title = response.optString("title", "فيديو مستخرج"),
                durationSeconds = response.optInt("duration_seconds", 1).coerceAtLeast(1),
                mediaUrl = mediaUrl,
                httpHeaders = response.optJSONObject("http_headers")?.let { headers ->
                    headers.keys().asSequence().associateWith { key -> headers.optString(key) }
                }.orEmpty()
            )
        } finally {
            connection.disconnect()
        }
    }
}
