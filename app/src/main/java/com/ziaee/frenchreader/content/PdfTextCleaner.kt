package com.ziaee.frenchreader.content

import kotlin.math.ceil

object PdfTextCleaner {
    private val pageNumber = Regex("""^\s*(?:page\s+)?\d+(?:\s*(?:/|of|sur)\s*\d+)?\s*$""", RegexOption.IGNORE_CASE)
    private val hyphenatedBreak = Regex("""(?<=\p{L})-\n(?=\p{Ll})""")
    private val retainedHyphenBreak = Regex("""(?<=\p{L})-\n(?=\p{Lu})""")

    fun clean(text: String): String = cleanLines(normalize(text).lines())

    fun cleanPages(pages: List<String>): String {
        if (pages.isEmpty()) return ""
        val normalizedPages = pages.map { normalize(it).lines() }
        val frequency = normalizedPages
            .flatMap { lines -> lines.map(String::trim).filter { it.isNotEmpty() }.distinct() }
            .groupingBy { it }
            .eachCount()
        val repeatThreshold = maxOf(2, ceil(pages.size * 0.6).toInt())
        val repeated = frequency.filterValues { it >= repeatThreshold }.keys

        val keptPages = normalizedPages.map { lines ->
            lines.filterNot { line ->
                val trimmed = line.trim()
                pageNumber.matches(trimmed) || trimmed in repeated
            }.joinToString("\n").trim()
        }.filter(String::isNotEmpty)
        // A page that doesn't end a sentence continues its paragraph (and any
        // hyphenated word) on the next page instead of being split in two.
        val joined = StringBuilder()
        keptPages.forEachIndexed { index, page ->
            if (index > 0) joined.append(if (endsSentence(keptPages[index - 1])) "\n\n" else "\n")
            joined.append(page)
        }
        return cleanLines(joined.toString().lines())
    }

    private fun endsSentence(page: String): Boolean =
        page.trimEnd().lastOrNull()?.let { it in ".!?…:;»\"”)" } ?: true

    private fun normalize(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\u000c", "")

    private fun cleanLines(lines: List<String>): String {
        val joinedHyphens = lines.joinToString("\n")
            .replace(hyphenatedBreak, "")
            .replace(retainedHyphenBreak, "-")
        val paragraphs = joinedHyphens.split(Regex("\n\\s*\n+"))
            .map { paragraph ->
                paragraph.lines()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .filterNot(pageNumber::matches)
                    .joinToString(" ")
            }
            .filter(String::isNotBlank)
        return paragraphs.joinToString("\n\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
