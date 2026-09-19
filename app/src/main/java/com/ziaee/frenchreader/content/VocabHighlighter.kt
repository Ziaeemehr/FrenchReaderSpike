package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.data.VocabStatus
import java.text.Normalizer
import java.util.Locale

data class SavedVocab(val word: String, val status: VocabStatus)

data class VocabMatch(val range: IntRange, val status: VocabStatus)

fun normalizeVocabWord(word: String): String = normalizePiece(word).trim()

private fun normalizePiece(text: String): String = Normalizer
    .normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(COMBINING_MARKS, "")
    .replace('’', '\'')
    .replace('‘', '\'')
    .replace('ʼ', '\'')
    .replace('`', '\'')

fun buildVocabStatusMap(entries: Iterable<SavedVocab>): Map<String, VocabStatus> {
    val result = LinkedHashMap<String, VocabStatus>()
    for (entry in entries) {
        val word = normalizeVocabWord(entry.word)
        if (word.isEmpty()) continue
        val previous = result[word]
        if (previous == null || entry.status.ordinal < previous.ordinal) {
            result[word] = entry.status
        }
    }
    return result
}

class VocabHighlighter(entries: Iterable<SavedVocab>) {
    private val candidatesByFirstChar: Map<Char, List<Pair<String, VocabStatus>>> =
        buildVocabStatusMap(entries)
            .entries
            .groupBy({ it.key.first() }, { it.key to it.value })
            .mapValues { (_, candidates) -> candidates.sortedByDescending { it.first.length } }

    fun findMatches(text: String): List<VocabMatch> {
        if (candidatesByFirstChar.isEmpty() || text.isEmpty()) return emptyList()
        val normalized = normalizeWithOffsets(text)
        val matches = ArrayList<VocabMatch>()
        var index = 0
        while (index < normalized.text.length) {
            val c = normalized.text[index]
            if (!c.isLetterOrDigit() || !isStartBoundary(normalized.text, index)) {
                index++
                continue
            }
            val match = candidatesByFirstChar[c]
                ?.firstOrNull { (word, _) ->
                    normalized.text.regionMatches(index, word, 0, word.length) &&
                        isEndBoundary(normalized.text, index + word.length)
                }
            if (match == null) {
                index++
                continue
            }
            val end = index + match.first.length - 1
            matches += VocabMatch(normalized.originalOffsets[index]..normalized.originalOffsets[end], match.second)
            index = end + 1
        }
        return matches
    }

    private fun isStartBoundary(text: String, index: Int): Boolean {
        if (index == 0) return true
        val previous = text[index - 1]
        if (previous != '\'') return !isWordPart(previous)
        var prefixStart = index - 2
        while (prefixStart >= 0 && text[prefixStart].isLetter()) prefixStart--
        val prefix = text.substring(prefixStart + 1, index - 1)
        return prefix in FRENCH_ELISION_PREFIXES
    }

    private fun isEndBoundary(text: String, endExclusive: Int): Boolean =
        endExclusive == text.length || !isWordPart(text[endExclusive])

    private fun isWordPart(c: Char): Boolean = c.isLetterOrDigit() || c == '\'' || c == '-'
}

private data class NormalizedText(val text: String, val originalOffsets: IntArray)

private fun normalizeWithOffsets(text: String): NormalizedText {
    val normalized = StringBuilder(text.length)
    val offsets = ArrayList<Int>(text.length)
    text.forEachIndexed { index, char ->
        val piece = normalizePiece(char.toString())
        for (normalizedChar in piece) {
            normalized.append(normalizedChar)
            offsets += index
        }
    }
    return NormalizedText(normalized.toString(), offsets.toIntArray())
}

private val COMBINING_MARKS = Regex("\\p{M}+")
private val FRENCH_ELISION_PREFIXES = setOf(
    "l", "d", "j", "m", "n", "s", "t", "c", "qu", "jusqu", "lorsqu", "puisqu"
)
