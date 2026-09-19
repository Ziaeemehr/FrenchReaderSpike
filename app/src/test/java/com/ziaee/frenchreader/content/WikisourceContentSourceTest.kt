package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.wikisource.WikisourceSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class WikisourceContentSourceTest {
    @Test
    fun `maps a Wikisource search result to a ContentResult`() {
        val wikisourceResult = WikisourceSearchResult(
            title = "Boule de suif (recueil)/Boule de Suif",
            snippet = "Pendant plusieurs jours de suite.",
            wordCount = 8200
        )

        val result = WikisourceContentSource.toContentResult(wikisourceResult)

        assertEquals(
            ContentResult(
                sourceId = "wikisource",
                sourceLabel = "Wikisource",
                title = "Boule de suif (recueil)/Boule de Suif",
                snippet = "Pendant plusieurs jours de suite.",
                lengthHint = "~8200 کلمه",
                ref = "Boule de suif (recueil)/Boule de Suif"
            ),
            result
        )
    }
}
