package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.news.FRANCE_INFO_FEED_URL
import com.ziaee.frenchreader.news.FRANCE_INFO_SOURCE_ID
import com.ziaee.frenchreader.news.FRANCE_INFO_SOURCE_LABEL
import com.ziaee.frenchreader.news.NewsFetcher
import com.ziaee.frenchreader.news.NewsItem
import com.ziaee.frenchreader.news.RFI_FACILE_FEED_URL
import com.ziaee.frenchreader.news.RFI_FACILE_SOURCE_ID
import com.ziaee.frenchreader.news.RFI_FACILE_SOURCE_LABEL
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** Some RSS feeds (e.g. RFI Facile's per-item links) still advertise plain
 * http:// even though the site itself serves https -- upgrade the scheme
 * before fetching so remote article traffic remains encrypted. */
internal fun httpsUrl(urlString: String): String =
    if (urlString.startsWith("http://")) "https://" + urlString.removePrefix("http://") else urlString

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
                ref = item.link,
                publishedAtMs = item.publishedAtMs
            )
        }
    }

    override suspend fun fetchArticle(result: ContentResult): ContentArticle? = withContext(Dispatchers.IO) {
        val url = httpsUrl(result.ref)
        val html = httpGetHtml(url)
        val text = extractArticleText(html) ?: return@withContext null
        ContentArticle(
            title = result.title,
            text = text,
            sourceUrl = url,
            sourceName = label,
            author = null,
            license = null,
            publishedAtMs = result.publishedAtMs
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
    id = RFI_FACILE_SOURCE_ID,
    label = RFI_FACILE_SOURCE_LABEL,
    feedUrl = RFI_FACILE_FEED_URL
) {
    override fun extractArticleText(html: String): String? = ArticleExtractor.extractRfiFacileTranscript(html)
}

/** France Info's general headlines -- see ROADMAP.md section 6.
 * Extracts full article text from `div.c-body`, excluding related-article embeds. */
object FranceInfoContentSource : NewsContentSource(
    id = FRANCE_INFO_SOURCE_ID,
    label = FRANCE_INFO_SOURCE_LABEL,
    feedUrl = FRANCE_INFO_FEED_URL
) {
    override fun extractArticleText(html: String): String? = ArticleExtractor.extractFranceInfoArticle(html)
}
