package com.ziaee.frenchreader.grammar

import java.text.Normalizer
import java.util.Locale

/** Snippet ranges refer to its original display text, with markdown delimiters removed. */
data class GrammarSearchResult(val note: GrammarNote, val tier: Int, val snippet: GrammarCell?)

private data class FoldedText(val text: String, val starts: List<Int>, val ends: List<Int>)
private data class SearchToken(val text: String, val start: Int, val end: Int)

fun foldForGrammarSearch(text: String): String = foldGrammarText(text).text

private fun foldGrammarText(source: String, spacedZwnj: Boolean = false): FoldedText {
    val output = StringBuilder()
    val starts = mutableListOf<Int>()
    val ends = mutableListOf<Int>()
    source.forEachIndexed { index, character ->
        val replacement = when (character) {
            '\u200C' -> if (spacedZwnj) " " else ""
            in '\u200B'..'\u200F', in '\u2066'..'\u2069', '\u0640', in '\u064B'..'\u0652', '\u0670' -> ""
            'ي', 'ى' -> "ی"
            'ك' -> "ک"
            'ة' -> "ه"
            'œ', 'Œ' -> "oe"
            'æ', 'Æ' -> "ae"
            '’', '‘', 'ʼ' -> "'"
            '`' -> if (index > 0 && index < source.lastIndex && source[index - 1].isLetter() && source[index + 1].isLetter()) "'" else " "
            in '۰'..'۹' -> ('0' + (character - '۰')).toString()
            in '٠'..'٩' -> ('0' + (character - '٠')).toString()
            else -> Normalizer.normalize(character.toString().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        }
        replacement.forEach { c ->
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) {
                if (ends.isNotEmpty()) ends[ends.lastIndex] = index + 1
            } else {
                val normalized = if (c.isLetterOrDigit() || c == '\'') c else ' '
                if (normalized != ' ' || (output.isNotEmpty() && output.last() != ' ')) {
                    output.append(normalized)
                    starts.add(index)
                    ends.add(index + 1)
                }
            }
        }
    }
    if (output.lastOrNull() == ' ') {
        output.deleteCharAt(output.lastIndex)
        starts.removeAt(starts.lastIndex)
        ends.removeAt(ends.lastIndex)
    }
    return FoldedText(output.toString(), starts, ends)
}

private fun searchTokens(text: String): List<SearchToken> = Regex("\\S+").findAll(text).flatMap { match ->
    val whole = SearchToken(match.value, match.range.first, match.range.last + 1)
    val apostrophe = match.value.lastIndexOf('\'')
    if (apostrophe >= 0 && apostrophe < match.value.lastIndex) {
        sequenceOf(whole, SearchToken(match.value.substring(apostrophe + 1), whole.start + apostrophe + 1, whole.end))
    } else sequenceOf(whole)
}.toList()

private val grammarStopWords = setOf(
    "فرق", "تفاوت", "و", "یا", "با", "در", "از", "به", "که", "را", "چیست", "چیه", "یعنی", "چه", "کاربرد", "است",
    "et", "ou", "vs", "la", "le", "les", "de", "des", "du", "un", "une"
)

private fun tokenMatches(token: String, query: String): Boolean =
    token == query || (query.length > 2 && token.startsWith(query))

private class SearchField(val original: String, val tier: Int, val title: Boolean = false) {
    val variants = listOf(false, true).map { foldGrammarText(original, it) }.distinctBy { it.text }
    val tokens = variants.flatMap { searchTokens(it.text) }
    fun matches(query: String) = tokens.any { tokenMatches(it.text, query) }
    fun exact(query: String) = tokens.any { it.text == query }
    fun phrase(query: String) = variants.any { " ${it.text} ".contains(" $query ") }
}

/** Prepared once; all searches and snippet offsets use the same folded fields. */
class GrammarSearchIndex(notes: List<GrammarNote>) {
    private val entries = notes.map { note ->
        val fields = mutableListOf<SearchField>()
        fun add(text: String, tier: Int, title: Boolean = false) {
            fields.add(SearchField(text.replace("**", "").replace("`", ""), tier, title))
        }
        add(note.titleFr, 2, true)
        add(note.titleFa, 2, true)
        add(note.id.replace('-', ' '), 2)
        (note.aliasesFr + note.aliasesFa).forEach { add(it, 2) }
        note.blocks.forEach { block ->
            when (block) {
                is GrammarBlock.Prose -> block.markdown.forEach { add(it.text, if (block.type == "summary") 3 else 4) }
                is GrammarBlock.Structure -> {
                    block.patterns.forEach { add(it, 3) }
                    block.text.forEach { add(it.text, 4) }
                }
                is GrammarBlock.Examples -> block.items.forEach { add(it.fr.text, 4); add(it.fa, 4) }
                is GrammarBlock.Mistake -> listOf(block.wrong, block.right, block.why).forEach { add(it, 4) }
                is GrammarBlock.Infographic -> (block.header + block.rows.flatten()).forEach { add(it.text, 4) }
            }
        }
        note to fields
    }
    private val vocabulary = entries.flatMap { it.second }.flatMap { it.tokens }.map { it.text }.toSet()

