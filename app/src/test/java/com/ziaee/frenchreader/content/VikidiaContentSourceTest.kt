package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.vikidia.VikidiaArticle
import com.ziaee.frenchreader.vikidia.VikidiaSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class VikidiaContentSourceTest {
    @Test
    fun `maps a Vikidia search result to a ContentResult`() {
        val vikidiaResult = VikidiaSearchResult(pageId = 8826, title = "Espace", snippet = "dans la course à l'espace.", wordCount = 457)

        val result = VikidiaContentSource.toContentResult(vikidiaResult)

        assertEquals(ContentResult(
            sourceId = "vikidia",
            sourceLabel = "Vikidia",
            title = "Espace",
            snippet = "dans la course à l'espace.",
            lengthHint = "~457 کلمه",
            ref = "8826"
        ), result)
    }

    @Test
    fun `maps a fetched Vikidia article to a ContentArticle`() {
        val article = VikidiaArticle(title = "Espace", text = "L'espace est l'étendue.", publishedAtMs = 1774901090000L)

        val contentArticle = VikidiaContentSource.toContentArticle(article)

        assertEquals(ContentArticle(
            title = "Espace",
            text = "L'espace est l'étendue.",
            sourceUrl = "https://fr.vikidia.org/wiki/Espace",
            sourceName = "Vikidia",
            author = null,
            license = "CC BY-SA 3.0",
            publishedAtMs = 1774901090000L
        ), contentArticle)
    }
}
