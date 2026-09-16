package com.ziaee.frenchreader.news

import android.util.Xml
import com.ziaee.frenchreader.util.HtmlUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

/** One RSS `<item>`, reduced to what the app needs: a title, a plain-text
 * body (HTML stripped), a guid/link used for de-duplication, and an
 * optional image URL for a future "image with text" pass (ROADMAP.md
 * section 4) -- not downloaded yet, just carried through in case. */
data class NewsItem(
    val title: String,
    val body: String,
    val guid: String,
    val imageUrl: String? = null
)

sealed class NewsFetchResult {
    data class NewItem(val item: NewsItem) : NewsFetchResult()
    data object NoNewItem : NewsFetchResult()
    data class Error(val message: String) : NewsFetchResult()
}

/**
 * Fetches [source]'s RSS feed and returns its first (most recent) item,
 * unless that item's guid matches [lastGuid] -- meaning nothing new has
 * been published since the last fetch of this source.
 *
 * Deliberately dependency-free: a single GET via [HttpURLConnection] and
 * Android's built-in [Xml] pull parser, rather than pulling in OkHttp or a
 * dedicated RSS library for one request.
 */
object NewsFetcher {
    suspend fun fetchLatest(source: NewsSource, lastGuid: String?): NewsFetchResult = withContext(Dispatchers.IO) {
        try {
            val xml = httpGet(source.feedUrl)
            val item = parseFirstItem(xml)
                ?: return@withContext NewsFetchResult.Error("فید خالی یا در قالب نامعتبر بود")
            if (lastGuid != null && item.guid == lastGuid) NewsFetchResult.NoNewItem
            else NewsFetchResult.NewItem(item)
        } catch (e: Exception) {
            NewsFetchResult.Error(e.message ?: e.toString())
        }
    }

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; FrenchReaderApp)")
        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Parses just the first `<item>` -- this app only ever wants "today's
     * latest", so there's no reason to walk (or hold in memory) the rest of
     * the feed. Namespace processing is left off so tags like
     * `content:encoded` and `media:content` show up under their literal
     * (prefixed) names, which is all this needs. */
    private fun parseFirstItem(xml: String): NewsItem? {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var inItem = false
        var title: String? = null
        var description: String? = null
        var contentEncoded: String? = null
        var link: String? = null
        var guid: String? = null
        var imageUrl: String? = null
        var currentTag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    currentTag = name
                    when {
                        name == "item" -> inItem = true
                        inItem && (name == "enclosure" || name == "media:content") -> {
                            val type = parser.getAttributeValue(null, "type")
                            val url = parser.getAttributeValue(null, "url")
                            if (imageUrl == null && url != null && (type == null || type.startsWith("image"))) {
                                imageUrl = url
                            }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (inItem) {
                        val text = parser.text
                        when (currentTag) {
                            "title" -> title = (title ?: "") + text
                            "description" -> description = (description ?: "") + text
                            "content:encoded" -> contentEncoded = (contentEncoded ?: "") + text
                            "link" -> link = (link ?: "") + text
                            "guid" -> guid = (guid ?: "") + text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        val rawBody = contentEncoded?.takeIf { it.isNotBlank() } ?: description.orEmpty()
                        val cleanTitle = HtmlUtil.stripHtml(title.orEmpty())
                        val cleanBody = HtmlUtil.stripHtml(rawBody)
                        return if (cleanTitle.isNotBlank() && cleanBody.isNotBlank()) {
                            NewsItem(
                                title = cleanTitle,
                                body = cleanBody,
                                guid = (guid?.takeIf { it.isNotBlank() } ?: link ?: cleanTitle).trim(),
                                imageUrl = imageUrl
                            )
                        } else null
                    }
                    currentTag = null
                }
            }
            event = parser.next()
        }
        return null
    }

}
