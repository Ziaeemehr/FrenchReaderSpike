package com.ziaee.frenchreader.wikisource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikisourceClientTest {
    // Trimmed real response shape from
    // https://fr.wikisource.org/w/api.php?action=query&list=search&srsearch=Maupassant&srlimit=2&srprop=snippet|wordcount&format=json
    private val sampleSearchJson = """
        {
          "batchcomplete": "",
          "continue": { "sroffset": 2, "continue": "-||" },
          "query": {
            "searchinfo": { "totalhits": 2183 },
            "search": [
              {
                "ns": 0,
                "title": "La Vie et l’Œuvre de Maupassant",
                "pageid": 2425905,
                "wordcount": 612,
                "snippet": "l’Œuvre de <span class=\"searchmatch\">Maupassant</span> Édouard Maynial"
              },
              {
                "ns": 0,
                "title": "Contes et Nouvelles de Maupassant",
                "pageid": 186466,
                "wordcount": 12,
                "snippet": "de Guy de <span class=\"searchmatch\">Maupassant</span> Les Contes et Nouvelles"
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `parses titles, wordcounts and strips snippet markup`() {
        val results = WikisourceClient.parseSearchResults(sampleSearchJson)

        assertEquals(2, results.size)
        assertEquals(
            WikisourceSearchResult(
                title = "La Vie et l’Œuvre de Maupassant",
                snippet = "l’Œuvre de Maupassant Édouard Maynial",
                wordCount = 612
            ),
            results[0]
        )
        assertEquals("Contes et Nouvelles de Maupassant", results[1].title)
        assertEquals(12, results[1].wordCount)
    }

    @Test
    fun `returns empty list when search array is missing`() {
        val results = WikisourceClient.parseSearchResults("""{"query": {}}""")
        assertEquals(emptyList<WikisourceSearchResult>(), results)
    }

    // Trimmed real response shape from
    // https://fr.wikisource.org/w/api.php?action=parse&page=Boule%20de%20suif%20(recueil)/Boule%20de%20Suif&prop=text&format=json
    private val sampleParseJson = """
        {
          "parse": {
            "title": "Boule de suif (recueil)/Boule de Suif",
            "pageid": 5250,
            "text": { "*": "<div class=\"mw-parser-output\"><p>Pendant plusieurs jours de suite.</p></div>" }
          }
        }
    """.trimIndent()

    @Test
    fun `parses the canonical title and raw html body`() {
        val page = WikisourceClient.parsePage(sampleParseJson)

        requireNotNull(page)
        assertEquals("Boule de suif (recueil)/Boule de Suif", page.title)
        assertEquals("<div class=\"mw-parser-output\"><p>Pendant plusieurs jours de suite.</p></div>", page.html)
    }

    // Trimmed real response shape for a title that doesn't exist, e.g.
    // https://fr.wikisource.org/w/api.php?action=parse&page=ThisPageDoesNotExist12345&prop=text&format=json
    @Test
    fun `returns null for a missing-title error response`() {
        val json = """{"error": {"code": "missingtitle", "info": "The page you specified doesn't exist."}}"""
        assertNull(WikisourceClient.parsePage(json))
    }

    @Test
    fun `articleUrl builds a wiki link with underscores and encoding`() {
        assertEquals(
            "https://fr.wikisource.org/wiki/Boule_de_suif_%28recueil%29%2FBoule_de_Suif",
            WikisourceClient.articleUrl("Boule de suif (recueil)/Boule de Suif")
        )
    }
}
