package com.example.service

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import java.io.IOException

/** Silero VAD finds speech; Whisper transcribes bounded windows and returns token alignment when available. */
class SherpaWhisperBackend private constructor(
    private val vad: Vad,
    private val recognizer: OfflineRecognizer,
    private val recognizerConfig: OfflineRecognizerConfig
) : SpeechBackend {
    private val sampleRate = AudioDecoder.TARGET_SAMPLE_RATE
    private var timestampDecodeEnabled = true

    private data class Recognition(val text: String, val words: List<TimedWord>)

    override fun accept(samples: FloatArray): List<RecognizedUtterance> {
        vad.acceptWaveform(samples)
        return drain()
    }

    override fun finish(): List<RecognizedUtterance> {
        vad.flush()
        return drain()
    }

    private fun drain(): List<RecognizedUtterance> {
        val results = ArrayList<RecognizedUtterance>()
        while (!vad.empty()) {
            val speech = vad.front()
            vad.pop()
            val audio = speech.samples
            if (audio.isEmpty()) continue

            val windows = SpeechWindowSplitter.split(audio, sampleRate)
            if (audio.size > sampleRate * 10 || windows.size > 1) {
                Log.d(TAG, "VAD speech: start=${speech.start}, durationMs=${audio.size * 1000L / sampleRate}, windows=${windows.size}")
            }
            for (window in windows) {
                val segment = audio.copyOfRange(window.startSample, window.endSample)
                results += transcribeWindow(segment, speech.start.toLong() + window.startSample)
            }
        }
        return results
    }

    /**
     * A second, finer pass is triggered only for long, suspiciously sparse output. This prevents a
     * truncated Whisper result from being treated as representative of a much longer audio region.
     */
    private fun transcribeWindow(samples: FloatArray, absoluteStartSample: Long): List<RecognizedUtterance> {
        val first = recognize(samples)
        val durationMs = samples.size * 1000L / sampleRate
        val density = if (durationMs > 0L) first.text.length * 1000.0 / durationMs else 0.0
        if (first.words.isEmpty() && durationMs > COARSE_WINDOW_RETRY_TRIGGER_MS) {
            Log.w(TAG, "Whisper returned no token alignment for ${durationMs}ms; retrying in short windows")
            // Do not turn a transcript without alignment into one long cue. Re-decode short,
            // overlapping audio windows so every fallback cue remains anchored to real audio.
            val shortWindows = SpeechWindowSplitter.split(
                samples = samples,
                sampleRate = sampleRate,
                maxWindowSeconds = COARSE_FALLBACK_WINDOW_SECONDS,
                minCutSeconds = COARSE_FALLBACK_MIN_CUT_SECONDS
            )
            if (shortWindows.size > 1) {
                val shortResults = shortWindows.mapNotNull { window ->
                    val part = samples.copyOfRange(window.startSample, window.endSample)
                    val decoded = recognize(part)
                    if (decoded.text.isBlank()) null
                    else Triple(decoded, absoluteStartSample + window.startSample, window.lengthSamples)
                }
                return shortResults.map { (decoded, startSample, sampleCount) ->
                    toUtterance(decoded, startSample, sampleCount)
                }
            }
        }
        if (durationMs < SPARSE_RETRY_MIN_DURATION_MS || density >= SPARSE_TEXT_CHARS_PER_SECOND) {
            return listOf(toUtterance(first, absoluteStartSample, samples.size))
        }
        Log.w(TAG, "Sparse Whisper result (${first.text.length} chars / ${durationMs}ms); retrying with smaller windows")

        val retryWindows = SpeechWindowSplitter.split(
            samples = samples,
            sampleRate = sampleRate,
            maxWindowSeconds = SPARSE_RETRY_WINDOW_SECONDS,
            minCutSeconds = SPARSE_RETRY_MIN_CUT_SECONDS
        )
        if (retryWindows.size < 2) return listOf(toUtterance(first, absoluteStartSample, samples.size))

        val retried = retryWindows.mapNotNull { window ->
            val part = samples.copyOfRange(window.startSample, window.endSample)
            val decoded = recognize(part)
            if (decoded.text.isBlank()) null
            else Triple(decoded, absoluteStartSample + window.startSample, window.lengthSamples)
        }
        val retriedCharacters = retried.sumOf { it.first.text.length }
        return if (retried.isNotEmpty() && retriedCharacters >= first.text.length * SPARSE_RETRY_IMPROVEMENT) {
            retried.map { (decoded, startSample, sampleCount) -> toUtterance(decoded, startSample, sampleCount) }
        } else {
            listOf(toUtterance(first, absoluteStartSample, samples.size))
        }
    }

    private fun toUtterance(recognition: Recognition, absoluteStartSample: Long, sampleCount: Int): RecognizedUtterance {
        val startMs = absoluteStartSample * 1000L / sampleRate
        val endMs = (absoluteStartSample + sampleCount) * 1000L / sampleRate
        val shiftedWords = recognition.words.map { word ->
            word.copy(startMs = word.startMs + startMs, endMs = word.endMs + startMs)
        }
        return RecognizedUtterance(startMs, endMs, recognition.text, shiftedWords)
    }

    private fun recognize(samples: FloatArray): Recognition {
        fun decode(): Recognition {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, sampleRate)
                recognizer.decode(stream)
                val result = recognizer.getResult(stream)
                val durationMs = samples.size * 1000L / sampleRate
                val words = if (timestampDecodeEnabled) {
                    WhisperTokenAligner.align(result.tokens, result.timestamps, 0L, durationMs)
                } else emptyList()
                return Recognition(result.text.trim(), words)
            } finally {
                stream.release()
            }
        }

        return try {
            decode()
        } catch (error: Exception) {
            if (!timestampDecodeEnabled) throw error
            // Some exported ONNX pairs omit cross-attention alignment outputs. Retry once without
            // timestamp decoding so transcription remains available rather than failing the project.
            timestampDecodeEnabled = false
            Log.w(TAG, "Whisper alignment unavailable; disabling timestamp outputs and retrying transcription", error)
            recognizerConfig.modelConfig.whisper.enableTokenTimestamps = false
            recognizerConfig.modelConfig.whisper.enableSegmentTimestamps = false
            recognizer.setConfig(recognizerConfig)
            decode()
        }
    }

    override fun close() {
        vad.release()
        recognizer.release()
    }

    companion object {
        private const val TAG = "SherpaWhisperBackend"
        private const val SPARSE_RETRY_MIN_DURATION_MS = 8_000L
        private const val SPARSE_TEXT_CHARS_PER_SECOND = 6.0
        private const val SPARSE_RETRY_WINDOW_SECONDS = 8
        private const val SPARSE_RETRY_MIN_CUT_SECONDS = 3
        private const val SPARSE_RETRY_IMPROVEMENT = 1.2
        private const val COARSE_WINDOW_RETRY_TRIGGER_MS = 5_500L
        private const val COARSE_FALLBACK_WINDOW_SECONDS = 5
        private const val COARSE_FALLBACK_MIN_CUT_SECONDS = 2

        fun install() {
            FrenchSTTEngine.backendFactory = { context, modelSize -> open(WhisperModelManager.modelDir(context, modelSize)) }
        }

        fun open(dir: File): SherpaWhisperBackend {
            fun find(suffix: String): File? = dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(suffix) }
            val vadModel = File(dir, "silero_vad.onnx").takeIf { it.isFile }
            val encoder = find("-encoder.int8.onnx")
            val decoder = find("-decoder.int8.onnx")
            val tokens = find("-tokens.txt")
            val missing = buildList {
                if (vadModel == null) add("silero_vad.onnx")
                if (encoder == null) add("encoder INT8")
                if (decoder == null) add("decoder INT8")
                if (tokens == null) add("tokens.txt")
            }
            if (missing.isNotEmpty()) {
                throw IOException("ملفات نموذج التفريغ ناقصة: " + missing.joinToString("، ") + ". أعد تنزيل النموذج.")
            }

            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            val recognizerConfig = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = AudioDecoder.TARGET_SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = encoder!!.absolutePath,
                        decoder = decoder!!.absolutePath,
                        language = "fr",
                        task = "transcribe",
                        enableTokenTimestamps = true,
                        enableSegmentTimestamps = true
                    ),
                    tokens = tokens!!.absolutePath,
                    numThreads = threads,
                    modelType = "whisper"
                )
            )
            val vadConfig = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = vadModel!!.absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.18f,
                    minSpeechDuration = 0.25f,
                    windowSize = 512,
                    maxSpeechDuration = SpeechWindowSplitter.DEFAULT_MAX_WINDOW_SECONDS.toFloat()
                ),
                sampleRate = AudioDecoder.TARGET_SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu"
            )
            val recognizer = try {
                OfflineRecognizer(assetManager = null, config = recognizerConfig)
            } catch (error: Throwable) {
                throw IOException("تعذر تحميل نموذج Whisper المحلي: " + error.message, error)
            }
            val vad = try {
                Vad(assetManager = null, config = vadConfig)
            } catch (error: Throwable) {
                recognizer.release()
                throw IOException("تعذر تحميل نموذج Silero VAD: " + error.message, error)
            }
            return SherpaWhisperBackend(vad, recognizer, recognizerConfig)
        }
    }
}
