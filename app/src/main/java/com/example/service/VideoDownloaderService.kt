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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

    const val SOURCE_QUALITY_LABEL = "Original source (no transcoding)"

    /** Copies only a user-selected local video URI into app-private storage. */
    suspend fun importVideoToAppStorage(
        context: Context,
        source: Uri,
        videoTitle: String,
        onProgress: suspend (DownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val scheme = source.scheme?.lowercase(Locale.ROOT)
        require(scheme == "content" || scheme == "file") {
            "اختر ملف فيديو من الهاتف؛ لا تُقبل الروابط في هذه النسخة."
        }

        val displayName = if (scheme == "content") {
            runCatching {
                context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull()
        } else source.lastPathSegment

        val sourceSizeBytes = if (scheme == "file") {
            File(source.path ?: "").takeIf { it.isFile }?.length() ?: 0L
        } else {
            runCatching {
                context.contentResolver.query(source, arrayOf(OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
                    } ?: 0L
            }.getOrDefault(0L)
        }
        val extensionFromName = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        val extension = extensionFromName
            ?.takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
            ?: when (context.contentResolver.getType(source)?.lowercase(Locale.ROOT)) {
                "video/mp4" -> "mp4"
                "video/webm" -> "webm"
                "video/3gpp" -> "3gp"
                "video/quicktime" -> "mov"
                "video/x-matroska" -> "mkv"
                else -> "video"
            }
        val sanitizedTitle = videoTitle.replace("[^a-zA-Z0-9ء-ي_\\-]".toRegex(), "_")
            .take(25).ifBlank { "video" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
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
            val input = if (scheme == "content") {
                context.contentResolver.openInputStream(source)
                    ?: error("تعذر فتح ملف الفيديو المختار.")
            } else {
                val sourceFile = File(source.path ?: error("مسار الملف المحلي غير صالح."))
                if (!sourceFile.isFile || !sourceFile.canRead()) error("ملف الفيديو المحلي غير موجود أو غير قابل للقراءة.")
                sourceFile.inputStream()
            }
            input.use { stream ->
                FileOutputStream(partialFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = stream.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(
                            DownloadProgress(
                                percent = if (sourceSizeBytes > 0L) {
                                    ((copied * 100L) / sourceSizeBytes).toInt().coerceIn(0, 99)
                                } else 0,
                                downloadedMb = copied / 1_000_000.0,
                                totalMb = sourceSizeBytes / 1_000_000.0,
                                speedMbps = 0.0
                            )
                        )
                    }
                }
            }
            if (!partialFile.isFile || partialFile.length() == 0L) {
                error("لم يتم استيراد أي بيانات من ملف الفيديو.")
            }
            if (!videoFile.delete() && videoFile.exists()) {
                error("تعذر تجهيز ملف الفيديو المستورد.")
            }
            if (!partialFile.renameTo(videoFile)) {
                error("تعذر اعتماد ملف الفيديو المستورد؛ تحقق من مساحة التخزين المتاحة.")
            }
            onProgress(
                DownloadProgress(
                    percent = 100,
                    downloadedMb = videoFile.length() / 1_000_000.0,
                    totalMb = videoFile.length() / 1_000_000.0,
                    speedMbps = 0.0
                )
            )
            videoFile
        } catch (error: kotlinx.coroutines.CancellationException) {
            partialFile.delete()
            videoFile.delete()
            throw error
        } catch (error: Exception) {
            partialFile.delete()
            videoFile.delete()
            throw error
        }
    }

    /**
     * Exports the imported original video to public user storage (Movies/SubVid)
     */
    fun saveVideoToUserGallery(context: Context, sourceFile: File, title: String): Uri? {
        if (!sourceFile.isFile || sourceFile.length() <= 0L) return null

        val sanitizedTitle = title.replace("[^a-zA-Z0-9ء-ي_\\-]".toRegex(), "_").take(25).ifBlank { "video" }
        val extension = sourceFile.extension.lowercase(Locale.ROOT)
            .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "video"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "SubVid_${sanitizedTitle}_$timestamp.$extension"
        val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?.takeIf { it.startsWith("video/") } ?: return null

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
        require(videoFile.isFile && videoFile.length() > 0L) { "ملف الفيديو غير موجود أو فارغ." }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", videoFile)
        val mimeType = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(videoFile.extension.lowercase(Locale.ROOT)) ?: "video/*"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "فيديو: $title")
            putExtra(Intent.EXTRA_TEXT, "فيديو مستورد من الهاتف عبر تطبيق SubVid.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الفيديو الأصلي").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
