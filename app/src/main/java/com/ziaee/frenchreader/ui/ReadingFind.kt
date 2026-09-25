package com.ziaee.frenchreader.ui

import java.text.Normalizer

/** One occurrence of the find query: the paragraph it's in and its range in that paragraph's shown text. */
internal data class FindMatch(val chunkIndex: Int, val range: IntRange)

/** The text a paragraph displays (what [SentenceFlowText] renders), so match ranges line up with it. */
internal fun chunkDisplayText(chunk: ChunkState): String =
    if (chunk.sentences.isEmpty()) chunk.text else chunk.sentences.joinToString(" ") { it.text }

/** Lowercased, accent-stripped copy of [text] with the same length, so indices map 1:1. */
internal fun foldForFind(text: String): String = buildString(text.length) {
    for (c in text) {
        val base = if (c.code < 128) c else Normalizer.normalize(c.toString(), Normalizer.Form.NFD)[0]
        append(base.lowercaseChar())
    }
}

/** Every non-overlapping occurrence of [query] in [text], ignoring case and accents. */
internal fun findRanges(text: String, query: String): List<IntRange> {
    val needle = foldForFind(query.trim())
    if (needle.isEmpty()) return emptyList()
    val haystack = foldForFind(text)
    val ranges = mutableListOf<IntRange>()
    var index = haystack.indexOf(needle)
    while (index >= 0) {
        ranges += index until index + needle.length
        index = haystack.indexOf(needle, index + needle.length)
    }
    return ranges
}

internal fun findMatches(texts: List<String>, query: String): List<FindMatch> =
    texts.flatMapIndexed { index, text -> findRanges(text, query).map { FindMatch(index, it) } }

/**
 * What to search for when a text is opened from a Library search. Library search matches each
 * word anywhere in the text, so the whole phrase may not occur; then fall back to the longest
 * word that does.
 */
internal fun bestFindQuery(texts: List<String>, query: String): String {
    val phrase = query.trim()
    if (texts.any { findRanges(it, phrase).isNotEmpty() }) return phrase
    return Regex("[\\p{L}\\p{N}]+").findAll(phrase).map { it.value }
        .sortedByDescending { it.length }
        .firstOrNull { word -> texts.any { findRanges(it, word).isNotEmpty() } }
        ?: phrase
}
