package com.example.service

import kotlin.math.sqrt

/** Index range in a mono PCM buffer. The ranges may overlap by a few milliseconds at cuts. */
data class SpeechWindow(val startSample: Int, val endSample: Int) {
    val lengthSamples: Int get() = endSample - startSample
}

/**
 * Hard-caps every audio window sent to Whisper independently of VAD's own max-duration setting.
 * Long windows are cut at the lowest-energy 180 ms region in a 10-to-20 second search band.
 */
object SpeechWindowSplitter {
    const val DEFAULT_MAX_WINDOW_SECONDS = 20
    const val DEFAULT_OVERLAP_MS = 80L
    private const val DEFAULT_MIN_CUT_SECONDS = 10
    private const val ENERGY_WINDOW_MS = 180
    private const val ENERGY_HOP_MS = 20

    fun split(
        samples: FloatArray,
        sampleRate: Int,
        maxWindowSeconds: Int = DEFAULT_MAX_WINDOW_SECONDS,
        minCutSeconds: Int = DEFAULT_MIN_CUT_SECONDS,
        overlapMs: Long = DEFAULT_OVERLAP_MS
    ): List<SpeechWindow> {
        if (samples.isEmpty() || sampleRate <= 0 || maxWindowSeconds <= 0) return emptyList()
        val overlap = (overlapMs * sampleRate / 1000L).toInt().coerceAtLeast(0)
        val maxSamples = maxWindowSeconds * sampleRate
        if (maxSamples <= 2 * overlap) return listOf(SpeechWindow(0, samples.size))
        if (samples.size <= maxSamples) return listOf(SpeechWindow(0, samples.size))

        val windows = ArrayList<SpeechWindow>()
        var logicalStart = 0
        while (logicalStart < samples.size) {
            val overlapAllowance = if (logicalStart > 0) overlap else 0
            val lastWindow = samples.size - logicalStart <= maxSamples - overlapAllowance
            if (lastWindow) {
                val start = (logicalStart - overlap).coerceAtLeast(0)
                windows += SpeechWindow(start, samples.size)
                break
            }

            // Reserve room for overlap on both sides so the actual recognizer input stays <= max.
            val logicalLimit = minOf(samples.size, logicalStart + maxSamples - 2 * overlap)
            val earliestCut = minOf(logicalLimit, logicalStart + minCutSeconds * sampleRate)
            val cut = findLowestEnergyCut(samples, earliestCut, logicalLimit, sampleRate)
                .coerceIn(logicalStart + 1, logicalLimit)
            val windowStart = (logicalStart - overlap).coerceAtLeast(0)
            val windowEnd = minOf(samples.size, cut + overlap)
            windows += SpeechWindow(windowStart, windowEnd)
            logicalStart = cut
        }
        return windows
    }

    private fun findLowestEnergyCut(
        samples: FloatArray,
        earliest: Int,
        latest: Int,
        sampleRate: Int
    ): Int {
        if (latest - earliest < sampleRate / 2) return latest
        val energyWindow = (sampleRate * ENERGY_WINDOW_MS / 1000).coerceAtLeast(1)
        val hop = (sampleRate * ENERGY_HOP_MS / 1000).coerceAtLeast(1)
        val lastStart = (latest - energyWindow).coerceAtLeast(earliest)
        var bestStart = earliest
        var bestEnergy = Double.POSITIVE_INFINITY
        var frameStart = earliest
        while (frameStart <= lastStart) {
            var sum = 0.0
            val frameEnd = frameStart + energyWindow
            for (i in frameStart until frameEnd) {
                val value = samples[i].toDouble()
                sum += value * value
            }
            val rms = sqrt(sum / energyWindow)
            if (rms < bestEnergy) {
                bestEnergy = rms
                bestStart = frameStart
            }
            frameStart += hop
        }
        return (bestStart + energyWindow / 2).coerceIn(earliest, latest)
    }
}
