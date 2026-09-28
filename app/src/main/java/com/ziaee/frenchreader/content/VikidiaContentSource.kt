package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.vikidia.VikidiaArticle
import com.ziaee.frenchreader.vikidia.VikidiaClient
import com.ziaee.frenchreader.vikidia.VikidiaSearchResult

/** Adapts [VikidiaClient] to [ContentSource] for one language edition of Vikidia. */
class VikidiaContentSource(private val language: String = "fr") : ContentSource {
    override val id = "vikidia"
    override val label = "Vikidia"

    override suspend fun search(query: String, limit: Int): List<ContentResult> =
        VikidiaClient.search(query, limit, language).map(::toContentResult)

    override suspend fun fetchArticle(result: ContentResult): ContentArticle? {
        val pageId = result.ref.toIntOrNull() ?: return null
        return VikidiaClient.fetchArticle(pageId, language)?.let(::toContentArticle)
    }

    internal fun toContentResult(r: VikidiaSearchResult): ContentResult = ContentResult(
        sourceId = id,
        sourceLabel = label,
        title = r.title,
        snippet = r.snippet,
        lengthHint = "~${r.wordCount} کلمه",
        ref = r.pageId.toString()
    )

    internal fun toContentArticle(a: VikidiaArticle): ContentArticle = ContentArticle(
        title = a.title,
        text = a.text,
        sourceUrl = VikidiaClient.articleUrl(a.title, language),
        sourceName = label,
        author = null,
        license = "CC BY-SA 3.0",
        publishedAtMs = a.publishedAtMs
    )
}
