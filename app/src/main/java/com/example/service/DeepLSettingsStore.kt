package com.example.service

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DeepLSettingsStore(context: Context) {
    enum class Plan { AUTO, FREE, PRO }

    data class Settings(val plan: Plan, val hasApiKey: Boolean)

    private val prefs = context.getSharedPreferences("deepl_secure_settings", Context.MODE_PRIVATE)

    fun read(): Settings = Settings(
        plan = runCatching { Plan.valueOf(prefs.getString("plan", Plan.AUTO.name)!!) }.getOrDefault(Plan.AUTO),
        hasApiKey = prefs.contains("api_key_cipher")
    )

    fun save(plan: Plan, apiKey: String) {
        val normalized = apiKey.trim()
        require(normalized.isNotEmpty()) { "أدخل مفتاح DeepL أولاً." }
        prefs.edit()
            .putString("plan", plan.name)
            .putString("api_key_cipher", encrypt(normalized))
            .apply()
    }

    fun clearApiKey() {
        prefs.edit().remove("api_key_cipher").apply()
    }

    internal fun apiKey(): String {
        val encoded = prefs.getString("api_key_cipher", null)
            ?: throw IllegalStateException("لم يتم ضبط مفتاح DeepL من الإعدادات.")
        return decrypt(encoded)
    }

    internal fun baseUrls(): List<String> = when (read().plan) {
        Plan.FREE -> listOf("https://api-free.deepl.com", "https://api.deepl.com")
        Plan.PRO -> listOf("https://api.deepl.com", "https://api-free.deepl.com")
        Plan.AUTO -> if (apiKey().endsWith(":fx", ignoreCase = true)) {
            listOf("https://api-free.deepl.com", "https://api.deepl.com")
        } else {
            listOf("https://api.deepl.com", "https://api-free.deepl.com")
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP))
        return payload.toString()
    }

    private fun decrypt(value: String): String {
        val payload = JSONObject(value)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(payload.getString("iv"), Base64.NO_WRAP))
        )
        return String(cipher.doFinal(Base64.decode(payload.getString("data"), Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }

    companion object { private const val KEY_ALIAS = "subvid_deepl_api_key" }
}
