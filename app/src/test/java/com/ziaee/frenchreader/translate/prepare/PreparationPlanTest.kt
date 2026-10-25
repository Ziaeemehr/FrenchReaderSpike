package com.ziaee.frenchreader.translate.prepare

import org.junit.Assert.*
import org.junit.Test
import com.ziaee.frenchreader.translate.llm.parseTranslations
import com.ziaee.frenchreader.translate.llm.LlmError
import org.json.JSONObject

class PreparationPlanTest {
    private fun line(text: String, pos: Int = 0, group: String = "video", lang: String = "fa") =
        PreparationItem(text, lang, group = group, position = pos)

    @Test fun onlyCurrentRunnableJobMaySendRequests() {
        for (status in PreparationStatus.entries) {
            val progress = PreparationProgress("current", status, 0, 1)
            val runnable = status in setOf(PreparationStatus.QUEUED, PreparationStatus.RUNNING, PreparationStatus.RETRY)
            assertEquals(status.name, runnable, progress.allowsRequest("current"))
            assertFalse(progress.allowsRequest("stale"))
        }
        assertFalse((null as PreparationProgress?).allowsRequest("current"))
    }

    @Test fun selectionDeduplicatesByLiveCacheKeyAndPreservesOrder() {
        val a = line("Bonjour")
        val b = line("Bonsoir", 1)
        val sources = listOf(PreparationSource("a", "A", listOf(a, b)),
            PreparationSource("b", "B", listOf(a.copy(before = "context"), line(" "))))
        assertEquals(listOf(a, b), selectedItems(sources, setOf("a", "b")))
        assertTrue(selectedItems(sources, emptySet()).isEmpty())
        assertEquals(listOf(a.copy(before = "context")), selectedItems(sources, setOf("b")))
    }

    @Test fun meaningsSeparateSentenceAndLanguageButLinesShareContexts() {
        val a = PreparationItem("capitale", "fa", PreparationKind.MEANING, sentence = "Paris est la capitale.")
        assertNotEquals(a.identity, a.copy(sentence = "Une erreur capitale.").identity)
        assertNotEquals(a.identity, a.copy(language = "en").identity)
        assertNotEquals(a.identity, a.copy(kind = PreparationKind.LINE).identity)
        assertEquals(line("Oui").identity, line("Oui").copy(before = "Before").identity)
        assertNotEquals(a.copy(sentence = null).identity, a.copy(sentence = "").identity)
    }

    @Test fun cachedItemsAreExcludedWithoutChangingContext() {
        val items = listOf(line("A"), line("B", 1).copy(before = "A"), line("C", 2))
        assertEquals(listOf(items[1]), pendingItems(items) { it.text != "B" })
        assertEquals("A", pendingItems(items) { it.text != "B" }.single().before)
    }

    @Test fun batchingRespectsOrderGapsGroupLanguageAndMeanings() {
        val items = listOf(line("A"), line("B", 1), line("C", 3), line("D", 4, lang = "en"),
            line("E", 5, "other"), PreparationItem("mot", "fa", PreparationKind.MEANING))
        val batches = preparationBatches(items)
        assertEquals(listOf(2, 1, 1, 1, 1), batches.map { it.size })
        assertEquals(items, batches.flatten())
        assertEquals(listOf(3, 1), preparationBatches((0..3).map { line("$it", it) }).map { it.size })
    }

    @Test fun batchSizeAndRecursiveSplitsNeverLoseItems() {
        val items = (0..4).map { line("x".repeat(2500), it) }
        assertEquals(listOf(2, 2, 1), preparationBatches(items).map { it.size })
        assertEquals(items, splitPreparationBatch(items).flatten())
        assertEquals(listOf(2, 3), splitPreparationBatch(items).map { it.size })
        assertEquals(listOf(listOf(items[0])), splitPreparationBatch(listOf(items[0])))
        assertTrue(splitPreparationBatch(emptyList()).isEmpty())
    }

    @Test fun estimatesIncludeBatchSavingsAndUtf8Storage() {
        assertEquals(PreparationEstimate(0, 0), estimatePreparation(emptyList()))
        val items = listOf(line("A"), line("B", 1), PreparationItem("mot", "fa", PreparationKind.MEANING))
        assertEquals(5L, estimatePreparation(items).seconds)
        assertEquals(394L, estimatePreparation(items).bytes)
        assertEquals(3L, estimatePreparation(listOf(line("A"))).seconds)
        assertTrue(estimatePreparation(listOf(line("سلام"))).bytes > estimatePreparation(listOf(line("abcd"))).bytes)
    }

    @Test fun existingBatchValidatorRejectsMismatchBlankAndNonStrings() {
        for (json in listOf("{\"translations\":[\"one\"]}", "{\"translations\":[\"one\",\"\"]}",
            "{\"translations\":[\"one\",3]}")) {
            try { parseTranslations(JSONObject(json), 2); fail("Invalid batch accepted") }
            catch (e: LlmError) { assertEquals(LlmError.Kind.BAD_RESPONSE, e.kind) }
        }
        assertEquals(listOf("one", "two"), parseTranslations(JSONObject("{\"translations\":[\"one\",\"two\"]}"), 2))
    }
}
