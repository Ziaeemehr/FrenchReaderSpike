package com.ziaee.frenchreader.watch

private val frenchAbbreviations = setOf(
    "m.", "mm.", "mme.", "mmes.", "mlle.", "mlles.", "dr.", "drs.", "pr.", "prs.",
    "st.", "ste.", "sts.", "stes.", "cf.", "p.", "pp.", "n°", "n°.", "av.", "apr.",
    "j.-c.", "ex.", "env.", "fig.", "vol.", "éd.", "chap.", "art."
)
private val capitalInitial = Regex("""\p{Lu}\.""")
private const val boundaryQuotes = "»«\"“”‘’')]}"

/** Shared by timed-text splitting and sentence joins; [nextText] supplies token lookahead. */
internal fun isSentenceEnd(text: String, nextText: String): Boolean {
    val end = text.trimEnd { it.isWhitespace() || it in boundaryQuotes }
    val next = nextText.firstOrNull { !it.isWhitespace() && it !in boundaryQuotes && it !in "([{" }
    if (end.endsWith("…") || end.endsWith("...")) return next?.isUpperCase() == true
    if (end.endsWith("!") || end.endsWith("?")) return true
    if (!end.endsWith(".")) return false
    val token = end.takeLastWhile { !it.isWhitespace() }.trimStart { it in boundaryQuotes || it in "([{" }
    if (token.equals("etc.", ignoreCase = true)) {
        return next == null || (!next.isLowerCase() && !next.isDigit())
    }
    if (token.lowercase() in frenchAbbreviations || capitalInitial.matches(token)) return false
    // A timestamped segment can end between the decimal/grouping dot and its digits.
    if (end.getOrNull(end.lastIndex - 1)?.isDigit() == true && next?.isDigit() == true) return false
    return true
}