    fun search(query: String): List<GrammarSearchResult> {
        var folded = foldForGrammarSearch(query)
        if (folded.isBlank()) return emptyList()
        // A spaced Persian compound also uses its joined spelling when it exists in the index.
        val words = folded.split(' ').toMutableList()
        var i = 0
        while (i < words.lastIndex) {
            val joined = words[i] + words[i + 1]
            if (words[i].all { it in '\u0600'..'\u06FF' } && words[i + 1].all { it in '\u0600'..'\u06FF' } && joined in vocabulary) {
                words[i] = joined
                words.removeAt(i + 1)
            } else i++
        }
        folded = words.joinToString(" ")
        val allTokens = words.map { it.substringAfterLast('\'') }.filter { it.isNotEmpty() }.distinct()
        val tokens = allTokens.filterNot { it in grammarStopWords }.ifEmpty { allTokens }
        if (tokens.isEmpty()) return emptyList()
        data class Ranked(val result: GrammarSearchResult, val bonus: Int)
        return entries.mapNotNull { (note, fields) ->
            if (!tokens.all { token -> fields.any { it.matches(token) } }) return@mapNotNull null
            val headings = fields.filter { it.tier == 2 }
            val tier = when {
                headings.any { field -> field.variants.any { it.text == folded } } -> 1
                headings.any { it.phrase(folded) } || tokens.all { token -> headings.any { it.matches(token) } } -> 2
                tokens.all { token -> fields.any { it.tier <= 3 && it.matches(token) } } -> 3
                else -> 4
            }
            val best = fields.filter { it.tier <= tier.coerceAtLeast(2) }
            val exact = tokens.count { token -> best.any { it.exact(token) } }
            val titleMatches = tokens.count { token -> headings.any { it.title && it.matches(token) } }
            val comparison = note.group == "comparisons" && tokens.size >= 2 &&
                tokens.all { token -> headings.any { it.title && it.exact(token) } }
            val options = note.titleFr.substringBefore('?').substringBefore('(').split(" ou ")
            val namedOption = options.size > 1 && options.any { option ->
                foldForGrammarSearch(option.trim()) == tokens.joinToString(" ")
            }
            val bonus = exact * 1000 + titleMatches * 100 - note.titleFr.length +
                (if (namedOption) 50 else 0) + (if (comparison) 10000 else 0)
            val snippet = if (tier <= 2) null else {
                val field = fields.filter { it.tier in 3..tier }.maxByOrNull { field ->
                    tokens.count { field.matches(it) } * 100 + tokens.count { field.exact(it) } * 10 - field.tier
                }
                field?.let { snippet(it, tokens) }
            }
            Ranked(GrammarSearchResult(note, tier, snippet), bonus)
        }.sortedWith(compareBy<Ranked> { it.result.tier }.thenByDescending { it.bonus }.thenBy { it.result.note.id })
            .map { it.result }
    }

    private fun snippet(field: SearchField, queries: List<String>): GrammarCell? {
        val ranges = field.variants.flatMap { folded ->
            searchTokens(folded.text).mapNotNull { token ->
                val query = queries.filter { tokenMatches(token.text, it) }.maxByOrNull { it.length } ?: return@mapNotNull null
                HighlightRange(folded.starts[token.start], folded.ends[token.start + query.length - 1])
            }
        }.distinct().sortedBy { it.start }
        val first = ranges.firstOrNull() ?: return null
        val start = (first.start - 30).coerceAtLeast(0)
        val end = (start + maxOf(90, first.end - start)).coerceAtMost(field.original.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < field.original.length) "…" else ""
        val text = prefix + field.original.substring(start, end) + suffix
        return GrammarCell(text, ranges.filter { it.start >= start && it.end <= end }.map {
            HighlightRange(it.start - start + prefix.length, it.end - start + prefix.length)
        })
    }
}
