package com.ziaee.frenchreader.watch

import com.ziaee.frenchreader.comprehension.FrenchLemmaLexicon
import com.ziaee.frenchreader.comprehension.normalizeFrench

data class IdiomEntry(val expression: String, val pos: String, val gloss: String) {
    val words: List<String> = tokenizeSubtitle(expression).filter { it.isWord }.map { normalizeFrench(it.text) }
}

class InMemoryIdiomLexicon(entries: List<IdiomEntry>) {
    val byFirstWord: Map<String, List<IdiomEntry>> = entries.filter { it.words.size >= 2 }.groupBy { it.words.first() }
}

/** Character offsets are UTF-16, with an exclusive end, like Compose annotations. */
data class IdiomMatch(val startChar: Int, val endChar: Int, val expression: String, val gloss: String)

class IdiomMatcher(private val idioms: InMemoryIdiomLexicon, private val lemmas: FrenchLemmaLexicon) {
    private data class Token(val surface: String, val lemmas: Set<String>, val start: Int, val end: Int, val segment: Int)

    fun match(text: String): List<IdiomMatch> {
        var offset = 0
        // An expression never spans a sentence end, a line break (speaker change) or a number.
        var segment = 0
        val tokens = tokenizeSubtitle(text).mapNotNull { token ->
            val start = offset
            offset += token.text.length
            if (!token.isWord) {
                if (token.text.any { it in ".!?…;:\n" || it.isDigit() }) segment++
                null
            } else {
                val surface = normalizeFrench(token.text)
                Token(surface, lemmas.lemmas(surface), start, offset, segment)
            }
        }
        return buildList {
            var start = 0
            while (start < tokens.size) {
                val first = tokens[start]
                val keys = (first.lemmas + first.surface).toMutableSet()
                if (first.surface in reflexives) keys.addAll(listOf("se", "s'"))
                val candidates = keys.flatMap { idioms.byFirstWord[it].orEmpty() }.distinct()
                var best: IdiomEntry? = null
                var bestEnd = -1
                for (entry in candidates) {
                    // Common nominal pairs are noisy; no exceptions are currently needed by the data.
                    if (entry.words.size == 2 && entry.pos != "verb" && entry.words.all { it in lemmas.commonLemmas }) continue
                    val gaps = entry.words.size != 2 || entry.pos == "verb"
                    fun walk(word: Int, previous: Int): Int {
                        if (word == entry.words.size) return previous
                        var end = -1
                        for (next in previous + 1..minOf(previous + if (gaps) 3 else 1, tokens.lastIndex)) {
                            if (tokens[next].segment != first.segment) break
                            if (next > previous + 1 && !isGap(tokens[next - 1])) break
                            if (matches(entry.words[word], tokens[next])) end = maxOf(end, walk(word + 1, next))
                        }
                        return end
                    }
                    if (!matches(entry.words.first(), first)) continue
                    val end = walk(1, start)
                    if (end >= 0 && (best == null || entry.words.size > best.words.size ||
                                (entry.words.size == best.words.size && end > bestEnd))) {
                        best = entry
                        bestEnd = end
                    }
                }
                val chosen = best
                if (chosen == null) start++ else {
                    add(IdiomMatch(first.start, tokens[bestEnd].end, chosen.expression, chosen.gloss))
                    start = bestEnd + 1
                }
            }
        }
    }

    private fun matches(word: String, token: Token): Boolean = when (word) {
        "se", "s'" -> token.surface in reflexives
        "en", "y" -> token.surface == word
        else -> word == token.surface || word in token.lemmas
    }

    private fun isGap(token: Token): Boolean = token.surface in gapWords || token.lemmas.any { it == "avoir" || it == "être" }

    private companion object {
        val reflexives = setOf("me", "m'", "te", "t'", "se", "s'", "nous", "vous")
        val gapWords = reflexives + setOf("le", "la", "les", "l'", "lui", "leur", "en", "y", "ne", "n'", "pas", "plus", "jamais", "rien",
            "bien", "déjà", "vraiment", "toujours", "encore", "trop", "très")
    }
}
