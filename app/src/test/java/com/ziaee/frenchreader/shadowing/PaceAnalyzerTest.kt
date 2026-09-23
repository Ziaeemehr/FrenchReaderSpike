package com.ziaee.frenchreader.shadowing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaceAnalyzerTest {
    private val rate = 16_000
    private fun silence(ms: Int) = ShortArray(rate * ms / 1000)
    private fun noise(ms: Int, amp: Int) = ShortArray(rate * ms / 1000) { if (it % 2 == 0) amp.toShort() else (-amp).toShort() }
    private fun concat(vararg parts: ShortArray) = parts.fold(ShortArray(0)) { acc, p -> acc + p }

    @Test fun leadingAndTrailingSilenceTrimmed() {
        val pcm = concat(silence(800), noise(1500, 6000), silence(700))
        assertEquals(1500.0, PaceAnalyzer.speechDurationMs(pcm, rate).toDouble(), 40.0)
    }

    @Test fun shortPauseInsideSpeechIsKept() {
        val pcm = concat(noise(500, 6000), silence(300), noise(500, 6000))
        assertEquals(1300.0, PaceAnalyzer.speechDurationMs(pcm, rate).toDouble(), 40.0)
    }

    @Test fun quietBackgroundNoiseIsNotSpeech() {
        val pcm = concat(noise(1000, 150), noise(1000, 6000), noise(1000, 150))
        assertEquals(1000.0, PaceAnalyzer.speechDurationMs(pcm, rate).toDouble(), 40.0)
    }

    @Test fun ratingThresholds() {
        assertEquals(PaceRating.FAST, PaceAnalyzer.rate(0.79f))
        assertEquals(PaceRating.GOOD, PaceAnalyzer.rate(0.8f))
        assertEquals(PaceRating.GOOD, PaceAnalyzer.rate(1.3f))
        assertEquals(PaceRating.SLOW, PaceAnalyzer.rate(1.31f))
    }

    @Test fun paceComparesAgainstExpected() {
        val result = PaceAnalyzer.pace(concat(silence(500), noise(2000, 6000)), rate, expectedMs = 1000)!!
        assertEquals(2.0f, result.ratio, 0.05f)
        assertEquals(PaceRating.SLOW, result.rating)
    }

    @Test fun noPcmOrTooShortOrNoExpectedGivesNull() {
        assertNull(PaceAnalyzer.pace(null, rate, 1000))
        assertNull(PaceAnalyzer.pace(noise(200, 6000), rate, 1000))
        assertNull(PaceAnalyzer.pace(noise(1000, 6000), rate, 0))
        assertNull(PaceAnalyzer.pace(silence(1000), rate, 1000))
    }
}
