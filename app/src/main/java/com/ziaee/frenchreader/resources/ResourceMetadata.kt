package com.ziaee.frenchreader.resources

import com.ziaee.frenchreader.data.ResourceLink
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.jsoup.Jsoup

data class ResourceMetadata(val title: String?, val imageUrl: String?)

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
    return ResourceMetadata(title = title, imageUrl = image)
}

fun filterResources(
    items: List<ResourceLink>,
    query: String,
    category: ResourceCategory? = null
): List<ResourceLink> {
    val needle = query.trim()
    return items.filter {
        (category == null || ResourceCategory.fromKey(it.category) == category) &&
            (needle.isBlank() ||
                it.title.contains(needle, ignoreCase = true) ||
                it.url.contains(needle, ignoreCase = true))
    }
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
