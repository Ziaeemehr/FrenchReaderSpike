package com.ziaee.frenchreader.content

/** One card from a user's spreadsheet: `word` is the card front, `sentence` gets TTS audio. */
data class CsvWord(val word: String, val meaning: String?, val sentence: String, val deck: String?)

data class CsvWordFile(val words: List<CsvWord>, val hasDeckColumn: Boolean)

/** Where imported words go: the file's own deck column (rows without one use [fallbackDeck]),
 * or a single deck by name (existing or new). */
sealed interface CsvImportTarget {
    data class DeckColumn(val fallbackDeck: String) : CsvImportTarget
    data class Deck(val name: String) : CsvImportTarget
}

data class PlannedCsvWord(val deck: String, val word: CsvWord)
data class CsvImportPlan(val deckNames: List<String>, val words: List<PlannedCsvWord>, val skipped: Int)

private val WORD_HEADERS = setOf("word", "front", "mot", "terme", "کلمه", "واژه", "لغت")
private val MEANING_HEADERS = setOf("meaning", "back", "translation", "sens", "traduction", "définition", "معنی", "ترجمه")
private val SENTENCE_HEADERS = setOf("sentence", "example", "phrase", "exemple", "جمله", "مثال")
private val DECK_HEADERS = setOf("deck", "list", "paquet", "liste", "دک", "دسته")

/**
 * Splits CSV/TSV text into rows (RFC 4180: quoted fields may hold the delimiter, newlines and
 * doubled quotes). The delimiter is guessed from the first line: tab, else `;` when it outnumbers
 * `,` (Excel in a French locale saves with `;`), else `,`. A UTF-8 BOM is ignored.
 */
internal fun parseDelimited(text: String): List<List<String>> {
    val input = text.removePrefix("﻿")
    val firstLine = input.substringBefore('\n')
    val delimiter = when {
        '\t' in firstLine -> '\t'
        firstLine.count { it == ';' } > firstLine.count { it == ',' } -> ';'
        else -> ','
    }
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < input.length) {
        val c = input[i]
        when {
            quoted && c == '"' && input.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
            c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
            quoted -> field.append(c)
            c == delimiter -> { row += field.toString(); field.clear() }
            c == '\n' || c == '\r' -> {
                if (c == '\r' && input.getOrNull(i + 1) == '\n') i++
                row += field.toString(); field.clear()
                rows += row; row = mutableListOf()
            }
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
    return rows.filter { cells -> cells.any { it.isNotBlank() } }
}

/** Reads cards from spreadsheet text. A first row naming the columns (word, meaning, sentence,
 * deck -- in English, French or Persian) is used as the header; otherwise columns are taken in
 * that order. Rows without a word are dropped. */
internal fun parseCsvWords(text: String): CsvWordFile {
    val rows = parseDelimited(text)
    val header = rows.firstOrNull()?.map { it.trim().lowercase() }.orEmpty()
    fun column(names: Set<String>) = header.indexOfFirst { it in names }
    val hasHeader = column(WORD_HEADERS) >= 0 || column(MEANING_HEADERS) >= 0
    val wordCol = if (hasHeader) column(WORD_HEADERS).takeIf { it >= 0 } ?: 0 else 0
    val meaningCol = if (hasHeader) column(MEANING_HEADERS) else 1
    val sentenceCol = if (hasHeader) column(SENTENCE_HEADERS) else 2
    val deckCol = if (hasHeader) column(DECK_HEADERS) else 3
    fun List<String>.cell(index: Int) = getOrNull(index)?.trim().orEmpty()
    val words = rows.drop(if (hasHeader) 1 else 0).mapNotNull { cells ->
        val word = cells.cell(wordCol).replace(Regex("\\s+"), " ")
        if (word.isEmpty()) null
        else CsvWord(
            word = word,
            meaning = cells.cell(meaningCol).ifEmpty { null },
            sentence = cells.cell(sentenceCol),
            deck = cells.cell(deckCol).ifEmpty { null }
        )
    }
    return CsvWordFile(words, hasDeckColumn = deckCol >= 0 && words.any { it.deck != null })
}

/** Assigns each word its deck and skips duplicates: a word already in that deck
 * ([existingWordKeys] = lowercase deck name to lowercase word) or repeated in the file. */
internal fun planCsvImport(
    file: CsvWordFile,
    target: CsvImportTarget,
    existingWordKeys: Set<Pair<String, String>>
): CsvImportPlan {
    val seen = existingWordKeys.toMutableSet()
    val decks = linkedSetOf<String>()
    val words = mutableListOf<PlannedCsvWord>()
    var skipped = 0
    for (word in file.words) {
        val deck = when (target) {
            is CsvImportTarget.Deck -> target.name
            is CsvImportTarget.DeckColumn -> word.deck ?: target.fallbackDeck
        }.trim()
        if (!seen.add(deck.lowercase() to word.word.lowercase())) { skipped++; continue }
        decks += deck
        words += PlannedCsvWord(deck, word)
    }
    return CsvImportPlan(decks.toList(), words, skipped)
}
