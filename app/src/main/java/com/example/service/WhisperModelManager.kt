package com.example.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** Resumable, checksum-verified download of Whisper base INT8 and Silero VAD. */
object WhisperModelManager {
    private const val MARKER = ".base-int8-verified-v1"
    private const val MIN_FREE_BYTES = 180L * 1024L * 1024L
    private const val REVISION = "31dc0cc3b59ba4a0b52941f1d6dce9c0e30311b4"

    private data class ModelFile(
        val name: String, val url: String, val sha256: String?,
        val minBytes: Long, val maxBytes: Long
    )

    private val files = listOf(
        ModelFile("base-encoder.int8.onnx",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/" + REVISION + "/base-encoder.int8.onnx",
            "38d196d447cf451f98193eb013ef1e0d23d83e6967031607497eb9eecbb0a06c",
            25_000_000L, 40_000_000L),
        ModelFile("base-decoder.int8.onnx",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/" + REVISION + "/base-decoder.int8.onnx",
            "f0b4d0314d591215f96fbfd78636fa524f51c5949688535b0ebd7f2b5b1f19a3",
            110_000_000L, 140_000_000L),
        ModelFile("base-tokens.txt",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/" + REVISION + "/base-tokens.txt",
            null, 750_000L, 900_000L),
        ModelFile("silero_vad.onnx",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
            500_000L, 1_000_000L)
    )

    fun modelDir(context: Context): File = File(context.filesDir, "stt/whisper")

    fun isReady(context: Context): Boolean {
        val dir = modelDir(context)
        return File(dir, MARKER).isFile && files.all { spec ->
            val file = File(dir, spec.name)
            file.isFile && file.length() in spec.minBytes..spec.maxBytes
        }
    }

    suspend fun ensureModel(
        context: Context,
        onProgress: suspend (percent: Int, message: String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val dir = modelDir(context)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("تعذر إنشاء مجلد نموذج التفريغ.")
        if (isReady(context)) {
            onProgress(100, "نموذج Whisper base المحلي جاهز.")
            return@withContext dir
        }
        if (dir.usableSpace < MIN_FREE_BYTES) {
            throw IOException("المساحة الحرة غير كافية؛ يلزم نحو 180 MB لتنزيل نموذج Whisper base.")
        }
        File(dir, MARKER).delete()

        for ((index, spec) in files.withIndex()) {
            currentCoroutineContext().ensureActive()
            val destination = File(dir, spec.name)
            if (isValid(destination, spec)) {
                onProgress(((index + 1) * 100) / files.size, "تم التحقق من " + spec.name + ".")
                continue
            }
            destination.delete()
            val partial = File(dir, spec.name + ".part")
            try {
                downloadOne(spec, partial) { filePercent, bytesDownloaded ->
                    val overall = ((index * 100) + filePercent.coerceIn(0, 100)) / files.size
                    onProgress(overall.coerceIn(0, 99),
                        "تنزيل " + spec.name + ": " + (bytesDownloaded / 1_000_000) + " MB")
                }
                if (!isValid(partial, spec)) {
                    partial.delete()
                    throw IOException("فشل التحقق من سلامة الملف " + spec.name + "؛ أعد المحاولة.")
                }
                if (!partial.renameTo(destination)) {
                    partial.copyTo(destination, overwrite = true)
                    partial.delete()
                }
            } catch (error: Exception) {
                // Preserve partial downloads so a later retry can resume them.
                throw if (error is IOException) error
                else IOException("تعذر تنزيل " + spec.name + "؛ تحقق من الاتصال ثم أعد المحاولة.", error)
            }
        }
        File(dir, MARKER).writeText("Whisper base INT8 + Silero VAD; verified")
        onProgress(100, "اكتمل تنزيل النموذج والتحقق من ملفاته.")
        dir
    }

    private suspend fun downloadOne(
        spec: ModelFile,
        partial: File,
        onProgress: suspend (filePercent: Int, downloadedBytes: Long) -> Unit
    ) {
        val existing = if (partial.isFile) partial.length() else 0L
        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SubVid/1.0")
            if (existing > 0L) setRequestProperty("Range", "bytes=" + existing + "-")
        }
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_REQUESTED_RANGE_NOT_SATISFIABLE && existing > 0L) {
                connection.disconnect()
                partial.delete()
                return downloadOne(spec, partial, onProgress)
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("خادم النموذج أعاد HTTP " + code + " أثناء تنزيل " + spec.name + ".")
            }
            val append = code == HttpURLConnection.HTTP_PARTIAL
            val baseBytes = if (append) existing else 0L
            if (!append) partial.delete()
            val responseLength = connection.contentLengthLong
            val totalBytes = if (responseLength > 0L) baseBytes + responseLength else -1L
            var written = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        written += count
                        val downloaded = baseBytes + written
                        val percent = if (totalBytes > 0L) (downloaded * 100L / totalBytes).toInt().coerceIn(0, 100) else 0
                        if (percent != lastPercent || written % (1024L * 1024L) < count) {
                            lastPercent = percent
                            onProgress(percent, downloaded)
                        }
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun isValid(file: File, spec: ModelFile): Boolean {
        if (!file.isFile || file.length() !in spec.minBytes..spec.maxBytes) return false
        val expected = spec.sha256 ?: return true
        return runCatching { sha256(file).equals(expected, ignoreCase = true) }.getOrDefault(false)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xff) }
    }
}
