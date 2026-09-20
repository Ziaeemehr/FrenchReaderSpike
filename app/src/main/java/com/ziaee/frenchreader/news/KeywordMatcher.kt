package com.ziaee.frenchreader.news

import com.ziaee.frenchreader.data.HeadlineEntity
import java.text.Normalizer
import java.util.Locale

/** Personalizes an already date-sorted list while retaining its order inside each group. */
fun personalizeHeadlines(
    headlines: List<HeadlineEntity>,
    rawKeywords: String,
    matchingFirst: Boolean
): List<HeadlineEntity> {
    val keywords = rawKeywords.split(',', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { it.normalizedForMatching() }
    if (keywords.isEmpty()) return headlines

    val (matching, other) = headlines.partition { headline ->
        val searchable = "${headline.title} ${headline.snippet}".normalizedForMatching()
        keywords.any(searchable::contains)
    }
    return if (matchingFirst) matching + other else matching
}

private fun String.normalizedForMatching(): String = Normalizer
    .normalize(this, Normalizer.Form.NFD)
    .replace(COMBINING_MARKS, "")
    .lowercase(Locale.ROOT)

private val COMBINING_MARKS = Regex("\\p{M}+")
