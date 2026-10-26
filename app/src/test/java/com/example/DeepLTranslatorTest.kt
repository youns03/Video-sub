package com.example

import com.example.service.DeepLTranslator
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLTranslatorTest {
    @Test
    fun sendsCurrentDeepLAuthorizationHeaderAndArabicTargetLanguage() {
        val authorization = AtomicReference<String?>()
        val formBody = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v2/translate") { exchange ->
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            formBody.set(exchange.requestBody.use { it.readBytes().toString(StandardCharsets.UTF_8) })
            val response = """{"translations":[{"text":"مرحبا"}]}""".toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val translated = DeepLTranslator.requestBatchAt(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "unit-test-secret",
                texts = listOf("Bonjour")
            )

            assertEquals("DeepL-Auth-Key unit-test-secret", authorization.get())
            assertTrue(formBody.get().orEmpty().contains("source_lang=FR"))
            assertTrue(formBody.get().orEmpty().contains("target_lang=AR"))
            assertTrue(formBody.get().orEmpty().contains("text=Bonjour"))
            assertEquals(listOf("مرحبا"), translated)
        } finally {
            server.stop(0)
        }
    }
}
