package com.example.service

import android.content.Context
import com.example.data.model.SubtitleSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Local French speech transcription; fails closed until a real backend is registered. */
object FrenchSTTEngine {
    const val MAX_CHARS_PER_LINE = 40
    const val MIN_DURATION_MS = 1400L
    const val MAX_DURATION_MS = 6500L
    const val PAUSE_BETWEEN_CUES_MS = 120L

    @Volatile
    var backendFactory: ((Context) -> SpeechBackend)? = null

    suspend fun transcribe(
        context: Context,
        mediaPath: String,
        durationSeconds: Int,
        onProgress: suspend (progress: Int, currentSegmentText: String) -> Unit
    ): List<SubtitleSegment> = transcribe(
        source = { onChunk -> AudioDecoder.decode(mediaPath, onChunk) },
        openBackend = { backendFactory?.invoke(context) },
        durationSeconds = durationSeconds,
        onProgress = onProgress
    )

    /** JVM-testable streaming core. */
    suspend fun transcribe(
        source: PcmSource,
        openBackend: () -> SpeechBackend?,
        durationSeconds: Int,
        onProgress: suspend (progress: Int, currentSegmentText: String) -> Unit
    ): List<SubtitleSegment> {
        if (durationSeconds <= 0) throw IOException("تعذر قراءة مدة الفيديو.")
        val durationMs = durationSeconds * 1000L
        return withContext(Dispatchers.Default) {
            val backend = openBackend() ?: throw IOException(
                "محرك التفريغ الصوتي الحقيقي غير مدمج بعد. أوقفنا إنشاء ترجمة تخمينية."
            )
            backend.use { engine ->
                val raw = ArrayList<RecognizedUtterance>()
                var fedSamples = 0L
                var lastPercent = -1
                val offsetMs = source { samples ->
                    raw += engine.accept(samples)
                    fedSamples += samples.size
                    val positionMs = fedSamples * 1000L / AudioDecoder.TARGET_SAMPLE_RATE
                    val percent = (positionMs * 100L / durationMs).toInt().coerceIn(0, 99)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent, raw.lastOrNull()?.text.orEmpty())
                    }
                }
                raw += engine.finish()
                val cues = SubtitleCueBuilder.build(raw, offsetMs, durationMs)
                if (cues.isEmpty()) throw IOException("لم يُكتشف كلام فرنسي قابل للتفريغ.")
                onProgress(100, cues.last().frenchText)
                cues
            }
        }
    }
}
