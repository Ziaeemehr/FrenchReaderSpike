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
}
