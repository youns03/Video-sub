package com.example

import com.example.service.DeepLTranslator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLTranslatorTest {
    @Test
    fun sendsCurrentDeepLAuthorizationHeaderAndArabicTargetLanguage() {
        var authorization: String? = null
        var formBody = ""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            authorization = request.header("Authorization")
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            formBody = buffer.readUtf8()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"translations":[{"text":"مرحبا"}]}""".toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }.build()

        val translated = DeepLTranslator.requestBatchAt(
            baseUrl = "https://deepl.test",
            apiKey = "unit-test-secret",
            texts = listOf("Bonjour"),
            httpClient = client
        )

        assertEquals("DeepL-Auth-Key unit-test-secret", authorization)
        assertTrue(formBody.contains("source_lang=FR"))
        assertTrue(formBody.contains("target_lang=AR"))
        assertTrue(formBody.contains("text=Bonjour"))
        assertEquals(listOf("مرحبا"), translated)
    }
}
