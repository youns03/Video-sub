package com.example

import com.example.service.AreaResampler
import com.example.service.FrenchSTTEngine
import com.example.service.PcmSource
import com.example.service.RecognizedUtterance
import com.example.service.SpeechBackend
import com.example.service.SubtitleCueBuilder
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
