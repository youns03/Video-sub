package com.example

import com.example.service.AreaResampler
import com.example.service.FrenchSTTEngine
import com.example.service.PcmSource
import com.example.service.RecognizedUtterance
import com.example.service.SpeechBackend
import com.example.service.SpeechWindowSplitter
import com.example.service.SubtitleCueBuilder
import com.example.service.SubtitleQualityAnalyzer
import com.example.service.TimedWord
import com.example.service.WhisperTokenAligner
import com.example.data.model.SubtitleSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SttLogicTest {
    private class FakeBackend : SpeechBackend {
        var closed = false
        private var emitted = false
        override fun accept(samples: FloatArray): List<RecognizedUtterance> {
            if (emitted) return emptyList()
            emitted = true
            return listOf(RecognizedUtterance(0, 1000, "Bonjour tout le monde"))
        }
        override fun finish(): List<RecognizedUtterance> = emptyList()
        override fun close() { closed = true }
    }

    @Test
    fun resamplerProducesExpectedLengthFor48kTo16k() {
        val resampler = AreaResampler(48_000, 16_000)
        val input = FloatArray(4_800) { 0.25f }
        val output = FloatArray(resampler.maxOutput(input.size))
        val written = resampler.process(input, input.size, output)
        assertEquals(1_600, written)
        assertTrue(output.take(written).all { kotlin.math.abs(it - 0.25f) < 0.0001f })
    }

    @Test
    fun whisperTokenTimestampsGroupSubwordsAndAttachPunctuation() {
        val words = WhisperTokenAligner.align(
            tokens = arrayOf(" Bonjour", " bon", "jour", " à", " tous", "!"),
            timestampsSeconds = floatArrayOf(0.0f, 0.1f, 0.15f, 0.3f, 0.45f, 0.65f),
            windowStartMs = 5_000L,
            windowEndMs = 7_000L
        )
        assertEquals(listOf("Bonjour", "bonjour", "à", "tous!"), words.map { it.text })
        assertEquals(5_000L, words.first().startMs)
        assertEquals(5_100L, words[1].startMs)
        assertEquals(5_450L, words.last().startMs)
        assertTrue(words.zipWithNext().all { (first, second) -> first.startMs <= second.startMs })
    }

    @Test
    fun whisperTokenAlignerRejectsCollapsedTimestamps() {
        val words = WhisperTokenAligner.align(
            tokens = arrayOf(" Bonjour", " tout", " le", " monde"),
            timestampsSeconds = floatArrayOf(0f, 0f, 0f, 0f),
            windowStartMs = 0L,
            windowEndMs = 20_000L
        )
        assertTrue(words.isEmpty())
    }

    @Test
    fun cueBuilderUsesWordTimesAndEndsAtSentenceAndPauseBoundaries() {
        val cues = SubtitleCueBuilder.build(
            listOf(
                RecognizedUtterance(
                    startMs = 0L,
                    endMs = 4_000L,
                    text = "Bonjour à tous. Comment allez-vous?",
                    words = listOf(
                        TimedWord(500L, 800L, "Bonjour"),
                        TimedWord(800L, 900L, "à"),
                        TimedWord(900L, 1_200L, "tous."),
                        TimedWord(1_700L, 2_050L, "Comment"),
                        TimedWord(2_050L, 2_600L, "allez-vous?")
                    )
                )
            ),
            offsetMs = 0L,
            durationMs = 4_000L
        )
        assertEquals(2, cues.size)
        assertEquals("Bonjour à tous.", cues[0].frenchText)
        assertEquals("Comment allez-vous?", cues[1].frenchText)
        assertTrue(cues[0].endTimeMs < cues[1].startTimeMs)
        assertEquals(400L, cues[0].startTimeMs)
    }

    @Test
    fun cueBuilderRejectsLongUnalignedIntervalInsteadOfStretchingText() {
        try {
            SubtitleCueBuilder.build(
                listOf(RecognizedUtterance(0L, 110_000L, "Bonjour, bienvenue.")),
                offsetMs = 0L,
                durationMs = 120_000L
            )
            throw AssertionError("Long unaligned text must not be stretched across the audio interval")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("نوافذ أصغر"))
        }
    }

    @Test
    fun qualityAnalyzerFlagsSparseOverlappingFastAndRepeatedCues() {
        val warnings = SubtitleQualityAnalyzer.analyze(
            listOf(
                SubtitleSegment(1, 0L, 6_000L, "Bonjour", "مرحباً"),
                SubtitleSegment(2, 5_500L, 7_500L, "Bonjour encore", "السلام عليكم"),
                SubtitleSegment(3, 8_000L, 8_500L, "C'est très rapide", "هذه جملة سريعة للغاية جدًا")
            )
        ).map { it.message }
        assertTrue(warnings.any { it.contains("نص قليل") })
        assertTrue(warnings.any { it.contains("يتداخل") })
        assertTrue(warnings.any { it.contains("مكررة") })
        assertTrue(warnings.any { it.contains("سرعة القراءة") })
    }

    @Test
    fun longSpeechIsHardSplitAtLowEnergyWithoutExceedingWhisperWindowLimit() {
        val sampleRate = 16_000
        val samples = FloatArray(sampleRate * 55) { 0.1f }
        for (index in sampleRate * 14_900 / 1_000 until sampleRate * 15_250 / 1_000) samples[index] = 0f
        for (index in sampleRate * 34_800 / 1_000 until sampleRate * 35_150 / 1_000) samples[index] = 0f

        val windows = SpeechWindowSplitter.split(samples, sampleRate)
        assertTrue(windows.size >= 3)
        assertTrue(windows.all { it.lengthSamples <= sampleRate * SpeechWindowSplitter.DEFAULT_MAX_WINDOW_SECONDS })
        assertEquals(0, windows.first().startSample)
        assertEquals(samples.size, windows.last().endSample)
        assertTrue(windows.zipWithNext().all { (first, second) -> second.startSample < first.endSample })
    }

    @Test
    fun cueBuilderShiftsAndDropsKnownCreditHallucination() {
        val cues = SubtitleCueBuilder.build(
            listOf(
                RecognizedUtterance(0, 1800, "Bonjour tout le monde"),
                RecognizedUtterance(2000, 4000, "Sous-titres réalisés par la communauté d'Amara.org")
            ),
            offsetMs = 250,
            durationMs = 8_000
        )
        assertEquals(1, cues.size)
        assertEquals("Bonjour tout le monde", cues.single().frenchText)
        assertEquals(100L, cues.single().startTimeMs)
    }

    @Test
    fun transcriptionFailsClosedWithoutBackend() = runBlocking {
        val source: PcmSource = { _ -> 0L }
        try {
            FrenchSTTEngine.transcribe(source, { null }, 2) { _, _ -> }
            throw AssertionError("Missing backend must not produce subtitles")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("غير جاهز"))
        }
    }

    @Test
    fun transcriptionStreamsAndClosesBackend() = runBlocking {
        val backend = FakeBackend()
        val source: PcmSource = { onChunk ->
            onChunk(FloatArray(16_000))
            onChunk(FloatArray(16_000))
            0L
        }
        val cues = FrenchSTTEngine.transcribe(source, { backend }, 2) { _, _ -> }
        assertEquals(1, cues.size)
        assertEquals("Bonjour tout le monde", cues.single().frenchText)
        assertTrue(backend.closed)
    }

    @Test
    fun hallucinationFilterDoesNotRejectNormalShortFrenchSpeech() {
        assertFalse(SubtitleCueBuilder.isLikelyHallucination("Non, non, ce n'est pas possible.", 2000))
        assertTrue(SubtitleCueBuilder.isLikelyHallucination("[Musique]", 2000))
    }
}
