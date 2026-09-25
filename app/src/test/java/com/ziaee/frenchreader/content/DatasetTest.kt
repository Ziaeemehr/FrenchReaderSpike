package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.data.displayMeaning
import com.ziaee.frenchreader.data.lessonNumber
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetTest {
    @Test
    fun parsesManifest() {
        val manifest = parseDatasetManifest(
            """{"version":1,"decks":[{"id":"a","name":"A1","level":"A1","kind":"words","file":"decks/a.json","count":2}],"stories":[{"id":"s","name":"Stories","level":"B1","source":"Source","file":"stories/s.json","count":1}]}"""
        )
        assertEquals(1, manifest.version)
        assertEquals("words", manifest.decks.single().kind)
        assertEquals("Source", manifest.stories.single().source)
    }

    @Test
    fun parsesDeckAndStoryRows() {
        val deck = parseDatasetDeck(
            """[{"w":"bonjour","m":"hello\ngood day","s":"Bonjour !","l":"05"}]"""
        ).single()
        assertEquals("05", deck.lesson)
        assertEquals("hello\ngood day", deck.meaning)
        val story = parseDatasetStories(
            """[{"key":"dataset:x","title":"Titre","body":"Un.\n\nDeux.","sourceUrl":"https://example.test","image":"images/a.jpg"}]"""
        ).single()
        assertEquals("images/a.jpg", story.image)
    }

    @Test
    fun lessonSuffixRoundTripsMultilineMeaning() {
        val meaning = datasetMeaning("first line\nsecond line", "32.5")
        val entry = VocabEntry(
            word = "mot",
            sentence = "",
            textId = 0,
            dictionaryUrl = "",
            meaning = meaning
        )
        assertEquals("32.5", entry.lessonNumber())
        assertEquals("first line\nsecond line", entry.displayMeaning())
    }

    @Test
    fun starterPacksExistInManifest() {
        val manifest = parseDatasetManifest(
            File("src/main/assets/dataset/manifest.json").readText()
        )
        val deckIds = manifest.decks.map { it.id }.toSet()
        val storyIds = manifest.stories.map { it.id }.toSet()

        assertEquals(
            setOf("gram-dial-a1-vocab", "comm-ess-a1-phrases"),
            STARTER_DECK_IDS
        )
        assertEquals(
            setOf("stories-fabulang-a1", "stories-lingua-a1"),
            STARTER_STORY_IDS
        )
        assertTrue(deckIds.containsAll(STARTER_DECK_IDS))
        assertTrue(storyIds.containsAll(STARTER_STORY_IDS))
    }

    @Test
    fun cleanupDeletesCurrentAndLegacyCopiesOfDeletedDecksOnly() {
        val rows = listOf(
            DatasetDeckRow("bonjour", "hello", "Bonjour !", "05"),
            DatasetDeckRow("Comment allez-vous ?", "How are you?", "How are you?", "12")
        )
        val entries = listOf(
            vocabEntry(1, "BONJOUR", "hello — Leçon 05"),
            vocabEntry(2, "How are you?", "Comment allez-vous ? — Leçon 12"),
            vocabEntry(3, "bonjour", "wrong meaning"),
            vocabEntry(4, "bonjour", "hello — Leçon 05", listId = 9),
            vocabEntry(5, "bonjour", "hello — Leçon 05", textId = 7)
        )

        val plan = planDatasetCardCleanup(listOf(rows to null), entries)
        assertEquals(setOf(1L, 2L), plan.deleteIds)
        assertTrue(plan.moveToList.isEmpty())
    }

    @Test
    fun legacyMatchRequiresANonBlankSentence() {
        val rows = listOf(DatasetDeckRow("mot", "meaning", "", null))
        val entries = listOf(vocabEntry(1, "", "mot"))

        assertTrue(planDatasetCardCleanup(listOf(rows to null), entries).deleteIds.isEmpty())
    }

    @Test
    fun cleanupOfReaddedDeckKeepsTheCopyWithProgress() {
        val rows = listOf(
            DatasetDeckRow("chat", "cat", "", null),
            DatasetDeckRow("chien", "dog", "", null),
            DatasetDeckRow("oiseau", "bird", "", null),
            DatasetDeckRow("این مرد", "", "Cet homme", "01")
        )
        val entries = listOf(
            vocabEntry(1, "chat", "cat", leitnerBox = 3),   // loose, progressed
            vocabEntry(2, "chat", "cat", listId = 9),        // deck copy, new -> replaced by 1
            vocabEntry(3, "chien", "dog"),                   // loose, new
            vocabEntry(4, "chien", "dog", listId = 9),       // deck copy kept
            vocabEntry(5, "oiseau", "bird"),                 // loose, not in deck -> moved in
            vocabEntry(6, "Cet homme", "این مرد — Leçon 01"), // legacy, deck is Persian-first
            vocabEntry(7, "این مرد", "", listId = 9)
        )

        val plan = planDatasetCardCleanup(listOf(rows to 9L), entries)
        assertEquals(setOf(2L, 3L, 6L), plan.deleteIds)
        assertEquals(mapOf(1L to 9L, 5L to 9L), plan.moveToList)
    }

    @Test
    fun legacyCopyWithProgressMergesIntoALegacyDeck() {
        val rows = listOf(DatasetDeckRow("این مرد", "", "Cet homme", "01"))
        val entries = listOf(
            vocabEntry(1, "Cet homme", "این مرد — Leçon 01", leitnerBox = 4),
            vocabEntry(2, "Cet homme", "این مرد — Leçon 01", listId = 9)
        )

        val plan = planDatasetCardCleanup(listOf(rows to 9L), entries)
        assertEquals(setOf(2L), plan.deleteIds)
        assertEquals(mapOf(1L to 9L), plan.moveToList)
    }

    private fun vocabEntry(
        id: Long,
        word: String,
        meaning: String,
        listId: Long? = null,
        textId: Long = 0,
        leitnerBox: Int = 1
    ) = VocabEntry(
        id = id,
        word = word,
        sentence = "",
        textId = textId,
        dictionaryUrl = "",
        meaning = meaning,
        listId = listId,
        leitnerBox = leitnerBox
    )
}
