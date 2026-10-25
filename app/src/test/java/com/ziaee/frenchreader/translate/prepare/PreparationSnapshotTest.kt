package com.ziaee.frenchreader.translate.prepare

import org.junit.Assert.*
import org.junit.Test

class PreparationSnapshotTest {
    @Test fun persistedDeckChoicesDistinguishLegacyAllFromNoneAndUnfiled() {
        assertNull(decodePreparationDeckSelection(null))
        for (decks in listOf(null, emptySet(), setOf(null, 12L))) {
            assertEquals(decks, decodePreparationDeckSelection(encodePreparationDeckSelection(decks)))
        }
        assertEquals(emptySet<Long?>(), decodePreparationDeckSelection(emptySet()))
    }

    @Test fun deckSelectionRoundTripsAllNoneAndUnfiled() {
        for (decks in listOf(null, emptySet(), setOf(null, 12L))) {
            val job = PreparationJob("job", "model", emptyList(), decks)
            assertEquals(job, decodePreparationJob(encodePreparationJob(job)))
        }
    }

    @Test fun sharedReaderSentenceRetainsBothConsumersBecauseReaderCannotUseBundle() {
        val reader = PreparationItem("Bonjour !", "fa", group = "text:1")
        val example = reader.copy(group = "example:2", deckId = 12L)
        val sources = listOf(PreparationSource("text:1", "Text", listOf(reader)),
            PreparationSource("examples", "Examples", listOf(example)))
        assertEquals(listOf(example, reader), selectedItems(sources, setOf("text:1", "examples")))
        assertEquals(listOf(reader), selectedItems(sources, setOf("text:1", "examples"), emptySet()))
    }

    @Test fun includedSentencesSkipWorkUnlessRegenerationNeedsPersonalCache() {
        assertTrue(examplePrepared(true, false, { error("Unexpected cache read") }, { error("Unexpected cache read") }))
        assertFalse(examplePrepared(true, true, { false }, { true }))
        assertTrue(examplePrepared(true, true, { true }, { error("Ordinary cache must not count") }))
        assertFalse(examplePrepared(false, false, { true }, { false }))
    }

    @Test fun regenerationSelectionRoundTripsAndDefaultsOff() {
        val legacy = """{"id":"job","model":"model","items":[]}"""
        assertFalse(decodePreparationJob(legacy).regenerateBundledExamples)
        for (enabled in listOf(false, true)) {
            val job = PreparationJob("job", "model", emptyList(), setOf(12L), enabled)
            assertEquals(job, decodePreparationJob(encodePreparationJob(job)))
        }
    }

    @Test fun oldSnapshotMeansAllDecks() {
        val old = """{"id":"job","model":"model","items":[]}"""
        assertNull(decodePreparationJob(old).selectedDecks)
    }

    @Test fun snapshotRoundTripPreservesApprovedModelLanguageAndContextExactly() {
        val items = listOf(
            PreparationItem("Bonjour\nle monde", "en", before = "Avant", after = "Après", group = "video:1", position = 2),
            PreparationItem("capitale", "fa", PreparationKind.MEANING, sentence = "Paris est la capitale."),
            PreparationItem("oui", "en", PreparationKind.MEANING, sentence = "", deckId = 12L),
            PreparationItem("non", "en", PreparationKind.MEANING, sentence = null))
        val job = PreparationJob("approved-job", "ONLINE|https://service.example/v1|model", items)
        val restored = decodePreparationJob(encodePreparationJob(job))
        assertEquals(job, restored)
        assertEquals(items.map { it.identity }, restored.items.map { it.identity })
        assertEquals("", restored.items[2].sentence)
        assertNull(restored.items[3].sentence)
    }
    @Test fun malformedSnapshotIsRejectedRatherThanSendingWrongContent() {
        for (json in listOf("", "{}", "{\"id\":\"job\",\"model\":\"model\",\"items\":[{}]}")) {
            try { decodePreparationJob(json); fail("Malformed snapshot accepted") }
            catch (_: org.json.JSONException) { }
        }
    }
}
