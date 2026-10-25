package com.example.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadProgress(
    val percent: Int,
    val downloadedMb: Double,
    val totalMb: Double,
    val speedMbps: Double
)

object VideoDownloaderService {

    const val TARGET_RESOLUTION = "Original source (no transcoding)"
    const val TARGET_CODEC = "MP4 (H.264 / AAC)"

    /** Downloads a local file or a direct media URL; never substitutes demo content. */
    suspend fun downloadVideo360p(
        context: Context,
        url: String,
        videoTitle: String,
        durationSeconds: Int,
        requestHeaders: Map<String, String> = emptyMap(),
        onProgress: suspend (DownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val sanitizedTitle = videoTitle.replace("[^a-zA-Z0-9ء-ي_\\-]".toRegex(), "_").take(25)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val sourceUri = Uri.parse(url)
        val extension = when (sourceUri.scheme?.lowercase(Locale.ROOT)) {
            "content", "file" -> {
                val displayName = if (sourceUri.scheme.equals("content", true)) {
                    runCatching {
                        context.contentResolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    }.getOrNull()
                } else sourceUri.lastPathSegment
                val fromName = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
                if (!fromName.isNullOrBlank() && fromName.matches(Regex("[a-z0-9]{2,5}"))) fromName
                else when (context.contentResolver.getType(sourceUri)?.lowercase(Locale.ROOT)) {
                    "video/mp4" -> "mp4"
                    "video/webm" -> "webm"
                    "video/3gpp" -> "3gp"
                    "video/quicktime" -> "mov"
                    "video/x-matroska" -> "mkv"
                    else -> "video"
                }
            }
            else -> "mp4"
        }
        val fileName = "Video_${sanitizedTitle}_$timestamp.$extension"

        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: error("مساحة تخزين التطبيق غير متاحة حالياً.")
        val dir = File(moviesDir, "SubVid")
        if (!dir.exists() && !dir.mkdirs()) {
            error("تعذر إنشاء مجلد الفيديوهات داخل مساحة التطبيق.")
        }

        val videoFile = File(dir, fileName)
        val partialFile = File(dir, "$fileName.part")

        try {
            when (sourceUri.scheme?.lowercase(Locale.ROOT)) {
                "content" -> {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        FileOutputStream(partialFile).use { output -> input.copyTo(output) }
                    } ?: error("تعذر فتح الملف المحلي المختار")
                    onProgress(DownloadProgress(100, partialFile.length() / 1_000_000.0, partialFile.length() / 1_000_000.0, 0.0))
                }
                "file" -> {
                    val sourceFile = File(sourceUri.path ?: error("مسار الملف المحلي غير صالح"))
                    if (!sourceFile.isFile) error("ملف الفيديو المحلي غير موجود")
                    sourceFile.inputStream().use { input ->
                        FileOutputStream(partialFile).use { output -> input.copyTo(output) }
                    }
                    onProgress(DownloadProgress(100, partialFile.length() / 1_000_000.0, partialFile.length() / 1_000_000.0, 0.0))
                }
                "http", "https" -> downloadDirectMediaUrl(url, partialFile, requestHeaders, onProgress)
                else -> error("مصدر الفيديو غير مدعوم")
            }

            if (!partialFile.isFile || partialFile.length() == 0L) {
                error("لم يتم استيراد أي بيانات من ملف الفيديو")
            }
            if (!partialFile.renameTo(videoFile)) {
                error("تعذر اعتماد ملف الفيديو المستورد؛ تحقق من مساحة التخزين المتاحة.")
            }
            return@withContext videoFile
        } catch (error: Exception) {
            partialFile.delete()
            videoFile.delete()
            throw error
        }
    }

    private suspend fun downloadDirectMediaUrl(
        sourceUrl: String,
        destination: File,
        requestHeaders: Map<String, String>,
        onProgress: suspend (DownloadProgress) -> Unit
    ) {
        val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "SubVid/1.0 Android")
            requestHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) error("تعذر تنزيل الفيديو: HTTP $responseCode")
            val contentType = connection.contentType?.lowercase(Locale.ROOT).orEmpty()
            if (contentType.contains("text/html") || contentType.contains("application/json")) {
                error("هذا الرابط صفحة ويب وليس رابط ملف فيديو مباشر. روابط YouTube/Facebook تحتاج خدمة استخراج مخصصة.")
            }
            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            val startedAt = System.currentTimeMillis()
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                        val elapsedSeconds = ((System.currentTimeMillis() - startedAt).coerceAtLeast(1L)) / 1000.0
                        val speedMbps = downloadedBytes / 1_000_000.0 / elapsedSeconds
                        onProgress(DownloadProgress(percent, downloadedBytes / 1_000_000.0, if (totalBytes > 0) totalBytes / 1_000_000.0 else 0.0, speedMbps))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Exports the downloaded 360p MP4 file to public user storage (Downloads or Movies)
     */
    fun saveVideoToUserGallery(context: Context, sourceFile: File, title: String): Uri? {
        if (!sourceFile.isFile || sourceFile.length() <= 0L) return null

        val sanitizedTitle = title.replace("[^a-zA-Z0-9ء-ي_\\-]".toRegex(), "_").take(25).ifBlank { "video" }
        val extension = sourceFile.extension.lowercase(Locale.ROOT)
            .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "video"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "SubVid_${sanitizedTitle}_$timestamp.$extension"
        val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SubVid")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
            return try {
                val output = resolver.openOutputStream(uri) ?: throw java.io.IOException("تعذر فتح ملف الحفظ في المعرض.")
                output.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                }
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                uri
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        } else {
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "SubVid"
            )
            if (!publicDir.exists() && !publicDir.mkdirs()) return null
            val targetFile = File(publicDir, fileName)
            return try {
                sourceFile.copyTo(targetFile, overwrite = false)
                FileProvider.getUriForFile(context, "${context.packageName}.provider", targetFile)
            } catch (error: Exception) {
                targetFile.delete()
                null
            }
        }
    }

    /**
     * Opens Android Sharesheet to send or open the original imported video.
     */
    fun shareVideo(context: Context, videoFile: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", videoFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "فيديو MP4 بدقة 360p: $title")
            putExtra(Intent.EXTRA_TEXT, "تم تحميل هذا الفيديو بدقة 360p عبر تطبيق مترجم الفيديوهات")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الفيديو بدقة 360p"))
    }
}
