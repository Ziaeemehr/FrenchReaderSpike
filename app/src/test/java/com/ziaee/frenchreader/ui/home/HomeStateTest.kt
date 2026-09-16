package com.ziaee.frenchreader.ui.home

import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateTest {
    @Test
    fun `cached headlines remain present while a refresh is in progress`() {
        val state = composeHomeState(
            headlines = listOf(headline()),
            recentTexts = emptyList(),
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
            recentTexts = emptyList(),
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
            recentTexts = emptyList(),
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
            recentTexts = many,
            continueReading = null,
            isRefreshing = false,
            sourceErrors = emptyList(),
            importingKey = null
        )

        assertEquals(5, state.recentTexts.size)
        assertEquals(many.take(5).map { it.id }, state.recentTexts.map { it.id })
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
}
