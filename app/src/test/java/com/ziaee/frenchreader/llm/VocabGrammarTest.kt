package com.ziaee.frenchreader.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabGrammarTest {
    @Test
    fun parsesWellFormedArray() {
        val json = """[{"mot":"montagne","definition_simple":"une montagne"},{"mot":"sommet","definition_simple":"le haut"}]"""
        val items = VocabGrammar.parse(json)
        assertEquals(2, items.size)
        assertEquals("montagne", items[0].mot)
        assertEquals("une montagne", items[0].definitionSimple)
    }

    @Test
    fun returnsEmptyListOnMalformedJson() {
        assertTrue(VocabGrammar.parse("not json").isEmpty())
    }

    @Test
    fun skipsItemsMissingRequiredKeys() {
        val json = """[{"mot":"montagne"},{"mot":"sommet","definition_simple":"le haut"}]"""
        val items = VocabGrammar.parse(json)
        assertEquals(1, items.size)
        assertEquals("sommet", items[0].mot)
    }
}
