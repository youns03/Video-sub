package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.model.SubtitleSegment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SrtGenerator {

    private fun validateSegments(segments: List<SubtitleSegment>) {
        require(segments.isNotEmpty()) { "لا توجد مقاطع ترجمة لتصديرها." }
        require(segments.all {
            it.startTimeMs >= 0L && it.endTimeMs > it.startTimeMs && it.arabicText.isNotBlank()
        }) {
            "توجد مقاطع ترجمة فارغة أو ذات توقيت غير صالح."
        }
        require(segments.zipWithNext().all { (first, second) ->
            second.startTimeMs >= first.startTimeMs
        }) {
            "ترتيب توقيتات الترجمة غير صحيح."
        }
        require(segments.zipWithNext().all { (first, second) ->
            first.endTimeMs <= second.startTimeMs
        }) {
            "توجد مقاطع SRT متداخلة زمنيًا؛ راجع حدود البداية والنهاية."
        }
    }

    /**
     * Builds a standard compliant SubRip (.srt) file content.
     */
    fun buildSrtContent(segments: List<SubtitleSegment>, includeFrenchSubtitle: Boolean = false): String {
        validateSegments(segments)
        return buildString {
            segments.forEachIndexed { index, seg ->
                append("${index + 1}\n")
                append("${seg.formattedStartTime} --> ${seg.formattedEndTime}\n")
                append("${seg.arabicText}\n")
                if (includeFrenchSubtitle && seg.frenchText.isNotBlank()) {
                    append("(${seg.frenchText})\n")
                }
                append("\n")
            }
        }
    }

    /**
     * Builds WebVTT format content.
     */
    fun buildVttContent(segments: List<SubtitleSegment>): String {
        validateSegments(segments)
        return buildString {
            append("WEBVTT\n\n")
            segments.forEachIndexed { index, seg ->
                val start = seg.formattedStartTime.replace(',', '.')
                val end = seg.formattedEndTime.replace(',', '.')
                append("${index + 1}\n")
                append("$start --> $end\n")
                append("${seg.arabicText}\n\n")
            }
        }
    }

    /**
     * Saves SRT content to local storage with a clean, descriptive filename.
     */
    fun saveSrtToFile(context: Context, srtContent: String, videoTitle: String): File {
        val sanitizedTitle = videoTitle.replace("[^a-zA-Z0-9ء-ي_\\-]".toRegex(), "_").take(30)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Sub_${sanitizedTitle}_$timestamp.srt"

        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: throw IOException("مساحة تخزين المستندات غير متاحة؛ لم يتم حفظ ملف SRT.")
        val exportDir = File(documentsDir, "Subtitles")
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw IOException("تعذر إنشاء مجلد حفظ الترجمة؛ تحقق من مساحة التخزين.")
        }

        val file = File(exportDir, fileName)
        val temporaryFile = File(exportDir, ".$fileName.part")
        try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(srtContent.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!temporaryFile.renameTo(file)) {
                throw IOException("تعذر اعتماد ملف SRT النهائي؛ تحقق من مساحة التخزين.")
            }
            return file
        } catch (error: Exception) {
            temporaryFile.delete()
            throw error
        }
    }

    /**
     * Shares SRT file or text to external apps (WhatsApp, Telegram, Drive, Gmail, etc.)
     */
    fun shareSrtFile(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ملف ترجمة عربية: $title")
            putExtra(Intent.EXTRA_TEXT, "ملف ترجمة بصيغة SRT متزامن مع الفيديو: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة ملف الترجمة SRT").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Copies SRT text directly to Android clipboard.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "SRT الترجمة") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }
}
