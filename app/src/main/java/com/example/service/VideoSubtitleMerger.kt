package com.example.service

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Subtitle burning is unavailable until a real video-rendering engine is integrated.
 * Never copy an unchanged video and label it as a hardsub result.
 */
object VideoSubtitleMerger {
    suspend fun mergeSubtitlesIntoVideo(
        context: Context,
        sourceVideoFile: File,
        srtContent: String,
        videoTitle: String,
        onProgress: suspend (Int) -> Unit
    ): File {
        if (!sourceVideoFile.isFile || sourceVideoFile.length() <= 0L) {
            throw IOException("ملف الفيديو الأصلي غير موجود أو فارغ.")
        }
        if (srtContent.isBlank()) {
            throw IOException("لا توجد ترجمة صالحة لدمجها.")
        }
        throw IOException(
            "دمج الترجمة داخل الصورة غير متاح بعد؛ لم ننشئ ملفاً مزيفاً أو نغيّر امتداد الفيديو فقط."
        )
    }
}
