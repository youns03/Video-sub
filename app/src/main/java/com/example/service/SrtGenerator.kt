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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SrtGenerator {

    /**
     * Builds a standard compliant SubRip (.srt) file content.
     */
    fun buildSrtContent(segments: List<SubtitleSegment>, includeFrenchSubtitle: Boolean = false): String {
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

        val exportDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Subtitles")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        val file = File(exportDir, fileName)
        FileOutputStream(file).use { output ->
            output.write(srtContent.toByteArray(Charsets.UTF_8))
        }
        return file
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
        context.startActivity(Intent.createChooser(intent, "مشاركة ملف الترجمة SRT"))
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
