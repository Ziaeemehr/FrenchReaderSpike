package com.ziaee.frenchreader.comprehension

import java.util.Locale

interface FrenchLemmaLexicon {
    val commonLemmas: Set<String>

    fun lemmas(form: String): Set<String>
    fun containsForm(form: String): Boolean
}

class InMemoryLemmaLexicon(
    private val forms: Map<String, Set<String>>,
    override val commonLemmas: Set<String>
) : FrenchLemmaLexicon {
    override fun lemmas(form: String): Set<String> = forms[form] ?: setOf(form)
    override fun containsForm(form: String): Boolean = form in forms
}

data class FrenchToken(
    val surface: String,
    val lemmas: Set<String>
)

private val wordPattern = Regex("[\\p{L}]+(?:['’ʼ-][\\p{L}]+)*['’ʼ]?")
private val markdownImageLine = Regex("^\\s*!\\[[^]]*]\\([^)]*\\)\\s*$")
private val elisions = listOf("l'", "d'", "j'", "m'", "t'", "s'", "n'", "c'", "qu'", "jusqu'", "lorsqu'", "puisqu'")
    .sortedByDescending(String::length)
private val sentenceInitialPredecessors = setOf('.', '!', '?', '…', '«', '»', '"', '—', '–', '-', ':', ';')

fun normalizeFrench(value: String): String = value
    .lowercase(Locale.FRENCH)
    .replace("œ", "oe")
    .replace("æ", "ae")
    .replace('’', '\'')
    .replace('ʼ', '\'')

fun tokenizeFrench(text: String, lexicon: FrenchLemmaLexicon): List<FrenchToken> {
    val filtered = text.lineSequence().filterNot { markdownImageLine.matches(it) }.joinToString("\n")
    return buildList {
        wordPattern.findAll(filtered).forEach { match ->
            val original = match.value
            if (isExcludedProperNoun(filtered, match.range.first, original)) return@forEach
            val normalized = normalizeFrench(original)
            splitElision(normalized).forEach { elisionPart ->
                val parts = if ('-' in elisionPart && !lexicon.containsForm(elisionPart)) {
                    elisionPart.split('-').filter(String::isNotBlank)
                } else {
                    listOf(elisionPart)
                }
                parts.forEach { surface -> add(FrenchToken(surface, lexicon.lemmas(surface))) }
            }
        }
    }
}

private fun splitElision(word: String): List<String> {
    val prefix = elisions.firstOrNull { word.startsWith(it) && word.length > it.length } ?: return listOf(word)
    return listOf(prefix) + splitElision(word.substring(prefix.length))
}

private fun isExcludedProperNoun(text: String, start: Int, original: String): Boolean {
    if (original.firstOrNull()?.isUpperCase() != true) return false
    // A word opening a line (a paragraph, or the first line after a title) starts a sentence.
    val previousIndex = (start - 1 downTo 0).firstOrNull { !text[it].isWhitespace() } ?: return false
    if ('\n' in text.substring(previousIndex, start)) return false
    return text[previousIndex] !in sentenceInitialPredecessors
}
