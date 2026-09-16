package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.news.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsContentSourceTest {
    @Test
    fun `upgrades cleartext http links to https before fetching`() {
        assertEquals("https://francaisfacile.rfi.fr/x", httpsUrl("http://francaisfacile.rfi.fr/x"))
    }

    @Test
    fun `leaves https links unchanged`() {
        assertEquals("https://example.com/x", httpsUrl("https://example.com/x"))
    }

    private val items = listOf(
        NewsItem(
            title = "Discours sur l'état de l'Union",
            snippet = "Ursula von der Leyen s'exprime à Strasbourg.",
            link = "https://example.com/a",
            guid = "https://example.com/a",
            publishedAtMs = 1L
        ),
        NewsItem(
            title = "Six questions sur le procès de Rachida Dati",
            snippet = "L'ancienne ministre comparaît mercredi.",
            link = "https://example.com/b",
            guid = "https://example.com/b",
            publishedAtMs = 2L
        )
    )

    @Test
    fun `matches query against title case-insensitively`() {
        val result = filterNewsItems(items, "union")
        assertEquals(listOf("Discours sur l'état de l'Union"), result.map { it.title })
    }

    @Test
    fun `matches query against snippet too`() {
        val result = filterNewsItems(items, "Strasbourg")
        assertEquals(listOf("Discours sur l'état de l'Union"), result.map { it.title })
    }

    @Test
    fun `blank query returns everything unfiltered, in order`() {
        val result = filterNewsItems(items, "")
        assertEquals(2, result.size)
        assertEquals(items[0].title, result[0].title)
    }

    @Test
    fun `no match returns empty list`() {
        val result = filterNewsItems(items, "cricket")
        assertEquals(emptyList<NewsItem>(), result)
    }
}
