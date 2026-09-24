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
}
