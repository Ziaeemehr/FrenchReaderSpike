package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.news.NewsFetcher
import com.ziaee.frenchreader.news.NewsItem
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** How many recent RSS items to pull before keyword-filtering -- RSS has no
 * server-side search, so this app fetches a window of recent items and
 * filters client-side. See ROADMAP.md section 6. */
private const val RECENT_ITEMS_WINDOW = 25

/** Matches [query] against each item's title or snippet, case-insensitively.
 * A blank query matches everything (used when a source should just show
 * its most recent items). Internal + top-level so it's testable without an
 * Android runtime, same pattern as VikidiaClient.parseSearchResults. */
internal fun filterNewsItems(items: List<NewsItem>, query: String): List<NewsItem> {
    val q = query.trim()
    if (q.isBlank()) return items
    return items.filter { it.title.contains(q, ignoreCase = true) || it.snippet.contains(q, ignoreCase = true) }
}

/** Shared implementation for an RSS-backed news [ContentSource]: search
 * pulls a window of recent items and keyword-filters them (real search,
 * unlike the old single-item "latest only" news feature); fetchArticle
 * downloads the item's real webpage and hands it to [extractArticleText]
 * for site-specific full-text extraction (see subclasses). */
abstract class NewsContentSource(
    override val id: String,
    override val label: String,
    private val feedUrl: String
) : ContentSource {

    override suspend fun search(query: String, limit: Int): List<ContentResult> {
        val items = NewsFetcher.fetchItems(feedUrl, limit = RECENT_ITEMS_WINDOW)
        return filterNewsItems(items, query).take(limit).map { item ->
            ContentResult(
                sourceId = id,
                sourceLabel = label,
                title = item.title,
                snippet = item.snippet,
                lengthHint = "خبر",
                ref = item.link
            )
        }
    }

    override suspend fun fetchArticle(result: ContentResult): ContentArticle? {
        val html = httpGetHtml(result.ref)
        val text = extractArticleText(html) ?: return null
        return ContentArticle(
            title = result.title,
            text = text,
            sourceUrl = result.ref,
            sourceName = label,
            author = null,
            license = null,
            publishedAtMs = null
        )
    }

    /** Site-specific full-text extraction from a fetched article page's raw
     * HTML, or null if the expected structure isn't found. */
    protected abstract fun extractArticleText(html: String): String?

    private fun httpGetHtml(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; FrenchReaderApp)")
        connection.setRequestProperty("Accept", "text/html")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** RFI's "Journal en français facile" -- see ROADMAP.md section 6.
 * Extracts the full transcript from `div.m-transcription__content` using jsoup. */
object RfiFacileContentSource : NewsContentSource(
    id = "rfi_facile",
    label = "RFI – فرانسهٔ ساده",
    feedUrl = "https://apis.fle.rfi.fr/products/get_product/fle_getpodcast_by_nid_author_rfi" +
        "?token_application=applepodcast_fle&program.entrepriseId=WBMZ39-FLE-FR-20220627"
) {
    override fun extractArticleText(html: String): String? = ArticleExtractor.extractRfiFacileTranscript(html)
}

/** France Info's general headlines -- see ROADMAP.md section 6.
 * Extracts full article text from `div.c-body`, excluding related-article embeds. */
object FranceInfoContentSource : NewsContentSource(
    id = "france_info",
    label = "France Info",
    feedUrl = "https://www.francetvinfo.fr/titres.rss"
) {
    override fun extractArticleText(html: String): String? = ArticleExtractor.extractFranceInfoArticle(html)
}
