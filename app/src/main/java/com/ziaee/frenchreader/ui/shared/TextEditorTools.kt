package com.ziaee.frenchreader.ui.shared

fun findMatches(
    text: String,
    query: String,
    matchCase: Boolean,
    wholeWord: Boolean
): List<IntRange> {
    if (query.isEmpty() || query.length > text.length) return emptyList()

    val matches = mutableListOf<IntRange>()
    var searchFrom = 0
    while (searchFrom <= text.length - query.length) {
        val start = text.indexOf(query, startIndex = searchFrom, ignoreCase = !matchCase)
        if (start < 0) break
        val endExclusive = start + query.length
        val startsAtBoundary = start == 0 ||
            !Character.isLetterOrDigit(Character.codePointBefore(text, start))
        val endsAtBoundary = endExclusive == text.length ||
            !Character.isLetterOrDigit(Character.codePointAt(text, endExclusive))
        if (!wholeWord || startsAtBoundary && endsAtBoundary) {
            matches += start until endExclusive
            searchFrom = endExclusive
        } else {
            searchFrom = start + 1
        }
    }
    return matches
}

fun replaceAll(
    text: String,
    query: String,
    replacement: String,
    matchCase: Boolean,
    wholeWord: Boolean
): Pair<String, Int> {
    val matches = findMatches(text, query, matchCase, wholeWord)
    if (matches.isEmpty()) return text to 0

    val result = StringBuilder(text.length + matches.size * (replacement.length - query.length))
    var copiedThrough = 0
    matches.forEach { match ->
        result.append(text, copiedThrough, match.first)
        result.append(replacement)
        copiedThrough = match.last + 1
    }
    result.append(text, copiedThrough, text.length)
    return result.toString() to matches.size
}

fun joinWrappedLines(text: String): String {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    return normalized
        .split(Regex("\n[ \\t]*(?:\n[ \\t]*)+"))
        .joinToString("\n\n") { paragraph ->
            paragraph.replace(Regex("[ \\t]*\n[ \\t]*"), "\n")
                .replace(Regex("-\n(?=\\p{Ll})"), "")
                .replace('\n', ' ')
        }
}

fun tidyWhitespace(text: String): String = text
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .lineSequence()
    .joinToString("\n") { line ->
        line.replace(Regex("[ \\t]+"), " ").trim()
    }
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
