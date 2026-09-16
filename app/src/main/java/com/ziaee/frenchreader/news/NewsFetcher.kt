package com.ziaee.frenchreader.news

import com.ziaee.frenchreader.util.HtmlUtil
import com.ziaee.frenchreader.content.httpsUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/** One RSS `<item>`: a title, a short plain-text snippet (HTML stripped --
 * this is a teaser, NOT the full article; see ROADMAP.md section 6 on why
 * RSS descriptions can't be treated as full text), the item's real webpage
 * link (used to fetch the full article), a guid, and the parsed publish
 * date if the feed provides one. */
data class NewsItem(
    val title: String,
    val snippet: String,
    val link: String,
    val guid: String,
    val publishedAtMs: Long?,
    val imageUrl: String? = null
)

/**
 * Fetches up to [limit] items from an RSS feed, in feed order (newest
 * first, by RSS convention). Dependency-free: HttpURLConnection + Android's
 * built-in XmlPullParser, no OkHttp or RSS library.
 */
object NewsFetcher {
    suspend fun fetchItems(feedUrl: String, limit: Int = 25): List<NewsItem> = withContext(Dispatchers.IO) {
        parseItems(httpGet(feedUrl), limit)
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

    /** Namespace processing is left off so tags like `content:encoded` show
     * up under their literal (prefixed) name, which is all this needs. */
    internal fun parseItems(xml: String, limit: Int): List<NewsItem> {
        val parser: XmlPullParser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        val items = mutableListOf<NewsItem>()
        var inItem = false
        var title: String? = null
        var description: String? = null
        var contentEncoded: String? = null
        var link: String? = null
        var guid: String? = null
        var pubDate: String? = null
        var mediaContentUrl: String? = null
        var mediaThumbnailUrl: String? = null
        var enclosureImageUrl: String? = null
        var descriptionImageUrl: String? = null
        var inDescription = false
        var descriptionDepth = 0
        var currentTag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && items.size < limit) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    currentTag = name
                    if (name == "item") {
                        inItem = true
                        title = null; description = null; contentEncoded = null
                        link = null; guid = null; pubDate = null
                        mediaContentUrl = null; mediaThumbnailUrl = null
                        enclosureImageUrl = null; descriptionImageUrl = null
                        inDescription = false; descriptionDepth = 0
                    } else if (inItem && name == "description") {
                        inDescription = true
                        descriptionDepth = 1
                    } else if (inDescription) {
                        descriptionDepth++
                    }
                    if (inItem && name == "media:content" && mediaContentUrl == null) {
                        mediaContentUrl = parser.mediaContentImageUrl()
                    } else if (inItem && name == "media:thumbnail" && mediaThumbnailUrl == null) {
                        mediaThumbnailUrl = parser.getAttributeValue(null, "url").asImageUrl()
                    } else if (inItem && name == "enclosure" && enclosureImageUrl == null) {
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        val url = parser.getAttributeValue(null, "url")
                        if (type.startsWith("image/", ignoreCase = true) || url.isImageExtension()) {
                            enclosureImageUrl = url.asImageUrl()
                        }
                    } else if (inDescription && name.equals("img", ignoreCase = true) && descriptionImageUrl == null) {
                        descriptionImageUrl = parser.getAttributeValue(null, "src").asImageUrl()
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
                            "pubDate" -> pubDate = (pubDate ?: "") + text
                        }
                        if (inDescription && currentTag != "description") {
                            description = (description ?: "") + text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        inItem = false
                        val rawSnippet = contentEncoded?.takeIf { it.isNotBlank() } ?: description.orEmpty()
                        val cleanTitle = HtmlUtil.stripHtml(title.orEmpty())
                        val cleanSnippet = HtmlUtil.stripHtml(rawSnippet)
                        val escapedDescriptionImage = descriptionImageUrl ?: description.orEmpty().firstImageUrl()
                        val itemLink = link?.trim().orEmpty()
                        if (cleanTitle.isNotBlank() && cleanSnippet.isNotBlank() && itemLink.isNotBlank()) {
                            items.add(
                                NewsItem(
                                    title = cleanTitle,
                                    snippet = cleanSnippet,
                                    link = itemLink,
                                    guid = (guid?.takeIf { it.isNotBlank() } ?: itemLink).trim(),
                                    publishedAtMs = pubDate?.let(::parseRfc822),
                                    imageUrl = mediaContentUrl ?: mediaThumbnailUrl ?: enclosureImageUrl ?: escapedDescriptionImage
                                )
                            )
                        }
                    } else if (inDescription) {
                        descriptionDepth--
                        if (descriptionDepth <= 0 || parser.name == "description") {
                            inDescription = false
                            descriptionDepth = 0
                        }
                    }
                    currentTag = null
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun String?.asImageUrl(): String? {
        val raw = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalizedScheme = when {
            raw.startsWith("http://", ignoreCase = true) -> "http://" + raw.substring(7)
            raw.startsWith("https://", ignoreCase = true) -> "https://" + raw.substring(8)
            else -> raw
        }
        val upgraded = httpsUrl(normalizedScheme)
        return try {
            val uri = URI(upgraded)
            upgraded.takeIf { uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun String?.isImageExtension(): Boolean =
        this?.substringBefore('?')?.substringBefore('#')?.lowercase(Locale.US)?.matches(
            Regex(".*\\.(?:jpg|jpeg|png|gif|webp|avif|bmp|svg)$")
        ) == true

    private fun XmlPullParser.mediaContentImageUrl(): String? {
        val type = getAttributeValue(null, "type")?.trim()?.lowercase(Locale.US).orEmpty()
        val medium = getAttributeValue(null, "medium")?.trim()?.lowercase(Locale.US).orEmpty()
        val url = getAttributeValue(null, "url")
        if (type.isNotBlank() && !type.startsWith("image/")) return null
        if (medium.isNotBlank() && medium != "image") return null
        if (!type.startsWith("image/") && medium != "image" && !url.isImageExtension()) return null
        return url.asImageUrl()
    }

    private fun String.firstImageUrl(): String? {
        val match = Regex("<img\\b[^>]*\\bsrc\\s*=\\s*(['\\\"])(.*?)\\1", setOf(RegexOption.IGNORE_CASE)).find(this)
            ?: return null
        return match.groupValues[2].asImageUrl()
    }

    private fun parseRfc822(date: String): Long? = try {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(date.trim())?.time
    } catch (e: Exception) {
        null
    }
}
