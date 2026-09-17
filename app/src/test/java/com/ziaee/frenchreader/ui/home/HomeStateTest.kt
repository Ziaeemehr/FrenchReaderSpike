package com.ziaee.frenchreader.ui.home

import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeStateTest {
    @Test
    fun `cached headlines remain present while a refresh is in progress`() {
        val state = composeHomeState(
            headlines = listOf(headline()),
            allTexts = emptyList(),
            vocabEntries = emptyList(),
            continueReading = null,
            isRefreshing = true,
            sourceErrors = emptyList(),
            importingKey = null
        )

        assertTrue(state.isRefreshing)
        assertEquals(1, state.headlines.size)
    }

    @Test
    fun `one failed source with cached headlines is a non-blocking warning`() {
        val state = composeHomeState(
            headlines = listOf(headline()),
            allTexts = emptyList(),
            vocabEntries = emptyList(),
            continueReading = null,
            isRefreshing = false,
            sourceErrors = listOf("france_info"),
            importingKey = null
        )

        assertTrue(state.hasPartialError)
        assertFalse(state.isEmptyError)
    }

    @Test
    fun `no cache with both sources failing is the full empty-error state`() {
        val state = composeHomeState(
            headlines = emptyList(),
            allTexts = emptyList(),
            vocabEntries = emptyList(),
            continueReading = null,
            isRefreshing = false,
            sourceErrors = listOf("rfi_facile", "france_info"),
            importingKey = null
        )

        assertTrue(state.isEmptyError)
        assertFalse(state.hasPartialError)
    }

    @Test
    fun `recent texts are capped at five even when more are supplied`() {
        val many = (1L..8L).map { textDoc(it) }

        val state = composeHomeState(
            headlines = emptyList(),
            allTexts = many,
            vocabEntries = emptyList(),
            continueReading = null,
            isRefreshing = false,
            sourceErrors = emptyList(),
            importingKey = null
        )

        assertEquals(5, state.recentTexts.size)
        assertEquals(many.take(5).map { it.id }, state.recentTexts.map { it.id })
        assertEquals(8, state.savedTextCount)
    }

    @Test
    fun `home derives saved learned and due counts without fake goals`() {
        val now = 1_000L
        val entries = listOf(
            vocab(learned = true, nextReviewAtMs = 0L),
            vocab(learned = false, nextReviewAtMs = 999L),
            vocab(learned = false, nextReviewAtMs = 1_001L)
        )
        val state = composeHomeState(
            headlines = emptyList(),
            allTexts = listOf(textDoc(1), textDoc(2)),
            vocabEntries = entries,
            nowMs = now,
            continueReading = null,
            isRefreshing = false,
            sourceErrors = emptyList(),
            importingKey = null
        )
        assertEquals(2, state.savedTextCount)
        assertEquals(3, state.savedWordCount)
        assertEquals(1, state.learnedWordCount)
        assertEquals(1, state.dueReviewCount)
    }

    @Test
    fun `home streak reuses the shared active-date computation`() {
        val today = LocalDate.of(2026, 9, 17)
        val activeDates = setOf(today, today.minusDays(1), today.minusDays(2))
        val state = composeHomeState(
            headlines = emptyList(),
            allTexts = emptyList(),
            vocabEntries = emptyList(),
            continueReading = null,
            isRefreshing = false,
            sourceErrors = emptyList(),
            importingKey = null,
            activeDates = activeDates,
            today = today
        )
        assertEquals(3, state.streakDays)
    }

    @Test
    fun `home streak is zero with no recent activity`() {
        val today = LocalDate.of(2026, 9, 17)
        val state = composeHomeState(
            headlines = emptyList(),
            allTexts = emptyList(),
            vocabEntries = emptyList(),
            continueReading = null,
            isRefreshing = false,
            sourceErrors = emptyList(),
            importingKey = null,
            activeDates = setOf(today.minusDays(5)),
            today = today
        )
        assertEquals(0, state.streakDays)
    }

    @Test
    fun `reading metrics clamp progress and round nonempty reading time up`() {
        val doc = TextDocument(title = "Long", rawText = List(401) { "mot" }.joinToString(" "))
        val metrics = homeReadingMetrics(doc)
        assertEquals(3, metrics.estimatedTotalMinutes)
        assertTrue(metrics.progressFraction in 0f..1f)
    }

    @Test
    fun `empty text has zero estimated reading time`() {
        val doc = TextDocument(title = "Empty", rawText = "")
        val metrics = homeReadingMetrics(doc)
        assertEquals(0, metrics.estimatedTotalMinutes)
        assertEquals(0, metrics.estimatedRemainingMinutes)
    }

    @Test
    fun `a document saved at its last chunk is fully complete`() {
        val doc = TextDocument(title = "Done", rawText = List(1200) { "mot" }.joinToString(" "), lastChunkIndex = 0)
        val chunks = com.ziaee.frenchreader.text.TextChunker.chunk(doc.rawText)
        val atLastChunk = doc.copy(lastChunkIndex = chunks.size - 1)
        val metrics = homeReadingMetrics(atLastChunk)
        assertEquals(100, metrics.progressPercent)
        assertEquals(0, metrics.estimatedRemainingMinutes)
    }

    @Test
    fun `an out-of-range chunk index is clamped rather than crashing`() {
        val doc = TextDocument(title = "Weird", rawText = List(300) { "mot" }.joinToString(" "), lastChunkIndex = 999)
        val metrics = homeReadingMetrics(doc)
        assertTrue(metrics.progressFraction in 0f..1f)
        assertEquals(100, metrics.progressPercent)
    }

    private fun headline(externalId: String = "guid-1") = HeadlineEntity(
        sourceId = "rfi_facile",
        sourceLabel = "RFI",
        externalId = externalId,
        title = "Titre",
        snippet = "Résumé",
        articleUrl = "https://example.com/$externalId",
        imageUrl = null,
        publishedAtMs = 100L,
        cachedAtMs = 200L
    )

    private fun textDoc(id: Long) = TextDocument(id = id, title = "Titre $id", rawText = "Texte $id")

    private fun vocab(learned: Boolean, nextReviewAtMs: Long) = VocabEntry(
        word = "mot",
        sentence = "Une phrase.",
        textId = 1L,
        dictionaryUrl = "https://example.com",
        learned = learned,
        nextReviewAtMs = nextReviewAtMs
    )
}
