package com.ziaee.frenchreader.translate.prepare

import com.ziaee.frenchreader.watch.SubtitleCue
import org.junit.Assert.*
import org.junit.Test

class PreparationProjectionTest {
    @Test fun deckSelectionFiltersBeforeDeduplicationAndEstimation() {
        val cards = listOf(
            com.ziaee.frenchreader.data.VocabEntry(id = 1, word = "bonjour", sentence = "Bonjour.", textId = 1, dictionaryUrl = "", listId = 10),
            com.ziaee.frenchreader.data.VocabEntry(id = 2, word = "bonjour", sentence = "Bonjour.", textId = 1, dictionaryUrl = "", listId = 20),
            com.ziaee.frenchreader.data.VocabEntry(id = 3, word = "salut", sentence = "Salut.", textId = 1, dictionaryUrl = ""))
        val decks = listOf(com.ziaee.frenchreader.data.VocabList(id = 10, name = "A"),
            com.ziaee.frenchreader.data.VocabList(id = 20, name = "B"),
            com.ziaee.frenchreader.data.VocabList(id = 30, name = "Empty"))
        val sources = flashcardPreparationSources(cards, "fa", "Meanings", "Examples", decks, "Unfiled")
        val kinds = setOf("meanings", "examples")
        assertEquals(listOf(1, 1, 0, 1), sources.first().decks.map { it.cardCount })
        assertEquals(4, selectedItems(sources, kinds).size)
        val chosen = selectedItems(sources, kinds, setOf(20L))
        assertEquals(listOf("bonjour", "Bonjour."), chosen.map { it.text })
        assertEquals(4L, estimatePreparation(chosen).seconds)
        assertEquals(listOf("salut", "Salut."), selectedItems(sources, kinds, setOf(null)).map { it.text })
        assertTrue(selectedItems(sources, kinds, emptySet()).isEmpty())
        assertTrue(selectedItems(sources, kinds, setOf(30L, 999L)).isEmpty())
        assertEquals(1, selectedItems(sources, setOf("meanings"), setOf(20L)).size)
        assertEquals(1, selectedItems(sources, setOf("examples"), setOf(20L)).size)
        val library = PreparationSource("text:1", "Book", listOf(PreparationItem("Livre.", "fa")))
        assertEquals(listOf("Livre."), selectedItems(sources + library, kinds + "text:1", emptySet()).map { it.text })
    }

    @Test fun libraryUsesLiveSpeechTextAndPreviousParagraphContext() {
        val source = libraryPreparationSource("text:1", "Book", "# **Bonjour**\n\nUne phrase. Une autre.\n\nVoici [Paris](https://example.com).\n\n![cover](epubimg:cover)", "fa")
        assertEquals(listOf("Bonjour", "Une phrase. Une autre.", "Voici Paris."), source.items.map { it.text })
        assertEquals("Bonjour", source.items[1].before)
        assertEquals("Une phrase. Une autre.", source.items[2].before)
        assertEquals(listOf(1, 1, 1), preparationBatches(source.items).map { it.size })
        assertTrue(libraryPreparationSource("id", "French", "Bonjour", "fr").items.isEmpty())
        assertTrue(libraryPreparationSource("id", "Persian", "سلام دنیا", "fa").items.isEmpty())
    }

    @Test fun readerWatchAndExampleShareTheExactLiveLineKey() {
        val text = "Bonjour le monde."
        val reader = libraryPreparationSource("text:1", "Book", "**Bonjour** le monde.", "fa").items.single()
        val watch = subtitlePreparationSource("video:1", "Video", listOf(
            SubtitleCue(0, 500, "Bonjour"), SubtitleCue(500, 1000, "le monde.")), "fa").items.single()
        val card = com.ziaee.frenchreader.data.VocabEntry(id = 1, word = "monde", sentence = text,
            textId = 1, dictionaryUrl = "")
        val example = flashcardPreparationSources(listOf(card), "fa", "Meanings", "Examples")[1].items.single()
        val liveKey = com.ziaee.frenchreader.translate.llm.translationCacheKey("", "fa", text)
        for (item in listOf(reader, watch, example)) assertEquals(liveKey, item.identity)
        val meaning = flashcardPreparationSources(listOf(card), "fa", "Meanings", "Examples")[0].items.single()
        assertEquals(com.ziaee.frenchreader.translate.llm.meaningCacheKey("", "fa", card.word, card.sentence), meaning.identity)
        assertNotEquals(liveKey, meaning.identity)
    }

    @Test fun flashcardsPreserveMeaningContextAndDoNotPrepareBlankExamples() {
        val cards = listOf(
            com.ziaee.frenchreader.data.VocabEntry(id = 1, word = "capitale", sentence = "Paris est la capitale.", textId = 1, dictionaryUrl = "", meaning = "My correction"),
            com.ziaee.frenchreader.data.VocabEntry(id = 2, word = "bonjour", sentence = "", textId = -1, dictionaryUrl = ""))
        val sources = flashcardPreparationSources(cards, "fa", "Meanings", "Examples")
        assertEquals(listOf("meanings", "examples"), sources.map { it.id })
        assertEquals(listOf("Paris est la capitale.", ""), sources[0].items.map { it.sentence })
        assertTrue(sources[0].items.all { it.kind == PreparationKind.MEANING })
        assertEquals(listOf("Paris est la capitale."), sources[1].items.map { it.text })
        assertEquals("My correction", cards[0].meaning)
    }

    @Test fun subtitlesUseMergedLiveSentencesWithBothNeighbours() {
        val source = subtitlePreparationSource("video:1", "Video", listOf(
            SubtitleCue(0, 500, "Bonjour"), SubtitleCue(500, 1000, "le monde."),
            SubtitleCue(1100, 1500, "Comment allez-vous ?"), SubtitleCue(1600, 2000, "Très bien.")), "en")
        assertEquals(listOf("Bonjour le monde.", "Comment allez-vous ?", "Très bien."), source.items.map { it.text })
        assertNull(source.items.first().before)
        assertEquals("Bonjour le monde.", source.items[1].before)
        assertEquals("Très bien.", source.items[1].after)
        assertNull(source.items.last().after)
        assertEquals(listOf(3), preparationBatches(source.items).map { it.size })
        // Skipping a cached middle sentence splits the batch but retains its context.
        val pending = pendingItems(source.items) { it.position == 1 }
        assertEquals(listOf(1, 1), preparationBatches(pending).map { it.size })
        assertEquals("Comment allez-vous ?", pending.last().before)
    }
}
