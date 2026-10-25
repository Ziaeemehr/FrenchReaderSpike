package com.ziaee.frenchreader.content

import org.junit.Assert.*
import org.junit.Test

class BundledSentenceTranslationsTest {
    @Test fun staleSourceHashIsIgnoredPerLanguageBeforeSentenceNormalization() {
        // SHA-256 of the exact UTF-8 source "Bonjour !".
        val hash = "bc7444869484dd1ed34bfc4465724b887fecf604fcad0d0eaf40d3746d0bfeb7"
        fun rows(sentence: String) = parseDatasetDeck("""[{"w":"bonjour","m":"hello","s":"$sentence",
            "st":{"fa":"سلام!","en":"Hello!"},
            "stMeta":{"fa":{"source":"$hash"},"en":{"source":"stale"}}}]""")
        val valid = BundledSentenceTranslations { rows("Bonjour !") }
        assertEquals("سلام!", valid.get("  BONJOUR !  ", "fa"))
        assertNull(valid.get("Bonjour !", "en"))
        val edited = BundledSentenceTranslations { rows("Bonjour ! ") }
        assertNull(edited.get("Bonjour !", "fa"))
    }

    @Test fun deckRowsSupportOptionalTranslationsWithoutChangingExistingFields() {
        val rows = parseDatasetDeck("""[{"w":"salut","m":"hello","s":"Salut !","l":"2"},
            {"w":"bonjour","m":"hello","s":"Bonjour !","st":{"fa":"سلام!","en":"Hello!","bad":null}}]""")
        assertTrue(rows[0].sentenceTranslations.isEmpty())
        assertEquals("2", rows[0].lesson)
        assertEquals(mapOf("fa" to "سلام!", "en" to "Hello!"), rows[1].sentenceTranslations)
    }

    @Test fun indexIsLazyNormalizesUnicodeWhitespaceCaseApostropheAndLanguage() {
        var loads = 0
        val index = BundledSentenceTranslations {
            loads++
            listOf(DatasetDeckRow("mot", "meaning", "L’été est arrivé.", null,
                mapOf("fa" to "تابستان رسید.")))
        }
        assertEquals(0, loads)
        assertEquals("تابستان رسید.", index.get("  L'E\u0301TE\u0301\u00a0est\narrivé.  ", "FA-IR"))
        assertEquals("تابستان رسید.", index.get("L’été est arrivé.", "fa_IR"))
        assertEquals(1, loads)
        assertNull(index.get("L’été est arrivé.", "en"))
        assertNull(index.get("L’hiver est arrivé.", "fa"))
    }

    @Test fun manifestMetadataIsOptional() {
        val legacy = """{"version":1,"decks":[],"stories":[]}"""
        assertNull(parseDatasetManifest(legacy).sentenceTranslations)
        val enriched = legacy.dropLast(1) + """, "sentenceTranslations":{"model":"gemma-3","promptRevision":"lines-fa-v1"}}"""
        assertEquals(DatasetSentenceTranslationMetadata("gemma-3", "lines-fa-v1"),
            parseDatasetManifest(enriched).sentenceTranslations)
    }
}
