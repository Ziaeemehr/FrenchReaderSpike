package com.ziaee.frenchreader.news

import com.ziaee.frenchreader.data.HeadlineEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class KeywordMatcherTest {
    @Test
    fun `matching ignores accents and case across title and snippet`() {
        val headlines = listOf(
            headline("tech", "L’intelligence artificielle", "Nouveautés"),
            headline("economy", "Marchés", "L’ECONOMIE française"),
            headline("sport", "Football", "Résultats")
        )

        val result = personalizeHeadlines(headlines, "ARTIFICIELLE, economie", matchingFirst = false)

        assertEquals(listOf("tech", "economy"), result.map { it.externalId })
    }

    @Test
    fun `empty keywords leave date order unchanged in both modes`() {
        val headlines = listOf(headline("newest", "A", "B"), headline("older", "C", "D"))

        assertEquals(headlines, personalizeHeadlines(headlines, " ,\n ", matchingFirst = true))
        assertEquals(headlines, personalizeHeadlines(headlines, " ,\n ", matchingFirst = false))
    }

    @Test
    fun `matching first preserves order within matching and nonmatching groups`() {
        val headlines = listOf(
            headline("newest-miss", "Culture", "Cinéma"),
            headline("newer-match", "Science", "Espace"),
            headline("older-match", "Santé", "Science médicale"),
            headline("oldest-miss", "Sport", "Football")
        )

        val result = personalizeHeadlines(headlines, "science", matchingFirst = true)

        assertEquals(listOf("newer-match", "older-match", "newest-miss", "oldest-miss"), result.map { it.externalId })
    }

    @Test
    fun `only matching removes nonmatching headlines`() {
        val headlines = listOf(
            headline("match", "Politique européenne", "Débat"),
            headline("miss", "Sport", "Football")
        )

        val result = personalizeHeadlines(headlines, "politique\neurope", matchingFirst = false)

        assertEquals(listOf("match"), result.map { it.externalId })
    }

    private fun headline(id: String, title: String, snippet: String) = HeadlineEntity(
        sourceId = "source",
        sourceLabel = "Source",
        externalId = id,
        title = title,
        snippet = snippet,
        articleUrl = "https://example.com/$id",
        imageUrl = null,
        publishedAtMs = null,
        cachedAtMs = 0L
    )
}
