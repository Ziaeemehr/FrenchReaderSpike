package com.ziaee.frenchreader.news

import com.ziaee.frenchreader.content.httpsUrl
import com.ziaee.frenchreader.data.HeadlineEntity
import java.net.URI

/** Merges cached headlines from independent sources deterministically. */
fun mergeHeadlines(vararg sources: List<HeadlineEntity>, limit: Int): List<HeadlineEntity> {
    if (limit <= 0) return emptyList()

    val byArticleUrl = LinkedHashMap<String, HeadlineEntity>()
    sources.asSequence().flatten().forEach { headline ->
        val key = normalizeArticleUrl(headline.articleUrl)
        val existing = byArticleUrl[key]
        if (existing == null || (existing.imageUrl == null && headline.imageUrl != null)) {
            byArticleUrl[key] = headline
        }
    }

    return byArticleUrl.values
        .sortedWith(
            Comparator { left, right ->
                when {
                    left.publishedAtMs != null && right.publishedAtMs != null ->
                        right.publishedAtMs.compareTo(left.publishedAtMs)
                    left.publishedAtMs != null -> -1
                    right.publishedAtMs != null -> 1
                    else -> 0
                }
            }
        )
        .take(limit)
}

private fun normalizeArticleUrl(articleUrl: String): String {
    val upgraded = httpsUrl(articleUrl.trim())
    return try {
        val uri = URI(upgraded)
        val host = uri.host?.lowercase() ?: return upgraded.substringBefore('#').trimEnd('/')
        val path = uri.path?.trimEnd('/')
        URI("https", uri.userInfo, host, uri.port, path, uri.query, null).toString()
    } catch (_: Exception) {
        upgraded.substringBefore('#').trimEnd('/')
    }
}
