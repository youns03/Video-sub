package com.example.service

import android.content.Context
import kotlinx.coroutines.CancellationException
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

/** Resumable, checksum-verified downloads for multilingual Whisper INT8 models and Silero VAD. */
object WhisperModelManager {
    enum class ModelSize(
        val displayName: String,
        val approximateSizeMb: Int,
        val directory: String,
        val marker: String
    ) {
        TINY("Whisper Tiny INT8 + Silero VAD", 105, "stt/whisper-tiny", ".tiny-int8-verified-v1"),
        BASE("Whisper Base INT8 + Silero VAD", 170, "stt/whisper", ".base-int8-verified-v1")
    }

    private const val PREFS_NAME = "whisper_model_settings"
    private const val SELECTED_MODEL_KEY = "selected_model"
    private const val BASE_REVISION = "31dc0cc3b59ba4a0b52941f1d6dce9c0e30311b4"
    private const val TINY_REVISION = "65176e2deb88badc814a94058666cadccc29b61c"

    private data class ModelFile(
        val name: String,
        val url: String,
        val sha256: String?,
        val minBytes: Long,
        val maxBytes: Long
    )

    private fun filesFor(model: ModelSize): List<ModelFile> = when (model) {
        ModelSize.BASE -> listOf(
            ModelFile("base-encoder.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/" + BASE_REVISION + "/base-encoder.int8.onnx",
                "38d196d447cf451f98193eb013ef1e0d23d83e6967031607497eb9eecbb0a06c",
                25_000_000L, 40_000_000L),
            ModelFile("base-decoder.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/" + BASE_REVISION + "/base-decoder.int8.onnx",
                "f0b4d0314d591215f96fbfd78636fa524f51c5949688535b0ebd7f2b5b1f19a3",
                110_000_000L, 140_000_000L),
            ModelFile("base-tokens.txt",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/" + BASE_REVISION + "/base-tokens.txt",
                null, 750_000L, 900_000L)
        )
        ModelSize.TINY -> listOf(
            ModelFile("tiny-encoder.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/" + TINY_REVISION + "/tiny-encoder.int8.onnx",
                "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434",
                12_000_000L, 14_000_000L),
            ModelFile("tiny-decoder.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/" + TINY_REVISION + "/tiny-decoder.int8.onnx",
                "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925",
                88_000_000L, 92_000_000L),
            ModelFile("tiny-tokens.txt",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/" + TINY_REVISION + "/tiny-tokens.txt",
                "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126", 800_000L, 850_000L)
        )
    } + ModelFile(
        "silero_vad.onnx",
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
        500_000L, 1_000_000L
    )

    fun selectedModel(context: Context): ModelSize {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(SELECTED_MODEL_KEY, ModelSize.BASE.name)
        return runCatching { ModelSize.valueOf(stored ?: ModelSize.BASE.name) }
            .getOrDefault(ModelSize.BASE)
    }

    fun selectModel(context: Context, model: ModelSize) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(SELECTED_MODEL_KEY, model.name).apply()
    }

    fun modelDir(context: Context): File = modelDir(context, selectedModel(context))

    private fun modelDir(context: Context, model: ModelSize): File = File(context.filesDir, model.directory)

    fun modelDisplayName(context: Context): String = selectedModel(context).displayName

    fun modelApproximateSizeMb(context: Context): Int = selectedModel(context).approximateSizeMb

    fun availableFreeMb(context: Context): Long = context.filesDir.usableSpace / 1_000_000L

    fun isReady(context: Context): Boolean = isReady(context, selectedModel(context))

    fun isReady(context: Context, model: ModelSize): Boolean {
        val dir = modelDir(context, model)
        return File(dir, model.marker).isFile && filesFor(model).all { spec ->
            val file = File(dir, spec.name)
            file.isFile && file.length() in spec.minBytes..spec.maxBytes
        }
    }

    fun deleteModel(context: Context): Boolean {
        val dir = modelDir(context)
        return !dir.exists() || dir.deleteRecursively()
    }

    suspend fun ensureModel(
        context: Context,
        onProgress: suspend (percent: Int, message: String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val model = selectedModel(context)
        val dir = modelDir(context, model)
        val files = filesFor(model)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("تعذر إنشاء مجلد نموذج التفريغ.")
        if (isReady(context, model)) {
            onProgress(100, "نموذج ${model.displayName} المحلي جاهز.")
            return@withContext dir
        }
        val minimumFreeBytes = if (model == ModelSize.TINY) 130L * 1024L * 1024L else 180L * 1024L * 1024L
        if (dir.usableSpace < minimumFreeBytes) {
            throw IOException("المساحة الحرة غير كافية؛ يلزم نحو ${if (model == ModelSize.TINY) 130 else 180} MB لتنزيل النموذج المحدد.")
        }
        File(dir, model.marker).delete()

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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Preserve partial downloads so a later retry can resume them.
                throw if (error is IOException) error
                else IOException("تعذر تنزيل " + spec.name + "؛ تحقق من الاتصال ثم أعد المحاولة.", error)
            }
        }
        File(dir, model.marker).writeText(model.displayName + "; files verified")
        onProgress(100, "اكتمل تنزيل " + model.displayName + " والتحقق من ملفاته.")
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
            if (code == 416 && existing > 0L) {
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
