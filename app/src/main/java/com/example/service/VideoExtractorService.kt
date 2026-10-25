package com.example.service

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class ExtractedVideo(
    val title: String,
    val durationSeconds: Int,
    val mediaUrl: String,
    val httpHeaders: Map<String, String> = emptyMap()
)

object VideoExtractorService {
    private val baseUrl: String
        get() = BuildConfig.EXTRACTOR_BASE_URL.trim().trimEnd('/')

    suspend fun extract(pageUrl: String): ExtractedVideo = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            throw IOException("لم يتم إعداد خادم استخراج Facebook وYouTube في نسخة التطبيق. يجب نشر Backend ثم إعادة بناء APK.")
        }

        val endpoint = try {
            URL("$baseUrl/extract")
        } catch (_: Exception) {
            throw IOException("عنوان خادم استخراج الفيديو غير صالح. تحقق من إعداد SUBVID_EXTRACTOR_URL.")
        }
        val connection = try {
            (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "SubVid/1.0 Android")
            }
        } catch (error: Exception) {
            throw friendlyNetworkError(error)
        }

        try {
            val payload = JSONObject().put("url", pageUrl).toString()
            try {
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            } catch (error: Exception) {
                throw friendlyNetworkError(error)
            }

            val responseCode = try {
                connection.responseCode
            } catch (error: Exception) {
                throw friendlyNetworkError(error)
            }
            val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            val json = runCatching { JSONObject(responseBody) }.getOrNull()
            if (responseCode !in 200..299) {
                val detail = json?.optString("detail").orEmpty()
                throw IOException(
                    when (responseCode) {
                        400 -> "الرابط غير مدعوم أو ليس رابط فيديو صالحًا."
                        502 -> "تعذر استخراج الفيديو. قد يكون خاصًا أو يتطلب تسجيل الدخول أو أن الموقع غيّر طريقة عرضه."
                        503, 504 -> "خدمة استخراج الفيديو مشغولة أو انتهت مهلة المعالجة. حاول مجددًا."
                        else -> detail.ifBlank { "تعذر استخراج الفيديو (HTTP $responseCode)." }
                    }
                )
            }

            val response = json ?: throw IOException("أعاد خادم الاستخراج استجابة غير صالحة.")
            val mediaUrl = response.optString("media_url").orEmpty()
            if (mediaUrl.isBlank()) throw IOException("لم يعثر خادم الاستخراج على ملف فيديو قابل للتنزيل.")
            ExtractedVideo(
                title = response.optString("title", "فيديو مستخرج"),
                durationSeconds = response.optInt("duration_seconds", 1).coerceAtLeast(1),
                mediaUrl = mediaUrl,
                httpHeaders = response.optJSONObject("http_headers")?.let { headers ->
                    headers.keys().asSequence().associateWith { key -> headers.optString(key) }
                }.orEmpty()
            )
        } catch (error: Exception) {
            if (error is IOException) throw error
            throw friendlyNetworkError(error)
        } finally {
            connection.disconnect()
        }
    }

    private fun friendlyNetworkError(error: Exception): IOException = when (error) {
        is UnknownHostException -> IOException("تعذر الوصول إلى خادم استخراج الفيديو. تحقق من الإنترنت ومن عنوان الخادم المنشور.")
        is SocketTimeoutException -> IOException("انتهت مهلة الاتصال بخادم استخراج الفيديو. حاول مجددًا بعد قليل.")
        is ConnectException -> IOException("تعذر الاتصال بخادم استخراج الفيديو. قد تكون الخدمة متوقفة مؤقتًا.")
        else -> IOException(error.message?.takeIf { it.isNotBlank() } ?: "حدث خطأ في الاتصال بخادم استخراج الفيديو.")
    }
}
