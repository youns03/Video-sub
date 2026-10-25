package com.example

import com.example.data.local.SubtitleConverters
import com.example.data.model.PlatformType
import com.example.data.model.SubtitleSegment
import com.example.service.ArabicTranslator
import com.example.service.SrtGenerator
import com.example.service.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.IOException

class SubVidLogicTest {

    @Test
    fun testUrlValidator_FacebookLinks() {
        val fbReel = UrlValidator.validate("https://www.facebook.com/reel/1083920194827110")
        assertTrue(fbReel.isValid)
        assertEquals(PlatformType.FACEBOOK, fbReel.platform)

        val fbWatch = UrlValidator.validate("https://fb.watch/mXz90Qla-1/")
        assertTrue(fbWatch.isValid)
        assertEquals(PlatformType.FACEBOOK, fbWatch.platform)
    }

    @Test
    fun testUrlValidator_YouTubeLinks() {
        val yt = UrlValidator.validate("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(yt.isValid)
        assertEquals(PlatformType.YOUTUBE, yt.platform)
    }

    @Test
    fun testUrlValidator_InvalidLinks() {
        val empty = UrlValidator.validate("")
        assertFalse(empty.isValid)

        val badProto = UrlValidator.validate("not_a_url")
        assertFalse(badProto.isValid)
    }

    @Test
    fun testSubtitleSegment_TimestampFormatting() {
        val segment = SubtitleSegment(
            id = 1,
            startTimeMs = 1250L,
            endTimeMs = 4800L,
            frenchText = "Bonjour le monde",
            arabicText = "مرحباً بالعالم"
        )

        assertEquals("00:00:01,250", segment.formattedStartTime)
        assertEquals("00:00:04,800", segment.formattedEndTime)
        assertEquals(3550L, segment.durationMs)

        val srtBlock = segment.toSrtBlock(1)
        assertTrue(srtBlock.contains("00:00:01,250 --> 00:00:04,800"))
        assertTrue(srtBlock.contains("مرحباً بالعالم"))
    }

    @Test
    fun testSrtGenerator_ContentBuilder() {
        val segments = listOf(
            SubtitleSegment(1, 0L, 2000L, "Bonjour", "مرحباً"),
            SubtitleSegment(2, 2500L, 5000L, "Au revoir", "مع السلامة")
        )

        val srt = SrtGenerator.buildSrtContent(segments)
        assertTrue(srt.contains("1\n00:00:00,000 --> 00:00:02,000\nمرحباً"))
        assertTrue(srt.contains("2\n00:00:02,500 --> 00:00:05,000\nمع السلامة"))
    }

    @Test
    fun testArabicTranslator_PunctuationNormalization() {
        val formatted = ArabicTranslator.formatArabicSubtitleText("كيف حالك اليوم? شكراً, نحن بخير")
        assertTrue(formatted.contains("؟"))
        assertTrue(formatted.contains("،"))
        assertFalse(formatted.contains("?"))
    }

    @Test
    fun testTranscriptionFailsClosedInsteadOfInventingDialogue() = runBlocking {
        try {
            com.example.service.FrenchSTTEngine.transcribeAudioWithStudioStandards(30) { _, _ -> }
            throw AssertionError("Transcription must not return fabricated dialogue")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("غير مدمج"))
        }
    }

    @Test
    fun testImportedVideoIsNotClaimedToBeTranscodedTo360p() {
        assertEquals("Original source (no transcoding)", com.example.service.VideoDownloaderService.TARGET_RESOLUTION)
    }
}
