package com.ziaee.frenchreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class VocabSrsTest {
    @Test fun likelyFrenchRejectsArabicAndNonLatinFronts() {
        assertTrue(isLikelyFrench("bonjour"))
        assertTrue(isLikelyFrench("C'est déjà l'été."))
        assertFalse(isLikelyFrench("سلام"))
        assertFalse(isLikelyFrench("bonjour سلام"))
        assertFalse(isLikelyFrench("12345"))
    }
    private val now = 1_000_000L
    private val utc = ZoneId.of("UTC")

    @Test
    fun `again returns card to box one and schedules the next calendar day`() {
        val result = VocabSrs.apply(entry(box = 4, learned = true), VocabAnswer.FORGOT, now, zoneId = utc)

        assertEquals(1, result.leitnerBox)
        assertEquals(VocabSrs.DAY_MS, result.nextReviewAtMs)
        assertEquals(now, result.lastReviewedAtMs)
        assertFalse(result.learned)
    }

    @Test
    fun `hard keeps the box and uses its full interval`() {
        val source = entry(box = 3)

        val result = VocabSrs.apply(source, VocabAnswer.HARD, now, zoneId = utc)

        assertEquals(3, result.leitnerBox)
        assertEquals(4L * VocabSrs.DAY_MS, result.nextReviewAtMs)
        assertEquals(4L, VocabSrs.previewIntervalDays(source, VocabAnswer.HARD))
    }

    @Test
    fun `knew advances the box and uses the new box interval`() {
        val result = VocabSrs.apply(entry(box = 2), VocabAnswer.KNEW, now, zoneId = utc)

        assertEquals(3, result.leitnerBox)
        assertEquals(4L * VocabSrs.DAY_MS, result.nextReviewAtMs)
        assertFalse(result.learned)
    }

    @Test
    fun `good caps at box five and graduates it`() {
        val promoted = VocabSrs.apply(entry(box = 4), VocabAnswer.KNEW, now, zoneId = utc)
        val capped = VocabSrs.apply(entry(box = 5), VocabAnswer.KNEW, now, zoneId = utc)

        assertEquals(5, promoted.leitnerBox)
        assertTrue(promoted.learned)
        assertEquals(5, capped.leitnerBox)
        assertEquals(16L * VocabSrs.DAY_MS, capped.nextReviewAtMs)
        assertTrue(capped.learned)
    }

    @Test
    fun `custom intervals are applied from the start of the current day`() {
        val intervals = listOf(2L, 5L, 9L, 20L, 40L)

        val result = VocabSrs.apply(entry(box = 1), VocabAnswer.KNEW, now, intervals, utc)

        assertEquals(2, result.leitnerBox)
        assertEquals(5L * VocabSrs.DAY_MS, result.nextReviewAtMs)
        assertEquals(5L, VocabSrs.previewIntervalDays(entry(box = 1), VocabAnswer.KNEW, intervals))
    }

    @Test
    fun `overdue timing does not shorten or extend the configured interval`() {
        val muchLater = 20L * VocabSrs.DAY_MS + now

        val result = VocabSrs.apply(entry(box = 2), VocabAnswer.HARD, muchLater, zoneId = utc)

        assertEquals(22L * VocabSrs.DAY_MS, result.nextReviewAtMs)
    }

    @Test
    fun `status maps learning stages and honors learned flag`() {
        assertEquals(VocabStatus.NEW, entry(box = 1).status())
        assertEquals(VocabStatus.LEARNING, entry(box = 2, reviewedAt = 1L).status())
        assertEquals(VocabStatus.REVIEWING, entry(box = 4, reviewedAt = 1L).status())
        assertEquals(VocabStatus.LEARNED, entry(box = 5, reviewedAt = 1L).status())
        assertEquals(VocabStatus.LEARNED, entry(box = 1, reviewedAt = 1L, learned = true).status())
    }

    private fun entry(
        box: Int,
        reviewedAt: Long? = null,
        learned: Boolean = false
    ) = VocabEntry(
        word = "mot",
        sentence = "Une phrase.",
        textId = 1,
        dictionaryUrl = "https://example.test",
        learned = learned,
        leitnerBox = box,
        lastReviewedAtMs = reviewedAt
    )
}
