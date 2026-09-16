package com.ziaee.frenchreader.news

import com.ziaee.frenchreader.data.HeadlineEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadlineMergerTest {
    @Test
    fun `merge sorts newest first and deduplicates normalized urls`() {
        val older = headline("older", "http://example.com/older/", publishedAtMs = 1L)
        val duplicateA = headline(
            "duplicate-a",
            "http://EXAMPLE.com/story/#source-a",
            imageUrl = "https://img.test/story.jpg",
            publishedAtMs = 2L
        )
        val newer = headline("newer", "https://example.com/newer", publishedAtMs = 3L)
        val duplicateB = headline("duplicate-b", "https://example.com/story", publishedAtMs = 4L)

        val merged = mergeHeadlines(listOf(older, duplicateA), listOf(newer, duplicateB), limit = 10)

        assertEquals(
            listOf("newer", "duplicate-a", "older"),
            merged.map { it.externalId }
        )
    }

    @Test
    fun `duplicate with image wins and null dates sort last`() {
        val noImage = headline("no-image", "https://example.com/story", publishedAtMs = 10L)
        val withImage = headline(
            "with-image",
            "http://example.com/story/#tracking",
            imageUrl = "https://img.test/story.jpg",
            publishedAtMs = null
        )
        val undated = headline("undated", "https://example.com/undated", publishedAtMs = null)

        val merged = mergeHeadlines(listOf(noImage, undated), listOf(withImage), limit = 10)

        assertEquals(listOf("with-image", "undated"), merged.map { it.externalId })
        assertEquals("https://img.test/story.jpg", merged.first().imageUrl)
    }

    @Test
    fun `limit zero returns no headlines`() {
        val headline = headline("one", "https://example.com/one", publishedAtMs = 1L)

        assertEquals(emptyList<HeadlineEntity>(), mergeHeadlines(listOf(headline), limit = 0))
    }

    private fun headline(
        externalId: String,
        articleUrl: String,
        imageUrl: String? = null,
        publishedAtMs: Long?
    ) = HeadlineEntity(
        sourceId = "source",
        sourceLabel = "Source",
        externalId = externalId,
        title = externalId,
        snippet = "Snippet",
        articleUrl = articleUrl,
        imageUrl = imageUrl,
        publishedAtMs = publishedAtMs,
        cachedAtMs = 0L
    )
}
