package com.ziaee.frenchreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingFindTest {
    @Test
    fun foldingKeepsLengthAndIgnoresCaseAndAccents() {
        val text = "École, ÉTÉ — naïve œuvre"
        assertEquals(text.length, foldForFind(text).length)
        assertEquals("ecole, ete — naive œuvre", foldForFind(text))
    }

    @Test
    fun findsEveryOccurrenceIgnoringAccents() {
        val text = "Une écharpe rouge. Deux ECHARPES."
        assertEquals(listOf(4..10, 24..30), findRanges(text, "echarpe"))
        assertEquals("écharpe", text.substring(4, 11))
        assertTrue(findRanges(text, "   ").isEmpty())
    }

    @Test
    fun matchesAreIndexedByParagraph() {
        val matches = findMatches(listOf("le chat", "", "un chat et un chat"), "chat")
        assertEquals(listOf(0, 2, 2), matches.map { it.chunkIndex })
        assertEquals(14..17, matches.last().range)
    }

    @Test
    fun bestQueryFallsBackToLongestWordThatOccurs() {
        val texts = listOf("Il pleut, prends ton parapluie.", "Pas d'abri ici.")
        assertEquals("ton parapluie", bestFindQuery(texts, " ton parapluie "))
        assertEquals("parapluie", bestFindQuery(texts, "abri parapluie"))
        assertEquals("zzz", bestFindQuery(texts, "zzz"))
    }
}
