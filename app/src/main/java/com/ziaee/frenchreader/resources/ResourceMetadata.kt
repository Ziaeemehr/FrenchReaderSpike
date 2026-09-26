package com.ziaee.frenchreader.resources

import com.ziaee.frenchreader.data.ResourceLink
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.jsoup.Jsoup

data class ResourceMetadata(val title: String?, val imageUrl: String?, val description: String? = null)

fun normalizeResourceUrl(raw: String): String? {
    val value = raw.trim()
    if (value.isBlank() || value.any(Char::isWhitespace)) return null
    val candidate = if (value.contains("://")) value else "https://$value"
    return try {
        val uri = URI(candidate)
        candidate.takeIf {
            uri.scheme.equals("https", ignoreCase = true) ||
                uri.scheme.equals("http", ignoreCase = true)
        }?.takeIf { !uri.host.isNullOrBlank() }
    } catch (_: Exception) {
        null
    }
}

fun parseResourceMetadata(html: String, pageUrl: String): ResourceMetadata {
    val document = Jsoup.parse(html, pageUrl)
    val ogTitle = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
    val title = ogTitle?.takeIf(String::isNotBlank)
        ?: document.title().trim().takeIf(String::isNotBlank)
    val image = document.selectFirst("meta[property=og:image]")
        ?.absUrl("content")
        ?.trim()
        ?.takeIf(String::isNotBlank)
    val description = (document.selectFirst("meta[property=og:description]")?.attr("content")
        ?: document.selectFirst("meta[name=description]")?.attr("content"))
        ?.trim()?.takeIf(String::isNotBlank)?.take(300)
    return ResourceMetadata(title = title, imageUrl = image, description = description)
}

/** Description to show: the user's own, else the built-in one in [language] (English fallback). */
fun resourceDescription(resource: ResourceLink, language: String): String? =
    resource.description?.takeIf(String::isNotBlank)
        ?: defaultResourceFor(resource.url)?.description?.let { it[language] ?: it["en"] }

fun resourceLevel(resource: ResourceLink): String? =
    resource.level?.takeIf(String::isNotBlank) ?: defaultResourceFor(resource.url)?.level

/** Built-ins have createdAtMs = their catalog position; anything real is a timestamp. */
private const val BUILT_IN_MAX_CREATED_AT = 10_000L

/**
 * Filters by category and by [query] (title, URL, level, or any description), then orders the
 * user's own links newest first, followed by built-ins in catalog order.
 */
fun filterResources(
    items: List<ResourceLink>,
    query: String,
    category: ResourceCategory? = null
): List<ResourceLink> {
    val needle = query.trim()
    fun matches(it: ResourceLink): Boolean {
        if (needle.isBlank()) return true
        val builtIn = defaultResourceFor(it.url)
        return sequenceOf(it.title, it.url, it.description, resourceLevel(it))
            .plus(builtIn?.description?.values.orEmpty())
            .any { text -> text?.contains(needle, ignoreCase = true) == true }
    }
    return items
        .filter { (category == null || ResourceCategory.fromKey(it.category) == category) && matches(it) }
        .sortedWith(
            compareBy<ResourceLink> { it.createdAtMs < BUILT_IN_MAX_CREATED_AT }
                .thenBy { if (it.createdAtMs < BUILT_IN_MAX_CREATED_AT) it.createdAtMs else -it.createdAtMs }
                .thenBy { it.id }
        )
}

fun fetchResourceMetadata(pageUrl: String): ResourceMetadata {
    val connection = URL(pageUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; FrenchReaderApp)")
    connection.setRequestProperty("Accept", "text/html")
    return try {
        if (connection.responseCode !in 200..299) ResourceMetadata(null, null)
        else connection.inputStream.bufferedReader(Charsets.UTF_8).use {
            parseResourceMetadata(it.readText(), connection.url.toString())
        }
    } finally {
        connection.disconnect()
    }
}

/** Built-ins dropped from the catalog; removed once from installs that seeded them. */
val RETIRED_RESOURCE_URLS = setOf(
    "https://www.youtube.com/results?search_query=Mahya+polyglot"
)

/** Built-in resources not added to this install yet. */
fun catalogEntriesToSeed(seededUrls: Set<String>): List<DefaultResource> =
    DEFAULT_RESOURCES.filter { it.url !in seededUrls }

/** Search-result links (web, YouTube) have no page image worth fetching. */
fun wantsImageLookup(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.removePrefix("www.") ?: return false
    return !(host.startsWith("google.") && uri.path == "/search") && !(host == "youtube.com" && uri.path == "/results")
}
