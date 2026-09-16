package com.ziaee.frenchreader.vikidia

import org.junit.Assert.assertEquals
import org.junit.Test

class VikidiaClientTest {
    // Trimmed real response shape from
    // https://fr.vikidia.org/w/api.php?action=query&list=search&srsearch=espace&srlimit=2&srprop=snippet|wordcount&format=json
    private val sampleSearchJson = """
        {
          "batchcomplete": "",
          "query": {
            "searchinfo": { "totalhits": 1857 },
            "search": [
              {
                "ns": 0,
                "title": "Espace",
                "pageid": 8826,
                "wordcount": 457,
                "snippet": "dans la course à <span class=\"searchmatch\">l'espace</span>. À ne pas confondre."
              },
              {
                "ns": 0,
                "title": "Superficie",
                "pageid": 8218,
                "wordcount": 227,
                "snippet": "comme un terrain (un jardin, un champ...)"
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `parses titles, pageids, wordcounts and strips snippet markup`() {
        val results = VikidiaClient.parseSearchResults(sampleSearchJson)

        assertEquals(2, results.size)
        assertEquals(VikidiaSearchResult(
            pageId = 8826,
            title = "Espace",
            snippet = "dans la course à l'espace. À ne pas confondre.",
            wordCount = 457
        ), results[0])
        assertEquals("Superficie", results[1].title)
        assertEquals(227, results[1].wordCount)
    }

    @Test
    fun `returns empty list when search array is missing`() {
        val results = VikidiaClient.parseSearchResults("""{"query": {}}""")
        assertEquals(emptyList<VikidiaSearchResult>(), results)
    }

    // Trimmed real response shape from
    // https://fr.vikidia.org/w/api.php?action=query&prop=extracts|revisions&explaintext=1&rvprop=timestamp&pageids=8826&format=json
    private val sampleArticleJson = """
        {
          "query": {
            "pages": {
              "8826": {
                "pageid": 8826,
                "ns": 0,
                "title": "Espace",
                "extract": "L'espace est l'étendue qui sépare les planètes.",
                "revisions": [ { "timestamp": "2026-03-30T20:04:50Z" } ]
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parses article extract and revision timestamp`() {
        val article = VikidiaClient.parseArticle(sampleArticleJson, pageId = 8826)

        requireNotNull(article)
        assertEquals("Espace", article.title)
        assertEquals("L'espace est l'étendue qui sépare les planètes.", article.text)
        assertEquals(1774901090000L, article.publishedAtMs) // 2026-03-30T20:04:50Z
    }

    @Test
    fun `returns null when the requested page id is missing`() {
        val article = VikidiaClient.parseArticle("""{"query": {"pages": {}}}""", pageId = 8826)
        assertEquals(null, article)
    }

    @Test
    fun `articleUrl builds a wiki link with underscores and encoding`() {
        assertEquals(
            "https://fr.vikidia.org/wiki/Conqu%C3%AAte_de_l%27espace",
            VikidiaClient.articleUrl("Conquête de l'espace")
        )
    }
}
