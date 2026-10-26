package com.example

import com.example.service.DeepLTranslator
import okhttp3.FormBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLTranslatorTest {
    @Test
    fun buildsCurrentDeepLAuthorizationHeaderAndArabicTargetLanguage() {
        val request = DeepLTranslator.buildRequest(
            baseUrl = "https://api-free.deepl.com",
            apiKey = "unit-test-secret",
            texts = listOf("Bonjour", "Comment allez-vous ?")
        )

        assertEquals("POST", request.method)
        assertEquals("DeepL-Auth-Key unit-test-secret", request.header("Authorization"))
        assertEquals("SubVid/1.0", request.header("User-Agent"))
        assertEquals("https://api-free.deepl.com/v2/translate", request.url.toString())

        val body = request.body as FormBody
        val fields = (0 until body.size).associate { body.name(it) to body.value(it) }
        assertEquals("FR", fields["source_lang"])
        assertEquals("AR", fields["target_lang"])
        assertTrue((0 until body.size).any { body.name(it) == "text" && body.value(it) == "Bonjour" })
        assertTrue((0 until body.size).any { body.name(it) == "text" && body.value(it) == "Comment allez-vous ?" })
    }

    @Test
    fun includesSurroundingFrenchContextInTranslationRequest() {
        val request = DeepLTranslator.buildRequest(
            baseUrl = "https://api-free.deepl.com",
            apiKey = "unit-test-secret",
            texts = listOf("Le navigateur connaît la route."),
            context = "Il s'agit du navigateur d'un navire, pas d'un logiciel."
        )
        val body = request.body as FormBody
        val contextValue = (0 until body.size)
            .firstOrNull { body.name(it) == "context" }
            ?.let(body::value)
        assertEquals("Il s'agit du navigateur d'un navire, pas d'un logiciel.", contextValue)
    }
}
