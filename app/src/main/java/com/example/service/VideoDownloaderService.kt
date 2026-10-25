package com.example.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

    const val TARGET_RESOLUTION = "360p (SD - 640x360)"
    const val TARGET_CODEC = "MP4 (H.264 / AAC)"

    /** Downloads a local file or a direct media URL; never substitutes demo content. */
    suspend fun downloadVideo360p(
        context: Context,
        url: String,
        videoTitle: String,
        durationSeconds: Int,
        onProgress: suspend (DownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val sanitizedTitle = videoTitle.replace("[^a-zA-Z0-9ء-ي_\\-]".toRegex(), "_").take(25)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Video_360p_${sanitizedTitle}_$timestamp.mp4"

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "SubVid")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val videoFile = File(dir, fileName)

        val sourceUri = Uri.parse(url)
        when (sourceUri.scheme?.lowercase(Locale.ROOT)) {
            "content", "file" -> {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(videoFile).use { output -> input.copyTo(output) }
                } ?: error("تعذر فتح الملف المحلي المختار")
                onProgress(DownloadProgress(100, videoFile.length() / 1_000_000.0, videoFile.length() / 1_000_000.0, 0.0))
            }
            "http", "https" -> downloadDirectMediaUrl(url, videoFile, onProgress)
            else -> error("مصدر الفيديو غير مدعوم")
        }

        if (!videoFile.exists() || videoFile.length() == 0L) {
            error("لم يتم تنزيل أي بيانات من الرابط")
        }

        return@withContext videoFile
    }

    private suspend fun downloadDirectMediaUrl(
        sourceUrl: String,
        destination: File,
        onProgress: suspend (DownloadProgress) -> Unit
    ) {
        val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "SubVid/1.0 Android")
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
        val fileName = "SubVid_360p_${title.replace("[^a-zA-Z0-9ء-ي_\\-]".toRegex(), "_").take(25)}.mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SubVid")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                return uri
            }
        } else {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val targetFile = File(publicDir, fileName)
            sourceFile.copyTo(targetFile, overwrite = true)
            return FileProvider.getUriForFile(context, "${context.packageName}.provider", targetFile)
        }
        return null
    }

    /**
     * Opens Android Sharesheet to send or open the 360p MP4 video.
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
