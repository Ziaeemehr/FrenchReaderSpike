package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatisticsUiStateTest {

    @Test
    fun `isTextCompleted is true when last chunk index reached the final chunk`() {
        val doc = TextDocument(
            id = 1,
            title = "t",
            rawText = "Paragraphe un.\n\nParagraphe deux.\n\nParagraphe trois.",
            lastChunkIndex = 2
        )
        assertEquals(true, isTextCompleted(doc))
    }

    @Test
    fun `isTextCompleted is false when reading stopped before the last chunk`() {
        val doc = TextDocument(
            id = 1,
            title = "t",
            rawText = "Paragraphe un.\n\nParagraphe deux.\n\nParagraphe trois.",
            lastChunkIndex = 0
        )
        assertEquals(false, isTextCompleted(doc))
    }

    @Test
    fun `leitnerBoxCounts fills every box from 1 to 5 even when empty`() {
        val entries = listOf(vocabEntry(1), vocabEntry(1), vocabEntry(3))

        val counts = leitnerBoxCounts(entries)

        assertEquals(mapOf(1 to 2, 2 to 0, 3 to 1, 4 to 0, 5 to 0), counts)
    }

    @Test
    fun `computeAccuracyPercent rounds down and handles zero reviews`() {
        assertEquals(0, computeAccuracyPercent(0, 0))
        assertEquals(66, computeAccuracyPercent(2, 3))
        assertEquals(100, computeAccuracyPercent(3, 3))
    }

    @Test
    fun `last7Days returns 7 consecutive days ending today`() {
        val today = LocalDate.of(2026, 9, 16)

        val days = last7Days(today)

        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 9, 10), days.first())
        assertEquals(today, days.last())
    }

    @Test
    fun `computeStreak counts consecutive active days ending today`() {
        val today = LocalDate.of(2026, 9, 16)
        val active = setOf(today, today.minusDays(1), today.minusDays(2))

        assertEquals(3, computeStreak(active, today))
    }

    @Test
    fun `computeStreak still counts yesterday if today has no activity yet`() {
        val today = LocalDate.of(2026, 9, 16)
        val active = setOf(today.minusDays(1), today.minusDays(2))

        assertEquals(2, computeStreak(active, today))
    }

    @Test
    fun `computeStreak resets to zero after a gap`() {
        val today = LocalDate.of(2026, 9, 16)
        val active = setOf(today.minusDays(3))

        assertEquals(0, computeStreak(active, today))
    }

    @Test
    fun `composeStatisticsState combines all derived metrics`() {
        val texts = listOf(
            TextDocument(id = 1, title = "a", rawText = "Un seul paragraphe.", lastChunkIndex = 0),
            TextDocument(id = 2, title = "b", rawText = "P1.\n\nP2.", lastChunkIndex = 0)
        )
        val vocab = listOf(vocabEntry(1), vocabEntry(5))
        val today = LocalDate.of(2026, 9, 16)

        val state = composeStatisticsState(
            texts = texts,
            vocabEntries = vocab,
            reviewedToday = 4,
            reviewedThisWeek = 10,
            knewCount = 8,
            totalReviewCount = 10,
            weeklyListening = last7Days(today).map { DailyListening(it, 0L) },
            activeDates = setOf(today),
            today = today
        )

        assertEquals(1, state.streakDays)
        assertEquals(4, state.reviewedToday)
        assertEquals(10, state.reviewedThisWeek)
        assertEquals(80, state.accuracyPercent)
        assertEquals(2, state.textsSaved)
        assertEquals(1, state.textsCompleted)
        assertEquals(2, state.wordsSaved)
        assertEquals(7, state.weeklyListening.size)
    }

    private fun vocabEntry(leitnerBox: Int) = VocabEntry(
        word = "mot",
        sentence = "Une phrase.",
        textId = 1,
        dictionaryUrl = "https://example.test",
        leitnerBox = leitnerBox
    )
}
