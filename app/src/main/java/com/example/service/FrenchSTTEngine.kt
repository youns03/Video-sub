package com.example.service

import com.example.data.model.SubtitleSegment
import java.io.IOException

/**
 * Speech transcription is deliberately fail-closed until a real speech-recognition
 * model is integrated. Never emit scripted dialogue as if it came from the video.
 */
object FrenchSTTEngine {
    const val MAX_CHARS_PER_LINE = 40
    const val MIN_DURATION_MS = 1400L
    const val MAX_DURATION_MS = 6500L
    const val PAUSE_BETWEEN_CUES_MS = 120L

    suspend fun transcribeAudioWithStudioStandards(
        durationSeconds: Int,
        onProgress: suspend (progress: Int, currentSegmentText: String) -> Unit
    ): List<SubtitleSegment> {
        if (durationSeconds <= 0) {
            throw IOException("تعذر قراءة مدة الفيديو. لم يبدأ تفريغ الصوت.")
        }
        throw IOException(
            "محرك التفريغ الصوتي الحقيقي غير مدمج بعد. أوقفنا إنشاء ترجمة تجريبية قد لا تطابق الفيديو."
        )
    }
}
