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
