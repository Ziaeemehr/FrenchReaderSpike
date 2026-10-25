package com.ziaee.frenchreader.translate.prepare

import com.ziaee.frenchreader.text.BlockType
import com.ziaee.frenchreader.text.MarkdownParser
import com.ziaee.frenchreader.text.TextChunker
import com.ziaee.frenchreader.text.isRtlText
import com.ziaee.frenchreader.translate.TranslationService
import com.ziaee.frenchreader.translate.paragraphTranslationContext
import com.ziaee.frenchreader.watch.SubtitleCue
import com.ziaee.frenchreader.watch.mergeIntoSentences

fun libraryPreparationSource(id: String, title: String, body: String, language: String): PreparationSource {
    val blocks = TextChunker.chunk(body).map(MarkdownParser::parse)
        .filter { it.type != BlockType.IMAGE && it.spokenText.isNotBlank() }
    val items = blocks.mapIndexedNotNull { index, block ->
        if (TranslationService.baseLanguage(language) == "fr" ||
            (TranslationService.baseLanguage(language) in setOf("fa", "ar", "he", "iw", "ur", "ps") && isRtlText(block.spokenText))) null
        else PreparationItem(block.spokenText, language,
            before = paragraphTranslationContext(block.spokenText, blocks.getOrNull(index - 1)?.spokenText),
            // Preserve the individual paragraph prompt used live.
            group = "$id:$index", position = index)
    }
    return PreparationSource(id, title, items)
}

fun subtitlePreparationSource(id: String, title: String, cues: List<SubtitleCue>, language: String): PreparationSource {
    val units = mergeIntoSentences(cues)
    return PreparationSource(id, title, units.mapIndexed { index, cue ->
        PreparationItem(cue.text, language, before = units.getOrNull(index - 1)?.text,
            after = units.getOrNull(index + 1)?.text, group = id, position = index)
    })
}

fun flashcardPreparationSources(
    cards: List<com.ziaee.frenchreader.data.VocabEntry>, language: String, meaningsTitle: String, examplesTitle: String,
    lists: List<com.ziaee.frenchreader.data.VocabList> = emptyList(), unfiledTitle: String = ""
): List<PreparationSource> {
    val counts = cards.groupingBy { it.listId }.eachCount()
    val decks = lists.map { PreparationDeck(it.id, it.name, counts[it.id] ?: 0) } +
        PreparationDeck(null, unfiledTitle, counts[null] ?: 0)
    return listOf(
        PreparationSource("meanings", meaningsTitle, cards.map {
            PreparationItem(it.word, language, PreparationKind.MEANING, sentence = it.sentence, deckId = it.listId)
        }, decks),
        PreparationSource("examples", examplesTitle, cards.filter { it.sentence.isNotBlank() }.map {
            PreparationItem(it.sentence, language, group = "example:${it.id}", deckId = it.listId)
        }, decks)
    )
}
