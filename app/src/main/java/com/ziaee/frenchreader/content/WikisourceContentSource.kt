package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.wikisource.WikisourceClient
import com.ziaee.frenchreader.wikisource.WikisourceSearchResult

/** Adapts [WikisourceClient] to [ContentSource] -- see ROADMAP.md section 6's
 * Wikisource technical design. [author] and [ContentArticle.publishedAtMs]
 * are left null in this v1, same call as Vikidia's: extracting them
 * reliably (author from `.headertemplate-author`, a meaningful publish
 * date from a wiki edit timestamp) isn't needed for the core "search and
 * download a short story" flow. */
object WikisourceContentSource : ContentSource {
    override val id = "wikisource"
    override val label = "Wikisource"

    override suspend fun search(query: String, limit: Int): List<ContentResult> =
        WikisourceClient.search(query, limit).map(::toContentResult)

    override suspend fun fetchArticle(result: ContentResult): ContentArticle? {
        val page = WikisourceClient.fetchPage(result.ref) ?: return null
        val text = ArticleExtractor.extractWikisourceArticle(page.html) ?: return null
        return ContentArticle(
            title = page.title,
            text = text,
            sourceUrl = WikisourceClient.articleUrl(page.title),
            sourceName = label,
            author = null,
            license = "CC BY-SA 4.0",
            publishedAtMs = null
        )
    }

    internal fun toContentResult(r: WikisourceSearchResult): ContentResult = ContentResult(
        sourceId = id,
        sourceLabel = label,
        title = r.title,
        snippet = r.snippet,
        lengthHint = "~${r.wordCount} کلمه",
        ref = r.title
    )
}
