package com.example.service

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

/** Silero VAD finds speech windows; multilingual Whisper transcribes each window in French. */
class SherpaWhisperBackend private constructor(
    private val vad: Vad,
    private val recognizer: OfflineRecognizer
) : SpeechBackend {
    private val sampleRate = AudioDecoder.TARGET_SAMPLE_RATE

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
            val text = recognize(speech.samples).trim()
            if (text.isEmpty()) continue
            val start = speech.start * 1000L / sampleRate
            val end = (speech.start + speech.samples.size) * 1000L / sampleRate
            results += RecognizedUtterance(start, end, text)
        }
        return results
    }

    private fun recognize(samples: FloatArray): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, sampleRate)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    override fun close() {
        vad.release()
        recognizer.release()
    }

    companion object {
        fun install() {
            FrenchSTTEngine.backendFactory = { context -> open(WhisperModelManager.modelDir(context)) }
        }

        fun open(dir: File): SherpaWhisperBackend {
            fun find(suffix: String): File? = dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(suffix) }
            val vadModel = File(dir, "silero_vad.onnx").takeIf { it.isFile }
            val encoder = find("base-encoder.int8.onnx")
            val decoder = find("base-decoder.int8.onnx")
            val tokens = find("base-tokens.txt")
            val missing = buildList {
                if (vadModel == null) add("silero_vad.onnx")
                if (encoder == null) add("base-encoder.int8.onnx")
                if (decoder == null) add("base-decoder.int8.onnx")
                if (tokens == null) add("base-tokens.txt")
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
                        task = "transcribe"
                    ),
                    tokens = tokens!!.absolutePath,
                    numThreads = threads,
                    modelType = "whisper"
                )
            )
            val maxSpeechSeconds = (FrenchSTTEngine.MAX_DURATION_MS -
                SubtitleCueBuilder.LEAD_IN_MS - SubtitleCueBuilder.TAIL_MS) / 1000f
            val vadConfig = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = vadModel!!.absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.3f,
                    minSpeechDuration = 0.25f,
                    windowSize = 512,
                    maxSpeechDuration = maxSpeechSeconds
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
            return SherpaWhisperBackend(vad, recognizer)
        }
    }
}
