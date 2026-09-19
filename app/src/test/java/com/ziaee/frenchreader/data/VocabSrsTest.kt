package com.ziaee.frenchreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabSrsTest {
    private val now = 1_000_000L

    @Test
    fun `forgot returns card to box one and schedules it in ten minutes`() {
        val result = VocabSrs.apply(entry(box = 4, learned = true), VocabAnswer.FORGOT, now)

        assertEquals(1, result.leitnerBox)
        assertEquals(now + VocabSrs.FORGOT_DELAY_MS, result.nextReviewAtMs)
        assertEquals(now, result.lastReviewedAtMs)
        assertFalse(result.learned)
    }

    @Test
    fun `hard keeps the box and uses half its interval`() {
        val source = entry(box = 3)

        val result = VocabSrs.apply(source, VocabAnswer.HARD, now)

        assertEquals(3, result.leitnerBox)
        assertEquals(now + 7L * VocabSrs.DAY_MS / 2, result.nextReviewAtMs)
        assertEquals(7L * VocabSrs.DAY_MS / 2, VocabSrs.previewIntervalMs(source, VocabAnswer.HARD))
    }

    @Test
    fun `knew advances the box and uses the new box interval`() {
        val result = VocabSrs.apply(entry(box = 2), VocabAnswer.KNEW, now)

        assertEquals(3, result.leitnerBox)
        assertEquals(now + 7L * VocabSrs.DAY_MS, result.nextReviewAtMs)
        assertFalse(result.learned)
    }

    @Test
    fun `knew caps at box five and marks the card learned`() {
        val promoted = VocabSrs.apply(entry(box = 4), VocabAnswer.KNEW, now)
        val capped = VocabSrs.apply(entry(box = 5), VocabAnswer.KNEW, now)

        assertEquals(5, promoted.leitnerBox)
        assertTrue(promoted.learned)
        assertEquals(5, capped.leitnerBox)
        assertEquals(now + 30L * VocabSrs.DAY_MS, capped.nextReviewAtMs)
        assertTrue(capped.learned)
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
