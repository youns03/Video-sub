package com.example.service

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** Decodes the first audio track to streamed mono 16 kHz float PCM without buffering the whole file. */
object AudioDecoder {
    const val TARGET_SAMPLE_RATE = 16_000
    private const val CHUNK_SAMPLES = 8_000
    private const val TIMEOUT_US = 10_000L
    private const val MAX_IDLE_AFTER_EOS = 500

    suspend fun decode(mediaPath: String, onChunk: suspend (FloatArray) -> Unit): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try { extractor.setDataSource(mediaPath) }
            catch (e: Exception) { throw IOException("تعذر فتح الفيديو لاستخراج الصوت.", e) }

            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("لا يحتوي الفيديو على مسار صوت.")

            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("صيغة مسار الصوت غير معروفة.")
            val decoder = try { MediaCodec.createDecoderByType(mime) }
            catch (e: Exception) { throw IOException("لا يوجد مفكك صوت متوافق مع $mime.", e) }
            codec = decoder
            try {
                decoder.configure(inputFormat, null, null, 0)
                decoder.start()
            } catch (e: Exception) { throw IOException("تعذر بدء مفكك الصوت.", e) }

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idle = 0
            var inputRate = 0
            var channels = 0
            var floatPcm = false
            var resampler: AreaResampler? = null
            var firstPtsMs = -1L
            var chunkSize = 0
            val chunk = FloatArray(CHUNK_SAMPLES)
            var mono = FloatArray(0)
            var resampled = FloatArray(0)

            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = decoder.outputFormat
                        inputRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        if (inputRate <= 0 || channels <= 0) throw IOException("مواصفات الصوت المفكوك غير صالحة.")
                        resampler = AreaResampler(inputRate, TARGET_SAMPLE_RATE)
                    }
                    outputIndex >= 0 -> {
                        idle = 0
                        val buffer = decoder.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            if (firstPtsMs < 0) firstPtsMs = info.presentationTimeUs / 1000L
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            buffer.order(ByteOrder.LITTLE_ENDIAN)
                            val frames: Int
                            if (floatPcm) {
                                val samples = buffer.asFloatBuffer()
                                frames = samples.remaining() / channels
                                if (mono.size < frames) mono = FloatArray(frames)
                                for (f in 0 until frames) {
                                    var sum = 0f
                                    repeat(channels) { sum += samples.get() }
                                    mono[f] = sum / channels
                                }
                            } else {
                                val samples = buffer.asShortBuffer()
                                frames = samples.remaining() / channels
                                if (mono.size < frames) mono = FloatArray(frames)
                                for (f in 0 until frames) {
                                    var sum = 0
                                    repeat(channels) { sum += samples.get().toInt() }
                                    mono[f] = sum / (channels * 32768f)
                                }
                            }
                            val rs = resampler ?: throw IOException("لم يهيأ محول معدل العينات.")
                            val needed = rs.maxOutput(frames)
                            if (resampled.size < needed) resampled = FloatArray(needed)
                            val count = rs.process(mono, frames, resampled)
                            var offset = 0
                            while (offset < count) {
                                val take = minOf(count - offset, CHUNK_SAMPLES - chunkSize)
                                System.arraycopy(resampled, offset, chunk, chunkSize, take)
                                chunkSize += take
                                offset += take
                                if (chunkSize == CHUNK_SAMPLES) {
                                    onChunk(chunk.copyOf())
                                    chunkSize = 0
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    else -> {
                        if (inputDone && ++idle > MAX_IDLE_AFTER_EOS) {
                            throw IOException("توقف مفكك الصوت قبل إنهاء الملف.")
                        }
                    }
                }
            }
            if (chunkSize > 0) onChunk(chunk.copyOf(chunkSize))
            if (firstPtsMs < 0) throw IOException("لم ينتج استخراج الصوت أي عينات.")
            return firstPtsMs
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}

/** Stateful area-average resampling suitable for streaming 44.1/48 kHz PCM down to 16 kHz. */
internal class AreaResampler(inputRate: Int, outputRate: Int) {
    private val step = inputRate.toDouble() / outputRate
    private var weighted = 0.0
    private var filled = 0.0

    fun maxOutput(inputLength: Int): Int = (inputLength / step).toInt() + 2

    fun process(input: FloatArray, length: Int, output: FloatArray): Int {
        var written = 0
        for (i in 0 until length) {
            val sample = input[i].toDouble()
            var remaining = 1.0
            while (remaining > 1e-9) {
                val needed = step - filled
                if (remaining >= needed - 1e-9) {
                    weighted += sample * needed
                    output[written++] = (weighted / step).toFloat()
                    weighted = 0.0
                    filled = 0.0
                    remaining -= needed
                } else {
                    weighted += sample * remaining
                    filled += remaining
                    remaining = 0.0
                }
            }
        }
        return written
    }
}
